package model;

import java.awt.event.ActionListener;
import java.io.Serializable;
import java.util.List;

/**
 * // TODO: Doc
 *
 * @author Erik Krogh Kristensen
 */
public final class InternalJob<E> implements Serializable {
    private Job<E> internalJob;
    private transient DisCoScheduler<E> scheduler;

    public InternalJob(Job<E> internalJob) {
        this.internalJob = internalJob;
    }

    public void setInvokeListener(ActionListener invokeListener) {
        this.scheduler = new DisCoScheduler<E>(invokeListener);
    }
    /**
     * Remember to increment when developing
     */
    private static final long serialVersionUID = 1L;

    public E work() {
        return internalJob.work(scheduler);
    }

    public E join(List list) {
        return internalJob.join(list, scheduler);
    }
}

