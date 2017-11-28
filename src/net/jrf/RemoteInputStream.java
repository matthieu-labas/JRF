package net.jrf;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Inflater;

import net.jrf.client.JRFClient;
import net.jrf.msg.Message;
import net.jrf.msg.MsgAck;
import net.jrf.msg.MsgClose;
import net.jrf.msg.MsgData;
import net.jrf.msg.MsgISAction;
import net.jrf.msg.MsgRead;
import net.jrf.msg.MsgISAction.StreamAction;
import net.jrf.server.JRFProvider;

/**
 * An {@link InputStream} on a file served by an instance of {@link JRFProvider}.
 * 
 * @author Matthieu Labas
 */
// TODO: Protocol handler? Change package to java.protocol.handler.pkgs.jrf. See http://stackoverflow.com/a/26409796/1098603
public class RemoteInputStream extends InputStream {
	
	/** The inflater to inflate data, when compression is used. {@code null} otherwise. */
	private Inflater infl;
	
	/** Stream information. */
	private StreamInfo info;
	
	/** Exception that might have been thrown and caught by overriden methods that are not supposed
	 * to throw exceptions (e.g. {@link #mark(int)}, {@link #reset()}). */
	private Exception ex;
	
	public RemoteInputStream(JRFClient cli, String remoteFile, short fileID) {
		info = new StreamInfo(cli, remoteFile, fileID);
		ex = null;
	}
	
	public int getFileID() {
		return info.fileID;
	}
	
	public StreamInfo getInfo() {
		return info;
	}
	
	/**
	 * @return The last exception that occurred (usually an {@link IOException} on methods returning a boolean),
	 * 		or {@code null} if none.
	 */
	public Exception getLastException() {
		return ex;
	}
	
	/**
	 * Method called internally when a message is spontaneously sent by the remote side (e.g. hardware failure,
	 * descriptor closed).
	 * @param msg The message sent.
	 * @throws IOException if an I/O error occurs.
	 */
	public void spontaneousMessage(Message msg) throws IOException {
		// TODO: Handle spontaneous close, etc.
	}
	
	@Override
	protected void finalize() throws Throwable {
		close();
		super.finalize();
	}
	
	@Override
	public void close() throws IOException {
		if (info.cli == null)
			return;
		
		if (infl != null) {
			infl.end();
			infl = null;
		}
		try {
			info.cli.send(new MsgClose(info.fileID));
		} finally { // Do that even when IOException occurs
			info.cli.remoteStreamClosed(this);
			info.cli = null;
		}
	}
	
	@Override
	public int read() throws IOException {
		return read(new byte[1], 0, 1);
	}
	
	@Override
    public int read(byte b[], int off, int len) throws IOException {
		JRFClient cli = info.cli;
		if (cli == null)
			throw new IOException("Closed");
		
		if (len == 0)
			return 0;
		// No latency computing for read messages because the received size can be too big and bandwidth would further polute the measurement
		long t0 = System.currentTimeMillis();
		short num = cli.send(new MsgRead(info.fileID, len));
		Message msg = cli.getReply(num, 0);
		info.msXfer += System.currentTimeMillis() - t0;
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
			if (infl == null)
				infl = new Inflater();
			data = Utils.inflate(data, 0, l, infl);
			l = data.length;
		}
		info.bytesIO += l;
		System.arraycopy(data, 0, b, off, l);
		return (l == 0 ? -1 : l);
	}
	
	private long sendAction(StreamAction action, short fileID, long val) throws IOException {
		JRFClient cli = info.cli;
		short num = cli.send(new MsgISAction(action, fileID, val));
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
    public long skip(final long len) throws IOException {
		if (info.cli == null)
			throw new IOException("Closed");
		if (len == 0)
			return 0;
		return sendAction(StreamAction.SKIP, info.fileID, len);
    }
	
	@Override
    public int available() throws IOException {
		if (info.cli == null)
			throw new IOException("Closed");
		return (int)sendAction(StreamAction.AVAILABLE, info.fileID, -1l);
	}
	
	@Override
    public boolean markSupported() {
		if (info.cli == null)
			return false;
		try {
			boolean ret = (sendAction(StreamAction.MARK_SUPPORTED, info.fileID, -1l) != 0l);
			ex = null;
			return ret;
		} catch (IOException e) {
			ex = e;
			return false;
		}
	}
	
	@Override
	public void mark(int readLimit) {
		if (info.cli == null)
			return;
		try {
			sendAction(StreamAction.MARK, info.fileID, readLimit);
			ex = null;
		} catch (IOException e) {
			ex = e;
		}
	}
	
	@Override
	public void reset() {
		if (info.cli == null)
			return;
		try {
			sendAction(StreamAction.RESET, info.fileID, -1l);
			ex = null;
		} catch (IOException e) {
			ex = e;
		}
	}
	
	@Override
	public String toString() {
		return "<"+info;
	}
	
}
