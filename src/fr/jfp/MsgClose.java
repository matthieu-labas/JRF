/**
 * 
 */
package fr.jfp;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * <p>File close request.</p>
 * 
 * @author Matthieu Labas
 */
class MsgClose extends MsgFile {
	
	// Mandatory no-arg constructor
	public MsgClose() {
		super(-1);
	}
	
	public MsgClose(int fileID) {
		super(fileID);
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(4);
		bb.writeInt(fileID);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			fileID = dis.readInt();
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+" file "+fileID;
	}
	
}
