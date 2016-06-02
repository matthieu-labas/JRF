package fr.jfp;

import java.io.IOException;

/**
 * <p>Interface to implement to process an "answer" message after a command has been issued on a
 * specific {@link RemoteInputStream}.</p>
 * <p><pre>
 * cli.send(this, new MsgXXX(fileID), new FileMessageProcessor() {
 *     &#64;Override
 *     public void process(Message msg) throws IOException {
 *         if (!(msg instanceof MsgAck)
 *             throw new IOException("Unexpected message "+msg+" ("+MsgAck.class+" was expected)");
 *         MsgAck m = (MsgAck)msg;
 *         ...
 *     }
 * });
 * </pre></p>
 * 
 * @author Matthieu Labas
 */
interface FileMessageProcessor {
	
	/**
	 * Process the answer message from the server after a command message was issued to it.
	 * @param msg
	 * @throws IOException
	 */
	public void process(Message msg) throws IOException;
	
}