package de.schunterkino.kinoapi.websocket;

import java.net.InetSocketAddress;
import java.util.Collection;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.Gson;

import de.schunterkino.kinoapi.dolby.IDolbyStatusUpdateReceiver;
import de.schunterkino.kinoapi.dolby.DolbyWrapper;
import de.schunterkino.kinoapi.websocket.messages.DolbyConnectionMessage;
import de.schunterkino.kinoapi.websocket.messages.VolumeUpdateMessage;

public class CinemaWebSocketServer extends WebSocketServer implements IDolbyStatusUpdateReceiver {

	private Gson gson;
	private DolbyWrapper dolby;

	public CinemaWebSocketServer(int port, DolbyWrapper dolby) {
		super(new InetSocketAddress(port));

		this.gson = new Gson();

		// Start listening for dolby events.
		this.dolby = dolby;
		dolby.getTelnetCommands().registerListener(this);
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		System.out.println("WebSocket: " + conn.getRemoteSocketAddress().getAddress().getHostAddress() + " connected!");

		// Inform the new client of the current status.
		conn.send(gson.toJson(new DolbyConnectionMessage(dolby.isConnected())));
		conn.send(gson.toJson(new VolumeUpdateMessage(dolby.getTelnetCommands().getVolume())));
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		System.out.println("WebSocket: " + conn + " disconnected!");
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
		if (conn != null) {
			// some errors like port binding failed may not be assignable to a
			// specific websocket
			System.err.println("WebSocket: ChildSocket " + conn + " error: " + ex.getMessage());
		} else {
			System.err.println("WebSocket: ServerSocket error: " + ex.getMessage());
		}
	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		System.out.println("WebSocket: " + conn + ": " + message);

	}

	@Override
	public void onStart() {
		System.out.println("Websocket server started!");
	}

	/**
	 * Sends <var>text</var> to all currently connected WebSocket clients.
	 * 
	 * @param text
	 *            The String to send across the network.
	 * @throws InterruptedException
	 *             When socket related I/O errors occur.
	 */
	public void sendToAll(String text) {
		Collection<WebSocket> con = connections();
		synchronized (con) {
			for (WebSocket c : con) {
				c.send(text);
			}
		}
	}

	@Override
	public void onDolbyConnected() {
		DolbyConnectionMessage msg = new DolbyConnectionMessage(true);
		sendToAll(gson.toJson(msg));
	}

	@Override
	public void onDolbyDisconnected() {
		DolbyConnectionMessage msg = new DolbyConnectionMessage(false);
		sendToAll(gson.toJson(msg));
	}

	@Override
	public void onVolumeChanged(int volume) {
		VolumeUpdateMessage msg = new VolumeUpdateMessage(volume);
		sendToAll(gson.toJson(msg));
	}
}
