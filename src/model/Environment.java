package model;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class is responsible for a number of different things in this framework.
 * All methods in this class are thread-safe.
 * 1: Maintaining an id (a long), that increments every time you access it.
 * 2: Providing a simple interface to a jobQueue (specified in the constructor).
 * 3: Maintaining a map of all the jobs that needs more results before they can proceed.
 * 4: Take results that are completed, and send them to the right place.
 * 5: Wait for the final result(s) to be ready.
 *
 * @author Erik Krogh Kristensen
 */
public class Environment<E> {
    private AtomicLong uniqueId;

    private JobQueue<E> jobQueue;
    private ConcurrentMap<Long, WorkContainer<E>> idleJobs; // This map hold all the jobs that waits for some of their subjobs to complete. This map allows me to quickly find them when i got the result that they invoked.


    private Map<Long, Result<E>> finalResults; // This map holds the final results that i've gotten (that means that no idle-job was there to take the result). But that have yet to get a callback that says what to do with it. Most of the time when something ends up here, its a bug.
    private Map<Long, WorkDoneObserver> finalResultsCallBacks; // This holds the callbacks that are launched when a finalResult is ready. The key is the ID of the WorkContainer you are waiting for.
    private Lock finalResultLock; // A lock to synchronize the 2 above.

    private Map<Job<E>, WorkContainer<E>> jobWorkContainerMap;

    public Environment(JobQueue jobQueue) {
        this.jobQueue = jobQueue;

        this.uniqueId = new AtomicLong(0);


        this.finalResultLock = new ReentrantLock();

        this.idleJobs = new ConcurrentHashMap();

        this.finalResults = new HashMap();
        this.finalResultsCallBacks = new HashMap<>();

        jobWorkContainerMap = new ConcurrentHashMap<>();
    }
    /*
	 * 1: Maintaining an uniqueId.
	 */

    /**
     * This method returns a ID unique on this Environment object. Starts from 1.
     *
     * @return an unique id.
     */
    public long getIncrementedLocalId() {
        return uniqueId.addAndGet(1);
    }
    /**
     * @return the next WorkContainer for local processing.
     * @throws InterruptedException
     */
    public WorkContainer<E> getLocalJobFromQueue() throws InterruptedException {
        Job<E> job = jobQueue.takeLocal();
        if (job == null) {
            return null;
        }
        return jobWorkContainerMap.remove(job);
    }

    /**
     * @return the next WorkContainer for remote processing.
     * @throws InterruptedException
     */
    public WorkContainer<E> getRemoteJobFromQueue() throws InterruptedException {
        Job<E> job = jobQueue.takeRemote();
        if (job == null) {
            return null;
        }
        return jobWorkContainerMap.remove(job);
    }

    /**
     * A method to put a new WorkContainer in the queue.
     *
     * @param workContainer the WorkContainer that should be run.
     * @throws InterruptedException
     */
    public void putJobInQueue(WorkContainer<E> workContainer) throws InterruptedException {
        jobWorkContainerMap.put(workContainer.getJob(), workContainer);
        jobQueue.put(workContainer.getJob());
    }
	/*
	 * 3: Map of idle jobs. 
	 */

    /**
     * Put a job in the queue of jobs that needs to more results before they can continue.
     * The environment will only pass results to jobs that are in the idle queue, and the environment will in no way change the idle queue.
     * The only way to change the queue is putIdleJob() and removeIdleJob().
     *
     * @param job The job to be put in the idle queue.
     */
    public void putIdleJob(WorkContainer<E> job) {
        idleJobs.put(job.getId(), job);
    }

    /**
     * Returns the job in the idle queue that has the id specified in the parameter id.
     *
     * @param id of the job.
     * @return the job in the idle queue that has the id, null if it isn't there (should never happen).
     */
    public WorkContainer<E> getIdleJob(Long id) {
        return idleJobs.get(id);
    }

    /**
     * Removes a job from the idle queue.
     * This method is normally only done by the job itself, when it find out that it doesn't need any more results.
     *
     * @param id of the job to be removed.
     * @return true if it was removed, false if i didn't find the ID.
     */
    public boolean removeIdleJob(Long id) {
        return idleJobs.remove(id) != null;
    }

    /**
     * Returns a map of the internal Jobs.
     * This is a clone, so it can safely be manipulated.
     *
     * @return a map of the internal Jobs.
     */
    public Map<Long, WorkContainer<E>> getIdleJobs() {
        return new HashMap(idleJobs);
    }
	/*
	 * 4: Sending results where they belong. 
	 */

    /**
     * This method basically just converts the result contained in the WorkContainer to an Result container, that is submitted to submitResult(Result<E> res);
     *
     * @param work the WorkContainer that contains the result that needs to be send further.
     */
    public void submitResult(WorkContainer<E> work) {
        // Converting WorkContainer to Result.
        Result<E> res = new Result<E>(work.getId(), work.getParentJobId(), work.getParentId(), work.getResult());
        // Passing it on.
        submitResult(res);
    }

    /**
     * Sends the result where it needs to go. It can go to 2 locations within the environment.
     * 1. To an idle job.
     * 2. If there is no idle job, it gets in the finalresultsqueue (the results that are waiting to be picked up from the outside). This could f.ex. happen if a result needs to be sent back to another server in the cluster.
     *
     * @param res the result container.
     */
    public void submitResult(Result<E> res) {
        WorkContainer<E> job = idleJobs.get(res.getParentID());
        if (job == null) {
            finalResultReady(res);
        } else {
            job.putResult(res.getJobID(), res.getResult());
        }
    }
	
	/*
	 * 5: Wait for the final result to be ready. 
	 */

    /**
     * A private method used when a result that doesn't belong to any idle job gets here.
     * This method either executes the callback associated with this returned result, or puts it in a queue, so the callback can be called when it gets here.
     *
     * @param res the Result that didn't belong to any idle job.
     */
    private void finalResultReady(Result<E> res) {
        finalResultLock.lock();
        try {
            WorkDoneObserver<E> callback;
            if ((callback = finalResultsCallBacks.get(res.getID())) == null) {
                System.out.println("Final Result waiting."); // I print this, because most times when this has happened, it was due to a bug.
                finalResults.put(res.getID(), res);
            } else {
                callback.workDone(res.getResult());
            }
        } finally {
            finalResultLock.unlock();
        }
    }

    /**
     * A method used to call a single observer when the result associated with the WorkContainer with the id of param:id.
     * There can only be one callback for each id.
     *
     * @param id       The id if the WorkContainer from which you are awaiting the result.
     * @param observer a observer thats called when the result is here.
     */
    public void setWorkDoneObserver(long id, WorkDoneObserver<E> observer) {
        finalResultLock.lock();
        try {
            Result<E> res;
            if ((res = finalResults.get(id)) == null) {
                if (finalResultsCallBacks.put(id, observer) != null) {
                    // It is possible to replace an callback with another, but it is not in any way recommended.
                    System.out.println("Replaced an earlier callback!!!! \nReplaced an earlier callback!!!!");
                }
            } else {
                observer.workDone(res.getResult());
            }
        } finally {
            finalResultLock.unlock();
        }
    }

    /**
     * This removes the callback associated with the ID.
     * Only call this method when you are 100% sure that the callback will never be called.
     * So this method is only meant to clean, not to interrupt.
     *
     * @param id The id of the WorkContainer that you are sure will not in any way return any kind of result.
     * @return if the operation was successful.
     */
    public boolean removeCallBack(long id) {
        finalResultLock.lock();
        try {
            return finalResultsCallBacks.remove(id) != null;
        } finally {
            finalResultLock.unlock();
        }
    }

    /**
     * A method that blocks until the result associated with the WorkContainer with the id of param:id is ready.
     * This method uses the callback mechanisms also described in this class. And this counts as a callback, so you cannot safely set another callback associated with the same id.
     *
     * @param id the ID of the WorkContainer from which you waiting an result from.
     * @return the result.
     * @throws InterruptedException If the current thread is interrupted.
     */
    public E waitForFinalResult(long id) throws InterruptedException {
        // This is only a container to hold the result object that is inserted with setResult in the WorkDoneObserver below.
        final Result<E> res = new Result<E>(0, 0, 0, null);

        final CountDownLatch resWait = new CountDownLatch(1);
        setWorkDoneObserver(id, new WorkDoneObserver<E>() {
            @Override
            public void workDone(E result) {
                res.setResult(result);
                resWait.countDown();
            }
        });
        resWait.await();
        return res.getResult();
    }

    /**
     * Returns a string representation of this Environment. Including all its lists, maps etc.
     */
    public String toString() {
        String res = "";
        res += "Jobs: " + jobQueue + "\n";
        res += "IdleJobs: " + idleJobs + "\n";
        res += "FinalResults waiting: " + finalResults;
        return res;
    }
}
