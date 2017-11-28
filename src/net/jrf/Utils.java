package net.jrf;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import net.jrf.ByteBufferOut.DirectByteArrayOutputStream;
import net.jrf.msg.Message;

/**
 * Class full of small static utilities methods.
 * 
 * @author Matthieu Labas
 */
public class Utils {
	
	/**
	 * Compress a byte array with a given level.
	 * @param source The array to compress.
	 * @param off The offset in {@code source}.
	 * @param len The number of bytes to process in {@code source}.
	 * @param level The compression level (0-9).
	 * @return The compressed array.
	 */
	public static byte[] deflate(byte[] source, int off, int len, int level) {
		Deflater defl = new Deflater(level);
		try {
			return deflate(source, off, len, defl);
		} finally {
			defl.end();
		}
	}
	
	/**
	 * Compress a byte array with a given {@link Deflater}.
	 * @param source The array to compress.
	 * @param off The offset in {@code source}.
	 * @param len The number of bytes to process in {@code source}.
	 * @param defl The deflater to use.
	 * @return The compressed array.
	 */
	public static byte[] deflate(byte[] source, int off, int len, Deflater defl) {
		defl.setInput(source, off, len);
		defl.finish();
		byte[] buf = new byte[1024];
		try (DirectByteArrayOutputStream bos = new DirectByteArrayOutputStream(1024)) {
			int n;
			while ((n = defl.deflate(buf)) > 0)
				bos.write(buf, 0, n);
			defl = null;
			return Arrays.copyOf(bos.toByteArray(), bos.size());
		} catch (IOException e) { // Never happens with ByteArrayOutputStream
			return null;
		}
	}
	
	/**
	 * Utility method to decompress a compressed byte array.
	 * @param source The compressed array.
	 * @param off The offset in {@code source}.
	 * @param len The number of bytes to decompress from {@code source}.
	 * @return The decompressed byte array.
	 * @throws IOException If input array contains invalid data.
	 */
	public static byte[] inflate(byte[] source, int off, int len) throws IOException {
		Inflater infl = new Inflater();
		try {
			return inflate(source, off, len, infl);
		} finally {
			infl.end();
		}
	}
	
	/**
	 * Utility method to decompress a compressed byte array, using the given {@link Inflater}.
	 * @param source The compressed array.
	 * @param off The offset in {@code source}.
	 * @param len The number of bytes in {@code source}.
	 * @param infl The inflater to use for decompression.
	 * @return The decompressed byte array.
	 * @throws IOException If input array contains invalid data.
	 */
	public static byte[] inflate(byte[] source, int off, int len, Inflater infl) throws IOException {
		infl.setInput(source, off, len);
		byte[] buf = new byte[1024];
		try (DirectByteArrayOutputStream bos = new DirectByteArrayOutputStream(buf.length)) {
			int n;
			while ((n = infl.inflate(buf)) > 0)
				bos.write(buf, 0, n);
			return Arrays.copyOf(bos.toByteArray(), bos.size());
		} catch (DataFormatException e) {
			throw new IOException("Cannot inflate data: "+e.getMessage(), e);
		}
	}
	
	/**
	 * Reads every possible byte into the given array, returning the number of bytes actually read,
	 * which can be {@code < buf.length} if EOF has been reached on {@code is}.
	 * @param is The {@code InputStream} to read.
	 * @param buf The buffer to fill with data read from {@code is}.
	 * @return The number of bytes read (up to {@code buf.length}.
	 * @throws IOException if an I/O error occurs.
	 */
	public static int readFully(InputStream is, byte[] buf) throws IOException {
		int n;
		int len = buf.length, tot = 0;
		while (len > 0) {
			n = is.read(buf, tot, len);
			if (n < 0)
				break;
			tot += n;
			len -= n;
		}
		return tot;
	}
	
	/**
	 * Utility method to read a {@code String} from a {@code DataInput}, as encoded by {@link ByteBufferOut#writeString(String)}.
	 * @param data The {@code DataInput} from which to read the {@code String}.
	 * @return The {@code String}.
	 * @throws IOException from reading {@code data}.
	 */
	public static String readString(DataInput data) throws IOException {
		int sz = data.readShort();
		if (sz < 0)
			return null;
		if (sz == 0)
			return "";
		byte[] buf = new byte[sz];
		data.readFully(buf);
		return new String(buf, Message.charset);
	}
	
}
