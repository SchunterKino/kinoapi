package de.schunterkino.kinoapi.dolby;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.net.telnet.TelnetClient;

public class DolbyTelnetCommands implements Runnable {

	private class CommandContainer {
		public Commands cmd;
		public int value;

		public CommandContainer(Commands cmd) {
			this.cmd = cmd;
			this.value = 0;
		}

		public CommandContainer(Commands cmd, int value) {
			this.cmd = cmd;
			this.value = value;
		}
	}

	private final CommandContainer noneCommand = new CommandContainer(Commands.None);

	private TelnetClient telnetClient;
	private boolean stop;
	private LinkedList<CommandContainer> commandQueue;
	private CommandContainer currentCommand;
	private Pattern faderPattern;
	private int volume;
	private Instant lastGetVolume;

	private LinkedList<IDolbyStatusUpdateReceiver> listeners;

	public DolbyTelnetCommands(TelnetClient telnetClient) {
		this.telnetClient = telnetClient;
		this.faderPattern = Pattern.compile("cp750\\.sys\\.fader (\\d+)");
		this.commandQueue = new LinkedList<>();
		this.listeners = new LinkedList<>();
		this.lastGetVolume = null;
	}

	public void reset() {
		stop = false;
		commandQueue.clear();
		currentCommand = noneCommand;
		volume = -1;
	}

	@Override
	public void run() {

		// Notify listeners.
		synchronized (listeners) {
			for (IDolbyStatusUpdateReceiver listener : listeners) {
				listener.onDolbyConnected();
			}
		}

		// Go in a loop to
		try {
			DataOutputStream out = new DataOutputStream(telnetClient.getOutputStream());
			do {

				// We're waiting on a response for that command. See if there's
				// something here.
				if (currentCommand.cmd != Commands.None) {
					String ret = read();
					// Nothing yet. Keep waiting.
					if (ret == null)
						continue;

					switch (currentCommand.cmd) {
					case GetVolume:
						// Parse the response
						Matcher matcher = faderPattern.matcher(ret);
						// Wait until we get the desired response.
						while (matcher.find()) {
							String volume = matcher.group(1);
							if (volume != null) {
								updateVolumeValue(Integer.parseInt(volume));
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
						currentCommand = new CommandContainer(Commands.GetVolume);
				}

				// Send the right command now.
				switch (currentCommand.cmd) {
				case None:
					break;
				case GetVolume:
					out.writeUTF("cp750.sys.fader ?");
					lastGetVolume = Instant.now();
					break;
				case SetVolume:
					out.writeUTF("cp750.sys.fader " + currentCommand.value);
					break;
				case IncreaseVolume:
					out.writeUTF("cp750.ctrl.fader_delta 1");
					break;
				case DecreaseVolume:
					out.writeUTF("cp750.ctrl.fader_delta -1");
					break;
				}

				// Wait a bit until processing the next command.
				Thread.sleep(500);
			} while (!stop);
		} catch (IOException | InterruptedException e) {
			if (!stop)
				e.printStackTrace();
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
			commandQueue.add(new CommandContainer(Commands.IncreaseVolume));
			// Get the new volume right away afterwards.
			commandQueue.add(new CommandContainer(Commands.GetVolume));
		}
	}

	public void decreaseVolume() {
		synchronized (commandQueue) {
			commandQueue.add(new CommandContainer(Commands.DecreaseVolume));
			// Get the new volume right away afterwards.
			commandQueue.add(new CommandContainer(Commands.GetVolume));
		}
	}

	public int getVolume() {
		return volume;
	}

	public void setVolume(int volume) {
		synchronized (commandQueue) {
			commandQueue.add(new CommandContainer(Commands.SetVolume, volume));
			// Get the new volume right away afterwards.
			commandQueue.add(new CommandContainer(Commands.GetVolume));
		}
	}

	private String read() throws IOException {
		InputStream in = telnetClient.getInputStream();
		byte[] buffer = new byte[1024];
		int ret_read = in.read(buffer);
		if (ret_read == -1)
			throw new IOException("EOF");

		if (ret_read == 0)
			return null;

		return new String(buffer, 0, ret_read);
	}

	public void stop() {
		stop = true;
	}

	public void registerListener(IDolbyStatusUpdateReceiver listener) {
		synchronized (listeners) {
			listeners.add(listener);
		}
	}

	private void updateVolumeValue(int volume) {
		this.volume = volume;

		// Notify listeners.
		synchronized (listeners) {
			for (IDolbyStatusUpdateReceiver listener : listeners) {
				listener.onVolumeChanged(volume);
			}
		}
	}
}
