package network;
import network.data.NetworkState;
import network.data.StateEntry;
import network.data.NetworkState.MemberState;

import org.jgroups.*;
import org.jgroups.util.Util;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ServerList implements Receiver {
	MessageReciever receiver; // The receiver that is called whenever this class doesn't know what to do with an received object.  
	ActionListener deletedServerListener = null; // The listener that is called whenever a server is removed from the cluster.
	boolean reportAvailable; // Whether or not this instance when started should say it is available, or busy. 
	
    JChannel channel; // The JChannel that i use to communicate. 
    final NetworkState state;// // Holding the shared state in the cluster. 
    
    Map<String, byte[]> thisNodesSharedStateContribution; // Well, the name says almost everything. This hold the entries this node want to contribute to the shared state. 
    
    List<Address> memberList = new ArrayList<Address>(); // Used to detect removed members of the cluster.
    
    /**
     * Note that nothing happens before start() is called.
     * @param reciever The receiver that receives anything that this class doesn't know what to do with (This class handles all NetworkState.MemberState and Entry<String, byte[]>). 
     * @param reportAvailable Whether or not this node should report that it is available.  
     */
    public ServerList(MessageReciever receiver, boolean reportAvailable)
	{
    	this(receiver, reportAvailable, new HashMap<String, byte[]>());
	}
    
    /**
     * Note that nothing happens before start() is called.
     * @param reciever The receiver that receives anything that this class doesn't know what to do with. That means, just about anything except some very specific classes that are also in this package. 
     * @param reportAvailable Whether or not this node should report that it is available.  
     * @param sharedState This nodes contribution to the shared state. 
     */
	public ServerList(MessageReciever receiver, boolean reportAvailable, Map<String, byte[]> sharedState)
	{
		this.receiver = receiver;
		this.reportAvailable = reportAvailable;
		try {
			channel = new JChannel();
		} catch (Exception e) {
			// Really really not supposed to happen. 
			e.printStackTrace();
		}
		state = new NetworkState(channel.getAddress());
		this.thisNodesSharedStateContribution = sharedState;
	}
	
	/**
	 * Starts this serverList, by connection to the "DisCoCluster". 
	 * @throws Exception
	 */
	public void start() throws Exception {
		// Making sure messages go somewhere.
		channel.setReceiver(this);  
		// Connecting.
        channel.connect("DisCoCluster");
        // Getting the state from somewhere else. I do not care where. 
        channel.getState(null, 10000);  
        
        // Just sending our contribution to the shared state, one at the time. 
        for (Entry<String, byte[]> entry : thisNodesSharedStateContribution.entrySet())
        {
        	SendToServer(new StateEntry(entry.getKey(), entry.getValue()), null, null);
        }
        isBusy(!reportAvailable);
    }
	/**
	 * Attempts to close the ServerList. 
	 * Sometimes, if this node has yet to fully join the cluster, this will not succeed. 
	 */
    public void close()
    {
    	channel.disconnect();
    	channel.close();
    }
    /**
     * Sets the listener that is called whenever a server disappears from the cluster. 
     * @param listener 
     */
    public void setDeletedServerListener(ActionListener listener)
    {
    	this.deletedServerListener = listener;
    }
    /**
     * This method is called by jGroup when the view (the connected members) changes. 
     * The only thing i use that information to, is to detect the members that have been removed/deleted/disappeared. 
     * This method runs in O(n) in Java 7 and O(n^2) in Java 6. 
     * @param new_view The new view. 
     */
    public void viewAccepted(View new_view) {
    	// First i put all the old ones in a new list. 
    	List<Address> deleted = new ArrayList<Address>(memberList);
    	// Then i remove all those that are left. 
    	deleted.removeAll(new_view.getMembers()); // I know that i modify the memberList by doing this. But i but a new list into memberList in the next line.
    	// And update the list to reflect the new members. 
    	memberList = new_view.getMembers();
    	
    	// Making callbacks if any client disappeared. 
    	if (deleted.size() != 0) 
    	{
    		for (Address address : deleted)
    		{
    			synchronized(state) {
    				state.removeServer(address);
                }
    			deletedServerListener.actionPerformed(new ActionEvent(address, 0, "Server removed"));
    		}
    	}
    }
    
    /**
     * This method is called by jGroups whenever a message is received. 
     * Then this method either handles it as part of the shared state, or sends it to the receiver. 
     * @param msg The message.  
     */
    @Override
    public void receive(Message msg) {
    	Object obj = msg.getObject();
        if (obj instanceof NetworkState.MemberState)
        {
        	// We received that one of the members in the cluster has changed its state. So we should update our NetworkState. 
        	NetworkState.MemberState memberState = (NetworkState.MemberState)obj;
        	synchronized(state) {
                state.setServerState(msg.getSrc(), memberState);
            }
        }
        else if (obj instanceof StateEntry)
        {
        	// We received a new entry for the shared state. 
        	StateEntry stateEntry = (StateEntry)obj;
    		System.out.println("Received a shared state entry for " + stateEntry.getKey());
    		state.putInSharedState(stateEntry);
        }
        else
        {
        	// When i do not know what to do with it, i pass it on. 
        	receiver.Recieve(msg.getObject(), msg.getSrc());
        }
    }
    /**
     * Puts a new entry in the shared state (including this nodes shared state. 
     * @param key The string key for this entry in the shared state. 
     * @param value The byte array value associated with this shared state entry. 
     * @return Whether or not this was successfully send. 
     */
    public boolean sendStateEntry(String key, byte[] value)
    {
    	return SendToServer(new StateEntry(key, value), null, null);
    }
    /**
     * Puts the current state in the specified OutputStream. 
     */
    public void getState(OutputStream output) throws Exception {
        synchronized(state) {
            Util.objectToStream(state, new DataOutputStream(output));
        }
    }
    /**
     * Sets the state that it got from another node in the cluster. 
     * This is only called once, if it joins a cluster that already has other members. 
     */
    public void setState(InputStream input) throws Exception {
        NetworkState new_state =(NetworkState)Util.objectFromStream(new DataInputStream(input));
        synchronized(state) {
        	// Setting the status of all the members. 
        	for (Entry<Address, NetworkState.MemberState> entry : new_state.getServerMap().entrySet())
        	{
        		state.setServerState(entry.getKey(), entry.getValue());
        	}
        	// Getting the shared state. 
        	for (Entry<String, byte[]> entry : new_state.getSharedState().entrySet())
        	{
        		state.putInSharedState(new StateEntry(entry.getKey(), entry.getValue()));
        	}
        }
        System.out.println("Got state: " + state);
    }
    /**
     * Returns the shared state that is shared in the cluster. Changes in the cluster state are reflected in this map. 
     * Do not modify it in any way!
     * 
     * @return the shared state that is shared in the cluster. Changes in the cluster state are reflected in this map.
     */
    public Map<String, byte[]> getSharedState()
    {
    	return state.getSharedState();
    }
    /**
     * Sets the current state of this server. 
     * @param busy = true. Available = false;
     * @throws Exception 
     */
    public void isBusy(Boolean busy) throws Exception
    {
    	NetworkState.MemberState message; 
    	if (busy)
    	{
    		message = NetworkState.MemberState.BUSY;
    	}
    	else
    	{
    		message = NetworkState.MemberState.AVAILABLE;
    	}
    	SendToServer(message, null, null);
    }
    /**
     * This method is called whenever you got a server from getAvailableServer, but then didn't use that anyway. 
     * @param server the server that was available that you didn't use. 
     * @throws InterruptedException 
     */
    public void didntUseAvailableServer(Address server) throws InterruptedException
    {
    	state.didntUseAvailableServer(server);
    }
    /**
     * Used when notifying a server, that you can still take more jobs.
     * @param server the server you got a job from, that you want to get another from. 
     * @throws Exception 
     */
    public void sendStillAvailable(Address server) throws Exception
    {
    	Message msg = new Message(server, null, NetworkState.MemberState.AVAILABLE);
    	channel.send(msg);
    }
    /**
     * Returns an available server. Will block until there is one available. 
     * 
     * @return an available server. Will block until there is one available.
     */
    public Address getAvailableServer()
    {
    	try {
    		while(true)
    		{
    			Address res = state.getAvailableServer();
    			// I do not return if the address is my own. 
    			if (!res.equals(channel.getAddress()))
				{
    				// If for some reason, something went wrong, i do not return it. 
    				// This should not be necessary, but i keep it anyway. 
    				if (state.containsServer(res))
    				{
    					return res;
    				}
				}
    		}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
    	return null;
    }
    /**
	 * Returns the current state of the server (or null if it isn't in the current cluster). 
	 * @param server the server you want the state from. 
	 * @return the current state of the server (or null if it isn't in the current cluster). 
	 */
	public MemberState getServerState(Address server)
	{
		return state.getServerState(server);
	}
    /**
     * Sends an object to the specified server (or the entire cluster). 
     * @param obj The object to send (generics doesn't work over the network, so no point in having them). 
     * @param server The server the object needs to be sent to. If null, the object will be send to the entire cluster. 
     * @param source Where does the object come from, if null then its by default this sever. 
     * @return Success. Whether or not it actually succeed in sending the object without errors. 
     */
    public synchronized boolean SendToServer(Object obj, Address server, Address source)
    {
    	
		Message msg = new Message(server, source, obj);
		try {
			channel.send(msg);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
    }
    /*
     * The below 3 methods are part of the Receiver interface, but i do not use them (yet). 
     */
    @Override
	public void block() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void suspect(Address arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void unblock() {
		// TODO Auto-generated method stub
		
	}
}