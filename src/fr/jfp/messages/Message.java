package fr.jfp.messages;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.logging.Logger;

import fr.jfp.ByteBufferOut;

/**
 * <p>Abstract class handling sending of messages.</p>
 * 
 * <p><strong>ALL SUB CLASSES <em>MUST</em> IMPLEMENT A NULLARY CONSTRUCTOR</strong>, as it will be
 * used for intanciation through {@link Class#newInstance()}.</p>
 * 
 * <p>On the wire, the Message is transmitted as follow:
 * <table border="1" style="border-collapse:collapse">
 * <tr bgcolor="silver"><th><strong>Field</strong></th><th><strong>Size</strong></th><th></th></tr>
 * <tr><td><strong>Marker</strong></td><td>4</td><td>{@code "_JFP"}</td></tr>
 * <tr><td><strong>Message number</strong></td><td>4</td><td>Starts at {@code 1}</td></tr>
 * <tr><td><strong>Reply to</strong></td><td>4</td><td>Message number to which this message replies</td></tr>
 * <tr><td><strong>Type length</strong></td><td>4</td><td></td></tr>
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
	
	/** Default package for Messages. */
	public static final String PACKAGE = Message.class.getPackage().getName();
	
	/** Charset to encode {@code String}s ({@code UTF-8}). */
	public static final Charset charset = Charset.forName("UTF-8");
	
	public static final byte[] MARKER = "_JFP".getBytes(charset);
	
	/** Message counter. Its access should be {@code synchronized} on {@code Message.class}. */
	private static volatile int numCounter = 0;
	
	/** Message number. */
	protected int num;
	
	/** Message number to which this message replies. */
	protected int replyTo;
	
	Message(int replyTo) {
		this.replyTo = replyTo;
		synchronized (Message.class) {
			num = ++numCounter;
		}
	}
	
	Message() {
		this(-1);
	}
	
	public int getNum() {
		return num;
	}
	
	public int getReplyTo() {
		return replyTo;
	}
	
	/**
	 * Send the Message on the {@code SocketChannel}.
	 * @param sok The channel to send the Message.
	 * @throws IOException
	 */
	public void send(Socket sok) throws IOException {
		ByteBufferOut bb = encode();
		String cls = getClass().getSimpleName();
		int sz = MARKER.length+16+cls.length()+bb.size();
		try (ByteBufferOut data = new ByteBufferOut(sz)) {
			data.write(MARKER); // Marker
			data.writeInt(num); // Message number
			data.writeInt(replyTo); // Reply to
			data.writeString(cls); // Type
			data.writeInt(bb.size()); // Body size
			data.write(bb.getRawArray(), 0, bb.size()); // Body
			byte[] buf = data.getRawArray();
			int len = data.size();
			log.info(Thread.currentThread().getName()+" sending message "+this+" ("+bb.size()+" body bytes)");
			log.finest(Thread.currentThread().getName()+"\t"+debug(buf, len));
			sok.getOutputStream().write(buf, 0, len);
		} finally {
			bb.close();
		}
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
	protected static String readString(DataInput data) throws IOException {
		int sz = data.readInt();
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
		
		int num = dis.readInt();
		int replyTo = dis.readInt();
		String clsName = PACKAGE+"."+readString(dis);
		Message msg;
		Class<?> cls;
		try {
			cls = Class.forName(clsName);
		} catch (ClassNotFoundException e) {
			throw new IOException("Class not found "+clsName+": "+e.getMessage(), e);
		}
		try {
			synchronized (Message.class) {
				numCounter--; // The nullary constructor will pre-increment numCounter, so to prevent jumps in message numbers we have to counter it ;)
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
