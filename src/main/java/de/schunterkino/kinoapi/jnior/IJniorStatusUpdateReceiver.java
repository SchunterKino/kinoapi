package de.schunterkino.kinoapi.jnior;

public interface IJniorStatusUpdateReceiver {
	void onJniorConnected();

	void onJniorDisconnected();
}
