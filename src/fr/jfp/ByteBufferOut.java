package fr.jfp;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * <p>Convenience class to use as a byte buffer to serialize objects before writing them to a Socket.</p>
 * <p>It basically wrapping {@link DataOutput} on top of a {@code byte[]} (more formally, extending
 * {@link DataOutputStream} wrapping {@link ByteArrayOutputStream}).</p>
 * 
 * @author Matthieu Labas
 */
public class ByteBufferOut extends DataOutputStream {
	
	public ByteBufferOut(int size) {
		super(new StraightByteArrayOutputStream(size));
	}
	
	public byte[] getArray() {
		return ((StraightByteArrayOutputStream)out).toByteArray();
	}
	
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
		
		public StraightByteArrayOutputStream(byte[] buf) {
			this.buf = buf;
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