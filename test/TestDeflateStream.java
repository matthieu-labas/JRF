import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import fr.jrf.ByteBufferOut.DirectByteArrayOutputStream;

public class TestDeflateStream {
	
	public static void testStream() throws IOException {
		Deflater defl = new Deflater(9);
		DirectByteArrayOutputStream bos = new DirectByteArrayOutputStream(8192);
		final DirectByteArrayOutputStream bos2 = new DirectByteArrayOutputStream(8192000);
		int sz = 0, tot = 0, len = 0;
		final byte[] out = new byte[8192];
		try (DeflaterOutputStream dos = new DeflaterOutputStream(bos, defl)) {
			try (FileInputStream fis = new FileInputStream("/dataw/Temp/Presentation NOVATECH 22 11 2016.pdf")) {
				byte[] in = new byte[8192];
				int ni;
				for (;;) {
					ni = fis.read(in);
					if (ni < 0)
						break;
					tot += ni;
					dos.write(in, 0, ni);
					while ((sz = bos.size()) > out.length) {
						int l = bos.reset(out, 0, out.length);
						len += l;
						bos2.write(out, 0, l);
						System.out.println(sz+" => reset "+l+" -> "+bos.size());
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} finally {
			// DeflaterOutputStream is closed, remaining bytes are compressed and written to bos => write them to bos2
			for (;;) {
				int l = bos.reset(out, 0, out.length);
				if (l == 0)
					break;
				len += l;
				bos2.write(out, 0, l);
			}
			System.out.println("Total      "+tot);
			System.out.println("Compressed "+bos2.size());
			defl.end();
		}
		
		len = 0;
		try (InflaterInputStream dis = new InflaterInputStream(new ByteArrayInputStream(bos2.toByteArray(), 0, bos2.size()))) {
			int n;
			for (;;) {
				n = dis.read(out);
				if (n < 0)
					break;
				len += n;
			}
		} finally {
//			System.out.println("Total "+tot);
			System.out.println("After "+len);
		}
		
		final PipedOutputStream pos = new PipedOutputStream(); // Create POS first, see http://stackoverflow.com/a/23874232/1098603
		len = 0;
		int n;
		try (final InflaterInputStream dis = new InflaterInputStream(new PipedInputStream(pos))) {
			Thread th = new Thread() {
				@Override public void run() {
					int len = 0;
					try {
						int n;
						ByteArrayInputStream bis = new ByteArrayInputStream(bos2.toByteArray(), 0, bos2.size());
						for (;;) {
							n = bis.read(out);
							if (n < 0)
								break;
							len += n;
							pos.write(out, 0, n);
						}
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						System.out.println("Written pipe "+len);
					}
				}
			};
			th.start();
			byte[] b = new byte[1024];
			for (;;) {
				n = dis.read(b);
				if (n < 0)
					break;
				len += n;
			}
			while (th.isAlive())
				try {th.join();} catch (InterruptedException e) {}
		} finally {
			System.out.println("Read "+len);
		}
	}
	
	public static void main(String[] args) throws IOException {
		testStream();
	}
	
}
