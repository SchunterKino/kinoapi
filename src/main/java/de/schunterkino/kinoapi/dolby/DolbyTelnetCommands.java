package de.schunterkino.kinoapi.dolby;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.net.telnet.TelnetClient;

public class DolbyTelnetCommands implements Runnable {

	private TelnetClient telnetClient;
	private boolean stop;
	private LinkedList<Commands> commandQueue;
	private Commands currentCommand;
	private Pattern faderPattern;

	public DolbyTelnetCommands(TelnetClient telnetClient) {
		this.telnetClient = telnetClient;
		this.stop = false;
		this.commandQueue = new LinkedList<>();
		this.currentCommand = Commands.None;
		this.faderPattern = Pattern.compile("cp750\\.sys\\.fader (\\d+)");
	}

	@Override
	public void run() {

		try {
			DataOutputStream out = new DataOutputStream(telnetClient.getOutputStream());
			do {

				// We're waiting on a response for that command. See if there's
				// something here.
				if (currentCommand != Commands.None) {
					String ret = read();
					// Nothing yet. Keep waiting.
					if (ret == null)
						continue;

					switch (currentCommand) {
					case GetVolume:
						// Parse the response
						Matcher matcher = faderPattern.matcher(ret);
						// Wait until we get the desired response.
						while (matcher.find()) {
							String volume = matcher.group(1);
							if (volume != null) {
								updateVolumeValue(Integer.parseInt(volume));
								currentCommand = Commands.None;
							}
						}
						break;
					default:
						currentCommand = Commands.None;
						break;
					}
				}

				// See if someone wanted to send some command.
				currentCommand = Commands.None;
				synchronized (commandQueue) {
					if (!commandQueue.isEmpty())
						currentCommand = commandQueue.removeFirst();
				}

				// TODO: Throw in a GetVolume command from time to time if the
				// current command is None.

				// Send the right command now.
				switch (currentCommand) {
				case None:
					break;
				case GetVolume:
					out.writeUTF("cp750.sys.fader ?");
					break;
				case IncreaseVolume:
					// FIXME: Lookup correct command again.
					out.writeUTF("cp750.sys.fader +1");
					break;
				case DecreaseVolume:
					// FIXME: Lookup correct command again.
					out.writeUTF("cp750.sys.fader -1");
					break;
				}
			} while (!stop);
		} catch (IOException e) {
			if (!stop)
				e.printStackTrace();
		}
	}

	public void increaseVolume() {
		synchronized (commandQueue) {
			commandQueue.add(Commands.IncreaseVolume);
			// Get the new volume right away afterwards.
			commandQueue.add(Commands.GetVolume);
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

	private void updateVolumeValue(int volume) {
		// TODO Notify listeners.
	}
}
