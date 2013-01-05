package model;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import concurrent.ConcurrentStatus;

/**
 * The container that holds a job, and is responsible for handling the results that it receives etc.
 * When sending over the network, after it has been received you must call resetInvokeListener() and setEnvironment(Environment). If you don't it will most likely crash the JVM.
 *
 * @param <E>
 * @author Erik Krogh Kristensen
 */
public class WorkContainer<E> implements Runnable, Serializable {

    // Remember to increment when developing. In a sense, this is the build number of the class.
    private static final long serialVersionUID = 3L;
    private transient DisCoScheduler<E> scheduler;

    // The status says if its ready to run, if it needs more results first or if it has an result.
    // The only use for this is currently just to see if the classes that calls this class's methods are doing what they are allowed to do.
    public enum Status {
        RUNNABLE, NEED_RESULTS, HAS_RESULT, ABORTED
    }

    private ConcurrentStatus<Status> status = new ConcurrentStatus<Status>(Status.RUNNABLE);

    private List<E> results = null; // The results that got in, that now waits to be treated.
    private Map<Integer, WorkContainer<E>> missingJobs = null; // The results that we are still waiting for.

    private Job<E> job; // The job that this container contains.
    transient private Environment<E> env; // Much goes through the Environment.
    private long id; // The id that this is assigned at construction time.
    private long parent; // The ID of the parent. This ID is unique on the machine that the parent is stored on.
    private int parentJobID; // The parent may need several smaller jobs done, this ID tells which one of those this job is.
    private E result; // The variable to hold the result from this job, once it is available.
    private int subJobId; // A counter that increment each time it is accessed. Giving a new id for a sub-job.

    private Lock subJobLock; // A lock used to synchronize the interaction regarding subjobs (giving them away and getting them again).

    private boolean subJobsCommitted; // This little boolean tells us if the running job added any sub-jobs to the jobQueue.

    /**
     * The default and only constructor.
     *
     * @param envIn       : The environment to work with, this is where new jobs go etc.
     * @param job         : The job to contain within this container.
     * @param id          : A unique ID. (Get it from the Environment). It is only unique on this machine.
     * @param parent      : The parents unique ID. (Still only on this machine)
     * @param parentJobID : The id that this job has within its parent.
     */
    public WorkContainer(Environment<E> envIn, Job<E> job, long id, long parent, int parentJobID) {
        this.job = job;
        this.env = envIn;
        this.parent = parent;
        this.parentJobID = parentJobID;
        this.id = id;
        this.subJobId = 0;

        this.subJobLock = new ReentrantLock();
        setupScheduler();
    }
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        setupScheduler();
    }
    // Anonymous classes won't serialize. So i'm doing this trick.
    private void setupScheduler() {
        this.scheduler = new DisCoScheduler<E>(){
            @Override
            public void invoke(Job<E> job) {
                invokeJob(job);
            }
        };
    }

    /**
     * For use when "this" does net reefer to this WorkContainer.
     *
     * @return the object that its called on.
     */
    public WorkContainer<E> getObject() {
        return this;
    }

    /**
     * This method is called whenever a job invokes any subJobs.
     * The argument is the subjob that was given by the job.
     *
     * @param job The subjob that was given by the job, whose result the job needs before it can continue.
     */
    private void invokeJob(Job<E> job) {
        // Locking because a while a subjob is being committed, it can happen that a completed sub-result is submitted.
        subJobLock.lock();
        try {
            if (!subJobsCommitted) {
                // Since this is the first subJob that is invoked, we need to a little thing first.
                env.putIdleJob(getObject()); // The parent is now an idle job.
            }
            if (missingJobs == null) {
                // A container to hold the subJobs.
                // I need these in case this job gets aborted, it need to tell the non-completed subjobs that they should abort to.
                missingJobs = new HashMap<Integer, WorkContainer<E>>();
            }

            // Making sure things go as they should.
            subJobsCommitted = true;

            // Counting up.
            int subJobId = getNextSubJobId();
            WorkContainer<E> newJob = new WorkContainer<E>(env, job, env.getIncrementedLocalId(), getId(), subJobId);

            synchronized (getObject()) {
                // If this job is aborted, then launching a new subJob gives no sense, since it will never reach its proper parent.
                if (getObject().getStatus() != WorkContainer.Status.ABORTED) {
                    try {
                        env.putJobInQueue(newJob);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        e.printStackTrace(); // Really not supposed to happen.
                    }
                    missingJobs.put(subJobId, newJob);
                }
            }

        } finally {
            subJobLock.unlock();
        }
    }

    /**
     * This method is only called when you need a new SubJobId (a id of a subjob to this job, so this job know which of the subjobs was returned when they come back).
     * BEWARE: This method DOES increment the subJobId.
     *
     * @return the next subJobId.
     */
    public int getNextSubJobId() {
        return subJobId++;
    }

    /**
     * Resets the subJobId back to 0.
     * A private method that is used when all the subjobs have returned safely.
     */
    private void resetSubJobId() {
        this.subJobId = 0;
    }

    /**
     * Returns the job that this WorkContainer contains.
     * This is mainly to compare to jobs against each other.
     * <p/>
     * Do not in any way modify it.
     *
     * @return the job that this WorkContainer contains.
     */
    public Job<E> getJob() {
        return job;
    }

    /**
     * This is the machine specific unique id of this job.
     *
     * @return the id associated with the workContainer.
     */
    public long getId() {
        return id;
    }

    /**
     * This method is only used when a job is received over a network connection, and you need to generate a new local id.
     *
     * @param id the new id associated with the workcontainer on this machine.
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * Used when this is transferred over the network. Since the environment (for obvious reasons) isn't transmitted.
     *
     * @param env
     */
    public void setEnvironment(Environment<E> env) {
        this.env = env;
    }

    /**
     * This is the id that is unique in the job that this jobs result will be send to.
     *
     * @return The unique ID of the job according to the parent-job.
     */
    public int getParentJobId() {
        return parentJobID;
    }

    /**
     * Returns the id of the parent, that this jobs result will be returned to once it has completed.
     * <p/>
     * If this is 0, it will always go to a finalresults-callback (see the Environment).
     *
     * @return the id of the parent, that this jobs result will be returned to once it has completed.
     */
    public long getParentId() {
        return parent;
    }

    /**
     * This is called whenever this workcontainer is send over the network, and doesn't have a valid parent.
     * Since we know that no job can have the id 0, we therefore set the parent to 0.
     */
    public void resetParentId() {
        parent = 0;
    }

    /**
     * Returns the current status of this job, if it is RUNNABLE, NEED_RESULTS or HAS_RESULTS.
     *
     * @return current status of this job.
     */
    public Status getStatus() {
        return status.getStatus();
    }

    /**
     * Hold the result when it is ready, if there is no result ready, it will throw an RuntimeException.
     *
     * @return the result calculated by the job this WorkContainer contains.
     */
    public E getResult() {
        if (status.getStatus() != Status.HAS_RESULT)
            System.out.println("Status is " + status + ", but yet someone accessed the result. ");
        return result;
    }

    /**
     * Submits a result that this workcontainer has been waiting for.
     * This method is thread safe.
     *
     * @param id     of the job. (The ID thats only unique inside this workcontainer).
     * @param result
     */
    public void putResult(int id, E result) {
        subJobLock.lock();
        try {
            // Making the list if it isn't there.
            if (results == null) {
                results = new ArrayList<E>();
            }
            // Expanding it if we need more room.
            while (id >= results.size()) {
                results.add(null);
            }

            if (missingJobs.containsKey(new Integer(id))) {
                missingJobs.remove(new Integer(id));
                results.set(id, result);
                // Everything that is a state change of this object, is synchronized to the object.
                synchronized (this) {
                    if (missingJobs.size() == 0 && status.getStatus() == Status.NEED_RESULTS) {
                        runAgain();
                    }
                }
            }
        } finally {
            subJobLock.unlock();
        }
    }

    /**
     * This method is called when all subJobs have been completed, and we need to put this job back in the jobQueue.
     */
    private void runAgain() {
        // Only putting it in, if i'm able to change the status to RUNNABLE.
        synchronized (this) {
            if (status.setStatus(Status.RUNNABLE)) {
                try {
                    env.putJobInQueue(this);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                System.out.println("Not running again, i couldn't set the status to Runnable");
            }

            // In any case, removing it from the idle jobs.
            env.removeIdleJob(this.getId());
        }
    }

    /**
     * The method being executes when the status of the workContainer is RUNNABLE.
     * There is no check whether or not the status is runnable before executing this method, so checking if the status is actually RUNNABLE is a good check.
     */
    @Override
    public void run() {
        // If it isn't runnable, it should't run. (Never happened besides from aborted jobs).
        if (status.getStatus() != Status.RUNNABLE) {
            // If it was just aborted, then we just quit, say nothing about it.
            if (status.getStatus() != Status.ABORTED) {
                // Hasn't happened yet. If it does, then that is very very bad!
                throw new RuntimeException("The workContainer was told to work, although its status is currently " + status);
            }
            return;
        }

        resetSubJobId();
        subJobsCommitted = false;

        // Running the thing is easy, it should be.
        // work() and join() gets treated completely the same. Only difference being if earlier jobs are passed on.
        E result;
        if (results == null || results.size() == 0) {
            result = job.work(scheduler);
        } else {
            result = job.join(results, scheduler);
        }
        if (!subJobsCommitted) {
            // We just need to submit an result.
            this.result = result;
            // I'm only submitting the result, if i can change the status to HAS_RESULT. And it is synchronized because i changes the state of the object.
            synchronized (this) {
                if (status.setStatus(Status.HAS_RESULT)) {
                    env.submitResult(this);
                }
            }
        } else {
            subJobLock.lock();
            try {
                // This is if all the results have already gotten back, since that happens multithreaded, that can happen.
                if (missingJobs.size() == 0) {
                    runAgain(); // Not running it again right now, just putting it in the queue.
                } else {
                    status.setStatus(Status.NEED_RESULTS); // Waiting.
                }
            } finally {
                subJobLock.unlock();
            }
        }
    }

    public boolean isAborted() {
        return this.status.getStatus() == Status.ABORTED;
    }

    /**
     * This method is called when the job should for some reason be aborted. Meaning that the result it produces is no longer relevant, and it and all its sub-jobs should terminate.
     * This only changes the state to ABORTED, which makes sure that it, and all its subJobs terminate really quickly.
     * The rest (removing references etc.) is done by someone else, not the responsibility of WorkContainer.
     */
    public void abort() {
        synchronized (this) {
            // First i abort this job.
            if (this.status.getStatus() != Status.ABORTED) {
                this.status.setFinal(Status.ABORTED);
            }
            // Then i make sure that all subJobs are also aborted.
            if (missingJobs != null) {
                for (Entry<Integer, WorkContainer<E>> e : missingJobs.entrySet()) {
                    e.getValue().abort();
                }
            }
            // Removing any reference to the job down the recursion tree, thereby making room for the garbage collector.
            missingJobs = null;
        }
    }

    @Override
    public String toString() {
        return "Work(" + getId() + ")[" + status + "]";
    }
}
