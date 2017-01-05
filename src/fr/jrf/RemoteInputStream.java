package fr.jrf;

import java.io.IOException;
import java.io.InputStream;

import fr.jrf.client.JRFClient;
import fr.jrf.msg.Message;
import fr.jrf.msg.MsgAck;
import fr.jrf.msg.MsgClose;
import fr.jrf.msg.MsgData;
import fr.jrf.msg.MsgRead;
import fr.jrf.msg.MsgSkip;
import fr.jrf.server.JRFProvider;

/**
 * An {@link InputStream} on a file served by an instance of {@link JRFProvider}.
 * 
 * @author Matthieu Labas
 */
// TODO: Protocol handler? Change package to java.protocol.handler.pkgs.jrf. See http://stackoverflow.com/a/26409796/1098603
public class RemoteInputStream extends InputStream {
	
	/** Stream information. */
	private StreamInfo info;
	
	public RemoteInputStream(String remoteFile, short fileID, JRFClient cli) {
		info = new StreamInfo(remoteFile, fileID, cli);
	}
	
	public int getFileID() {
		return info.fileID;
	}
	
	public StreamInfo getInfo() {
		return info;
	}
	
	public void spontaneousMessage(Message msg) throws IOException {
		// TODO: Handle spontaneous close, etc.
	}
	
	@Override
	public void close() throws IOException {
		if (info.cli == null)
			return;
		
		info.cli.send(new MsgClose(info.fileID));
		info.cli.remoteStreamClosed(this);
		info.cli = null;
	}
	
	@Override
	public int read() throws IOException {
		return read(new byte[1], 0, 1);
	}
	
	@Override
    public int read(final byte b[], final int off, final int len) throws IOException {
		JRFClient cli = info.cli;
		if (cli == null)
			throw new IOException("Closed");
		
		if (len == 0)
			return 0;
		// No latency computing for read messages because the received size can be too big and bandwidth would further polute the measurement
		long t0 = System.currentTimeMillis();
		short num = cli.send(new MsgRead(info.fileID, len));
		info.msXfer += System.currentTimeMillis() - t0;
		Message msg = cli.getReply(num, 0);
		if (msg instanceof MsgAck) { // Exception occurred
			MsgAck m = (MsgAck)msg;
			close();
			if (m.getCode() == MsgAck.ERR) // File not found remotely (bug?): close the file
				throw new IOException(m.getMessage());
		}
		if (!(msg instanceof MsgData)) // Unexpected message
			throw new IOException("Unexpected message "+msg+" ("+MsgData.class+" was expected)");
		
		MsgData m = (MsgData)msg;
		byte[] data = m.getData();
		int l = m.getLength();
		info.bytesXfer += l;
		if (m.getDeflate() > 0) {
			data = MsgData.inflate(data, l);
			l = data.length;
		}
		info.bytesIO += l;
		System.arraycopy(data, 0, b, off, l);
		return (l == 0 ? -1 : l);
	}
	
	@Override
    public synchronized long skip(final long len) throws IOException {
		JRFClient cli = info.cli;
		if (cli == null)
			throw new IOException("Closed");
		
		if (len == 0)
			return 0;
		short num = cli.send(new MsgSkip(info.fileID, len));
		long t0 = System.nanoTime();
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
		return "<"+info;
	}
	
}
