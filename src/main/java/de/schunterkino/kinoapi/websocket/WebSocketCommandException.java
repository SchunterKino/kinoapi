package de.schunterkino.kinoapi.websocket;

/**
 * Thrown when there was a problem processing a WebSocket command.
 *
 */
@SuppressWarnings("serial")
public class WebSocketCommandException extends Exception {
	
	public WebSocketCommandException(String string) {
		super(string);
	}

}
