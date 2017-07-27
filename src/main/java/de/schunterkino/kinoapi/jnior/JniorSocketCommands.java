package de.schunterkino.kinoapi.jnior;

import com.google.gson.JsonSyntaxException;

import de.schunterkino.kinoapi.sockets.BaseSocketCommands;
import de.schunterkino.kinoapi.sockets.CommandContainer;
import de.schunterkino.kinoapi.websocket.WebSocketCommandException;
import de.schunterkino.kinoapi.websocket.messages.BaseMessage;
import de.schunterkino.kinoapi.websocket.messages.jnior.SetLightLevelMessage;

public class JniorSocketCommands extends BaseSocketCommands<IJniorStatusUpdateReceiver, JniorCommand> {

	protected int UPDATE_INTERVAL = 10000;
	
	public JniorSocketCommands() {
		super();
		ignoreResponses();
	}
	
	@Override
	protected void onSocketConnected() {
		// Notify listeners.
		synchronized (listeners) {
			for (IJniorStatusUpdateReceiver listener : listeners) {
				listener.onJniorConnected();
			}
		}
	}

	@Override
	protected void onSocketDisconnected() {
		// Notify listeners.
		synchronized (listeners) {
			for (IJniorStatusUpdateReceiver listener : listeners) {
				listener.onJniorDisconnected();
			}
		}
	}

	@Override
	protected boolean onReceiveCommandOutput(String input) {
		return true; // Don't care for stuff sent to us.
	}

	@Override
	protected String getCommandString(CommandContainer<JniorCommand> cmd) {
		String command = null;
		switch (cmd.cmd) {
		case SetLightLevel:
			switch (cmd.value) {
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
		return command;
	}

	

	public void setLightLevel(int level) {
		addCommand(JniorCommand.SetLightLevel, level);
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
