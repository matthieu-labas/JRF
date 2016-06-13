package fr.jfp.msg;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import fr.jfp.ByteBufferOut;
import fr.jfp.ByteBufferOut.StraightByteArrayOutputStream;

public class MsgData extends MsgFileCmd {
	
	/** The chunk data, which is supposed to be compressed as per the value of {@link #deflate}. */
	protected byte[] data;
	
	/** Valid number of bytes in {@link #data}. */
	protected int len;
	
	/** The deflate level {@link #data} was deflated with, or {@code <= 0} if {@link data} is
	 * not deflated. N.B. that the value is not necessary for inflation but is kept for potential
	 * logging. */
	protected int deflate;
	
	/** {@code false} if this is the last reply data message. */
	protected boolean hasNext;
	
	// Mandatory no-arg constructor
	public MsgData() {
		super((short)-1);
	}
	
	/**
	 * Create a new data chunk message for the specified file. If indicated ({@code deflate > 0},
	 * the data will be deflated upon construction (i.e. by the constructor).
	 * @param replyTo The message number asking for data.
	 * @param fileID The file {@code data} belongs to.
	 * @param data The chunk data.
	 * @param deflate If {@code > 0}, {@code data} should be considered deflated.
	 */
	public MsgData(short replyTo, short fileID, byte[] data, int len, int deflate, boolean hasNext) {
		super(replyTo, fileID);
		this.hasNext = hasNext;
		this.deflate = deflate;
		this.len = len;
		this.data = data;
	}
	
	public byte[] getData() {
		return data;
	}
	
	public int getLength() {
		return len;
	}
	
	public int getDeflate() {
		return deflate;
	}
	
	public boolean hasNext() {
		return hasNext;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(8+len);
		bb.writeShort(fileID);
		bb.writeByte(hasNext?1:0);
		bb.writeByte(deflate); // Between 0 and 9
		bb.writeInt(len);
		bb.write(data, 0, len);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			fileID = dis.readShort();
			hasNext = (dis.readByte() != 0);
			deflate = dis.readByte();
			len = dis.readInt();
			data = new byte[len];
			dis.readFully(data);
		}
	}
	
	/**
	 * Utility method to compress a byte array.
	 * @param source The array to compress.
	 * @param level The compression level (0-9).
	 * @return The compressed array.
	 */
	public static byte[] deflate(byte[] source, int level) {
		Deflater defl = new Deflater(level);
		defl.setInput(source);
		defl.finish();
		byte[] buf = new byte[1024];
		try (StraightByteArrayOutputStream bos = new StraightByteArrayOutputStream(1024)) {
			int n;
			while ((n = defl.deflate(buf)) > 0)
				bos.write(buf, 0, n);
			defl.end();
			defl = null;
			return Arrays.copyOf(bos.toByteArray(), bos.size());
		} catch (IOException e) { // Never happens with ByteArrayOutputStream
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Utility method to decompress a compressed byte array.
	 * @param source The compressed array.
	 * @return The decompressed byte array.
	 * @throws IOException If input array contains invalid data.
	 */
	public static byte[] inflate(byte[] source) throws IOException {
		Inflater infl = new Inflater();
		infl.setInput(source);
		byte[] buf = new byte[1024];
		try (StraightByteArrayOutputStream bos = new StraightByteArrayOutputStream(buf.length)) {
			int n;
			while ((n = infl.inflate(buf)) > 0)
				bos.write(buf, 0, n);
			return Arrays.copyOf(bos.toByteArray(), bos.size());
		} catch (DataFormatException e) {
			throw new IOException("Cannot inflate data: "+e.getMessage(), e);
		} finally {
			infl.end();
			infl = null;
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+" "+len+" bytes on file "+fileID;
	}
	
}
