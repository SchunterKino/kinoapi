package de.schunterkino.kinoapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

public class App {

	// the configuration file is stored in the class path as a
	// .properties file
	private static final String CONFIGURATION_FILE = "/config.properties";

	private static final Properties properties;

	// use static initializer to read the configuration file when the class is
	// loaded
	static {
		properties = new Properties();
		try (InputStream inputStream = App.class.getResourceAsStream(CONFIGURATION_FILE)) {
			properties.load(inputStream);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read file " + CONFIGURATION_FILE, e);
		}
	}

	public static void main(String[] args) {

		final Main server = new Main();

		// Setup VM shutdown hook.
		// Stop services gracefully on Ctrl+C or other signals.
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				server.stop();
			}
		});

		// Start all services and try to connect to the hardware.
		server.start();

		// Keep the server running forever.
		BufferedReader sysin = new BufferedReader(new InputStreamReader(System.in));
		try {
			while (true) {
				System.out.println("Type \"q\" or \"quit\" to stop the server.");
				// Stop it eventually if the user types one of the commands.
				// readLine blocks.
				String input = sysin.readLine();
				if (input == null || input.equals("q") || input.equals("quit") || input.equals("exit"))
					break;
			}
		} catch (IOException e) {
			// Ctrl+C anyone?
			// e.printStackTrace();
		} finally {

			// Cleanup!
			server.stop();
		}
	}

	public static String getConfigurationString(String key) {
		return properties.getProperty(key);
	}

	public static int getConfigurationInteger(String key) {
		return Integer.parseInt(properties.getProperty(key));
	}
}
