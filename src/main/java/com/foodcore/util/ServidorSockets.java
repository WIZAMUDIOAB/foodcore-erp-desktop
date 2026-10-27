package com.foodcore.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servidor TCP en puerto 9090 para comunicación en tiempo real entre Salón y KDS (Cocina).
 */
public class ServidorSockets {

    private static final Logger LOGGER = Logger.getLogger(ServidorSockets.class.getName());
    public static final int PUERTO_TCP = 9090;

    private static ServidorSockets instance;
    private ServerSocket serverSocket;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private final Set<PrintWriter> clientesConectados = Collections.synchronizedSet(new HashSet<>());
    private Consumer<JsonObject> mensajeListener;
    private volatile boolean corriendo = false;

    private ServidorSockets() {
    }

    public static synchronized ServidorSockets getInstance() {
        if (instance == null) {
            instance = new ServidorSockets();
        }
        return instance;
    }

    public synchronized void iniciar() {
        if (corriendo) {
            return;
        }
        corriendo = true;
        threadPool.execute(() -> {
            try {
                serverSocket = new ServerSocket(PUERTO_TCP);
                LOGGER.info("Servidor de Sockets escuchando en el puerto " + PUERTO_TCP);

                while (corriendo && !serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    LOGGER.info("Cliente conectado desde: " + clientSocket.getRemoteSocketAddress());
                    atenderCliente(clientSocket);
                }
            } catch (Exception e) {
                if (corriendo) {
                    LOGGER.log(Level.SEVERE, "Error en ServidorSockets", e);
                }
            }
        });
    }

    private void atenderCliente(Socket socket) {
        threadPool.execute(() -> {
            PrintWriter out = null;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                out = new PrintWriter(socket.getOutputStream(), true);
                clientesConectados.add(out);

                String linea;
                while ((linea = in.readLine()) != null) {
                    LOGGER.info("Mensaje socket recibido: " + linea);
                    try {
                        JsonObject json = JsonParser.parseString(linea).getAsJsonObject();
                        if (mensajeListener != null) {
                            mensajeListener.accept(json);
                        }
                        // Reenviar a las demás terminales suscritas (broadcast)
                        transmitirATodos(linea, out);
                    } catch (Exception parseEx) {
                        LOGGER.warning("Mensaje no es JSON válido: " + linea);
                    }
                }
            } catch (Exception e) {
                LOGGER.info("Cliente desconectado: " + socket.getRemoteSocketAddress());
            } finally {
                if (out != null) {
                    clientesConectados.remove(out);
                }
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        });
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
