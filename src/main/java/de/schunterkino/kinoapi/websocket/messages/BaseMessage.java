package de.schunterkino.kinoapi.websocket.messages;

public class BaseMessage {
	protected String msg_type;

	public BaseMessage(String msg_type) {
		this.msg_type = msg_type;
	}

	public String getMessageType() {
		return msg_type;
	}
}
