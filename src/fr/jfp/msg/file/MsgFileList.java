package fr.jfp.msg.file;

/**
 * Mapping on the {@link java.io.File#list()} command.
 * @author Matthieu Labas
 */
public class MsgFileList extends MsgFile {
	
	// Mandatory nullary constructor
	public MsgFileList() {
		this(null);
	}
	
	public MsgFileList(String file) {
		super(file);
	}
	
}
