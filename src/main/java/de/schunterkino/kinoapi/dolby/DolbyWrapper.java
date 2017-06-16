package de.schunterkino.kinoapi.dolby;

import java.io.IOException;

import org.apache.commons.net.telnet.EchoOptionHandler;
import org.apache.commons.net.telnet.InvalidTelnetOptionException;
import org.apache.commons.net.telnet.SuppressGAOptionHandler;
import org.apache.commons.net.telnet.TelnetClient;
import org.apache.commons.net.telnet.TelnetNotificationHandler;
import org.apache.commons.net.telnet.TerminalTypeOptionHandler;

public class DolbyWrapper implements Runnable, TelnetNotificationHandler {

	private String dolbyip;
	private int dolbyport;
	private TelnetClient telnetClient;
	private DolbyTelnetCommands telnetCommands;
	private boolean connected;
	private boolean stop;

	public DolbyWrapper(String ip, int port) {
		dolbyip = ip;
		dolbyport = port;
		connected = false;
		stop = false;

		// Setup the telnet connection.
		telnetClient = new TelnetClient();
		telnetClient.setDefaultTimeout(5000);
		telnetCommands = new DolbyTelnetCommands(telnetClient);

		TerminalTypeOptionHandler ttopt = new TerminalTypeOptionHandler("VT100", false, false, true, false);
		EchoOptionHandler echoopt = new EchoOptionHandler(true, false, true, false);
		SuppressGAOptionHandler gaopt = new SuppressGAOptionHandler(true, true, true, true);

		try {
			telnetClient.addOptionHandler(ttopt);
			telnetClient.addOptionHandler(echoopt);
			telnetClient.addOptionHandler(gaopt);
		} catch (InvalidTelnetOptionException | IOException e) {
			System.err.println("Error registering option handlers: " + e.getMessage());
		}
	}

	@Override
	public void run() {
		// Connect! And keep trying to connect too.
		while (!stop) {
			try {
				try {
					telnetClient.connect(dolbyip, dolbyport);
					telnetClient.registerNotifHandler(this);

					// Start a thread to handle telnet messages.
					telnetCommands.reset();
					Thread readerThread = new Thread(telnetCommands);
					readerThread.start();

					System.out.printf("Dolby: Connected to %s:%d.%n", dolbyip, dolbyport);
					connected = true;

					// Wait for the thread to be done.
					// The thread only terminates, if there is an issue with the
					// socket or we requested it to stop.
					readerThread.join();

				} catch (IOException e) {
					// The readerThread will die on its own if it already
					// started.
					if (!stop)
						System.err.println("Error in dolby telnet connection. Reconnecting in 30 seconds. Exception: "
								+ e.getMessage());
				}
			} catch (InterruptedException e) {
				System.err.println("Error while waiting for dolby reader thread: " + e.getMessage());
			}

			// Properly shutdown the telnet client connection.
			if (telnetClient.isConnected()) {
				try {
					telnetClient.disconnect();
					System.out.println("Dolby: Connection closed.");
				} catch (IOException e) {
					System.err.println("Error while closing dolby telnet connection: " + e.getMessage());
				}
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

	/***
	 * Callback method called when TelnetClient receives an option negotiation
	 * command.
	 *
	 * @param negotiation_code
	 *            - type of negotiation command received (RECEIVED_DO,
	 *            RECEIVED_DONT, RECEIVED_WILL, RECEIVED_WONT, RECEIVED_COMMAND)
	 * @param option_code
	 *            - code of the option negotiated
	 ***/
	@Override
	public void receivedNegotiation(int negotiation_code, int option_code) {
		String command = null;
		switch (negotiation_code) {
		case TelnetNotificationHandler.RECEIVED_DO:
			command = "DO";
			break;
		case TelnetNotificationHandler.RECEIVED_DONT:
			command = "DONT";
			break;
		case TelnetNotificationHandler.RECEIVED_WILL:
			command = "WILL";
			break;
		case TelnetNotificationHandler.RECEIVED_WONT:
			command = "WONT";
			break;
		case TelnetNotificationHandler.RECEIVED_COMMAND:
			command = "COMMAND";
			break;
		default:
			command = Integer.toString(negotiation_code); // Should not happen
			break;
		}
		System.out.println("Dolby: Received " + command + " for option code " + option_code);
	}

	public boolean isConnected() {
		return connected;
	}

	public DolbyTelnetCommands getTelnetCommands() {
		return telnetCommands;
	}

	public void stopServer() {
		stop = true;
		if (telnetCommands != null)
			telnetCommands.stop();
		try {
			if (telnetClient != null)
				telnetClient.disconnect();
		} catch (IOException e) {
			// Who cares.
		}
		if (!connected)
			Thread.currentThread().interrupt();
	}
}
