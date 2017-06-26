package de.schunterkino.kinoapi.christie;

public interface IChristieStatusUpdateReceiver {
	public void onChristieConnected();

	public void onChristieDisconnected();
}
