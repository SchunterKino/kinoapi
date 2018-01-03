package de.schunterkino.kinoapi.websocket.messages.christie;

import java.time.Instant;

import de.schunterkino.kinoapi.christie.serial.PowerState;
import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class PowerChangedMessage extends BaseMessage {

	int state;
	String timestamp;

	public PowerChangedMessage(PowerState state, Instant changeTime) {
		super("projector", "power_changed");
		this.state = state.ordinal();
		// Only include this field if we have information to send.
		if (changeTime != null)
			this.timestamp = changeTime.toString();
		else
			this.timestamp = null;
	}
}
