package fr.jrf;

import java.io.File;
import java.io.IOException;

import fr.jrf.client.JRFClient;
import fr.jrf.msg.Message;
import fr.jrf.msg.file.MsgFileDelete;
import fr.jrf.msg.file.MsgFileInfos;
import fr.jrf.msg.file.MsgFileList;
import fr.jrf.msg.file.MsgFileMkdirs;
import fr.jrf.msg.file.MsgFileRoots;
import fr.jrf.msg.file.MsgFileSpace;
import fr.jrf.msg.file.MsgFileSpace.SpaceInfo;
import fr.jrf.msg.file.MsgReplyFileInfos;
import fr.jrf.msg.file.MsgReplyFileList;
import fr.jrf.msg.file.MsgReplyFileLong;
import fr.jrf.server.JRFProvider;

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
	protected JRFClient cli;
	
	private boolean init;
	protected long lastModified;
	protected long length;
	protected byte attributes;
	
	/** Exception thrown during last command, or {@code null} if no exception was thrown. */
	protected IOException ex;
	
	/**
	 * Create a new {@link File} using {@code server} to query a remote {@link JRFProvider}.
	 * @param server The connection to the remote {@code JRFProvider}.
	 * @param pathname The absolute path of the <em>remote</em> file.
	 * @param reqInfos {@code true} if file meta information should be requested. In that case,
	 * 		the call will block until the {@code server} has sent the information.
	 * @throws IOException In case of communication error with {@code server} when requesting meta
	 * 		information.
	 */
	public RemoteFile(JRFClient server, String pathname, boolean reqInfos) throws IOException {
		super(pathname);
		this.pathname = super.getAbsolutePath();
		this.cli = server;
		if (reqInfos)
			refresh();
		
		// TODO: Override
//		super.list(filter);
		
//		super.renameTo(dest);
//		super.createNewFile();
	}
	
	public RemoteFile(JRFClient server, String pathname) throws IOException {
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
	 * @param server The connection to the remote {@code JRFProvider}.
	 * @return The remote {@link File#listRoots() filesystem roots}, or {@code null} if a network
	 * 		error occured during communication with the server.
	 */
	public static RemoteFile[] listRoots(JRFClient server) {
		short num;
		try {
			num = server.send(new MsgFileRoots());
			long t0 = System.nanoTime();
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
	
	private void checkRefresh() {
		if (init)
			return;
		try {
			refresh();
			ex = null;
		} catch (IOException e) {
			ex = e;
		}
	}
	
	@Override
	public boolean exists() {
		checkRefresh();
		return (attributes != 0);
	}
	
	@Override
	public boolean isFile() {
		checkRefresh();
		return (attributes & FileInfos.BIT_ISFILE) != 0;
	}
	
	@Override
	public boolean isDirectory() {
		checkRefresh();
		return (attributes & FileInfos.BIT_ISDIRECTORY) != 0;
	}
	
	@Override
	public boolean isHidden() {
		checkRefresh();
		return (attributes & FileInfos.BIT_ISHIDDEN) != 0;
	}
	
	@Override
	public boolean canRead() {
		checkRefresh();
		return (attributes & FileInfos.BIT_CANREAD) != 0;
	}
	
	@Override
	public boolean setReadable(boolean readable) {
		// TODO
		return false;
	}
	
	@Override
	public boolean canWrite() {
		checkRefresh();
		return (attributes & FileInfos.BIT_CANWRITE) != 0;
	}
	
	@Override
	public boolean setWritable(boolean writable) {
		// TODO
		return false;
	}
	
	@Override
	public boolean canExecute() {
		checkRefresh();
		return (attributes & FileInfos.BIT_CANEXECUTE) != 0;
	}
	
	@Override
	public boolean setExecutable(boolean executable) {
		// TODO
		return false;
	}
	
	@Override
	public boolean setReadOnly() {
		// TODO
		return false;
	}
	
	private long getLong(Message msgLong) {
		short num;
		try {
			num = cli.send(msgLong);
			long t0 = System.nanoTime();
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
		checkRefresh();
		return length;
	}
	
	@Override
	public boolean delete() {
		return (getLong(new MsgFileDelete(pathname)) != 0l);
	}
	
	@Override
	public boolean mkdir() {
		// TODO
		return false;
	}
	
	@Override
	public boolean mkdirs() {
		return (getLong(new MsgFileMkdirs(pathname)) != 0l);
	}
	
	@Override
	public long getFreeSpace() {
		return getLong(new MsgFileSpace(pathname, SpaceInfo.FREE_SPACE));
	}
	
	@Override
	public long getTotalSpace() {
		return getLong(new MsgFileSpace(pathname, SpaceInfo.TOTAL_SPACE));
	}
	
	@Override
	public long getUsableSpace() {
		return getLong(new MsgFileSpace(pathname, SpaceInfo.USABLE_SPACE));
	}
	
	@Override
	public long lastModified() {
		checkRefresh();
		return lastModified;
	}
	
	@Override
	public boolean setLastModified(long time) {
		// TODO
		return false;
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
	 * @throws IOException When the communication cannot be performed with the JRF provider.
	 */
	public MsgReplyFileInfos getFileInfos() throws IOException {
		short num = cli.send(new MsgFileInfos(pathname));
		long t0 = System.nanoTime();
		Message msg = cli.getReply(num, 0);
		cli.addLatencyNow(t0);
		if (!(msg instanceof MsgReplyFileInfos))
			throw new IOException();
		return (MsgReplyFileInfos)msg;
	}
	
}
