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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
		
		new HashMap<Class<?>, Boolean>();
		
		if (useCluster)
		{
			cluster = new ClusterHandler<E>(env, reportAvailable);
			cluster.start();
		}
	}
	/**
	 * Adds a classes to the cluster, so that the cluster can understand jobs sent from this client.
	 * 
	 * This method must be called before the job that relates to the classes is executed using execute(). 
	 * @param clazzes The classes to add to the cluster. 
	 */
	public void addClasses(Class<?>... clazzes)
	{
		if (cluster != null)
		{
			for (Entry<String, byte[]> entry : getClassesMap(clazzes).entrySet())
			{
				cluster.addToClasses(entry.getKey(), entry.getValue());
			}
		}
		else
		{
			throw new IllegalArgumentException("This method is only valid if a DisCo is configured to use a network cluster.");
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

		// Making sure the job class is known by the cluster. 
		if (cluster != null)
		{
			for (Entry<String, byte[]> entry : getClassesMap(job.getClass()).entrySet())
			{
				cluster.addToClasses(entry.getKey(), entry.getValue());
			}
		}
		
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
			public synchronized E get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
				final ObjectContainer<E> res = new ObjectContainer<E>();
				final CountDownLatch waitingLatch = new CountDownLatch(1);
				final Thread timeOutThread = new Thread(new Runnable(){
					@Override
					public void run() {
						try {
							unit.sleep(timeout);
						} catch (InterruptedException e) {
							return;
						}
						res.setException(new TimeoutException("Waited the " + timeout + " " + unit + "."));
						container.abort();
						resultLatch.countDown();
						waitingLatch.countDown();
					}
				});
				Thread resultThread;
				resultThread = new Thread(new Runnable(){
					@Override
					public void run() {
						try {
							resultLatch.await();
							if (container.isAborted())
							{
								res.setException(new ExecutionException(new RuntimeException("Job was aborted/cancelled.")));								
							}
						} catch (InterruptedException e) {
							res.setException(e);
						}
						res.setObject(result.getResult());
						timeOutThread.interrupt();
						waitingLatch.countDown();
					}
				});
				
				timeOutThread.start();
				resultThread.start();
				waitingLatch.await();
				try {
					return res.getObject();
				} catch (Exception e) {
					if (e instanceof InterruptedException)
						throw (InterruptedException)e;
					else if (e instanceof ExecutionException)
						throw (ExecutionException)e;
					else if (e instanceof TimeoutException)
						throw (TimeoutException)e;
					else
						e.printStackTrace();
				}
				return null;
			}
		};
		
		return res;
	}
	class ObjectContainer<T> {
		T obj;
		Exception e = null;
		synchronized void setObject(T obj)
		{
			this.obj = obj;
		}
		synchronized void setException(Exception e)
		{
			// Only 1 exception, the first. 
			if (this.e == null)
			{
				this.e = e;
			}
		}
		synchronized T getObject() throws Exception
		{
			if (e != null)
				throw e;
			return obj;
		}
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
	 * Returns a map of String and bytes representing the name and bytes of the classes given as input. 
	 * @param classesIn The classes to convert. 
	 * @return a map of String and bytes representing the name and bytes of the classes given as input.
	 */
	private Map<String, byte[]> getClassesMap(Class<?>... classesIn)
	{
		List<Class<?>> classes = new ArrayList<Class<?>>();
		
		for (int i = 0; i < classesIn.length; i++)
		{
			classes.add(classesIn[i]);
		}
		
		Class<?>[] emptyClassArray = {};
		try {
			return io.Classes.toClassNameAndBytesMap(classes.toArray(emptyClassArray));
		} catch (IOException e1) {
			return null;
		}
	}
}
