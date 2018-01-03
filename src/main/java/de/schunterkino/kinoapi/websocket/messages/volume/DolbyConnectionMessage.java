package de.schunterkino.kinoapi.websocket.messages.volume;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class DolbyConnectionMessage extends BaseMessage {
	public boolean connected;

	public DolbyConnectionMessage(boolean connected) {
		super("volume", "connection");
		this.connected = connected;
	}
}
