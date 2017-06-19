package de.schunterkino.kinoapi.websocket.messages;

public class SetVolumeMessage extends BaseMessage {

	private int volume;

	public SetVolumeMessage() {
		super("volume", "set_volume");
	}

	public int getVolume() {
		return volume;
	}
}
