package de.schunterkino.kinoapi.websocket.messages;

public class SetLightLevelMessage extends BaseMessage {
	private int light_level;

	public SetLightLevelMessage(int light_level) {
		super("lights", "set_light_level");
		this.light_level = light_level;
	}

	public int getLightLevel() {
		return light_level;
	}

}
