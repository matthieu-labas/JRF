package fr.jfp;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * <p>Message received to acknowledge a command or report an error during execution of the last
 * command.</p>
 * 
 * @author Matthieu Labas
 */
class MsgAck extends MsgFile {
	
	// Ack codes constants
	
	/** Operation succeeded. */
	public static final int OK = 0;
	/** Operation succeeded with warning. */
	public static final int WARN = 1;
	/** Operation failed. */
	public static final int ERR = 2;
	
	/** Return code. */
	int code;
	
	/** An optional error message, in case of an exception being thrown (when {@link #code} is
	 * {@link #WARN} or {@link #ERR}). {@code null} if not present. */
	String msg;
	
	// Mandatory no-arg constructor
	public MsgAck() {
		super(-1);
	}
	
	MsgAck(int replyTo, int fileID, int code, String msg) {
		super(fileID);
		this.replyTo = replyTo;
		this.code = code;
		this.msg = msg;
	}
	
	MsgAck(int replyTo, int fileID) {
		this(replyTo, fileID, OK);
	}
	
	MsgAck(int replyTo, int fileID, int code) {
		this(replyTo, fileID, code, null);
	}
	
	public static ByteBuffer encode(int idFichier, int code, String msg) {
		byte[] _msg = null;
		if (msg != null)
			_msg = msg.getBytes(charset);
		ByteBuffer bb = ByteBuffer.allocate(12 + (_msg == null ? 0 : _msg.length));
		bb.putInt(idFichier);
		bb.putInt(code);
		bb.putInt(_msg == null ? -1 : _msg.length);
		if (_msg.length > 0)
			bb.put(_msg, 0, _msg.length);
		return bb;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		byte[] _msg = null;
		if (msg != null)
			_msg = msg.getBytes(charset);
		ByteBufferOut bb = new ByteBufferOut(12+(_msg == null ? 0 : _msg.length));
		bb.writeInt(fileID);
		bb.writeInt(code);
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
			fileID = dis.readInt();
			code = dis.readInt();
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
