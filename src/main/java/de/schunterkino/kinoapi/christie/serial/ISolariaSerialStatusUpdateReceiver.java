package de.schunterkino.kinoapi.christie.serial;

import java.time.Instant;

public interface ISolariaSerialStatusUpdateReceiver {

	public void onPowerModeChanged(PowerMode mode, PowerMode oldMode, Instant timestamp, Integer cooldown);
	public void onDouserStateChanged(boolean isopen);
}
