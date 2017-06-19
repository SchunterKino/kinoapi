package de.schunterkino.kinoapi.dolby;

import java.io.IOException;
import java.net.Socket;

public class DolbyWrapper implements Runnable {

	private String dolbyip;
	private int dolbyport;
	private Socket socket;
	private DolbySocketCommands dolbyCommands;
	private boolean connected;
	private boolean stop;

	public DolbyWrapper(String ip, int port) {
		dolbyip = ip;
		dolbyport = port;
		socket = null;
		dolbyCommands = new DolbySocketCommands();
		connected = false;
		stop = false;
	}

	@Override
	public void run() {
		// Connect! And keep trying to connect too.
		while (!stop) {
			try {
				try {
					socket = new Socket(dolbyip, dolbyport);
					socket.setSoTimeout(5000);
					connected = true;

					// Start a thread to handle telnet messages.
					dolbyCommands.setSocket(socket);
					Thread readerThread = new Thread(dolbyCommands);
					readerThread.start();

					System.out.printf("Dolby: Connected to %s:%d.%n", dolbyip, dolbyport);

					// Wait for the thread to be done.
					// The thread only terminates, if there is an issue with the
					// socket or we requested it to stop.
					readerThread.join();

				} catch (IOException e) {
					// The readerThread will die on its own if it already
					// started.
					if (!stop)
						System.err.println(
								"Error in dolby connection. Reconnecting in 30 seconds. Exception: " + e.getMessage());
				}
			} catch (InterruptedException e) {
				System.err.println("Error while waiting for dolby reader thread: " + e.getMessage());
			}

			// Properly shutdown the client connection.
			try {
				socket.close();
				System.out.println("Dolby: Connection closed.");
			} catch (IOException e) {
				System.err.println("Error while closing dolby connection: " + e.getMessage());
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

	public DolbySocketCommands getTelnetCommands() {
		return dolbyCommands;
	}

	public void stopServer() {
		stop = true;
		if (dolbyCommands != null)
			dolbyCommands.stop();
		try {
			if (socket != null)
				socket.close();
		} catch (IOException e) {
			// Who cares.
		}
		if (!connected)
			Thread.currentThread().interrupt();
	}
}
