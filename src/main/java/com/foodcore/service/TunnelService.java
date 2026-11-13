package com.foodcore.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio para gestionar el túnel público de Cloudflare ({@code cloudflared.exe})
 * en segundo plano y exponer su URL dinámica al estado global de FoodCore ERP (v4.6.0 Fase 2).
 */
public final class TunnelService {

    private static final Logger LOGGER = Logger.getLogger(TunnelService.class.getName());
    private static final Pattern TRY_CLOUDFLARE_PATTERN =
            Pattern.compile("https://[a-zA-Z0-9-]+\\.trycloudflare\\.com");

    private static final Path DEFAULT_SCRIPT_PATH = Path.of("scripts", "start-tunnel.ps1");
    private static final Path DEFAULT_EXE_PATH = Path.of("scripts", "cloudflared.exe");
    private static final Path DEFAULT_LOG_PATH = Path.of("scripts", "tunnel.log");
    private static final Path FALLBACK_LOG_PATH = Path.of("tunnel.log");

    private static final long POLL_INTERVAL_MS = 250L;
    private static final AtomicReference<String> URL_PUBLICA_ACTIVA = new AtomicReference<>("");
    private static final Object LOCK = new Object();

    private static Process procesoTunel;
    private static Thread hiloMonitor;
    private static volatile boolean ejecucionActiva;
    private static volatile boolean shutdownHookRegistrado;

    private TunnelService() {
    }

    /**
     * Inicia el script de túnel Cloudflare en segundo plano en Windows cuando el ejecutable
     * y el script están disponibles. En otros entornos (por ejemplo CI Linux) omite el arranque sin fallar.
     *
     * @return {@code true} si se lanzó el proceso de túnel; {@code false} si se omitió o ya estaba activo.
     */
    public static boolean iniciarEnSegundoPlano() {
        return iniciarEnSegundoPlano(DEFAULT_SCRIPT_PATH, DEFAULT_EXE_PATH, DEFAULT_LOG_PATH);
    }

    /**
     * Inicia el script de túnel con rutas explícitas y comienza a leer el log en tiempo real.
     *
     * @param scriptPath ruta al script PowerShell de arranque.
     * @param exePath ruta al binario {@code cloudflared.exe}.
     * @param logPath ruta al archivo {@code tunnel.log} generado por el script.
     * @return {@code true} si el proceso se inició correctamente; {@code false} en caso contrario.
     */
    public static boolean iniciarEnSegundoPlano(Path scriptPath, Path exePath, Path logPath) {
        if (!esWindows()) {
            LOGGER.info("Túnel Cloudflare omitido: sistema operativo distinto de Windows.");
            return false;
        }
        if (scriptPath == null || exePath == null || logPath == null
                || !Files.isRegularFile(scriptPath) || !Files.isRegularFile(exePath)) {
            LOGGER.info("Túnel Cloudflare omitido: no se encontró scripts/start-tunnel.ps1 o scripts/cloudflared.exe.");
            return false;
        }

        synchronized (LOCK) {
            if (procesoTunel != null && procesoTunel.isAlive()) {
                return false;
            }
            URL_PUBLICA_ACTIVA.set("");
            limpiarLogPrevio(logPath);

            Path absScript = scriptPath.toAbsolutePath().normalize();
            Path absWorkDir = absScript.getParent();

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-File", absScript.toString()
            );
            if (absWorkDir != null) {
                pb.directory(absWorkDir.toFile());
            }
            pb.redirectErrorStream(true);

            try {
                procesoTunel = pb.start();
                ejecucionActiva = true;
                registrarShutdownHookUnaVez();
                iniciarHiloMonitorLog(logPath);
                LOGGER.info("Proceso de túnel Cloudflare iniciado en segundo plano.");
                return true;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "No se pudo iniciar el túnel Cloudflare: " + e.getMessage(), e);
                ejecucionActiva = false;
                procesoTunel = null;
                return false;
            }
        }
    }

    /**
     * Inicia únicamente la vigilancia en tiempo real sobre un archivo {@code tunnel.log} (útil en pruebas).
     *
     * @param logPath archivo de log a monitorear.
     */
    static void iniciarMonitorSoloLectura(Path logPath) {
        synchronized (LOCK) {
            detenerHiloMonitorLocked();
            ejecucionActiva = true;
            iniciarHiloMonitorLog(logPath);
        }
    }

    /**
     * Detiene el proceso del túnel Cloudflare (y sus procesos hijos) y limpia el estado global.
     */
    public static void detener() {
        synchronized (LOCK) {
            ejecucionActiva = false;
            detenerHiloMonitorLocked();
            if (procesoTunel != null) {
                terminarArbolProceso(procesoTunel);
                procesoTunel = null;
            }
            URL_PUBLICA_ACTIVA.set("");
        }
    }

    /**
     * Devuelve la URL pública activa del túnel (ej. {@code https://demo.trycloudflare.com}),
     * o cadena vacía si no existe túnel conectado aún.
     */
    public static String obtenerUrlPublicaActiva() {
        String url = URL_PUBLICA_ACTIVA.get();
        return url != null ? url : "";
    }

    /**
     * Indica si existe una URL pública activa de Cloudflare Tunnel.
     */
    public static boolean tieneUrlActiva() {
        return !obtenerUrlPublicaActiva().isBlank();
    }

    /**
     * Combina la URL pública activa del túnel con una ruta de endpoint (ej. {@code /api/public/menu-digital}).
     *
     * @param rutaEndpoint ruta relativa del endpoint HTTP.
     * @return URL completa pública, o cadena vacía si el túnel no está activo.
     */
    public static String construirUrlPublicaEndpoint(String rutaEndpoint) {
        String base = obtenerUrlPublicaActiva();
        if (base.isBlank()) {
            return "";
        }
        if (rutaEndpoint == null || rutaEndpoint.isBlank()) {
            return base;
        }
        String rutaNormalizada = rutaEndpoint.startsWith("/") ? rutaEndpoint : "/" + rutaEndpoint;
        return base + rutaNormalizada;
    }

    /**
     * Extrae la primera URL {@code https://<subdominio>.trycloudflare.com} desde una línea o texto del log.
     *
     * @param texto línea o contenido de {@code tunnel.log}.
     * @return URL detectada o {@code null} si no aparece.
     */
    public static String extraerUrlTryCloudflare(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        Matcher matcher = TRY_CLOUDFLARE_PATTERN.matcher(texto);
        if (matcher.find()) {
            return normalizarUrlBase(matcher.group());
        }
        return null;
    }

    /**
     * Inspecciona el archivo {@code tunnel.log} indicado y actualiza el estado global si detecta una URL pública.
     * Soporta codificación UTF-8 y UTF-16LE (redirección estándar de Windows PowerShell).
     *
     * @param logPath archivo de log a leer.
     * @return {@code true} si se encontró y registró una URL válida.
     */
    public static boolean leerUrlDesdeLog(Path logPath) {
        if (logPath == null || !Files.isRegularFile(logPath)) {
            return false;
        }
        try {
            byte[] bytes = Files.readAllBytes(logPath);
            if (bytes.length == 0) {
                return false;
            }
            String contenido = decodificarContenidoLog(bytes);
            String[] lineas = contenido.split("\\R");
            for (String linea : lineas) {
                String detectada = extraerUrlTryCloudflare(linea);
                if (detectada != null) {
                    String previa = URL_PUBLICA_ACTIVA.getAndSet(detectada);
                    if (!detectada.equals(previa)) {
                        LOGGER.info("URL pública de Cloudflare Tunnel detectada: " + detectada);
                    }
                    return true;
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Esperando lectura de tunnel.log: " + e.getMessage());
        }
        return false;
    }

    /**
     * Establece manualmente una URL pública en el estado global (principalmente para pruebas unitarias/E2E).
     *
     * @param url URL base del túnel; {@code null} o blanco limpia el estado.
     */
    public static void establecerUrlPublicaManual(String url) {
        URL_PUBLICA_ACTIVA.set(normalizarUrlBase(url));
    }

    /**
     * Limpia el estado y detiene cualquier monitor activo al finalizar pruebas.
     */
    public static void limpiarEstadoParaPruebas() {
        detener();
    }

    private static void iniciarHiloMonitorLog(Path logPath) {
        Thread monitor = new Thread(() -> {
            while (ejecucionActiva && !Thread.currentThread().isInterrupted()) {
                boolean encontrada = leerUrlDesdeLog(logPath);
                if (!encontrada && !logPath.equals(FALLBACK_LOG_PATH) && Files.isRegularFile(FALLBACK_LOG_PATH)) {
                    encontrada = leerUrlDesdeLog(FALLBACK_LOG_PATH);
                }
                if (encontrada) {
                    return;
                }
                Process procActual;
                synchronized (LOCK) {
                    procActual = procesoTunel;
                }
                if (procActual != null && !procActual.isAlive() && !Files.isRegularFile(logPath)) {
                    return;
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "foodcore-tunnel-log-watcher");
        monitor.setDaemon(true);
        hiloMonitor = monitor;
        monitor.start();
    }

    private static void detenerHiloMonitorLocked() {
        if (hiloMonitor != null) {
            hiloMonitor.interrupt();
            hiloMonitor = null;
        }
    }

    private static void terminarArbolProceso(Process process) {
        try {
            process.toHandle().descendants().forEach(desc -> {
                try {
                    desc.destroyForcibly();
                } catch (Exception ignored) {
                    // Mejor esfuerzo al cerrar procesos descendientes
                }
            });
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error al detener proceso de túnel Cloudflare: " + e.getMessage());
        }
    }

    private static void limpiarLogPrevio(Path logPath) {
        try {
            if (Files.exists(logPath)) {
                Files.writeString(logPath, "", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "No se pudo limpiar tunnel.log previo: " + e.getMessage());
        }
    }

    private static void registrarShutdownHookUnaVez() {
        if (!shutdownHookRegistrado) {
            shutdownHookRegistrado = true;
            Runtime.getRuntime().addShutdownHook(new Thread(TunnelService::detener, "foodcore-tunnel-shutdown"));
        }
    }

    private static boolean esWindows() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return osName.contains("win");
    }

    private static String decodificarContenidoLog(byte[] bytes) {
        if (bytes.length >= 2) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            if (b0 == 0xFF && b1 == 0xFE) {
                return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String normalizarUrlBase(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
