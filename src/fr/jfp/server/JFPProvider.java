package fr.jfp.server;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.jfp.RemoteInputStream;
import fr.jfp.client.JFPClient;
import fr.jfp.messages.Message;
import fr.jfp.messages.MsgAck;
import fr.jfp.messages.MsgClose;
import fr.jfp.messages.MsgData;
import fr.jfp.messages.MsgOpen;
import fr.jfp.messages.MsgRead;
import fr.jfp.messages.MsgSkip;

/**
 * <p>The JFP Provider is the {@link JFPClient} Server counterpart, receiving file commands.</p>
 * <p>It keeps a list of locally opened files, corresponding to {@link RemoteInputStream}s on the
 * client side.</p>
 * 
 * @author Matthieu Labas
 */
public class JFPProvider extends Thread {
	
	private static final Logger log = Logger.getLogger(JFPProvider.class.getName());
	
	private static final AtomicInteger fileCounter = new AtomicInteger();
	
	private Socket sok;
	
	/** Map of locally open files. Key is the file ID. */
	private Map<Integer,InputStream> localOpened;
	
	private volatile boolean goOn;
	
	/**
	 * Create a {@ode JFPProvider} serving files to a remote {@link JFPClient} connected through the
	 * given socket.
	 * @param sok The connection to the remote {@code JFPClient}.
	 */
	JFPProvider(Socket sok) {
		setName(JFPProvider.class.getSimpleName()+"/"+sa2Str((InetSocketAddress)sok.getLocalSocketAddress())+">"+sa2Str((InetSocketAddress)sok.getRemoteSocketAddress()));
		try {
			sok.setKeepAlive(true);
		} catch (SocketException e) {
			log.warning(getName()+": Unable to set keepalive on "+sok+": "+e.getMessage());
		}
		this.sok = sok;
		localOpened = new HashMap<>();
		goOn = true;
	}
	
	/**
	 * @param addr
	 * @return {@code addr} in dotted numeric format (no DNS lookup).
	 */
	private static String sa2Str(InetSocketAddress addr) {
		return addr.getAddress().getHostAddress()+":"+addr.getPort();
	}
	
	private synchronized void close() {
		// Close all locally opened files
		for (InputStream is : localOpened.values()) {
			try {
				is.close();
			} catch (IOException e) {
				log.warning(getName()+": Exception while closing local file "+is+": "+e.getMessage());
			}
		}
		
		// Close the connection
		JFPClient.gracefulClose(sok);
	}
	
	public void requestStop() {
		goOn = false;
		close();
	}
	
	// "Open file" command
	private void handleOpen(MsgOpen m) throws IOException {
		int num = m.getNum();
		String file = m.getFile();
		log.info(getName()+": Request open file "+file);
		MsgAck ack;
		try {
			InputStream is = new FileInputStream(file);
			ack = new MsgAck(num, fileCounter.incrementAndGet());
			log.fine(getName()+": "+file+" > ID "+ack.getFileID());
			localOpened.put(ack.getFileID(), is);
		} catch (FileNotFoundException e) {
			log.warning(getName()+": File not found "+file);
			ack = new MsgAck(num, -1, MsgAck.WARN, e.getMessage());
		}
		try {
			ack.send(sok);
		} catch (IOException e) {
			log.warning(getName()+": Unable to send open-Ack event back to requestor: "+e.getMessage());
			throw e;
		}
	}
	
	// "Skip from file" command
	private void handleSkip(MsgSkip m) throws IOException {
		int num = m.getNum();
		int fileID = m.getFileID();
		long skip = m.getSkip();
		log.info(getName()+": Request skip "+skip+" bytes on file "+fileID);
		MsgAck ack;
		InputStream is = localOpened.get(fileID);
		if (is == null) { // File descriptor not found
			log.warning(getName()+": Local file ID "+fileID+" not found");
			ack = new MsgAck(num, fileID, MsgAck.WARN, "File not found");
		} else {
			try {
				ack = new MsgAck(num, fileID, (int)is.skip(skip), null); // Perform skip
				log.fine(getName()+": Actually skipped "+ack.getCode()+" bytes from file "+fileID);
			} catch (IOException e) { // Exception during skip
				String msg = e.getMessage();
				log.warning(getName()+": Error when skipping "+skip+" bytes in file ID "+fileID+": "+msg);
				ack = new MsgAck(num, fileID, MsgAck.ERR, msg);
			}
		}
		try {
			ack.send(sok);
		} catch (IOException e) {
			log.warning(getName()+": Unable to send skip-Ack event back to requestor: "+e.getMessage());
			throw e;
		}
	}
	
	// "File read" command
	private void handleRead(MsgRead m) throws IOException {
		int num = m.getNum();
		int fileID = m.getFileID();
		int len = m.getLength();
		log.info(getName()+": Request read "+len+" bytes from file "+fileID);
		MsgAck ack = null;
		MsgData data = null;
		InputStream is = localOpened.get(fileID);
		if (is == null) { // File descriptor not found
			log.warning(getName()+": Local file ID "+fileID+" not found");
			ack = new MsgAck(num, fileID, MsgAck.WARN, "File not found");
		} else {
			int n = 0;
			try {
				byte[] buf = new byte[len];
				while (n < len) {
					int r = is.read(buf, n, len-n);
					if (r <= 0)
						break;
					n += r;
				}
				data = new MsgData(num, fileID, buf, n, 0); // TODO: Handle deflate
				log.fine(getName()+": read "+n+" bytes from file "+fileID);
			} catch (IOException e) { // Exception during read
				String msg = e.getMessage();
				log.warning(getName()+": Error when reading "+n+"/"+len+" bytes from file ID "+fileID+": "+msg);
				ack = new MsgAck(num, fileID, MsgAck.ERR, msg);
			}
		}
		try {
			if (data != null)
				data.send(sok);
			else
				ack.send(sok);
		} catch (IOException e) {
			log.warning(getName()+": Unable to send read-"+(data!=null?"Data":"Ack")+"event back to requestor: "+e.getMessage());
			throw e;
		}
	}
	
	// "File close" command
	private void handleClose(MsgClose m) throws IOException {
		int fileID = m.getFileID();
		log.info(getName()+": Request close file "+fileID);
		InputStream is = localOpened.get(m.getFileID());
		if (is == null) { // File descriptor not found
			log.warning(getName()+": Local file ID "+fileID+" not found");
		} else {
			try {
				is.close();
				log.fine(getName()+": Closed file "+fileID);
			} catch (IOException e) { // Exception during skip
				log.warning(getName()+": Error when closing file ID "+fileID+": "+e.getMessage());
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
				
				// Command messages
				if (msg instanceof MsgOpen) { // Open file: reply with MsgAck to reply with file ID
					handleOpen((MsgOpen)msg);
				} else if (msg instanceof MsgRead) { // Read in file: reply with MsgData, or MsgAck upon exception
					handleRead((MsgRead)msg);
				} else if (msg instanceof MsgSkip) { // Skip in file: reply with MsgAck
					handleSkip((MsgSkip)msg);
				} else if (msg instanceof MsgClose) { // Close file: no reply
					handleClose((MsgClose)msg);
				} else { // Unknown
					log.warning(getName()+": Don't know how to handle file message "+msg);
				}
			} catch (SocketTimeoutException e) {
				continue;
			} catch (EOFException e) { // FIN received: graceful disconnection
				log.info(getName()+": "+sok.getRemoteSocketAddress()+" disconnected. Ending.");
				goOn = false;
			} catch (IOException e) {
				log.log(Level.SEVERE, getName()+": Exception while processing messages. Closing connection: "+e.getClass().getSimpleName()+" - "+e.getMessage(), e);
				goOn = false;
				break;
			}
		}
		
		// End of thread: close all remainig remote files and terminate connection gracefully
		close();
		log.info(getName()+": Closed.");
	}
	
}
