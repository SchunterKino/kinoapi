package de.schunterkino.kinoapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

import de.schunterkino.kinoapi.audio.AudioPlayer;
import de.schunterkino.kinoapi.christie.ChristieCommand;
import de.schunterkino.kinoapi.christie.ChristieSocketCommands;
import de.schunterkino.kinoapi.christie.IChristieStatusUpdateReceiver;
import de.schunterkino.kinoapi.christie.serial.ISolariaSerialStatusUpdateReceiver;
import de.schunterkino.kinoapi.christie.serial.SolariaCommand;
import de.schunterkino.kinoapi.christie.serial.SolariaSocketCommands;
import de.schunterkino.kinoapi.dolby.DolbyCommand;
import de.schunterkino.kinoapi.dolby.DolbySocketCommands;
import de.schunterkino.kinoapi.dolby.IDolbyStatusUpdateReceiver;
import de.schunterkino.kinoapi.jnior.IJniorStatusUpdateReceiver;
import de.schunterkino.kinoapi.jnior.JniorCommand;
import de.schunterkino.kinoapi.jnior.JniorSocketCommands;
import de.schunterkino.kinoapi.listen.SchunterServerSocket;
import de.schunterkino.kinoapi.sockets.BaseSerialPortClient;
import de.schunterkino.kinoapi.sockets.BaseSocketClient;
import de.schunterkino.kinoapi.websocket.CinemaWebSocketServer;

public class App {

	// the configuration file is stored in the class path as a
	// .properties file
	private static final String CONFIGURATION_FILE = "/config.properties";

	private static final Properties properties;

	// use static initializer to read the configuration file when the class is
	// loaded
	static {
		properties = new Properties();
		try (InputStream inputStream = Configuration.class.getResourceAsStream(CONFIGURATION_FILE)) {
			properties.load(inputStream);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read file " + CONFIGURATION_FILE, e);
		}
	}

	public static void main(String[] args) {

		// Setup the Dolby CP750 connection.
		BaseSocketClient<DolbySocketCommands, IDolbyStatusUpdateReceiver, DolbyCommand> dolbyConnection = new BaseSocketClient<>(
				getConfigurationString("dolby_ip"), getConfigurationInteger("dolby_port"), DolbySocketCommands.class);
		Thread dolbyThread = new Thread(dolbyConnection);
		dolbyThread.start();

		// Setup the Integ Jnior 310 connection.
		BaseSocketClient<JniorSocketCommands, IJniorStatusUpdateReceiver, JniorCommand> jniorConnection = new BaseSocketClient<>(
				getConfigurationString("jnior_ip"), getConfigurationInteger("jnior_port"), JniorSocketCommands.class);
		Thread jniorThread = new Thread(jniorConnection);
		jniorThread.start();

		// Setup the Christie Projection connection.
		BaseSocketClient<ChristieSocketCommands, IChristieStatusUpdateReceiver, ChristieCommand> christieConnection = new BaseSocketClient<>(
				getConfigurationString("christie_imb_ip"), getConfigurationInteger("christie_imb_port"),
				ChristieSocketCommands.class);
		Thread christieThread = new Thread(christieConnection);
		christieThread.start();

		BaseSerialPortClient<SolariaSocketCommands, ISolariaSerialStatusUpdateReceiver, SolariaCommand> solariaConnection = new BaseSerialPortClient<>(
				getConfigurationString("pib_serial_port"), SolariaSocketCommands.class);
		Thread solariaThread = new Thread(solariaConnection);
		solariaThread.start();

		SchunterServerSocket serverSocket = new SchunterServerSocket(getConfigurationInteger("listen_port"));
		Thread serverThread = new Thread(serverSocket);
		serverThread.start();

		// Create an audio player to play some nice tunes when the lamp is
		// cooled off.
		AudioPlayer audio = new AudioPlayer(dolbyConnection, solariaConnection, serverSocket);

		// Start the websocket server now.
		CinemaWebSocketServer websocketServer = new CinemaWebSocketServer(getConfigurationInteger("websocket_port"),
				dolbyConnection, jniorConnection, christieConnection, solariaConnection, serverSocket);
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
			christieConnection.stopServer();
			solariaConnection.stopServer();
			serverSocket.stopServer();

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
			System.out.println("Dolby: Client stopped.");

			try {
				if (!jniorConnection.isConnected())
					jniorThread.interrupt();
				jniorThread.join();
			} catch (InterruptedException e) {
				// We want to stop anyways. Errors are ok.
			}
			System.out.println("Jnior: Client stopped.");

			try {
				if (!christieConnection.isConnected())
					christieThread.interrupt();
				christieThread.join();
			} catch (InterruptedException e) {
				// We want to stop anyways. Errors are ok.
			}
			System.out.println("Christie: Client stopped.");

			try {
				if (!solariaConnection.isConnected())
					solariaThread.interrupt();
				solariaThread.join();
			} catch (InterruptedException e) {
				// We want to stop anyways. Errors are ok.
			}
			System.out.println("Solaria: Client stopped.");

			try {
				if (!serverSocket.isRunning())
					serverThread.interrupt();
				serverThread.join();
			} catch (InterruptedException e) {
				// We want to stop anyways. Errors are ok.
			}
			System.out.println("ServerSocket: Server stopped.");

			// Kill any running audio thread.
			audio.stopSound();
		}
	}

	public static String getConfigurationString(String key) {
		return properties.getProperty(key);
	}

	public static int getConfigurationInteger(String key) {
		return Integer.parseInt(properties.getProperty(key));
	}
}
