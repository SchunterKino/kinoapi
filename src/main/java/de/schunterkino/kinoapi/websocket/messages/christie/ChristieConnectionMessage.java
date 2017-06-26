package de.schunterkino.kinoapi.websocket.messages.christie;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class ChristieConnectionMessage extends BaseMessage {
	public boolean connected;

	public ChristieConnectionMessage(boolean connected) {
		super("playback", "projector_connection");
		this.connected = connected;
	}
}
