package fr.jrf;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;

import fr.jrf.client.JRFClient;
import fr.jrf.msg.Message;
import fr.jrf.msg.MsgAck;
import fr.jrf.msg.MsgClose;
import fr.jrf.msg.MsgWrite;

public class RemoteOutputStream extends OutputStream {
	
	private int deflateLevel;
	private Deflater defl;
	
	/** Stream statistics. */
	private StreamInfo info;
	
	public RemoteOutputStream(String remoteFile, short fileID, int deflate, JRFClient cli) {
		info = new StreamInfo(remoteFile, fileID, cli);
		if (deflate > 0)
			this.defl = new Deflater(deflate);
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
	protected void finalize() throws Throwable {
		close();
		super.finalize();
	}
	
	@Override
	public void close() throws IOException {
		if (info.cli == null)
			return;
		
		if (defl != null) {
			defl.end();
			defl = null;
		}
		try {
			info.cli.send(new MsgClose(info.fileID));
		} finally { // Do that even when IOException occurs
			info.cli.remoteStreamClosed(this);
			info.cli = null;
		}
	}
	
	@Override
	public void write(int b) throws IOException {
		write(new byte[]{ (byte)(b & 0xff) }, 0, 1);
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		JRFClient cli = info.cli;
		if (cli == null)
			throw new IOException("Closed");
		
		if (len == 0)
			return;
		info.bytesIO += len;
		int level = deflateLevel;
		if (defl != null) {
			byte[] bd = Utils.deflate(b, off, len, defl);
			if (bd.length < len) { // Only apply deflate if it's worth it
				b = bd;
				len = b.length;
			} else
				level = 0;
		}
		info.bytesXfer += len;
		long t0 = System.currentTimeMillis();
		short num = cli.send(new MsgWrite(info.fileID, b, off, len, level));
		info.msXfer += System.currentTimeMillis() - t0;
		t0 = System.nanoTime();
		Message msg = cli.getReply(num, 0);
		cli.addLatencyNow(t0);
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
		JRFClient cli = info.cli;
		short num = cli.send(new MsgClose(info.fileID));
		long t0 = System.nanoTime();
		Message msg = cli.getReply(num, 0);
		cli.addLatencyNow(t0);
		if (!(msg instanceof MsgAck)) // Unexpected message
			throw new IOException("Unexpected message "+msg+" ("+MsgAck.class+" was expected)");
		
		MsgAck m = (MsgAck)msg;
		if (m.getCode() != MsgAck.OK) {
			close();
			throw new IOException(m.getMessage());
		}
    }
	
	@Override
	public String toString() {
		return ">"+info;
	}
	
}
