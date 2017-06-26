package de.schunterkino.kinoapi.websocket.messages;

import de.schunterkino.kinoapi.dolby.InputMode;

public class InputModeChangedMessage extends BaseMessage {

	int mode;

	public InputModeChangedMessage(InputMode mode) {
		super("volume", "input_mode_changed");
		this.mode = mode.ordinal();
	}
}
