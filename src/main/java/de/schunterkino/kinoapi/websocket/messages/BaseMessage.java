package de.schunterkino.kinoapi.websocket.messages;

public class BaseMessage {
	protected String msg_type;
	protected String action;

	public BaseMessage(String msg_type, String action) {
		this.msg_type = msg_type;
		this.action = action;
	}

	public String getMessageType() {
		return msg_type;
	}

	public String getAction() {
		return action;
	}
}
