package de.schunterkino.kinoapi.websocket.messages.volume;

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
		this.timestamp = modeChangeTime.toString();
		this.cooldown_time = cooldown;
	}
}
