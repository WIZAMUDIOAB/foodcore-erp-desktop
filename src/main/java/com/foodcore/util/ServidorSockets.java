package com.foodcore.util;

import com.foodcore.events.OutboxRepository;
import com.foodcore.exception.TerminalNoAutorizadaException;
import com.foodcore.model.dto.EventoKdsDTO;
import com.foodcore.security.TerminalAuthService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servidor TCP para comunicación autenticada en tiempo real entre Salón (POS) y Cocina (KDS)
 * según SPEC-FOODCORE-v1.0 (§10.4, §11; REMEDIATION R-13, R-14, R-02.7; Hardening v5.7.1):
 * registro exclusivo tras handshake válido, lectura acotada anti-OOM por trama,
 * pool acotado + semáforo de 20 clientes concurrentes y timeout de lectura de 30s.
 */
public class ServidorSockets {

    private static final Logger LOGGER = Logger.getLogger(ServidorSockets.class.getName());
    public static final int PUERTO_TCP = 9090;
    public static final int MAX_FRAME_BYTES = TerminalAuthService.MAX_FRAME_BYTES; // 1 MB
    public static final int MAX_CLIENTES_CONCURRENTES = 20;
    public static final int SOCKET_READ_TIMEOUT_MS = 30_000;
    private static final Gson GSON = new Gson();

    private static ServidorSockets instance;
    private final AtomicReference<ServerSocket> serverSocket = new AtomicReference<>();
    private final Semaphore semaforoClientes = new Semaphore(MAX_CLIENTES_CONCURRENTES);
    private final AtomicInteger threadSeq = new AtomicInteger(1);
    private ExecutorService threadPool = crearPoolAcotado();
    private final Set<PrintWriter> clientesConectados = Collections.synchronizedSet(new HashSet<>());
    private final TerminalAuthService terminalAuthService = new TerminalAuthService();
    private final OutboxRepository outboxRepository = new OutboxRepository();
    private volatile Consumer<JsonObject> mensajeListener;
    private volatile boolean corriendo = false;
    private int puertoActual = PUERTO_TCP;

    static final class FrameTooLargeException extends IOException {
        private static final long serialVersionUID = 1L;
        private final int bytesLeidos;

        FrameTooLargeException(int bytesLeidos) {
            super("La trama excede el límite máximo permitido de " + MAX_FRAME_BYTES + " bytes.");
            this.bytesLeidos = bytesLeidos;
        }

        int getBytesLeidos() {
            return bytesLeidos;
        }
    }

    private ServidorSockets() {
    }

    private ExecutorService crearPoolAcotado() {
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "ServidorSockets-Worker-" + threadSeq.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2,
                MAX_CLIENTES_CONCURRENTES + 2,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(MAX_CLIENTES_CONCURRENTES),
                factory,
                new ThreadPoolExecutor.AbortPolicy()
        );
        pool.allowCoreThreadTimeOut(true);
        return pool;
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
            threadPool = crearPoolAcotado();
        }
        this.puertoActual = puerto;
        corriendo = true;
        CountDownLatch boundLatch = new CountDownLatch(1);
        threadPool.execute(() -> {
            ServerSocket ss = null;
            try {
                ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress(puerto));
                serverSocket.set(ss);
                this.puertoActual = ss.getLocalPort();
                boundLatch.countDown();
                LOGGER.info("Servidor de Sockets escuchando en el puerto " + this.puertoActual);

                while (corriendo && !ss.isClosed()) {
                    Socket clientSocket = ss.accept();
                    atenderCliente(clientSocket);
                }
            } catch (IOException | RuntimeException e) {
                boundLatch.countDown();
                if (ss != null && serverSocket.get() != ss && !ss.isClosed()) {
                    try {
                        ss.close();
                    } catch (IOException ignored) {
                        // Ignore close failure during failed bind cleanup
                    }
                }
                if (corriendo) {
                    LOGGER.log(Level.WARNING, "Aviso en ServidorSockets", e);
                }
            }
        });
        try {
            boolean bound = boundLatch.await(2, TimeUnit.SECONDS);
            if (!bound) {
                LOGGER.warning("Tiempo de espera agotado iniciando ServidorSockets en puerto " + puerto);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isCorriendo() {
        ServerSocket ss = serverSocket.get();
        return corriendo && ss != null && !ss.isClosed();
    }

    public int getPuertoActual() {
        return puertoActual;
    }

    /**
     * Retorna la cantidad de clientes/terminales KDS autenticados conectados actualmente al socket.
     */
    public int getCantidadClientesConectados() {
        synchronized (clientesConectados) {
            return clientesConectados.size();
        }
    }

    private void atenderCliente(Socket socket) {
        if (!semaforoClientes.tryAcquire()) {
            LOGGER.warning("Conexión KDS rechazada: límite máximo de " + MAX_CLIENTES_CONCURRENTES + " terminales alcanzado.");
            cerrarSocketSilencioso(socket);
            return;
        }
        try {
            ExecutorService poolActivo = obtenerThreadPool();
            poolActivo.execute(() -> ejecutarSesionCliente(socket));
        } catch (RejectedExecutionException ex) {
            semaforoClientes.release();
            LOGGER.warning("Pool de hilos de ServidorSockets saturado; cerrando conexión entrante.");
            cerrarSocketSilencioso(socket);
        }
    }

    private synchronized ExecutorService obtenerThreadPool() {
        return threadPool;
    }

    private void ejecutarSesionCliente(Socket socket) {
        PrintWriter out = null;
        try {
            socket.setSoTimeout(SOCKET_READ_TIMEOUT_MS);
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream(), 8192);
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String linea;
            while (corriendo && (linea = leerLineaAcotada(in, MAX_FRAME_BYTES)) != null) {
                JsonObject respuesta = procesarTramaProtocolo(linea, out);
                if (respuesta != null) {
                    out.println(respuesta.toString());
                }
            }
        } catch (FrameTooLargeException ftle) {
            LOGGER.warning("Trama rechazada durante lectura por exceder 1 MB (" + ftle.getBytesLeidos() + " bytes).");
            if (out != null) {
                out.println(crearError("FRAME_TOO_LARGE", "El mensaje supera el límite máximo de 1 MB").toString());
            }
        } catch (SocketTimeoutException ste) {
            LOGGER.fine("Timeout de lectura (30s) en socket cliente: " + socket.getRemoteSocketAddress());
        } catch (IOException | RuntimeException e) {
            LOGGER.fine("Cliente desconectado: " + socket.getRemoteSocketAddress());
        } finally {
            if (out != null) {
                clientesConectados.remove(out);
            }
            cerrarSocketSilencioso(socket);
            semaforoClientes.release();
        }
    }

    /**
     * Lee una línea terminada en {@code \n} imponiendo el límite {@code maxBytes} DURANTE la lectura
     * para prevenir ataques de agotamiento de memoria (OOM) mediante tramas sin salto de línea.
     */
    public static String leerLineaAcotada(InputStream in, int maxBytes) throws IOException {
        if (in == null) {
            return null;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.min(4096, Math.max(64, maxBytes)));
        int b;
        int count = 0;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b == '\r') {
                continue;
            }
            count++;
            if (count > maxBytes) {
                throw new FrameTooLargeException(count);
            }
            baos.write(b);
        }
        if (b == -1 && count == 0) {
            return null;
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    /**
     * Procesa una trama del protocolo KDS (HELLO/AUTH_TERMINAL, RENEW, ACK autenticado, EVENT con HMAC-SHA256
     * y límite de 1 MB). Rechaza cualquier trama desconocida o sin sesión válida.
     */
    public JsonObject procesarTramaProtocolo(String linea, PrintWriter remitente) {
        if (linea == null || linea.isBlank()) {
            return crearError("EMPTY_FRAME", "Trama vacía");
        }
        int byteLen = linea.getBytes(StandardCharsets.UTF_8).length;
        if (byteLen > MAX_FRAME_BYTES) {
            LOGGER.warning("Trama rechazada por exceder 1 MB (" + byteLen + " bytes).");
            return crearError("FRAME_TOO_LARGE", "El mensaje supera el límite máximo de 1 MB");
        }

        try {
            JsonElement parsed = JsonParser.parseString(linea);
            if (!parsed.isJsonObject()) {
                return crearError("INVALID_JSON", "Mensaje JSON malformado");
            }
            JsonObject json = parsed.getAsJsonObject();
            String type = json.has("type") && !json.get("type").isJsonNull() ? json.get("type").getAsString() : "";

            if ("HELLO".equalsIgnoreCase(type) || "AUTH_TERMINAL".equalsIgnoreCase(type)) {
                String codigoTerminal = json.has("codigoTerminal") && !json.get("codigoTerminal").isJsonNull()
                        ? json.get("codigoTerminal").getAsString() : "";
                String secreto = json.has("secreto") && !json.get("secreto").isJsonNull()
                        ? json.get("secreto").getAsString() : "";
                try {
                    TerminalAuthService.SesionTerminal sesion = terminalAuthService.autenticar(codigoTerminal, secreto);
                    if (remitente != null) {
                        clientesConectados.add(remitente);
                    }
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
                String token = json.has("sessionToken") && !json.get("sessionToken").isJsonNull()
                        ? json.get("sessionToken").getAsString() : "";
                try {
                    TerminalAuthService.SesionTerminal renovada = terminalAuthService.renovar(token);
                    if (remitente != null) {
                        clientesConectados.add(remitente);
                    }
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
                String sessionToken = json.has("sessionToken") && !json.get("sessionToken").isJsonNull()
                        ? json.get("sessionToken").getAsString() : "";
                TerminalAuthService.SesionTerminal sesion = terminalAuthService.obtenerSesion(sessionToken);
                if (sesion == null) {
                    return crearError("TerminalNoAutorizada", "Sesión no autenticada o expirada para ACK");
                }
                long idEvento = json.has("idEvento") && !json.get("idEvento").isJsonNull()
                        ? json.get("idEvento").getAsLong() : 0L;
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
                String sessionToken = json.has("sessionToken") && !json.get("sessionToken").isJsonNull()
                        ? json.get("sessionToken").getAsString() : "";
                TerminalAuthService.SesionTerminal sesion = terminalAuthService.obtenerSesion(sessionToken);
                if (sesion == null) {
                    return crearError("TerminalNoAutorizada", "Sesión no autenticada o expirada");
                }
                if (!json.has("payload") || !json.get("payload").isJsonObject()) {
                    return crearError("INVALID_PAYLOAD", "Payload de evento ausente o inválido");
                }
                EventoKdsDTO payload = EventoKdsDTO.fromJson(json.get("payload").toString());
                if (payload == null || !payload.verificarHmac(sesion.getSessionSecret())) {
                    return crearError("HMAC_INVALIDO", "Firma HMAC-SHA256 adulterada o expirada");
                }
                Consumer<JsonObject> listener = this.mensajeListener;
                if (listener != null) {
                    listener.accept(json);
                }
                transmitirATodos(linea, remitente);
                JsonObject ack = new JsonObject();
                ack.addProperty("type", "ACK");
                ack.addProperty("eventId", payload.getEventId());
                return ack;
            }

            return crearError("FRAME_NO_AUTORIZADO", "Tipo de trama desconocido o no autenticado");
        } catch (SQLException | RuntimeException parseEx) {
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

    /**
     * Despacha un evento JSON originado dentro del mismo proceso JVM (ej. {@code PedidoService} / {@code ApiServer})
     * hacia el listener local del KDS JavaFX y hacia todas las terminales TCP autenticadas conectadas.
     */
    public void emitirMensajeInterno(String jsonMensaje) {
        if (jsonMensaje == null || jsonMensaje.isBlank()) {
            return;
        }
        Consumer<JsonObject> listener = this.mensajeListener;
        if (listener != null) {
            try {
                JsonElement el = JsonParser.parseString(jsonMensaje);
                if (el.isJsonObject()) {
                    listener.accept(el.getAsJsonObject());
                }
            } catch (RuntimeException ex) {
                LOGGER.log(Level.FINE, "Mensaje interno KDS no pudo parsearse como JsonObject", ex);
            }
        }
        synchronized (clientesConectados) {
            for (PrintWriter writer : clientesConectados) {
                writer.println(jsonMensaje);
            }
        }
    }

    public void emitirMensaje(JsonObject mensaje) {
        if (mensaje == null) {
            return;
        }
        emitirMensajeInterno(GSON.toJson(mensaje));
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

    private static void cerrarSocketSilencioso(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException e) {
            LOGGER.log(Level.FINEST, "Cierre de socket cliente", e);
        }
    }

    public synchronized void detener() {
        corriendo = false;
        try {
            ServerSocket ss = serverSocket.getAndSet(null);
            if (ss != null && !ss.isClosed()) {
                ss.close();
            }
            synchronized (clientesConectados) {
                clientesConectados.clear();
            }
            threadPool.shutdownNow();
            try {
                if (!threadPool.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    LOGGER.fine("Hilos de ServidorSockets aún finalizando tras cierre.");
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            LOGGER.info("Servidor de Sockets detenido.");
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error al detener ServidorSockets", e);
        }
    }
}
