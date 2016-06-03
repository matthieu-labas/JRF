package fr.jfp;

import java.io.File;
import java.io.IOException;

import fr.jfp.client.JFPClient;
import fr.jfp.msg.Message;
import fr.jfp.msg.file.MsgFile;
import fr.jfp.msg.file.MsgFileIs;
import fr.jfp.msg.file.MsgFileIs.IsType;
import fr.jfp.msg.file.MsgFileList;
import fr.jfp.msg.file.MsgFileRoots;
import fr.jfp.msg.file.MsgFileSpace;
import fr.jfp.msg.file.MsgFileSpace.SpaceType;
import fr.jfp.msg.file.MsgReplyFileList;
import fr.jfp.msg.file.MsgReplyFileLong;
import fr.jfp.server.JFPProvider;

public class RemoteFile extends File {
	
	private static final long serialVersionUID = 1L;
	
	private String pathname;
	private JFPClient cli;
	
	/**
	 * Create a new {@link File} using {@code server} to query a remote {@link JFPProvider}.
	 * @param server The connection to the remote {@code JFPProvider}.
	 * @param pathname The absolute path of the <em>remote</em> file.
	 */
	public RemoteFile(JFPClient server, String pathname) {
		super(pathname);
		this.pathname = super.getAbsolutePath();
		this.cli = server;
		
		// TODO: Override
//		super.list(filter);
		
//		super.mkdirs();
//		super.renameTo(dest);
//		super.delete();
//		super.createNewFile();
	}
	
	/**
	 * List the roots of the remote connection.
	 * @param server The connection to the remote {@code JFPProvider}.
	 * @return The remote {@link File#listRoots() filesystem roots}. Each element is a {@code RemoteFile}.
	 */
	public static File[] listRoots(JFPClient server) {
		int num;
		try {
			long t0 = System.nanoTime();
			num = server.send(new MsgFileRoots());
			Message msg = server.getReply(num, 0);
			server.addLatencyNow(t0);
			if (!(msg instanceof MsgReplyFileList))
				throw new IOException();
			String[] rfiles = ((MsgReplyFileList)msg).getFiles();
			File[] files = new File[rfiles.length];
			for (int i = 0; i < rfiles.length; i++)
				files[i] = new RemoteFile(server, rfiles[i]);
			return files;
		} catch (IOException e) {
			// TODO: Report back to 'cli' so it can close?
			e.printStackTrace();
			return null;
		}
	}
	
	private boolean is(MsgFile msgIs) {
		int num;
		try {
			long t0 = System.nanoTime();
			num = cli.send(msgIs);
			Message msg = cli.getReply(num, 0);
			cli.addLatencyNow(t0);
			if (!(msg instanceof MsgReplyFileLong))
				throw new IOException();
			return (((MsgReplyFileLong)msg).getValue() != 0);
		} catch (IOException e) {
			// TODO: Report back to 'cli' so it can close?
			e.printStackTrace();
			return false;
		}
	}
	
	@Override
	public boolean exists() {
		return is(new MsgFileIs(pathname, IsType.IS_EXIST));
	}
	
	@Override
	public boolean isFile() {
		return is(new MsgFileIs(pathname, IsType.IS_FILE));
	}
	
	@Override
	public boolean isDirectory() {
		return is(new MsgFileIs(pathname, IsType.IS_DIRECTORY));
	}
	
	private long getLong(Message msgLong) {
		int num;
		try {
			long t0 = System.nanoTime();
			num = cli.send(msgLong);
			Message msg = cli.getReply(num, 0);
			cli.addLatencyNow(t0);
			if (!(msg instanceof MsgReplyFileLong))
				throw new IOException();
			return ((MsgReplyFileLong)msg).getValue();
		} catch (IOException e) {
			// TODO: Report back to 'cli' so it can close?
			e.printStackTrace();
			return 0l;
		}
	}
	
	@Override
	public long length() {
		return getLong(new MsgFileSpace(pathname, SpaceType.LENGTH));
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
		return getLong(new MsgFileSpace(pathname, SpaceType.LAST_MODIFIED));
	}
	
	@Override
	public String[] list() {
		int num;
		try {
			// No latency calculation for file list because the received message can be big
			num = cli.send(new MsgFileList(pathname));
			Message msg = cli.getReply(num, 0);
			if (!(msg instanceof MsgReplyFileList))
				throw new IOException();
			return ((MsgReplyFileList)msg).getFiles();
		} catch (IOException e) {
			// TODO: Report back to 'cli' so it can close?
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	public RemoteFile[] listFiles() {
		String[] files = list();
		if (files == null)
			return null;
		RemoteFile[] rfs = new RemoteFile[files.length];
		for (int i = 0; i < files.length; i++)
			rfs[i] = new RemoteFile(cli, files[i]);
		return rfs;
	}
	
}
