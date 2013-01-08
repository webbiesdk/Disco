package concurrent;

import model.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is a simple ThreadPool, that takes runnables from an external env, and executes them in the pool.
 * Unlike other pools, its safe to edit the env from outside the pool, by removing or adding runnables.
 * This can be used to remove jobs if you want to execute those on another part of a cluster instead (just an example).
 * <p/>
 * The task are executed by the order they are in the env (FIFO).
 *
 * @author Erik Krogh Kristensen
 */
public class ThreadPool {
    Environment env;
    int corePoolSize;
    List<Thread> coreThreadPool;

    public ThreadPool(Environment env) {
        this(env, Runtime.getRuntime().availableProcessors() + 1);
    }

    public ThreadPool(Environment env, int corePoolSize) {
        this.env = env;
        this.corePoolSize = corePoolSize;
        this.coreThreadPool = new ArrayList();
        for (int i = 0; i < corePoolSize; i++) {
            Thread thread = new Thread(new Executor());
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
     * Shuts down the pool.
     */
    public void shutdownNow() {
        for (Thread t : coreThreadPool) {
            t.interrupt();
        }
    }

    /**
     * Returns the next element in the env to be processed.
     *
     * @return the next element in the env to be processed.
     */
    private Runnable getNext() {
        try {
            return env.getLocalJobFromQueue();
        } catch (InterruptedException ignored) {
            return null;
        }
    }

    /**
     * This class is a runnable that simply executes all the runnables that it gets from the getNext() method.
     *
     * @author Erik
     */
    private class Executor implements Runnable {
        @Override
        public void run() {
            Runnable next;
            while ((next = getNext()) != null) {
                next.run();
            }

        }
    }

}
