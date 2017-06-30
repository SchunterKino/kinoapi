package de.schunterkino.kinoapi.websocket.messages.volume;

import de.schunterkino.kinoapi.dolby.DecodeMode;
import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class DecodeModeChangedMessage extends BaseMessage {

	int mode;

	public DecodeModeChangedMessage(DecodeMode mode) {
		super("volume", "decode_mode_changed");
		this.mode = mode.ordinal();
	}
}
