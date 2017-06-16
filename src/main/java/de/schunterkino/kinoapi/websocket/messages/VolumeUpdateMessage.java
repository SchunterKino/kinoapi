package de.schunterkino.kinoapi.websocket.messages;

public class VolumeUpdateMessage extends BaseMessage {
	public int volume;

	public VolumeUpdateMessage(int volume) {
		super("volume_update");
		this.volume = volume;
	}
}
