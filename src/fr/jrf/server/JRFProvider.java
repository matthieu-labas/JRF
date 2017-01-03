package fr.jrf.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.jrf.RemoteInputStream;
import fr.jrf.client.JRFClient;
import fr.jrf.msg.Message;
import fr.jrf.msg.MsgAck;
import fr.jrf.msg.MsgClose;
import fr.jrf.msg.MsgData;
import fr.jrf.msg.MsgGet;
import fr.jrf.msg.MsgOpen;
import fr.jrf.msg.MsgRead;
import fr.jrf.msg.MsgSkip;
import fr.jrf.msg.MsgWrite;
import fr.jrf.msg.file.MsgFile;
import fr.jrf.msg.file.MsgFileDelete;
import fr.jrf.msg.file.MsgFileInfos;
import fr.jrf.msg.file.MsgFileList;
import fr.jrf.msg.file.MsgFileMkdirs;
import fr.jrf.msg.file.MsgFileRoots;
import fr.jrf.msg.file.MsgFileSpace;
import fr.jrf.msg.file.MsgReplyFileInfos;
import fr.jrf.msg.file.MsgReplyFileList;
import fr.jrf.msg.file.MsgReplyFileLong;

/**
 * <p>The JFP Provider is the {@link JRFClient} Server counterpart, receiving file commands.</p>
 * <p>It keeps a list of locally opened files, corresponding to {@link RemoteInputStream}s on the
 * client side.</p>
 * 
 * @author Matthieu Labas
 */
public class JRFProvider extends Thread {
	
	private static final Logger log = Logger.getLogger(JRFProvider.class.getName());
	
	private static final AtomicInteger fileCounter = new AtomicInteger();
	
	private Socket sok;
	
	/** The Server to report close event to. */
	private JRFServer srv;
	
	/** Map of locally open files. Key is the file ID. */
	private Map<Short,NamedFileInputStream> localIS;
	
	/** Map of locally open files. Key is the file ID. */
	private Map<Short,NamedFileOutputStream> localOS;
	
	/** Executor used to send files requested through {@link MsgGet} commands. */
	private ExecutorService execGet;
	
	private volatile boolean goOn;
	
	/**
	 * Create a {@ode JFPProvider} serving files to a remote {@link JRFClient} connected through the
	 * given socket.
	 * @param sok The connection to the remote {@code JFPClient}.
	 */
	JRFProvider(Socket sok, JRFServer srv) {
		this.srv = srv;
		setName(JRFProvider.class.getSimpleName()+"/"+sa2Str((InetSocketAddress)sok.getLocalSocketAddress())+">"+sa2Str((InetSocketAddress)sok.getRemoteSocketAddress()));
		try {
			sok.setSoTimeout(JRFClient.TIMEOUT);
		} catch (SocketException e) {
			log.warning(getName()+": Unable to set timeout on "+sok+": "+e.getMessage());
		}
		try {
			sok.setKeepAlive(true);
		} catch (SocketException e) {
			log.warning(getName()+": Unable to set keepalive on "+sok+": "+e.getMessage());
		}
		this.sok = sok;
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
	 * @return The list of currently opened input files.
	 */
	public List<String> getOpenedInputFiles() {
		// Could be synchronized...
		List<String> opnd = new ArrayList<>(localIS.size());
		for (NamedFileInputStream is : localIS.values())
			opnd.add(is.getOptions().getFile());
		return opnd;
	}
	
	/**
	 * @return The list of currently opened output files.
	 */
	public List<String> getOpenedOutputFiles() {
		// Could be synchronized...
		List<String> opnd = new ArrayList<>(localOS.size());
		for (NamedFileOutputStream is : localOS.values())
			opnd.add(is.getOptions().getFile());
		return opnd;
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
		for (InputStream is : localIS.values()) {
			try {
				is.close();
			} catch (IOException e) {
				log.warning(getName()+": Exception while closing local file "+is+": "+e.getMessage());
			}
		}
		for (OutputStream os : localOS.values()) {
			try {
				os.close();
			} catch (IOException e) {
				log.warning(getName()+": Exception while closing local file "+os+": "+e.getMessage());
			}
		}
		
		// Close the connection
		JRFClient.gracefulClose(sok, true);
		if (execGet != null) {
			execGet.shutdown();
			for (;;) {
				try {
					if (execGet.awaitTermination(1, TimeUnit.SECONDS))
						break;
				} catch (InterruptedException e) { }
			}
		}
		srv.providerClosed(this);
	}
	
	public void requestStop() {
		goOn = false;
		close();
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
					NamedFileOutputStream os = new NamedFileOutputStream(m);
					ack = new MsgAck(num, (short)(fileCounter.incrementAndGet() & 0xffff));
					localOS.put(ack.getFileID(), os);
					break; }
				
				default:
					log.warning("Unhandled mode '"+mode+"', assuming 'r'");
				case 'r': {
					NamedFileInputStream is = new NamedFileInputStream(m);
					ack = new MsgAck(num, (short)(fileCounter.incrementAndGet() & 0xffff));
					localIS.put(ack.getFileID(), is);
					break; }
			}
			log.fine(getName()+": "+file+"["+mode+"] > ID "+ack.getFileID());
		} catch (IOException | SecurityException e) {
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
		NamedFileInputStream is = localIS.get(fileID);
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
				int defl = is.getOptions().getDeflate();
				if (defl > 0) {
					buf = MsgData.deflate(buf, n, defl);
					n = buf.length;
				}
				data = new MsgData(num, fileID, buf, n, 0, false);
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
		NamedFileOutputStream os = localOS.get(fileID);
		if (os == null) { // File descriptor not found
			log.warning(getName()+": Local file ID "+fileID+" not found");
			ack = new MsgAck(num, fileID, MsgAck.WARN, "File not found");
		} else {
			byte[] buf = m.getBuffer();
			if (os.getOptions().getDeflate() > 0)
				buf = MsgData.inflate(buf, len);
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
	
	// "Skip from file" command
	private void handleSkip(MsgSkip m) throws IOException {
		short num = m.getNum();
		short fileID = m.getFileID();
		long skip = m.getSkip();
		log.info(getName()+": Request skip "+skip+" bytes on file "+fileID);
		MsgAck ack;
		InputStream is = localIS.get(fileID);
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
	
	// "File close" command
	private void handleClose(MsgClose m) throws IOException {
		int fileID = m.getFileID();
		log.info(getName()+": Request close file "+fileID);
		InputStream is = localIS.get(m.getFileID());
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
	
	/**
	 * Reads every possible byte into the given array, returning the number of bytes actually read,
	 * which can be {@code < buf.length} if EOF has been reached on {@code is}.
	 * @param is The {@code InputStream} to read.
	 * @param buf The buffer to fill with data read from {@code is}.
	 * @return The number of bytes read (up to {@code buf.length}.
	 * @throws IOException
	 */
	private static int read(InputStream is, byte[] buf) throws IOException {
		int n;
		int len = buf.length, tot = 0;
		while (len > 0) {
			n = is.read(buf, tot, len);
			if (n < 0)
				break;
			tot += n;
			len -= n;
		}
		return tot;
	}
	
	// "File get" command
	private void handleFileGet(final MsgGet m) throws IOException {
		log.info(getName()+": Request get file "+m.getFilename());
		if (execGet == null)
			execGet = Executors.newSingleThreadExecutor();
		execGet.execute(new Runnable() {
			@Override
			public void run() {
				byte[] bufIn = new byte[m.getMTU()];
				byte[] bufOut = bufIn;
				String name = m.getFilename();
				Thread.currentThread().setName("GET "+name);
				short replyTo = m.getNum();
				int deflate = m.getDeflate();
				long len = new File(name).length();
				try (InputStream is = new BufferedInputStream(new FileInputStream(name), 2*bufIn.length)) {
					int n;
					while (len > 0) {
						n = read(is, bufIn);
						len -= n;
						if (deflate > 0) {
							bufOut = MsgData.deflate(bufIn, n, deflate);
							n = bufOut.length; // TODO: Read until MTU bytes available after compression (see DeflateInputStream and PipedInputStream)
						}
						new MsgData(replyTo, (short)-1, bufOut, n, deflate, len > 0).send(sok);
					}
				} catch (IOException ex) {
					try {
						new MsgAck(m.getReplyTo(), (short)-1, MsgAck.ERR, ex.getMessage()).send(sok);
					} catch (IOException e) {
						log.severe("I/O error when sending I/O error report on file GET "+name);
					}
				}
			}
		});
	}
	
	private void handleFileOp(MsgFile msg) throws IOException {
		log.info(getName()+": Request FileOp "+msg);
		
		if (msg instanceof MsgFileList) {
			new MsgReplyFileList(msg.getNum(), msg.getFile().listFiles(), true).send(sok); // TODO: Split if too many files
			
		} else if (msg instanceof MsgFileDelete) {
			new MsgReplyFileLong(msg.getNum(), msg.getFile().delete() ? 1l : 0l).send(sok);
			
		} else if (msg instanceof MsgFileMkdirs) {
			new MsgReplyFileLong(msg.getNum(), msg.getFile().mkdirs() ? 1l : 0l).send(sok);
			
		} else if (msg instanceof MsgFileInfos) {
			new MsgReplyFileInfos(msg.getNum(), msg.getFile()).send(sok);
			
		} else if (msg instanceof MsgFileSpace) {
			MsgFileSpace m = (MsgFileSpace)msg;
			long val = 0l;
			switch (m.spaceInfo()) {
				case LENGTH: val = m.getFile().length(); break;
				case FREE_SPACE: val = m.getFile().getFreeSpace(); break;
				case LAST_MODIFIED: val = m.getFile().lastModified(); break;
				case TOTAL_SPACE: val = m.getFile().getTotalSpace(); break;
				case USABLE_SPACE: val = m.getFile().getUsableSpace(); break;
			}
			new MsgReplyFileLong(m.getNum(), val).send(sok);
		
		} else if (msg instanceof MsgFileRoots) {
			new MsgReplyFileList(msg.getNum(), File.listRoots(), true).send(sok);
		
		} else {
			log.warning(getName()+": Unhandled FileOp message "+msg);
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
					
				} else if (msg instanceof MsgWrite) { // Write to file: reply with MsgAck
					handleWrite((MsgWrite)msg);
					
				} else if (msg instanceof MsgSkip) { // Skip in file: reply with MsgAck
					handleSkip((MsgSkip)msg);
					
				} else if (msg instanceof MsgClose) { // Close file: no reply
					handleClose((MsgClose)msg);
					
				} else if (msg instanceof MsgFile) { // Operation on java.io.File
					handleFileOp((MsgFile)msg);
					
				} else if (msg instanceof MsgGet) { // Request file content
					handleFileGet((MsgGet)msg);
					
				} else { // Unknown
					log.warning(getName()+": Don't know how to handle file message "+msg);
				}
			} catch (SocketTimeoutException e) {
				log.finest(getName()+": timeout...");
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
		private MsgOpen opts;
		public NamedFileInputStream(MsgOpen opts) throws FileNotFoundException, SecurityException {
			super(new FileInputStream(opts.getFile()));
			this.opts = opts;
		}
		public MsgOpen getOptions() {
			return opts;
		}
		@Override public String toString() {
			return "in:"+opts.getFile();
		}
	}
	
	private static class NamedFileOutputStream extends BufferedOutputStream {
		private MsgOpen opts;
		public NamedFileOutputStream(MsgOpen opts) throws FileNotFoundException, SecurityException {
			super(new FileOutputStream(opts.getFile()));
			this.opts = opts;
		}
		public MsgOpen getOptions() {
			return opts;
		}
		@Override public String toString() {
			return "out:"+opts.getFile();
		}
	}
	
}
