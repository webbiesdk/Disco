package concurrent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * This class creates a synchronized way to access and change a status based on a enum. 
 * It also provides some extra functions, like setting some of the statuses as final, meaning that once they get in that state, nothing can change them back. 
 * 
 * @author Erik Krogh Kristensen
 * 
 * @param <E> The Enum that makes the basis of this status. 
 */
public class ConcurrentStatus<E extends Enum<E>> implements Serializable  {
	// Remember to increment when developing. 
	private static final long serialVersionUID = 1L;
	boolean finalEntered;
	List<E> finals;
	E status;
	public ConcurrentStatus(E status)
	{
		this.finalEntered = false;
		this.finals = new ArrayList<E>();
		this.status = status;
	}
	/**
	 * Returns the current status
	 * @return the current status
	 */
	public synchronized E getStatus()
	{
		return status;
	}
	/**
	 * This method sets the status, but returns false if a final status has been set. (And it therefore cannot change it anymore. 
	 * @param status
	 * @return true if status was changed. False if it wasn't due to a final status being there. 
	 */
	public synchronized boolean setStatus(E status)
	{
		if (!finalEntered)
		{
			this.status = status;
			return true;
		}
		return false;
	}
	/**
	 * Use this method when you want to set a status as final.
	 * @param status
	 */
	public synchronized void setFinal(E status)
	{
		if (finalEntered)
		{
			throw new RuntimeException("Cannot put a final in 2 times. ");
		}
		this.status = status;
		this.finalEntered = true;
	}
	@Override
	public String toString()
	{
		return status.toString();
	}
}
