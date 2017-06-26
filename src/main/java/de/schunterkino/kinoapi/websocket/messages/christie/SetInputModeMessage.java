package de.schunterkino.kinoapi.websocket.messages.christie;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class SetInputModeMessage extends BaseMessage {

	private String mode;

	public SetInputModeMessage(String mode) {
		super("playback", "set_input_mode");
		this.mode = mode;
	}

	public String getInputMode() {
		return mode;
	}
}
