package model;

import java.io.Serializable;

/**
 * This class contains an result, and hold the relevant information necessary to send it back to where it belong (except what machine that is on). 
 * @author Erik Krogh Kristensen
 *
 * @param <E>
 */
public class Result<E> implements Serializable {
	private static final long serialVersionUID = 1L; // Increment when changing this class (havn't happened yet). 
	long ID; // The id of the job that calculated this result. 
	int jobID; // The id that this jobs parent created for it (i a binary recursion tree the number is 0-1).
	long parentID; // The id of the parent. 
	E result; // The result. 
	/**
	 * The default (and only constructor), see the params for what is needed to input. 
	 * 
	 * @param id The id of the job that calculated this result. 
	 * @param jobID The id that this jobs parent created for it (i a binary recursion tree the number is 0-1).
	 * @param parentID The id of the parent. 
	 * @param result The result. 
	 */
	public Result(long id, int jobID, long parentID, E result)
	{
		this.ID = id;
		this.jobID = jobID;
		this.parentID = parentID;
		this.result = result;
	}
	/**
	 * Returns The id of the job that calculated this result. 
	 * @return The id of the job that calculated this result. 
	 */
	public long getID()
	{
		return ID;
	}
	/**
	 * Returns the id that this jobs parent created for it (i a binary recursion tree the number is 0-1).
	 * @return The id that this jobs parent created for it (i a binary recursion tree the number is 0-1).
	 */
	public int getJobID()
	{
		return jobID;
	}
	/**
	 * Returns the id of the parent. 
	 * @return The id of the parent. 
	 */
	public long getParentID()
	{
		return parentID;
	}
	/**
	 * Returns the result. 
	 * @return The result. 
	 */
	public E getResult()
	{
		return result;
	}
	/**
	 * Beware when using this, this is only for use when you really need a mutable class. 
	 * @param res, the result this Result container should store. 
	 */
	public void setResult(E res)
	{
		this.result = res;
	}
}
