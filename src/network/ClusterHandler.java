package network;

import io.RuntimeClassLoader;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import model.Environment;
import model.Result;
import model.WorkContainer;
import network.data.BytePackage;
import network.data.ClusterMessage;
import network.data.NetworkState;
import network.data.ReceivedJob;
import network.data.Server;

/**
 * This class takes an Environment in the constructor, and from that, sends out jobs to other nodes in the cluster that can run the jobs. 
 * If you give it a map of classes (<String, byte[]>) it will also be able to send that out to the other nodes in the cluster, and from that send jobs whose class at compile-time was unknown to the receiver. 
 * 
 * In general, once you initialize and start() and object from this class, it handles itself. 
 * 
 * Note: Closing it using close() will sometimes fail it there has passed less that 3 seconds from start() was called. 
 * 
 * @author Erik Krogh Kristensen
 *
 * @param <E>
 */
public class ClusterHandler<E> implements Runnable, MessageReciever {
	private Environment<E> env; // The Environment that i interact with. 
	private ServerList serverList; // The ServerList i use to keep a reference to all the other servers in the cluster (and to discover them). 
	private CountDownLatch serverListReady;
	
	private Thread sendThread; // The thread that just keeps sending out jobs to everyone else. 
	
	private Map<Server, List<WorkContainer<E>>> sentJobs; // A map holding all the jobs that have been send in a map with the Address they were send to as the key.
	
	private Map<Long, ReceivedJob<E>> receivedJobs; // A map of all the jobs that has been received. Mostly used when sending a received job to another client.  
	
	private boolean reportAvailable; // A boolean variable telling whether or not this node should tell the other nodes in the cluster that it is available. 
	
	private AtomicInteger activeOutsideJobs; // This is how many jobs we have accepted from the outside that is currently running. 
	private int maxActiveOutsideJobs; // READ THE NAME!
	
	private boolean safeMode; // safeMode determines from where in the queue jobs to the other clients should be taken. 
	
	private ClassLoader customClassLoader = null;
	
	private Map<String, byte[]> classes; // A map over the names and the bytes of the classes that need to be sent to the other nodes. 
	
	/**
	 * This is the most simple constructor. The Environment is obviously needed, and reportAvailable tells whether or not this node should accept any jobs (tells the others that it is available.)
	 * @param env The Environment that this node works on. 
	 * @param reportAvailable Whether or not this node should tell the others that it can do some work. 
	 */
	public ClusterHandler(Environment<E> env, boolean reportAvailable)
	{
		this(env, reportAvailable, reportAvailable ? 3 : 0);
	}
	/**
	 * The Environment is obviously needed, and reportAvailable tells whether or not this node should accept any jobs (tells the others that it is available.). 
	 * 
	 * The maxJobs parameter is how many remote jobs should at the most be accepted at any one time. The default value is 3. 
	 * 
	 * @param env The Environment that this node works on. 
	 * @param reportAvailable Whether or not this node should tell the others that it can do some work. 
	 * @param maxJobs How many remote jobs this node at the most should accept. 
	 */
	public ClusterHandler(Environment<E> env, boolean reportAvailable, int maxJobs)
	{
		this(env, reportAvailable, maxJobs, null);
	}
	/**
	 * The Environment is obviously needed, and reportAvailable tells whether or not this node should accept any jobs (tells the others that it is available.). 
	 * 
	 * The maxJobs parameter is how many remote jobs should at the most be accepted at any one time. The default value is 3. 
	 * 
	 * safeMode is by default off, if turned on (setting the parameter to true) this node will try to send out very small jobs, instead of the big jobs that it per default tries to send out. 
	 * If setting this to true, any computation involving a lot of individual jobs will most likely take a lot longer time (i tested it, up to 50% slower with 3 nodes). 
	 * It is however called safeMode, because if any of the other nodes crashes, it does not mean a lot, since all results are returned very quickly.  
	 * 
	 * @param env The Environment that this node works on. 
	 * @param reportAvailable Whether or not this node should tell the others that it can do some work. 
	 * @param maxJobs How many remote jobs this node at the most should accept. 
	 * @param safeMode Default value is false. If set to true, the cluster will send much smaller jobs much much more frequently, thereby reducing the average size of jobs that a client at any time has in its possession. 
	 */
	public ClusterHandler(Environment<E> env, boolean reportAvailable, int maxJobs, Map<String, byte[]> classes )
	{
		this(env, reportAvailable, maxJobs, classes, false);
	}
	/**
	 * The Environment is obviously needed, and reportAvailable tells whether or not this node should accept any jobs (tells the others that it is available.). 
	 * 
	 * The maxJobs parameter is how many remote jobs should at the most be accepted at any one time. The default value is 3. 
	 * 
	 * safeMode is by default off, if turned on (setting the parameter to true) this node will try to send out very small jobs, instead of the big jobs that it per default tries to send out. 
	 * If setting this to true, any computation involving a lot of individual jobs will most likely take a lot longer time (i tested it, up to 50% slower with 3 nodes). 
	 * It is however called safeMode, because if any of the other nodes crashes, it does not mean a lot, since all results are returned very quickly.  
	 * 
	 * @param env The Environment that this node works on. 
	 * @param reportAvailable Whether or not this node should tell the others that it can do some work. 
	 * @param maxJobs How many remote jobs this node at the most should accept. 
	 * @param safeMode Default value is false. If set to true, the cluster will send much smaller jobs much much more frequently, thereby reducing the average size of jobs that a client at any time has in its possession.
	 */
	public ClusterHandler(Environment<E> env, boolean reportAvailable, int maxJobs, Map<String, byte[]> classes, boolean safeMode)
	{
		this.env = env;
		this.reportAvailable = reportAvailable;
		
		this.sentJobs = new HashMap<Server, List<WorkContainer<E>>>();
		
		this.receivedJobs = new HashMap<Long, ReceivedJob<E>>();
		
		this.activeOutsideJobs = new AtomicInteger();
		this.maxActiveOutsideJobs = maxJobs;
		
		this.sendThread = new Thread(this);
		
		new ReentrantLock();
		
		this.safeMode = safeMode;
		this.classes = classes;
		if (this.classes == null)
		{
			this.classes = new HashMap<String, byte[]>();
		}
		
		// The serverlist is set up later, and that opens up this latch, so i can call stuff that needs the serverlist, before its ready. 
		this.serverListReady = new CountDownLatch(1);
	}
	/**
	 * A private method that returns a ActionListener that is called when a server is removed/disappears from the network. 
	 * 
	 * This method should only be called once, because it doesn't cache the value. (It returns a new ActionListener() each time). 
	 * 
	 * @return a the ActionListener that is called when a server is removed/disappears from the network. 
	 */
	private ActionListener getDeletedServerListener()
	{
		return new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				Server address = (Server)e.getSource();
				serverCrashed(address);
			}
		};
	}
	/**
	 * Starts the cluster. 
	 */
	public void start()
	{
		sendThread.start();
	}
	/**
	 * Closes the cluster, if the cluster has not yet started completely, this may not work. 
	 * Its recommended to close the cluster at least 3 seconds after it was started, to make sure that it is able to shut down correctly. 
	 */
	public void close()
	{
		sendThread.interrupt();
		
		// Waiting until the serverList is ready. 
		try {
			serverListReady.await();
		} catch (InterruptedException ignored) {}
		serverList.close();
	}
	/**
	 * Puts a class in the CluserHandler, so it can send it to another server if needed. 
	 * @param name
	 * @param bytes
	 */
	public void addToClasses(String name, byte[] bytes)
	{
		classes.put(name, bytes);
	}
	/**
	 * This methods returns a classLoader that can translate the objects that belongs to the classes that is contained within the SharedStateMap. 
	 * @return A ClassLoader that can translate the objects that belongs to the classes that is contained within the SharedStateMap.
	 */
	private ClassLoader getClassloader()
	{
		if (customClassLoader == null)
		{
			customClassLoader = new RuntimeClassLoader(serverList.getSharedState());
		}
		return customClassLoader;
	}
	/**
	 * This method is continuously run in its own thread. It basically just waits for an server to become available, and then send a job to it. 
	 * It begins by setting up and starting the ServerList. 
	 */
	@SuppressWarnings("static-access")
	@Override
	public void run() {
		/*
		 * Setting up the serverList. 
		 */
		this.serverList = new ServerList(this, reportAvailable);
		this.serverList.setDeletedServerListener(getDeletedServerListener());
		
		try {
			// Starting it. 
			this.serverList.start();
		} catch (Exception e1) {
			// Really not supposed to happen.
			// Interrupting this thread makes sure that we do not proceed to send any jobs to any other client (since the below while-loop never runs). 
			Thread.currentThread().interrupt();
			e1.printStackTrace();
		}
		
		// Telling the others that the serverList is now ready. 
		serverListReady.countDown();
		
		/*
		 * This thread just keeps on going.   
		 */
		while(!Thread.currentThread().isInterrupted())
		{
			Server server;
			// The getAvailableServer() method is a blocking method, so we have no idea of when it is going to return anything. 
			if ((server = serverList.getAvailableServer()) != null)
			{
				WorkContainer<E> work = null;
				try {
					// safeMode is described in the constructor.
					// Both of the below methods are blocking methods, so we have no idea of when they return. 
					if (safeMode)
					{
						// Gets a job thats far down in the recursion tree. 
						work = env.getLocalJobFromQueue();
					}
					else
					{	
						// Gets a job that is far up in the recursion tree. 
						work = env.getRemoteJobFromQueue();
					}
				} catch (InterruptedException e) {
					// Perfectly normal. 
					Thread.currentThread().interrupt();
					break;
				}
				

				// Checking if this job was received from someone else. It is null if it wasn't received from the outside.  
				ReceivedJob<E> received;
				synchronized(receivedJobs)
				{
					received = receivedJobs.get(work.getId());
				}
				
				/*
				 * Before actually sending them there are 2 things that could make me change my mind. 
				 * 1: The server is no longer available. This can happen because getting the job can take a long time. 
				 * 2: The job we are trying to send is a job that we received from someone else, so no point in wasting more bandwidth by passing it on. 
				 */
				if (serverList.getServerState(server) != NetworkState.MemberState.AVAILABLE || received != null)
				{
					try {
						// I didn't send it, i keep it.
						env.putJobInQueue(work);
						// Making sure that i can send another job to that server.
						serverList.didntUseAvailableServer(server);
						// Sleeping a while, it happens that i keep trying to send the same thing to the same server.
						Thread.currentThread().sleep(100);  
					} catch (InterruptedException e) {
						// Making sure that it actually terminates. 
						Thread.currentThread().interrupt();
					}
				}
				else
				{
					System.out.println("Send work: " + work.getId() + " to " + server);
					synchronized(sentJobs)
					{
						// First of all doing some more checking. And the second condition in the if actually sends the job. 
						if (work.getStatus() == WorkContainer.Status.RUNNABLE && SendToServer(work, server))
						{
							// So now the work has been send, we update our own lists to reflect that.  
							List<WorkContainer<E>> list = sentJobs.get(server);
							if (list == null)
							{
								list = new ArrayList<WorkContainer<E>>();
								list.add(work);
								sentJobs.put(server, list);
							}
							else
							{
								list.add(work);
							}
						}
						else
						{
							// This is not supposed to happen, but it did. Printing out some stuff. 
							if (work.getStatus() != WorkContainer.Status.RUNNABLE)
							{
								System.out.println("Failed to send job because status is: " + work.getStatus());
							}
							else
							{
								System.out.println("Failed to send job, i think the network connection failed us sire.");
							}
							try {
								// I didn't send it, i keep it.
								env.putJobInQueue(work);
								// Making sure that i can send another job to that server.
								serverList.didntUseAvailableServer(server);
								// Sleeping a while, it happens that i keep trying to send the same thing to the same server.
								Thread.currentThread().sleep(100); 
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
							}
						}
					}
				}
			}
		}
	}
	/**
	 * This method is used to send an object to a server, if it is a job, result or whatever. 
	 * In some cases, the method will recognize the object, and based on that encapsulate it in a BytePackage. 
	 * @param obj The object to be sent. 
	 * @param server The server it should be sent to. 
	 * @return Whether or not the object was successfully sent. 
	 */
	@SuppressWarnings("rawtypes")
	private boolean SendToServer(Object obj, Server server)
	{
		// First i need to know that i can do this. 
		try {
			serverListReady.await();
		} catch (InterruptedException ignored) {}
		
		/*
		 * Before sending i see if the package should be sent as a byte-array instead.
		 * I go through all the things that i can translate and if the translation to bytes is successful, i send that BytePackage instead. 
		 */
		if (obj instanceof WorkContainer)
		{
			WorkContainer work = (WorkContainer)obj;
			try {
				obj = new BytePackage(BytePackage.Type.WORK, NetworkTools.objectToBytes(obj), work.getId());
			} catch (IOException ignore) {
				// Since the obj hasn't been overwritten, it will still attempt to send the original obj. 
			}
		}
		else if (obj instanceof Result)
		{
			Result res = (Result)obj;
			try {
				obj = new BytePackage(BytePackage.Type.RESULT, NetworkTools.objectToBytes(obj), res.getID());
			} catch (IOException ignore) {
				// Since the obj hasn't been overwritten, it will still attempt to send the original obj. 
			}
		}
		return serverList.SendToServer(obj, server, null);
	}
	/**
	 * This method is called by the ServerList whenever it receives something that it does know what it. 
	 * This can either be a WorkContainer or a Result. 
	 */
	@SuppressWarnings({ "rawtypes" }) // Using generics, because its useful. Then completely abusing it beyond anything it was designed for, because thats useful too. 
	@Override
	public void Recieve(Object obj, final Server sender) {
		if (obj instanceof BytePackage)
		{
			BytePackage pack = (BytePackage)obj;
			if (pack.getType() == BytePackage.Type.WORK)
			{
				try {
					// Translating the object the fancy way. 
					WorkContainer work = (WorkContainer)NetworkTools.bytesToObject(pack.getBytes(), getClassloader());
					// I now have some work, lets handle it. 
					receiveWork(work, sender);
				} catch (Exception e) {
					// Some kind of exception, so i send that back. 
					System.out.println("Couldn't understand the work, sending a repport back to the sender");
					// Sending back the exception
					SendToServer(new ClusterMessage(ClusterMessage.Message.WORK_CLASSEXCEPTION, pack.getId()), sender);
					// But i'm still available. 
					SendToServer(NetworkState.MemberState.AVAILABLE, sender);
				}
			}
			else if (pack.getType() == BytePackage.Type.RESULT)
			{
				try {
					// Translating the object the fancy way. 
					Result res = (Result)NetworkTools.bytesToObject(pack.getBytes(), getClassloader());
					// I have a result, lets pass it on. 
					receiveResult(res, sender);
				} catch (Exception e) {
					// Some kind of exception, so i send that back.
					System.out.println("Couldn't understand the result, sending a repport back to the sender");
					SendToServer(new ClusterMessage(ClusterMessage.Message.RESULT_CLASSEXCEPTION, pack.getId()), sender);
					
					// And i also make sure to recover the job, or else it would just be lost. 
					System.out.println("And recovering the job");
					recoverJob(pack.getId(), sender);
				}
			}
		}
		// This shouldn't be called, ever. But i still keep it in case something weird happens to the sender, because then we do risk that they send an WorkContainer. 
		else if (obj instanceof WorkContainer)
		{
			System.out.println("A WorkContainer was send, that's not supposed to happen!");
			WorkContainer work = (WorkContainer)obj;
			receiveWork(work, sender);
		}
		// This shouldn't be called, ever. But i still keep it in case something weird happens to the sender, because then we do risk that they send a Result. 
		else if (obj instanceof Result)
		{
			System.out.println("A Result was send, that's not supposed to happen!");
			Result result = (Result)obj;
			receiveResult(result, sender);
		}
		// Receiving a message. This cannot be in a BytePackage, so this code is here to stay. 
		else if (obj instanceof ClusterMessage)
		{
			ClusterMessage message = (ClusterMessage)obj;
			receiveMessage(message, sender);
		}
	}
	/**
	 * This method is used to recover the jobs, that for some reason was sent to, but not completed by the receiver.  
	 * @param id The id of the Job. From this the job is found in the sent jobs. 
	 * @param sender The address of the node that wasn't able to complete the job. 
	 */
	private void recoverJob(long id, Server sender)
	{
		synchronized(sentJobs)
		{
			List<WorkContainer<E>> workList = sentJobs.get(sender);
			Iterator<WorkContainer<E>> workIterator = workList.iterator();
			while(workIterator.hasNext())
			{
				WorkContainer<E> work = workIterator.next();
				if (work.getId() == id)
				{
					System.out.println("Recovering " + work);
					workIterator.remove();
					try {
						env.putJobInQueue(work);
					} catch (InterruptedException ignored) {/* Should really not happen */}
				}
			}
			if (workList.size() == 0)
			{
				sentJobs.remove(sender);
			}
		}
	}
	/**
	 * This method is called whenever we receive an WorkContainer from another client. It makes sure that it is called, and that an result is returned to the sender when it has completed. 
	 * @param work The WorkContainer that was send. 
	 * @param sender Who it was send from. Mostly used to send the result back. 
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void receiveWork(final WorkContainer work, final Server sender)
	{
		// Saving the id the job had on the machine it came from. This is useful when sending back the result.  
		final long prevID = work.getId();
		// For us to handle it here, the job needs an id that is unique on this machine. 
		final long newID = env.getIncrementedLocalId();
		// Setting the new ID. 
		work.setId(newID);
		// Part of the contract with WorkContainer is that these 2 methods are called whenever it reaches another client, so we do that. 
		work.resetInvokeListener(); // This one because an instance of an anonymous class can't be serialized. 
		work.setEnvironment(this.env); // It is obvious that the environment can't and shouldn't be sent over the air. 
		
		// Used when sending back the result. 
		final long ParentID = work.getParentId();
		// This sets the parentId inside to work to 0, this makes sure that when completed, the result will not hit any idleJob, but it will reach us through the callback we specified. 
		work.resetParentId();
		// Keeping track of the work we sent, in case the client crashes or can't complete the job. 
		synchronized(receivedJobs)
		{
			receivedJobs.put(newID, new ReceivedJob(work, prevID, sender));
		}
		
		// System.out.println("Got work: " + prevID);
		try {
			// Setting the callback first
			env.callWhenFinalResultDone(newID, new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent arg) {
					synchronized(receivedJobs)
					{
						receivedJobs.remove(newID); // Now it is finally gone.
					}
					
					// Always the most important stuff first. So first i send back the result. 
					//System.out.println("Sending back: " + prevID);
					Result res = new Result(prevID, work.getParentJobId(), ParentID, arg.getSource());
					SendToServer(res, sender);
					
					// This see if we need to tell the world that we are now open for business. 
					int activeJobs = activeOutsideJobs.decrementAndGet() + 1; // Counting down the number of active jobs, and caching the value. 
					if (activeJobs == maxActiveOutsideJobs)
					{
						try {
							// Telling that world that we are not busy. 
							serverList.isBusy(false);
						} catch (Exception ignored) {}
					}
					
				}
			});
			
			// THEN putting the work in the queue. 
			env.putJobInQueue(work);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			// Yeah, not supposed to happen. 
			e.printStackTrace();
		}
		
		// I send back an signal to the server that send the job, that i'm still available, if i havn't received more than the threshold allows. 
		int activeJobs = activeOutsideJobs.incrementAndGet();  // Just a little cache. 
		if (activeJobs < maxActiveOutsideJobs)
		{
			try {
				serverList.sendStillAvailable(sender);
			} catch (Exception ignore) {}
		}
		// if i can't accept any more jobs, i send out that i'm busy. 
		else if (activeJobs == maxActiveOutsideJobs)
		{
			try {
				serverList.isBusy(true);
			} catch (Exception ignored) {}
		}
	}
	/**
	 * This method is called whenever a result comes in from another node in the cluster. 
	 * @param result The result that was sent from the node. 
	 * @param sender The address of the node that sent the result to us. (Makes it easier to find). 
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void receiveResult(Result result, Server sender) {
		// Removes the job from the map of jobs.
		synchronized(sentJobs)
		{
			List<WorkContainer<E>> workList = sentJobs.get(sender);
			for(WorkContainer<E> work : workList)
			{
				if (work.getId() == result.getID())
				{
					workList.remove(work);
					if (workList.size() == 0)
					{
						sentJobs.remove(sender);
					}
					break;
				}
			}
		}
		System.out.println("Got result " + result.getID() + "(" + result.getJobID() + ") from " + sender);
		env.submitResult(result);
	}
	/**
	 * Receives a message from another server in the cluster. 
	 * This could be that one of my jobs should be aborted (shutdown), or that the job they received could not be interpreted.   
	 * @param message The message that was sent from another server in the cluster. 
	 * @param sender The address of the server that sent the message. 
	 */
	private void receiveMessage(ClusterMessage message, Server sender) {
		if (message.getMessage() == ClusterMessage.Message.ABORT)
		{
			// We have been told that we should abort a single job, that was send from the sender. So lets first find all the jobs that they have sent to us. 
			long id = message.getId(); // This is the id it had on its own machine.
			synchronized(receivedJobs)
			{
				for (Entry<Long, ReceivedJob<E>> entry : receivedJobs.entrySet())
				{
					ReceivedJob<E> received = entry.getValue();
					// If address equals the one we got the message from.
					if (received.getAddress().equals(sender))
					{
						// If the foreign id match.
						if (received.getOrgId() == id)
						{
							// Now we have our job. 
							received.getWork().abort();
							// No need to continue. 
							break;
						}
					}
				}
			}
			// Cleaning up. 
			abortCleanup(null);
		}
		else if (message.getMessage() == ClusterMessage.Message.WORK_CLASSEXCEPTION)
		{
			// A job couldn't be translated by the far side. 
			// We just put the job back in the queue, and send the classes that could fix the issue. 
			for (Entry<String, byte[]> entry : classes.entrySet())
			{
				serverList.sendStateEntry(entry.getKey(), entry.getValue());
			}
			System.out.println("Got a WORK_CLASSEXCEPTION recovering");
			recoverJob(message.getId(), sender);
		}
		else if (message.getMessage() == ClusterMessage.Message.RESULT_CLASSEXCEPTION)
		{
			System.out.println("Got a RESULT_CLASSEXCEPTION back. I have no idea what to do about that.");
		}
	}
	/**
	 * This method is called whenever a server crashes, it makes sure to make a proper recovery on this node. 
	 * @param server the server that crashed. 
	 */
	private synchronized void serverCrashed(Server server)
	{
		/*
		 * There are two parts to this. 
		 * 1: We need to recover those jobs that we send out to the crashed client. 
		 * 2: Abort those jobs that we got from him. 
		 */
		
		/*
		 * 1: Recover the jobs that we send to him. 
		 */
		// Doing a little workaround to make a thread safe loading of a final. 
		// We are getting the jobs that we have sent to the client. 
		List<WorkContainer<E>> removedJobsTmp;
		synchronized (sentJobs)
		{
			removedJobsTmp = sentJobs.get(server);
		}
		final List<WorkContainer<E>> removedJobs = removedJobsTmp;
		
		if (removedJobs == null || removedJobs.size() == 0)
		{
			// No need to to anything, since we didn't send anything to the server. 
			System.out.println("Removed: " + server);
		}
		else
		{
			
			System.out.println(server + " crashed, recovering " + removedJobs.size() + " jobs.");
			// It's simple, i just put the jobs back in the queue.  
			for (WorkContainer<E> work : removedJobs)
			{
				// I know that this is O(n^2), and is easy to do in O(n), but for simplicity i keep it like this. 
				recoverJob(work.getId(), server);
			}
		}
		
		/*
		 * 2: Abort the jobs that we got from him.
		 */
		boolean needCleanUp = false; 
		synchronized(receivedJobs)
		{
			for (Entry<Long, ReceivedJob<E>> entry : receivedJobs.entrySet())
			{
				ReceivedJob<E> received = entry.getValue();
				// If address equals the one we got the message from.
				if (received.getAddress().equals(server))
				{
					// Now we have our job. 
					received.getWork().abort(); // This method also calls abort() to all the subJobs further down the recursion tree.
					// There may be many jobs that needs to be aborted, but we only need to clean it all once. 
					needCleanUp = true;
				}
			}
		}
		if (needCleanUp)
		{
			System.out.println("Aborting jobs recieved from crashed server");
			// Cleaning up. 
			abortCleanup(server);
		}
	}
	/**
	 * This method is called whenever you all the local jobs have been to the status == abort. 
	 * The only thing left to do is to make sure that the stuff we have sent out, gets cancelled.
	 * The server parameter is if it is a specific server that has crashed, and we need to remove every trace of that. The parameter can safely be null, if it is not a specific server that has crashed. 
	 * @param server the server that has crashed (or null if no specific server has crashed). 
	 */
	private void abortCleanup(final Server crashedServer)
	{
		// Doing this all in a new thread, since it can do quite some blocking, and it can take a lot of time. 
		new Thread(new Runnable(){
			@Override
			public void run() {
				// Nothing to do other than just going through everything. 
				// Starting by going through all the sentJobs, if any of them were aborted, the receiver should know. 
				// Not sending the messages right now, since we have a lock on sentJobs. 
				Map<ClusterMessage, Server> messages = new HashMap<ClusterMessage, Server>();
				synchronized(sentJobs)
				{
					/*
					 * Going through all sent jobs, if any of them is no longer RUNNABLE, we tell the server that received the job, that it has been aborted. /
					 */
					Iterator<Entry<Server, List<WorkContainer<E>>>> sentJobsIterator = sentJobs.entrySet().iterator();
					while (sentJobsIterator.hasNext())
					{
						// Getting a list and a iterator of the work associated with this server. 
						Entry<Server, List<WorkContainer<E>>> entry = sentJobsIterator.next();
						List<WorkContainer<E>> workList = entry.getValue();
						Iterator<WorkContainer<E>> workIterator = workList.iterator();
						while(workIterator.hasNext())
						{
							WorkContainer<E> work = workIterator.next();
							if (work.getStatus() != WorkContainer.Status.RUNNABLE)
							{
								// messages.put(message, server); 
								messages.put(new ClusterMessage(ClusterMessage.Message.ABORT, work.getId()), entry.getKey());
								workIterator.remove();
							}
						}
						// If we removed everything, we remove the list. 
						if (workList.size() == 0)
						{
							sentJobsIterator.remove();
						}
					}
					if (crashedServer != null)
					{
						sentJobs.remove(crashedServer);
					}
				}
				// Sending those messages. 
				for (Entry<ClusterMessage, Server> entry : messages.entrySet())
				{
					SendToServer(entry.getKey(), entry.getValue());
				}
				// Cleaning the receivedJobs.
				// The only thing i do here, is deleting jobs that have been aborted. 
				synchronized(receivedJobs)
				{ 
					Iterator<Entry<Long, ReceivedJob<E>>> receivedJobsIterator = receivedJobs.entrySet().iterator();
					// I can't just use the for(E e: collection), because i need to remove stuff. 
					while(receivedJobsIterator.hasNext())
					{
						Entry<Long, ReceivedJob<E>> entry = receivedJobsIterator.next();
						if (entry.getValue().getWork().getStatus() == WorkContainer.Status.ABORTED)
						{
							receivedJobsIterator.remove();
						}
					}
					
				}
				
				// Cleaning up the idle jobs. 
				for (Entry<Long, WorkContainer<E>> entry : env.getIdleJobs().entrySet())
				{
					if (entry.getValue().getStatus() == WorkContainer.Status.ABORTED)
					{
						env.removeIdleJob(entry.getKey());
					}
				}
				
				
			}
		}, "Abort cleanup thread").start();
		
	}
	@Override
	public String toString()
	{
		return "Received jobs: " + receivedJobs + "\nSent Jobs: " + sentJobs;
	}
}
