package network;

import io.RuntimeClassLoader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;


/*
 * Everything in here are TCP connections. 
 */
public class NetworkTools {
	public static void main(String[] args) throws UnknownHostException, IOException, ClassNotFoundException {
		if (args[0].equals("send")) {
			// Making the stuff to send, can be any object. 
			ArrayList<String> send = new ArrayList<String>();
			send.add("String1");
			send.add("String2");
			
			sendObjectToClient(InetAddress.getByName("192.168.80.148"), 6789, send);
			
		}
		else 
		{
			// Socket to be permanently open. 
			ServerSocket recieveSocket = new ServerSocket(6789);
			// This one is just for show atm. 
			RuntimeClassLoader loader = new RuntimeClassLoader(new HashMap<String,byte[]>());

			while (true) {
				System.out.println(recieveObjectFromClient(recieveSocket, loader));
			}
		}
	}
	/*
	 * The returned array contains the following:
	 * 0: The object that was received from the client.
	 * 1: The InetAddress of the client that sent it. 
	 */
	public static Object[] recieveObjectFromClient(ServerSocket socket, ClassLoader loader) throws IOException, ClassNotFoundException
	{
		// Accept a new connection.
		Socket connectionSocket = socket.accept();
		// Lets open a inputstream. 
		BufferedInputStream inFromClient = new BufferedInputStream(connectionSocket.getInputStream());
		// Using that to create a objectInputstream
		ObjectInputStream is = new io.ObjectInputStream(inFromClient, loader);
		// And from that, we read an object. 
		Object object = is.readObject();
		is.close();
		// Where did it come from?
		InetAddress from = connectionSocket.getInetAddress();
		// Putting it all together. 
		Object[] res = {object, from}; 
		return res;
	}
	public static Object getObjectFormServer(InetAddress client, int port, int timeout) throws SocketException, IOException, ClassNotFoundException
	{
		// Opening the socket
		Socket clientSocket = new Socket(client, port);
		// Setting the timeout. 
		clientSocket.setSoTimeout(timeout);
		// Getting a reader
		BufferedInputStream inFromClient = new BufferedInputStream(clientSocket.getInputStream());
		// Geting a something from wich i load an object. 
		ObjectInputStream is = new io.ObjectInputStream(inFromClient, ClassLoader.getSystemClassLoader());
		// Reading the damn thing.
		Object object = is.readObject();
		// Returning. 
		is.close();
		clientSocket.close();
		return object;
	}
	/*
	 * This method only sends the object once, but it doesn't close the socket, so just call it again for repeat. 
	 */
	public static void sendWhenRequested(ServerSocket socket, Object o) throws IOException
	{
		// Accept a new connection.
		Socket connectionSocket = socket.accept();
		// Lets write. 
		new DataOutputStream(connectionSocket.getOutputStream()).write(objectToBytes(o));
	}
	/*
	 * This one sends the object returned by the callable. 
	 */
	public static <V> void sendWhenRequested(ServerSocket socket, Callable<V> callable) throws Exception
	{
		// Accept a new connection.
		Socket connectionSocket = socket.accept();
		// Lets write. 
		new DataOutputStream(connectionSocket.getOutputStream()).write(objectToBytes(callable.call()));
	}
	public static void sendObjectToClient(InetAddress client, int port, Object o) throws IOException
	{
		// Opening, can also open with a InetAddress
		Socket clientSocket = new Socket(client, port);
		// Sending the package. 
		new DataOutputStream(clientSocket.getOutputStream()).write(objectToBytes(o));
		// Closing the socket. 
		clientSocket.close();
	}
	public static Object bytesToObject(byte[] bytes, ClassLoader classloader) throws IOException, ClassNotFoundException
	{
		ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
		ObjectInputStream is = new io.ObjectInputStream(new BufferedInputStream(byteStream), classloader);
		Object object = is.readObject();
		is.close();
		return object;
	}
	public static byte[] objectToBytes(Object o) throws IOException {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		ObjectOutputStream os = new ObjectOutputStream(new BufferedOutputStream(byteStream));
		os.flush();
		os.writeObject(o);
		os.flush();
		return byteStream.toByteArray();
	}
}
