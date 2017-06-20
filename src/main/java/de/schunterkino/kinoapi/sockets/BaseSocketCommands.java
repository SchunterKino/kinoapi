package de.schunterkino.kinoapi.sockets;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.LinkedList;

import com.google.gson.Gson;

import de.schunterkino.kinoapi.websocket.IWebSocketMessageHandler;

public abstract class BaseSocketCommands<T> implements Runnable, IWebSocketMessageHandler {

	protected Socket socket;
	protected boolean stop;
	protected LinkedList<T> listeners;
	protected Gson gson;

	protected BaseSocketCommands() {
		this.socket = null;
		this.stop = false;
		this.listeners = new LinkedList<>();
		this.gson = new Gson();
	}

	public void stop() {
		stop = true;
	}

	public void setSocket(Socket socket) {
		this.socket = socket;
	}

	public void registerListener(T listener) {
		synchronized (listeners) {
			listeners.add(listener);
		}
	}
	
	protected String read() throws IOException {
		InputStream in = socket.getInputStream();
		byte[] buffer = new byte[1024];

		int ret_read = in.read(buffer);
		if (ret_read == -1)
			throw new IOException("EOF");

		if (ret_read == 0)
			return null;

		return new String(buffer, 0, ret_read);
	}
}
