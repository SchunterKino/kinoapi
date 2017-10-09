package de.schunterkino.kinoapi.dolby;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonSyntaxException;

import de.schunterkino.kinoapi.sockets.BaseCommands;
import de.schunterkino.kinoapi.sockets.CommandContainer;
import de.schunterkino.kinoapi.websocket.WebSocketCommandException;
import de.schunterkino.kinoapi.websocket.messages.BaseMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.SetDecodeModeMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.SetInputModeMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.SetMuteStatusMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.SetVolumeMessage;

public class DolbySocketCommands extends BaseCommands<IDolbyStatusUpdateReceiver, DolbyCommand> {

	// Volume control
	private Pattern faderPattern;
	private int volume;

	// Mute button
	private Pattern mutePattern;
	private boolean muted;

	// Input mode
	// This list must match the InputMode enum.
	private static final List<String> inputModeNames = Arrays.asList("dig_1", "dig_2", "dig_3", "dig_4", "analog",
			"non_sync", "mic", "last");
	private Pattern inputModePattern;
	private InputMode inputMode;

	// Digital 1 decode mode (5.1 or 7.1 surround)
	// This list must match the DecodeMode enum.
	private static final List<String> decodeModeNames = Arrays.asList("invalid", "auto", "n_a", "lr_discrete",
			"prologic", "prologic_2", "4_discrete_sur");
	private Pattern decodeModePattern;
	private DecodeMode decodeMode;

	public DolbySocketCommands() {
		super();

		this.faderPattern = Pattern.compile("cp750\\.sys\\.fader (\\d+)");
		this.volume = -1;
		watchCommand(DolbyCommand.GetVolume);

		this.mutePattern = Pattern.compile("cp750\\.sys\\.mute (\\d+)");
		this.muted = false;
		watchCommand(DolbyCommand.GetMuteStatus);

		this.inputModePattern = Pattern.compile("cp750\\.sys\\.input_mode ([a-zA-Z0-9_]+)");
		this.inputMode = InputMode.Digital_1;
		watchCommand(DolbyCommand.GetInputMode);

		this.decodeModePattern = Pattern.compile("cp750\\.sys\\.pcm_2_channel_decode_mode_1 ([a-zA-Z0-9_]+)");
		this.decodeMode = DecodeMode.Auto;
		watchCommand(DolbyCommand.GetDecodeMode);
	}

	@Override
	protected void onSocketConnected() {
		// Notify listeners.
		synchronized (listeners) {
			for (IDolbyStatusUpdateReceiver listener : listeners) {
				listener.onDolbyConnected();
			}
		}
	}
	
	@Override
	protected void onSocketDisconnected() {
		// Notify listeners.
		synchronized (listeners) {
			for (IDolbyStatusUpdateReceiver listener : listeners) {
				listener.onDolbyDisconnected();
			}
		}
	}
	
	@Override
	protected boolean onReceiveCommandOutput(String output) {
		boolean handled = false;
		Matcher matcher;
		switch (getCurrentCommand().cmd) {
		case GetVolume:
		case SetVolume:
			// Parse the response
			matcher = faderPattern.matcher(output);
			// Wait until we get the desired response.
			while (matcher.find()) {
				String volume = matcher.group(1);
				if (volume != null) {
					updateVolumeValue(Integer.parseInt(volume));
					handled = true;
				}
			}
			break;
		case GetMuteStatus:
		case SetMuteStatus:
			// Parse the response
			matcher = mutePattern.matcher(output);
			// Wait until we get the desired response.
			while (matcher.find()) {
				String muted = matcher.group(1);
				if (muted != null) {
					updateMuteStatus(Integer.parseInt(muted) != 0);
					handled = true;
				}
			}
			break;
		case GetInputMode:
		case SetInputMode:
			// Parse the response
			matcher = inputModePattern.matcher(output);
			// Wait until we get the desired response.
			while (matcher.find()) {
				String inputMode = matcher.group(1);
				if (inputMode != null) {
					int ordInputMode = inputModeNames.indexOf(inputMode);
					if (ordInputMode != -1) {
						updateInputMode(InputMode.values()[ordInputMode]);
					} else {
						System.err.printf("%s: Received invalid input_mode: %s%n", LOG_TAG, inputMode);
					}
					handled = true;
				}
			}
			break;
		case GetDecodeMode:
		case SetDecodeMode:
			// Parse the response
			matcher = decodeModePattern.matcher(output);
			// Wait until we get the desired response.
			while (matcher.find()) {
				String decodeMode = matcher.group(1);
				if (decodeMode != null) {
					int ordDecodeMode = decodeModeNames.indexOf(decodeMode);
					if (ordDecodeMode != -1) {
						updateDecodeMode(DecodeMode.values()[ordDecodeMode]);
					} else {
						System.err.printf("%s: Received invalid pcm_2_channel_decode_mode_1: %s%n",
								LOG_TAG, decodeMode);
					}
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
	
	@Override
	protected String getCommandString(CommandContainer<DolbyCommand> cmd) {
		String command = null;
		switch (cmd.cmd) {
		case GetVolume:
			command = "cp750.sys.fader ?";
			break;
		case SetVolume:
			command = "cp750.sys.fader " + cmd.value;
			break;
		case IncreaseVolume:
			command = "cp750.ctrl.fader_delta 1";
			break;
		case DecreaseVolume:
			command = "cp750.ctrl.fader_delta -1";
			break;
		case GetMuteStatus:
			command = "cp750.sys.mute ?";
			break;
		case SetMuteStatus:
			command = "cp750.sys.mute " + cmd.value;
			break;
		case SetInputMode:
			command = "cp750.sys.input_mode " + inputModeNames.get(cmd.value);
			break;
		case GetInputMode:
			command = "cp750.sys.input_mode ?";
			break;
		case SetDecodeMode:
			command = "cp750.sys.pcm_2_channel_decode_mode_1 " + decodeModeNames.get(cmd.value);
			break;
		case GetDecodeMode:
			command = "cp750.sys.pcm_2_channel_decode_mode_1 ?";
			break;
		}
		return command;
	}

	public void increaseVolume() {
		addCommand(DolbyCommand.IncreaseVolume);
		// Get the new volume right away afterwards.
		addCommand(DolbyCommand.GetVolume);
	}

	public void decreaseVolume() {
		addCommand(DolbyCommand.DecreaseVolume);
		// Get the new volume right away afterwards.
		addCommand(DolbyCommand.GetVolume);
	}

	public int getVolume() {
		return volume;
	}

	public void setVolume(int volume) {
		addCommand(DolbyCommand.SetVolume, volume);
	}

	public boolean isMuted() {
		return muted;
	}

	public void setMuted(boolean muted) {
		addCommand(DolbyCommand.SetMuteStatus, muted ? 1 : 0);
	}

	public InputMode getInputMode() {
		return inputMode;
	}

	public void setInputMode(InputMode mode) {
		addCommand(DolbyCommand.SetInputMode, mode.ordinal());
	}

	public DecodeMode getDecodeMode() {
		return decodeMode;
	}

	public void setDecodeMode(DecodeMode mode) {
		addCommand(DolbyCommand.SetDecodeMode, mode.ordinal());
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

	private void updateDecodeMode(DecodeMode mode) {
		// Don't inform if the status didn't change.
		if (this.decodeMode == mode)
			return;

		this.decodeMode = mode;

		// Notify listeners.
		synchronized (listeners) {
			for (IDolbyStatusUpdateReceiver listener : listeners) {
				listener.onDecodeModeChanged(mode);
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

		case "set_decode_mode":
			SetDecodeModeMessage setDecodeModeMsg = gson.fromJson(message, SetDecodeModeMessage.class);
			if (socket.isConnected()) {
				int desiredMode = setDecodeModeMsg.getDecodeMode();
				if (desiredMode < 0 || desiredMode >= DecodeMode.values().length)
					throw new WebSocketCommandException("Invalid decode mode " + desiredMode
							+ ". Has to be between 0 and " + DecodeMode.values().length + ".");
				DecodeMode mode = DecodeMode.values()[desiredMode];
				setDecodeMode(mode);
			} else
				throw new WebSocketCommandException(
						"Failed to change decode mode. No connection to Dolby audio processor.");

			return true;
		}

		return false;
	}
}
