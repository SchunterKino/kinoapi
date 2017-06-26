package de.schunterkino.kinoapi.dolby;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonSyntaxException;

import de.schunterkino.kinoapi.sockets.BaseSocketCommands;
import de.schunterkino.kinoapi.sockets.CommandContainer;
import de.schunterkino.kinoapi.websocket.WebSocketCommandException;
import de.schunterkino.kinoapi.websocket.messages.BaseMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.SetInputModeMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.SetMuteStatusMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.SetVolumeMessage;

public class DolbySocketCommands extends BaseSocketCommands<IDolbyStatusUpdateReceiver> {

	private CommandContainer<Commands> noneCommand = new CommandContainer<>(Commands.None);

	private LinkedList<CommandContainer<Commands>> commandQueue;
	private CommandContainer<Commands> currentCommand;

	// Volume control
	private Pattern faderPattern;
	private int volume;
	private Instant lastGetVolume;

	// Mute button
	private Pattern mutePattern;
	private boolean muted;
	private Instant lastGetMuteStatus;

	// Input mode
	// This list must match the InputMode enum.
	private static final List<String> inputModeNames = Arrays.asList("dig_1", "dig_2", "dig_3", "dig_4", "analog",
			"non_sync", "mic", "last");
	private Pattern inputModePattern;
	private InputMode inputMode;
	private Instant lastGetInputMode;

	public DolbySocketCommands() {
		super();
		this.commandQueue = new LinkedList<>();
		this.currentCommand = noneCommand;

		this.faderPattern = Pattern.compile("cp750\\.sys\\.fader (\\d+)");
		this.volume = -1;
		this.lastGetVolume = null;

		this.mutePattern = Pattern.compile("cp750\\.sys\\.mute (\\d+)");
		this.muted = false;
		this.lastGetMuteStatus = null;

		this.inputModePattern = Pattern.compile("cp750\\.sys\\.input_mode ([a-zA-Z0-9_]+)");
		this.inputMode = InputMode.Digital_1;
		this.lastGetInputMode = null;
	}

	@Override
	public void run() {

		// Notify listeners.
		synchronized (listeners) {
			for (IDolbyStatusUpdateReceiver listener : listeners) {
				listener.onDolbyConnected();
			}
		}

		// Go in a loop to process the data on the socket.
		try {
			do {
				// We're waiting on a response for that command. See if there's
				// something here.
				if (currentCommand.cmd != Commands.None) {
					String ret = read();
					// Nothing yet. Keep waiting.
					if (ret == null)
						continue;

					// Don't spam the commands that are sent every 5 seconds.
					if (!ignoreCommandInOutput(currentCommand.cmd))
						System.out
								.println("Dolby: Current command: " + currentCommand.cmd + ". Received: " + ret.trim());

					Matcher matcher;
					switch (currentCommand.cmd) {
					case GetVolume:
						// Parse the response
						matcher = faderPattern.matcher(ret);
						// Wait until we get the desired response.
						while (matcher.find()) {
							String volume = matcher.group(1);
							if (volume != null) {
								updateVolumeValue(Integer.parseInt(volume));
								currentCommand = noneCommand;
							}
						}
						break;
					case GetMuteStatus:
						// Parse the response
						matcher = mutePattern.matcher(ret);
						// Wait until we get the desired response.
						while (matcher.find()) {
							String muted = matcher.group(1);
							if (muted != null) {
								updateMuteStatus(Integer.parseInt(muted) != 0);
								currentCommand = noneCommand;
							}
						}
						break;
					case GetInputMode:
						// Parse the response
						matcher = inputModePattern.matcher(ret);
						// Wait until we get the desired response.
						while (matcher.find()) {
							String inputMode = matcher.group(1);
							if (inputMode != null) {
								int ordInputMode = inputModeNames.indexOf(inputMode);
								if (ordInputMode != -1) {
									updateInputMode(InputMode.values()[ordInputMode]);
								}
								else {
									System.err.printf("Dolby: Received invalid input_mode: %s%n", inputMode);
								}
								currentCommand = noneCommand;
							}
						}
						break;
					default:
						currentCommand = noneCommand;
						break;
					}
				}

				// See if someone wanted to send some command.
				currentCommand = noneCommand;
				synchronized (commandQueue) {
					if (!commandQueue.isEmpty())
						currentCommand = commandQueue.removeFirst();
				}

				// Throw in a GetVolume command from time to time if the
				// current command is None.
				if (currentCommand.cmd == Commands.None) {
					// Get the current volume every 5 seconds.
					// TODO: Increase the interval if no websocket clients are
					// connected.
					if (lastGetVolume == null || Duration.between(lastGetVolume, Instant.now()).toMillis() > 5000)
						currentCommand = new CommandContainer<>(Commands.GetVolume);
					else if (lastGetMuteStatus == null
							|| Duration.between(lastGetMuteStatus, Instant.now()).toMillis() > 5000)
						currentCommand = new CommandContainer<>(Commands.GetMuteStatus);
					else if (lastGetInputMode == null
							|| Duration.between(lastGetInputMode, Instant.now()).toMillis() > 5000)
						currentCommand = new CommandContainer<>(Commands.GetInputMode);
				}

				// Send the right command now.
				String command = null;
				switch (currentCommand.cmd) {
				case None:
					break;
				case GetVolume:
					command = "cp750.sys.fader ?";
					lastGetVolume = Instant.now();
					break;
				case SetVolume:
					command = "cp750.sys.fader " + currentCommand.value;
					break;
				case IncreaseVolume:
					command = "cp750.ctrl.fader_delta 1";
					break;
				case DecreaseVolume:
					command = "cp750.ctrl.fader_delta -1";
					break;
				case GetMuteStatus:
					command = "cp750.sys.mute ?";
					lastGetMuteStatus = Instant.now();
					break;
				case SetMuteStatus:
					command = "cp750.sys.mute " + currentCommand.value;
					break;
				case SetInputMode:
					command = "cp750.sys.input_mode " + inputModeNames.get(currentCommand.value);
					break;
				case GetInputMode:
					command = "cp750.sys.input_mode ?";
					lastGetInputMode = Instant.now();
					break;
				}

				if (command != null) {
					socket.getOutputStream().write((command + "\r\n").getBytes(Charset.forName("ascii")));
					// Don't spam the commands that are sent every 5 seconds.
					if (!ignoreCommandInOutput(currentCommand.cmd))
						System.out.println("Dolby: Sent: " + command);
				}

				// Wait a bit until processing the next command.
				Thread.sleep(500);
			} while (!stop);
		} catch (IOException | InterruptedException e) {
			if (!stop) {
				System.err.println("Dolby: Error in reader thread: " + e.getMessage());
				e.printStackTrace();
			}
		}

		// Notify listeners.
		synchronized (listeners) {
			for (IDolbyStatusUpdateReceiver listener : listeners) {
				listener.onDolbyDisconnected();
			}
		}
	}

	public void increaseVolume() {
		synchronized (commandQueue) {
			addCommand(Commands.IncreaseVolume);
			// Get the new volume right away afterwards.
			addCommand(Commands.GetVolume);
		}
	}

	public void decreaseVolume() {
		addCommand(Commands.DecreaseVolume);
		// Get the new volume right away afterwards.
		addCommand(Commands.GetVolume);
	}

	public int getVolume() {
		return volume;
	}

	public void setVolume(int volume) {
		addCommand(Commands.SetVolume, volume);
		// Get the new volume right away afterwards.
		addCommand(Commands.GetVolume);
	}

	public boolean isMuted() {
		return muted;
	}

	public void setMuted(boolean muted) {
		addCommand(Commands.SetMuteStatus, muted ? 1 : 0);
		// Get the new status right away afterwards.
		addCommand(Commands.GetMuteStatus);
	}

	public InputMode getInputMode() {
		return inputMode;
	}

	public void setInputMode(InputMode mode) {
		addCommand(Commands.SetInputMode, mode.ordinal());
		// Get the new mode right away afterwards.
		addCommand(Commands.GetInputMode);
	}

	private void addCommand(Commands cmd, int value) {
		synchronized (commandQueue) {
			// Make sure this is the only command of that type in the queue.
			Iterator<CommandContainer<Commands>> i = commandQueue.iterator();
			while (i.hasNext()) {
				CommandContainer<Commands> command = i.next();
				if (command.cmd == cmd)
					i.remove();
			}

			// Add the new command now.
			commandQueue.add(new CommandContainer<>(cmd, value));
		}
	}

	private void addCommand(Commands cmd) {
		addCommand(cmd, 0);
	}

	private boolean ignoreCommandInOutput(Commands cmd) {
		return cmd == Commands.GetVolume || cmd == Commands.GetMuteStatus || cmd == Commands.GetInputMode;
	}

	private void updateVolumeValue(int volume) {
		// Don't inform if the volume didn't change.
		if (this.volume == volume)
			return;

		this.volume = volume;

		// Notify listeners.
		synchronized (listeners) {
			for (IDolbyStatusUpdateReceiver listener : listeners) {
				listener.onVolumeChanged(volume);
			}
		}
	}

	private void updateMuteStatus(boolean muted) {
		// Don't inform if the status didn't change.
		if (this.muted == muted)
			return;

		this.muted = muted;

		// Notify listeners.
		synchronized (listeners) {
			for (IDolbyStatusUpdateReceiver listener : listeners) {
				listener.onMuteStatusChanged(muted);
			}
		}
	}

	private void updateInputMode(InputMode mode) {
		// Don't inform if the status didn't change.
		if (this.inputMode == mode)
			return;

		this.inputMode = mode;

		// Notify listeners.
		synchronized (listeners) {
			for (IDolbyStatusUpdateReceiver listener : listeners) {
				listener.onInputModeChanged(mode);
			}
		}
	}

	@Override
	public boolean onMessage(BaseMessage base_msg, String message)
			throws WebSocketCommandException, JsonSyntaxException {
		// Handle all Dolby Volume related commands.
		if (!"volume".equals(base_msg.getMessageType()))
			return false;

		switch (base_msg.getAction()) {
		case "set_volume":
			SetVolumeMessage setVolumeMsg = gson.fromJson(message, SetVolumeMessage.class);
			if (socket.isConnected())
				setVolume(setVolumeMsg.getVolume());
			else
				throw new WebSocketCommandException("Failed to change volume. No connection to Dolby audio processor.");
			return true;

		case "increase_volume":
			if (socket.isConnected())
				increaseVolume();
			else
				throw new WebSocketCommandException(
						"Failed to increase volume. No connection to Dolby audio processor.");

			return true;

		case "decrease_volume":
			if (socket.isConnected())
				decreaseVolume();
			else
				throw new WebSocketCommandException(
						"Failed to decrease volume. No connection to Dolby audio processor.");

			return true;

		case "set_mute_status":
			SetMuteStatusMessage setMuteStatusMsg = gson.fromJson(message, SetMuteStatusMessage.class);
			if (socket.isConnected())
				setMuted(setMuteStatusMsg.isMuted());
			else
				throw new WebSocketCommandException(
						"Failed to change mute state. No connection to Dolby audio processor.");

			return true;

		case "set_input_mode":
			SetInputModeMessage setInputModeMsg = gson.fromJson(message, SetInputModeMessage.class);
			if (socket.isConnected()) {
				int desiredMode = setInputModeMsg.getInputMode();
				if (desiredMode < 0 || desiredMode >= InputMode.values().length)
					throw new WebSocketCommandException("Invalid input mode " + desiredMode
							+ ". Has to be between 0 and " + InputMode.values().length + ".");
				InputMode mode = InputMode.values()[desiredMode];
				setInputMode(mode);
			} else
				throw new WebSocketCommandException(
						"Failed to change input mode. No connection to Dolby audio processor.");

			return true;
		}

		return false;
	}
}
