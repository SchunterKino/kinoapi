package de.schunterkino.kinoapi.websocket;

import com.google.gson.JsonSyntaxException;

import de.schunterkino.kinoapi.websocket.messages.BaseMessage;

/**
 * Interface to enable a class to listen for WebSocket messages.
 * 
 * @see CinemaWebSocketServer
 *
 */
public interface IWebSocketMessageHandler {

	/**
	 * Called when a WebSocket client sent a well formatted JSON message.
	 * 
	 * @param baseMsg
	 *            The partially parsed message containing the message type and
	 *            action.
	 * @param message
	 *            The pure message sent by the client. Can be used to reparse
	 *            the JSON message to look for further fields.
	 * @return True if the message was handled, false to keep trying a different
	 *         handler.
	 * @throws WebSocketCommandException
	 *             Throw this exception if there is a problem with a command you
	 *             usually handle.
	 * @throws JsonSyntaxException
	 *             Thrown when there is a problem reparsing the JSON message.
	 */
	boolean onMessage(BaseMessage baseMsg, String message) throws WebSocketCommandException, JsonSyntaxException;
}
