package de.schunterkino.kinoapi;

import java.io.IOException;

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
import de.schunterkino.kinoapi.sockets.BaseSerialPortClient;
import de.schunterkino.kinoapi.sockets.BaseSocketClient;
import de.schunterkino.kinoapi.websocket.CinemaWebSocketServer;

public class Main {

	private BaseSocketClient<DolbySocketCommands, IDolbyStatusUpdateReceiver, DolbyCommand> dolbyConnection;
	private Thread dolbyThread;

	private BaseSocketClient<JniorSocketCommands, IJniorStatusUpdateReceiver, JniorCommand> jniorConnection;
	private Thread jniorThread;

	private BaseSocketClient<ChristieSocketCommands, IChristieStatusUpdateReceiver, ChristieCommand> christieConnection;
	private Thread christieThread;

	private BaseSerialPortClient<SolariaSocketCommands, ISolariaSerialStatusUpdateReceiver, SolariaCommand> solariaConnection;
	private Thread solariaThread;

	private AudioPlayer audio;

	private CinemaWebSocketServer websocketServer;

	public void start() {
		// Setup the Dolby CP750 connection.
		dolbyConnection = new BaseSocketClient<>(App.getConfigurationString("dolby_ip"),
				App.getConfigurationInteger("dolby_port"), DolbySocketCommands.class);
		dolbyThread = new Thread(dolbyConnection);
		dolbyThread.start();

		// Setup the Integ Jnior 310 connection.
		jniorConnection = new BaseSocketClient<>(App.getConfigurationString("jnior_ip"),
				App.getConfigurationInteger("jnior_port"), JniorSocketCommands.class);
		jniorThread = new Thread(jniorConnection);
		jniorThread.start();

		// Setup the Christie Projection connection.
		christieConnection = new BaseSocketClient<>(App.getConfigurationString("christie_imb_ip"),
				App.getConfigurationInteger("christie_imb_port"), ChristieSocketCommands.class);
		christieThread = new Thread(christieConnection);
		christieThread.start();

		solariaConnection = new BaseSerialPortClient<>(App.getConfigurationString("pib_serial_port"),
				SolariaSocketCommands.class);
		solariaThread = new Thread(solariaConnection);
		solariaThread.start();

		// Create an audio player to play some nice tunes when the lamp is
		// cooled off.
		audio = new AudioPlayer(dolbyConnection, solariaConnection);

		// Start the websocket server now.
		websocketServer = new CinemaWebSocketServer(App.getConfigurationInteger("websocket_port"), dolbyConnection,
				jniorConnection, christieConnection, solariaConnection);
		websocketServer.start();
		System.out.println("WebSocket: Server created on port: " + websocketServer.getPort());
	}

	public void stop() {
		// Cleanup!
		dolbyConnection.stopServer();
		jniorConnection.stopServer();
		christieConnection.stopServer();
		solariaConnection.stopServer();

		// Shutdown the servers.
		if (websocketServer != null) {
			try {
				websocketServer.stop();
			} catch (InterruptedException e) {
				// We want to stop anyways. Errors are ok.
			}
			websocketServer = null;
			System.out.println("WebSocket: Server stopped.");
		}

		// Wait for the hardware connections to terminate.
		if (dolbyThread != null) {
			try {
				if (!dolbyConnection.isConnected())
					dolbyThread.interrupt();
				dolbyThread.join();
			} catch (InterruptedException e) {
				// We want to stop anyways. Errors are ok.
			}
			dolbyThread = null;
			System.out.println("Dolby: Client stopped.");
		}

		if (jniorThread != null) {
			try {
				if (!jniorConnection.isConnected())
					jniorThread.interrupt();
				jniorThread.join();
			} catch (InterruptedException e) {
				// We want to stop anyways. Errors are ok.
			}
			jniorThread = null;
			System.out.println("Jnior: Client stopped.");
		}

		if (christieThread != null) {
			try {
				if (!christieConnection.isConnected())
					christieThread.interrupt();
				christieThread.join();
			} catch (InterruptedException e) {
				// We want to stop anyways. Errors are ok.
			}
			christieThread = null;
			System.out.println("Christie: Client stopped.");
		}

		if (solariaThread != null) {
			try {
				if (!solariaConnection.isConnected())
					solariaThread.interrupt();
				solariaThread.join();
			} catch (InterruptedException e) {
				// We want to stop anyways. Errors are ok.
			}
			solariaThread = null;
			System.out.println("Solaria: Client stopped.");
		}

		// Kill any running audio thread.
		if (audio != null) {
			audio.stopSound();
			audio = null;
		}
	}
}
