package de.schunterkino.kinoapi.audio;

import java.io.File;
import java.io.IOException;
import java.time.Instant;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

import de.schunterkino.kinoapi.dolby.DecodeMode;
import de.schunterkino.kinoapi.dolby.DolbyCommand;
import de.schunterkino.kinoapi.dolby.DolbySocketCommands;
import de.schunterkino.kinoapi.dolby.IDolbyStatusUpdateReceiver;
import de.schunterkino.kinoapi.dolby.InputMode;
import de.schunterkino.kinoapi.listen.IServerSocketStatusUpdateReceiver;
import de.schunterkino.kinoapi.listen.SchunterServerSocket;
import de.schunterkino.kinoapi.sockets.BaseSocketClient;

/**
 * This is an example program that demonstrates how to play back an audio file
 * using the Clip in Java Sound API.
 * 
 * @author www.codejava.net
 *
 */
public class AudioPlayer implements LineListener, IDolbyStatusUpdateReceiver, IServerSocketStatusUpdateReceiver {

	private BaseSocketClient<DolbySocketCommands, IDolbyStatusUpdateReceiver, DolbyCommand> dolby;

	private boolean lampTurnedOff;
	InputMode oldInputMode;
	Thread playThread = null;

	public AudioPlayer(BaseSocketClient<DolbySocketCommands, IDolbyStatusUpdateReceiver, DolbyCommand> dolby,
			SchunterServerSocket server) {
		this.dolby = dolby;
		dolby.getCommands().registerListener(this);
		server.registerListener(this);
	}

	/**
	 * this flag indicates whether the playback completes or not.
	 */
	boolean playCompleted;

	/**
	 * Play a given audio file.
	 * 
	 * @param audioFilePath
	 *            Path of the audio file.
	 */
	void play(String audioFilePath) {
		File audioFile = new File(audioFilePath);

		// Try to play the audio in a seperate non-blocking thread.
		playThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);

					AudioFormat format = audioStream.getFormat();

					DataLine.Info info = new DataLine.Info(Clip.class, format);

					Clip audioClip = (Clip) AudioSystem.getLine(info);

					audioClip.addLineListener(AudioPlayer.this);

					audioClip.open(audioStream);

					playCompleted = false;
					audioClip.start();

					try {
						while (!playCompleted) {
							// wait for the playback completes
							Thread.sleep(500);
						}
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}

					audioClip.close();
					audioStream.close();

				} catch (UnsupportedAudioFileException ex) {
					System.out.println("The specified audio file is not supported.");
					ex.printStackTrace();
				} catch (LineUnavailableException ex) {
					System.out.println("Audio line for playing back is unavailable.");
					ex.printStackTrace();
				} catch (IOException ex) {
					System.out.println("Error playing the audio file.");
					ex.printStackTrace();
				}
				playThread = null;
			}
		});
		playThread.start();
	}

	/**
	 * Listens to the START and STOP events of the audio line.
	 */
	@Override
	public void update(LineEvent event) {
		LineEvent.Type type = event.getType();

		if (type == LineEvent.Type.START) {
			System.out.println("Playback started.");

		} else if (type == LineEvent.Type.STOP) {
			playCompleted = true;
			System.out.println("Playback completed.");
			if (dolby.isConnected())
				dolby.getCommands().setInputMode(oldInputMode);
		}

	}

	@Override
	public void onDolbyConnected() {
	}

	@Override
	public void onDolbyDisconnected() {
	}

	@Override
	public void onVolumeChanged(int volume) {
	}

	@Override
	public void onMuteStatusChanged(boolean muted) {
	}

	@Override
	public void onInputModeChanged(InputMode mode) {
		// We didn't change the input mode ourselves. Don't care.
		if (!lampTurnedOff)
			return;

		// Play a nice sound, now that the lamp is cooled off.
		lampTurnedOff = false;
		play("sounds/yeah1.wav");
	}

	@Override
	public void onDecodeModeChanged(DecodeMode mode) {
	}

	@Override
	public void onLampTurnedOff(Instant lampOffTime) {
		// Can't do anything if no Audio.
		if (!dolby.isConnected() || dolby.getCommands().isMuted())
			return;

		// Can't process this too fast again.
		if (lampTurnedOff)
			return;

		// Save current input source of audio.
		oldInputMode = dolby.getCommands().getInputMode();

		// TODO: Turn volume down?

		// Switch to Non Sync - where we're connected to!
		lampTurnedOff = true;
		dolby.getCommands().setInputMode(InputMode.NonSync);
	}

	public void stopSound() {
		if (playThread != null) {
			playThread.interrupt();
			try {
				playThread.join();
			} catch (InterruptedException e) {
				// Just stahp.
			}
			playThread = null;
		}
	}
}