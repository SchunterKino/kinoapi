package de.schunterkino.kinoapi.websocket.messages.volume;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class VolumeChangedMessage extends BaseMessage {
	public int volume;

	public VolumeChangedMessage(int volume) {
		super("volume", "volume_changed");
		this.volume = volume;
	}
}
