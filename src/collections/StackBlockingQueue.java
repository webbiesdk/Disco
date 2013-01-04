package collections;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * This class takes a dequeue in the constructor, and creates a interface to that queue that works like a stack. 
 * So the method take() actually takes the last from the queue. 
 * But besides from that, its a normal blockingqueue. 
 * @author Erik
 *
 */
public class StackBlockingQueue<E> implements BlockingQueue<E>{
	BlockingDeque<E> deque;
	public StackBlockingQueue(BlockingDeque<E> deque)
	{
		this.deque = deque;
	}
	@Override
	public E take() throws InterruptedException {
		return deque.takeLast();
	}
	@Override
	public E element() {
		return deque.element();
	}
	@Override
	public E peek() {
		return deque.peek();
	}
	@Override
	public E poll() {
		return deque.poll();
	}
	@Override
	public E remove() {
		return deque.remove();
	}
	@Override
	public boolean addAll(Collection<? extends E> c) {
		return deque.addAll(c);
	}
	@Override
	public void clear() {
		deque.clear();
	}
	@Override
	public boolean containsAll(Collection<?> c) {
		return deque.containsAll(c);
	}
	@Override
	public boolean isEmpty() {
		return deque.isEmpty();
	}
	@Override
	public Iterator<E> iterator() {
		return deque.iterator();
	}
	@Override
	public boolean removeAll(Collection<?> c) {
		return deque.removeAll(c);
	}
	@Override
	public boolean retainAll(Collection<?> c) {
		return deque.retainAll(c);
	}
	@Override
	public int size() {
		return deque.size();
	}
	@Override
	public Object[] toArray() {
		return deque.toArray();
	}
	@Override
	public <T> T[] toArray(T[] a) {
		return deque.toArray(a);
	}
	@Override
	public boolean add(E e) {
		return deque.add(e);
	}
	@Override
	public boolean contains(Object o) {
		return deque.contains(o);
	}
	@Override
	public int drainTo(Collection<? super E> c) {
		return deque.drainTo(c);
	}
	@Override
	public int drainTo(Collection<? super E> c, int maxElements) {
		return deque.drainTo(c, maxElements);
	}
	@Override
	public boolean offer(E e) {
		return deque.offer(e);
	}
	@Override
	public boolean offer(E e, long timeout, TimeUnit unit)
			throws InterruptedException {
		return deque.offer(e, timeout, unit);
	}
	@Override
	public E poll(long timeout, TimeUnit unit) throws InterruptedException {
		return deque.poll();
	}
	@Override
	public void put(E e) throws InterruptedException {
		deque.put(e);
	}
	@Override
	public int remainingCapacity() {
		return deque.remainingCapacity();
	}
	@Override
	public boolean remove(Object o) {
		return deque.remove(o);
	}
}
