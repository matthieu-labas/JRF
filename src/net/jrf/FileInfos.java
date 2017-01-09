package net.jrf;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

/**
 * {@link java.io.File} meta information.
 * 
 * @author Matthieu Labas
 */
public class FileInfos {
	
	public static final int BIT_ISFILE = 1 << 0;
	public static final int BIT_ISDIRECTORY = 1 << 1;
	public static final int BIT_ISHIDDEN = 1 << 2;
	public static final int BIT_CANREAD = 1 << 3;
	public static final int BIT_CANWRITE = 1 << 4;
	public static final int BIT_CANEXECUTE = 1 << 5;
	
	private String name;
	private long length;
	private long lastModified;
	/** File attributes (see {@code BIT_} for each attribute mask). */
	private byte attributes;
	
	public FileInfos(File f) {
		name = f.getName();
		if (name.isEmpty()) // Happens when 'f' is a root
			name = f.getPath();
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
	
	public FileInfos(String name, long length, long lastModified, byte attributes) {
		this.name = name;
		this.length = length;
		this.lastModified = lastModified;
		this.attributes = attributes;
	}
	
	public FileInfos(DataInputStream dis) throws IOException {
		decode(dis);
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
	
	public byte getAttributes() {
		return attributes;
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
	
	/**
	 * @return A raw estimate of the encoded size for this instance, to be used to size different buffers.
	 */
	public int guessEncodedSize() {
		return 21+2*name.length();
	}
	
	/**
	 * Append this file information to the given {@code ByteBufferOut}.
	 * @param buf The buffer to update.
	 * @return {@code buf}.
	 * @throws IOException
	 */
	public ByteBufferOut encodeAppend(ByteBufferOut buf) throws IOException {
		buf.writeString(name);
		buf.writeLong(lastModified);
		buf.writeLong(length);
		buf.writeByte(attributes);
		return buf;
	}
	
	public void decode(DataInputStream dis) throws IOException {
		name = Utils.readString(dis);
		lastModified = dis.readLong();
		length = dis.readLong();
		attributes = dis.readByte();
	}
	
	@Override
	public String toString() {
		return name;
	}
	
}