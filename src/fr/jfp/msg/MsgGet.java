package fr.jfp.msg;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import fr.jfp.ByteBufferOut;

/**
 * Special message to request a complete file, potentially deflated.
 * 
 * @author Matthieu Labas
 */
public class MsgGet extends Message {
	
	/** The file name to open. */
	protected String file;
	
	/** The requested deflate level for chunk transfer. No deflate requested when {@code <= 0}. */
	protected int deflate;
	
	// Mandatory no-arg constructor
	public MsgGet() {
		super();
	}
	
	public MsgGet(String file, int deflate) {
		super();
		this.file = file;
		this.deflate = deflate;
	}
	
	public String getFilename() {
		return file;
	}
	
	public int getDeflate() {
		return deflate;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(8+2*file.length()); // Should be enough
		bb.writeString(file);
		bb.writeByte(deflate);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			file = readString(dis);
			deflate = dis.readByte();
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+" "+file;
	}
	
}
