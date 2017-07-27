package de.schunterkino.kinoapi.listen;

import java.time.Instant;

public interface IServerSocketStatusUpdateReceiver {
	void onLampTurnedOff(Instant lampOffTime);
}
