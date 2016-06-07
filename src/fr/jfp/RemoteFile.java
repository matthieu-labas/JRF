package fr.jfp;

import java.io.File;
import java.io.IOException;

import fr.jfp.client.JFPClient;
import fr.jfp.msg.Message;
import fr.jfp.msg.file.MsgFileDelete;
import fr.jfp.msg.file.MsgFileInfos;
import fr.jfp.msg.file.MsgFileList;
import fr.jfp.msg.file.MsgFileRoots;
import fr.jfp.msg.file.MsgFileSpace;
import fr.jfp.msg.file.MsgFileSpace.SpaceType;
import fr.jfp.msg.file.MsgReplyFileInfos;
import fr.jfp.msg.file.MsgReplyFileList;
import fr.jfp.msg.file.MsgReplyFileLong;
import fr.jfp.server.JFPProvider;

/**
 * <p>A remote equivalent of {@link java.io.File}.</p>
 * <p>Meta information (file size, modified date and attributes) are requested when the {@code RemoteFile}
 * is created so they are cached locally. They can be refreshed by a call to {@link #refresh()}.</p>
 * 
 * @author Matthieu Labas
 */
public class RemoteFile extends File {
	
	private static final long serialVersionUID = 1L;
	
	protected String pathname;
	protected JFPClient cli;
	
	private boolean init;
	protected long lastModified;
	protected long length;
	protected byte attributes;
	
	/** Exception thrown during last command, or {@code null} if no exception was thrown. */
	protected IOException ex;
	
	/**
	 * Create a new {@link File} using {@code server} to query a remote {@link JFPProvider}.
	 * @param server The connection to the remote {@code JFPProvider}.
	 * @param pathname The absolute path of the <em>remote</em> file.
	 * @param reqInfos {@code true} if file meta information should be requested. In that case,
	 * 		the call will block until the {@code server} has sent the information.
	 * @throws IOException In case of communication error with {@code server} when requesting meta
	 * 		information.
	 */
	public RemoteFile(JFPClient server, String pathname, boolean reqInfos) throws IOException {
		super(pathname);
		this.pathname = super.getAbsolutePath();
		this.cli = server;
		if (reqInfos)
			refresh();
		
		// TODO: Override
//		super.list(filter);
		
//		super.mkdirs();
//		super.renameTo(dest);
//		super.createNewFile();
	}
	
	public RemoteFile(JFPClient server, String pathname) throws IOException {
		this(server, pathname, true);
	}
	
	public RemoteFile(RemoteFile parent, String child, boolean reqInfos) throws IOException {
		super(parent == null ? null : parent.getAbsolutePath(), child);
		this.pathname = super.getAbsolutePath();
		this.cli = parent.cli;
		if (reqInfos)
			refresh();
	}
	
	public RemoteFile(RemoteFile parent, String child) throws IOException {
		this(parent, child, true);
	}
	
	/**
	 * Refresh file meta information.
	 * @throws IOException When an error occurs during communication with server.
	 */
	public void refresh() throws IOException {
		MsgReplyFileInfos infos = getFileInfos();
		length = infos.length();
		lastModified = infos.lastModified();
		attributes = infos.getAttributes();
		init = true;
	}
	
	/**
	 * List the roots of the remote connection.
	 * @param server The connection to the remote {@code JFPProvider}.
	 * @return The remote {@link File#listRoots() filesystem roots}, or {@code null} if a network
	 * 		error occured during communication with the server.
	 */
	public static RemoteFile[] listRoots(JFPClient server) {
		short num;
		try {
			long t0 = System.nanoTime();
			num = server.send(new MsgFileRoots());
			Message msg = server.getReply(num, 0);
			server.addLatencyNow(t0);
			if (!(msg instanceof MsgReplyFileList))
				throw new IOException();
			FileInfos[] rfiles = ((MsgReplyFileList)msg).getFiles();
			RemoteFile[] files = new RemoteFile[rfiles.length];
			for (int i = 0; i < rfiles.length; i++) {
				files[i] = new RemoteFile(server, rfiles[i].getName(), false);
				files[i].attributes = rfiles[i].getAttributes();
				files[i].lastModified = rfiles[i].lastModified();
				files[i].length = rfiles[i].length();
				files[i].init = true;
			}
			return files;
		} catch (IOException e) {
			return null;
		}
	}
	
//	private boolean is(MsgFile msgIs) {
//		short num;
//		try {
//			long t0 = System.nanoTime();
//			num = cli.send(msgIs);
//			Message msg = cli.getReply(num, 0);
//			cli.addLatencyNow(t0);
//			if (!(msg instanceof MsgReplyFileLong))
//				throw new IOException("Unexpected reply message "+msg);
//			ex = null;
//			return (((MsgReplyFileLong)msg).getValue() != 0);
//		} catch (IOException e) {
//			ex = e;
//			return false;
//		}
//	}
	
	@Override
	public boolean exists() {
		if (!init) {
			try {
				refresh();
				ex = null;
			} catch (IOException e) {
				ex = e;
			}
		}
		return (attributes != 0);
	}
	
	@Override
	public boolean isFile() {
		if (!init) {
			try {
				refresh();
				ex = null;
			} catch (IOException e) {
				ex = e;
			}
		}
		return (attributes & FileInfos.BIT_ISFILE) != 0;
	}
	
	@Override
	public boolean isDirectory() {
		if (!init) {
			try {
				refresh();
				ex = null;
			} catch (IOException e) {
				ex = e;
			}
		}
		return (attributes & FileInfos.BIT_ISDIRECTORY) != 0;
	}
	
	@Override
	public boolean isHidden() {
		if (!init) {
			try {
				refresh();
				ex = null;
			} catch (IOException e) {
				ex = e;
			}
		}
		return (attributes & FileInfos.BIT_ISHIDDEN) != 0;
	}
	
	@Override
	public boolean canRead() {
		if (!init) {
			try {
				refresh();
				ex = null;
			} catch (IOException e) {
				ex = e;
			}
		}
		return (attributes & FileInfos.BIT_CANREAD) != 0;
	}
	
	@Override
	public boolean canWrite() {
		if (!init) {
			try {
				refresh();
				ex = null;
			} catch (IOException e) {
				ex = e;
			}
		}
		return (attributes & FileInfos.BIT_CANWRITE) != 0;
	}
	
	@Override
	public boolean canExecute() {
		if (!init) {
			try {
				refresh();
				ex = null;
			} catch (IOException e) {
				ex = e;
			}
		}
		return (attributes & FileInfos.BIT_CANEXECUTE) != 0;
	}
	
	private long getLong(Message msgLong) {
		short num;
		try {
			long t0 = System.nanoTime();
			num = cli.send(msgLong);
			Message msg = cli.getReply(num, 0);
			cli.addLatencyNow(t0);
			if (!(msg instanceof MsgReplyFileLong))
				throw new IOException("Unexpected reply message "+msg);
			ex = null;
			return ((MsgReplyFileLong)msg).getValue();
		} catch (IOException e) {
			ex = e;
			return 0l;
		}
	}
	
	@Override
	public long length() {
		if (!init) {
			try {
				refresh();
				ex = null;
			} catch (IOException e) {
				ex = e;
			}
		}
		return length;
	}
	
	@Override
	public boolean delete() {
		return (getLong(new MsgFileDelete(pathname)) != 0l);
	}
	
	@Override
	public long getFreeSpace() {
		return getLong(new MsgFileSpace(pathname, SpaceType.FREE_SPACE));
	}
	
	@Override
	public long getTotalSpace() {
		return getLong(new MsgFileSpace(pathname, SpaceType.TOTAL_SPACE));
	}
	
	@Override
	public long getUsableSpace() {
		return getLong(new MsgFileSpace(pathname, SpaceType.USABLE_SPACE));
	}
	
	@Override
	public long lastModified() {
		if (!init) {
			try {
				refresh();
				ex = null;
			} catch (IOException e) {
				ex = e;
			}
		}
		return lastModified;
	}
	
	@Override
	public File getParentFile() {
		String parent = getParent();
		if (parent == null)
			return null;
		try {
			RemoteFile rf = new RemoteFile(cli, parent);
			ex = null;
			return rf;
		} catch (IOException e) {
			ex = e;
			return null;
		}
	}
	
	@Override
	public String getName() {
		String name = super.getName();
		if (!name.isEmpty())
			return name;
		return getPath();
	}
	
	@Override
	public String[] list() {
		RemoteFile[] rfs = listFiles();
		if (rfs == null)
			return null;
		String[] fs = new String[rfs.length];
		for (int i = 0; i < rfs.length; i++)
			fs[i] = rfs[i].getName();
		return fs;
	}
	
	@Override
	public RemoteFile[] listFiles() {
		short num;
		try {
			// No latency calculation for file list because the received message can be big
			num = cli.send(new MsgFileList(pathname));
			Message msg = cli.getReply(num, 0);
			if (!(msg instanceof MsgReplyFileList))
				throw new IOException();
			FileInfos[] infos = ((MsgReplyFileList)msg).getFiles();
			if (infos == null)
				return null;
			RemoteFile[] rfs = new RemoteFile[infos.length];
			for (int i = 0; i < infos.length; i++) {
				rfs[i] = new RemoteFile(cli, infos[i].getName(), false);
				rfs[i].attributes = infos[i].getAttributes();
				rfs[i].lastModified = infos[i].lastModified();
				rfs[i].length = infos[i].length();
				rfs[i].init = true;
			}
			ex = null;
			return rfs;
		} catch (IOException e) {
			ex = e;
			return null;
		}
	}
	
	/**
	 * @return All remote file information (length, last modified date, attributes).
	 * @throws IOException When the communication cannot be performed with the JFP provider.
	 */
	public MsgReplyFileInfos getFileInfos() throws IOException {
		long t0 = System.nanoTime();
		short num = cli.send(new MsgFileInfos(pathname));
		Message msg = cli.getReply(num, 0);
		cli.addLatencyNow(t0);
		if (!(msg instanceof MsgReplyFileInfos))
			throw new IOException();
		return (MsgReplyFileInfos)msg;
	}
	
}
