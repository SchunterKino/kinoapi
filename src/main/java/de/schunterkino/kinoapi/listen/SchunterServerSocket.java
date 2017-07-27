package de.schunterkino.kinoapi.listen;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.LinkedList;

public class SchunterServerSocket implements Runnable {
	private int port;
	private boolean stop;
	private ServerSocket server;
	private Instant lampOffTime;

	private LinkedList<IServerSocketStatusUpdateReceiver> listeners;

	public SchunterServerSocket(int port) {
		this.port = port;
		this.stop = false;
		this.server = null;
		this.lampOffTime = null;
		this.listeners = new LinkedList<>();
	}

	public void registerListener(IServerSocketStatusUpdateReceiver listener) {
		synchronized (listeners) {
			listeners.add(listener);
		}
	}

	@Override
	public void run() {
		// Keep trying to create a socket server.
		while (!stop) {
			try {
				server = new ServerSocket(port);
				System.out.printf("ServerSocket: Listening on port %d%n", port);

				while (!stop) {
					// Wait for the IMB to connect.
					Socket client = server.accept();
					handleChildSocket(client);
				}
			} catch (IOException e) {
				if (!stop)
					System.err.printf("ServerSocket: Error in server socket: %s%n", e.getMessage());
			}
			try {
				if (isRunning()) {
					server.close();
					System.out.println("ServerSocket: Server shutdown.");
				}
			} catch (IOException e) {
				System.err.printf("ServerSocket: Error while closing server socket: %s%n", e.getMessage());
			}
		}
	}

	private void handleChildSocket(Socket client) {
		try {
			// Only wait for input for 2 seconds.
			client.setSoTimeout(2000);

			try {
				// Read what it has to say.
				String input = read(client);
				if (input != null) {
					System.out.println("ServerSocket: Received " + input);

					if ("LampIsOffYo".equals(input)) {
						lampOffTime = Instant.now();
						synchronized (listeners) {
							for (IServerSocketStatusUpdateReceiver listener : listeners) {
								listener.onLampTurnedOff(lampOffTime);
							}
						}
					}
				}

			} catch (SocketTimeoutException e) {
				// Ignore. We don't have time forever.
			}

			client.close();
		} catch (IOException e) {
			if (!stop)
				System.err.printf("ServerSocket: Error in client socket: %s%n", e.getMessage());
		}
	}

	public void stopServer() {
		stop = true;
		try {
			if (isRunning())
				server.close();
		} catch (IOException e) {
		}
	}

	public boolean isRunning() {
		return server != null && server.isBound() && !server.isClosed();
	}

	public Instant getLampOffTime() {
		return lampOffTime;
	}

	protected String read(Socket socket) throws IOException {
		InputStream in = socket.getInputStream();
		byte[] buffer = new byte[1024];

		int ret_read = in.read(buffer);
		if (ret_read == -1)
			throw new IOException("EOF");

		if (ret_read == 0)
			return null;

		return new String(buffer, 0, ret_read);
	}
}
