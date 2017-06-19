package de.schunterkino.kinoapi.jnior;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;

import de.schunterkino.kinoapi.sockets.BaseSocketCommands;
import de.schunterkino.kinoapi.sockets.CommandContainer;

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
			commandQueue.add(new CommandContainer<>(Commands.SetLightLevel, level));
		}
	}
}
