package fr.jfp.msg;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import fr.jfp.ByteBufferOut;

/**
 * <p>Open file request.</p>
 * <p>The {@link #file} is an absolute path, as seen by the Server. An optional {@link #deflate} level
 * can be specified to activate in-place deflate when transferring file chunks.</p>
 * 
 * @author Matthieu Labas
 */
public class MsgOpen extends Message {
	
	/** The file name to open. */
	protected String file;
	
	/** The requested deflate level for chunk transfer. No deflate requested when {@code <= 0}. */
	protected int deflate;
	
	// Mandatory no-arg constructor
	MsgOpen() {
		super();
	}
	
	public MsgOpen(String file, int deflate) {
		super();
		this.file = file;
		this.deflate = deflate;
	}
	
	public String getFile() {
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
