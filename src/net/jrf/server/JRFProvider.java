package net.jrf.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterInputStream;

import net.jrf.RemoteInputStream;
import net.jrf.RemoteOutputStream;
import net.jrf.Utils;
import net.jrf.client.JRFClient;
import net.jrf.msg.Message;
import net.jrf.msg.MsgAck;
import net.jrf.msg.MsgClose;
import net.jrf.msg.MsgData;
import net.jrf.msg.MsgFlush;
import net.jrf.msg.MsgGet;
import net.jrf.msg.MsgISAction;
import net.jrf.msg.MsgISAction.StreamAction;
import net.jrf.msg.MsgOpen;
import net.jrf.msg.MsgPing;
import net.jrf.msg.MsgRead;
import net.jrf.msg.MsgWrite;
import net.jrf.msg.file.MsgFALong;
import net.jrf.msg.file.MsgFAString;
import net.jrf.msg.file.MsgFileAction;
import net.jrf.msg.file.MsgFileAction.FileAction;
import net.jrf.msg.file.MsgFileInfos;
import net.jrf.msg.file.MsgFileList;
import net.jrf.msg.file.MsgFileLong;

/**
 * <p>The JRF Provider is the {@link JRFClient} Server counterpart, receiving and processing file
 * commands.</p>
 * <p>It keeps a list of locally opened files, corresponding to {@link RemoteInputStream}s and
 * {@link RemoteOutputStream}s on the client side.</p>
 * 
 * @author Matthieu Labas
 */
public class JRFProvider extends Thread {
	
	private static final Logger log = Logger.getLogger(JRFProvider.class.getName());
	
	/** Inactivity time after which a ping message is sent to the client to check for
	 * network connectivity. */
	public static final int PING_TIMEOUT = 5000;
	
	/** Counter for file IDs. */
	private static final AtomicInteger fileCounter = new AtomicInteger();
	
	private Socket sok;
	
	/** The Server to report close event to. */
	private JRFServer srv;
	
	/** Map of locally opened files for read. Key is the file ID. */
	private Map<Short,NamedFileInputStream> localIS;
	
	/** Map of locally opened files for write. Key is the file ID. */
	private Map<Short,NamedFileOutputStream> localOS;
	
	/** When was the last network activity. */
	private long lastActivity;
	
	/** Executor used to exchange files requested through {@link MsgGet} commands.
	 * Such exchanges are processed in the background so other commands can be processed. */
	private ExecutorService execFile;
	
	/** Last timestamp a ping was sent. */
	private long pingStamp;
	private boolean pingSent;
	
	private volatile boolean goOn;
	
	/**
	 * Create a {@code JRFProvider} serving files to a remote {@link JRFClient} connected through the
	 * given socket.
	 * @param sok The connection to the remote {@code JRFClient}.
	 * @param srv The Server creating this object.
	 */
	JRFProvider(Socket sok, JRFServer srv) {
		this.srv = srv;
		setName(JRFProvider.class.getSimpleName()+"/"+sa2Str((InetSocketAddress)sok.getLocalSocketAddress())+">"+sa2Str((InetSocketAddress)sok.getRemoteSocketAddress()));
		try {
			sok.setSoTimeout(JRFClient.TIMEOUT);
		} catch (SocketException e) {
			log.warning(getName()+": Unable to set timeout on "+sok+": "+e.getMessage());
		}
		// Keepalive is basically useless, we use our own ping messages
		this.sok = sok;
		lastActivity = System.currentTimeMillis();
		localIS = new HashMap<>();
		localOS = new HashMap<>();
		goOn = true;
	}
	
	/**
	 * @return The address of the connected {@link JRFClient}.
	 */
	public InetSocketAddress getRemote() {
		return (InetSocketAddress)sok.getRemoteSocketAddress();
	}
	
	/**
	 * @return The list of currently opened input files names.
	 */
	public List<String> getOpenedInputFiles() {
		synchronized (localIS) {
			List<String> opnd = new ArrayList<>(localIS.size());
			for (NamedFileInputStream is : localIS.values())
				opnd.add(is.name);
			return opnd;
		}
	}
	
	/**
	 * @return The list of currently opened output files names.
	 */
	public List<String> getOpenedOutputFiles() {
		synchronized (localOS) {
			List<String> opnd = new ArrayList<>(localOS.size());
			for (NamedFileOutputStream os : localOS.values())
				opnd.add(os.name);
			return opnd;
		}
	}
	
	/**
	 * @param addr
	 * @return {@code addr} in dotted numeric format (no DNS lookup).
	 */
	private static String sa2Str(InetSocketAddress addr) {
		return addr.getAddress().getHostAddress()+":"+addr.getPort();
	}
	
	/**
	 * Close all opened files and terminates the connection gracefully.
	 */
	private synchronized void close() {
		// Close all locally opened files
		synchronized (localIS) {
			for (InputStream is : localIS.values()) {
				try {
					is.close();
				} catch (IOException e) {
					log.warning(getName()+": Exception while closing local file "+is+": "+e.getMessage());
				}
			}
		}
		synchronized (localOS) {
			for (OutputStream os : localOS.values()) {
				try {
					os.close();
				} catch (IOException e) {
					log.warning(getName()+": Exception while closing local file "+os+": "+e.getMessage());
				}
			}
		}
		
		// Close the connection
		if (execFile != null) {
			execFile.shutdown();
			for (;;) {
				try {
					if (execFile.awaitTermination(1, TimeUnit.SECONDS))
						break;
				} catch (InterruptedException e) { }
			}
		}
		JRFClient.gracefulClose(sok, true); // Close socket after executor has finished
		srv.providerClosed(this);
	}
	
	/**
	 * Request graceful connection termination.
	 */
	public void requestStop() {
		goOn = false;
		close();
	}
	
	/**
	 * @return The last timestamp when network activity occurred.
	 */
	public long lastActivityTime() {
		return lastActivity;
	}
	
	// "Open file" command
	private void handleOpen(MsgOpen m) throws IOException {
		short num = m.getNum();
		String file = m.getFile();
		log.info(getName()+": Request open file "+file);
		MsgAck ack;
		try {
			char mode = m.getMode();
			switch (mode) {
				case 'w': {
					NamedFileOutputStream os = new NamedFileOutputStream(m.getFile());
					ack = new MsgAck(num, (short)(fileCounter.incrementAndGet() & 0xffff));
					synchronized (localOS) {
						localOS.put(ack.getFileID(), os);
					}
					break; }
				
				default:
					log.warning("Unhandled mode '"+mode+"', assuming 'r'");
				case 'r': {
					NamedFileInputStream is = new NamedFileInputStream(m.getFile(), m.getDeflate());
					ack = new MsgAck(num, (short)(fileCounter.incrementAndGet() & 0xffff));
					synchronized (localIS) {
						localIS.put(ack.getFileID(), is);
					}
					break; }
			}
			log.fine(getName()+": "+file+"["+mode+"] > ID "+ack.getFileID());
		} catch (IOException e) {
			log.warning(getName()+": "+file+": "+e.getClass().getSimpleName()+" - "+e.getMessage());
			ack = new MsgAck(num, (short)-1, MsgAck.WARN, e.getMessage());
		}
		try {
			ack.send(sok);
		} catch (IOException e) {
			log.warning(getName()+": Unable to send open-Ack event back to requestor: "+e.getMessage());
			throw e;
		}
	}
	
	// "File read" command
	private void handleRead(MsgRead m) throws IOException {
		short num = m.getNum();
		short fileID = m.getFileID();
		int len = m.getLength();
		log.info(getName()+": Request read "+len+" bytes from file "+fileID);
		MsgAck ack = null;
		MsgData data = null;
		NamedFileInputStream is;
		synchronized (localIS) {
			is = localIS.get(fileID);
		}
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
				int defl = is.deflate;
				if (defl > 0) {
					byte[] bufd = Utils.deflate(buf, 0, n, defl);
					if (bufd.length < n) { // Only apply deflate if it's worth it
						buf = bufd;
						n = buf.length;
					} else
						defl = 0;
				}
				data = new MsgData(num, fileID, buf, n, defl, false);
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
	
	// "File write" command
	private void handleWrite(MsgWrite m) throws IOException {
		short num = m.getNum();
		short fileID = m.getFileID();
		int len = m.getLength();
		log.info(getName()+": Request write "+len+" bytes to file "+fileID);
		MsgAck ack = null;
		NamedFileOutputStream os;
		synchronized (localOS) {
			os = localOS.get(fileID);
		}
		if (os == null) { // File descriptor not found
			log.warning(getName()+": Local file ID "+fileID+" not found");
			ack = new MsgAck(num, fileID, MsgAck.WARN, "File not found");
		} else {
			byte[] buf = m.getBuffer();
			if (m.getDeflate() > 0) {
				buf = Utils.inflate(buf, 0, len);
				len = buf.length;
			}
			try {
				os.write(buf, 0, len);
				log.fine(getName()+": wrote "+len+" to file "+fileID);
				ack = new MsgAck(num, fileID, MsgAck.OK, null);
			} catch (IOException e) { // Exception during read
				String msg = e.getMessage();
				log.warning(getName()+": Error when writing "+len+" bytes to file ID "+fileID+": "+msg);
				ack = new MsgAck(num, fileID, MsgAck.ERR, msg);
			}
		}
		try {
			ack.send(sok);
		} catch (IOException e) {
			log.warning(getName()+": Unable to send write-ack event back to requestor: "+e.getMessage());
			throw e;
		}
	}
	
	// "Action on file" command
	private void handleAction(MsgISAction m) throws IOException {
		short num = m.getNum();
		short fileID = m.getFileID();
		StreamAction action = m.getAction();
		long val = m.getValue();
		log.info(getName()+": Request "+m);
		MsgAck ack;
		InputStream is;
		synchronized (localIS) {
			is = localIS.get(fileID);
		}
		if (is == null) { // File descriptor not found
			log.warning(getName()+": Local file ID "+fileID+" not found");
			ack = new MsgAck(num, fileID, MsgAck.WARN, "File not found");
		} else {
			try {
				long ret = -1l;
				// Perform the action
				switch (action) {
					case AVAILABLE: ret = is.available(); break;
					case MARK_SUPPORTED: ret = (is.markSupported() ? 1l : 0l); break;
					case MARK: is.mark((int)val); break;
					case RESET: is.reset(); break;
					case SKIP: ret = is.skip(val); break;
				}
				ack = new MsgAck(num, fileID, ret, null);
				log.fine(getName()+": Performed "+action+"="+ret+" on file "+fileID);
			} catch (IOException e) { // Exception during action
				String msg = e.getMessage();
				log.warning(getName()+": Error when performing "+action+"/"+val+" on file ID "+fileID+": "+msg);
				ack = new MsgAck(num, fileID, MsgAck.ERR, msg);
			}
		}
		try {
			ack.send(sok);
		} catch (IOException e) {
			log.warning(getName()+": Unable to send "+action+"-Ack event back to requestor: "+e.getMessage());
			throw e;
		}
	}
	
	// "Flush on file" command
	private void handleFlush(MsgFlush m) throws IOException {
		short num = m.getNum();
		short fileID = m.getFileID();
		log.info(getName()+": Request "+m);
		MsgAck ack;
		OutputStream os;
		synchronized (localOS) {
			os = localOS.get(fileID);
		}
		if (os == null) { // File descriptor not found
			log.warning(getName()+": Local file ID "+fileID+" not found");
			ack = new MsgAck(num, fileID, MsgAck.WARN, "File not found");
		} else {
			try {
				os.flush();
				ack = new MsgAck(num, fileID, 1l, null);
				log.fine(getName()+": Performed flush on file "+fileID);
			} catch (IOException e) {
				String msg = e.getMessage();
				log.warning(getName()+": Error when performing flush on file ID "+fileID+": "+msg);
				ack = new MsgAck(num, fileID, MsgAck.ERR, msg);
			}
		}
		try {
			ack.send(sok);
		} catch (IOException e) {
			log.warning(getName()+": Unable to send flush-ack event back to requestor: "+e.getMessage());
			throw e;
		}
	}
	
	// "File close" command
	private void handleClose(MsgClose m) throws IOException {
		int fileID = m.getFileID();
		log.info(getName()+": Request close file "+fileID);
		Closeable stream;
		synchronized (localIS) {
			stream = localIS.get(m.getFileID());
		}
		if (stream == null) { // Maybe an OutputStream?
			synchronized (localOS) {
				stream = localOS.get(m.getFileID());
			}
		}
		if (stream == null) { // File descriptor not found
			log.warning(getName()+": Local file ID "+fileID+" not found");
		} else {
			try {
				stream.close();
				log.fine(getName()+": Closed file "+fileID);
			} catch (IOException e) { // Exception during skip
				log.warning(getName()+": Error when closing file ID "+fileID+": "+e.getMessage());
			}
		}
	}
	
	// "File get" command
	private void handleFileGet(final MsgGet m) throws IOException {
		log.info(getName()+": Request get file "+m.getFilename());
		if (execFile == null)
			execFile = Executors.newSingleThreadExecutor(); // TODO: Or multi-thread (but parallelizing disk I/O might not be a good thing...)
		try {
			execFile.execute(new Runnable() {
				@Override public void run() {
					byte[] buf = new byte[m.getMTU() - Message.getHeaderSize(MsgData.class)];
					String name = m.getFilename();
					Thread.currentThread().setName("GET "+name);
					short replyTo = m.getNum();
					int deflate = m.getDeflate();
					Deflater defl = null;
					InputStream is = null;
					try (InputStream topis = new BufferedInputStream(new FileInputStream(name), 2*buf.length)) {
						if (deflate > 0) {
							defl = new Deflater(deflate);
							is = new DeflaterInputStream(topis, defl);
						} else
							is = topis;
						
						int n;
						boolean next = true;
						while (next) {
							n = Utils.readFully(is, buf);
							next = (n == buf.length);
							new MsgData(replyTo, (short)-1, buf, n, deflate, next).send(sok);
						}
					} catch (IOException ex) {
						try {
							new MsgAck(m.getReplyTo(), (short)-1, MsgAck.ERR, ex.getMessage()).send(sok);
						} catch (IOException e) {
							log.severe("I/O error when sending I/O error report on file GET "+name+": "+e.getMessage());
						}
					} finally {
						if (defl != null) {
							defl.end();
							try {
								is.close(); // 'is' is the DeflaterInputStream
							} catch (IOException e) { }
						}
					}
				}
			});
		} catch (RejectedExecutionException e) {
			throw new IOException("Provider is closing... "+e.getMessage());
		}
	}
	
	// Any operation on RemoteFile
	private void handleFileOp(MsgFileAction msg) throws IOException {
		log.info(getName()+": Request FileOp "+msg);
		
		FileAction action = msg.getAction();
		short num = msg.getNum();
		File f = msg.getFile();
		switch (action) {
			case GET_ATTRIBUTES: new MsgFileInfos(num, f).send(sok); break;
			
			case LIST_FILES: new MsgFileList(num, f.listFiles(), true).send(sok); break;
			case LIST_ROOTS: new MsgFileList(num, File.listRoots(), true).send(sok); break;
			
			case CREATE_NEW: new MsgFileLong(num, f.createNewFile() ? 1l : 0l).send(sok); break;
			case DELETE: new MsgFileLong(num, f.delete() ? 1l : 0l).send(sok); break;
			case MKDIR: new MsgFileLong(num, f.mkdir() ? 1l : 0l).send(sok); break;
			case MKDIRS: new MsgFileLong(num, f.mkdirs() ? 1l : 0l).send(sok); break;
			
			case RENAME: new MsgFileLong(num, f.renameTo(new File(((MsgFAString)msg).getValue())) ? 1l : 0l).send(sok); break;
			
			case SET_EXECUTE: new MsgFileLong(num, f.setExecutable(((MsgFALong)msg).getValue() != 0l) ? 1l : 0l).send(sok); break;
			case SET_LAST_MODIFIED: new MsgFileLong(num, f.setLastModified(((MsgFALong)msg).getValue()) ? 1l : 0l).send(sok); break;
			case SET_READ: new MsgFileLong(num, f.setReadable(((MsgFALong)msg).getValue() != 0l) ? 1l : 0l).send(sok); break;
			case SET_READONLY: new MsgFileLong(num, f.setReadOnly() ? 1l : 0l).send(sok); break;
			case SET_WRITE: new MsgFileLong(num, f.setWritable(((MsgFALong)msg).getValue() != 0l) ? 1l : 0l).send(sok); break;
			
			case FREE_SPACE: new MsgFileLong(num, f.getFreeSpace()).send(sok); break;
			case TOTAL_SPACE: new MsgFileLong(num, f.getTotalSpace()).send(sok); break;
			case USABLE_SPACE: new MsgFileLong(num, f.getUsableSpace()).send(sok); break;
		}
	}
	
	/**
	 * Check if inactivity timeout has expired and send a ping message.
	 * @return {@code true} if a ping message was sent, {@code false} if there was no need
	 * 		to send one yet.
	 */
	private boolean checkPing() {
		long t0 = System.currentTimeMillis();
		long dead = t0 - lastActivity;
//		log.finest(this+": "+dead+" ms inactivity");
		if (dead < JRFServer.CLIENT_TIMEOUT)
			return false;
		
		log.fine(getName()+": No activity for "+(dead/1000)+"s. Pinging...");
		try {
			new MsgPing().send(sok);
			pingStamp = t0;
			pingSent = true;
		} catch (IOException e) {
			log.warning(this+": Unable to send ping, closing. "+e.getMessage());
			requestStop();
		}
		return true;
	}
	
	@Override
	public void run() {
		while (goOn) {
			try {
				log.fine(getName()+": waiting for message...");
				Message msg = Message.receive(sok); // Can SocketTimeoutException
				log.fine(getName()+": received message "+msg);
				lastActivity = System.currentTimeMillis();
				pingSent = false; // Something was received, don't ping
				
				// Command messages
				if (msg instanceof MsgOpen) { // Open file: reply with MsgAck to reply with file ID
					handleOpen((MsgOpen)msg);
					
				} else if (msg instanceof MsgRead) { // Read in file: reply with MsgData, or MsgAck upon exception
					handleRead((MsgRead)msg);
					
				} else if (msg instanceof MsgWrite) { // Write to file: reply with MsgAck
					handleWrite((MsgWrite)msg);
					
				} else if (msg instanceof MsgISAction) { // Action on file: reply with MsgAck
					handleAction((MsgISAction)msg);
					
				} else if (msg instanceof MsgFlush) { // Action on file: reply with MsgAck
					handleFlush((MsgFlush)msg);
					
				} else if (msg instanceof MsgClose) { // Close file: no reply
					handleClose((MsgClose)msg);
					
				} else if (msg instanceof MsgFileAction) { // Operation on java.io.File
					handleFileOp((MsgFileAction)msg);
					
				} else if (msg instanceof MsgGet) { // Request file download
					handleFileGet((MsgGet)msg);
					
				} else if (msg instanceof MsgPing) { // Ping reply received ("pong")
					// Nothing, lastActivity is now updated
					
				} else { // Unknown
					log.warning(getName()+": Don't know how to handle file message "+msg);
				}
			} catch (SocketTimeoutException e) {
				log.finest(getName()+": timeout...");
				checkPing(); // Ping client if needed
				if (pingSent && lastActivity - pingStamp > PING_TIMEOUT) {
					long dead = System.currentTimeMillis() - lastActivity;
					log.warning(getName()+": No response to ping after inactivity of "+(dead/1000)+"s. Closing...");
					goOn = false;
				}
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
	
	
	
	private static class NamedFileInputStream extends BufferedInputStream {
		public final String name;
		public final int deflate;
		public NamedFileInputStream(String name, int deflate) throws FileNotFoundException {
			super(new FileInputStream(name));
			this.name = name;
			this.deflate = deflate;
		}
		@Override public String toString() {
			return "in:"+name;
		}
	}
	
	private static class NamedFileOutputStream extends BufferedOutputStream {
		public final String name;
		public NamedFileOutputStream(String name) throws FileNotFoundException {
			super(new FileOutputStream(name));
			this.name = name;
		}
		@Override public String toString() {
			return "out:"+name;
		}
	}
	
}
