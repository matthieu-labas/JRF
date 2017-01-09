package fr.jrf.msg;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import fr.jrf.ByteBufferOut;

public class MsgFlush extends MsgFileCmd {
	
	public MsgFlush() {
		this((short)-1);
	}
	
	public MsgFlush(short fileID) {
		super(fileID);
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(12);
		bb.writeShort(fileID);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			fileID = dis.readShort();
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+" flush on file "+fileID;
	}
	
}
