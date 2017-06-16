package de.schunterkino.kinoapi.websocket.messages;

public class DolbyConnectionMessage extends BaseMessage {
	public boolean connected;

	public DolbyConnectionMessage(boolean connected) {
		super("dolby_connection");
		this.connected = connected;
	}
}
