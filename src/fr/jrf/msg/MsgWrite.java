package fr.jrf.msg;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import fr.jrf.ByteBufferOut;

public class MsgWrite extends MsgFileCmd {
	
	/** The buffer to write. */
	protected byte[] buffer;
	
	/** The offset in {@link #buffer}. */
	protected int off;
	
	/** The number of bytes to write. */
	protected int len;
	
	// Mandatory no-arg constructor
	public MsgWrite() {
		super((short)-1);
	}
	
	public MsgWrite(short fileID, byte[] buf, int off, int len) {
		super(fileID);
		this.buffer = buf;
		this.off = off;
		this.len = len;
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
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(6+len);
		bb.writeShort(fileID);
		bb.writeInt(len);
		bb.write(buffer, off, len);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			fileID = dis.readShort();
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
