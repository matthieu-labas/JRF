package net.jrf.msg;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import net.jrf.ByteBufferOut;

/**
 * <p>Data chunk message, used to transfer file chunks between JRF client and server.
 * <p>Each data chunk can be separately compressed.
 * 
 * @author Matthieu Labas
 */
public class MsgData extends MsgFileCmd {
	
	/** The chunk data, which is supposed to be compressed as per the value of {@link #deflate}. */
	protected byte[] data;
	
	/** Valid number of bytes in {@link #data}. */
	protected int len;
	
	/** The deflate level {@link #data} was deflated with, or {@code <= 0} if {code data} is
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
	 * @param len The {@code data} length.
	 * @param deflate If {@code > 0}, {@code data} should be considered deflated.
	 * @param hasNext {@code true} if another data chunk is expected after this one.
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
	
	@Override
	public String toString() {
		return stdToString()+" "+len+" bytes on file "+fileID;
	}
	
}
