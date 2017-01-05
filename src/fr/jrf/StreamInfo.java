package fr.jrf;

import fr.jrf.client.JRFClient;
import fr.jrf.server.JRFProvider;

public class StreamInfo {
	
	/** The remote absolute path to the file. */
	public final String remoteFile;
	
	public final short fileID;
	
	/** The client used to transfer commands to its connected {@link JRFProvider}. */
	JRFClient cli;
	
	/** Number of bytes transferred from/to the disk (read or written).
	 * @see #bytesXfer */
	long bytesIO;
	
	/** Number of bytes transferred on the network (sent or received). Average compression
	 * is computed as {@code bytesXfer / bytesIO}.
	 * @see #bytesIO */
	long bytesXfer;
	
	/** <p>Time spent waiting for byte transfer.</p>
	 * <p>Network bandwidth (including latency) is {@code 1000 * bytesXfer / msXfer}<br/>
	 *    Data throughput is {@code 1000 * bytesIO / msXfer}</p> */
	long msXfer;
	
	StreamInfo(String remoteFile, short fileID, JRFClient cli) {
		this.remoteFile = remoteFile;
		this.fileID = fileID;
		this.cli = cli;
		bytesIO = 0l;
		bytesXfer = 0l;
		msXfer = 0;
	}
	
	@Override
	public String toString() {
		return remoteFile+"["+fileID+"]";
	}
	
	public long getIOBytes() {
		return bytesIO;
	}
	
	public long getXferBytes() {
		return bytesXfer;
	}
	
	public double getAverageDeflateLevel() {
		return (double)bytesXfer / bytesIO;
	}
	
}
