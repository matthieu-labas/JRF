package net.jrf.msg;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import net.jrf.ByteBufferOut;

public class MsgWrite extends MsgFileCmd {
	
	/** The buffer to write, which is supposed to be compressed as per the value of {@link #deflate}. */
	protected byte[] buffer;
	
	/** The offset in {@link #buffer}. */
	protected int off;
	
	/** The number of bytes to write. */
	protected int len;
	
	/** The deflate level {@link #buffer} was deflated with, or {@code <= 0} if {@code buffer} is
	 * not deflated. N.B. that the value is not necessary for inflation but is kept for potential
	 * logging. */
	protected int deflate;
	
	// Mandatory no-arg constructor
	public MsgWrite() {
		super((short)-1);
	}
	
	public MsgWrite(short fileID, byte[] buf, int off, int len, int deflate) {
		super(fileID);
		this.buffer = buf;
		this.off = off;
		this.len = len;
		this.deflate = deflate;
	}
	
	public byte[] getBuffer() {
		return buffer;
	}
	
	public int getOffset() {
		return off;
	}
	
	public int getLength() {
		return len;
	}
	
	public int getDeflate() {
		return deflate;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(6+len);
		bb.writeShort(fileID);
		bb.writeByte(deflate); // Between 0 and 9
		bb.writeInt(len);
		bb.write(buffer, off, len);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			fileID = dis.readShort();
			deflate = dis.readByte();
			len = dis.readInt();
			buffer = new byte[len];
			dis.readFully(buffer);
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+" "+len+" bytes on file "+fileID;
	}
	
}
