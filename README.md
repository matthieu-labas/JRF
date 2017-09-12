# JRF - Java Remote File

JRF is a client/server application that allows to extend local storage to other computers running the JRF Server. It has been designed for ultra-simplicity, **not for security!** It does not request for authentication when accessing files on a remote server, nor does it prevent access to files or directories. It should be used on already-secured networks (e.g. LANs or inside VPNs), where file-sharing setup is tedious (or impossible). Basic access configuration/restriction should be performed on the OS, by changing rights of the user running the JRF Server.

It is like an insecure FTP-like file sharing runnning on a single TCP port. Its Java nature gives Java programmers access to remote files as remote `InputStream` and `OutputStream`, with the added value of optionally **compressing data exchange** to ease up network usage.

An FTP-like CLI is available for end-users as well (with the same confidentiality and open access).

**DISCLAIMER** - YOU UNDERSTAND IT SHOULD NOT BE USED TO GIVE ACCESS TO CONFIDENTIAL DATA AND YOU ARE SOLELY RESPONSIBLE FOR SUCH DATA LEAKS, IF ANY SUCH SHOULD OCCUR.

## Design and use-cases

There is a `JRFServer` listening for incoming connections of remote `JRFClient`s. Those clients can then open `InputStream` or `OutputStream` remotely and use them as if they were local.

#### Remote `File`

The `RemoteFile` class extends `java.io.File` so it can be used to give to methods requiring a `File` argument. Use it to query and act on the remote filesystem, as if it were a local drive.

All methods are overriden to query the JRF Server for specific actions (e.g. `delete()`, `mkdir()`, ...), though meta-information such as size or rwx attributes are cached (but can be refreshed by a call to `RemoteFile.refresh()`).

#### Remote streams

Remote streams embed compression capabilities to ease up network load and trade it for CPU, which makes file chunk transfers more efficient. It adapts automatically if data cannot be properly compressed: the original data chunk is sent instead of the compressed one (whichever is smaller).

They have a `getInfo()` method that return a `StreamInfo` object, containing information about the number of bytes transferred from/to the remote file and on the network, as well as the time it took for the transfer. It allows for compression ratio and network latency/bandwidth computation.

Under the hood they are a `Buffered*Stream` wrapping a `File*Stream` (`*` being `Input` or `Output`).

## Java Examples

### Server side

#### Start a JRF Server instance:

```java
int port = 2205; // Port on which JRF Server will listen for connection (should be TCP-opened in the firewall)
JRFServer srv = JRFServer.get(new InetSocketAddress(port));
srv.start();
while (!exit) {
    // Do things
}
srv.requestStop(); // Disconnects all connected clients
srv.join(); // Wait for graceful shutdown
```

### Client side

#### Connect to a JRF Server:

```java
JRFClient cli = new JRFClient(new InetSocketAddress(serverAddr, serverPort));
cli.start();
while (!exit) {
    // Do things
}
cli.requestStop(); // Closes all remote stream and disconnects from the JRF Server
cli.join(); // Wait for close completion
```

#### Remote File

```java
JRFClient cli = new JRFClient(new InetSocketAddress(serverAddr, serverPort));
cli.start();
File f = null;
try {
    f = new RemoteFile(cli, "/tmp/test.xml");
} catch (IOException e) { // Network error: file is not reachable
    e.printStackTrace();
}
if (f != null) {
    f.length();
    f.delete();
    f.createNew();
    // etc.
}
cli.requestStop();
cli.join();
```

#### Open a remote stream

`InputStream`:

```java
String file = "C:/Windows/System32/drivers/etc/hosts"; // ANY file, as seen from the JRF Server (a Windows machine, in this case)
int deflate = 3; // Use chunk compression for network data transfer
try (InputStream is = cli.getRemoteInputStream(file, deflate)) {
	    // 'is' is a regular InputStream, so read(), skip(), mark(), reset(), ...
	    // If compression is used, data read is already decompressed
} catch (IOException e) {
    e.printStackTrace();
}
```

`OutputStream`:

```java
String file = "/etc/passwd";
try (OutputStream os = cli.getRemoteOutputStream(file)) { // No compression
	    // 'os' is a regular OutputStream, so write(), flush(), ...
	    // Writing to /etc/passwd could be allowed if the JRFServer is run as root!!!
} catch (IOException e) {
    e.printStackTrace();
}
```

#### File transfer

Files can be transferred as a whole, either using `RemoteInputStream` (to download) or `RemoteOutputStream` (to upload), but for complete file download, a more convenient method `JRFClient.getFile()` is provided that copies a remote file locally. Its biggest advantage is the use of compression on the whole file content (through `InflaterOutputStream`) that will compress the data remotely before sending them. That is useful when working with big, easily-compressable files on low-bandwidth networks. You can also tune the data chunk size, for network requiring specific, smaller MTUs:

```java
String file = "/data/images/huge_bitmap.bmp"; // Still using .bmp?
String local = file; // Copy to the same path
int deflate = 9; // Best compression
try {
    cli.getFile(file, deflate, local, 1450); // Send packets which payload do not exceed 1450 bytes
} catch (IOException e) {
    e.printStackTrace();
}
```

There also is a `JRFClient.putFile()` method, but it uses `RemoteOutputStream` (so is less efficient with regards to compression) and is provided for convenience and completeness.

## Command Line Interface (CLI)

JRF can also be used as a kind of FTP server, through the command line. Mind that it will bring full access to all files on the remote server, without requiring authentication!

### Server

The JRF Server is run:

    java -cp JRF-v1.0.0.jar net.jrf.server.JRFServer [hostname[:port]]

If no port is specified, the default `2205` will be used.

When started, a prompt allows to query the Server status or exit:

    $ help
    Commands:
    bye - Exit
    ?   - Show connected clients and opened files
    $ ?
    2 client(s) connected:
    10.2.17.101:36528
        [ in] /tmp/test.tiff
        [out] /tmp/out.big
        [out] /tmp/temp.tmp
    johnpc:19262
        [ in] /media/movies/idiocracy.mp4
    $ bye
    
    Do you want to exit (y/N)? y
    Bye.

### Client

The JRF CLI client is run:

    java -cp JRF-v1.0.0.jar net.jrf.client.JRFClient <hostname[:port]>

If no port is specified, the default `2205` will be used.

The commands are similar to the standard `ftp` CLI, with a few variations:

Command                | Description
-----------------------|------------
`cd`                   | Change remote directory
`lcd`                  | Change local directory
`ls`                   | List remote files
`lls`                  | List local files
`rm`                   | Delete remote file
`lrm`                  | Delete local file
`mv`                   | Rename/Move remote file
`lmv`                  | Rename/Move local file
`md`                   | Create remote directory
`lmd`                  | Create local directory
`get <file>`           | Download remote file
`put <file>`           | Upload local file
`opt <name> [value]`   | Set option value:<br/>- `z` Set compression level: 0 (no compression) to 9 (max compression)<br/>- `mtu` Set network chunk size
`bye`             | Exit

The prompt will show both local and remote directories:

    C:\Temp <> (no root) $ ls
    dr-x       4096 22 May 2004 16:30:01 /
    C:\Temp <> (no root) $ cd /
    C:\Temp <> / $ ls
    dr-x       4096 22 May 2004 16:30:01 tmp
    ...
    dr-x       4096 30 Apr 2010 12:41:55 media
    ...
    C:\Temp <> / $ cd media
    C:\Temp <> /media $ ls
    dr-x       4096 14 Nov 2010 13:56:48 music
    dr-x       4096 10 Dec 2015 13:56:48 movies
    C:\Temp <> /media $ cd movies
    C:\Temp <> /media/movies $ ls
    -r--  365847192 28 Dec 2015 09:12:21 idiocracy.mp4
    C:\Temp <> /media/movies $ lcd D:\Movies
    D:\Movies <> /media/movies $ lls
    D:\Movies <> /media/movies $ get idiocracy.mp4
    Copied 365847192 bytes in 182.3s (1959.8 kB/s, 0% deflate)
    C:\Temp <> /media/movies $ lls
    -r--  365847192 28 Dec 2015 09:12:21 idiocracy.mp4

## Why?

This project was born out of my frustration accessing files from Windows shares on local networks where the remote computer is there but access is denied because:
- it is not in the same Workgroup
- the username/password provided is already used somewhere else
- the session has somehow "expired"
- ...

I wanted to have a *simple* way of accessing my files on a remote computer without the hassle of creating users/passwords/credentials/...

Also, remote files could be big, uncompressed images and a slow network was making their access difficult.

As I'm using Java as a main programming language, I decided to use its `InputStream`/`OutputStream` facilities to allow me to access my remote files without the above hassle. Extending those classes allows to seamlessly pass them to external libraries dealing with streams without them noticing it's actually going through the network.

And, it has built-in support for data chunk/file compression through `Deflater`/`Inflater` classes, which allows for a more efficient use of the network bandwidth (in exchange for CPU time to (un)compress data).
