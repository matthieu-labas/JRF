package net.jrf.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

import net.jrf.RemoteInputStream;
import net.jrf.RemoteOutputStream;
import net.jrf.msg.Message;
import net.jrf.msg.MsgAck;
import net.jrf.msg.MsgClose;
import net.jrf.msg.MsgData;
import net.jrf.msg.MsgFileCmd;
import net.jrf.msg.MsgGet;
import net.jrf.msg.MsgOpen;
import net.jrf.msg.MsgPing;
import net.jrf.server.JRFProvider;
import net.jrf.server.JRFServer;

/**
 * The JRF Client is used to send file commands to a {@linkplain JRFProvider JRF Server}.
 * 
 * @author Matthieu Labas
 */

// TODO: Create SocketFactory to use SSLSocket, UDTSocket

public class JRFClient extends Thread {
	
	private static final Logger log = Logger.getLogger(JRFClient.class.getName());
	
	/** The Socket timeout, in ms. */
	public static final int TIMEOUT = 1000;
	
	/** Socket connected to a {@link JRFProvider}. */
	private Socket sok;
	
	/** Map of remotely opened {@code InputStream}. Key is the file ID. */
	private Map<Integer,RemoteInputStream> remoteIS;
	
	/** Map of remotely opened {@code OutputStream}. Key is the file ID. */
	private Map<Integer,RemoteOutputStream> remoteOS;
	
	/** List of {@link Message}s received that can be queried by {@link RemoteInputStream}s looking
	 * for a reply message to their command message. */
	private List<Message> msgQueue;
	
	/** Latency accumulator, in µs. Average latency is {@code totLatency / nLatency}. */
	private long totLatency;
	/** Latency counter. */
	private int nLatency;
	
	private volatile boolean goOn;
	
	JRFClient(Socket sok) {
		setName("JRFClient "+sok.getLocalSocketAddress()+">"+sok.getRemoteSocketAddress());
		try {
			sok.setSoTimeout(TIMEOUT);
		} catch (SocketException e) {
			log.warning(getName()+": Unable to set timeout on "+sok+": "+e.getMessage());
		}
		try {
			sok.setKeepAlive(true);
		} catch (SocketException e) {
			log.warning(getName()+": Unable to set keepalive on "+sok+": "+e.getMessage());
		}
		this.sok = sok;
		remoteIS = new HashMap<>();
		remoteOS = new HashMap<>();
		msgQueue = new ArrayList<Message>();
		totLatency = 0;
		nLatency = 0;
		goOn = true;
	}
	
	/**
	 * Instantiate and connects a JRF Client to a {@linkplain JRFServer JRF Server}.
	 * @param addr The address of the JRF Server to connect to.
	 * @throws IOException
	 */
	public JRFClient(InetSocketAddress addr) throws IOException {
		this(new Socket(addr.getAddress(), addr.getPort()));
	}
	
	/**
	 * Add a latency for a command/reply. {@code t0} is in ns and is expected to be the
	 * {@link System#nanoTime()} before the {@code send()} command was issued:
	 * <pre>
	 * int num = jrfClient.send(msgOut); // Latency does not include serialization and socket send time
	 * long t0 = System.nanoTime();
	 * Message reply = jrfClient.getReply(num); // Latency includes remote processing, network receive, deserialization and synchronization time
	 * jrfClient.addLatencyNow(t0);
	 * </pre>
	 * That value is not a precise network latency as it includes different durations:
	 * <ol><li>Time spent in network sending</li>
	 * <li>Remote command processing (that could take a while)</li>
	 * <li>Time spent in network receiving</li>
	 * <li>Miscellaneous synchronization delays</li></ol>
	 * Therefore, it should be used on "control" messages only, when the expected command/reply message are
	 * small in size and processing.
	 * @param t0 The latency reference, in ns.
	 */
	public synchronized void addLatencyNow(long t0) {
		totLatency += (System.nanoTime() - t0) / 1000;
		nLatency++;
	}
	
	/**
	 * @return The average latency (time between command send timestamp and answer timestamp).
	 */
	public synchronized double getLatency() {
		return (double)totLatency / nLatency;
	}
	
	/** Reset the latency counters to start new measurements. */
	public synchronized void resetLatencyCounters() {
		totLatency = 0;
		nLatency = 0;
	}
	
	/**
	 * Called by {@code RemoteInputStream} when its stream has been closed, so it can be removed
	 * from {@link #remoteIS}.
	 * @param ris The closed {@code RemoteInputStream} to remove.
	 */
	public synchronized void remoteStreamClosed(RemoteInputStream ris) {
		remoteIS.remove(ris.getFileID());
	}
	
	/**
	 * Called by {@code RemoteOutputStream} when its stream has been closed, so it can be removed
	 * from {@link #remoteOS}.
	 * @param ros The closed {@code RemoteOutputStream} to remove.
	 */
	public synchronized void remoteStreamClosed(RemoteOutputStream ros) {
		remoteOS.remove(ros.getFileID());
	}
	
	/** Close all remotely opened files and gracefully disconnects from the JRF Server. */
	private synchronized void close() {
		for (RemoteInputStream ris : remoteIS.values()) {
			try {
				ris.close();
			} catch (IOException e) {
				log.warning(getName()+": Exception while closing remote read file "+ris+": "+e.getMessage());
			}
		}
		remoteIS.clear();
		
		for (RemoteOutputStream ros : remoteOS.values()) {
			try {
				ros.close();
			} catch (IOException e) {
				log.warning(getName()+": Exception while closing remote write file "+ros+": "+e.getMessage());
			}
		}
		remoteOS.clear();
		
		// Close the connection
		gracefulClose(sok, true);
	}
	
	/** Stops the Client and closes its connection to the JRF Server. */
	public void requestStop() {
		goOn = false;
		close();
	}
	
	/**
	 * Get a {@link RemoteInputStream} from the server.
	 * @param remoteFile The absolute path name of the file to retrieve, <em>as seen by the server</em>.
	 * @param deflate The deflate level to use when transferring file chunks. No compression is performed
	 * 		if {@code <= 0}.
	 * @return The {@code RemoteInputStream} (never {@code null}).
	 * @throws FileNotFoundException If the file was not found remotely.
	 * @throws IOException If a network error occurs.
	 */
	public RemoteInputStream getRemoteInputStream(String remoteFile, int deflate) throws IOException {
		short num = new MsgOpen(remoteFile, 'r', deflate).send(sok); // Remote open file
		long t0 = System.nanoTime();
		Message m = getReply(num, 0); // Wait for MsgAck to get file ID
		addLatencyNow(t0);
		if (m instanceof MsgAck) {
			MsgAck msg = (MsgAck)m;
			String err = msg.getMessage();
			if (err != null) {
				if (msg.getCode() == MsgAck.WARN) // File not found
					throw new FileNotFoundException(err);
				throw new IOException(err);
			}
			short fileID = msg.getFileID();
			RemoteInputStream ris = new RemoteInputStream(this, remoteFile, fileID);
			synchronized (this) {
				remoteIS.put(Integer.valueOf(fileID), ris);
			}
			return ris;
		} else {
			throw new IOException("Unexpected message "+remoteFile);
		}
	}
	
	/**
	 * Get a {@link RemoteInputStream} from the server without compression.
	 * @param remoteFile The absolute path name of the file to retrieve, <em>as seen by the server</em>.
	 * @return The {@code RemoteInputStream} (never {@code null}).
	 * @throws FileNotFoundException If the file was not found remotely.
	 * @throws IOException If a network error occurs.
	 */
	public RemoteInputStream getRemoteInputStream(String remoteFile) throws IOException {
		return getRemoteInputStream(remoteFile, 0);
	}
	
	/**
	 * Get a {@link RemoteOutputStream} to the server.
	 * @param remoteFile The absolute path name of the file to write to, <em>as seen by the server</em>.
	 * @param deflate The deflate level to use when transferring file chunks. No compression is performed
	 * 		if {@code <= 0}.
	 * @return The {@code RemoteOutputStream} (never {@code null}).
	 * @throws FileNotFoundException If the file could not be created remotely.
	 * @throws IOException If a network error occurs.
	 */
	public RemoteOutputStream getRemoteOutputStream(String remoteFile, int deflate) throws IOException {
		short num = new MsgOpen(remoteFile, 'w', deflate).send(sok); // Remote open file
		long t0 = System.nanoTime();
		Message m = getReply(num, 0); // Wait for MsgAck to get file ID
		addLatencyNow(t0);
		if (!(m instanceof MsgAck))
			throw new IOException("Unexpected message "+remoteFile);
		
		MsgAck msg = (MsgAck)m;
		String err = msg.getMessage();
		if (err != null) {
			if (msg.getCode() == MsgAck.WARN) // File not found
				throw new FileNotFoundException(err);
			throw new IOException(err);
		}
		short fileID = msg.getFileID();
		RemoteOutputStream ros = new RemoteOutputStream(this, remoteFile, fileID, deflate);
		synchronized (this) {
			remoteOS.put(Integer.valueOf(fileID), ros);
		}
		return ros;
	}
	
	/**
	 * Get a {@link RemoteOutputStream} to the server, without compression.
	 * @param remoteFile The absolute path name of the file to write to, <em>as seen by the server</em>.
	 * @return The {@code RemoteOutputStream} (never {@code null}).
	 * @throws FileNotFoundException If the file could not be created remotely.
	 * @throws IOException If a network error occurs.
	 */
	public RemoteOutputStream getRemoteOutputStream(String remoteFile) throws IOException {
		return getRemoteOutputStream(remoteFile, 0);
	}
	
	/**
	 * Send a command message to the remote {@link JRFProvider}.
	 * @param cmd The command message to send.
	 * @return The message number {@code cmd.num}.
	 * @throws IOException on error while sending.
	 */
	public short send(Message cmd) throws IOException {
		return cmd.send(sok);
	}
	
	/**
	 * <p>Retrieve the next reply-message to the given message number, so that
	 * {@code ret.replyTo == msgNum}. In all cases, if the message was already received, it will be
	 * returned immediately.</p>
	 * <p><ul>
	 * <li>If the given {@code timeout} is {@code < 0}, the call will return immediately the reply
	 * message if it was already received, or {@code null} if it wasn't, which can be useful to
	 * create an external active polling.</li>
	 * <li>If {@code 0}, the call will block until the reply message is received or an exception is
	 * thrown.</li>
	 * <li>If {@code > 0}, the call will block at most {@code timeout} ms until the reply message is
	 * received or an exception is thrown. If the message was not received during this {@code timeout},
	 * {@code null} will be returned.</li></p>
	 * @param msgNum The message number to which a reply message was sent.
	 * @param timeout {@code >= 0} if the call should be blocking, waiting for the reply message to
	 * 		arrive in the given {@code timeout} ms.
	 * @return The first received message, sent as a reply to message #{@code msgNum}, or {@code null}
	 * 		if no such message was received during the given {@code timeout}.
	 */
	public Message getReply(short msgNum, int timeout) {
		synchronized (msgQueue) {
			for (Iterator<Message> iter = msgQueue.iterator(); iter.hasNext();) {
				Message msg = iter.next();
				if (msg.getReplyTo() == msgNum) {
					iter.remove();
					return msg;
				}
			}
			// Message not in the queue
			if (timeout < 0)
				return null;
			
			// Wait for the next received message
			int sz = msgQueue.size(); // Next received message will be at this index
			long t0 = System.currentTimeMillis();
			for (;;) {
				long t = (timeout == 0 ? 0 : timeout - (System.currentTimeMillis() - t0));
				try { msgQueue.wait(t); } catch (InterruptedException e) { } // Already in synchronized (msgQueue)
				if (msgQueue.size() != sz) { // New message received
					Message msg = msgQueue.get(sz);
					if (msg.getReplyTo() == msgNum) { // Not for us: go back to sleep
						msgQueue.remove(sz);
						return msg;
					}
				}
				if (timeout > 0 && (System.currentTimeMillis() - t0) >= timeout) // Timeout expired: return null
					return null;
			}
		}
	}
	
	/**
	 * Performs a graceful close on the given Socket. Data still in the socket input buffer will be
	 * silently discarded.
	 * @param sok The socket to gracefully close.
	 * @param flush {@code true} if the socket input stream should be emptied.
	 * @see <a href="http://stackoverflow.com/a/9399617/1098603">http://stackoverflow.com/a/9399617/1098603</a> :)
	 */
	public static void gracefulClose(Socket sok, boolean flush) {
		if (sok.isClosed())
			return;
		try {
			sok.shutdownOutput(); // Send FIN, which might throw an IOException in run(), blocked on Message.receive(). Socket would then be closed.
			if (flush && !sok.isClosed()) {
				byte[] buf = new byte[1024];
				InputStream is = sok.getInputStream();
				while (is.read(buf) >= 0) ;
			}
		} catch (IOException e) {
			log.warning(Thread.currentThread().getName()+": Exception while gracefully closing "+sok+": "+e.getMessage());
		} finally {
			try {
				sok.close();
			} catch (IOException e) {
				log.warning(Thread.currentThread().getName()+": Exception while closing "+sok+": "+e.getMessage());
			}
		}
	}
	
	/**
	 * Retrieve a remote file completely, without using {@link RemoteInputStream}. This method is preferred when
	 * a whole file is to be copied locally, especially if compression is to be used, as it compresses the whole
	 * file content, not individual chunks (as performed by {@code RemoteInputStream}). It also allows for MTU
	 * sizing, when the network has such constraints.
	 * @param remote The remote file path.
	 * @param deflate The deflate value to apply remotely on the data.
	 * @param local The local file to write to.
	 * @param mtu The MTU to use to size packets sent remotely. N.B. that it actually is a <em>payload</em> MTU,
	 * 		not a network one (i.e. network headers such as TCP will be added), but those are constant and can be
	 * 		taken into account when calling the method.
	 * @return The number of <em>network</em> bytes received (which can be less than the actual file length,
	 * 		if compression is used).
	 * @throws IOException
	 */
	public long getFile(String remote, int deflate, String local, int mtu) throws IOException {
		short num = new MsgGet(remote, deflate, mtu).send(sok);
		long len = 0l;
		OutputStream os = new BufferedOutputStream(new FileOutputStream(local));
		Inflater infl = null;
		if (deflate > 0) {
			infl = new Inflater();
			os = new InflaterOutputStream(os, infl);
		}
		try {
			Message m;
			byte[] buf;
			for (;;) {
				m = getReply(num, 0); // Wait for MsgAck to get file ID
				if (m instanceof MsgAck) // Exception
					throw new IOException(((MsgAck)m).getMessage());
				if (!(m instanceof MsgData)) // Unknown message
					throw new IOException("Unexpected message during file GET: "+m);
				MsgData msg = (MsgData)m;
				buf = msg.getData();
				os.write(buf);
				len += buf.length; // Bytes received, network-wise (use infl.getBytesWritten() to get disk bytes i.e. actual file size)
				if (!msg.hasNext())
					break;
			}
		} finally {
			try {
				os.close();
			} catch (IOException e) { }
		}
		return len;
	}
	
	/**
	 * Sends a file completely, using a {@link RemoteOutputStream}.
	 * @param local The local file to write to.
	 * @param deflate The deflate value to apply on each data chunk.
	 * @param remote The remote file path.
	 * @param mtu The number of bytes to send for each packet. N.B. that if {@code deflate > 0}, that many
	 * 		bytes will be deflated (compressed) before being sent, resulting in smaller packets.
	 * @return The number of <em>network</em> bytes sent (which can be less than the actual file length,
	 * 		if compression is used).
	 * @throws IOException
	 */
	public long putFile(String local, int deflate, String remote, int mtu) throws IOException {
		// TODO: One day, implement a deflated putFile(), as in getFile(), but requires message queuing in JRFProvider
		long len = 0l;
		byte[] buf = new byte[mtu];
		try (RemoteOutputStream os = getRemoteOutputStream(remote, deflate)) {
			try (InputStream is = new BufferedInputStream(new FileInputStream(local), 2*buf.length)) {
				int n;
				for (;;) {
					n = is.read(buf);
					if (n < 0)
						break;
					os.write(buf, 0, n);
					len += n;
				}
			}
		}
		return len;
	}
	
	@Override
	public void run() {
		while (goOn) {
			try {
				log.fine(getName()+": waiting for message...");
				Message msg = Message.receive(sok);
				log.fine(getName()+": received message "+msg);
				if (msg.getReplyTo() > 0) { // Reply message: add it to msgQueue and wakeup all RemoteInputStreams waiting for a reply
					synchronized (msgQueue) {
						msgQueue.add(msg);
						msgQueue.notifyAll();
					}
					continue;
				}
				
				// Messages spontaneously sent by the JRFFileProvider
				if (msg instanceof MsgFileCmd) { // Default case: file not found locally: close it remotely
					Integer fileID = Integer.valueOf(((MsgFileCmd)msg).getFileID());
					RemoteInputStream ris = remoteIS.get(fileID);
					if (ris == null) { // Cannot find client: send a close()
						log.warning(getName()+": Cannot find remote opened file with ID "+fileID+", closing file... (message "+msg+")");
						new MsgClose(fileID.shortValue()).send(sok);
					} else // Handle "spontaneous" messages
						ris.spontaneousMessage(msg);
					
				} else if (msg instanceof MsgPing) {
					new MsgPing(msg.getNum()).send(sok);
					
				} else {
					log.severe(getName()+": Unknown message "+msg);
				}
			} catch (SocketTimeoutException e) {
				log.finest(getName()+": timeout...");
				continue;
			} catch (IOException e) {
				if (goOn) {
					log.warning(getName()+": Exception while processing messages. Closing connection: "+e.getClass().getSimpleName()+" - "+e.getMessage());
					goOn = false;
				}
				break;
			}
		}
		
		// End of thread: close all remainig remote files and terminate connection gracefully
		close();
		log.info(getName()+": closed.");
	}
	
	
	
	public static void usage() {
		System.out.println("Options: <hostname[:port]> of the JRF Server to connect to.");
	}
	
	public static void main(String[] args) {
		if (args.length != 1) {
			usage();
			System.exit(1);
		}
		
		int port = JRFServer.DEFAULT_PORT;
		String[] hp = args[0].split(":");
		if (hp.length > 1) {
			try {
				port = Integer.parseInt(hp[1]);
			} catch (NumberFormatException e) {
				System.err.println("Cannot parse port '"+hp[1]+"'");
				System.exit(2);
			}
		}
		
		final JRFClientCLI cli;
		try {
			JRFClient cl = new JRFClient(new InetSocketAddress(hp[0], port));
			cl.start();
			cli = new JRFClientCLI(cl);
		} catch (IOException e) {
			System.err.print("Cannot connect to "+args[0]+": "+e.getMessage());
			System.exit(2);
			return;
		}
		System.out.println("Connected to "+args[0]);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override public void run() {
				cli.stop();
			}
		});
		cli.run();
	}
	
}
