package fr.jfp;

import java.io.File;

import fr.jfp.server.JFPServer;

// TODO: URLStreamHandler?

public class RemoteFile extends File {
	
	private static final long serialVersionUID = 1L;
	
	private JFPServer server;
	
	public RemoteFile(JFPServer server, String pathname) {
		super(pathname);
		this.server = server;
		
		// TODO: Override
//		super.list();
//		super.listFiles();
//		super.list(filter);
//		super.mkdirs();
//		super.isDirectory();
//		super.isFile();
//		super.listRoots();
//		super.delete();
//		super.exists();
//		super.createNewFile();
//		super.getFreeSpace();
//		super.getTotalSpace();
//		super.getUsableSpace();
//		super.lastModified();
//		super.renameTo(dest);
	}
	
}
