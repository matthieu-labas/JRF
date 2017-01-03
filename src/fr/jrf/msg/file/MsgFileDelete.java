package fr.jrf.msg.file;

/**
 * Mapping on the {@link java.io.File#delete()} command.
 * @author Matthieu Labas
 */
public class MsgFileDelete extends MsgFile {
	
	// Mandatory nullary constructor
	public MsgFileDelete() {
		this(null);
	}
	
	public MsgFileDelete(String file) {
		super(file);
	}
	
}
