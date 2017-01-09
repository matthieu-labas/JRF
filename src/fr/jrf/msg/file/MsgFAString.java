package fr.jrf.msg.file;

import java.io.DataInputStream;
import java.io.IOException;

import fr.jrf.ByteBufferOut;

public class MsgFAString extends MsgFileAction {
	
	protected String val;
	
	public MsgFAString() {
		this(null, null, null);
	}
	
	public MsgFAString(FileAction action, String pathname, String value) {
		super(action, pathname);
		this.val = value;
	}
	
	public String getValue() {
		return val;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = super.encode();
		bb.writeString(val);
		return bb;
	}
	
	@Override
	protected void decode(DataInputStream dis) throws IOException {
		super.decode(dis);
		val = readString(dis);
	}
	
}
