package fr.jrf.msg.file;

/**
 * Mapping on the {@link java.io.File#mkdirs()} command.
 * @author Matthieu Labas
 */
public class MsgFileMkdirs extends MsgFile {
	
	// Mandatory nullary constructor
	public MsgFileMkdirs() {
		this(null);
	}
	
	public MsgFileMkdirs(String file) {
		super(file);
	}
	
}
