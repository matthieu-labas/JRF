package net.jrf.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Logger;

/**
 * <p>Class listening for connections on a {@code ServerSocket} and creating {@link JRFProvider} instances
 * on new connections.</p>
 * <p>Several server instances can be created on different ports, if needed. The {@code main()} method
 * will create a single server running on the given @code address:port} and show a small CLI to query
 * connections:
 * <table><tr>2<th>Command</th><th>Description</th></tr>
 * <tr><td><code>?</code></td><td>Query connected clients and their currently opened files</td></tr>
 * <tr><td><code>bye</code></td><td>Shutdown the server and exit</td></tr>
 * </table></p>
 * 
 * @author Matthieu Labas
 */
public class JRFServer extends Thread {
	
	private static final Logger log = Logger.getLogger(JRFServer.class.getName());
	
	/** Default port on which the server is listening, when none is given. */
	public static final int DEFAULT_PORT = 2205;
	
	/** If no activity is detected on a client after this timeout, a ping message is sent to the client
	 * which will close if it doesn't reply. */
	public static final int CLIENT_TIMEOUT = 5 * 60_000;
	
	/** Server instances. The key is the bind address. */
	private static final Map<InetSocketAddress,JRFServer> instances = new HashMap<>(4);
	
	public static JRFServer get(InetSocketAddress addr) throws IOException {
		synchronized (instances) {
			JRFServer fp = instances.get(addr);
			if (fp == null) {
				fp = new JRFServer(addr);
				instances.put(addr, fp);
			}
			return fp;
		}
	}
	
	public static List<JRFServer> getServers() {
		synchronized (instances) {
			return new ArrayList<>(instances.values());
		}
	}
	
	private ServerSocket srv;
	
	private volatile boolean goOn;
	
	private List<JRFProvider> clients;
	
	private JRFServer(InetSocketAddress addr) throws IOException {
		srv = new ServerSocket();
		srv.bind(addr);
		clients = new ArrayList<>();
		setName(JRFServer.class.getSimpleName()+" on *:"+srv.getLocalPort());
		goOn = true;
	}
	
	/**
	 * @return The address the JRF Server is bound to.
	 */
	public InetSocketAddress getAddress() {
		return (InetSocketAddress)srv.getLocalSocketAddress();
	}
	
	/**
	 * @return A copy of the list of JRF Providers currently connected to the JRF Server.
	 */
	public List<JRFProvider> getClients() {
		return new ArrayList<>(clients);
	}
	
	/**
	 * Request the server to stop listening to new connections, and close all connected
	 * clients.
	 */
	public void requestStop() {
		goOn = false;
		try {
			srv.close();
		} catch (IOException e) {
			log.warning("Exception while closing Server in an attemp to stop it: "+e.getMessage());
		}
	}
	
	/**
	 * Method called by {@code prov} when it has been closed.
	 * @param prov The client that finished closing.
	 */
	void providerClosed(JRFProvider prov) {
		if (!goOn) // Closing in progress
			return;
		synchronized (clients) {
			clients.remove(prov);
		}
	}
	
	@Override
	public void run() {
		while (goOn) {
			try {
				Socket sok = srv.accept();
				JRFProvider cli = new JRFProvider(sok, this);
				synchronized (clients) {
					clients.add(cli);
				}
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
		
		synchronized (clients) {
			for (JRFProvider prov : clients)
				prov.requestStop();
			for (JRFProvider prov : clients)
				try{prov.join();}catch(InterruptedException e){}
			clients = null;
		}
		
		synchronized (instances) {
			instances.values().remove(this);
		}
	}
	
	
	
	public static void usage() {
		System.out.println("Options: [hostname[:port]]");
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
		InetSocketAddress addr = (hp == null || hp[0].isEmpty() ? new InetSocketAddress(port) : new InetSocketAddress(hp[0], port));
		final JRFServer srv = JRFServer.get(addr);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override public void run() {
				srv.requestStop();
				System.out.print("Waiting for server to shutdown and close connections ...");
				while (srv.isAlive())
					try{srv.join();}catch(InterruptedException e){}
				System.out.println("done.");
			}
		});
		srv.start();
		
		System.out.println(srv.getName());
		Scanner sc = new Scanner(System.in);
		while (srv.isAlive()) {
			System.out.print("$ ");
			String c = sc.next().toLowerCase();
			switch (c) {
				case "bye":
					System.out.print("\nDo you want to exit (y/N)? ");
					c = sc.next();
					if ("y".equalsIgnoreCase(c)) {
						srv.requestStop();
						while (srv.isAlive())
							try{srv.join();}catch(InterruptedException e){}
					}
					break;
				
				case "?":
					synchronized (srv.clients) {
						if (srv.clients.isEmpty()) {
							System.out.println("No client connected.");
							break;
						}
						System.out.println(srv.clients.size()+" client(s) connected:");
						for (JRFProvider prov : srv.clients) {
							System.out.println(prov.getRemote());
							for (String fi : prov.getOpenedInputFiles())
								System.out.println("    [ in] "+fi);
							for (String fi : prov.getOpenedOutputFiles())
								System.out.println("    [out] "+fi);
						}
					}
					break;
				
				default:
					System.out.println("Unknown command '"+c+"'");
				case "h":
				case "help":
					System.out.println("Commands:");
					System.out.println("bye - Exit");
					System.out.println("?   - Show connected clients and opened files");
					break;
			}
		}
		sc.close();
		System.out.println("Bye.");
	}
	
}
