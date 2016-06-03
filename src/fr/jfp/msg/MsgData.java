package fr.jfp.msg;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import fr.jfp.ByteBufferOut;
import fr.jfp.ByteBufferOut.StraightByteArrayOutputStream;

public class MsgData extends MsgFileCmd {
	
	/** <p>The chunk data.</p>
	 * <p>When the message is <em>sent</em>, the content is the original data when no compression
	 * was requested, or the deflated data when the {@code deflate} parameter is {@code > 0}.<br/>
	 * When the message is <em>received</em>, the content is always the original data (i.e. it is
	 * deflated during deserialization).</p> */
	protected byte[] data;
	
	/** Valid number of bytes in {@link #data}. */
	protected int len;
	
	/** The deflate level {@link #data} was deflated with, or {@code <= 0} if {@link data} is
	 * not deflated. N.B. that the value is not necessary for inflation but is kept for potential
	 * logging. */
	protected int deflate;
	
	// Mandatory no-arg constructor
	public MsgData() {
		super(-1);
	}
	
	/**
	 * Create a new data chunk message for the specified file. If indicated ({@code deflate > 0},
	 * the data will be deflated upon construction (i.e. by the constructor).
	 * @param replyTo The message number asking for data.
	 * @param fileID The file {@code data} belongs to.
	 * @param data The chunk data.
	 * @param deflate If {@code > 0}, {@code data} will be deflated before being stored.
	 */
	public MsgData(int replyTo, int fileID, byte[] data, int len, int deflate) {
		super(replyTo, fileID);
		this.deflate = deflate;
		this.len = len;
		if (deflate > 0)
			this.data = deflate(data, deflate);
		else
			this.data = data;
	}
	
	public byte[] getData() {
		return data;
	}
	
	public int getLength() {
		return len;
	}
	
	public int getDeflate() {
		return deflate;
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		ByteBufferOut bb = new ByteBufferOut(8+len);
		bb.writeInt(fileID);
		bb.writeInt(deflate);
		bb.writeInt(len);
		bb.write(data, 0, len);
		return bb;
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
		len = buf.length - 8; // 8 being the two int 'fileID' and 'deflate'
		try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
			fileID = dis.readInt();
			deflate = dis.readInt();
			len = dis.readInt();
			data = new byte[len];
			dis.readFully(data);
		}
		if (deflate > 0)
			data = inflate(data);
	}
	
	public static byte[] deflate(byte[] source, int level) {
		Deflater defl = new Deflater(level);
		defl.setInput(source);
		defl.finish();
		byte[] buf = new byte[1024];
		try (StraightByteArrayOutputStream bos = new StraightByteArrayOutputStream(1024)) {
			int n;
			while ((n = defl.deflate(buf)) > 0)
				bos.write(buf, 0, n);
			defl.end();
			defl = null;
			return bos.toByteArray();
		} catch (IOException e) { // Never happens with ByteArrayOutputStream
			e.printStackTrace();
			return null;
		}
	}
	
	public static byte[] inflate(byte[] source) throws IOException {
		Inflater infl = new Inflater();
		infl.setInput(source);
		byte[] buf = new byte[1024];
		try (StraightByteArrayOutputStream bos = new StraightByteArrayOutputStream(1024)) {
			int n;
			while ((n = infl.inflate(buf)) > 0)
				bos.write(buf, 0, n);
			return bos.toByteArray();
		} catch (DataFormatException e) {
			throw new IOException("Cannot inflate data: "+e.getMessage(), e);
		} finally {
			infl.end();
			infl = null;
		}
	}
	
	@Override
	public String toString() {
		return stdToString()+" "+len+" bytes on file "+fileID;
	}
	
}
