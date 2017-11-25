package de.schunterkino.kinoapi.websocket.messages.christie;

import java.time.Instant;

import de.schunterkino.kinoapi.christie.serial.PowerMode;
import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class PowerModeChangedMessage extends BaseMessage {

	int mode;
	String timestamp;
	Integer cooldown_time;

	public PowerModeChangedMessage(PowerMode mode, Instant modeChangeTime, Integer cooldown) {
		super("playback", "power_mode_changed");
		this.mode = mode.ordinal();
		// Only include this field if we have information to send.
		if (modeChangeTime != null)
			this.timestamp = modeChangeTime.toString();
		else
			this.timestamp = null;
		this.cooldown_time = cooldown;
	}
}
