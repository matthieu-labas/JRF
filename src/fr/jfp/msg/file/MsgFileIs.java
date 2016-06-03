package fr.jfp.msg.file;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

import fr.jfp.ByteBufferOut;

/**
 * <p>Mapping on {@code java.io.File.is*} methods returning a single boolean.</p>
 * <p><ul>
 * <li>{@link File#exists()}</li>
 * <li>{@link File#isFile()}</li>
 * <li>{@link File#isDirectory()}</li>
 * </ul></p>
 * @author Matthieu Labas
 */
public class MsgFileIs extends MsgFile {
	
	public static enum IsType {
		IS_EXIST, IS_FILE, IS_DIRECTORY;
	}
	
	
	protected IsType isType;
	
	// Mandatory nullary constructor
	public MsgFileIs() {
		this(null, IsType.IS_EXIST);
	}
	
	public MsgFileIs(String file, IsType isType) {
		super(file);
		this.isType = isType;
	}
	
	public IsType isType() {
		return isType;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(4+2*file.length());
		bb.writeString(file);
		bb.writeInt(isType.ordinal());
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			file = readString(dis);
			isType = IsType.values()[dis.readInt()];
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+":"+file+" "+isType;
	}
	
}
