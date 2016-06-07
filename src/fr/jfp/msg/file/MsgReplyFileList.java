package fr.jfp.msg.file;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

import fr.jfp.ByteBufferOut;
import fr.jfp.FileInfos;
import fr.jfp.msg.Message;

/**
 * <p>Message received to acknowledge a command or report an error during execution of the last
 * command.</p>
 * 
 * @author Matthieu Labas
 */
public class MsgReplyFileList extends Message {
	
	/** The list of file names. */
	protected FileInfos[] infos;
	
	/** Indicate this is the last message. */
	protected boolean last;
	
	// Mandatory no-arg constructor
	public MsgReplyFileList() {
		this((short)-1, null, false);
	}
	
	public MsgReplyFileList(short replyTo, File[] files, boolean last) {
		super(replyTo);
		setFiles(files);
		this.last = last;
	}
	
	public boolean isLast() {
		return last;
	}
	
	public FileInfos[] getFiles() {
		return infos;
	}
	
	public void setFiles(File[] files) {
		if (files == null) {
			infos = null;
			return;
		}
		infos = new FileInfos[files.length];
		for (int i = 0; i < files.length; i++)
			infos[i] = new FileInfos(files[i]);
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		int n = 5;
		for (FileInfos f : infos)
			n += f.guessEncodedSize();
		ByteBufferOut bb = new ByteBufferOut(n);
		bb.writeByte(last ? 0 : 1);
		bb.writeInt(infos.length);
		for (FileInfos f : infos)
			f.encodeAppend(bb);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			last = (dis.readByte() != 0);
			infos = new FileInfos[dis.readInt()];
			for (int i = 0; i < infos.length; i++)
				infos[i] = new FileInfos(dis);
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+(infos == null ? " ?" : " "+infos.length)+" files";
	}
	
}
