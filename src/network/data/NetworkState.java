package network.data;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
/**
 * This class holds the state of the cluster, including the shared state (a <string, byte[]> map). 
 * It also hold a blocking method to get an available server. 
 * 
 * @author Erik Krogh Kristensen
 *
 */
public class NetworkState implements Serializable{
	/**
	 * This class it not synchronized, it needs to be synchronized externally. 
	 */
	private static final long serialVersionUID = 1L;
	
	public static enum MemberState {AVAILABLE, BUSY}; // The different states that the members can be in. 
	
	Map<Server, MemberState> servers; // A map of the servers
	ConcurrentMap<String, byte[]> sharedStateMap; // The shared state of the cluster
	BlockingQueue<Server> availableServers; // A queue of the available servers. When one is removed from the queue, it is not removed from 
	Server localAddress; // The local address, used to make sure that the local JVM is not considered an available server.
	/**
	 * Creates a new NetworkState, where you input the local address of this node, so that the NetworkState does not consider that an available server. 
	 * @param localAddress The local address of this node in the cluster. 
	 */
	public NetworkState(Server localAddress)
	{
		this.servers = new HashMap<Server, MemberState>();
		this.availableServers = new LinkedBlockingQueue<Server>();
		this.localAddress = localAddress;
		this.sharedStateMap = new ConcurrentHashMap<String, byte[]>();
	}
	/**
	 * Sets the server that is considered the local address from this point forward. 
	 * 
	 * Is only supposed to be called once, when a local address is available. 
	 * @param local
	 */
	public void setLocal(Server local)
	{
		this.localAddress = local;
	}
	/**
	 * This method is called whenever this node receives an update about a nodes state. 
	 * @param address The address that the change came from. 
	 * @param state The new state of that address. 
	 */
	public synchronized void setServerState(Server address, MemberState state)
	{
		// The map returns the previous value of the key, when you call put(). I use that here to save the previous value. 
		MemberState prevState = servers.put(address, state);
		
		// I do not put it in the available servers queue, if the state change was from me.  
		if (address.equals(localAddress))
			return;
		
		if (prevState == MemberState.AVAILABLE && state != MemberState.AVAILABLE)
		{
			// We need to remove it from the list of available servers.
			availableServers.remove(address); // This runs in O(n) time, but it has to be done. 
		}
		else if (state == MemberState.AVAILABLE)
		{
			// I do know that this can fail, but this list is far from critical, so if it fails, i do nothing about it. I'm am definitely not using .put(), since i then have to wait for possible far to long, instead of the thing just failing.
			availableServers.add(address);  
		}
	}
	/**
	 * Returns whether or not the server is in the cluster. 
	 * @param server The server that may, or may not still be in the cluster. 
	 * @return whether or not the server is in the cluster. 
	 */
	public synchronized boolean containsServer(Server server)
	{
		return servers.containsKey(server);
	}
	/**
	 * Removes a server from this state. 
	 * @param address The address of the server to be removed. 
	 */
	public synchronized void removeServer(Server address)
	{
		servers.remove(address);
		availableServers.remove(address);
	}
	/**
	 * Returns a available server if there is none, it will wait for a server to become available. 
	 * After this has been returned, this state will no longer return that object using getAvailableServer[s](), until that server actually updates it state.
	 * @return a available server (or null). 
	 * @throws InterruptedException if the current thread is interrupted. 
	 */
	public Server getAvailableServer() throws InterruptedException
	{
		return availableServers.take();
	}
	/**
     * This method is called whenever you got a server from getAvailableServer, but then didn't use that anyway. 
     * @param server the server that was available that you didn't use. 
	 * @throws InterruptedException 
     */
	public synchronized void didntUseAvailableServer(Server server) throws InterruptedException {
		if (servers.get(server) == MemberState.AVAILABLE)
		{
			availableServers.put(server);
		}
	}
	/**
	 * Returns the current state of the server (or null if it isn't in the current cluster). 
	 * @param server the server you want the state from. 
	 * @return the current state of the server (or null if it isn't in the current cluster). 
	 */
	public synchronized MemberState getServerState(Server server)
	{
		return servers.get(server);
	}
	/**
	 * Returns a map of all the servers as the key, and their current state as the value. 
	 * 
	 * DO NOT modify this map. 
	 * 
	 * This is also the map used internally, so the map will get updated as new statechanges are received. 
	 * 
	 * @return a map of all the servers as the key, and their current state as the value. 
	 */
	public synchronized Map<Server, MemberState> getServerMap()
	{
		return servers;
	}
	/**
	 * Returns the current shared state. This map is not a clone, so it will change if the shared state changes. The returned map is thread-safe. 
	 * @return the current shared state. This map is not a clone, so it will change if the shared state changes. The returned map is thread-safe.
	 */
	public Map<String, byte[]> getSharedState()
	{
		return sharedStateMap;
	}
	/**
	 * This methods puts an entry in the shared state on this machine.
	 * If the string specified in the key is already in the shared state, it will be overwritten. And nothing will be done about that.  
	 * IT DOES NOT SEND THAT TO THE REST OF THE CLUSTER!
	 */
	public void putInSharedState(StateEntry stateEntry)
	{
		sharedStateMap.put(stateEntry.getKey(), stateEntry.getBytes());
	}
	
	@Override
	public synchronized String toString()
	{
		return servers.toString();
	}
}
