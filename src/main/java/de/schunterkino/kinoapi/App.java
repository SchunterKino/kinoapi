package de.schunterkino.kinoapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import de.schunterkino.kinoapi.dolby.DolbySocketCommands;
import de.schunterkino.kinoapi.dolby.IDolbyStatusUpdateReceiver;
import de.schunterkino.kinoapi.jnior.IJniorStatusUpdateReceiver;
import de.schunterkino.kinoapi.jnior.JniorSocketCommands;
import de.schunterkino.kinoapi.sockets.BaseSocketClient;
import de.schunterkino.kinoapi.websocket.CinemaWebSocketServer;

public class App {
	// TODO: Put in config file.
	private final static int WEBSOCKET_PORT = 8641;
	private final static String DOLBY_IP = "10.100.152.16";
	private final static int DOLBY_PORT = 61408;
	private final static String CHRISTIE_IMB_IP = "10.100.152.13";
	private final static int CHRISTIE_IMB_PORT = 5111;
	private final static String JNIOR_IP = "10.100.152.12";
	private final static int JNIOR_PORT = 9202;

	public static void main(String[] args) {

		// Setup the Dolby CP750 connection.
		BaseSocketClient<DolbySocketCommands, IDolbyStatusUpdateReceiver> dolbyConnection = new BaseSocketClient<>(
				DOLBY_IP, DOLBY_PORT, "Dolby", DolbySocketCommands.class);
		Thread dolbyThread = new Thread(dolbyConnection);
		dolbyThread.start();

		// Setup the Integ Jnior 310 connection.
		BaseSocketClient<JniorSocketCommands, IJniorStatusUpdateReceiver> jniorConnection = new BaseSocketClient<>(
				JNIOR_IP, JNIOR_PORT, "Jnior", JniorSocketCommands.class);
		Thread jniorThread = new Thread(jniorConnection);
		jniorThread.start();

		// Start the websocket server now.
		CinemaWebSocketServer websocketServer = new CinemaWebSocketServer(WEBSOCKET_PORT, dolbyConnection,
				jniorConnection);
		websocketServer.start();
		System.out.println("WebSocket: Server created on port: " + websocketServer.getPort());

		// Keep the server running forever.
		BufferedReader sysin = new BufferedReader(new InputStreamReader(System.in));
		try {
			while (true) {
				System.out.println("Type \"q\" or \"quit\" to stop the server.");
				// Stop it eventually if the user types one of the commands.
				// readLine blocks.
				String input = sysin.readLine();
				if (input.equals("q") || input.equals("quit") || input.equals("exit"))
					break;
			}
		} catch (IOException e) {
			// Ctrl+C anyone?
			e.printStackTrace();
		} finally {

			// Cleanup!
			dolbyConnection.stopServer();
			jniorConnection.stopServer();

			// Shutdown the servers.
			try {
				websocketServer.stop();
			} catch (IOException | InterruptedException e) {
				// We want to stop anyways. Errors are ok.
			}
			System.out.println("WebSocket: Server stopped.");

			// Wait for the hardware connections to terminate.
			try {
				if (!dolbyConnection.isConnected())
					dolbyThread.interrupt();
				dolbyThread.join();
			} catch (InterruptedException e) {
				// We want to stop anyways. Errors are ok.
			}
			System.out.println("Dolby: Server stopped.");

			try {
				if (!jniorConnection.isConnected())
					jniorThread.interrupt();
				jniorThread.join();
			} catch (InterruptedException e) {
				// We want to stop anyways. Errors are ok.
			}
			System.out.println("Jnior: Server stopped.");
		}
	}
}
