package de.schunterkino.kinoapi.sockets;

import java.io.IOException;

import purejavacomm.CommPort;
import purejavacomm.CommPortIdentifier;
import purejavacomm.NoSuchPortException;
import purejavacomm.PortInUseException;
import purejavacomm.SerialPort;
import purejavacomm.UnsupportedCommOperationException;

public class BaseSerialPortClient<T extends BaseCommands<S, V>, S, V> implements Runnable {
	private String portName;
	private String log_tag;
	private SerialPort serial;
	private T commands;
	private boolean stop;
	private boolean alreadyPrintedError;

	// Wait X seconds after a connection problem until trying again.
	private static int RECONNECT_TIME = 10;

	public BaseSerialPortClient(String portName, Class<T> typeArgumentClass) {
		this.portName = portName;
		this.log_tag = typeArgumentClass.getSimpleName();
		this.serial = null;
		try {
			this.commands = typeArgumentClass.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
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
				try {
					CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
					if (portIdentifier.isCurrentlyOwned())
						throw new IOException("Port " + portName + " is currently in use");

					int timeout = 2000;
					CommPort commPort = portIdentifier.open(this.getClass().getName(), timeout);

					if (!(commPort instanceof SerialPort)) {
						commPort.close();
						throw new IOException(portName + " is not a serial port.");
					}

					serial = (SerialPort) commPort;
					serial.setSerialPortParams(115200, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
							SerialPort.PARITY_NONE);
					// serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN
					// |
					// SerialPort.FLOWCONTROL_RTSCTS_OUT);

					// Start a thread to handle messages.
					commands.setSerialPort(serial);
					Thread readerThread = new Thread(commands);
					readerThread.start();

					System.out.printf("%s: Opened serial connection on %s.%n", log_tag, portName);

					// Print a reconnect error message next time again now that
					// we connected again.
					alreadyPrintedError = false;

					// Wait for the thread to be done.
					// The thread only terminates, if there is an issue with the
					// socket or we requested it to stop.
					readerThread.join();

				} catch (IOException | NoSuchPortException | PortInUseException | UnsupportedCommOperationException e) {
					// The readerThread will die on its own if it already
					// started.
					if (!stop && !alreadyPrintedError) {
						System.err.printf(
								"%s: Error in connection. Trying to reconnect every %d seconds. Exception: %s%n",
								log_tag, RECONNECT_TIME, e.getMessage());
						// Don't print the error again if the server stays down.
						alreadyPrintedError = true;
					}
				}
			} catch (InterruptedException e) {
				System.err.printf("%s: Error while waiting for reader thread: %s%n", log_tag, e.getMessage());
			}

			// Properly shutdown the client connection.
			if (isConnected()) {
				serial.close();
				System.out.printf("%s: Connection closed.%n", log_tag);
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
		return serial != null;
	}

	public T getCommands() {
		return commands;
	}

	public void stopServer() {
		stop = true;
		if (commands != null)
			commands.stop();

		if (serial != null) {
			serial.close();
			serial = null;
		}

	}
}
