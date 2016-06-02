package fr.jfp;

import java.io.IOException;
import java.io.InputStream;

public class RemoteInputStream extends InputStream {
	
	public static final int INVALID = -1;
	public static final int BUFFER_SIZE = 4096;
	
	private String remoteFile;
	
	private int fileID;
	
	private JFPClient cli;
	
	RemoteInputStream(String remoteFile, int fileID, JFPClient cli) {
		this.remoteFile = remoteFile;
		this.fileID = fileID;
		this.cli = cli;
	}
	
	int getFileID() {
		return fileID;
	}
	
	void spontaneousMessage(Message msg) throws IOException {
		// TODO: Handle spontaneous close, etc.
	}
	
	@Override
	public void close() throws IOException {
		if (cli == null)
			throw new IOException("Already closed");
		
		try {
			cli.send(new MsgClose(fileID));
		} catch (IOException e) {
			throw new IOException("Timeout en fermeture de "+remoteFile, e);
		} finally {
			cli.risClosed(this);
			cli = null;
		}
	}
	
	/**
	 * @deprecated Use {@link #read(byte[], int, int)}, more bandwidth-friendly.
	 */
	@Override
	@Deprecated
	public int read() throws IOException {
		return read(new byte[1], 0, 1);
	}
	
	@Override
    public int read(final byte b[], final int off, final int len) throws IOException {
		if (cli == null)
			throw new IOException("Closed");
		
		if (len == 0)
			return 0;
		int num = cli.send(new MsgRead(fileID, len));
		Message msg = cli.getReply(num, 0);
		if (msg instanceof MsgAck) { // Exception occurred
			MsgAck m = (MsgAck)msg;
			if (m.code == MsgAck.WARN) // File not found remotely (bug?): close the file
				close();
			else {
				throw new IOException(m.msg);
			}
		}
		if (!(msg instanceof MsgData)) // Unexpected message
			throw new IOException("Unexpected message "+msg+" ("+MsgData.class+" was expected)");
		
		MsgData m = (MsgData)msg;
		System.arraycopy(m.data, 0, b, off, m.len);
		return (m.len == 0 ? -1 : m.len);
	}
	
	@Override
    public synchronized long skip(final long len) throws IOException {
		if (cli == null)
			throw new IOException("Closed");
		
		if (len == 0)
			return 0;
		int num = cli.send(new MsgSkip(fileID, len));
		Message msg = cli.getReply(num, 0);
		if (!(msg instanceof MsgAck))
			throw new IOException("Unexpected message "+msg+" ("+MsgAck.class+" was expected)");
		MsgAck m = (MsgAck)msg;
		if (m.msg != null) { // Exception
			if (m.code == MsgAck.WARN) // File not found remotely (bug?): close the file
				close();
			throw new IOException(m.msg);
		}
		return m.code;
    }
	
	@Override
	public String toString() {
		return remoteFile+"["+fileID+"]";
	}
    
}
