package fr.jrf.msg.file;

/**
 * Request all information about a file.
 * @author Matthieu Labas
 */
public class MsgFileInfos extends MsgFile {
	
	// Mandatory nullary constructor
	public MsgFileInfos() {
		this(null);
	}
	
	public MsgFileInfos(String file) {
		super(file);
	}
	
}
