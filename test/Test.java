import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Date;

import fr.jfp.RemoteFile;
import fr.jfp.client.JFPClient;
import fr.jfp.msg.file.MsgReplyFileInfos;
import fr.jfp.server.JFPServer;

public class Test {
	
	public static void printFileInfos(File f) {
		System.out.println("File "+f.getAbsolutePath());
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
		JFPServer srv = JFPServer.get(new InetSocketAddress(JFPServer.DEFAULT_PORT));
		srv.start();
		JFPClient cli = new JFPClient(new InetSocketAddress("127.0.0.1", JFPServer.DEFAULT_PORT));
		System.out.println("Connected.");
		srv.requestStop();
		cli.start();
		
		// Read remote file
		String remf = "D:/Temp/test.txt";
		File fil = new RemoteFile(cli, remf);
		printFileInfos(fil); // Print file meta-information
		printFileInfos(((RemoteFile)fil).getFileInfos()); // Print file meta-information, received all-in-one
		int len = (int)fil.length();
		System.out.println("Remote "+remf+" is "+len+" bytes");
		byte[] buf = new byte[len+1];
		InputStream is = cli.getRemoteInputStream(remf);
		int n = is.read(buf);
		System.out.println("Read "+n+" bytes: "+new String(buf, 0, n));
		is.close();
		
		// Get list of files
		File[] roots = RemoteFile.listRoots(cli);
		System.out.println("Remote roots:");
		for (File f : roots)
			System.out.println(String.format("%c %20.20s %s %d bytes", f.isDirectory() ? 'd' : 'f', f.getPath()+File.separator+f.getName(), new Date(f.lastModified()), f.length()));
		
		cli.requestStop();
		while (cli.isAlive())
			try{Thread.sleep(200);}catch(InterruptedException e){}
		System.out.println("The end. Average latency "+cli.getLatency()+" µs");
	}
	
	static Socket cli = null;
	@SuppressWarnings("resource")
	public static void testNBSok() {
		Thread th = new Thread() {
			@Override public void run() {
				ServerSocket srv = null;
				try {
					srv = new ServerSocket(2205);
					cli = srv.accept();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					if (srv != null) {
						try {
							srv.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		};
		th.start();
		Socket cli2;
		try {
			cli2 = new Socket("localhost", 2205);
		} catch (IOException e) {
			System.out.println("Exception "+e.getClass().getSimpleName()+" - "+e.getMessage());
			return;
		}
		while (th.isAlive())
			try { th.join(); } catch (InterruptedException e) { }
		try {
			cli.setSoTimeout(1000);
		} catch (SocketException e) {
			System.out.println("Exception "+e.getClass().getSimpleName()+" - "+e.getMessage());
		}
		while (!cli.isClosed()) {
			int n = 0;
			try {
				System.out.println((char)cli.getInputStream().read());
			} catch (SocketTimeoutException e) {
				System.out.println("Timeout");
				try {
					cli2.getOutputStream().write('a'+(n++));
				} catch (IOException e1) {
					System.out.println("Exception "+e.getClass().getSimpleName()+" - "+e.getMessage());
					try {cli.close();}catch (IOException e2){}
				}
			} catch (IOException e) {
				System.out.println("Exception "+e.getClass().getSimpleName()+" - "+e.getMessage());
				try {cli.close();}catch (IOException e2){}
			}
		}
	}
	
	public static void main(String[] args) throws IOException {
		test();
//		testNBSok();
	}
	
}
