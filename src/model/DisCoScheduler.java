package model;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

/**
 *
 * @author  Erik
 * @created 04-01-13 19:53
 */
public class DisCoScheduler<E> {
    private ActionListener invokeListener; // The listener used to tell the parent container that a new job has been invoked.
    private int eventCounter = 0; // This it really not used, but it still gives a unique id.

    /**
     * The default and only constructor.
     *
     * @param invokeListener The invokelistner the jobs should be sent to.
     */
    public DisCoScheduler(ActionListener invokeListener) {
        this.invokeListener = invokeListener;
    }

    /**
     * By calling this, you make sure that the job is invoked at some point in the future. Resulting in that the join() method is called with a list of the results.
     * This method is not thread safe.
     *
     * @param job
     */
    public final void invoke(Job<E> job) {
        InternalJob internalJob = new InternalJob(job);
        invokeListener.actionPerformed(new ActionEvent(internalJob, eventCounter, "Invoked element: " + job));
        eventCounter++;
    }

    /**
     * Invokes all the jobs specified. See invoke();
     *
     * @param jobs to be invoked.
     */
    public final void invokeAll(Job<E>... jobs) {
        for (int i = 0; i < jobs.length; i++) {
            invoke(jobs[i]);
        }
    }

    /**
     * Invokes all the jobs specified. See invoke();
     *
     * @param jobs to be invoked.
     */
    public final void invokeAll(Collection<? extends Job<E>> jobs) {
        for (Job<E> job : jobs) {
            invoke(job);
        }
    }
}
