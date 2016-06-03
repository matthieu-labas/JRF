package fr.jfp.msg.file;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

import fr.jfp.ByteBufferOut;
import fr.jfp.msg.Message;

/**
 * Abstract class to map a remote {@code java.io.File} operation. The class defines {@code encode()}
 * and {@code decode()} methods, as the only data is the file name. The operation itself is defined
 * by the sub-class name.
 * 
 * @author Matthieu Labas
 */
public abstract class MsgFile extends Message {
	
	protected String file;
	
	MsgFile(String file) {
		super();
		this.file = file;
	}
	
	public File getFile() {
		return new File(file);
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut((file == null ? 4 : 4+2*file.length()));
		bb.writeString(file);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			file = readString(dis);
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+":"+file;
	}
	
}
