package network;

import org.jgroups.*;

public interface MessageReciever {
	/**
	 * Receives some kind of method that is not related to the status of the other nodes, so in DisCo. Either jobs, results or other messages.
	 * @param obj the received object. 
	 * @param sender the sender. 
	 */
	public void Recieve(Object obj, Address sender);
}
