package fr.jrf.msg.file;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

import fr.jrf.ByteBufferOut;

/**
 * <p>Mapping on {@code java.io.File.get*Space()} methods returning a single long.</p>
 * <p><ul>
 * <li>{@link File#getFreeSpace()}</li>
 * <li>{@link File#getTotalSpace()}</li>
 * <li>{@link File#getUsableSpace()}</li>
 * </ul></p>
 * @author Matthieu Labas
 */
public class MsgFileSpace extends MsgFile {
	
	public static enum SpaceInfo {
		LAST_MODIFIED, LENGTH, FREE_SPACE, TOTAL_SPACE, USABLE_SPACE;
	}
	
	
	protected SpaceInfo spaceType;
	
	// Mandatory nullary constructor
	public MsgFileSpace() {
		this(null, SpaceInfo.LAST_MODIFIED);
	}
	
	public MsgFileSpace(String file, SpaceInfo isType) {
		super(file);
		this.spaceType = isType;
	}
	
	public SpaceInfo spaceInfo() {
		return spaceType;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(4+2*file.length());
		bb.writeString(file);
		bb.writeInt(spaceType.ordinal());
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			file = readString(dis);
			spaceType = SpaceInfo.values()[dis.readInt()];
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+":"+file+" "+spaceType;
	}
	
}
