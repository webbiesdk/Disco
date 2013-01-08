package model;

/**
 * // TODO: Doc
 *
 * @author Erik
 * @date 07-01-13, 22:34
 */
public interface JobQueue<E> {
    public Job<E> takeLocal();

    Job<E> takeRemote();

    public void put(Job<E> job);
}
