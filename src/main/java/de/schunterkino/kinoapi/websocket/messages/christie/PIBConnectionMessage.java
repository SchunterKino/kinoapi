package de.schunterkino.kinoapi.websocket.messages.christie;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class PIBConnectionMessage extends BaseMessage {
	public boolean connected;

	public PIBConnectionMessage(boolean connected) {
		super("projector", "connection");
		this.connected = connected;
	}
}
