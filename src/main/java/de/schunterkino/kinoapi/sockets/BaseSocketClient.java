package de.schunterkino.kinoapi.sockets;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;

public class BaseSocketClient<T extends BaseCommands<S, V>, S, V> implements Runnable {
	private String ip;
	private int port;
	private String log_tag;
	private SocketAddress socketAddress;
	private Socket socket;
	private T commands;
	private boolean stop;
	private boolean alreadyPrintedError;

	// Wait X seconds after a connection problem until trying again.
	private static int RECONNECT_TIME = 10;

	public BaseSocketClient(String ip, int port, Class<T> typeArgumentClass) {
		this.ip = ip;
		this.port = port;
		this.log_tag = typeArgumentClass.getSimpleName();
		this.socket = null;
		try {
			this.socketAddress = new InetSocketAddress(InetAddress.getByName(ip), port);
			this.commands = typeArgumentClass.newInstance();
		} catch (InstantiationException | IllegalAccessException | UnknownHostException e) {
			e.printStackTrace();
		}
		this.stop = false;
		this.alreadyPrintedError = false;
	}

	@Override
	public void run() {
		// Connect! And keep trying to connect too.
		while (!stop) {
			try {
				socket = new Socket();
				socket.connect(socketAddress, 5000);
				socket.setKeepAlive(true);
				socket.setSoTimeout(5000);

				System.out.printf("%s: Connected to %s:%d.%n", log_tag, ip, port);

				// Start to handle socket messages.
				commands.setSocket(socket);

				// Block until the socket to is done.
				// This only returns if there is an issue with the
				// socket or we requested it to stop.
				commands.processSocket();

				// Print a reconnect error message next time again now that
				// we connected again.
				alreadyPrintedError = false;

			} catch (IOException e) {
				// The readerThread will die on its own if it already
				// started.
				if (!stop && !alreadyPrintedError) {
					System.err.printf("%s: Error in connection. Trying to reconnect every %d seconds. Exception: %s%n",
							log_tag, RECONNECT_TIME, e.getMessage());
					// Don't print the error again if the server stays down.
					alreadyPrintedError = true;
				}
			}

			// Properly shutdown the client connection.
			try {
				if (isConnected()) {
					socket.close();
					System.out.printf("%s: Connection closed.%n", log_tag);
				}
			} catch (IOException e) {
				System.err.printf("%s: Error while closing connection: %s%n", log_tag, e.getMessage());
			}

			if (stop)
				break;

			// Wait X seconds until we try to connect again.
			try {
				Thread.sleep(RECONNECT_TIME * 1000);
			} catch (InterruptedException e) {
				if (!stop)
					e.printStackTrace();
			}
		}
	}

	public boolean isConnected() {
		return socket != null && socket.isConnected() && !socket.isClosed();
	}

	public T getCommands() {
		return commands;
	}

	public void stopServer() {
		if (stop)
			return;

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
