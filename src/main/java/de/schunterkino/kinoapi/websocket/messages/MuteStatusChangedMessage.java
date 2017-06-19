package de.schunterkino.kinoapi.websocket.messages;

public class MuteStatusChangedMessage extends BaseMessage {

	public boolean muted;

	public MuteStatusChangedMessage(boolean muted) {
		super("volume", "mute_status_changed");

		this.muted = muted;
	}

}
