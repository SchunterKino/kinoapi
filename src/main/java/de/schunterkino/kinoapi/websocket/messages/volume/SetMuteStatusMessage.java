package de.schunterkino.kinoapi.websocket.messages.volume;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class SetMuteStatusMessage extends BaseMessage {
	private boolean muted;

	public SetMuteStatusMessage() {
		super("volume", "set_mute_status");
	}

	public boolean isMuted() {
		return muted;
	}
}
