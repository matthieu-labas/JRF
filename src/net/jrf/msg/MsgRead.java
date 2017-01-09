package net.jrf.msg;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import net.jrf.ByteBufferOut;

public class MsgRead extends MsgFileCmd {
	
	/** The number of bytes to read. */
	protected int len;
	
	// Mandatory no-arg constructor
	public MsgRead() {
		super((short)-1);
	}
	
	public MsgRead(short fileID, int len) {
		super(fileID);
		this.len = len;
	}
	
	public int getLength() {
		return len;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(6);
		bb.writeShort(fileID);
		bb.writeInt(len);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			fileID = dis.readShort();
			len = dis.readInt();
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+" "+len+" bytes on file "+fileID;
	}
	
}
