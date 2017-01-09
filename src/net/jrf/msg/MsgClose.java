/**
 * 
 */
package net.jrf.msg;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import net.jrf.ByteBufferOut;

/**
 * <p>File close request.</p>
 * 
 * @author Matthieu Labas
 */
public class MsgClose extends MsgFileCmd {
	
	// Mandatory no-arg constructor
	public MsgClose() {
		super((short)-1);
	}
	
	public MsgClose(short fileID) {
		super(fileID);
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(4);
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
		return stdToString()+" file "+fileID;
	}
	
}
