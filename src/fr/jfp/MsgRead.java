package fr.jfp;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

class MsgRead extends MsgFile {
	
	/** The number of bytes to read. */
	int len;
	
	// Mandatory no-arg constructor
	public MsgRead() {
		super(-1);
	}
	
	public MsgRead(int fileID, int len) {
		super(fileID);
		this.len = len;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(4);
		bb.writeInt(fileID);
		bb.writeInt(len);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			fileID = dis.readInt();
			len = dis.readInt();
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+" "+len+" bytes on file "+fileID;
	}
	
}
