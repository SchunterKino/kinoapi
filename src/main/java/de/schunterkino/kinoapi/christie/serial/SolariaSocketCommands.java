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
import de.schunterkino.kinoapi.websocket.messages.christie.SetChannelMessage;

public class SolariaSocketCommands extends BaseCommands<ISolariaSerialStatusUpdateReceiver, SolariaCommand> {

	protected int UPDATE_INTERVAL = 1000;

	private Pattern errorPattern;

	// Power mode
	// This list must match the PowerMode enum.
	private static final List<Integer> powerModeNames = Arrays.asList(0, 1, 2, 3, 10, 11, -1);
	private Pattern powerModePattern;
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

	// Which image source is active?
	private Pattern activeChannelPattern;
	private int activeChannelIndex;
	private ChannelType activeChannel;
	// This list must match the ChannelType enum.
	private static final List<Integer> channelMapping = Arrays.asList(-1, 101, 102, 109, 110);

	private Pattern ingestStatePattern;
	private boolean isIngesting;
	private Instant ingsetStateChangedTimestamp;

	public SolariaSocketCommands() {
		super();

		// General pattern for errors with well-formed commands.
		errorPattern = Pattern.compile("\\([0-9]+ [0-9]+ ERR([0-9]+) \"([^\"]+)\"\\)");

		powerModePattern = Pattern.compile("\\(PWR\\+STAT!([0-9]+) \"([^\"]*)\"\\)");
		powerMode = PowerMode.Unknown;

		powerState = PowerState.Off;
		powerStateChangedTimestamp = null;
		lampState = oldLampState = LampState.Off;
		lampStateChangedTimestamp = null;

		cooldownPattern = Pattern.compile("\\(PWR\\+COOL!([0-9]+)\\)");
		cooldownTime = null;

		douserStatePattern = Pattern.compile("\\(SHU!([0-9]+)\\)");
		douserOpen = false;

		// Channels are only valid from 101-164.
		// Initialize with an invalid number to know that we don't know yet.
		activeChannelPattern = Pattern.compile("\\(CHA!([0-9]+)\\)");
		activeChannelIndex = -1;
		activeChannel = ChannelType.Unknown;

		ingestStatePattern = Pattern.compile("\\(PWR\\+IGST!([0-9]+)\\)");
		isIngesting = false;
		ingsetStateChangedTimestamp = null;

		watchCommand(SolariaCommand.GetPowerStatus);
		watchCommand(SolariaCommand.GetDouserState);
		watchCommand(SolariaCommand.GetActiveChannel);
		watchCommand(SolariaCommand.GetIngestState);
	}

	@Override
	protected void onSocketConnected() {
		// Notify listeners.
		synchronized (listeners) {
			for (ISolariaSerialStatusUpdateReceiver listener : listeners) {
				listener.onSolariaConnected();
			}
		}
	}

	@Override
	protected void onSocketDisconnected() {
		// Notify listeners.
		synchronized (listeners) {
			for (ISolariaSerialStatusUpdateReceiver listener : listeners) {
				listener.onSolariaDisconnected();
			}
		}
	}

	@Override
	protected boolean onReceiveCommandOutput(String input) {
		boolean handled = false;
		Matcher matcher;

		// See if we got an error as response.
		// Just handle and ignore the error message and move on to the next command in
		// the queue.
		matcher = errorPattern.matcher(input);
		if (matcher.find()) {
			// We expect to get errors when asking for the current channel while IMB is
			// powered off.
			if (getCurrentCommand().cmd != SolariaCommand.GetActiveChannel)
				System.err.printf("Error response for command %s: %s%n", getCurrentCommand().cmd, matcher.group());
			return true;
		}

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
					updateCooldownTimer(Long.parseLong(cooldown));
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
		case GetActiveChannel:
			// Parse the response
			matcher = activeChannelPattern.matcher(input);
			// Wait until we get the desired response.
			while (matcher.find()) {
				String channel = matcher.group(1);
				if (channel != null) {
					updateActiveChannel(Integer.parseInt(channel));
					handled = true;
				}
			}
			break;
		case GetIngestState:
			// Parse the response
			matcher = ingestStatePattern.matcher(input);
			// Wait until we get the desired response.
			while (matcher.find()) {
				String ingesting = matcher.group(1);
				if (ingesting != null) {
					updateIngestState(Integer.parseInt(ingesting) == 1);
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

	public ChannelType getActiveChannel() {
		return activeChannel;
	}

	public boolean isIngesting() {
		return isIngesting;
	}

	public Instant getIngestStateChangedTimestamp() {
		return ingsetStateChangedTimestamp;
	}

	private void updateCooldownTimer(Long cooldown) {
		// Don't inform if the status didn't change.
		if (cooldownTime == cooldown)
			return;

		cooldownTime = cooldown;

		// Notify listeners.
		synchronized (listeners) {
			for (ISolariaSerialStatusUpdateReceiver listener : listeners) {
				listener.onLampStateChanged(lampState, oldLampState, lampStateChangedTimestamp, cooldownTime);
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
		PowerState oldPowerState = powerState;
		switch (powerMode) {
		case PowerOff:
			powerState = PowerState.Off;
			break;
		case InWarmUp:
			powerState = PowerState.WarmingUp;
			break;
		case LampOff:
		case InCoolDown:
		case LampOn:
			powerState = PowerState.On;
			break;
		default:
			// Not a state we care about here.
			return;
		}

		// Don't notify anyone if the state didn't really change for us.
		if (powerState == oldPowerState && powerStateChangedTimestamp != null)
			return;

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
		case PowerOff:
		case InWarmUp:
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

		// The state didn't change (like PowerOff and LampOff still off)
		if (lampState == oldLampState && lampStateChangedTimestamp != null)
			return;

		// Can't change from On to Off without cooling the lamp.
		// The projector switches the state prematurely and changes to "Cooling"
		// right after. Ignore the first message and wait for the "Cooling" one instead.
		if (lampState == LampState.Off && oldLampState == LampState.On)
			return;

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

		// Reset cooldown time now that it's irrelevant.
		cooldownTime = null;

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

	private void updateActiveChannel(int channel) {
		if (activeChannelIndex == channel)
			return;

		// (NAM+CALL?)
		// 101 - Flat
		// 102 - Scope
		// 109 - DVI A Scaler Flat
		// 110 - DVI A Scaler Scope
		// 111 - DVI B Flat
		// 112 - DVI B Scope
		activeChannelIndex = channel;

		// See which channel this is and map it to our enum.
		int ordActiveChannel = channelMapping.indexOf(channel);
		// Any other channel not in our enum is just unknown.
		if (ordActiveChannel == -1)
			activeChannel = ChannelType.Unknown;
		else
			activeChannel = ChannelType.values()[ordActiveChannel];

		// TODO: Get name of the channel using (NAM+C1XX?) or cache all names using
		// (NAM+CALL?)

		// Notify listeners.
		synchronized (listeners) {
			for (ISolariaSerialStatusUpdateReceiver listener : listeners) {
				listener.onActiveChannelChanged(activeChannel);
			}
		}
	}

	private void updateIngestState(boolean ingesting) {
		if (isIngesting == ingesting && ingsetStateChangedTimestamp != null)
			return;

		isIngesting = ingesting;
		ingsetStateChangedTimestamp = Instant.now();

		// Notify listeners.
		synchronized (listeners) {
			for (ISolariaSerialStatusUpdateReceiver listener : listeners) {
				listener.onIngestStatusChanged(isIngesting, ingsetStateChangedTimestamp);
			}
		}
	}

	@Override
	protected String getCommandString(CommandContainer<SolariaCommand> cmd) {
		String command = null;
		switch (cmd.cmd) {
		case GetPowerStatus:
			command = "(PWR+STAT?)";
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
		case GetActiveChannel:
			command = "(CHA?)";
			break;
		case SetActiveChannel:
			command = "(CHA " + cmd.value + ")";
			break;
		case GetIngestState:
			command = "(PWR+IGST?)";
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
			} else
				throw new WebSocketCommandException("Failed to turn lamp off. No connection to Christie projector.");
			return true;
		case "power_off":
			if (socket.isConnected()) {
				if (isIngesting())
					throw new WebSocketCommandException("The IMB is currently ingesting content.");

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
		case "set_channel":
			if (socket.isConnected()) {
				SetChannelMessage setChannelMsg = gson.fromJson(message, SetChannelMessage.class);
				if (setChannelMsg.getChannel() <= 0 || setChannelMsg.getChannel() >= channelMapping.size())
					throw new WebSocketCommandException("Invalid projector channel: " + setChannelMsg.getChannel());

				// Get the right actual channel number.
				int channelIndex = channelMapping.get(setChannelMsg.getChannel());

				addCommand(SolariaCommand.SetActiveChannel, channelIndex, UseResponse.IgnoreResponse);
				addCommand(SolariaCommand.GetActiveChannel);
			} else
				throw new WebSocketCommandException(
						"Failed to change active channel. No connection to Christie projector.");
			return true;
		}

		return false;
	}
}
