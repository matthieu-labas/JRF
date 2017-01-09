/**
 * 
 */
package net.jrf.msg;

/**
 * <p>Abstract class denoting a file operation. Used to retrieve the associated file ID.
 * {@link #encode()} and {@link #decode(byte[])} are left up to implementors.</p>
 * 
 * @author Matthieu Labas
 */
public abstract class MsgFileCmd extends Message {
	
	protected short fileID;
	
	public MsgFileCmd(short replyTo, short fileID) {
		super(replyTo);
		this.fileID = fileID;
	}
	
	public MsgFileCmd(short fileID) {
		this((short)-1, fileID);
	}
	
	public short getFileID() {
		return fileID;
	}
	
}
