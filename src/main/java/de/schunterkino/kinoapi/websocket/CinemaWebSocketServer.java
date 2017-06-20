package de.schunterkino.kinoapi.websocket;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.LinkedList;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import de.schunterkino.kinoapi.dolby.DolbySocketCommands;
import de.schunterkino.kinoapi.dolby.IDolbyStatusUpdateReceiver;
import de.schunterkino.kinoapi.jnior.IJniorStatusUpdateReceiver;
import de.schunterkino.kinoapi.jnior.JniorSocketCommands;
import de.schunterkino.kinoapi.sockets.BaseSocketServer;
import de.schunterkino.kinoapi.websocket.messages.BaseMessage;
import de.schunterkino.kinoapi.websocket.messages.DolbyConnectionMessage;
import de.schunterkino.kinoapi.websocket.messages.ErrorMessage;
import de.schunterkino.kinoapi.websocket.messages.LightsConnectionMessage;
import de.schunterkino.kinoapi.websocket.messages.MuteStatusChangedMessage;
import de.schunterkino.kinoapi.websocket.messages.VolumeChangedMessage;

public class CinemaWebSocketServer extends WebSocketServer
		implements IDolbyStatusUpdateReceiver, IJniorStatusUpdateReceiver {

	private Gson gson;
	private BaseSocketServer<DolbySocketCommands, IDolbyStatusUpdateReceiver> dolby;
	private BaseSocketServer<JniorSocketCommands, IJniorStatusUpdateReceiver> jnior;

	private LinkedList<IWebSocketMessageHandler> messageHandlers;

	public CinemaWebSocketServer(int port, BaseSocketServer<DolbySocketCommands, IDolbyStatusUpdateReceiver> dolby,
			BaseSocketServer<JniorSocketCommands, IJniorStatusUpdateReceiver> jnior) {
		super(new InetSocketAddress(port));

		this.gson = new Gson();
		this.messageHandlers = new LinkedList<>();

		// Start listening for dolby events.
		this.dolby = dolby;
		dolby.getCommands().registerListener(this);
		messageHandlers.add(dolby.getCommands());

		this.jnior = jnior;
		jnior.getCommands().registerListener(this);
		messageHandlers.add(jnior.getCommands());
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		System.out.println("WebSocket: " + conn.getRemoteSocketAddress().getAddress().getHostAddress() + " connected!");

		// Inform the new client of the current status.
		conn.send(gson.toJson(new DolbyConnectionMessage(dolby.isConnected())));
		if (dolby.isConnected()) {
			conn.send(gson.toJson(new VolumeChangedMessage(dolby.getCommands().getVolume())));
			conn.send(gson.toJson(new MuteStatusChangedMessage(dolby.getCommands().isMuted())));
		}

		conn.send(gson.toJson(new LightsConnectionMessage(jnior.isConnected())));
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

			try {
				for (IWebSocketMessageHandler handler : messageHandlers) {
					if (handler.onMessage(msg_type, message))
						return;
				}

			} catch (WebSocketCommandException e) {
				conn.send(gson.toJson(new ErrorMessage(e.getMessage())));
				return;
			}

			System.err.println("Websocket: Invalid command from " + conn + ": " + message);
			conn.send(gson.toJson(
					new ErrorMessage("Invalid command: " + msg_type.getMessageType() + " - " + msg_type.getAction())));
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

	@Override
	public void onMuteStatusChanged(boolean muted) {
		MuteStatusChangedMessage msg = new MuteStatusChangedMessage(muted);
		sendToAll(gson.toJson(msg));
	}

	@Override
	public void onJniorConnected() {
		LightsConnectionMessage msg = new LightsConnectionMessage(true);
		sendToAll(gson.toJson(msg));
	}

	@Override
	public void onJniorDisconnected() {
		LightsConnectionMessage msg = new LightsConnectionMessage(false);
		sendToAll(gson.toJson(msg));
	}
}
