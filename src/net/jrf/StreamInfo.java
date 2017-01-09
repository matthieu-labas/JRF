package net.jrf;

import net.jrf.client.JRFClient;
import net.jrf.server.JRFProvider;

/**
 * <p>File information, shared by both {@link RemoteInputStream} and {@link RemoteOutputStream}.</p>
 * <p>The object keeps track of bytes read from/written to the file, and transferred over the network,
 * after potential compression, as well as the time spent in network I/O (sending data and waiting for
 * reply, which can be big data send/small reply ack for writes, and small read request/big data received
 * for reads).</p>
 * 
 * @author Matthieu Labas
 */
public class StreamInfo {
	
	/** The remote absolute path to the file. */
	public final String remoteFile;
	
	/** The unique file ID, given by the remote {@link JRFProvider}. */
	public final short fileID;
	
	/** The client used to transfer commands to its connected {@link JRFProvider}. */
	JRFClient cli;
	
	/** Number of bytes transferred from/to the disk (read or written).
	 * @see #bytesXfer */
	long bytesIO; // Package-private to allow direct modification by RemoteInput/OutputStream
	
	/** Number of bytes transferred on the network (sent or received). Average compression
	 * is computed as {@code bytesXfer / bytesIO}.
	 * @see #bytesIO */
	long bytesXfer; // Package-private
	
	/** <p>Time spent waiting for byte transfer.</p>
	 * <p>Network bandwidth (including latency) is {@code 1000 * bytesXfer / msXfer}<br/>
	 *    Data throughput is {@code 1000 * bytesIO / msXfer}</p> */
	long msXfer; // Package-private
	
	StreamInfo(JRFClient cli, String remoteFile, short fileID) {
		this.cli = cli;
		this.remoteFile = remoteFile;
		this.fileID = fileID;
		bytesIO = 0l;
		bytesXfer = 0l;
		msXfer = 0;
	}
	
	@Override
	public String toString() {
		return remoteFile+"["+fileID+"]";
	}
	
	/**
	 * @return The number of bytes read ({@code RemoteInputStream}) / written ({@code RemoteOutputStream}).
	 */
	public long getIOBytes() {
		return bytesIO;
	}
	
	/**
	 * @return The number of bytes sent ({@code RemoteOutputStream}) / received ({@code RemoteInputStream})
	 * 		over the network.
	 */
	public long getXferBytes() {
		return bytesXfer;
	}
	
	/**
	 * @return The average compression level, when activated ({@code 1.0} if not set).
	 */
	public double getAverageDeflateLevel() {
		return (double)bytesXfer / bytesIO;
	}
	
	/**
	 * @return The average network speed, in bytes per seconds.
	 */
	public double getAverageThroughput() {
		return 1000.0 * bytesXfer / msXfer;
	}
	
}
