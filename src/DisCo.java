import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import network.ClusterHandler;

import collections.StackBlockingQueue;
import concurrent.ThreadPool;
import model.Environment;
import model.Job;
import model.Result;
import model.WorkContainer;


public class DisCo<E> {
	private Environment<E> env; // The environment, that holds everything of importance on this machine. 
	
	private ThreadPool pool; // The threadpool.
	
	private ClusterHandler<E> cluster = null; // If the cluster is started, this is where it goes. 
	private Map<Class<?>, Boolean> alreadySubmitted; // A map (where i do not use the keys) to hold all the classes that have already gone into the clusters shared state. So that i know what not to do again. 
	
	/**
	 * The simplest constructor of DisCo. 
	 * This just launches a local-only instance of DisCo, with a default number of threads that can use the entire local processor. 
	 */
	public DisCo()
	{
		this(0);
	}
	/**
	 * This constructor specifies how many threads DisCo should run. 
	 * This could be useful if you do not wan't the Cluster creation to take way to long (it can if you use the default values for threads).  
	 * @param threads The number of threads this DisCo instance should use. 
	 */
	public DisCo(int threads)
	{
		this(threads, false, false);
	}
	/**
	 * This constructor allows you to specify that DisCo should try to spread the work out to the other nodes in the cluster. 
	 * @param useCluster Whether or not a cluster should be used. 
	 * @param reportAvailable If using a cluster, this variable sets whether or not this machine should accept jobs from other nodes. 
	 */
	public DisCo(boolean useCluster, boolean reportAvailable)
	{
		this(0, useCluster, reportAvailable);
	}
	/**
	 * The complete constructor for DisCo. 
	 * @param threads The number of threads this DisCo instance should use. 
	 * @param useCluster Whether or not a cluster should be used. 
	 * @param reportAvailable If using a cluster, this variable sets whether or not this machine should accept jobs from other nodes. 
	 */
	public DisCo(int threads, boolean useCluster, boolean reportAvailable)
	{
		this.env = new Environment<E>();
		
		threads = threads == 0 ? Runtime.getRuntime().availableProcessors() + 1 : threads;
		
		this.pool = new ThreadPool(new StackBlockingQueue<WorkContainer<E>>(env.getDeque()), threads);
		pool.start();
		
		alreadySubmitted = new HashMap<Class<?>, Boolean>();
		
		if (useCluster)
		{
			cluster = new ClusterHandler<E>(env, reportAvailable);
			cluster.start();
		}
	}
	/**
	 * Returns a Future that holds the result from the job that was given as a input. 
	 * The get method of the Future throws an ExecutionException if the job was cancelled. 
	 * @param job The job that should be calculated. 
	 * @return A future holding the result from the job, and gives a interface to cancel it. 
	 */
	public Future<E> execute(Job<E> job)
	{
		// First setting all the variables that we use. 
		long id = env.getIncrementedLocalId();
		
		final WorkContainer<E> container = new WorkContainer<E>(env, job, id, 0, 0);
		
		final CountDownLatch resultLatch = new CountDownLatch(1);
		
		final Result<E> result = new Result<E>(0, 0, 0, null);
		
		// Then i make sure that it is calculated. 
		try {
			env.putJobInQueue(container);
		} catch (InterruptedException e1) {
			// This really shouldn't happen. 
			throw new RuntimeException("Could not put the job in the queue because of an interrupt. ");
		}
		env.callWhenFinalResultDone(id, new ActionListener(){
			@SuppressWarnings("unchecked")
			@Override
			public void actionPerformed(ActionEvent e) {
				result.setResult((E) e.getSource());
				resultLatch.countDown();
			}
		});
		// making a future to return. Its a really just a simple interface to the WorkContainer. 
		Future<E> res = new Future<E>(){
			@Override
			public synchronized boolean cancel(boolean mayInterruptIfRunning) {
				if (resultLatch.getCount() == 0)
				{
					return false;
				}
				container.abort();
				return true;
			}

			@Override
			public synchronized boolean isCancelled() {
				return container.isAborted();
			}

			@Override
			public synchronized boolean isDone() {
				return resultLatch.getCount() == 0;
			}
			
			@Override
			public synchronized E get() throws InterruptedException, ExecutionException {
				resultLatch.await(); 
				if (container.isAborted())
				{
					throw new ExecutionException(new RuntimeException("Job was aborted/cancelled."));
				}
				return result.getResult();
			}

			@Override
			public synchronized E get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
				throw new UnsupportedOperationException(); // TODO: Don't do this. 
			}
		};
		
		// Making sure that the cluster (if there) can execute it. 
		submitClasses(job.getClass());
		
		
		return res;
	}
	/**
	 * Closes this DisCo instance. Interrupts the threads and close the cluster. 
	 */
	public void close()
	{
		pool.shutdownNow();
		cluster.close();
		new Thread(new Runnable(){
			@SuppressWarnings("static-access")
			@Override
			public void run() {
				try {
					Thread.currentThread().sleep(3000);
				} catch (InterruptedException ignored) {}
				cluster.close();
			}
		}).start();
	}
	/**
	 * A private method to submit classes to the shared state of the network. 
	 * @param classesIn The classes that should be submitted. 
	 * @return false if there is no cluster. 
	 */
	private boolean submitClasses(Class<?>... classesIn)
	{
		if (cluster == null)
			return false;
		List<Class<?>> classes = new ArrayList<Class<?>>();
		
		for (int i = 0; i < classesIn.length; i++)
		{
			if (!alreadySubmitted.containsKey(classesIn[i]))
			{
				classes.add(classesIn[i]);
				alreadySubmitted.put(classesIn[i], true);
			}
		}
		
		Map<String, byte[]> classesMap = null;
		Class<?>[] emptyClassArray = {};
		try {
			classesMap = io.Classes.toClassNameAndBytesMap(classes.toArray(emptyClassArray));
		} catch (IOException e1) {}
		for (Entry<String, byte[]> entry : classesMap.entrySet())
		{
			cluster.putClassInSharedState(entry.getKey(), entry.getValue());
		}
		
		return true;
	}
}
