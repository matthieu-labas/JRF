package fr.jfp;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import fr.jfp.msg.Message;

/**
 * <p>Convenience class to use as a byte buffer to serialize objects before writing them to a Socket.</p>
 * <p>It basically wrapping {@link DataOutput} on top of a {@code byte[]} (more formally, extending
 * {@link DataOutputStream} wrapping {@link ByteArrayOutputStream}).</p>
 * 
 * @author Matthieu Labas
 */
public class ByteBufferOut extends DataOutputStream {
	
	/**
	 * Create an empty byte buffer of the given initial size.
	 * @param size The size to allocate.
	 */
	public ByteBufferOut(int size) {
		super(new StraightByteArrayOutputStream(size));
	}
	
	/**
	 * Create a pre-initialized byte buffer ({@code StraightByteArrayOutputStream} wrapping {@code buf}).
	 * @param buf The data buffer.
	 * @param len The number of already-valid bytes in {@code buf} (i.e. after which further {@code write()}
	 * 		operations should write bytes).
	 */
	public ByteBufferOut(byte[] buf, int len) {
		super(new StraightByteArrayOutputStream(buf, len));
		written = len; // Initialize the size() of the DataOutputStream.
	}
	
	/**
	 * Create a pre-initialized byte buffer ({@code StraightByteArrayOutputStream} wrapping {@code buf}).
	 * As {@code buf} is considered "full", that constructor is useful to wrap an already-encoded
	 * buffer.
	 * @param buf The data buffer, considered full.
	 */
	public ByteBufferOut(byte[] buf) {
		this(buf, buf.length);
	}
	
	/**
	 * Overrides {@link ByteArrayOutputStream#toByteArray()} which is synchronized and creates a copy
	 * of the internal byte array. That version is not synchronized and returns the raw byte array,
	 * <strong>in which only the first {@link #size()} bytes are valid!</strong>
	 * @return The raw byte array.
	 */
	public byte[] getRawArray() {
		return ((StraightByteArrayOutputStream)out).toByteArray();
	}
	
	/**
	 * <p>Utility method to write a {@code String} in the underlying {@code DataOutputStream}.</p>
	 * <p>The string is converted as {@code byte[]} using the {@link Message#charset}, its {@code length}
	 * is written, followed by the extracted {@code byte[]}. An empty string codes only a {@code 0}
	 * size, a {@code null} string codes only a {@code -1} size.</p>
	 * @param str The string to write.
	 * @throws IOException
	 */
	public void writeString(String str) throws IOException {
		if (str == null) {
			writeInt(-1);
		} else if (str.isEmpty()) {
			writeInt(-1);
		} else {
			byte[] buf = str.getBytes(Message.charset);
			writeInt(buf.length);
			write(buf, 0, buf.length);
		}
	}
	
	
	
	/**
	 * Unsynchronized version of {@link ByteArrayOutputStream} to get direct access to the buffer
	 * and its size.
	 * 
	 * @author Matthieu Labas
	 */
	public static class StraightByteArrayOutputStream extends ByteArrayOutputStream {
		
		public StraightByteArrayOutputStream(int size) {
			super(size);
		}
		
		/**
		 * Wraps the given byte buffer.
		 * @param buf The byte buffer.
		 * @param len The number of valid bytes in {@code buf}.
		 * @throws NullPointerException if {@code buf} is {@code null}.
		 * @throws NegativeArraySizeException if {@code len < 0}.
		 * @throws ArrayIndexOutOfBoundsException if {@code len > buf.length}.
		 */
		public StraightByteArrayOutputStream(byte[] buf, int len) {
			if (buf == null)
				throw new NullPointerException();
			if (len < 0)
				throw new NegativeArraySizeException(""+len);
			if (len > buf.length)
				throw new ArrayIndexOutOfBoundsException(len+" > "+buf.length);
			this.buf = buf;
			count = len;
		}
		
		@Override
		public byte[] toByteArray() {
			return buf;
		}
		
		@Override
		public int size() {
			return count;
		}
		
	}
}