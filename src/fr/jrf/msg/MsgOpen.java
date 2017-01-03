package fr.jrf.msg;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import fr.jrf.ByteBufferOut;

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
	
	/** {@code "r"} to read or {@code "w"} to write. */
	protected char mode;
	
	/** The requested deflate level for chunk transfer. No deflate requested when {@code <= 0}. */
	protected int deflate;
	
	// Mandatory no-arg constructor
	MsgOpen() {
		super();
	}
	
	public MsgOpen(String file, char mode, int deflate) {
		super();
		this.file = file;
		this.mode = mode;
		this.deflate = deflate;
	}
	
	public String getFile() {
		return file;
	}
	
	public char getMode() {
		return mode;
	}
	
	public int getDeflate() {
		return deflate;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(10+2*file.length()); // Should be enough
		bb.writeString(file);
		bb.writeChar(mode);
		bb.writeByte(deflate);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			file = readString(dis);
			mode = dis.readChar();
			deflate = dis.readByte();
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+" "+file+"["+mode+"]";
	}
	
}
