package network.data;

import java.io.Serializable;
/**
 * This class represents an entry in the <String, byte[]> map that makes the shared state. 
 * 
 * @author Erik Krogh Kristensen
 *
 */
public class StateEntry implements Serializable{
	private static final long serialVersionUID = 1L;
	String key; 
	byte[] bytes;
	public StateEntry(String key, byte[] bytes)
	{
		this.key = key;
		this.bytes = bytes;
	}
	public String getKey()
	{
		return key;
	}
	public byte[] getBytes()
	{
		return bytes;
	}
}
