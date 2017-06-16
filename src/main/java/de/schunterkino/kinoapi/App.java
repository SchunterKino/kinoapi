package de.schunterkino.kinoapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import de.schunterkino.kinoapi.dolby.DolbyWrapper;
import de.schunterkino.kinoapi.websocket.CinemaWebSocketServer;

public class App {
	private final static int WEBSOCKET_PORT = 8641;
	private final static String DOLBY_IP = "10.100.152.16";
	private final static int DOLBY_PORT = 61408;
	private final static String CHRISTIE_IMB_IP = "10.100.152.13";
	private final static int CHRISTIE_IMB_PORT = 5111;
	private final static String JNIOR_IP = "10.100.152.12";
	private final static int JNIOR_PORT = 9200;

	public static void main(String[] args) {

		// Setup the Dolby CP750 connection.
		DolbyWrapper dolbyConnection = new DolbyWrapper(DOLBY_IP, DOLBY_PORT);
		Thread dolbyThread = new Thread(dolbyConnection);
		dolbyThread.start();

		// Start the websocket server now.
		CinemaWebSocketServer websocketServer = new CinemaWebSocketServer(WEBSOCKET_PORT, dolbyConnection);
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

			// Shutdown the servers.
			try {
				websocketServer.stop();
			} catch (IOException | InterruptedException e) {
				// We want to stop anyways. Errors are ok.
			}
			System.out.println("WebSocket: Server stopped.");

			// Wait for the hardware connections to terminate.
			try {
				dolbyThread.join();
			} catch (InterruptedException e) {
				// We want to stop anyways. Errors are ok.
			}
			System.out.println("Dolby: Server stopped.");
		}
	}
}
