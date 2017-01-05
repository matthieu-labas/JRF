import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fr.jrf.RemoteFile;
import fr.jrf.RemoteInputStream;
import fr.jrf.RemoteOutputStream;
import fr.jrf.StreamInfo;
import fr.jrf.client.JRFClient;
import fr.jrf.msg.file.MsgReplyFileInfos;
import fr.jrf.server.JRFServer;

public class UnitTesting {
	
	// Local setup
	public static final String workDir = "/dataw/Temp";
	public static final String file2ReadRaw = workDir+"/test_raw.txt";
	public static final String file2ReadDeflate = workDir+"/test_deflate.txt";
	public static final String file2Write = workDir+"/test_out.txt";
	
	private static byte[] contentUndeflatable = "This string gets bigger on deflate".getBytes();
	private static byte[] contentDeflatable = "blablablablablablablablablablablablablabla".getBytes();
	
	public static void compareFileAttributes(File f1, File f2) {
		if (!f1.getName().equals(f2.getName()))
			fail("name");
		if (f1.canRead() != f2.canRead() || f1.canWrite() != f2.canWrite() || f1.canExecute() != f2.canExecute())
			fail("attributes");
		if (f1.length() != f2.length())
			fail("length");
		if (f1.lastModified() != f2.lastModified())
			fail("time");
		if (f1.isFile() != f2.isFile() || f1.isDirectory() != f2.isDirectory())
			fail("status");
	}
	
	public static void compareFileAttributes(File f1, MsgReplyFileInfos f2) {
		if (!f1.getName().equals(f2.getName()))
			fail("name");
		if (f1.canRead() != f2.canRead() || f1.canWrite() != f2.canWrite() || f1.canExecute() != f2.canExecute())
			fail("attributes");
		if (f1.length() != f2.length())
			fail("length");
		if (f1.lastModified() != f2.lastModified())
			fail("time");
		if (f1.isFile() != f2.isFile() || f1.isDirectory() != f2.isDirectory())
			fail("status");
	}
	
	JRFServer srv;
	JRFClient cli;
	
	@Before
	public void init() throws IOException {
		File wd = new File(workDir);
		assertTrue(wd.isDirectory());
		srv = JRFServer.get(new InetSocketAddress(JRFServer.DEFAULT_PORT));
		srv.start();
		cli = new JRFClient(new InetSocketAddress("127.0.0.1", JRFServer.DEFAULT_PORT));
		cli.start();
		try (OutputStream os = new FileOutputStream(file2ReadRaw)) {
			os.write(contentUndeflatable);
		}
		try (OutputStream os = new FileOutputStream(file2ReadDeflate)) {
			os.write(contentDeflatable);
		}
	}
	
	@Test
	public void fileInfoUnitary() {
		try {
			compareFileAttributes(new File(file2ReadRaw), new RemoteFile(cli, file2ReadRaw));
		} catch (IOException e) {
			fail(e.getMessage());
		}
	}
	
	@Test
	public void fileInfoBatch() {
		try {
			compareFileAttributes(new File(file2ReadRaw), new RemoteFile(cli, file2ReadRaw).getFileInfos());
		} catch (IOException e) {
			fail(e.getMessage());
		}
	}
	
	private StreamInfo read(byte[] expectedContent, String file, int deflate) {
		byte[] buf = new byte[expectedContent.length + 1];
		try (RemoteInputStream is = cli.getRemoteInputStream(file, deflate)) {
			int n = is.read(buf);
			buf = Arrays.copyOf(buf, n);
			assertArrayEquals(expectedContent, buf);
			return is.getInfo();
		} catch (IOException e) {
			fail(e.getMessage());
			return null;
		}
	}
	
	@Test
	public void read() {
		StreamInfo info = read(contentUndeflatable, file2ReadRaw, 0);
		assertEquals(info.getIOBytes(), info.getXferBytes()); // No deflate performed
	}
	
	@Test
	public void readDeflateNoDeflate() {
		StreamInfo info = read(contentUndeflatable, file2ReadRaw, 3);
		assertEquals(info.getIOBytes(), info.getXferBytes()); // No deflate performed, even though requested
	}
		
	@Test
	public void readDeflate() {
		StreamInfo info = read(contentDeflatable, file2ReadDeflate, 3);
		assertTrue(info.getIOBytes() > info.getXferBytes()); // Deflate performed
	}
	
	@Test
	public void writeDelete() {
		try (RemoteOutputStream os = cli.getRemoteOutputStream(file2Write)) {
			os.write(contentUndeflatable);
			StreamInfo info = os.getInfo();
			assertEquals(info.getIOBytes(), info.getXferBytes()); // No deflate performed
		} catch (IOException e) {
			fail(e.getMessage());
		} finally { // Delete created file
			try {
				assertTrue(new RemoteFile(cli, file2Write).delete());
			} catch (IOException e) {
				if (!new File(file2Write).delete())
					System.err.println("Cannot delete "+file2Write);
				fail(e.getMessage());
			}
		}
	}
	
	@Test
	public void writeDeflateNoDeflate() {
		try (RemoteOutputStream os = cli.getRemoteOutputStream(file2Write, 3)) {
			os.write(contentUndeflatable);
			StreamInfo info = os.getInfo();
			assertEquals(info.getIOBytes(), info.getXferBytes()); // No deflate performed, even through requested
		} catch (IOException e) {
			fail(e.getMessage());
		} finally {
			if (!new File(file2Write).delete())
				System.err.println("Cannot delete "+file2Write);
		}
	}
	
	@Test
	public void writeDeflate() {
		try (RemoteOutputStream os = cli.getRemoteOutputStream(file2Write, 3)) {
			os.write(contentDeflatable);
			StreamInfo info = os.getInfo();
			assertTrue(info.getIOBytes() > info.getXferBytes()); // Deflate performed
		} catch (IOException e) {
			fail(e.getMessage());
		} finally {
			if (!new File(file2Write).delete())
				System.err.println("Cannot delete "+file2Write);
		}
	}
	
	@Test
	public void list() {
		File dir = new File(workDir, "test");
		if (!dir.mkdir())
			fail("Cannot create test directory");
		for (int i = 1; i <= 3; i++) {
			try (OutputStream os = new FileOutputStream(new File(dir, "file"+i))) {
				os.write(new byte[]{(byte)i});
			} catch (IOException e) {
				fail(e.getMessage());
			}
		}
		
		try {
			File f = new RemoteFile(cli, dir.getPath());
			assertTrue(f.isDirectory());
			String[] list = f.list();
			assertNotNull(list);
			Arrays.sort(list); // For binarySearch() to work properly
			assertEquals(3, list.length);
			assertTrue(Arrays.binarySearch(list, "file1") >= 0);
			assertTrue(Arrays.binarySearch(list, "file2") >= 0);
			assertTrue(Arrays.binarySearch(list, "file3") >= 0);
			if (new File(dir, "file2").delete()) {
				list = f.list();
				assertTrue(Arrays.binarySearch(list, "file1") >= 0);
				assertTrue(Arrays.binarySearch(list, "file2") < 0); // Should not be seen anymore
				assertTrue(Arrays.binarySearch(list, "file3") >= 0);
			} else
				System.err.println("Cannot delete test file");
		} catch (IOException e) {
			fail(e.getMessage());
		}
		
		for (int i = 1; i <= 3; i++) {
			if (!new File(dir, "file"+i).delete())
				System.err.println("Cannot delete test file #"+i);
		}
		if (!dir.delete())
			System.err.println("Cannot delete test directory");
	}
	
	@After
	public void clean() {
		cli.requestStop();
		srv.requestStop();
		if (!new File(file2ReadRaw).delete())
			System.err.println("Could not delete "+file2ReadRaw);
		if (!new File(file2ReadDeflate).delete())
			System.err.println("Could not delete "+file2ReadDeflate);
		while (cli.isAlive())
			try{cli.join();}catch(InterruptedException e){}
		while (srv.isAlive())
			try{srv.join();}catch(InterruptedException e){}
	}
	
}
