package de.schunterkino.kinoapi.jnior;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.LinkedList;

import com.google.gson.JsonSyntaxException;

import de.schunterkino.kinoapi.sockets.BaseSocketCommands;
import de.schunterkino.kinoapi.sockets.CommandContainer;
import de.schunterkino.kinoapi.websocket.WebSocketCommandException;
import de.schunterkino.kinoapi.websocket.messages.BaseMessage;
import de.schunterkino.kinoapi.websocket.messages.SetLightLevelMessage;

public class JniorSocketCommands extends BaseSocketCommands<IJniorStatusUpdateReceiver> {

	private CommandContainer<Commands> noneCommand = new CommandContainer<>(Commands.None);

	private LinkedList<CommandContainer<Commands>> commandQueue;
	private CommandContainer<Commands> currentCommand;

	public JniorSocketCommands() {
		super();
		this.commandQueue = new LinkedList<>();
		this.currentCommand = noneCommand;
	}

	@Override
	public void run() {
		// Notify listeners.
		synchronized (listeners) {
			for (IJniorStatusUpdateReceiver listener : listeners) {
				listener.onJniorConnected();
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
				case SetLightLevel:
					switch (currentCommand.value) {
					case 0:
						// Close relay output 1 for 500 ms.
						command = "c1p=500";
						break;
					case 1:
						command = "c2p=500";
						break;
					case 2:
						command = "c3p=500";
						break;
					case 3:
						command = "c4p=500";
						break;
					}
					break;
				}

				if (command != null) {
					socket.getOutputStream().write((command + "\r\n").getBytes(Charset.forName("ascii")));
					System.out.println("Jnior: Sent: " + command);
				}

				// Wait a bit until processing the next command.
				Thread.sleep(500);
			} while (!stop);
		} catch (IOException | InterruptedException e) {
			if (!stop) {
				System.err.println("Jnior: Error in reader thread: " + e.getMessage());
				e.printStackTrace();
			}
		}

		// Notify listeners.
		synchronized (listeners) {
			for (IJniorStatusUpdateReceiver listener : listeners) {
				listener.onJniorDisconnected();
			}
		}
	}

	public void setLightLevel(int level) {
		synchronized (commandQueue) {
			addCommand(Commands.SetLightLevel, level);
		}
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

	@Override
	public boolean onMessage(BaseMessage base_msg, String message)
			throws WebSocketCommandException, JsonSyntaxException {
		// Handle all Jnior managed commands.
		if (!"lights".equals(base_msg.getMessageType()))
			return false;

		switch (base_msg.getAction()) {
		case "set_light_level":
			SetLightLevelMessage setLightLevelMsg = gson.fromJson(message, SetLightLevelMessage.class);
			if (socket.isConnected())
				setLightLevel(setLightLevelMsg.getLightLevel());
			else
				throw new WebSocketCommandException(
						"Failed to change light level. No connection to Jnior automation box.");
			return true;
		}

		return false;
	}
}
