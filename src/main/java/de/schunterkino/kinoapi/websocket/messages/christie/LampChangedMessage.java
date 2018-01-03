package de.schunterkino.kinoapi.websocket.messages.christie;

import java.time.Instant;

import de.schunterkino.kinoapi.christie.serial.LampState;
import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class LampChangedMessage extends BaseMessage {

	boolean is_on;
	String timestamp;
	Long cooldown;

	public LampChangedMessage(LampState state, Instant changeTime, Long cooldown) {
		super("projector", "lamp_changed");
		this.is_on = state == LampState.On;
		// Only include this field if we have information to send.
		if (changeTime != null)
			this.timestamp = changeTime.toString();
		else
			this.timestamp = null;
		this.cooldown = cooldown;
	}
}
