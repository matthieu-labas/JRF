package fr.jrf;

import java.io.IOException;
import java.io.OutputStream;

import fr.jrf.client.JRFClient;
import fr.jrf.msg.Message;
import fr.jrf.msg.MsgAck;
import fr.jrf.msg.MsgClose;
import fr.jrf.msg.MsgData;
import fr.jrf.msg.MsgWrite;

public class RemoteOutputStream extends OutputStream {
	
	private int deflate;
	
	/** Stream statistics. */
	private StreamInfo info;
	
	public RemoteOutputStream(String remoteFile, short fileID, int deflate, JRFClient cli) {
		info = new StreamInfo(remoteFile, fileID, cli);
		this.deflate = deflate;
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
		int defl = deflate;
		if (defl > 0) {
			byte[] bd = MsgData.deflate(b, off, len, deflate);
			if (bd.length < len) { // Only apply deflate if it's worth it
				b = bd;
				len = b.length;
			} else
				defl = 0;
		}
		info.bytesXfer += len;
		long t0 = System.currentTimeMillis();
		short num = cli.send(new MsgWrite(info.fileID, b, off, len, defl));
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
    	// TODO
    }
	
	@Override
	public String toString() {
		return ">"+info;
	}
	
}
