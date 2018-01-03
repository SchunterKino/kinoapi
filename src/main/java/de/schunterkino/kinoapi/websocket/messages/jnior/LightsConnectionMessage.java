package de.schunterkino.kinoapi.websocket.messages.jnior;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class LightsConnectionMessage extends BaseMessage {
	public boolean connected;

	public LightsConnectionMessage(boolean connected) {
		super("lights", "connection");
		this.connected = connected;
	}
}
