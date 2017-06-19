package de.schunterkino.kinoapi.dolby;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DolbySocketCommands implements Runnable {

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

	private Socket socket;
	private boolean stop;
	private LinkedList<CommandContainer> commandQueue;
	private CommandContainer currentCommand;

	// Volume control
	private Pattern faderPattern;
	private int volume;
	private Instant lastGetVolume;

	// Mute button
	private Pattern mutePattern;
	private boolean muted;
	private Instant lastGetMuteStatus;

	private LinkedList<IDolbyStatusUpdateReceiver> listeners;

	public DolbySocketCommands() {
		this.socket = null;
		this.stop = false;
		this.commandQueue = new LinkedList<>();
		this.currentCommand = noneCommand;

		this.faderPattern = Pattern.compile("cp750\\.sys\\.fader (\\d+)");
		this.volume = -1;
		this.lastGetVolume = null;

		this.mutePattern = Pattern.compile("cp750\\.sys\\.mute (\\d+)");
		this.muted = false;
		this.lastGetMuteStatus = null;

		this.listeners = new LinkedList<>();
	}

	public void setSocket(Socket socket) {
		this.socket = socket;
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
			do {
				// We're waiting on a response for that command. See if there's
				// something here.
				if (currentCommand.cmd != Commands.None) {
					String ret = read();
					// Nothing yet. Keep waiting.
					if (ret == null)
						continue;

					System.out.println("Dolby: Current command: " + currentCommand.cmd + ". Received: " + ret.trim());

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
					else if (lastGetMuteStatus == null
							|| Duration.between(lastGetMuteStatus, Instant.now()).toMillis() > 5000)
						currentCommand = new CommandContainer(Commands.GetMuteStatus);
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
				}

				if (command != null) {
					socket.getOutputStream().write((command + "\r\n").getBytes(Charset.forName("ascii")));
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

	public boolean isMuted() {
		return muted;
	}

	public void setMuted(boolean muted) {
		synchronized (commandQueue) {
			commandQueue.add(new CommandContainer(Commands.SetMuteStatus, muted ? 1 : 0));
			// Get the new status right away afterwards.
			commandQueue.add(new CommandContainer(Commands.GetMuteStatus));
		}
	}

	private String read() throws IOException {
		InputStream in = socket.getInputStream();
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

	private void updateMuteStatus(boolean muted) {
		this.muted = muted;

		// Notify listeners.
		synchronized (listeners) {
			for (IDolbyStatusUpdateReceiver listener : listeners) {
				listener.onMuteStatusChanged(muted);
			}
		}
	}
}
