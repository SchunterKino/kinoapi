package de.schunterkino.kinoapi.christie;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.LinkedList;

import com.google.gson.JsonSyntaxException;

import de.schunterkino.kinoapi.sockets.BaseSocketCommands;
import de.schunterkino.kinoapi.sockets.CommandContainer;
import de.schunterkino.kinoapi.websocket.WebSocketCommandException;
import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public class ChristieSocketCommands extends BaseSocketCommands<IChristieStatusUpdateReceiver> {

	private static CommandContainer<Command> noneCommand = new CommandContainer<>(Command.None);

	private LinkedList<CommandContainer<Command>> commandQueue;
	private CommandContainer<Command> currentCommand;

	public ChristieSocketCommands() {
		super();
		this.commandQueue = new LinkedList<>();
		this.currentCommand = noneCommand;
	}

	@Override
	public void run() {
		// Notify listeners.
		synchronized (listeners) {
			for (IChristieStatusUpdateReceiver listener : listeners) {
				listener.onChristieConnected();
			}
		}

		// Go in a loop to process the data on the socket.
		try {
			do {
				// See if someone wanted to send some command.
				currentCommand = noneCommand;
				synchronized (commandQueue) {
					if (!commandQueue.isEmpty())
						currentCommand = commandQueue.removeFirst();
				}

				// Send the right command now.
				String command = null;
				switch (currentCommand.cmd) {
				case None:
					break;
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
				}

				if (command != null) {
					socket.getOutputStream().write((command + "\r\n").getBytes(Charset.forName("ascii")));
					System.out.println("Christie: Sent: " + command);
				}

				// Wait a bit until processing the next command.
				Thread.sleep(500);
			} while (!stop);
		} catch (IOException | InterruptedException e) {
			if (!stop) {
				System.err.println("Christie: Error in reader thread: " + e.getMessage());
				e.printStackTrace();
			}
		}

		// Notify listeners.
		synchronized (listeners) {
			for (IChristieStatusUpdateReceiver listener : listeners) {
				listener.onChristieDisconnected();
			}
		}
	}
	
	private void addCommand(Command cmd) {
		synchronized (commandQueue) {
			// Make sure this is the only command of that type in the queue.
			Iterator<CommandContainer<Command>> i = commandQueue.iterator();
			while (i.hasNext()) {
				CommandContainer<Command> command = i.next();
				if (command.cmd == cmd)
					i.remove();
			}

			// Add the new command now.
			commandQueue.add(new CommandContainer<>(cmd, 0));
		}
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
				addCommand(Command.Play);
			else
				throw new WebSocketCommandException("Failed to play content. No connection to Christie projector.");
			return true;
		case "pause":
			if (socket.isConnected())
				addCommand(Command.Pause);
			else
				throw new WebSocketCommandException("Failed to pause content. No connection to Christie projector.");
			return true;
		case "stop":
			if (socket.isConnected())
				addCommand(Command.Stop);
			else
				throw new WebSocketCommandException("Failed to stop content. No connection to Christie projector.");
			return true;
		case "lamp_on":
			if (socket.isConnected())
				addCommand(Command.LampOn);
			else
				throw new WebSocketCommandException("Failed to turn lamp on. No connection to Christie projector.");
			return true;
		case "lamp_off":
			if (socket.isConnected())
				addCommand(Command.LampOff);
			else
				throw new WebSocketCommandException("Failed to turn lamp off. No connection to Christie projector.");
			return true;
		case "douser_open":
			if (socket.isConnected())
				addCommand(Command.DouserOpen);
			else
				throw new WebSocketCommandException("Failed to open the douser. No connection to Christie projector.");
			return true;
		case "douser_close":
			if (socket.isConnected())
				addCommand(Command.DouserClose);
			else
				throw new WebSocketCommandException("Failed to close the douser. No connection to Christie projector.");
			return true;
		case "format_cinema_flat":
			if (socket.isConnected())
				addCommand(Command.FormatCinemaFlat);
			else
				throw new WebSocketCommandException("Failed to switch to cinema flat input. No connection to Christie projector.");
			return true;
		case "format_cinema_scope":
			if (socket.isConnected())
				addCommand(Command.FormatCinemaScope);
			else
				throw new WebSocketCommandException("Failed to switch to cinema scope input. No connection to Christie projector.");
			return true;
		case "format_pc_flat":
			if (socket.isConnected())
				addCommand(Command.FormatPCFlat);
			else
				throw new WebSocketCommandException("Failed to switch to PC flat input. No connection to Christie projector.");
			return true;
		case "format_pc_scope":
			if (socket.isConnected())
				addCommand(Command.FormatPCScope);
			else
				throw new WebSocketCommandException("Failed to switch to PC scope input. No connection to Christie projector.");
			return true;
		}

		return false;
	}

}
