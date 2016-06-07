package fr.jfp.client;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

import fr.jfp.RemoteFile;

/**
 * Command Line Interface for {@link JFPClient}. Though it implements {@code Runnable}, it is not
 * primarily designed to run in a separate Thread as it is reading from {@code System.in}.
 * 
 * @author Matthieu Labas
 */
public class JFPClientCLI implements Runnable {
	
	public static final String dateFormat = "d MMM YYYY HH:mm:ss";
	
	private JFPClient cli;
	
	private Scanner sc;
	
	private File local;
	private RemoteFile remote;
	
	public JFPClientCLI(JFPClient cli) {
		this.cli = cli;
		local = new File(System.getProperty("user.dir"));
		remote = null;
		sc = null;
	}
	
	/** Stops the CLI */
	public void stop() {
		cli.requestStop();
		if (sc != null)
			sc.close();
		while (cli.isAlive())
			try{cli.join();}catch(InterruptedException e){}
	}
	
	private static String[] splitCommand(String line) {
		// TODO: Split 'line', escaping spaces with quotes and backslash
		return line.split(" ");
	}
	
	/** Starts the CLI. The method blocks until the user requested a stop. Once the method returns,
	 * the {@link JFPClient} passed in the constructor is closed. */
	@Override
	public void run() {
		sc = new Scanner(System.in);
		main: while (cli.isAlive()) {
			System.out.print(local.getAbsolutePath()+" < "+(remote == null ? "(no root)" : remote.getAbsolutePath())+" $ ");
			// Non-blocking read from 'sc'
			try {
				while (System.in.available() == 0) {
					if (!cli.isAlive()) {
						System.out.println("Remote host closed the connection.");
						break main;
					}
					try{Thread.sleep(100);}catch(InterruptedException e){}
				}
			} catch (IOException e) { }
			String c = sc.next();
			String[] cmds = splitCommand(c);
			String arg1; 
			switch (cmds[0].toLowerCase()) {
				case "cd":
					RemoteFile nrem;
					arg1 = (cmds.length > 1 ? cmds[1] : sc.next());
					try {
						if (remote == null)
							nrem = new RemoteFile(cli, arg1);
						else {
							if ("..".equals(arg1)) {
								String parent = remote.getParent();
								if (parent != null) // 'remote' is a root => no change
									nrem = new RemoteFile(cli, parent);
								else
									nrem = remote;
							} else
								nrem = new RemoteFile(remote, arg1);
						}
					} catch (IOException e) {
						System.err.println("I/O error: "+e.getMessage());
						stop();
						continue;
					}
					if (!nrem.isDirectory()) {
						System.out.println("Cannot change remote directory to "+nrem.getName());
						break;
					}
					remote = nrem;
					break;
				
				case "lcd":
					File nloc = new File(local, cmds.length > 1 ? cmds[1] : sc.next());
					if (!nloc.isDirectory()) {
						System.out.println(nloc.getName()+" is not a directory");
						break;
					}
					local = nloc;
					break;
				
				case "ls": {
					RemoteFile[] lst = (remote == null ? RemoteFile.listRoots(cli) : remote.listFiles());
					for (RemoteFile f : lst) {
						System.out.print(f.isDirectory() ? "d" : "-");
						System.out.print(f.canRead() ? "r" : "-");
						System.out.print(f.canWrite() ? "w" : "-");
						System.out.print(f.canExecute() ? "x" : "-");
						System.out.print(' ');
						System.out.print(String.format("%10d", f.length()));
						System.out.print(' ');
						System.out.print(String.format("%12s", new SimpleDateFormat(dateFormat).format(new Date(f.lastModified()))));
						System.out.print(' ');
						System.out.println(f.getName());
					}
					break; }
					
				case "lls":
					for (File f : local.listFiles()) {
						System.out.print(f.isDirectory() ? "d" : "-");
						System.out.print(f.canRead() ? "r" : "-");
						System.out.print(f.canWrite() ? "w" : "-");
						System.out.print(f.canExecute() ? "x" : "-");
						System.out.print(' ');
						System.out.print(f.length());
						System.out.print(' ');
						System.out.print(new SimpleDateFormat(dateFormat).format(new Date(f.lastModified())));
						System.out.print(' ');
						System.out.println(f.getName());
					}
					break;
					
				case "rm":
					arg1 = (cmds.length > 1 ? cmds[1] : sc.next());
					// TODO: Handle wildcards
					try {
						if (new RemoteFile(remote, arg1, false).delete())
							System.out.println(arg1+" deleted.");
						else
							System.out.println("Could not delete "+arg1+".");
					} catch (IOException e) { } // Does not happen with 'false' as a third argument of new RemoteFile()
					break;
					
				case "lrm":
					arg1 = (cmds.length > 1 ? cmds[1] : sc.next());
					if (new File(local, arg1).delete())
						System.out.println(arg1+" deleted.");
					else
						System.out.println("Could not delete "+arg1+".");
					break;
					
				case "get":
					// TODO:
					break;
					
				case "put":
					// TODO: RemoteOuputStream
					break;
					
				case "opt":
					// TODO:
					break;
					
				case "bye":
					stop();
					break;
				
				default:
					printHelp();
					break;
			}
		}
		System.out.println("Connection closed.");
	}
	
	public static void printHelp() {
		System.out.println("Commands:");
		System.out.println("LS  - List remote files");
		System.out.println("LLS - List local files");
		System.out.println("CD  <remote directory>         - Change remote directory");
		System.out.println("LCD <local directory>          - Change local directory");
		System.out.println("RM  <remote file>              - Delete remote file");
		System.out.println("LRM  <local file>              - Delete local file");
		System.out.println("GET <remote file> [local file] - Retrieve remote file");
		System.out.println("PUT <local file> [remote file] - Send a local file");
		System.out.println("OPT <option> [value]           - Set or retrieve an option value:");
		System.out.println("    Z [true|false]                 - Set deflate compression");
	}
	
}
