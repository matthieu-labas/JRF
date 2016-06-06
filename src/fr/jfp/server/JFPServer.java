package fr.jfp.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Class listening for connections on a {@code ServerSocket} and creating {@link JFPProvider} instances
 * on new connections.
 * 
 * @author Matthieu Labas
 */
public class JFPServer extends Thread {
	
	private static final Logger log = Logger.getLogger(JFPServer.class.getName());
	
	public static final int DEFAULT_PORT = 2205;
	
	private static final Map<InetSocketAddress,JFPServer> instances = new HashMap<>(4);
	
	public static JFPServer get(InetSocketAddress addr) throws IOException {
		synchronized (instances) {
			JFPServer fp = instances.get(addr);
			if (fp == null) {
				fp = new JFPServer(addr);
				instances.put(addr, fp);
			}
			return fp;
		}
	}
	
	private ServerSocket srv;
	
	private boolean goOn;
	
	private List<JFPProvider> clients;
	
	private JFPServer(InetSocketAddress addr) throws IOException {
		srv = new ServerSocket();
		srv.bind(addr);
		clients = new ArrayList<>();
		setName(JFPServer.class.getSimpleName()+" on *:"+srv.getLocalPort());
		goOn = true;
	}
	
	public void requestStop() {
		goOn = false;
		try {
			srv.close();
		} catch (IOException e) {
			log.warning("Exception while closing Server in an attemp to stop it: "+e.getMessage());
		}
	}
	
	@Override
	public void run() {
		while (goOn) {
			try {
				Socket sok = srv.accept();
				JFPProvider cli = new JFPProvider(sok);
				clients.add(cli);
				log.info("Connection from "+sok.getRemoteSocketAddress());
				cli.start();
			} catch (SocketTimeoutException e) {
				continue;
			} catch (IOException e) {
				if (goOn)
					log.warning(e.getMessage());
			}
		}
		log.info("Server closed.");
		
		synchronized (instances) {
			instances.values().remove(this);
		}
	}
	
	
	
	public static void usage() {
		System.out.println("Options: <hostname[:port]>");
		System.out.println("If <port> is not specified, 2205 will be used.");
	}
	
	public static void main(String[] args) throws NumberFormatException, IOException {
		String[] hp = null;
		int port = DEFAULT_PORT;
		
		switch (args.length) {
			case 0: break;
			
			case 1:
				hp = args[0].split(":");
				if (hp.length > 1) {
					try {
						port = Integer.parseInt(hp[1]);
					} catch (NumberFormatException e) {
						System.err.println("Cannot parse port '"+hp[1]+"'");
						System.exit(2);
					}
				}
				break;
			
			default:
				usage();
				System.exit(1);
				break;
		}
		InetSocketAddress addr = hp == null || hp[0].isEmpty() ? new InetSocketAddress(port) : new InetSocketAddress(hp[0], port);
		JFPServer.get(addr).start();
	}
	
}
