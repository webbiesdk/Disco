package concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * This class is a simple ThreadPool, that takes runnables from an external queue, and executes them in the pool.
 * Unlike other pools, its safe to edit the queue from outside the pool, by removing or adding runnables.
 * This can be used to remove jobs if you want to execute those on another part of a cluster instead (just an example).
 * <p/>
 * The task are executed by the order they are in the queue (FIFO).
 *
 * @author Erik Krogh Kristensen
 */
public class ThreadPool {
    BlockingQueue<? extends Runnable> queue;
    int corePoolSize;
    List<Thread> coreThreadPool;

    public ThreadPool(BlockingQueue<? extends Runnable> queue) {
        this(queue, Runtime.getRuntime().availableProcessors() + 1);
    }

    public ThreadPool(BlockingQueue<? extends Runnable> queue, int corePoolSize) {
        this.queue = queue;
        this.corePoolSize = corePoolSize;
        this.coreThreadPool = new ArrayList<Thread>();
        for (int i = 0; i < corePoolSize; i++) {
            Thread thread = new Thread(new Executer());
            coreThreadPool.add(thread);
        }
    }

    /**
     * Starting the threads in the pool
     */
    public void start() {
        for (Thread t : coreThreadPool) {
            t.start();
        }
    }

    /**
     * Shuts down the pool, and returns the remaining runnables in the queue as a list.
     * The list should in most cases have a size of 0.
     *
     * @return list of remaining runnables in queue.
     */
    public List<Runnable> shutdownNow() {
        for (Thread t : coreThreadPool) {
            t.interrupt();
        }
        List<Runnable> res = new ArrayList<Runnable>();
        queue.drainTo(res);
        return res;
    }

    /**
     * Returns the next element in the queue to be processed.
     *
     * @return the next element in the queue to be processed.
     */
    private Runnable getNext() {
        try {
            return queue.take();
        } catch (InterruptedException ignored) {
            return null;
        }
    }

    /**
     * Returns the queue that is used. This queue can safely be manipulated from outside the threadpool, by adding or removing elements.
     *
     * @return the queue that is used by the pool.
     */
    public BlockingQueue<? extends Runnable> getQueue() {
        return queue;
    }

    /**
     * This class is a runnable that simply executes all the runnables that it gets from the getNext() method.
     *
     * @author Erik
     */
    private class Executer implements Runnable {
        @Override
        public void run() {
            Runnable next;
            while ((next = getNext()) != null) {
                next.run();
            }

        }
    }

}
