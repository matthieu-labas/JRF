package fr.jfp.client;

import java.io.BufferedOutputStream;
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

import fr.jfp.RemoteInputStream;
import fr.jfp.msg.Message;
import fr.jfp.msg.MsgAck;
import fr.jfp.msg.MsgClose;
import fr.jfp.msg.MsgData;
import fr.jfp.msg.MsgFileCmd;
import fr.jfp.msg.MsgGet;
import fr.jfp.msg.MsgOpen;
import fr.jfp.server.JFPProvider;
import fr.jfp.server.JFPServer;

/**
 * The JFP Client is used to send file commands to a {@link JFPProvider}.
 * 
 * @author Matthieu Labas
 */

// TODO: MTU setting to split potentially big messages (e.g. MsgData, MsgReplyFileList) into several smaller parts
// TODO: Handle deflate compression when required upon opening
// TODO: Create SocketFactory to use SSLSocket, UDTSocket

public class JFPClient extends Thread {
	
	private static final Logger log = Logger.getLogger(JFPClient.class.getName());
	
	public static final int TIMEOUT = 1000;
	
	private Socket sok;
	
	/** Map of remotely open files. Key is the file ID. */
	private Map<Integer,RemoteInputStream> remoteOpened;
	
	/** List of {@link Message}s received that can be queried by {@link RemoteInputStream}s looking
	 * for a reply message to their command message. */
	private List<Message> msgQueue;
	
	/** Latency accumulator, in µs. Average latency is {@code totLatency / nLatency}. */
	private long totLatency;
	/** Latency counter. */
	private int nLatency;
	
	private volatile boolean goOn;
	
	JFPClient(Socket sok) {
		setName("JFPClient "+sok.getLocalSocketAddress()+">"+sok.getRemoteSocketAddress());
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
		remoteOpened = new HashMap<>();
		msgQueue = new ArrayList<Message>();
		totLatency = 0;
		nLatency = 0;
		goOn = true;
	}
	
	public JFPClient(InetSocketAddress addr) throws IOException {
		this(new Socket(addr.getAddress(), addr.getPort()));
	}
	
	/**
	 * Add a latency for a command/reply. {@code t0} is in ns and is expected to be the
	 * {@link System#nanoTime()} before the {@code send()} command was issued:
	 * <pre>
	 * long t0 = System.nanoTime();
	 * int num = jfpClient.send(msgOut); // Latency also includes serialization and socket send time
	 * Message reply = jfpClient.getReply(num); // Latency also includes network receive, deserialization and synchronization time
	 * jfpClient.addLatencyNow(t0);
	 * </pre>
	 * That value is not a precise network latency because it also include the time taken receiving
	 * a message (bandwidth) and thread synchronization delays. Therefore, it should be used on
	 * "control" messages only, when the expected command/reply message are small in size and processing.
	 * @param t0 The latency to add, in µs.
	 */
	public synchronized void addLatencyNow(long t0) {
		totLatency += (System.nanoTime() - t0) / 1000;
		nLatency++;
	}
	
	/**
	 * @return The average latency (time between command send timestamp and answer timestamp).
	 */
	public synchronized  double getLatency() {
		return (double)totLatency / nLatency;
	}
	
	/** Reset the latency counters to start new measurements. */
	public synchronized void resetLatencyCounters() {
		totLatency = 0;
		nLatency = 0;
	}
	
	/**
	 * Called by {@code RemoteInputStream} when its stream has been closed, so it can be removed
	 * from {@link #remoteOpened}.
	 * @param ris The closed {@code RemoteInputStream} to remove.
	 */
	public synchronized void risClosed(RemoteInputStream ris) {
		remoteOpened.remove(ris.getFileID());
	}
	
	/** Close all remotely opened files. */
	private synchronized void close() {
		for (RemoteInputStream ris : remoteOpened.values()) {
			try {
				ris.close();
			} catch (IOException e) {
				log.warning(getName()+": Exception while closing remote file "+ris+": "+e.getMessage());
			}
		}
		remoteOpened.clear();
		
		// Close the connection
		gracefulClose(sok, true);
	}
	
	/** Stops the Client and closes its connection to the Server. */
	public void requestStop() {
		goOn = false;
		close();
	}
	
	/**
	 * Get a {@link RemoteInputStream} from the server.
	 * @param remoteFile The absolute path name of the file to retrieve, <em>as seen by the server</em>.
	 * @return The {@code RemoteInputStream} (never {@code null}).
	 * @throws FileNotFoundException If the file was not found remotely.
	 * @throws IOException If a network error occurs.
	 */
	public InputStream getRemoteInputStream(String remoteFile) throws IOException {
		long t0 = System.nanoTime();
		short num = new MsgOpen(remoteFile, 0).send(sok); // Remote open file
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
			RemoteInputStream ris = new RemoteInputStream(remoteFile, fileID, this);
			synchronized (this) {
				remoteOpened.put(Integer.valueOf(fileID), ris);
			}
			return ris;
		} else {
			throw new IOException("Unexpected message "+remoteFile);
		}
	}
	
	/**
	 * Send a command message to the remote {@link JFPProvider}.
	 * @param cmd The command message to send.
	 * @return The message number {@code cmd.num}.
	 * @throws IOException on error while sending.
	 */
	public short send(Message cmd) throws IOException {
		cmd.send(sok);
		return cmd.getNum();
	}
	
	/**
	 * <p>Get received message sent as a reply to the given message number, so that
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
			sok.shutdownOutput(); // Send FIN, which might close the socket and throw an IOException in run(), blocked on Message.receive(). Socket would then be closed.
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
	 * Retrieve a remote file.
	 * @param remote The remote file path.
	 * @param deflate The deflate value to apply remotely on the data.
	 * @param local The local file to write to.
	 * @throws IOException
	 */
	public long getFile(String remote, int deflate, String local, int mtu) throws IOException {
		short num = new MsgGet(remote, deflate, mtu).send(sok);
		long len = 0l;
		try (OutputStream os = new BufferedOutputStream(new FileOutputStream(local))) {
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
				if (msg.getDeflate() > 0)
					buf = MsgData.inflate(buf);
				os.write(buf);
				len += buf.length;
				if (!msg.hasNext())
					break;
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
				
				// Messages spontaneously sent by the JFPFileProvider
				if (msg instanceof MsgFileCmd) { // Default case: file not found locally: close it remotely
					Integer fileID = Integer.valueOf(((MsgFileCmd)msg).getFileID());
					RemoteInputStream ris = remoteOpened.get(fileID);
					if (ris == null) { // Cannot find client: send a close()
						log.warning(getName()+": Cannot find remote opened file with ID "+fileID+", closing file... (message "+msg+")");
						new MsgClose(fileID.shortValue()).send(sok);
					} else { // Handle "spontaneous" messages
						ris.spontaneousMessage(msg);
					}
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
		System.out.println("Options: <hostname[:port]> of the JFP Server to connect to.");
	}
	
	public static void main(String[] args) {
		if (args.length != 1) {
			usage();
			System.exit(1);
		}
		
		int port = JFPServer.DEFAULT_PORT;
		String[] hp = args[0].split(":");
		if (hp.length > 1) {
			try {
				port = Integer.parseInt(hp[1]);
			} catch (NumberFormatException e) {
				System.err.println("Cannot parse port '"+hp[1]+"'");
				System.exit(2);
			}
		}
		
		final JFPClientCLI cli;
		try {
			JFPClient cl = new JFPClient(new InetSocketAddress(hp[0], port));
			cl.start();
			cli = new JFPClientCLI(cl);
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
