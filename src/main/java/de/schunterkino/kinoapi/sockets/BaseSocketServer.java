package de.schunterkino.kinoapi.sockets;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;

public class BaseSocketServer<T extends BaseSocketCommands<S>, S> implements Runnable {
	private String ip;
	private int port;
	private String log_tag;
	private SocketAddress socketAddress;
	private Socket socket;
	private T commands;
	private boolean connected;
	private boolean stop;

	public BaseSocketServer(String ip, int port, String log_tag, Class<T> typeArgumentClass) {
		this.ip = ip;
		this.port = port;
		this.log_tag = log_tag;
		this.socket = null;
		try {
			this.socketAddress = new InetSocketAddress(InetAddress.getByName(ip), port);
			this.commands = typeArgumentClass.newInstance();
		} catch (InstantiationException | IllegalAccessException | UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.connected = false;
		this.stop = false;
	}

	@Override
	public void run() {
		// Connect! And keep trying to connect too.
		while (!stop) {
			try {
				try {
					socket = new Socket();
					socket.connect(socketAddress, 5000);
					socket.setSoTimeout(5000);
					connected = true;

					// Start a thread to handle telnet messages.
					commands.setSocket(socket);
					Thread readerThread = new Thread(commands);
					readerThread.start();

					System.out.printf("%s: Connected to %s:%d.%n", log_tag, ip, port);

					// Wait for the thread to be done.
					// The thread only terminates, if there is an issue with the
					// socket or we requested it to stop.
					readerThread.join();

				} catch (IOException e) {
					// The readerThread will die on its own if it already
					// started.
					if (!stop)
						System.err.printf(
								"%s: Error in connection. Reconnecting in 30 seconds. Exception: %s%n", log_tag, e.getMessage());
				}
			} catch (InterruptedException e) {
				System.err.printf("%s: Error while waiting for reader thread: %s%n", log_tag, e.getMessage());
			}

			// Properly shutdown the client connection.
			try {
				if (socket != null) {
					socket.close();
					System.out.printf("%s: Connection closed.%n", log_tag);
				}
			} catch (IOException e) {
				System.err.printf("%s: Error while closing connection: %s%n", log_tag, e.getMessage());
			}

			connected = false;

			if (stop)
				break;

			// Wait 30 seconds until we try to connect again.
			try {
				Thread.sleep(30000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public boolean isConnected() {
		return connected;
	}

	public T getCommands() {
		return commands;
	}

	public void stopServer() {
		stop = true;
		if (commands != null)
			commands.stop();
		try {
			if (socket != null)
				socket.close();
		} catch (IOException e) {
			// Who cares.
		}
	}
}
