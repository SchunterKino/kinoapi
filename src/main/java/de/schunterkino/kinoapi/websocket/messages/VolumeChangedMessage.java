package de.schunterkino.kinoapi.websocket.messages;

public class VolumeChangedMessage extends BaseMessage {
	public int volume;

	public VolumeChangedMessage(int volume) {
		super("volume_changed");
		this.volume = volume;
	}
}
