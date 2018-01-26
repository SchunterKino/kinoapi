package de.schunterkino.kinoapi.websocket;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpCookie;
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
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.xml.bind.DatatypeConverter;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import de.schunterkino.kinoapi.App;
import de.schunterkino.kinoapi.christie.ChristieCommand;
import de.schunterkino.kinoapi.christie.ChristieSocketCommands;
import de.schunterkino.kinoapi.christie.IChristieStatusUpdateReceiver;
import de.schunterkino.kinoapi.christie.serial.ISolariaSerialStatusUpdateReceiver;
import de.schunterkino.kinoapi.christie.serial.LampState;
import de.schunterkino.kinoapi.christie.serial.PowerState;
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
import de.schunterkino.kinoapi.websocket.messages.christie.DouserChangedMessage;
import de.schunterkino.kinoapi.websocket.messages.christie.IMBConnectionMessage;
import de.schunterkino.kinoapi.websocket.messages.christie.LampChangedMessage;
import de.schunterkino.kinoapi.websocket.messages.christie.LampOffMessage;
import de.schunterkino.kinoapi.websocket.messages.christie.PIBConnectionMessage;
import de.schunterkino.kinoapi.websocket.messages.christie.PowerChangedMessage;
import de.schunterkino.kinoapi.websocket.messages.jnior.LightsConnectionMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.DecodeModeChangedMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.DolbyConnectionMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.InputModeChangedMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.MuteStatusChangedMessage;
import de.schunterkino.kinoapi.websocket.messages.volume.VolumeChangedMessage;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.impl.TextCodec;

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

	// Close the websocket if there are problems with the token.
	public static final int AUTH_INVALID_TOKEN_ERROR_CODE = 4401;
	public static final int AUTH_TOKEN_EXPIRED_ERROR_CODE = 4402;

	// The number of threads that will be used to process the incoming network data.
	// By default this will be Runtime.getRuntime().availableProcessors() which is
	// 1 on a raspberry pi.
	public static final int DECODER_POOL_SIZE = 4;

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
	 * WebSockets are passed to the handlers until one claims responsibility for the
	 * packet.
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
		super(new InetSocketAddress(port), DECODER_POOL_SIZE);

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

	private void validateToken(ClientHandshake request) throws InvalidDataException {
		if (!request.hasFieldValue("Cookie"))
			throw new InvalidDataException(AUTH_INVALID_TOKEN_ERROR_CODE, "Token missing.");

		List<HttpCookie> cookie = HttpCookie.parse(request.getFieldValue("Cookie"));
		// Malformed cookie.
		if (cookie.size() != 1)
			throw new InvalidDataException(AUTH_INVALID_TOKEN_ERROR_CODE, "Not accepted!");

		HttpCookie tokenCookie = cookie.get(0);
		// Invalid cookie name.
		if (!tokenCookie.getName().equals("token"))
			throw new InvalidDataException(AUTH_INVALID_TOKEN_ERROR_CODE, "Not accepted!");

		String compactJws = tokenCookie.getValue();
		// System.out.println("Token is " + compactJws);

		// Validate the signature and the subject. The subject is just there
		// because.
		try {
			Jwts.parser().requireSubject("SchunterKinoRemote")
					.setSigningKey(TextCodec.BASE64.decode(App.getConfigurationString("jws_signature_key")))
					.parseClaimsJws(compactJws);
		} catch (ExpiredJwtException e) {
			System.err.println("Expired! " + e.getMessage());
			// e.printStackTrace();
			throw new InvalidDataException(AUTH_TOKEN_EXPIRED_ERROR_CODE, "Token expired.");
		} catch (Exception e) {
			System.err.println(e.getMessage());
			// e.printStackTrace();
			throw new InvalidDataException(AUTH_INVALID_TOKEN_ERROR_CODE, "Invalid token.");
		}
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		System.out.println("WebSocket: " + prettySocket(conn) + " connected!");

		// Try to validate the token in the handshake cookie.
		// Close the connection correctly if there's a problem.
		try {
			validateToken(handshake);
		} catch (InvalidDataException e) {
			System.err.println("WebSocket: " + prettySocket(conn) + " failed to authenticate: " + e.getMessage());
			conn.close(e.getCloseCode(), e.getMessage());
			return;
		}

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
		conn.send(gson.toJson(new IMBConnectionMessage(christie.isConnected())));
		if (christie.isConnected()) {
			Instant lampOffTime = server.getLampOffTime();
			// Only send the lamp off time if it's been max. 2 hours ago.
			if (lampOffTime != null && Duration.between(lampOffTime, Instant.now()).toHours() < 2)
				conn.send(gson.toJson(new LampOffMessage(lampOffTime)));
		}

		// Tell which part of the projector is currently enabled.
		conn.send(gson.toJson(new PIBConnectionMessage(solaria.isConnected())));
		if (solaria.isConnected()) {
			conn.send(gson.toJson(new PowerChangedMessage(solaria.getCommands().getPowerState(),
					solaria.getCommands().getPowerStateChangedTimestamp())));

			conn.send(gson.toJson(new LampChangedMessage(solaria.getCommands().getLampState(),
					solaria.getCommands().getLampStateChangedTimestamp(), solaria.getCommands().getCooldownTime())));

			conn.send(gson.toJson(new DouserChangedMessage(solaria.getCommands().isDouserOpen())));
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
		ex.printStackTrace();
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

	private static String prettySocket(WebSocket conn) {
		// We can't get the IP if this socket isn't open anymore.
		if (conn.getRemoteSocketAddress() == null)
			return "unconnected(" + conn + ")";

		return conn.getRemoteSocketAddress().getAddress().getHostAddress() + ":"
				+ conn.getRemoteSocketAddress().getPort();
	}

	@Override
	public void onDolbyConnected() {
		DolbyConnectionMessage msg = new DolbyConnectionMessage(true);
		broadcast(gson.toJson(msg));
	}

	@Override
	public void onDolbyDisconnected() {
		DolbyConnectionMessage msg = new DolbyConnectionMessage(false);
		broadcast(gson.toJson(msg));
	}

	@Override
	public void onVolumeChanged(int volume) {
		VolumeChangedMessage msg = new VolumeChangedMessage(volume);
		broadcast(gson.toJson(msg));
	}

	@Override
	public void onMuteStatusChanged(boolean muted) {
		MuteStatusChangedMessage msg = new MuteStatusChangedMessage(muted);
		broadcast(gson.toJson(msg));
	}

	@Override
	public void onJniorConnected() {
		LightsConnectionMessage msg = new LightsConnectionMessage(true);
		broadcast(gson.toJson(msg));
	}

	@Override
	public void onJniorDisconnected() {
		LightsConnectionMessage msg = new LightsConnectionMessage(false);
		broadcast(gson.toJson(msg));
	}

	@Override
	public void onInputModeChanged(InputMode mode) {
		InputModeChangedMessage msg = new InputModeChangedMessage(mode);
		broadcast(gson.toJson(msg));
	}

	@Override
	public void onDecodeModeChanged(DecodeMode mode) {
		DecodeModeChangedMessage msg = new DecodeModeChangedMessage(mode);
		broadcast(gson.toJson(msg));
	}

	@Override
	public void onChristieConnected() {
		IMBConnectionMessage msg = new IMBConnectionMessage(true);
		broadcast(gson.toJson(msg));
	}

	@Override
	public void onChristieDisconnected() {
		IMBConnectionMessage msg = new IMBConnectionMessage(false);
		broadcast(gson.toJson(msg));
	}

	@Override
	public void onLampTurnedOff(Instant lampOffTime) {
		LampOffMessage msg = new LampOffMessage(lampOffTime);
		broadcast(gson.toJson(msg));
	}

	@Override
	public void onSolariaConnected() {
		PIBConnectionMessage msg = new PIBConnectionMessage(true);
		broadcast(gson.toJson(msg));
	}

	@Override
	public void onSolariaDisconnected() {
		PIBConnectionMessage msg = new PIBConnectionMessage(false);
		broadcast(gson.toJson(msg));
	}

	@Override
	public void onPowerStateChanged(PowerState state, Instant timestamp) {
		PowerChangedMessage msg = new PowerChangedMessage(state, timestamp);
		broadcast(gson.toJson(msg));
	}

	@Override
	public void onLampStateChanged(LampState state, LampState oldState, Instant timestamp, Long cooldown) {
		LampChangedMessage msg = new LampChangedMessage(state, timestamp, cooldown);
		broadcast(gson.toJson(msg));
	}

	@Override
	public void onDouserStateChanged(boolean isopen) {
		DouserChangedMessage msg = new DouserChangedMessage(isopen);
		broadcast(gson.toJson(msg));
	}

	/*
	 * SSL helpers
	 */
	private SSLContext getSSLContext() {
		SSLContext context;
		String password = App.getConfigurationString("ssl_keystore_password");
		String pathname = App.getConfigurationString("ssl_basepath");
		try {
			context = SSLContext.getInstance("TLS");

			byte[] certBytes = parseDERFromPEM(
					getBytes(new File(pathname + File.separator + App.getConfigurationString("ssl_certificate_file"))),
					"-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----");
			byte[] keyBytes = parseDERFromPEM(
					getBytes(new File(pathname + File.separator + App.getConfigurationString("ssl_privatekey_file"))),
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
			System.err.printf("Error initializing SSL certificate. Websocket Server WON'T support SSL. Exception: %s%n",
					e.getMessage());
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

	private byte[] getBytes(File file) throws IOException {
		byte[] bytesArray = new byte[(int) file.length()];

		FileInputStream fis = new FileInputStream(file);
		fis.read(bytesArray); // read file into bytes[]
		fis.close();
		return bytesArray;
	}
}
