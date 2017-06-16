package de.schunterkino.kinoapi.websocket.messages;

public abstract class BaseMessage {
	protected String msg_type;

	protected BaseMessage(String msg_type) {
		this.msg_type = msg_type;
	}
}
