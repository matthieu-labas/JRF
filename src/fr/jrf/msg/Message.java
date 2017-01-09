package fr.jrf.msg;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import fr.jrf.ByteBufferOut;

/**
 * <p>Abstract class handling sending of messages.</p>
 * 
 * <p><strong>ALL SUB CLASSES <em>MUST</em> IMPLEMENT A NULLARY CONSTRUCTOR</strong>, as it will be
 * used for intanciation through {@link Class#newInstance()}.</p>
 * 
 * <p>On the wire, the Message is transmitted as follow:
 * <table border="1" style="border-collapse:collapse">
 * <tr bgcolor="silver"><th><strong>Field</strong></th><th><strong>Size</strong></th><th></th></tr>
 * <tr><td><strong>Marker</strong></td><td>4</td><td>{@code "_JRF"}</td></tr>
 * <tr><td><strong>Message number</strong></td><td>2</td><td>Starts at {@code 1}</td></tr>
 * <tr><td><strong>Reply to</strong></td><td>2</td><td>Message number to which this message replies</td></tr>
 * <tr><td><strong>Type length</strong></td><td>2</td><td></td></tr>
 * <tr><td><strong>Type content</strong></td><td><em>&lt;<code>type length</code>&gt;</em></td><td>(class name in {@link #PACKAGE})</td></tr>
 * <tr><td><strong>Body length</strong></td><td>4</td><td></td></tr>
 * <tr><td><strong>Body content</strong></td><td><em>&lt;<code>body length</code>&gt;</em></td><td></td></tr>
 * </table>
 * </p>
 * 
 * @author Matthieu Labas
 */
public abstract class Message {
	
	private static final Logger log = Logger.getLogger(Message.class.getName());
	
	/** Charset to encode {@code String}s ({@code UTF-8}). */
	public static final Charset charset = Charset.forName("UTF-8");
	
	public static final byte[] MARKER = "_JRF".getBytes(charset);
	
	/** Message counter. */
	private static AtomicInteger numCounter = new AtomicInteger();
	
	/** Message number. */
	protected short num;
	
	/** Message number to which this message replies. */
	protected short replyTo;
	
	protected Message(short replyTo) {
		this.replyTo = replyTo;
		num = (short)(numCounter.incrementAndGet() & 0xffff);
	}
	
	protected Message() {
		this((short)-1);
	}
	
	public short getNum() {
		return num;
	}
	
	public short getReplyTo() {
		return replyTo;
	}
	
	private static String debug(byte[] buf, int len) {
		StringBuffer sb = new StringBuffer(3*len);
		for (int i = 0; i < len; i++) {
			byte b = buf[i];
			if (b >= 0x20 && b <= 0x7e)
				sb.append((char)b);
			else
				sb.append(Integer.toHexString(b & 0xff)).append(' ');
		}
		return sb.toString();
	}
	
	/**
	 * Utility method to read a {@code String} from a {@code DataInput}, as encoded by {@link ByteBufferOut#writeString(String)}.
	 * @param data The {@code DataInput} from which to read the {@code String}.
	 * @return The {@code String}.
	 * @throws IOException from reading {@code data}.
	 */
	public static String readString(DataInput data) throws IOException {
		int sz = data.readShort();
		if (sz < 0)
			return null;
		if (sz == 0)
			return "";
		byte[] buf = new byte[sz];
		data.readFully(buf);
		return new String(buf, charset);
	}
	
	/**
	 * Serializes the message content as a {@link ByteBufferOut} for convenient use of {@link DataOutput}
	 * methods.
	 * @return A byte buffer initialized with the data.
	 * @throws IOException 
	 */
	protected abstract ByteBufferOut encode() throws IOException;
	
	/**
	 * Deserializes the message from the given buffer. Can be wrapped on top of {@link DataInputStream}
	 * wrapping {@link ByteArrayInputStream} to use the {@link DataInput} methods:
	 * <pre>
	 * try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf))) {
	 * 	int sz = dis.readInt();
	 * 	...
	 * } catch (...) {
	 * 	...
	 * } finally {
	 * 	...
	 * }
	 * </pre>
	 * @param buf The buffer containing the whole body, as serialized by {@link #encode()}.
	 * @throws IOException 
	 */
	protected abstract void decode(byte[] buf) throws IOException;
	
	// Force sub-classes to implement a proper toString()
	public abstract String toString();
	
	/**
	 * @return A "standard" description for the message, that can be used as a prefix for sub-classes.
	 */
	protected String stdToString() {
		return getClass().getSimpleName()+"/"+num+(replyTo <= 0 ? "" : ">"+replyTo);
	}
	
	/**
	 * Compute the network header overhead for a given {@link Message} subclass. Useful when precise MTU
	 * control is needed.
	 * @param cls The {@code Message} subclass.
	 * @return The number of bytes overhead.
	 */
	public static int getHeaderSize(Class<? extends Message> cls) {
		return MARKER.length + 2+2 + 2+cls.getName().getBytes(charset).length + 4;
	}
	
	/**
	 * Send the Message on the {@code SocketChannel}.
	 * @param sok The channel to send the Message.
	 * @return The message number.
	 * @throws IOException
	 */
	public synchronized short send(Socket sok) throws IOException {
		ByteBufferOut bb = encode();
		int szEnc = bb.size();
		String cls = getClass().getName();
		int sz = MARKER.length+10+cls.length()+szEnc;
		try (ByteBufferOut data = new ByteBufferOut(sz)) {
			data.write(MARKER); // Marker
			data.writeShort(num); // Message number
			data.writeShort(replyTo); // Reply to
			data.writeString(cls); // Type
			data.writeInt(szEnc); // Body size
			data.write(bb.getRawArray(), 0, szEnc); // Body
			byte[] buf = data.getRawArray();
			int len = data.size();
			log.fine(Thread.currentThread().getName()+" sending message "+this+" ("+szEnc+" body bytes)");
			log.finest(Thread.currentThread().getName()+"\t"+debug(buf, len));
			sok.getOutputStream().write(buf, 0, len);
			return num;
		} finally {
			bb.close();
		}
	}
	
	/**
	 * Receive and decode a message by reading a {@code Socket}. The appropriate {@code Message}
	 * subclass is instanciated by reflection, using the nullary constructor of the decoded class
	 * name. If the class cannot be found in the classpath or no nullary constructor exists, an
	 * {@code IOException} is thrown.
	 * @param sok The socket to read from.
	 * @return The decoded message.
	 * @throws IOException when reading from the socket, or when the {@code Message} subclass could
	 * 		not be instanciated, or when the decoding could not be performed.
	 */
	public static Message receive(Socket sok) throws IOException {
		DataInputStream dis = new DataInputStream(sok.getInputStream()); // Do NOT close this DataInputStream, as it will cascade-close the socket InputStream, cascade-closing the socket itself!
		byte[] mrk = new byte[MARKER.length];
		dis.readFully(mrk);
		if (!Arrays.equals(mrk, MARKER))
			throw new IOException("Bad marker "+new String(mrk, charset));
		mrk = null;
		
		short num = dis.readShort();
		short replyTo = dis.readShort();
		String clsName = readString(dis);
		Message msg;
		Class<?> cls;
		try {
			cls = Class.forName(clsName);
		} catch (ClassNotFoundException e) {
			throw new IOException("Class not found "+clsName+": "+e.getMessage(), e);
		}
		try {
			synchronized (Message.class) {
				numCounter.decrementAndGet(); // The nullary constructor will pre-increment numCounter, so to prevent jumps in message numbers we have to counter it ;)
				msg = (Message)cls.newInstance();
			}
		} catch (InstantiationException | IllegalAccessException e) {
			throw new IOException("Unable to instanciate message of class "+cls+" does it define a nullary constructor?: "+e.getMessage(), e);
		}
		msg.num = num;
		msg.replyTo = replyTo;
		byte[] buf = new byte[dis.readInt()]; // Allocate body size
		dis.readFully(buf);
		msg.decode(buf);
		log.fine(Thread.currentThread().getName()+" received message "+msg+" ("+buf.length+" body bytes)");
		return msg;
	}
	
}
