package com.foodcore.util;

import com.foodcore.events.OutboxRepository;
import com.foodcore.exception.TerminalNoAutorizadaException;
import com.foodcore.model.dto.EventoKdsDTO;
import com.foodcore.security.TerminalAuthService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servidor TCP para comunicación autenticada en tiempo real entre Salón (POS) y Cocina (KDS)
 * según SPEC-FOODCORE-v1.0 (§10.4, §11; REMEDIATION R-13, R-14, R-02.7).
 */
public class ServidorSockets {

    private static final Logger LOGGER = Logger.getLogger(ServidorSockets.class.getName());
    public static final int PUERTO_TCP = 9090;
    public static final int MAX_FRAME_BYTES = TerminalAuthService.MAX_FRAME_BYTES; // 1 MB

    private static ServidorSockets instance;
    private ServerSocket serverSocket;
    private ExecutorService threadPool = Executors.newCachedThreadPool();
    private final Set<PrintWriter> clientesConectados = Collections.synchronizedSet(new HashSet<>());
    private final TerminalAuthService terminalAuthService = new TerminalAuthService();
    private final OutboxRepository outboxRepository = new OutboxRepository();
    private Consumer<JsonObject> mensajeListener;
    private volatile boolean corriendo = false;
    private int puertoActual = PUERTO_TCP;

    private ServidorSockets() {
    }

    public static synchronized ServidorSockets getInstance() {
        if (instance == null) {
            instance = new ServidorSockets();
        }
        return instance;
    }

    public synchronized void iniciar() {
        iniciar(PUERTO_TCP);
    }

    public synchronized void iniciar(int puerto) {
        if (corriendo) {
            return;
        }
        if (threadPool.isShutdown()) {
            threadPool = Executors.newCachedThreadPool();
        }
        this.puertoActual = puerto;
        corriendo = true;
        threadPool.execute(() -> {
            try {
                serverSocket = new ServerSocket(puerto);
                LOGGER.info("Servidor de Sockets escuchando en el puerto " + puerto);

                while (corriendo && !serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    atenderCliente(clientSocket);
                }
            } catch (Exception e) {
                if (corriendo) {
                    LOGGER.log(Level.WARNING, "Aviso en ServidorSockets", e);
                }
            }
        });
    }

    private void atenderCliente(Socket socket) {
        threadPool.execute(() -> {
            PrintWriter out = null;
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                clientesConectados.add(out);

                String linea;
                while ((linea = in.readLine()) != null) {
                    JsonObject respuesta = procesarTramaProtocolo(linea, out);
                    if (respuesta != null) {
                        out.println(respuesta.toString());
                    }
                }
            } catch (Exception e) {
                LOGGER.fine("Cliente desconectado: " + socket.getRemoteSocketAddress());
            } finally {
                if (out != null) {
                    clientesConectados.remove(out);
                }
                try {
                    socket.close();
                } catch (Exception e) {
                    LOGGER.log(Level.FINEST, "Cierre de socket cliente", e);
                }
            }
        });
    }

    /**
     * Procesa una trama del protocolo KDS (HELLO, RENEW, ACK, EVENT con HMAC-SHA256 y límite de 1 MB).
     */
    public JsonObject procesarTramaProtocolo(String linea, PrintWriter remitente) {
        if (linea == null) {
            return crearError("EMPTY_FRAME", "Trama vacía");
        }
        int byteLen = linea.getBytes(StandardCharsets.UTF_8).length;
        if (byteLen > MAX_FRAME_BYTES) {
            LOGGER.warning("Trama rechazada por exceder 1 MB (" + byteLen + " bytes).");
            return crearError("FRAME_TOO_LARGE", "El mensaje supera el límite máximo de 1 MB");
        }

        try {
            JsonObject json = JsonParser.parseString(linea).getAsJsonObject();
            String type = json.has("type") ? json.get("type").getAsString() : "";

            if ("HELLO".equalsIgnoreCase(type) || "AUTH_TERMINAL".equalsIgnoreCase(type)) {
                String codigoTerminal = json.has("codigoTerminal") ? json.get("codigoTerminal").getAsString() : "";
                String secreto = json.has("secreto") ? json.get("secreto").getAsString() : "";
                try {
                    TerminalAuthService.SesionTerminal sesion = terminalAuthService.autenticar(codigoTerminal, secreto);
                    JsonObject welcome = new JsonObject();
                    welcome.addProperty("type", "WELCOME");
                    welcome.addProperty("sessionToken", sesion.getSessionToken());
                    welcome.addProperty("sessionSecret", sesion.getSessionSecret());
                    welcome.addProperty("ttl", sesion.getTtlSegundos());
                    return welcome;
                } catch (TerminalNoAutorizadaException ex) {
                    return crearError("TerminalNoAutorizada", ex.getMessage());
                }
            }

            if ("RENEW".equalsIgnoreCase(type)) {
                String token = json.has("sessionToken") ? json.get("sessionToken").getAsString() : "";
                try {
                    TerminalAuthService.SesionTerminal renovada = terminalAuthService.renovar(token);
                    JsonObject welcome = new JsonObject();
                    welcome.addProperty("type", "WELCOME");
                    welcome.addProperty("sessionToken", renovada.getSessionToken());
                    welcome.addProperty("sessionSecret", renovada.getSessionSecret());
                    welcome.addProperty("ttl", renovada.getTtlSegundos());
                    return welcome;
                } catch (TerminalNoAutorizadaException ex) {
                    return crearError("TerminalNoAutorizada", ex.getMessage());
                }
            }

            if ("ACK".equalsIgnoreCase(type)) {
                long idEvento = json.has("idEvento") ? json.get("idEvento").getAsLong() : 0L;
                if (idEvento > 0) {
                    try (Connection conn = ConexionBD.getConnection()) {
                        outboxRepository.marcarEnviado(conn, idEvento);
                    }
                }
                JsonObject ackOk = new JsonObject();
                ackOk.addProperty("type", "ACK_CONFIRMED");
                ackOk.addProperty("idEvento", idEvento);
                return ackOk;
            }

            if ("EVENT".equalsIgnoreCase(type)) {
                String sessionToken = json.has("sessionToken") ? json.get("sessionToken").getAsString() : "";
                TerminalAuthService.SesionTerminal sesion = terminalAuthService.obtenerSesion(sessionToken);
                if (sesion == null) {
                    return crearError("TerminalNoAutorizada", "Sesión no autenticada");
                }
                EventoKdsDTO payload = EventoKdsDTO.fromJson(json.get("payload").toString());
                if (payload == null || !payload.verificarHmac(sesion.getSessionSecret())) {
                    return crearError("HMAC_INVALIDO", "Firma HMAC-SHA256 adulterada o expirada");
                }
                if (mensajeListener != null) {
                    mensajeListener.accept(json);
                }
                transmitirATodos(linea, remitente);
                JsonObject ack = new JsonObject();
                ack.addProperty("type", "ACK");
                ack.addProperty("eventId", payload.getEventId());
                return ack;
            }

            if (mensajeListener != null) {
                mensajeListener.accept(json);
            }
            if (remitente != null) {
                transmitirATodos(linea, remitente);
            }
            return null;
        } catch (Exception parseEx) {
            return crearError("INVALID_JSON", "Mensaje JSON malformado");
        }
    }

    private JsonObject crearError(String code, String message) {
        JsonObject err = new JsonObject();
        err.addProperty("type", "ERROR");
        err.addProperty("code", code);
        err.addProperty("message", message);
        return err;
    }

    public void transmitirATodos(String mensaje, PrintWriter remitente) {
        synchronized (clientesConectados) {
            for (PrintWriter writer : clientesConectados) {
                if (writer != remitente) {
                    writer.println(mensaje);
                }
            }
        }
    }

    public void emitirMensaje(JsonObject mensaje) {
        String jsonStr = new Gson().toJson(mensaje);
        synchronized (clientesConectados) {
            for (PrintWriter writer : clientesConectados) {
                writer.println(jsonStr);
            }
        }
    }

    public void setMensajeListener(Consumer<JsonObject> listener) {
        this.mensajeListener = listener;
    }

    public TerminalAuthService getTerminalAuthService() {
        return terminalAuthService;
    }

    public void cerrarSesionesActivas() {
        terminalAuthService.limpiarSesiones();
    }

    public synchronized void detener() {
        corriendo = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            threadPool.shutdownNow();
            LOGGER.info("Servidor de Sockets detenido.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al detener ServidorSockets", e);
        }
    }
}
