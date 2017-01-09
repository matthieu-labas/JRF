package net.jrf.msg.file;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

import net.jrf.ByteBufferOut;
import net.jrf.msg.Message;

/**
 * Reply message for {@code java.io.File} methods returning a {@code long}.
 * <p><ul>
 * <li>{@link File#length()}</li>
 * <li>{@link File#getFreeSpace()}</li>
 * <li>{@link File#getTotalSpace()}</li>
 * <li>{@link File#getUsableSpace()}</li>
 * </ul></p>
 * @author Matthieu Labas
 */
public class MsgFileLong extends Message {
	
	protected long val;
	
	// Mandatory nullary constructor
	public MsgFileLong() {
		this((short)-1, -1l);
	}
	
	public MsgFileLong(short replyTo, long val) {
		super(replyTo);
		this.val = val;
	}
	
	public long getValue() {
		return val;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(8);
		bb.writeLong(val);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			val = dis.readLong();
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+":"+val;
	}
	
}
