package network.data;

import java.io.Serializable;

/**
 * This class is used to send some kind of message, relating to a job that was sent from another machine in the cluster. 
 * 
 * @author Erik Krogh Kristensen
 *
 */
public class ClusterMessage implements Serializable {
	private static final long serialVersionUID = 1L;
	 // The type of messages that can be transmitted.
	public enum Message {
		ABORT, // Terminate the job that i sent you earlier. 
		WORK_CLASSEXCEPTION, // I got some kind of classException when trying to translate the work you just send.
		RESULT_CLASSEXCEPTION, // -||- result -||-.
	};
	private Message msg; // The message is an enum, telling what we want to say. 
	private long id; // The id of the job that this message is about.
	/**
	 * 
	 * @param msg The message is an enum, telling what we want to say. 
	 * @param id The id of the job that this message is about. This id fits the id of the job on the machine this message is received on.
	 */
	public ClusterMessage(Message msg, long id)
	{
		this.id = id;
		this.msg = msg;
	}
	/**
	 * @return The message, an enum telling what we want to say. 
	 */
	public Message getMessage()
	{
		return msg;
	}
	/**
	 * @return The id that tells what job/result this message is about. This id fits the id of the job on the machine this message is received on. 
	 */
	public long getId()
	{
		return id;
	}
	
}
