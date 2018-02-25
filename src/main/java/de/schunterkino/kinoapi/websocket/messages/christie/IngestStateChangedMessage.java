package de.schunterkino.kinoapi.websocket.messages.christie;

import java.time.Instant;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class IngestStateChangedMessage extends BaseMessage {

	boolean is_ingesting;
	String timestamp;

	public IngestStateChangedMessage(boolean isingesting, Instant changeTime) {
		super("projector", "ingest_state_changed");
		this.is_ingesting = isingesting;
		
		// Only include this field if we have information to send.
		if (changeTime != null)
			this.timestamp = changeTime.toString();
		else
			this.timestamp = null;
	}
}
