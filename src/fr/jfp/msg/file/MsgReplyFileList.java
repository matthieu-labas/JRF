package fr.jfp.msg.file;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import fr.jfp.ByteBufferOut;
import fr.jfp.msg.Message;

/**
 * <p>Message received to acknowledge a command or report an error during execution of the last
 * command.</p>
 * 
 * @author Matthieu Labas
 */
public class MsgReplyFileList extends Message {
	
	/** The list of file names. */
	protected String[] files;
	
	/** Indicate this is the last message. */
	protected boolean last;
	
	// Mandatory no-arg constructor
	public MsgReplyFileList() {
		this((short)-1, null, false);
	}
	
	public MsgReplyFileList(short replyTo, String[] files, boolean last) {
		super(replyTo);
		this.files = files;
		this.last = last;
	}
	
	public boolean isLast() {
		return last;
	}
	
	public String[] getFiles() {
		return files;
	}
	
	public void setFiles(String[] files) {
		this.files = files;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		int n = 8;
		for (String f : files)
			n += 2*f.length();
		ByteBufferOut bb = new ByteBufferOut(n);
		bb.writeByte(last ? 0 : 1);
		bb.writeInt(files.length);
		for (String f : files)
			bb.writeString(f);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			last = (dis.readByte() != 0);
			files = new String[dis.readInt()];
			for (int i = 0; i < files.length; i++)
				files[i] = readString(dis);
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+" "+files.length+" files";
	}
	
}
