package fr.jrf.msg;

import java.io.IOException;

import fr.jrf.ByteBufferOut;

/**
 * Special message to check for network connectivity.
 * 
 * @author Matthieu Labas
 */
public class MsgPing extends Message {
	
	public MsgPing(short replyTo) {
		super(replyTo);
	}
	
	// Mandatory no-arg constructor
	public MsgPing() {
		this((short)-1);
	}
	
	@Override
	protected ByteBufferOut encode() throws IOException {
		return new ByteBufferOut(0);
	}
	
	@Override
	protected void decode(byte[] buf) throws IOException {
	}
	
	@Override
	public String toString() {
		return stdToString();
	}
	
}
