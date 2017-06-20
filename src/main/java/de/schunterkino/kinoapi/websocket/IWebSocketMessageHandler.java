package de.schunterkino.kinoapi.websocket;

import com.google.gson.JsonSyntaxException;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

public interface IWebSocketMessageHandler {
	boolean onMessage(BaseMessage base_msg, String message) throws WebSocketCommandException, JsonSyntaxException;
}
