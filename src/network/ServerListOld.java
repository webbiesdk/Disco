package network;

import java.util.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.UUID;

public class ServerListOld {
	public static void main(String[] args) {
		final ServerListOld list = new ServerListOld(4000, 4001);
		list.addAddedIpListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				System.out.println("Added: " + e.getSource().toString().substring(1));
				System.out.println("Current list: " + list.getList());
			}
		});
		list.addDeletedIpListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				System.out.println("Deleted: " + e.getSource().toString().substring(1));
				System.out.println("Current list: " + list.getList());
			}
		});
		list.start();
	}
	// For the internal work
	private Thread listenThread;
	private Thread announcerThread;
	private ArrayList<InetAddress> list;
	private String LocalIdentifier;
	private String keppAliveString = "Still there? ";
	private String answerString = "I'm here ";
	private String removeMeString = "Plz remove me from your list ";
	// The listeners.
	private ArrayList<ActionListener> addedIpListeners;
	private ArrayList<ActionListener> deletedIpListeners;
	// The ports
	private int announcePort;
	private int listenPort;
	// A shutdownhook
	private Thread shutDownHook;
	public ServerListOld(final int announcePort, final int listenPort)
	{
		// So the other methods can benefit from nowing where to send stuff.
		this.announcePort = announcePort;
		this.listenPort = listenPort;
		list = new ArrayList<InetAddress>();
		
		// Making the LocalIdentifier
		try {
			LocalIdentifier = InetAddress.getLocalHost().toString() + UUID.randomUUID().toString();
		} catch (UnknownHostException e) {
			// This should never happen, maybe if there is not network card installed on the machine, but then this code makes no sence. 
			e.printStackTrace();
		}		
		
		// Making sure that i listen when other servers try to tell me where they are. 
		listenThread = new Thread(new Runnable() {
			@Override
			public void run() {
				listen(announcePort, listenPort);
			}
		});
		
		/*
		 * First i tell everyone IM HERE! (anounce()).
		 * Then i keep checking if the others are still there (keepAlive())
		 * And every now and then, i tell everyone that IM HERE!
		 */
		announcerThread = new Thread(new Runnable() {
			@SuppressWarnings("static-access")
			@Override
			public void run() {
				int count = 0;
				anounce(announcePort, listenPort);
				while (!Thread.currentThread().isInterrupted()) {
					try {
						Thread.currentThread().sleep(5000);
						keepAlive(announcePort, listenPort);
						count = (count + 1) % 7;
						if (count == 0)
						{
							anounce(announcePort, listenPort);
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			}
		});
		
		// Finally, making the lists to hold the callbacks.
		addedIpListeners = new ArrayList<ActionListener>();
		deletedIpListeners = new ArrayList<ActionListener>();
		
		shutDownHook = new Thread(new Runnable(){
			@Override
			public void run() {
				close();
			}
		});
	}
	// Since stuff here sometimes works out of sync, its important we only begin doing stuff after listeners etc. has been added. 
	public void start()
	{
		listenThread.start();
		announcerThread.start();
		Runtime.getRuntime().addShutdownHook(shutDownHook);
	}
	public synchronized void addAddedIpListener(ActionListener listener)
	{
		addedIpListeners.add(listener);
	}
	public synchronized void addDeletedIpListener(ActionListener listener)
	{
		deletedIpListeners.add(listener);
	}
	public synchronized void removeAddedIpListener(ActionListener listener)
	{
		if (addedIpListeners.contains(listener))
		{
			addedIpListeners.remove(listener);
		}
	}
	public synchronized void removeDeletedIpListener(ActionListener listener)
	{
		if (deletedIpListeners.contains(listener))
		{
			deletedIpListeners.remove(listener);
		}
	}
	private int eventCount = 0; // Giving a unique id to each event. 
	private synchronized void runAddedIp(final InetAddress ip)
	{
		eventCount++;
		final int eventCountLocal = eventCount;
		@SuppressWarnings("unchecked")
		final ArrayList<ActionListener> localList = (ArrayList<ActionListener>) addedIpListeners.clone(); // Avoiding concurrentModificationException. 
		// I really do not now how much work is in an ActionListener, so i start it in a new thread. 
		new Thread(new Runnable(){
			@Override
			public void run() {
				for (ActionListener action : localList)
				{
					action.actionPerformed(new ActionEvent(ip, eventCountLocal, "Added server: " + ip));
				}
			}
		}).start();
	}
	private synchronized void runDeletedIp(final InetAddress ip)
	{
		eventCount++;
		final int eventCountLocal = eventCount;
		@SuppressWarnings("unchecked")
		final ArrayList<ActionListener> localList = (ArrayList<ActionListener>) deletedIpListeners.clone(); // Avoiding concurrentModificationException. 
		// I really do not now how much work is in an ActionListener, so i start it in a new thread. 
		new Thread(new Runnable(){
			@Override
			public void run() {
				for (ActionListener action : localList)
				{
					action.actionPerformed(new ActionEvent(ip, eventCountLocal, "This server disappeared: " + ip));
				}
			}
		}).start();
		
	}
	private synchronized void addToList(InetAddress address)
	{
		if (!list.contains(address))
		{
			list.add(address);
			runAddedIp(address);
		}
	}
	private synchronized void removeFromList(InetAddress address)
	{
		if (list.contains(address))
		{
			list.remove(address);
			runDeletedIp(address);
		}
	}
	@SuppressWarnings("unchecked")
	public synchronized List<InetAddress> getList()
	{
		return (List<InetAddress>) list.clone();
	}
	@Override
	public void finalize() throws Throwable
	{
		try {
	        close();
	    }
	    finally {
	        super.finalize();
	    }
	}
	public void close()
	{
		// When i close the sockets, the thread continues, so i need to interrupt them first. 
		listenThread.interrupt();
		announcerThread.interrupt();
		listenSocket.close();
		announceSocket.close();
		try 
		{
			Runtime.getRuntime().removeShutdownHook(shutDownHook);
		}
		catch (IllegalStateException e)
		{
			// This happens when the system is shutting down. 
		}
		signOff(announcePort, listenPort);
	}
	private void signOff(int announcePort, int listenPort) {
		InetAddress serverAddress = null;
		try {
			serverAddress = InetAddress.getByName("255.255.255.255");
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			announceSocket = new DatagramSocket(announcePort); // I now that this one is closed now. 

			// Send PING.
			byte[] sendPackage = (removeMeString + LocalIdentifier).getBytes();
			DatagramPacket reply = new DatagramPacket(sendPackage,sendPackage.length, serverAddress, listenPort);
			announceSocket.send(reply);
			announceSocket.close();
		}  catch (SocketException e) {
			// This happens when the socket is closed by .close(). So the only thing i have to do, is getting out of this. 
			if (!Thread.currentThread().isInterrupted()) // But in case that wasn't what happened, print error data. 
			{
				e.printStackTrace();
			}
		} catch (IOException e) {
			// yahla yahla yahla.
			e.printStackTrace();
		}
	}
	private DatagramSocket announceSocket;
	private void keepAlive(int announcePort, int listenPort) {
		@SuppressWarnings("unchecked")
		ArrayList<InetAddress> clone = (ArrayList<InetAddress>)list.clone(); // To avoid concurrent exceptions. And besides, if a new server is added after i clone this list, it doesn't need a keepalive just now.  
		for (InetAddress address : clone)
		{
			try {
				announceSocket = new DatagramSocket(announcePort);
				// Send PING.
				byte[] sendPackage = (keppAliveString + LocalIdentifier).getBytes();
				DatagramPacket reply = new DatagramPacket(sendPackage, sendPackage.length, address, listenPort);
				announceSocket.send(reply);
				
				// Now we need to wait for a reply.
				DatagramPacket answer = new DatagramPacket(new byte[1024], 1024);
				try {
					announceSocket.setSoTimeout(200);
					announceSocket.receive(answer);
				}
				catch (SocketTimeoutException e)
				{
					// This means that the server didn't respond in time. So i remove it from the list. 
					removeFromList(address);
				}
				finally 
				{
					announceSocket.close();
				}
			} catch (SocketException e) {
				// This happens when the socket is closed by .close(). So the only thing i have to do, is getting out of this. 
				if (!Thread.currentThread().isInterrupted()) // But in case that wasn't what happened, print error data. 
				{
					e.printStackTrace();
				}
			} catch (IOException e) {
				// Another case of this shouldn't happen. 
				e.printStackTrace();
			}
			if (Thread.currentThread().isInterrupted()) // Don't wanna hang around if i don't have to. 
			{
				break;
			}
		}
	}
	private void anounce(int announcePort, int listenPort) {
		InetAddress serverAddress = null;
		try {
			serverAddress = InetAddress.getByName("255.255.255.255");
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			announceSocket = new DatagramSocket(announcePort);

			// Send PING.
			byte[] sendPackage = ("Is anyone there? " + LocalIdentifier).getBytes();
			DatagramPacket reply = new DatagramPacket(sendPackage,sendPackage.length, serverAddress, listenPort);
			announceSocket.send(reply);
			announceSocket.close();
		}  catch (SocketException e) {
			// This happens when the socket is closed by .close(). So the only thing i have to do, is getting out of this. 
			if (!Thread.currentThread().isInterrupted()) // But in case that wasn't what happened, print error data. 
			{
				e.printStackTrace();
			}
		} catch (IOException e) {
			// yahla yahla yahla.
			e.printStackTrace();
		}
	}
	private DatagramSocket listenSocket;
	private void listen(int announcePort, int listenPort) {
		byte[] sending = (answerString + LocalIdentifier).getBytes();
		listenSocket = null;
		try {
			listenSocket = new DatagramSocket(listenPort);
		} catch (SocketException e) {
			// Aha got you, but... Well, shouldn't happen. 
			e.printStackTrace();
			// Well, it can happen, but then it is an real error. 
		}

		// While true in multithread lang. 
		while (!Thread.currentThread().isInterrupted()) {
			// Create a datagram packet to hold incomming UDP packet.
			DatagramPacket request = new DatagramPacket(new byte[1024], 1024);

			try {
				// Block until the host receives a UDP packet.
				listenSocket.receive(request);
				/*
				 * There many possible scenarioes, 2 of wich we react to.
				 * 1. If it is a keepalive request, reply back to same port. 
				 * 2. If it is not an answer, reply with an answer. 
				 * The first excludes the second.
				 * And none of them are run of its out own machine that sent it. 
				 */
				String received = new String(request.getData());
				if (!received.contains(LocalIdentifier)) // Did this machine send this request. (Happens all the time, that is what happens when you send to 255.255.255.255). 
				{
					if (received.startsWith(keppAliveString)) // Sending a keepalive answer, back to same port. 
					{
						InetAddress clientHost = request.getAddress();
						int clientPort = request.getPort();
						DatagramPacket reply = new DatagramPacket(sending, sending.length,clientHost, clientPort);
						listenSocket.send(reply);
					}
					else if (!received.startsWith(answerString)) // Sending an answer to a "are you there" request, to the listen port. (the announcer does not listen).  
					{
						InetAddress clientHost = request.getAddress();
						DatagramPacket reply = new DatagramPacket(sending, sending.length,clientHost, listenPort);
						listenSocket.send(reply);
						
					}
					// Now, is he signing off?
					if (received.startsWith(removeMeString))
					{
						removeFromList(request.getAddress());
					}
					else
					{
						addToList(request.getAddress());
					}
				}
			} catch (SocketException e) {
				// This happens when the socket is closed by .close(). So the only thing i have to do, is getting out of this. 
				if (!Thread.currentThread().isInterrupted()) // But in case that wasn't what happened, print error data. 
				{
					e.printStackTrace();
				}
			}
			catch (IOException e) {
				// Getting tired of writing stupid "this should never" happen catch comments. 
				e.printStackTrace();
			}
			
		}
	}
}
