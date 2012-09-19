package network.data;

import org.jgroups.Address;

/**
 * This is a Server, that holds a Address. 
 * It's here since in case i switch the network stuff, the classes that interact with the network stuff don't have to change. 
 * This have class have to change, but thats about it. 
 * 
 * @author Erik Krogh Kristensen
 *
 */
public class Server{
	Address address;
	public Server(Address address)
	{
		this.address = address;
	}
	@Override
	public int hashCode()
	{
		return address.hashCode();
	}
	@Override
	public boolean equals(Object o)
	{
		if (o instanceof Server)
		{
			Server server = (Server)o;
			return this.getAddress().equals(server.getAddress());
		}
		return false;
	}
	public Address getAddress()
	{
		return this.address;
	}
	
}
