package de.schunterkino.kinoapi.websocket.messages.christie;

import de.schunterkino.kinoapi.christie.serial.ChannelType;
import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class ActiveChannelChangedMessage extends BaseMessage {

	int channel;

	public ActiveChannelChangedMessage(ChannelType channel) {
		super("projector", "channel_changed");
		this.channel = channel.ordinal();
	}
}
