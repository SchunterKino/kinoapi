package de.schunterkino.kinoapi.christie.serial;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonSyntaxException;

import de.schunterkino.kinoapi.sockets.BaseCommands;
import de.schunterkino.kinoapi.sockets.CommandContainer;
import de.schunterkino.kinoapi.websocket.WebSocketCommandException;
import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class SolariaSocketCommands extends BaseCommands<ISolariaSerialStatusUpdateReceiver, SolariaCommand> {

	protected int UPDATE_INTERVAL = 1000;

	// Power mode
	// This list must match the PowerMode enum.
	private static final List<Integer> powerModeNames = Arrays.asList(0, 1, 2, 3, 10, 11);
	private Pattern powerModePattern;
	private PowerMode oldPowerMode;
	private PowerMode powerMode;

	private Pattern cooldownPattern;
	private Integer cooldownTime;

	private Instant powerModeChangedTimestamp;

	public SolariaSocketCommands() {
		super();

		powerModePattern = Pattern.compile("\\(PWR!([0-9]+) \"([^\"]*)\"\\)");
		powerMode = oldPowerMode = PowerMode.PowerOff;

		cooldownPattern = Pattern.compile("\\(PWR\\+COOL!([0-9]+)\\)");
		cooldownTime = null;
		powerModeChangedTimestamp = null;

		watchCommand(SolariaCommand.GetPowerStatus);
	}

	@Override
	protected void onSocketConnected() {
	}

	@Override
	protected void onSocketDisconnected() {
	}

	@Override
	protected boolean onReceiveCommandOutput(String input) {
		boolean handled = false;
		Matcher matcher;
		switch (getCurrentCommand().cmd) {
		case GetPowerStatus:
			// Parse the response
			matcher = powerModePattern.matcher(input);
			// Wait until we get the desired response.
			while (matcher.find()) {
				String powerModeMatch = matcher.group(1);
				if (powerModeMatch != null) {
					int powerMode = Integer.parseInt(powerModeMatch);
					int ordPowerMode = powerModeNames.indexOf(powerMode);
					if (ordPowerMode != -1) {
						updatePowerMode(PowerMode.values()[ordPowerMode]);
					} else {
						System.err.printf("%s: Received invalid power mode: %s \"%s\"%n", LOG_TAG, powerModeMatch, matcher.group(2));
					}
					handled = true;
				}
			}
			break;
		case GetCooldownTimer:
			// Parse the response
			matcher = cooldownPattern.matcher(input);
			// Wait until we get the desired response.
			while (matcher.find()) {
				String cooldown = matcher.group(1);
				if (cooldown != null) {
					updateCooldownTimer(Integer.parseInt(cooldown));
					handled = true;
				}
			}
			break;
		default:
			handled = true;
			break;
		}
		return handled;
	}

	public PowerMode getPowerMode() {
		return powerMode;
	}

	public Instant getPowerModeChangedTimestamp() {
		return powerModeChangedTimestamp;
	}

	public Integer getCooldownTime() {
		return cooldownTime;
	}

	private void updateCooldownTimer(int cooldown) {
		// Don't inform if the status didn't change.
		if (cooldownTime == cooldown)
			return;

		cooldownTime = cooldown;

		// Notify listeners.
		synchronized (listeners) {
			for (ISolariaSerialStatusUpdateReceiver listener : listeners) {
				listener.onPowerModeChanged(powerMode, oldPowerMode, powerModeChangedTimestamp, cooldown);
			}
		}
	}

	private void updatePowerMode(PowerMode mode) {
		// Don't inform if the status didn't change.
		if (powerMode == mode)
			return;

		oldPowerMode = powerMode;
		powerMode = mode;
		powerModeChangedTimestamp = Instant.now();

		// The cooldown mode is special in that we ask for the remaining
		// cooldown time first.
		// Inform the listeners after we got the time it's still cooling.
		if (powerMode == PowerMode.InCoolDown) {
			if (oldPowerMode != PowerMode.InCoolDown)
				addCommand(SolariaCommand.GetCooldownTimer);
		} else {
			// Reset cooldown time now that it's irrelevant.
			cooldownTime = null;

			// Notify listeners.
			synchronized (listeners) {
				for (ISolariaSerialStatusUpdateReceiver listener : listeners) {
					listener.onPowerModeChanged(mode, oldPowerMode, powerModeChangedTimestamp, null);
				}
			}
		}
	}

	@Override
	protected String getCommandString(CommandContainer<SolariaCommand> cmd) {
		String command = null;
		switch (cmd.cmd) {
		case GetPowerStatus:
			command = "(PWR?)";
			break;
		case GetCooldownTimer:
			command = "(PWR+COOL?)";
			break;
		}
		return command;
	}

	@Override
	public boolean onMessage(BaseMessage baseMsg, String message)
			throws WebSocketCommandException, JsonSyntaxException {

		return false;
	}

}
