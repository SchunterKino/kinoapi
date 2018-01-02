package de.schunterkino.kinoapi.websocket.messages.christie;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class SetPowerModeMessage extends BaseMessage {

	private int mode;

	public SetPowerModeMessage(int mode) {
		super("playback", "set_power_mode");
		this.mode = mode;
	}

	public int getPowerMode() {
		return mode;
	}
}
