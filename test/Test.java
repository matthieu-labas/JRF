import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Date;

import fr.jrf.RemoteFile;
import fr.jrf.client.JRFClient;
import fr.jrf.msg.file.MsgReplyFileInfos;
import fr.jrf.server.JRFServer;

public class Test {
	
	public static void printFileInfos(File f) {
		System.out.println("File "+f.getAbsolutePath());
		System.out.println("Attributes    : "+(f.canRead()?"r":"-")+(f.canWrite()?"w":"-")+(f.canExecute()?"x":"-"));
		System.out.println("Length        : "+f.length());
		System.out.println("Last modified : "+new Date(f.lastModified()));
		System.out.println("isFile        : "+f.isFile());
		System.out.println("isDirectory   : "+f.isDirectory());
	}
	
	public static void printFileInfos(MsgReplyFileInfos f) {
		System.out.println("File "+f.getName());
		System.out.println("Attributes    : "+(f.canRead()?"r":"-")+(f.canWrite()?"w":"-")+(f.canExecute()?"x":"-"));
		System.out.println("Length        : "+f.length());
		System.out.println("Last modified : "+new Date(f.lastModified()));
		System.out.println("isFile        : "+f.isFile());
		System.out.println("isDirectory   : "+f.isDirectory());
	}
	
	public static void test() throws IOException {
		JRFServer srv = JRFServer.get(new InetSocketAddress(JRFServer.DEFAULT_PORT));
		srv.start();
		JRFClient cli = new JRFClient(new InetSocketAddress("127.0.0.1", JRFServer.DEFAULT_PORT));
		System.out.println("Connected.");
		cli.start();
		
		// Read remote file
		String remf = "/dataw/Temp/test.txt";
		File fil = new RemoteFile(cli, remf);
		printFileInfos(fil); // Print file meta-information
		printFileInfos(((RemoteFile)fil).getFileInfos()); // Print file meta-information, received all-in-one
		int len = (int)fil.length();
		System.out.println("Remote "+remf+" is "+len+" bytes");
		byte[] buf = new byte[len+1];
		
		// Read it (raw)
		try (InputStream is = cli.getRemoteInputStream(remf)) {
			int n = is.read(buf);
			System.out.println("Read (raw) "+n+" bytes: {"+new String(buf, 0, n)+"}");
		}
		
		// Read it (deflated chunks)
		try (InputStream is = cli.getRemoteInputStream(remf, 9)) {
			int n = is.read(buf);
			System.out.println("Read (deflated) "+n+" bytes: {"+new String(buf, 0, n)+"}");
		}
		
		// Get list of files
		File[] roots = RemoteFile.listRoots(cli);
		System.out.println("Remote roots:");
		for (File f : roots)
			System.out.println(String.format("%c [%20.20s] %s %d bytes", f.isDirectory() ? 'd' : 'f', f.getPath()+File.separator+f.getName(), new Date(f.lastModified()), f.length()));
		
		srv.requestStop();
		cli.requestStop();
		try{cli.join();}catch(InterruptedException e){}
		try{srv.join();}catch(InterruptedException e){}
		System.out.println("The end. Average latency "+cli.getLatency()+" µs");
	}
	
	public static void main(String[] args) throws IOException {
		test();
	}

}

