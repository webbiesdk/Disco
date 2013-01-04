package model;

import java.util.List;

/**
 * This interface specifies what a job should be able to do, using the 2 methods work() and join().
 *
 * If you call any of the invoke methods on the scheduler, these methods MUST return before work() or join() returns.
 *
 * @author Erik
 * @date 04-01-13, 19:56
 */
public interface Job<E> {
    /**
     * This method is called the first time the worker has to do something.
     * The worker can do one of 2 things:
     * 1: Produce an result (that can be null).
     * 2: Tell that more works needs to be done, by calling invoke() or invokeAll() on the scheduler. If one of these methods are called, the result from the method will be discarded.
     *
     * @param scheduler The scheduler to call if more jobs should be done.
     *
     * @return an result or null.
     */
    public abstract E work(DisCoScheduler<E> scheduler);
    /**
     * This method works much like work, except that this one is only called if work() (or join()) has specified that more work needs to be done.
     * Just like work, if invoke() or invokeAll() is called, the result will be discarded.
     *
     * @param scheduler The scheduler to call if more jobs should be done.
     *
     * @return an result or null.
     */
    public abstract E join(List<E> list, DisCoScheduler<E> scheduler);
}
