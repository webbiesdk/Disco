package model;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * This abstract class specifies what a job should be able to do, using the 2 methods work() and join().  
 * 
 * If you call any of the invoke methods, these methods MUST return before work() or join() returns. So it is not in any way recommended to start another thread that may call invoke. 
 *  
 * @author Erik Krogh Kristensen
 */
public abstract class Job<E> implements Serializable {
	/**
	 * Remember to increment when developing
	 */
	private static final long serialVersionUID = 1L;
	
	transient private ActionListener invokeListener; // The listener used to tell the parent container that a new job has been invoked. 
	private int eventCounter = 0; // This it really not used, but it still gives a unique id. 
	
	/**
	 * It is required to call this before calling work() or join(). 
	 * @param invokeListener, the listener. 
	 */
	protected void setInvokeListener(ActionListener invokeListener)
	{
		this.invokeListener = invokeListener;
	}
	/**
	 * This method is called the first time the worker has to do something. 
	 * The worker can do one of 2 things:
	 * 1: Produce an result (that can be null).
	 * 2: Tell that more works needs to be done, by calling invoke() or invokeAll(). If one of these methods are called, the result from the method will be discarded.   
	 * @return an result or null. 
	 */
	public abstract E work();
	/**
	 * This method works much like work, except that this one is only called if work() (or join()) has specified that more work needs to be done. 
	 * Just like work, if invoke() or invokeAll() is called, the result will be discarded. 
	 * @return an result or null. 
	 */
	public abstract E join(List<E> list);
	
	/**
	 * By calling this, you make sure that the job is invoked at some point in the future. Resulting in that the join() method is called with a list of the results.  
	 * This method is not thread safe. 
	 * @param job
	 */
	public final void invoke(Job<E> job)
	{
		if (invokeListener == null)
		{
			System.out.println("invorker == null");
		}
		invokeListener.actionPerformed(new ActionEvent(job, eventCounter, "Invoked element: " + job));
		eventCounter++;
	}
	/**
	 * Invokes all the jobs specified. See invoke();
	 * @param jobs to be invoked. 
	 */
	public final void invokeAll(Job<E>... jobs)
	{
		for (int i = 0; i < jobs.length; i++)
		{
			invoke(jobs[i]);
		}
	}
	/**
	 * Invokes all the jobs specified. See invoke();
	 * @param jobs to be invoked. 
	 */
	public final void invokeAll(Collection<? extends Job<E>> jobs)
	{
		for (Job<E> job : jobs)
		{
			invoke(job);
		}
	}
}

