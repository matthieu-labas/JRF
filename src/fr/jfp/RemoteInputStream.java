package fr.jfp;

import java.io.IOException;
import java.io.InputStream;

import fr.jfp.client.JFPClient;
import fr.jfp.msg.Message;
import fr.jfp.msg.MsgAck;
import fr.jfp.msg.MsgClose;
import fr.jfp.msg.MsgData;
import fr.jfp.msg.MsgRead;
import fr.jfp.msg.MsgSkip;
import fr.jfp.server.JFPProvider;

/**
 * An {@link InputStream} on a file served by an instance of {@link JFPProvider}.
 * 
 * @author Matthieu Labas
 */
// TODO: Protocol handler? Change package to java.protocol.handler.pkgs.jfp. See http://stackoverflow.com/a/26409796/1098603
public class RemoteInputStream extends InputStream {
	
	/** The remote absolute path to the file. */
	private String remoteFile;
	
	private short fileID;
	
	/** The client used to transfer commands to its connected {@link JFPProvider}. */
	private JFPClient cli;
	
	public RemoteInputStream(String remoteFile, short fileID, JFPClient cli) {
		this.remoteFile = remoteFile;
		this.fileID = fileID;
		this.cli = cli;
	}
	
	public int getFileID() {
		return fileID;
	}
	
	public void spontaneousMessage(Message msg) throws IOException {
		// TODO: Handle spontaneous close, etc.
	}
	
	@Override
	public void close() throws IOException {
		if (cli == null)
			return;
		
		cli.send(new MsgClose(fileID));
		cli.risClosed(this);
		cli = null;
	}
	
	/**
	 * @deprecated Use more bandwidth-friendly {@link #read(byte[], int, int)}.
	 */
	@Override
	@Deprecated
	public int read() throws IOException {
		return read(new byte[1], 0, 1);
	}
	
	@Override
    public int read(final byte b[], final int off, final int len) throws IOException { // TODO: Use UDP to get data back?
		if (cli == null)
			throw new IOException("Closed");
		
		if (len == 0)
			return 0;
		// No latency computing for read messages because the received size can be too big and bandwidth would further pollute the measurement
		int num = cli.send(new MsgRead(fileID, len));
		Message msg = cli.getReply(num, 0);
		if (msg instanceof MsgAck) { // Exception occurred
			MsgAck m = (MsgAck)msg;
			if (m.getCode() == MsgAck.WARN) // File not found remotely (bug?): close the file
				close();
			else {
				throw new IOException(m.getMessage());
			}
		}
		if (!(msg instanceof MsgData)) // Unexpected message
			throw new IOException("Unexpected message "+msg+" ("+MsgData.class+" was expected)");
		
		MsgData m = (MsgData)msg;
		int l = m.getLength();
		System.arraycopy(m.getData(), 0, b, off, l);
		return (l == 0 ? -1 : l);
	}
	
	@Override
    public synchronized long skip(final long len) throws IOException {
		if (cli == null)
			throw new IOException("Closed");
		
		if (len == 0)
			return 0;
		long t0 = System.nanoTime();
		int num = cli.send(new MsgSkip(fileID, len));
		Message msg = cli.getReply(num, 0);
		cli.addLatencyNow(t0);
		if (!(msg instanceof MsgAck))
			throw new IOException("Unexpected message "+msg+" ("+MsgAck.class+" was expected)");
		MsgAck m = (MsgAck)msg;
		String err = m.getMessage();
		if (err != null) { // Exception
			if (m.getCode() == MsgAck.WARN) // File not found remotely (bug?): close the file
				close();
			throw new IOException(err);
		}
		return m.getCode();
    }
	
	@Override
	public String toString() {
		return remoteFile+"["+fileID+"]";
	}
    
}
