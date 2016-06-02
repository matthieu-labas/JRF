package fr.jfp;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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

/**
 * The JFP Client is used to send file commands to a {@link JFPProvider}.
 * 
 * @author Matthieu Labas
 */
public class JFPClient extends Thread {
	
	private static final Logger log = Logger.getLogger(JFPClient.class.getName());
	
	private Socket sok;
	
	/** Map of remotely open files. Key is the file ID. */
	private Map<Integer,RemoteInputStream> remoteOpened;
	
	/** List of {@link Message}s received that can be queried by {@link RemoteInputStream}s looking
	 * for a reply message to their command message. */
	private List<Message> msgQueue;
	
	private volatile boolean goOn;
	
	JFPClient(Socket sok) {
		setName("JFPClient "+sok.getLocalSocketAddress()+">"+sok.getRemoteSocketAddress());
		try {
			sok.setKeepAlive(true);
		} catch (SocketException e) {
			log.warning(getName()+": Unable to set keepalive on "+sok+": "+e.getMessage());
		}
		this.sok = sok;
		remoteOpened = new HashMap<>();
		msgQueue = new ArrayList<Message>();
		goOn = true;
	}
	
	public JFPClient(InetSocketAddress addr) throws IOException {
		this(new Socket(addr.getAddress(), addr.getPort()));
	}
	
	/**
	 * Called by {@code RemoteInputStream} when its stream has been closed, so it can be removed
	 * from {@link #remoteOpened}.
	 * @param ris The closed {@code RemoteInputStream} to remove.
	 */
	synchronized void risClosed(RemoteInputStream ris) {
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
		gracefulClose(sok);
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
		MsgOpen mo = new MsgOpen(remoteFile, 0);
		mo.send(sok); // Remote open file
		Message m = getReply(mo.num, 0); // Wait for MsgAck to get file ID
		if (m instanceof MsgAck) {
			MsgAck msg = (MsgAck)m;
			if (msg.msg != null) {
				if (msg.code == MsgAck.WARN) // File not found
					throw new FileNotFoundException(msg.msg);
				throw new IOException(msg.msg);
			}
			RemoteInputStream ris = new RemoteInputStream(remoteFile, msg.fileID, this);
			synchronized (this) {
				remoteOpened.put(Integer.valueOf(msg.fileID), ris);
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
	int send(Message cmd) throws IOException {
		cmd.send(sok);
		return cmd.num;
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
	Message getReply(int msgNum, int timeout) {
		synchronized (msgQueue) {
			for (Iterator<Message> iter = msgQueue.iterator(); iter.hasNext();) {
				Message msg = iter.next();
				if (msg.replyTo == msgNum) {
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
					if (msg.replyTo == msgNum) { // Not for us: go back to sleep
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
	 * @see <a href="http://stackoverflow.com/a/9399617/1098603">http://stackoverflow.com/a/9399617/1098603</a> :)
	 */
	public static void gracefulClose(Socket sok) {
		try {
			sok.shutdownOutput(); // Send FIN, which might close the socket and throw an IOException in run(), blocked on Message.receive(). Socket would then be closed.
			if (!sok.isClosed()) {
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
	
	@Override
	public void run() {
		while (goOn) {
			try {
				log.fine(getName()+": waiting for message...");
				Message msg = Message.receive(sok);
				log.fine(getName()+": received message "+msg);
				if (msg.replyTo > 0) { // Reply message: add it to msgQueue and wakeup all RemoteInputStreams waiting for a reply
					synchronized (msgQueue) {
						msgQueue.add(msg);
						msgQueue.notifyAll();
					}
					continue;
				}
				
				// Messages spontaneously sent by the JFPFileProvider
				if (msg instanceof MsgFile) { // Default case: file not found locally: close it remotely
					Integer fileID = Integer.valueOf(((MsgFile)msg).fileID);
					RemoteInputStream ris = remoteOpened.get(fileID);
					if (ris == null) { // Cannot find client: send a close()
						log.warning(getName()+": Cannot find remote opened file with ID "+fileID+", closing file... (message "+msg+")");
						new MsgClose(fileID.intValue()).send(sok);
					} else { // Handle "spontaneous" messages
						ris.spontaneousMessage(msg);
					}
				} else {
					log.severe(getName()+": Unknown message "+msg);
				}
			} catch (SocketTimeoutException e) {
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
	
}
