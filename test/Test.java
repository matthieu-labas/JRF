import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;

import fr.jfp.client.JFPClient;
import fr.jfp.server.JFPServer;

public class Test {

	public static void main(String[] args) throws IOException {
		JFPServer srv = JFPServer.get(2205);
		srv.start();
		JFPClient cli = new JFPClient(new InetSocketAddress("127.0.0.1", 2205));
		System.out.println("Connected.");
		srv.requestStop();
		cli.start();
		InputStream is = cli.getRemoteInputStream("D:/Temp/test.txt");
		byte[] buf = new byte[1024];
		int n = is.read(buf);
		System.out.println(new String(buf, 0, n));
		is.close();
		cli.requestStop();
		while (cli.isAlive())
			try{Thread.sleep(200);}catch(InterruptedException e){}
		System.out.println("The end.");
	}

}
