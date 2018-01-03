package de.schunterkino.kinoapi.websocket.messages.christie;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class DouserChangedMessage extends BaseMessage {

	boolean is_open;

	public DouserChangedMessage(boolean isopen) {
		super("projector", "douser_changed");
		this.is_open = isopen;
	}
}
