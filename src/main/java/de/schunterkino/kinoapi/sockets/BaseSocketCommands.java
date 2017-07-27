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

public abstract class BaseSocketCommands<T, V> implements Runnable, IWebSocketMessageHandler {

	protected String LOG_TAG = this.getClass().getSimpleName();
	
	protected int UPDATE_INTERVAL = 2000;
	
	protected Socket socket;
	protected boolean stop;
	protected LinkedList<T> listeners;
	protected Gson gson;
	
	private LinkedList<CommandContainer<V>> commandQueue;
	private CommandContainer<V> currentCommand;
	private CommandContainer<V> noneCommand = new CommandContainer<>(null);
	
	// A map to remember when we last sent a command.
	// The command is added to the queue again if the last time is longer than
	// UPDATE_INTERVAL ago.
	private HashMap<V, Instant> updateCommands;
	
	// Maybe we're not interested at all in what the server has to say.
	// Don't wait for responses.
	private boolean ignoreResponses;

	protected BaseSocketCommands() {
		this.socket = null;
		this.stop = false;
		this.listeners = new LinkedList<>();
		this.gson = new Gson();
		this.commandQueue = new LinkedList<>();
		this.updateCommands = new HashMap<>();
		this.ignoreResponses = false;
	}

	public void stop() {
		stop = true;
	}

	public void setSocket(Socket socket) {
		this.socket = socket;
	}

	public void registerListener(T listener) {
		synchronized (listeners) {
			listeners.add(listener);
		}
	}
	
	@Override
	public void run() {
		onSocketConnected();
		
		// Go in a loop to process the data on the socket.
		try {
			do {
				// We're waiting on a response for that command. See if there's
				// something here.
				if (currentCommand.cmd != null && !ignoreResponses) {
					String ret = read();
					// Nothing yet. Keep waiting.
					if (ret == null)
						continue;

					// Don't spam the commands that are sent every
					// UPDATE_INTERVAL seconds.
					if (!isRepeatingCommand(currentCommand.cmd))
						System.out
								.printf("%s: Current command: %s. Received: %s%n", LOG_TAG, currentCommand.cmd.toString(), ret.trim());

					// The command wasn't handled yet.
					if (!onReceiveCommandOutput(ret))
						continue;
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
						for (Entry<V, Instant> e : updateCommands.entrySet()) {
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
				String command = getCommandString(currentCommand);

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
				System.err.printf("%s: Error in reader thread: %s%n", LOG_TAG, e.getMessage());
				e.printStackTrace();
			}
		}
		
		onSocketDisconnected();
	}
	
	protected abstract void onSocketConnected();
	protected abstract void onSocketDisconnected();
	protected abstract boolean onReceiveCommandOutput(String input);
	protected abstract String getCommandString(CommandContainer<V> cmd);
	
	private boolean isRepeatingCommand(V cmd) {
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
	
	protected CommandContainer<V> getCurrentCommand() {
		return currentCommand;
	}
	
	protected void addCommand(V cmd, int value) {
		synchronized (commandQueue) {
			// Make sure this is the only command of that type in the queue.
			Iterator<CommandContainer<V>> i = commandQueue.iterator();
			while (i.hasNext()) {
				CommandContainer<V> command = i.next();
				if (command.cmd == cmd)
					i.remove();
			}

			// Add the new command now.
			commandQueue.add(new CommandContainer<>(cmd, value));
		}
	}
	
	protected void addCommand(V cmd) {
		addCommand(cmd, 0);
	}
	
	protected void watchCommand(V cmd) {
		updateCommands.put(cmd, null);
	}
	
	protected void ignoreResponses() {
		ignoreResponses = true;
	}
}
