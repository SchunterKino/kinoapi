package de.schunterkino.kinoapi.websocket.messages;

public class ErrorMessage extends BaseMessage {

	private String error;

	public ErrorMessage(String error) {
		super("error", "error");
		this.error = error;
	}

	public String getErrorMessage() {
		return error;
	}
}
