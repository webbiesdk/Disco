package network;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class ObjectSender{
	public static void main(String[] args)
	{
		final ServerListOld list = new ServerListOld(4445, 4000);
		if (args[0].equals("send"))
		{
			final ArrayList<String> sendList = new ArrayList<String>();
			sendList.add("Dette er en tekst");
			list.addAddedIpObserver(new IPChangedObserver() {
                @Override
                public void ipChanged(InetAddress address) {
                    sendTo(sendList, address);
                    list.removeAddedIpListener(this);
                    list.close();
                }
            });
			list.start();
		}
		else
		{
			list.start();
			System.out.println(recvObjFrom());
			list.close();
		}
	}
	static int port = 4040;
	public static void sendTo(Object o, InetAddress address) {
		DatagramSocket dSock = null;
		try {
			dSock = new DatagramSocket(port);
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream(5000);
			ObjectOutputStream os = new ObjectOutputStream(new BufferedOutputStream(byteStream));
			os.flush();
			os.writeObject(o);
			os.flush();
			// retrieves byte array
			byte[] sendBuf = byteStream.toByteArray();
			DatagramPacket packet = new DatagramPacket(sendBuf, sendBuf.length,address, port);
			dSock.send(packet);
			os.close();
		} catch (UnknownHostException e) {
			System.err.println("Exception:  " + e);
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			dSock.close();
		}
	}

	public static Object recvObjFrom() {
		DatagramSocket dSock = null;
		try {
			dSock = new DatagramSocket(port);
			byte[] recvBuf = new byte[5000];
			DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
			dSock.receive(packet);
			ByteArrayInputStream byteStream = new ByteArrayInputStream(recvBuf);
			ObjectInputStream is = new ObjectInputStream(new BufferedInputStream(byteStream));
			Object o = is.readObject();
			is.close();
			return (o);
		} catch (IOException e) {
			System.err.println("Exception:  " + e);
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		finally
		{
			dSock.close();
		}
		return (null);
	}
}
