package de.schunterkino.kinoapi.websocket.messages;

public class SetLightLevelMessage extends BaseMessage {
	private int level;

	public SetLightLevelMessage(int level) {
		super("lights", "set_light_level");
		this.level = level;
	}

	public int getLightLevel() {
		return level;
	}

}
