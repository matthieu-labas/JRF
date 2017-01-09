package net.jrf.msg.file;

import java.io.DataInputStream;
import java.io.IOException;

import net.jrf.ByteBufferOut;

public class MsgFALong extends MsgFileAction {
	
	protected long val;
	
	public MsgFALong() {
		this(null, null, -1l);
	}
	
	public MsgFALong(FileAction action, String pathname, long value) {
		super(action, pathname);
		this.val = value;
	}
	
	public long getValue() {
		return val;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = super.encode();
		bb.writeLong(val);
		return bb;
	}
	
	@Override
	protected void decode(DataInputStream dis) throws IOException {
		super.decode(dis);
		val = dis.readLong();
	}
	
}
