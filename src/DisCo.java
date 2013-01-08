import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.*;

import model.*;
import network.ClusterHandler;

import concurrent.ThreadPool;


public class DisCo<E> {
    private Environment<E> env; // The environment, that holds everything of importance on this machine.

    private ThreadPool pool; // The threadpool.

    private ClusterHandler<E> cluster = null; // If the cluster is started, this is where it goes.

    /**
     * The simplest constructor of DisCo.
     * This just launches a local-only instance of DisCo, with a default number of threads that can use the entire local processor.
     */
    public DisCo() {
        this(0);
    }

    /**
     * This constructor specifies how many threads DisCo should run.
     * This could be useful if you do not wan't the Cluster creation to take way to long (it can if you use the default values for threads).
     *
     * @param threads The number of threads this DisCo instance should use.
     */
    public DisCo(int threads) {
        this(threads, false, false);
    }

    /**
     * This constructor allows you to specify that DisCo should try to spread the work out to the other nodes in the cluster.
     *
     * @param useCluster      Whether or not a cluster should be used.
     * @param reportAvailable If using a cluster, this variable sets whether or not this machine should accept jobs from other nodes.
     */
    public DisCo(boolean useCluster, boolean reportAvailable) {
        this(0, useCluster, reportAvailable);
    }

    /**
     * @param threads         The number of threads this DisCo instance should use.
     * @param useCluster      Whether or not a cluster should be used.
     * @param reportAvailable If using a cluster, this variable sets whether or not this machine should accept jobs from other nodes.
     */
    public DisCo(int threads, boolean useCluster, boolean reportAvailable) {
        this(threads, useCluster, reportAvailable, new StandardJobQueue<E>());
    }

    /**
     * @param threads         The number of threads this DisCo instance should use.
     * @param useCluster      Whether or not a cluster should be used.
     * @param reportAvailable If using a cluster, this variable sets whether or not this machine should accept jobs from other nodes.
     * @param jobQueue        The JobQueue to use.
     */
    public DisCo(int threads, boolean useCluster, boolean reportAvailable, JobQueue<E> jobQueue) {
        this.env = new Environment(jobQueue);

        threads = threads == 0 ? Runtime.getRuntime().availableProcessors() + 1 : threads;

        this.pool = new ThreadPool(env, threads);
        pool.start();

        if (useCluster) {
            cluster = new ClusterHandler(env, reportAvailable);
            cluster.start();
        }
    }

    /**
     * Returns a Future that holds the result from the job that was given as a input.
     * The get method of the Future throws an ExecutionException if the job was cancelled.
     *
     * @param job The job that should be calculated.
     * @return A future holding the result from the job, and gives a interface to cancel it.
     */
    public Future<E> execute(Job<E> job) {
        // First setting all the variables that we use.
        long id = env.getIncrementedLocalId();

        // Making sure the job class is known by the cluster.
        if (cluster != null) {
            for (Entry<String, byte[]> entry : getClassesMap(job.getClass()).entrySet()) {
                cluster.addToClasses(entry.getKey(), entry.getValue());
            }
        }

        final WorkContainer<E> container = new WorkContainer(env, job, id, 0, 0);

        final CountDownLatch resultLatch = new CountDownLatch(1);

        final ObjectContainer<E> result = new ObjectContainer<E>();

        // Then i make sure that it is calculated.
        try {
            env.putJobInQueue(container);
        } catch (InterruptedException e1) {
            // This really shouldn't happen.
            throw new RuntimeException("Could not put the job in the queue because of an interrupt. ");
        }
        env.setWorkDoneObserver(id, new WorkDoneObserver<E>() {
            @Override
            public void workDone(E gotResult) {
                result.setObject(gotResult);
                resultLatch.countDown();
            }
        });
        // making a future to return. Its a really just a simple interface to the WorkContainer.
        Future<E> res = new Future<E>() {
            @Override
            public synchronized boolean cancel(boolean mayInterruptIfRunning) {
                if (resultLatch.getCount() == 0) {
                    return false;
                }
                container.abort();
                return true;
            }

            @Override
            public synchronized boolean isCancelled() {
                return container.isAborted();
            }

            @Override
            public synchronized boolean isDone() {
                return resultLatch.getCount() == 0;
            }

            @Override
            public synchronized E get() throws InterruptedException, ExecutionException {
                resultLatch.await();
                if (container.isAborted()) {
                    throw new CancellationException("Job was aborted/cancelled.");
                }
                try {
                    return result.getObject();
                } catch (InterruptedException | ExecutionException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public synchronized E get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                final ObjectContainer<E> res = new ObjectContainer<E>();
                final CountDownLatch waitingLatch = new CountDownLatch(1);
                final Thread timeOutThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            unit.sleep(timeout);
                        } catch (InterruptedException e) {
                            return;
                        }
                        res.setException(new TimeoutException("Waited the " + timeout + " " + unit + "."));
                        container.abort();
                        resultLatch.countDown();
                        waitingLatch.countDown();
                    }
                });
                Thread resultThread;
                resultThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            resultLatch.await();
                            if (container.isAborted()) {
                                res.setException(new CancellationException("Job was aborted/cancelled."));
                            }
                        } catch (InterruptedException e) {
                            res.setException(e);
                        }
                        try {
                            res.setObject(result.getObject());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        timeOutThread.interrupt();
                        waitingLatch.countDown();
                    }
                });

                timeOutThread.start();
                resultThread.start();
                waitingLatch.await();
                try {
                    return res.getObject();
                } catch (InterruptedException | ExecutionException | TimeoutException | CancellationException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        return res;
    }

    private class ObjectContainer<T> {
        private T obj;
        private Exception exception = null;

        public synchronized void setObject(T obj) {
            this.obj = obj;
        }

        public synchronized void setException(Exception e) {
            // Only 1 exception, the first.
            if (this.exception == null) {
                this.exception = e;
            }
        }

        public synchronized T getObject() throws Exception {
            if (exception != null)
                throw exception;
            return obj;
        }
    }

    /**
     * Closes this DisCo instance. Interrupts the threads and close the cluster.
     */
    public void close() {
        pool.shutdownNow();
        if (cluster != null) {
            cluster.close();
            new Thread(new Runnable() {
                @SuppressWarnings("static-access")
                @Override
                public void run() {
                    try {
                        Thread.currentThread().sleep(3000);
                    } catch (InterruptedException ignored) { }

                    cluster.close();
                }
            }).start();
        }
    }

    /**
     * Returns a map of String and bytes representing the name and bytes of the classes given as input.
     *
     * @param classesIn The classes to convert.
     * @return a map of String and bytes representing the name and bytes of the classes given as input.
     */
    private Map<String, byte[]> getClassesMap(Class<?>... classesIn) {
        List<Class<?>> classes = new ArrayList<Class<?>>();

        for (int i = 0; i < classesIn.length; i++) {
            classes.add(classesIn[i]);
        }

        Class<?>[] emptyClassArray = {};
        try {
            return io.Classes.toClassNameAndBytesMap(classes.toArray(emptyClassArray));
        } catch (IOException e1) {
            return null;
        }
    }
}
