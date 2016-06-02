package fr.jfp;

import java.io.File;
import java.io.IOException;
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
	
	private static final Map<Integer,JFPServer> instances = new HashMap<>(4);
	
	public static JFPServer get(int port) throws IOException {
		Integer p = Integer.valueOf(port);
		synchronized (instances) {
			JFPServer fp = instances.get(p);
			if (fp == null) {
				fp = new JFPServer(port);
				instances.put(p, fp);
			}
			return fp;
		}
	}
	
	private ServerSocket srv;
	
	private boolean goOn;
	
	private List<JFPProvider> clients;
	
	private JFPServer(int port) throws IOException {
		srv = new ServerSocket(port);
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
	
	/**
	 * @return The {@link File#listRoots() filesystem roots} of the Server.
	 */
	public RemoteFile[] listRoots() {
		File[] roots = File.listRoots();
		if (roots == null)
			return null;
		RemoteFile[] rroots = new RemoteFile[roots.length];
		for (int i = 0; i < roots.length; i++)
			rroots[i] = new RemoteFile(this, roots[i].getAbsolutePath());
		return rroots;
	}
	
	
	public static void main(String[] args) throws NumberFormatException, IOException {
		JFPServer.get(Integer.parseInt(args[0])).start(); // TODO
	}
	
}
