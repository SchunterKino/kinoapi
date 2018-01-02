package de.schunterkino.kinoapi.websocket.messages.christie;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class DouserChangedMessage extends BaseMessage {

	boolean isopen;

	public DouserChangedMessage(boolean isopen) {
		super("playback", "douser_changed");
		this.isopen = isopen;
	}
}
