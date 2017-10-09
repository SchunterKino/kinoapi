package de.schunterkino.kinoapi.sockets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import purejavacomm.SerialPort;

public class LineWrapper {

	private Socket socket;

	private SerialPort serial;

	public LineWrapper(Socket socket) {
		this.socket = socket;
		this.serial = null;
	}

	public LineWrapper(SerialPort serial) {
		this.socket = null;
		this.serial = serial;
	}

	public InputStream getInputStream() throws IOException {
		if (socket != null)
			return socket.getInputStream();
		if (serial != null)
			return serial.getInputStream();
		return null;
	}

	public OutputStream getOutputStream() throws IOException {
		if (socket != null)
			return socket.getOutputStream();
		if (serial != null)
			return serial.getOutputStream();
		return null;
	}

	public boolean isConnected() {
		if (socket != null)
			return socket.isConnected();
		if (serial != null)
			return true;
		return false;
	}
}
