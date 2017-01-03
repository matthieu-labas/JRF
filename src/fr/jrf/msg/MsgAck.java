package fr.jrf.msg;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import fr.jrf.ByteBufferOut;

/**
 * <p>Message received to acknowledge a command or report an error during execution of the last
 * command.</p>
 * 
 * @author Matthieu Labas
 */
public class MsgAck extends MsgFileCmd {
	
	// Ack codes constants
	
	/** Operation succeeded. */
	public static final int OK = 0;
	/** Operation succeeded with warning. */
	public static final int WARN = 1;
	/** Operation failed. */
	public static final int ERR = 2;
	
	/** Return code. */
	protected long code;
	
	/** An optional error message, in case of an exception being thrown (when {@link #code} is
	 * {@link #WARN} or {@link #ERR}). {@code null} if not present. */
	protected String msg;
	
	// Mandatory no-arg constructor
	public MsgAck() {
		super((short)-1);
	}
	
	public MsgAck(short replyTo, short fileID, long code, String msg) {
		super(fileID);
		this.replyTo = replyTo;
		this.code = code;
		this.msg = msg;
	}
	
	public MsgAck(short replyTo, short fileID) {
		this(replyTo, fileID, OK);
	}
	
	MsgAck(short replyTo, short fileID, long code) {
		this(replyTo, fileID, code, null);
	}
	
	public long getCode() {
		return code;
	}
	
	public String getMessage() {
		return msg;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		byte[] _msg = null;
		if (msg != null)
			_msg = msg.getBytes(charset);
		ByteBufferOut bb = new ByteBufferOut(12+(_msg == null ? 0 : _msg.length));
		bb.writeShort(fileID);
		bb.writeLong(code);
		if (_msg == null)
			bb.writeInt(-1);
		else {
			bb.writeInt(_msg.length);
			if (_msg.length > 0)
				bb.write(_msg, 0, _msg.length);
		}
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			fileID = dis.readShort();
			code = dis.readLong();
			int n = dis.readInt();
			if (n < 0)
				msg = null;
			else if (n == 0)
				msg = "";
			else
				msg = readString(dis);
		}
	}
	
	@Override
	public String toString() {
		String s = stdToString()+" on file "+fileID+" - code "+code;
		if (msg != null)
			s += " ["+msg+"]";
		return s;
	}
	
}
