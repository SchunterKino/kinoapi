package de.schunterkino.kinoapi.christie.serial;

import java.time.Instant;

public interface ISolariaSerialStatusUpdateReceiver {
	public void onSolariaConnected();

	public void onSolariaDisconnected();

	public void onPowerStateChanged(PowerState state, Instant timestamp);

	public void onLampStateChanged(LampState state, LampState oldState, Instant timestamp, Long cooldown);

	public void onDouserStateChanged(boolean isopen);
}
