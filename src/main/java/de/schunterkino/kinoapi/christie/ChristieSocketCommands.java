package de.schunterkino.kinoapi.christie;

import com.google.gson.JsonSyntaxException;

import de.schunterkino.kinoapi.sockets.BaseSocketCommands;
import de.schunterkino.kinoapi.sockets.CommandContainer;
import de.schunterkino.kinoapi.websocket.WebSocketCommandException;
import de.schunterkino.kinoapi.websocket.messages.BaseMessage;
import de.schunterkino.kinoapi.websocket.messages.christie.SetInputModeMessage;

public class ChristieSocketCommands extends BaseSocketCommands<IChristieStatusUpdateReceiver, ChristieCommand> {

	protected int UPDATE_INTERVAL = 10000;

	public ChristieSocketCommands() {
		super();
		ignoreResponses();

		// Send some random string every now and then to keep the projector from
		// closing our connection.
		// And detect if the projector is turned off.
		watchCommand(ChristieCommand.KeepAlive);
	}

	@Override
	protected void onSocketConnected() {
		// Notify listeners.
		synchronized (listeners) {
			for (IChristieStatusUpdateReceiver listener : listeners) {
				listener.onChristieConnected();
			}
		}
	}

	@Override
	protected void onSocketDisconnected() {
		// Notify listeners.
		synchronized (listeners) {
			for (IChristieStatusUpdateReceiver listener : listeners) {
				listener.onChristieDisconnected();
			}
		}
	}

	@Override
	protected boolean onReceiveCommandOutput(String input) {
		return true; // Don't care for stuff sent to us.
	}

	@Override
	protected String getCommandString(CommandContainer<ChristieCommand> cmd) {
		String command = null;
		switch (cmd.cmd) {
		case Play:
			command = "Play";
			break;
		case Pause:
			command = "Pause";
			break;
		case Stop:
			command = "Stop";
			break;
		case LampOn:
			command = "LampOn";
			break;
		case LampOff:
			command = "LampOff";
			break;
		case DouserOpen:
			command = "DouserOpen";
			break;
		case DouserClose:
			command = "DouserClose";
			break;
		case FormatCinemaFlat:
			command = "CinemaFlat";
			break;
		case FormatCinemaScope:
			command = "CinemaScope";
			break;
		case FormatPCFlat:
			command = "ComputerFlat";
			break;
		case FormatPCScope:
			command = "ComputerScope";
			break;
		case KeepAlive:
			command = "PenisKopf";
			break;
		}
		return command;
	}

	@Override
	public boolean onMessage(BaseMessage baseMsg, String message)
			throws WebSocketCommandException, JsonSyntaxException {

		// Handle all Dolby Volume related commands.
		if (!"playback".equals(baseMsg.getMessageType()))
			return false;

		switch (baseMsg.getAction()) {
		case "play":
			if (socket.isConnected())
				addCommand(ChristieCommand.Play);
			else
				throw new WebSocketCommandException("Failed to play content. No connection to Christie projector.");
			return true;
		case "pause":
			if (socket.isConnected())
				addCommand(ChristieCommand.Pause);
			else
				throw new WebSocketCommandException("Failed to pause content. No connection to Christie projector.");
			return true;
		case "stop":
			if (socket.isConnected())
				addCommand(ChristieCommand.Stop);
			else
				throw new WebSocketCommandException("Failed to stop content. No connection to Christie projector.");
			return true;
		case "lamp_on":
			if (socket.isConnected())
				addCommand(ChristieCommand.LampOn);
			else
				throw new WebSocketCommandException("Failed to turn lamp on. No connection to Christie projector.");
			return true;
		case "lamp_off":
			if (socket.isConnected())
				addCommand(ChristieCommand.LampOff);
			else
				throw new WebSocketCommandException("Failed to turn lamp off. No connection to Christie projector.");
			return true;
		case "douser_open":
			if (socket.isConnected())
				addCommand(ChristieCommand.DouserOpen);
			else
				throw new WebSocketCommandException("Failed to open the douser. No connection to Christie projector.");
			return true;
		case "douser_close":
			if (socket.isConnected())
				addCommand(ChristieCommand.DouserClose);
			else
				throw new WebSocketCommandException("Failed to close the douser. No connection to Christie projector.");
			return true;
		case "set_input_mode":
			if (socket.isConnected()) {
				SetInputModeMessage setInputModeMsg = gson.fromJson(message, SetInputModeMessage.class);
				switch (setInputModeMsg.getInputMode()) {
				case "cinema_flat":
					addCommand(ChristieCommand.FormatCinemaFlat);
					break;
				case "cinema_scope":
					addCommand(ChristieCommand.FormatCinemaScope);
					break;
				case "pc_flat":
					addCommand(ChristieCommand.FormatPCFlat);
					break;
				case "pc_scope":
					addCommand(ChristieCommand.FormatPCScope);
					break;
				default:
					throw new WebSocketCommandException(
							"Invalid projector input mode: " + setInputModeMsg.getInputMode());
				}
			} else
				throw new WebSocketCommandException(
						"Failed to switch to cinema flat input. No connection to Christie projector.");
			return true;
		}

		return false;
	}

}
