package de.schunterkino.kinoapi.websocket.messages.volume;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class SetDecodeModeMessage extends BaseMessage {

	private int mode;

	public SetDecodeModeMessage(int mode) {
		super("volume", "set_decode_mode");
		this.mode = mode;
	}

	public int getDecodeMode() {
		return mode;
	}
}
