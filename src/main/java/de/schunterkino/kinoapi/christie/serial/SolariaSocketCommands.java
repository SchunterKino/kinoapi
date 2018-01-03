package de.schunterkino.kinoapi.christie.serial;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

	// Abstraction for clients.
	// Separate lamp and IMB power.
	private PowerState powerState;
	private Instant powerStateChangedTimestamp;

	private LampState lampState;
	private LampState oldLampState;
	private Instant lampStateChangedTimestamp;

	// Get how long the lamp still needs to be cooled.
	private Pattern cooldownPattern;
	private Long cooldownTime;

	// Cache if the douser is currently open.
	private Pattern douserStatePattern;
	private boolean douserOpen;

	public SolariaSocketCommands() {
		super();

		powerModePattern = Pattern.compile("\\(PWR!([0-9]+) \"([^\"]*)\"\\)");
		powerMode = PowerMode.PowerOff;

		powerState = PowerState.Off;
		powerStateChangedTimestamp = null;
		lampState = oldLampState = LampState.Off;
		lampStateChangedTimestamp = null;

		cooldownPattern = Pattern.compile("\\(PWR\\+COOL!([0-9]+)\\)");
		cooldownTime = null;

		douserStatePattern = Pattern.compile("\\(SHU!([0-9]+)\\)");
		douserOpen = false;

		watchCommand(SolariaCommand.GetPowerStatus);
		watchCommand(SolariaCommand.GetDouserState);
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
						System.err.printf("%s: Received invalid power mode: %s \"%s\"%n", LOG_TAG, powerModeMatch,
								matcher.group(2));
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
		case GetDouserState:
			// Parse the response
			matcher = douserStatePattern.matcher(input);
			// Wait until we get the desired response.
			while (matcher.find()) {
				String state = matcher.group(1);
				if (state != null) {
					updateDouserState(Integer.parseInt(state) == 0);
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

	public PowerState getPowerState() {
		return powerState;
	}

	public Instant getPowerStateChangedTimestamp() {
		return powerStateChangedTimestamp;
	}

	public LampState getLampState() {
		return lampState;
	}

	public Instant getLampStateChangedTimestamp() {
		return lampStateChangedTimestamp;
	}

	public Long getCooldownTime() {
		// Always calculate the current cooldown time.
		if (cooldownTime == null)
			return null;

		// See how much time already elapsed since we noticed that the lamp started
		// cooling.
		long timeSinceCoolingStart = lampStateChangedTimestamp.until(Instant.now(), ChronoUnit.SECONDS);
		return Math.max(0, cooldownTime - timeSinceCoolingStart);
	}

	public boolean isDouserOpen() {
		return douserOpen;
	}

	private void updateCooldownTimer(long cooldown) {
		// Don't inform if the status didn't change.
		if (cooldownTime == cooldown)
			return;

		cooldownTime = cooldown;

		// Notify listeners.
		synchronized (listeners) {
			for (ISolariaSerialStatusUpdateReceiver listener : listeners) {
				listener.onLampStateChanged(lampState, oldLampState, lampStateChangedTimestamp, cooldown);
			}
		}
	}

	private void updatePowerMode(PowerMode mode) {
		// Don't inform if the status didn't change.
		if (powerMode == mode)
			return;

		powerMode = mode;

		// See if the power mode change involved the lamp.
		handleLampStateChange();

		// Update power state.
		handlePowerStateChange();
	}

	private void handlePowerStateChange() {
		switch (powerMode) {
		case PowerOff:
			powerState = PowerState.Off;
			break;
		case InWarmUp:
			powerState = PowerState.WarmingUp;
			break;
		case LampOff:
			powerState = PowerState.On;
			break;
		default:
			// Not a state we care about here.
			return;
		}

		// Reset cooldown time now that it's irrelevant.
		cooldownTime = null;

		powerStateChangedTimestamp = Instant.now();

		// Notify listeners.
		synchronized (listeners) {
			for (ISolariaSerialStatusUpdateReceiver listener : listeners) {
				listener.onPowerStateChanged(powerState, powerStateChangedTimestamp);
			}
		}
	}

	private void handleLampStateChange() {
		LampState oldLampState = lampState;
		switch (powerMode) {
		case LampOff:
			lampState = LampState.Off;
			break;
		case InCoolDown:
			lampState = LampState.Cooling;
			break;
		case LampOn:
			lampState = LampState.On;
			break;
		default:
			// Not a state we care about here.
			return;
		}

		this.oldLampState = oldLampState;
		lampStateChangedTimestamp = Instant.now();

		// We're waiting until we know the cooldown time from the second command.
		// The cooldown mode is special in that we ask for the remaining
		// cooldown time first.
		// Inform the listeners after we got the time it's still cooling.
		if (lampState == LampState.Cooling) {
			addCommand(SolariaCommand.GetCooldownTimer);
			return;
		}

		// Notify listeners.
		synchronized (listeners) {
			for (ISolariaSerialStatusUpdateReceiver listener : listeners) {
				listener.onLampStateChanged(lampState, oldLampState, lampStateChangedTimestamp, cooldownTime);
			}
		}
	}

	private void updateDouserState(boolean isopen) {
		// Don't inform if the status didn't change.
		if (douserOpen == isopen)
			return;

		douserOpen = isopen;

		// Notify listeners.
		synchronized (listeners) {
			for (ISolariaSerialStatusUpdateReceiver listener : listeners) {
				listener.onDouserStateChanged(douserOpen);
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
		case SetPowerStatus:
			command = "(PWR" + cmd.value + ")";
			break;
		case GetDouserState:
			command = "(SHU?)";
			break;
		case SetDouserState:
			command = "(SHU " + cmd.value + ")";
			break;
		}
		return command;
	}

	@Override
	public boolean onMessage(BaseMessage baseMsg, String message)
			throws WebSocketCommandException, JsonSyntaxException {

		// Handle all PIB related commands.
		if (!"projector".equals(baseMsg.getMessageType()))
			return false;

		switch (baseMsg.getAction()) {
		case "douser_open":
			if (socket.isConnected())
				addCommand(SolariaCommand.SetDouserState, 0, UseResponse.IgnoreResponse);
			else
				throw new WebSocketCommandException(
						"Failed to open the douser. No connection to Christie projector intelligence board.");
			return true;
		case "douser_close":
			if (socket.isConnected())
				addCommand(SolariaCommand.SetDouserState, 1, UseResponse.IgnoreResponse);
			else
				throw new WebSocketCommandException(
						"Failed to close the douser. No connection to Christie projector intelligence board.");
			return true;
		case "lamp_on":
			if (socket.isConnected()) {
				addCommand(SolariaCommand.SetPowerStatus, PowerMode.LampOn.ordinal(), UseResponse.IgnoreResponse);
				addCommand(SolariaCommand.GetPowerStatus);
			} else
				throw new WebSocketCommandException("Failed to turn lamp on. No connection to Christie projector.");
			return true;
		case "lamp_off":
			if (socket.isConnected()) {
				addCommand(SolariaCommand.SetPowerStatus, PowerMode.LampOff.ordinal(), UseResponse.IgnoreResponse);
				addCommand(SolariaCommand.GetPowerStatus);
			}
			else
				throw new WebSocketCommandException("Failed to turn lamp off. No connection to Christie projector.");
			return true;
		case "power_off":
			if (socket.isConnected()) {
				addCommand(SolariaCommand.SetPowerStatus, PowerMode.PowerOff.ordinal(), UseResponse.IgnoreResponse);
				addCommand(SolariaCommand.GetPowerStatus);
			} else
				throw new WebSocketCommandException(
						"Failed to power off the IMB. No connection to Christie projector.");
			return true;
		case "power_on":
			if (socket.isConnected()) {
				// Make sure we actually turn the IMB on and not the lamp off.
				if (powerMode != PowerMode.PowerOff)
					throw new WebSocketCommandException("The IMB is already on.");

				addCommand(SolariaCommand.SetPowerStatus, PowerMode.LampOff.ordinal(), UseResponse.IgnoreResponse);
				addCommand(SolariaCommand.GetPowerStatus);
			} else
				throw new WebSocketCommandException("Failed to power on the IMB. No connection to Christie projector.");
			return true;
		}

		return false;
	}
}
