package de.schunterkino.kinoapi.websocket.messages.volume;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class SetVolumeMessage extends BaseMessage {

	private int volume;

	public SetVolumeMessage() {
		super("volume", "set_volume");
	}

	public int getVolume() {
		return volume;
	}
}
