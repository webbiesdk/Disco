package model;

import java.util.Collection;

/**
 *
 * @author  Erik
 * @created 04-01-13 19:53
 */
public abstract class DisCoScheduler<E> {
    /**
     * By calling this, you make sure that the job is invoked at some point in the future. Resulting in that the join() method is called with a list of the results.
     * This method is not thread safe.
     *
     * @param job
     */
    public abstract void invoke(Job<E> job);

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
