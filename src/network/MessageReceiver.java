package network;

import network.data.Server;

public interface MessageReceiver {
	/**
	 * Receives some kind of method that is not related to the status of the other nodes, so in DisCo. Either jobs, results or other messages.
	 * @param obj the received object. 
	 * @param sender the sender. 
	 */
	public void recieve(Object obj, Server sender);
}
