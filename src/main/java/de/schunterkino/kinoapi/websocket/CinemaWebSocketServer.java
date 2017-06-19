package de.schunterkino.kinoapi.websocket;

import java.net.InetSocketAddress;
import java.util.Collection;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import de.schunterkino.kinoapi.dolby.DolbyWrapper;
import de.schunterkino.kinoapi.dolby.IDolbyStatusUpdateReceiver;
import de.schunterkino.kinoapi.websocket.messages.BaseMessage;
import de.schunterkino.kinoapi.websocket.messages.DolbyConnectionMessage;
import de.schunterkino.kinoapi.websocket.messages.ErrorMessage;
import de.schunterkino.kinoapi.websocket.messages.SetVolumeMessage;
import de.schunterkino.kinoapi.websocket.messages.VolumeChangedMessage;

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
		if (dolby.isConnected())
			conn.send(gson.toJson(new VolumeChangedMessage(dolby.getTelnetCommands().getVolume())));
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
			// TODO: Stop the application or try to start the server again in a
			// bit.
			System.err.println("WebSocket: ServerSocket error: " + ex.getMessage());
		}
	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		System.out.println("WebSocket: " + conn + ": " + message);

		try {
			BaseMessage msg_type = gson.fromJson(message, BaseMessage.class);

			switch (msg_type.getMessageType()) {
			case "set_volume":
				SetVolumeMessage setVolumeMsg = gson.fromJson(message, SetVolumeMessage.class);
				if (dolby.isConnected())
					dolby.getTelnetCommands().setVolume(setVolumeMsg.getVolume());
				else
					conn.send(gson.toJson(
							new ErrorMessage("Failed to change volume. No connection to Dolby audio processor.")));
				break;
			case "increase_volume":
				if (dolby.isConnected())
					dolby.getTelnetCommands().increaseVolume();
				else
					conn.send(gson.toJson(
							new ErrorMessage("Failed to increase volume. No connection to Dolby audio processor.")));

				break;
			case "decrease_volume":
				if (dolby.isConnected())
					dolby.getTelnetCommands().decreaseVolume();
				else
					conn.send(gson.toJson(
							new ErrorMessage("Failed to decrease volume. No connection to Dolby audio processor.")));

				break;
			default:
				System.err.println("Websocket: Invalid command from " + conn + ": " + message);
				conn.send(gson.toJson(new ErrorMessage("Invalid command: " + msg_type.getMessageType())));
			}
		} catch (JsonSyntaxException e) {
			System.err.println("Websocket: Error parsing message from " + conn + ": " + e.getMessage());
			conn.send(gson.toJson(new ErrorMessage(e.getMessage())));
		}
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
		VolumeChangedMessage msg = new VolumeChangedMessage(volume);
		sendToAll(gson.toJson(msg));
	}
}
