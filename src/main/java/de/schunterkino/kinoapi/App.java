package de.schunterkino.kinoapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import de.schunterkino.kinoapi.dolby.DolbyWrapper;
import de.schunterkino.kinoapi.websocket.CinemaWebSocketServer;

public class App {
	public static void main(String[] args) {
		// Start the websocket server right away.
		CinemaWebSocketServer websocketServer = new CinemaWebSocketServer();
		websocketServer.start();
		System.out.println("WebSocket: Server created on port: " + websocketServer.getPort());

		DolbyWrapper dolbyConnection = new DolbyWrapper();
		Thread dolbyThread = new Thread(dolbyConnection);
		dolbyThread.start();

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
