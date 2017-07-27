package de.schunterkino.kinoapi.websocket.messages.christie;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class LampOffMessage extends BaseMessage {
	public String timestamp;

	public LampOffMessage(Instant lampOffTime) {
		super("playback", "lamp_off");

		DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.systemDefault());
		this.timestamp = formatter.format(lampOffTime);
	}
}
