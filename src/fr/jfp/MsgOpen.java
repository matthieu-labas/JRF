package fr.jfp;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * <p>Open file request.</p>
 * <p>The {@link #file} is an absolute path, as seen by the Server. An optional {@link #deflate} level
 * can be specified to activate in-place deflate when transferring file chunks.</p>
 * 
 * @author Matthieu Labas
 */
class MsgOpen extends Message {
	
	/** The file name to open. */
	String file;
	
	/** The requested deflate level for chunk transfer. No deflate requested when {@code <= 0}. */
	int deflate;
	
	// Mandatory no-arg constructor
	public MsgOpen() { }
	
	public MsgOpen(String file, int deflate) {
		this.file = file;
		this.deflate = deflate;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(8+2*file.length()); // Should be enough
		bb.writeString(file);
		bb.writeInt(deflate);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			file = readString(dis);
			deflate = dis.readInt();
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+" "+file;
	}
	
}
