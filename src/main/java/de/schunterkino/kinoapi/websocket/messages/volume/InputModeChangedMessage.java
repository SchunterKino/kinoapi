package de.schunterkino.kinoapi.websocket.messages.volume;

import de.schunterkino.kinoapi.dolby.InputMode;
import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class InputModeChangedMessage extends BaseMessage {

	int mode;

	public InputModeChangedMessage(InputMode mode) {
		super("volume", "input_mode_changed");
		this.mode = mode.ordinal();
	}
}
