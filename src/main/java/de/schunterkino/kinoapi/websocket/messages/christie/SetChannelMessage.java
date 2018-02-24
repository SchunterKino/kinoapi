package de.schunterkino.kinoapi.websocket.messages.christie;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class SetChannelMessage extends BaseMessage {

	private int channel;

	public SetChannelMessage(int channel) {
		super("projector", "set_channel");
		this.channel = channel;
	}

	public int getChannel() {
		return channel;
	}
}
