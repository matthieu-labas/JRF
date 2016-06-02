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
	
	/** Map of remotely open files. */
	private Map<Integer,RemoteInputStream> remoteOpened;
	
	/** List of {@link Message}s received that can be queried by {@link RemoteInputStream}s looking
	 * for a reply-message to their command message. */
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
		
		// Close the connection
		gracefulClose();
	}
	
	public void requestStop() {
		goOn = false;
		gracefulClose();
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
				if (msg.code == MsgAck.WARN)
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
	 * Send a message and potentially process a message received as a reply to the message sent.
	 * @param ris The {@code RemoteInputStream} sending the command.
	 * @param cmd The command message to send.
	 * @return The message number.
	 * @throws IOException on error while sending.
	 */
	int send(Message cmd) throws IOException {
		cmd.send(sok);
		return cmd.num;
	}
	
	/**
	 * Get received message sent as a reply to the given message number, so that the returned
	 * message {@code replyTo} member equals the given {@code msgNum}.
	 * @param msgNum The message number to which a reply message was sent.
	 * @param timeout {@code >= 0} if the call should be blocking, waiting for the reply message to
	 * 		arrive in the given {@code timeout} ms.
	 * @return The first received message, sent as a reply to message #{@code msgNum}.
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
				try { msgQueue.wait(t); } catch (InterruptedException e) { }
				if (msgQueue.size() != sz) { // New message received
					Message msg = msgQueue.get(sz);
					if (msg.replyTo == msgNum) {
						msgQueue.remove(sz);
						return msg;
					}
				}
				// Timeout expired: return null
				if (timeout > 0 && (System.currentTimeMillis() - t0) >= timeout)
					return null;
			}
		}
	}
	
	/**
	 * Performs a graceful close on the given Socket. Data still in the socket input buffer will be
	 * discarded.
	 * @param sok The socket to gracefully close.
	 * @see http://stackoverflow.com/a/9399617/1098603 :)
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
	
	private void gracefulClose() {
		gracefulClose(sok);
	}
	
	@Override
	public void run() {
		while (goOn) {
			try {
				log.fine(getName()+": waiting for message...");
				Message msg = Message.receive(sok);
				log.fine(getName()+": received message "+msg);
				if (msg.replyTo > 0) { // Reply message: add it to the list and wakeup all RemoteInputStreams waiting for a reply
					synchronized (msgQueue) {
						msgQueue.add(msg);
						msgQueue.notifyAll();
						continue;
					}
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
					log.severe("Unknown message "+msg);
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
