package net.jrf.msg.file;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

import net.jrf.ByteBufferOut;
import net.jrf.Utils;
import net.jrf.msg.Message;

/**
 * Class to map a remote {@code java.io.File} unitary operation (e.g. {@code delete}, {@code mkdir(s)}, ...).
 * Operations requiring parameters (e.g. {@code renameTo}, {@code setLastModified}, ...) should sub-class it,
 * add new fields to store the attribute(s) and override {@link #encode()} and {@link #decode(DataInputStream)}
 * to encode/decode them (calling the appropriate {@code super.} before).
 * 
 * @author Matthieu Labas
 */
public class MsgFileAction extends Message {
	
	protected String file;
	
	protected FileAction action;
	
	// Mandatory nullary constructor
	public MsgFileAction() {
		this(null, null);
	}
	
	public MsgFileAction(FileAction action, String file) {
		super();
		this.action = action;
		this.file = file;
	}
	
	public File getFile() {
		return new File(file);
	}
	
	public FileAction getAction() {
		return action;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut((file == null ? 4 : 4+2*file.length()));
		bb.writeString(file);
		bb.writeByte(action.ordinal());
		return bb;
	}
	
	protected void decode(DataInputStream dis) throws IOException {
		file = Utils.readString(dis);
		action = FileAction.values()[dis.readByte()];
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			decode(dis);
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+":"+file;
	}
	
	
	
	public static enum FileAction {
		
		/** Reads file length, last modified date and RWX attributes. */
		GET_ATTRIBUTES,
		
		LIST_FILES,
		LIST_ROOTS,
		
		CREATE_NEW,
		DELETE,
		MKDIR,
		MKDIRS,
		
		RENAME,
		
		SET_LAST_MODIFIED,
		SET_READ,
		SET_WRITE,
		SET_EXECUTE,
		SET_READONLY,
		
		FREE_SPACE,
		TOTAL_SPACE,
		USABLE_SPACE,
		;
	}
	
}
