/**
 * 
 */
package fr.jfp;

/**
 * <p>Abstract class denoting a file operation. Used to retrieve the associated file ID.
 * {@link #encode()} and {@link #decode(byte[])} are left up to implementors.</p>
 * 
 * @author Matthieu Labas
 */
abstract class MsgFile extends Message {
	
	/** The file ID to close. */
	int fileID;
	
	public MsgFile(int replyTo, int fileID) {
		super(replyTo);
		this.fileID = fileID;
	}
	
	public MsgFile(int fileID) {
		this(-1, fileID);
	}
	
}
