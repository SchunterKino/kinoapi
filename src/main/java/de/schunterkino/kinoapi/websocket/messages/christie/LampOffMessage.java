package de.schunterkino.kinoapi.websocket.messages.christie;

import java.time.Instant;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class LampOffMessage extends BaseMessage {
	public String timestamp;

	public LampOffMessage(Instant lampOffTime) {
		super("playback", "lamp_off");
		
		this.timestamp = lampOffTime.toString();
	}
}
