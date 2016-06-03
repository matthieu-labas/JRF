/**
 * 
 */
package fr.jfp.msg;

/**
 * <p>Abstract class denoting a file operation. Used to retrieve the associated file ID.
 * {@link #encode()} and {@link #decode(byte[])} are left up to implementors.</p>
 * 
 * @author Matthieu Labas
 */
public abstract class MsgFileCmd extends Message {
	
	/** The file ID to close. */
	protected int fileID;
	
	public MsgFileCmd(int replyTo, int fileID) {
		super(replyTo);
		this.fileID = fileID;
	}
	
	public MsgFileCmd(int fileID) {
		this(-1, fileID);
	}
	
	public int getFileID() {
		return fileID;
	}
	
}
