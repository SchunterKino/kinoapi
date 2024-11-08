package de.schunterkino.kinoapi.sockets;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

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
			this.commands = typeArgumentClass.getDeclaredConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
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
				serial.enableReceiveTimeout(timeout);
				// serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN
				// |
				// SerialPort.FLOWCONTROL_RTSCTS_OUT);

				System.out.printf("%s: Opened serial connection on %s.%n", log_tag, portName);

				// Start to handle messages.
				commands.setSerialPort(serial);

				// Block until the socket to is done.
				// This only returns if there is an issue with the
				// socket or we requested it to stop.
				commands.processSocket();

				// Print a reconnect error message next time again now that
				// we connected again.
				alreadyPrintedError = false;

			} catch (IOException | NoSuchPortException | PortInUseException | UnsupportedCommOperationException e) {
				// The readerThread will die on its own if it already
				// started.
				if (!stop && !alreadyPrintedError) {
					System.err.printf("%s: Error in connection. Trying to reconnect every %d seconds. Exception: %s%n",
							log_tag, RECONNECT_TIME, e.getMessage());
					// Don't print the error again if the server stays down.
					alreadyPrintedError = true;
				}
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
		if (stop)
			return;

		stop = true;
		if (commands != null)
			commands.stop();

		if (serial != null) {
			serial.close();
			serial = null;
		}

	}
}
