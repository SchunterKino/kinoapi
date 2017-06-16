package de.schunterkino.kinoapi.dolby;

public interface IDolbyStatusUpdateReceiver {
	void onDolbyConnected();

	void onDolbyDisconnected();

	void onVolumeChanged(int volume);
}
