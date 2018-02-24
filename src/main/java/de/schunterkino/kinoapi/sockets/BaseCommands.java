package de.schunterkino.kinoapi.sockets;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;

import com.google.gson.Gson;

import de.schunterkino.kinoapi.websocket.IWebSocketMessageHandler;
import purejavacomm.SerialPort;

public abstract class BaseCommands<ListenerInterface, CommandEnum> implements IWebSocketMessageHandler {

	protected String LOG_TAG = this.getClass().getSimpleName();

	protected int UPDATE_INTERVAL = 2000;

	protected LineWrapper socket;
	protected boolean stop;
	protected LinkedList<ListenerInterface> listeners;
	protected Gson gson;

	private LinkedList<CommandContainer<CommandEnum>> commandQueue;
	private CommandContainer<CommandEnum> noneCommand = new CommandContainer<>(null);
	private CommandContainer<CommandEnum> currentCommand = noneCommand;

	// A map to remember when we last sent a command.
	// The command is added to the queue again if the last time is longer than
	// UPDATE_INTERVAL ago.
	private HashMap<CommandEnum, Instant> updateCommands;

	// Maybe we're not interested at all in what the server has to say.
	// Don't wait for responses.
	private boolean ignoreResponses;

	// Aggregate returned strings until our expected value is in there.
	// This helps if we read from the socket faster than the server is sending data.
	private String fullResponse;

	// Readable way to add a command and specify if we're interested in the
	// response.
	protected enum UseResponse {
		WaitForResponse, IgnoreResponse
	}

	protected BaseCommands() {
		this.socket = null;
		this.stop = false;
		this.listeners = new LinkedList<>();
		this.gson = new Gson();
		this.commandQueue = new LinkedList<>();
		this.updateCommands = new HashMap<>();

		this.ignoreResponses = false;
		this.fullResponse = "";
	}

	public void stop() {
		stop = true;
	}

	public void setSocket(Socket socket) {
		this.socket = new LineWrapper(socket);
	}

	public void setSerialPort(SerialPort serial) {
		this.socket = new LineWrapper(serial);
	}

	public void registerListener(ListenerInterface listener) {
		synchronized (listeners) {
			listeners.add(listener);
		}
	}

	public void processSocket() {
		onSocketConnected();

		// Go in a loop to process the data on the socket.
		try {
			do {
				// We're waiting on a response for that command. See if there's
				// something here.
				if (currentCommand.cmd != null && !currentCommand.ignoreResponse && !ignoreResponses) {
					String ret = read();
					// Nothing yet. Keep waiting.
					if (ret == null)
						continue;

					// Don't spam the commands that are sent every
					// UPDATE_INTERVAL seconds.
					if (!isRepeatingCommand(currentCommand.cmd))
						System.out.printf("%s: Current command: %s. Received: %s%n", LOG_TAG,
								currentCommand.cmd.toString(), ret.trim());

					// Add the last bit to the end.
					fullResponse += ret;

					// The command wasn't handled yet.
					if (!onReceiveCommandOutput(fullResponse))
						continue;

					// This command was handled now. Start scouting for the next output.
					fullResponse = "";
				}

				// See if someone wanted to send some command.
				currentCommand = noneCommand;
				synchronized (commandQueue) {
					if (!commandQueue.isEmpty()) {
						currentCommand = commandQueue.removeFirst();
					} else {
						// Throw in a status update command every
						// UPDATE_INTERVAL if the current command is None.
						// TODO: Increase the interval if no websocket clients
						// are connected.
						for (Entry<CommandEnum, Instant> e : updateCommands.entrySet()) {
							if (e.getValue() == null
									|| Duration.between(e.getValue(), Instant.now()).toMillis() > UPDATE_INTERVAL)
								addCommand(e.getKey());
						}

						// See if we have a command in there now and execute
						// right away.
						if (!commandQueue.isEmpty())
							currentCommand = commandQueue.removeFirst();
					}
				}

				// Send the right command now.
				String command = null;
				if (currentCommand.cmd != null)
					command = getCommandString(currentCommand);

				// Update the timestamp of when we last executed this command if
				// it's one of the repeating ones.
				if (isRepeatingCommand(currentCommand.cmd))
					updateCommands.put(currentCommand.cmd, Instant.now());

				// Send the command in the correct format if we want to send
				// something.
				if (command != null) {
					socket.getOutputStream().write((command + "\r\n").getBytes(Charset.forName("ascii")));
					// Don't spam the commands that are sent every 5 seconds.
					if (!isRepeatingCommand(currentCommand.cmd))
						System.out.printf("%s: Sent: %s%n", LOG_TAG, command);
				}

				// Wait a bit until processing the next command.
				Thread.sleep(500);
			} while (!stop);
		} catch (IOException | InterruptedException e) {
			if (!stop) {
				System.err.printf("%s: Error while reading: %s%n", LOG_TAG, e.getMessage());
			}

			// Reset command so we don't wait for a response anymore.
			// We can't be sure the endpoint even got our request.
			// Don't run outdated commands either after we get a connection
			// again later.
			synchronized (commandQueue) {
				commandQueue.clear();
				currentCommand = noneCommand;
			}

			// Start fresh.
			fullResponse = "";
		}

		onSocketDisconnected();
	}

	protected abstract void onSocketConnected();

	protected abstract void onSocketDisconnected();

	protected abstract boolean onReceiveCommandOutput(String input);

	protected abstract String getCommandString(CommandContainer<CommandEnum> cmd);

	private boolean isRepeatingCommand(CommandEnum cmd) {
		return updateCommands.containsKey(cmd);
	}

	protected String read() throws IOException {
		InputStream in = socket.getInputStream();
		byte[] buffer = new byte[1024];

		int ret_read = in.read(buffer);
		if (ret_read == -1)
			throw new IOException("EOF");

		if (ret_read == 0)
			return null;

		return new String(buffer, 0, ret_read);
	}

	protected CommandContainer<CommandEnum> getCurrentCommand() {
		return currentCommand;
	}

	protected void addCommand(CommandEnum cmd, int value, UseResponse response) {
		synchronized (commandQueue) {
			// Make sure this is the only command of that type in the queue.
			Iterator<CommandContainer<CommandEnum>> i = commandQueue.iterator();
			while (i.hasNext()) {
				CommandContainer<CommandEnum> command = i.next();
				if (command.cmd == cmd)
					i.remove();
			}

			// Add the new command now.
			commandQueue.add(new CommandContainer<>(cmd, value, response == UseResponse.IgnoreResponse));
		}
	}

	protected void addCommand(CommandEnum cmd, int value) {
		addCommand(cmd, value, UseResponse.WaitForResponse);
	}

	protected void addCommand(CommandEnum cmd) {
		addCommand(cmd, 0);
	}

	protected void watchCommand(CommandEnum cmd) {
		updateCommands.put(cmd, null);
	}

	protected void ignoreResponses() {
		ignoreResponses = true;
	}
}
