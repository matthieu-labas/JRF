package fr.jfp.msg.file;

/**
 * Mapping on the {@link java.io.File#listRoots()} command.
 * @author Matthieu Labas
 */
public class MsgFileRoots extends MsgFile {
	
	// Mandatory nullary constructor
	public MsgFileRoots() {
		this(null);
	}
	
	public MsgFileRoots(String file) {
		super(file);
	}
	
}
