package fr.jrf;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;

import fr.jrf.client.JRFClient;
import fr.jrf.msg.Message;
import fr.jrf.msg.file.MsgFALong;
import fr.jrf.msg.file.MsgFAString;
import fr.jrf.msg.file.MsgFileAction;
import fr.jrf.msg.file.MsgFileAction.FileAction;
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
	 * @return The last exception that occurred (usually an {@link IOException} on methods returning a boolean),
	 * 		or {@code null} if none.
	 */
	public Exception getLastException() {
		return ex;
	}
	
	/**
	 * Refresh file meta information (length, date, attributes).
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
			num = server.send(new MsgFileAction(FileAction.LIST_ROOTS, null));
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
		return getLong(new MsgFileAction(FileAction.SET_READ, pathname)) != 0l;
	}
	
	@Override
	public boolean canWrite() {
		checkRefresh();
		return (attributes & FileInfos.BIT_CANWRITE) != 0;
	}
	
	@Override
	public boolean setWritable(boolean writable) {
		return getLong(new MsgFileAction(FileAction.SET_WRITE, pathname)) != 0l;
	}
	
	@Override
	public boolean canExecute() {
		checkRefresh();
		return (attributes & FileInfos.BIT_CANEXECUTE) != 0;
	}
	
	@Override
	public boolean setExecutable(boolean executable) {
		return getLong(new MsgFileAction(FileAction.SET_EXECUTE, pathname)) != 0l;
	}
	
	@Override
	public boolean setReadOnly() {
		return getLong(new MsgFileAction(FileAction.SET_READONLY, pathname)) != 0l;
	}
	
	@Override
    public String[] list(FilenameFilter filter) {
		if (filter == null)
			return list();
		RemoteFile[] files = listFiles();
		ArrayList<String> ret = new ArrayList<>(files.length);
		for (File f : files) {
			if (filter.accept(f, f.getName()))
				ret.add(f.getName());
		}
		return ret.toArray(new String[0]);
	}
	
	@Override
    public boolean renameTo(File dest) {
		String path = dest.getAbsolutePath();
		boolean ret = (getLong(new MsgFAString(FileAction.RENAME, pathname, path)) != 0l);
		if (ret)
			pathname = path;
		return ret;
	}
	
	@Override
    public boolean createNewFile() throws IOException {
		return getLong(new MsgFileAction(FileAction.CREATE_NEW, pathname)) != 0l;
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
		boolean ret = (getLong(new MsgFileAction(FileAction.DELETE, pathname)) != 0l);
		if (ret)
			attributes = 0;
		return ret;
	}
	
	@Override
	public boolean mkdir() {
		return (getLong(new MsgFileAction(FileAction.MKDIR, pathname)) != 0l);
	}
	
	@Override
	public boolean mkdirs() {
		return (getLong(new MsgFileAction(FileAction.MKDIRS, pathname)) != 0l);
	}
	
	@Override
	public long getFreeSpace() {
		return getLong(new MsgFileAction(FileAction.FREE_SPACE, pathname));
	}
	
	@Override
	public long getTotalSpace() {
		return getLong(new MsgFileAction(FileAction.TOTAL_SPACE, pathname));
	}
	
	@Override
	public long getUsableSpace() {
		return getLong(new MsgFileAction(FileAction.USABLE_SPACE, pathname));
	}
	
	@Override
	public long lastModified() {
		checkRefresh();
		return lastModified;
	}
	
	@Override
	public boolean setLastModified(long time) {
		return getLong(new MsgFALong(FileAction.SET_LAST_MODIFIED, pathname, time)) != 0l;
	}
	
	@Override
	public RemoteFile getParentFile() {
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
			num = cli.send(new MsgFileAction(FileAction.LIST_FILES, pathname));
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
		short num = cli.send(new MsgFileAction(FileAction.GET_ATTRIBUTES, pathname));
		long t0 = System.nanoTime();
		Message msg = cli.getReply(num, 0);
		cli.addLatencyNow(t0);
		if (!(msg instanceof MsgReplyFileInfos))
			throw new IOException();
		return (MsgReplyFileInfos)msg;
	}
	
}
