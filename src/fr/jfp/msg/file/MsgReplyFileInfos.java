package fr.jfp.msg.file;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

import fr.jfp.ByteBufferOut;
import fr.jfp.msg.Message;

/**
 * Wraps several {@link java.io.File} information into a single message, for efficiency.
 * 
 * @author Matthieu Labas
 */
public class MsgReplyFileInfos extends Message {
	
	protected static final int BIT_ISFILE = 1 << 0;
	protected static final int BIT_ISDIRECTORY = 1 << 1;
	protected static final int BIT_ISHIDDEN = 1 << 2;
	protected static final int BIT_CANREAD = 1 << 3;
	protected static final int BIT_CANWRITE = 1 << 4;
	protected static final int BIT_CANEXECUTE = 1 << 5;
	
	protected String name;
	protected long length;
	protected long lastModified;
	protected byte attributes;
	
	// Mandatory nullary constructor
	public MsgReplyFileInfos() {
		this((short)-1, null);
	}
	
	public MsgReplyFileInfos(short replyTo, File f) {
		super(replyTo);
		if (f != null) {
			name = f.getName();
			length = f.length();
			lastModified = f.lastModified();
			attributes = 0;
			if (f.isDirectory()) attributes |= BIT_ISDIRECTORY;
			if (f.isFile())      attributes |= BIT_ISFILE;
			if (f.isHidden())    attributes |= BIT_ISHIDDEN;
			if (f.canRead())     attributes |= BIT_CANREAD;
			if (f.canWrite())    attributes |= BIT_CANWRITE;
			if (f.canExecute())  attributes |= BIT_CANEXECUTE;
		}
	}
	
	public String getName() {
		return name;
	}
	
	public long length() {
		return length;
	}
	
	public long lastModified() {
		return lastModified;
	}
	
	public boolean isDirectory() {
		return (attributes & BIT_ISDIRECTORY) != 0;
	}
	
	public boolean isFile() {
		return (attributes & BIT_ISFILE) != 0;
	}
	
	public boolean isHidden() {
		return (attributes & BIT_ISFILE) != 0;
	}
	
	public boolean canRead() {
		return (attributes & BIT_CANREAD) != 0;
	}
	
	public boolean canWrite() {
		return (attributes & BIT_CANWRITE) != 0;
	}
	
	public boolean canExecute() {
		return (attributes & BIT_CANEXECUTE) != 0;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(19+2*name.length());
		bb.writeString(name);
		bb.writeLong(length);
		bb.writeLong(lastModified);
		bb.write(attributes);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			name = readString(dis);
			length = dis.readLong();
			lastModified = dis.readLong();
			attributes = dis.readByte();
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+":"+name;
	}
	
}
