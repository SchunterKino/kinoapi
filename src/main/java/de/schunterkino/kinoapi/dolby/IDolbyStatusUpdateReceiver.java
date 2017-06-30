package de.schunterkino.kinoapi.dolby;

public interface IDolbyStatusUpdateReceiver {
	void onDolbyConnected();

	void onDolbyDisconnected();

	void onVolumeChanged(int volume);

	void onMuteStatusChanged(boolean muted);

	void onInputModeChanged(InputMode mode);

	void onDecodeModeChanged(DecodeMode mode);
}
