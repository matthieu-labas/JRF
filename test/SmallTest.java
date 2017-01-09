import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;

import net.jrf.Utils;
import net.jrf.client.JRFClientCLI;

public class SmallTest {
	
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
	
	public static void testEscape() {
		for (String s : JRFClientCLI.splitCommand("cd \"di wi sp\" \"and another\""))
			System.out.print("["+s+"] ");
		System.out.println();
		for (String s : JRFClientCLI.splitCommand("get file\\ with\\ spaces.txt"))
			System.out.print("["+s+"] ");
		System.out.println();
		for (String s : JRFClientCLI.splitCommand("get file\\\"with\\\"doublequote.txt"))
			System.out.print("["+s+"] ");
		System.out.println();
		for (String s : JRFClientCLI.splitCommand("get \"file \\\"with\\\"\\ doublequote.txt\" and\\ another"))
			System.out.print("["+s+"] ");
		System.out.println();
	}
	
	public static void testParents() {
		File f = File.listRoots()[0];
		System.out.println(String.format("path [%s] parent [%s] name [%s]", f.getPath(), f.getParent(), f.getName()));
		f = new File("D:/temp");
		System.out.println(String.format("path [%s] parent [%s] name [%s]", f.getPath(), f.getParent(), f.getName()));
	}
	
	public static void testDeflate() {
		byte[] in = new byte[1500];
		for (int i=0;i<in.length;i++)in[i]=(byte)i;
//		new Random().nextBytes(in); // Random will actually make deflation bigger
		byte[] out = Utils.deflate(in, 0, in.length, 9);
		System.out.println("Before "+in.length+", after "+out.length);
		try {
			out = Utils.inflate(out, 0, out.length);
			System.out.println("Back "+out.length+", equals "+Arrays.equals(in, out));
		} catch (IOException e) {
			Throwable t = e.getCause();
			System.err.println(t.getClass()+" - "+t.getMessage());
		}
	}
	
	public static void main(String[] args) throws IOException {
		testDeflate();
//		testParents();
//		testEscape();
//		testNBSok();
	}
	
}
