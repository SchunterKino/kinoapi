package de.schunterkino.kinoapi.websocket.messages;

public class LightsConnectionMessage extends BaseMessage {
	public boolean connected;

	public LightsConnectionMessage(boolean connected) {
		super("lights", "lights_connection");
		this.connected = connected;
	}
}
