package fr.jrf.msg.file;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

import fr.jrf.ByteBufferOut;
import fr.jrf.FileInfos;
import fr.jrf.msg.Message;

/**
 * Wraps several {@link java.io.File} information into a single message, for efficiency.
 * 
 * @author Matthieu Labas
 */
public class MsgReplyFileInfos extends Message {
	
	protected FileInfos infos;
	
	// Mandatory nullary constructor
	public MsgReplyFileInfos() {
		this((short)-1, null);
	}
	
	public MsgReplyFileInfos(short replyTo, File f) {
		super(replyTo);
		if (f != null)
			infos = new FileInfos(f);
	}
	
	public String getName() {
		return infos.getName();
	}
	
	public long length() {
		return infos.length();
	}
	
	public long lastModified() {
		return infos.lastModified();
	}
	
	public byte getAttributes() {
		return infos.getAttributes();
	}
	
	public boolean isDirectory() {
		return infos.isDirectory();
	}
	
	public boolean isFile() {
		return infos.isFile();
	}
	
	public boolean isHidden() {
		return infos.isHidden();
	}
	
	public boolean canRead() {
		return infos.canRead();
	}
	
	public boolean canWrite() {
		return infos.canWrite();
	}
	
	public boolean canExecute() {
		return infos.canExecute();
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		String name = infos.getName();
		ByteBufferOut bb = new ByteBufferOut(19+2*name.length());
		bb.writeString(name);
		bb.writeLong(infos.length());
		bb.writeLong(infos.lastModified());
		bb.write(infos.getAttributes());
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			infos = new FileInfos(readString(dis), dis.readLong(), dis.readLong(), dis.readByte());
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+":"+infos;
	}
	
}
