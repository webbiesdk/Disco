package network.data;

import org.jgroups.Address;

import model.WorkContainer;

/**
 * This represents a job that has been received from another client. 
 * It hold the original id (the id that the job had on the client it came from) and what machine it came from. 
 * 
 * @author Erik Krogh Kristensen
 *
 * @param <E>
 */
public class ReceivedJob<E> {
	private WorkContainer<E> work;
	private long orgId;
	private Address address;
	/**
	 * @param work The job associated with this received jobs. 
	 * @param orgId The id the job had on the machine it came from.  
	 * @param address The address of the machine the job came from. 
	 */
	public ReceivedJob(WorkContainer<E> work, long orgId, Address address)
	{
		this.work = work;
		this.orgId = orgId;
		this.address = address;
	}
	/**
	 * @return the WorkContainer associated with this received job. 
	 */
	public WorkContainer<E> getWork() {
		return work;
	}
	/**
	 * @return the id the job had on the machine it came from.
	 */
	public long getOrgId() {
		return orgId;
	}
	/**
	 * @return the address of the machine where the job came from. 
	 */
	public Address getAddress() {
		return address;
	}
	@Override
	public String toString()
	{
		return getWork().toString();
	}
}
