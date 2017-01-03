package fr.jrf;

import java.io.IOException;
import java.io.OutputStream;

import fr.jrf.client.JRFClient;
import fr.jrf.msg.Message;
import fr.jrf.msg.MsgAck;
import fr.jrf.msg.MsgClose;
import fr.jrf.msg.MsgWrite;
import fr.jrf.server.JRFProvider;

public class RemoteOutputStream extends OutputStream {
	
	/** The remote absolute path to the file. */
	private String remoteFile;
	
	private short fileID;
	
	/** The client used to transfer commands to its connected {@link JRFProvider}. */
	private JRFClient cli;
	
	public RemoteOutputStream(String remoteFile, short fileID, JRFClient cli) { // TODO: Deflate
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
		cli.remoteStreamClosed(this);
		cli = null;
	}
	
	@Override
	public void write(int b) throws IOException {
		write(new byte[]{ (byte)(b & 0xff) }, 0, 1);
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if (cli == null)
			throw new IOException("Closed");
		
		if (len == 0)
			return;
		// No latency computing for write messages because the size can be too big and bandwidth would further polute the measurement
		short num = cli.send(new MsgWrite(fileID, b, off, len));
		Message msg = cli.getReply(num, 0);
		if (!(msg instanceof MsgAck)) // Unexpected message
			throw new IOException("Unexpected message "+msg+" ("+MsgAck.class+" was expected)");
		
		MsgAck m = (MsgAck)msg;
		if (m.getCode() != MsgAck.OK) {
			close();
			throw new IOException(m.getMessage());
		}
	}
	
	@Override
    public void flush() throws IOException {
    	// TODO
    }
	
	@Override
	public String toString() {
		return ">"+remoteFile+"["+fileID+"]";
	}
	
}
