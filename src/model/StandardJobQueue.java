package model;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * // TODO: Doc
 *
 * @author Erik
 * @date 07-01-13, 22:37
 */
public class StandardJobQueue<E> implements JobQueue<E> {
    private final BlockingDeque<Job<E>> queue = new LinkedBlockingDeque();

    @Override
    public Job<E> takeLocal() {
        try {
            return queue.takeLast();
        } catch (InterruptedException e) {
            return null;
        }
    }

    @Override
    public Job<E> takeRemote() {
        try {
            return queue.takeFirst();
        } catch (InterruptedException e) {
            return null;
        }
    }

    @Override
    public void put(Job<E> job) {
        try {
            queue.put(job);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
