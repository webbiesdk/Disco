package network.data;

import java.io.Serializable;
/**
 * Due to the fact that not all classes are known by the "far side". I cannot always just simply transfer something. 
 * 
 * So the work, and the result are transferred this way. And can be dealt with properly when they reach the other node. 
 * 
 * @author Erik Krogh Kristensen
 *
 */
public class BytePackage implements Serializable{
	private static final long serialVersionUID = 1L;
	
	public enum Type {WORK, RESULT}; // The type that this BytePackage can have. It specifies what it contains (at the moment only work or result). 
	private byte[] bytes; // The bytes that hopefully translates into some kind of object in the other end. 
	private Type type; // The variable that holds the Type (see above) of this BytePackage. 
	private long id; // I kind of have to send the id with, because if the receiver can't translate the bytes it needs to be able to tell the sender what went wrong.  
	/**
	 * The only constructor of this immutable class. 
	 * @param type The type of this bytePackage, is it a work (WorkContainer) or is a a result (Result). 
	 * @param bytes The bytes that translated gives us the object. 
	 * @param id An id that can be used to send an error back, if the conversion from bytes to object fails. 
	 */
	public BytePackage(Type type, byte[] bytes, long id)
	{
		this.type = type;
		this.bytes = bytes;
		this.id = id;
	}
	/**
	 * @return the type of this BytePackage. Work or Result atm.
	 */
	public Type getType()
	{
		return this.type;
	}
	/**
	 * @return the bytes associated with this package. Hopefully they can be translated into an object. 
	 */
	public byte[] getBytes()
	{
		return this.bytes;
	}
	/**
	 * @return the id if the result/work contained in the package. Used when the conversion from byte to object fails. 
	 */
	public long getId()
	{
		return this.id;
	}
}
