package fr.jrf.client;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import fr.jrf.RemoteFile;

/**
 * Command Line Interface for {@link JRFClient}. Though it implements {@code Runnable}, it is not
 * primarily designed to run in a separate Thread as it is reading from {@code System.in}.
 * 
 * @author Matthieu Labas
 */
public class JRFClientCLI implements Runnable {
	
	public static final String dateFormat = "d MMM YYYY HH:mm:ss";
	
	private JRFClient cli;
	
	private Scanner sc;
	
	private File local;
	private RemoteFile remote;
	
	private int deflate = 0;
	private int mtu = 8192;
	
	public JRFClientCLI(JRFClient cli) {
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
	
	public static String[] splitCommand(String line) {
		line = line.trim();
		List<String> tokens = new ArrayList<>();
		int i0 = 0;
		boolean inEsc = false; // true when spaces should be escaped
		boolean remBS = false; // true to remove the backslashes-escapes
		boolean remDQ = false; // true to remove the double-quotes-escapes
		for (int i = i0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (ch == '\\') { // Escape next character
				remBS = true;
				i++;
				continue;
			}
			if (ch == '"') { // Start/End escaped sequence
				if (!inEsc) {
					remDQ = true;
					i++;
				}
				inEsc = !inEsc;
				continue;
			}
			if (Character.isWhitespace(ch) && !inEsc) {
				String sub;
				if (remDQ) {
					sub = line.substring(i0+1, i-1).trim();
					remDQ = false;
				} else
					sub = line.substring(i0, i).trim();
				if (remBS) {
					sub = sub.replace("\\", "");
					remBS = false;
				}
				tokens.add(sub);
				for (i0 = i; i0 < line.length() && Character.isWhitespace(line.charAt(i0)); i0++) ;
				continue;
			}
		}
		if (i0 < line.length()) {
			String sub;
			if (remDQ)
				sub = line.substring(i0+1, line.length()-1).trim();
			else
				sub = line.substring(i0).trim();
			if (remBS) sub = sub.replace("\\", "");
			tokens.add(sub);
		}
		return tokens.toArray(new String[0]);
	}
	
	/**
	 * <p>Tries to determine whether the given {@code path} is an absolute path.</p>
	 * <p>Under *nix, it's easy: {@code path} should start with a {@code '/'}.<br/>
	 * Under Windows, it's a little bit trickier (as usual...): 2nd character is {@code ':'}, 1st
	 * being the drive letter, or 1st character is a {@code '\'} (then good luck find the drive).</p>
	 * @param path The path to check
	 * @return
	 */
	public static boolean isAbsolute(String path) {
		if (path.charAt(0) == File.separatorChar) // Starts with '/' on *nix, or '\' on Windows
			return true;
		return (File.separatorChar == '\\' && path.charAt(1) == ':'); // Windows
	}
	
	/** Starts the CLI. The method blocks until the user requested a stop. Once the method returns,
	 * the {@link JRFClient} passed in the constructor is closed. */
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
			String c = sc.nextLine();
			String[] cmds = splitCommand(c);
			String arg1, arg2; 
			switch (cmds[0].toLowerCase()) {
				case "cd": {
					RemoteFile nrem;
					arg1 = (cmds.length > 1 ? cmds[1] : sc.next());
					try {
						if (remote == null || isAbsolute(arg1))
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
					break; }
				
				case "lcd": {
					File nloc;
					arg1 = cmds.length > 1 ? cmds[1] : sc.next();
					if (isAbsolute(arg1))
						nloc = new File(arg1);
					else if ("..".equals(arg1)) {
						String parent = local.getParent();
						if (parent != null) // 'remote' is a root => no change
							nloc = new File(parent);
						else
							nloc = local;
					} else
						nloc = new File(local, arg1);
					if (!nloc.isDirectory()) {
						System.out.println(nloc.getName()+" is not a directory");
						break;
					}
					local = nloc;
					break; }
				
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
					
				case "lls": {
					SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
					for (File f : local.listFiles()) {
						System.out.print(f.isDirectory() ? "d" : "-");
						System.out.print(f.canRead() ? "r" : "-");
						System.out.print(f.canWrite() ? "w" : "-");
						System.out.print(f.canExecute() ? "x" : "-");
						System.out.print(' ');
						System.out.print(f.length());
						System.out.print(' ');
						System.out.print(sdf.format(new Date(f.lastModified())));
						System.out.print(' ');
						System.out.println(f.getName());
					}
					} break;
					
				case "rm":
					arg1 = (cmds.length > 1 ? cmds[1] : sc.next());
					try {
						if (new RemoteFile(remote, arg1, false).delete())
							System.out.println(arg1+" deleted.");
						else
							System.out.println("Could not delete "+arg1);
					} catch (IOException e) { } // Does not happen with 'false' as a third argument of new RemoteFile()
					break;
					
				case "lrm":
					arg1 = (cmds.length > 1 ? cmds[1] : sc.next());
					if (new File(local, arg1).delete())
						System.out.println(arg1+" deleted.");
					else
						System.out.println("Could not delete "+arg1);
					break;
					
				case "mv":
					arg1 = (cmds.length > 1 ? cmds[1] : sc.next());
					arg2 = (cmds.length > 2 ? cmds[2] : sc.next());
					try {
						if (new RemoteFile(remote, arg1, false).renameTo(new RemoteFile(remote, arg2, false)))
							System.out.println(arg1+" renamed to "+arg2);
						else
							System.out.println("Could not rename "+arg1+" to "+arg2);
					} catch (IOException e) { } // Does not happen with 'false' as a third argument of new RemoteFile()
					break;
					
				case "lmv":
					arg1 = (cmds.length > 1 ? cmds[1] : sc.next());
					arg2 = (cmds.length > 2 ? cmds[2] : sc.next());
					if (new File(arg1).renameTo(new File(arg2)))
						System.out.println(arg1+" renamed to "+arg2);
					else
						System.out.println("Could not rename "+arg1+" to "+arg2);
					break;
					
				case "md":
					arg1 = (cmds.length > 1 ? cmds[1] : sc.next());
					try {
						if (new RemoteFile(remote, arg1, false).mkdirs())
							System.out.println(arg1+" created.");
						else
							System.out.println("Could not create "+arg1+".");
					} catch (IOException e) { } // Does not happen with 'false' as a third argument of new RemoteFile()
					break;
					
				case "lmd":
					arg1 = (cmds.length > 1 ? cmds[1] : sc.next());
					if (new File(local, arg1).mkdirs())
						System.out.println(arg1+" created.");
					else
						System.out.println("Could not create "+arg1+".");
					break;
					
				case "get":
					if (remote == null) {
						System.out.println("No remote directory selected.");
						break;
					}
					arg1 = (cmds.length > 1 ? cmds[1] : sc.next());
					String rem = remote.getPath()+"/"+arg1;
					try {
						long t0 = System.currentTimeMillis();
						long len = 0;
//						try (FileOutputStream fos = new FileOutputStream(new File(local, new File(arg1).getName()))) {
//							byte[] buf = new byte[1500]; // TODO: MTU
//							try (InputStream is = new BufferedInputStream(cli.getRemoteInputStream(rem), 2*buf.length)) {
//								int n;
//								for (;;) {
//									n = is.read(buf);
//									if (n < 0) // EOF
//										break;
//									len += n;
//									fos.write(buf, 0, n);
//								}
//							}
//						}
//						t0 = System.currentTimeMillis() - t0;
						len = cli.getFile(rem, deflate, arg1, 1500); // TODO: MTU
						t0 = System.currentTimeMillis() - t0;
						System.out.println(String.format("Copied %d bytes in %.1f s (%.1f kB/s)", len, t0 / 1000.0f, (len/1024.0f*1000.0f/t0)));
					} catch (IOException e) {
						System.out.println("Error while getting "+arg1+": "+e.getMessage());
					}
					break;
					
				case "put":
					arg1 = (cmds.length > 1 ? cmds[1] : sc.next());
					// TODO: RemoteOuputStream
					break;
					
				case "opt":
					arg1 = (cmds.length > 1 ? cmds[1] : sc.next()).toLowerCase();
					switch (arg1) {
						case "z":
							if (cmds.length > 2) {
								try {
									deflate = Integer.parseInt(cmds[2]);
									if (deflate <= 0)
										System.out.println("Compression disabled");
									else if (deflate > 9) {
										deflate = 9;
										System.out.println("Compression set to maximum 9");
									} else
										System.out.println("Compression set to "+deflate);
								} catch (NumberFormatException e) {
									System.out.println(cmds[2]+" is not a valid compression value.");
								}
							} else
								System.out.println("Compression set to "+(deflate > 0 ? ""+deflate : "disabled"));
							break;
						
						case "mtu":
							if (cmds.length > 2) {
								try {
									mtu = Integer.parseInt(cmds[2]);
									System.out.println("MTU set to "+mtu);
								} catch (NumberFormatException e) {
									System.out.println(cmds[2]+" is not a valid MTU value.");
								}
							} else
								System.out.println("MTU set to "+mtu);
							break;
					}
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
		System.out.println("LS                             - List remote files");
		System.out.println("LLS                            - List local files");
		System.out.println("CD  <remote directory>         - Change remote directory");
		System.out.println("LCD <local directory>          - Change local directory");
		System.out.println("MD  <remote directory>         - Create remote directories");
		System.out.println("LMD <local directories>        - Create local directories");
		System.out.println("RM  <remote file>              - Delete remote file");
		System.out.println("LRM <local file>               - Delete local file");
		System.out.println("MV  <remote file> <new name>   - Rename/Move remote file");
		System.out.println("LMV <local file> <new name>    - Rename/Move remote file");
		System.out.println("GET <remote file> [local file] - Retrieve remote file");
		System.out.println("PUT <local file> [remote file] - Send a local file");
		System.out.println("OPT <option> [value]           - Set or retrieve an option value:");
		System.out.println("    Z   [0..9]                     - Set deflate compression (0:none, 9:max)");
		System.out.println("    MTU [value]                    - Set network MTU");
	}
	
}
