package de.schunterkino.kinoapi.websocket;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedList;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.xml.bind.DatatypeConverter;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import de.schunterkino.kinoapi.christie.ChristieCommand;
import de.schunterkino.kinoapi.christie.ChristieSocketCommands;
import de.schunterkino.kinoapi.christie.IChristieStatusUpdateReceiver;
import de.schunterkino.kinoapi.christie.serial.ISolariaSerialStatusUpdateReceiver;
import de.schunterkino.kinoapi.christie.serial.PowerMode;
import de.schunterkino.kinoapi.christie.serial.SolariaCommand;
import de.schunterkino.kinoapi.christie.serial.SolariaSocketCommands;
import de.schunterkino.kinoapi.dolby.DecodeMode;
import de.schunterkino.kinoapi.dolby.DolbyCommand;
import de.schunterkino.kinoapi.dolby.DolbySocketCommands;
import de.schunterkino.kinoapi.dolby.IDolbyStatusUpdateReceiver;
import de.schunterkino.kinoapi.dolby.InputMode;
import de.schunterkino.kinoapi.jnior.IJniorStatusUpdateReceiver;
import de.schunterkino.kinoapi.jnior.JniorCommand;
import de.schunterkino.kinoapi.jnior.JniorSocketCommands;
import de.schunterkino.kinoapi.listen.IServerSocketStatusUpdateReceiver;
import de.schunterkino.kinoapi.listen.SchunterServerSocket;
import de.schunterkino.kinoapi.sockets.BaseSerialPortClient;
import de.schunterkino.kinoapi.sockets.BaseSocketClient;
import de.schunterkino.kinoapi.websocket.messages.BaseMessage;
import de.schunterkino.kinoapi.websocket.messages.ErrorMessage;
import de.schunterkino.kinoapi.websocket.messages.christie.ChristieConnectionMessage;
import de.schunterkino.kinoapi.websocket.messages.christie.LampOffMessage;
import de.schunterkino.kinoapi.websocket.messages.jnior.LightsConnectionMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.DecodeModeChangedMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.DolbyConnectionMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.InputModeChangedMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.MuteStatusChangedMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.PowerModeChangedMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.VolumeChangedMessage;

/**
 * WebSocket server class which serves the documented JSON API. This class acts
 * as a proxy and forwards the events from the different hardware connections -
 * like volume changes on the Dolby audio processor - to all connected websocket
 * clients.
 * 
 * @see API.md
 */
public class CinemaWebSocketServer extends WebSocketServer
		implements IDolbyStatusUpdateReceiver, IJniorStatusUpdateReceiver, IChristieStatusUpdateReceiver,
		ISolariaSerialStatusUpdateReceiver, IServerSocketStatusUpdateReceiver {

	/**
	 * Google JSON instance to convert Java objects into JSON objects.
	 * {@link https://github.com/google/gson/blob/master/UserGuide.md}
	 */
	private Gson gson;

	/**
	 * Socket connection and protocol handler for the Dolby CP750.
	 */
	private BaseSocketClient<DolbySocketCommands, IDolbyStatusUpdateReceiver, DolbyCommand> dolby;

	/**
	 * Socket connection and protocol handler for the Integ Jnior 310 automation
	 * box.
	 */
	private BaseSocketClient<JniorSocketCommands, IJniorStatusUpdateReceiver, JniorCommand> jnior;

	/**
	 * Socket connection to trigger global triggers on the Christie IMB.
	 */
	private BaseSocketClient<ChristieSocketCommands, IChristieStatusUpdateReceiver, ChristieCommand> christie;

	/**
	 * Serial connection to the PIB to get projector hardware updates.
	 */
	private BaseSerialPortClient<SolariaSocketCommands, ISolariaSerialStatusUpdateReceiver, SolariaCommand> solaria;

	/**
	 * Server socket which is used to listen to the event of the projector lamp
	 * being off.
	 */
	private SchunterServerSocket server;

	/**
	 * List of JSON protocol incoming command handlers. Incoming messages on the
	 * WebSockets are passed to the handlers until one claims responsibility for
	 * the packet.
	 */
	private LinkedList<IWebSocketMessageHandler> messageHandlers;

	/**
	 * Creates a CinemaWebSocketServer on the desired port.
	 * 
	 * @param port
	 *            The desired server port to listen on.
	 * @param dolby
	 *            Instance of Dolby socket client.
	 * @param jnior
	 *            Instance of Jnior socket client.
	 */
	public CinemaWebSocketServer(int port,
			BaseSocketClient<DolbySocketCommands, IDolbyStatusUpdateReceiver, DolbyCommand> dolby,
			BaseSocketClient<JniorSocketCommands, IJniorStatusUpdateReceiver, JniorCommand> jnior,
			BaseSocketClient<ChristieSocketCommands, IChristieStatusUpdateReceiver, ChristieCommand> christie,
			BaseSerialPortClient<SolariaSocketCommands, ISolariaSerialStatusUpdateReceiver, SolariaCommand> solaria,
			SchunterServerSocket server) {
		super(new InetSocketAddress(port));

		this.gson = new Gson();
		this.messageHandlers = new LinkedList<>();

		// Start listening for dolby events.
		this.dolby = dolby;
		// Start listening for Dolby events like volume changes.
		dolby.getCommands().registerListener(this);
		messageHandlers.add(dolby.getCommands());

		this.jnior = jnior;
		// Start listening for Jnior events like connection updates.
		jnior.getCommands().registerListener(this);
		messageHandlers.add(jnior.getCommands());

		this.christie = christie;
		// Start listening for Christie IMB events like connection updates.
		christie.getCommands().registerListener(this);
		messageHandlers.add(christie.getCommands());

		this.solaria = solaria;
		solaria.getCommands().registerListener(this);
		messageHandlers.add(solaria.getCommands());

		this.server = server;
		server.registerListener(this);
	}

	@Override
	public void start() {

		// Setup the SSL context for WSS support.
		// WebSocketImpl.DEBUG = true;

		SSLContext context = getSSLContext();
		if (context != null) {
			setWebSocketFactory(new DefaultSSLWebSocketServerFactory(context));
		}
		setConnectionLostTimeout(30);

		super.start();
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		System.out.println("WebSocket: " + prettySocket(conn) + " connected!");

		// Inform the new client of the current status.

		// Let the client know if we were able to connect to the Dolby audio
		// processor.
		conn.send(gson.toJson(new DolbyConnectionMessage(dolby.isConnected())));
		if (dolby.isConnected()) {
			// If we're connected, send the current status too.
			conn.send(gson.toJson(new VolumeChangedMessage(dolby.getCommands().getVolume())));
			conn.send(gson.toJson(new MuteStatusChangedMessage(dolby.getCommands().isMuted())));
			conn.send(gson.toJson(new InputModeChangedMessage(dolby.getCommands().getInputMode())));
			conn.send(gson.toJson(new DecodeModeChangedMessage(dolby.getCommands().getDecodeMode())));
		}

		// Also tell the client if we have a connection to the Jnior box.
		conn.send(gson.toJson(new LightsConnectionMessage(jnior.isConnected())));

		// And if the projector is up.
		conn.send(gson.toJson(new ChristieConnectionMessage(christie.isConnected())));
		if (christie.isConnected()) {
			Instant lampOffTime = server.getLampOffTime();
			// Only send the lamp off time if it's been max. 2 hours ago.
			if (lampOffTime != null && Duration.between(lampOffTime, Instant.now()).toHours() < 2)
				conn.send(gson.toJson(new LampOffMessage(lampOffTime)));
		}

		// Tell which part of the projector is currently enabled.
		if (solaria.isConnected()) {
			conn.send(gson.toJson(new PowerModeChangedMessage(solaria.getCommands().getPowerMode(),
					solaria.getCommands().getPowerModeChangedTimestamp(), solaria.getCommands().getCooldownTime())));
		}
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		System.out.println("WebSocket: " + prettySocket(conn) + " disconnected!");
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
		if (conn != null) {
			// some errors like port binding failed may not be assignable to a
			// specific websocket
			System.err.println("WebSocket: ChildSocket " + prettySocket(conn) + " error: " + ex.getMessage());
		} else {
			// TODO: Stop the application or try to start the server again in a
			// bit.
			System.err.println("WebSocket: ServerSocket error: " + ex.getMessage());
		}
	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		System.out.println("WebSocket: " + prettySocket(conn) + ": " + message);

		try {
			// Try to parse this message as JSON and try to extract the message
			// type.
			BaseMessage baseMsg = gson.fromJson(message, BaseMessage.class);

			// Make sure the required fields are set in the JSON object.
			if (baseMsg.getMessageType() == null || baseMsg.getAction() == null) {
				System.err.println(
						"Websocket: Invalid JSON message from " + conn + " (missing required fields): " + message);
				conn.send(gson.toJson(new ErrorMessage(
						"Malformed message. Messages MUST include a \"msg_type\" and an \"action\".")));
				return;
			}

			// Run through all handlers and see if one of them knows what to do
			// with that message.
			try {
				for (IWebSocketMessageHandler handler : messageHandlers) {
					if (handler.onMessage(baseMsg, message))
						return;
				}

			} catch (WebSocketCommandException e) {
				// Tell the client why the command failed.
				conn.send(gson.toJson(new ErrorMessage(e.getMessage())));
				return;
			}

			// No message handler was able to handle that message. Tell the
			// client!
			System.err.println("Websocket: Unhandled command from " + prettySocket(conn) + ": " + message);
			conn.send(gson.toJson(
					new ErrorMessage("Unhandled command: " + baseMsg.getMessageType() + " - " + baseMsg.getAction())));
		} catch (JsonSyntaxException e) {
			System.err.println("Websocket: Error parsing message from " + prettySocket(conn) + ": " + e.getMessage());
			conn.send(gson.toJson(new ErrorMessage(e.getMessage())));
		}
	}

	@Override
	public void onStart() {
		System.out.println("Websocket server started!");
	}

	/**
	 * Sends <var>text</var> to all currently connected WebSocket clients.
	 * 
	 * @param text
	 *            The String to send across the network.
	 * @throws InterruptedException
	 *             When socket related I/O errors occur.
	 */
	public void sendToAll(String text) {
		Collection<WebSocket> con = connections();
		synchronized (con) {
			for (WebSocket c : con) {
				// Only send to fully connected websockets.
				if (!c.isOpen())
					continue;
				c.send(text);
			}
		}
	}

	private static String prettySocket(WebSocket conn) {
		return conn.getRemoteSocketAddress().getAddress().getHostAddress() + ":"
				+ conn.getRemoteSocketAddress().getPort();
	}

	@Override
	public void onDolbyConnected() {
		DolbyConnectionMessage msg = new DolbyConnectionMessage(true);
		sendToAll(gson.toJson(msg));
	}

	@Override
	public void onDolbyDisconnected() {
		DolbyConnectionMessage msg = new DolbyConnectionMessage(false);
		sendToAll(gson.toJson(msg));
	}

	@Override
	public void onVolumeChanged(int volume) {
		VolumeChangedMessage msg = new VolumeChangedMessage(volume);
		sendToAll(gson.toJson(msg));
	}

	@Override
	public void onMuteStatusChanged(boolean muted) {
		MuteStatusChangedMessage msg = new MuteStatusChangedMessage(muted);
		sendToAll(gson.toJson(msg));
	}

	@Override
	public void onJniorConnected() {
		LightsConnectionMessage msg = new LightsConnectionMessage(true);
		sendToAll(gson.toJson(msg));
	}

	@Override
	public void onJniorDisconnected() {
		LightsConnectionMessage msg = new LightsConnectionMessage(false);
		sendToAll(gson.toJson(msg));
	}

	@Override
	public void onInputModeChanged(InputMode mode) {
		InputModeChangedMessage msg = new InputModeChangedMessage(mode);
		sendToAll(gson.toJson(msg));
	}

	@Override
	public void onDecodeModeChanged(DecodeMode mode) {
		DecodeModeChangedMessage msg = new DecodeModeChangedMessage(mode);
		sendToAll(gson.toJson(msg));
	}

	@Override
	public void onChristieConnected() {
		ChristieConnectionMessage msg = new ChristieConnectionMessage(true);
		sendToAll(gson.toJson(msg));
	}

	@Override
	public void onChristieDisconnected() {
		ChristieConnectionMessage msg = new ChristieConnectionMessage(false);
		sendToAll(gson.toJson(msg));
	}

	@Override
	public void onLampTurnedOff(Instant lampOffTime) {
		LampOffMessage msg = new LampOffMessage(lampOffTime);
		sendToAll(gson.toJson(msg));
	}

	@Override
	public void onPowerModeChanged(PowerMode mode, PowerMode oldPowerMode, Instant timestamp, Integer cooldown) {
		PowerModeChangedMessage msg = new PowerModeChangedMessage(mode, timestamp, cooldown);
		sendToAll(gson.toJson(msg));
	}

	/*
	 * SSL helpers
	 */
	private SSLContext getSSLContext() {
		SSLContext context;
		String password = "noonewillseethisongit"; // TODO put into config :B
		String pathname = "/etc/letsencrypt/live/remote.schunterkino.de";
		try {
			context = SSLContext.getInstance("TLS");

			byte[] certBytes = parseDERFromPEM(getBytes(new File(pathname + File.separator + "cert.pem")),
					"-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----");
			byte[] keyBytes = parseDERFromPEM(getBytes(new File(pathname + File.separator + "privkey.pem")),
					"-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----");

			X509Certificate cert = generateCertificateFromDER(certBytes);
			RSAPrivateKey key = generatePrivateKeyFromDER(keyBytes);

			KeyStore keystore = KeyStore.getInstance("JKS");
			keystore.load(null);
			keystore.setCertificateEntry("cert-alias", cert);
			keystore.setKeyEntry("key-alias", key, password.toCharArray(), new Certificate[] { cert });

			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(keystore, password.toCharArray());

			KeyManager[] km = kmf.getKeyManagers();

			context.init(km, null, null);
		} catch (Exception e) {
			context = null;
		}
		return context;
	}

	private byte[] parseDERFromPEM(byte[] pem, String beginDelimiter, String endDelimiter) {
		String data = new String(pem);
		String[] tokens = data.split(beginDelimiter);
		tokens = tokens[1].split(endDelimiter);
		return DatatypeConverter.parseBase64Binary(tokens[0]);
	}

	private RSAPrivateKey generatePrivateKeyFromDER(byte[] keyBytes)
			throws InvalidKeySpecException, NoSuchAlgorithmException {
		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);

		KeyFactory factory = KeyFactory.getInstance("RSA");

		return (RSAPrivateKey) factory.generatePrivate(spec);
	}

	private X509Certificate generateCertificateFromDER(byte[] certBytes) throws CertificateException {
		CertificateFactory factory = CertificateFactory.getInstance("X.509");

		return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
	}

	private byte[] getBytes(File file) {
		byte[] bytesArray = new byte[(int) file.length()];

		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			fis.read(bytesArray); // read file into bytes[]
			fis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return bytesArray;
	}
}
