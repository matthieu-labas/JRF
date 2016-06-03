import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Date;

import fr.jfp.RemoteFile;
import fr.jfp.client.JFPClient;
import fr.jfp.server.JFPServer;

public class Test {
	
	public static void printFileInfos(File f) {
		System.out.println("File "+f.getAbsolutePath());
		System.out.println("Length        : "+f.length());
		System.out.println("Last modified : "+new Date(f.lastModified()));
		System.out.println("isFile        : "+f.isFile());
		System.out.println("isDirectory   : "+f.isDirectory());
	}
	
	public static void main(String[] args) throws IOException {
		JFPServer srv = JFPServer.get(2205);
		srv.start();
		JFPClient cli = new JFPClient(new InetSocketAddress("127.0.0.1", 2205));
		System.out.println("Connected.");
		srv.requestStop();
		cli.start();
		
		// Read remote file
		String remf = "D:/Temp/test.txt";
		File f = new RemoteFile(cli, remf);
		printFileInfos(f);
		int len = (int)f.length();
		System.out.println("Remote "+remf+" is "+len+" bytes");
		byte[] buf = new byte[len+1];
		InputStream is = cli.getRemoteInputStream(remf);
		int n = is.read(buf);
		System.out.println("Read "+n+" bytes: "+new String(buf, 0, n));
		is.close();
		
		cli.requestStop();
		while (cli.isAlive())
			try{Thread.sleep(200);}catch(InterruptedException e){}
		System.out.println("The end.");
	}

}
