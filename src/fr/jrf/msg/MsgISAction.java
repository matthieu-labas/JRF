package fr.jrf.msg;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import fr.jrf.ByteBufferOut;
import fr.jrf.RemoteInputStream;

/**
 * <p>Perform an action on a {@link RemoteInputStream}.</p>
 * @see StreamAction
 * 
 * @author Matthieu Labas
 */
public class MsgISAction extends MsgFileCmd {
	
	/** The action to perform. */
	protected StreamAction action;
	
	/** The number of bytes to skip. */
	protected long val;
	
	// Mandatory no-arg constructor
	public MsgISAction() {
		super((short)-1);
	}
	
	public MsgISAction(StreamAction action, short fileID, long val) {
		super(fileID);
		this.action = action;
		this.val = val;
	}
	
	public MsgISAction(StreamAction action, short fileID) {
		this(action, fileID, -1l);
	}
	
	public StreamAction getAction() {
		return action;
	}
	
	public long getValue() {
		return val;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(12);
		bb.writeShort(fileID);
		bb.writeByte(action.ordinal());
		bb.writeLong(val);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			fileID = dis.readShort();
			action = StreamAction.values()[dis.readByte()];
			val = dis.readLong();
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+" "+action+"/"+val+" on file "+fileID;
	}
	
	
	
	public static enum StreamAction {
		AVAILABLE,
		SKIP,
		MARK_SUPPORTED,
		MARK,
		RESET,
		;
	}
	
}
