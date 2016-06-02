package fr.jfp;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * <p>Skip request in file.</p>
 * 
 * @author Matthieu Labas
 */
public class MsgSkip extends MsgFile {
	
	/** The number of bytes to skip. */
	long skip;
	
	// Mandatory no-arg constructor
	public MsgSkip() {
		super(-1);
	}
	
	public MsgSkip(int fileID, long skip) {
		super(fileID);
		this.skip = skip;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(12);
		bb.writeInt(fileID);
		bb.writeLong(skip);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			fileID = dis.readInt();
			skip = dis.readLong();
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+" "+skip+" bytes on file "+fileID;
	}
	
}
