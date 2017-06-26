package de.schunterkino.kinoapi.websocket.messages;

public class SetInputModeMessage extends BaseMessage {

	private int mode;

	public SetInputModeMessage(int mode) {
		super("volume", "set_input_mode");
		this.mode = mode;
	}

	public int getInputMode() {
		return mode;
	}
}
