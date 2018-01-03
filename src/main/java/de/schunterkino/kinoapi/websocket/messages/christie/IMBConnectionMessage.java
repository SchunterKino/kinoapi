package de.schunterkino.kinoapi.websocket.messages.christie;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class IMBConnectionMessage extends BaseMessage {
	public boolean connected;

	public IMBConnectionMessage(boolean connected) {
		super("playback", "connection");
		this.connected = connected;
	}
}
