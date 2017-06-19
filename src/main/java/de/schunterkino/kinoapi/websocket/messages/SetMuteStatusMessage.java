package de.schunterkino.kinoapi.websocket.messages;

public class SetMuteStatusMessage extends BaseMessage {
	private boolean muted;

	public SetMuteStatusMessage() {
		super("volume", "set_mute_status");
	}

	public boolean isMuted() {
		return muted;
	}
}
