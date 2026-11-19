package com.foodcore.service;

import com.foodcore.api.ApiServer;

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
 * en segundo plano apuntando exclusivamente al conector loopback del menú QR público
 * y exponer su URL dinámica al estado global de FoodCore ERP (v5.7.1 Hardening).
 */
public final class TunnelService {

    private static final Logger LOGGER = Logger.getLogger(TunnelService.class.getName());
    private static final Pattern TRY_CLOUDFLARE_PATTERN =
            Pattern.compile("https://[a-zA-Z0-9-]+\\.trycloudflare\\.com");
    private static final Pattern SCRIPT_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+\\.ps1$");

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
     * y el script están disponibles, apuntando al conector loopback del menú QR público.
     */
    public static boolean iniciarEnSegundoPlano() {
        return iniciarEnSegundoPlano(DEFAULT_SCRIPT_PATH, DEFAULT_EXE_PATH, DEFAULT_LOG_PATH);
    }

    /**
     * Inicia el script de túnel con rutas explícitas y comienza a leer el log en tiempo real.
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
        Path scriptSeguro = validarScriptTunel(scriptPath);
        if (scriptSeguro == null) {
            LOGGER.warning("Túnel Cloudflare rechazado: ruta de script inválida o no permitida.");
            return false;
        }

        synchronized (LOCK) {
            if (procesoTunel != null && procesoTunel.isAlive()) {
                return false;
            }
            detenerHiloMonitorLocked();
            URL_PUBLICA_ACTIVA.set("");
            limpiarLogPrevio(logPath);

            Path absWorkDir = scriptSeguro.getParent();
            int puertoPublico = resolverPuertoPublicoMenu();

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-File", ".\\start-tunnel.ps1"
            );
            if (absWorkDir != null) {
                pb.directory(absWorkDir.toFile());
            }
            pb.environment().put(ApiServer.ENV_PUBLIC_MENU_PORT, String.valueOf(puertoPublico));
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);

            try {
                procesoTunel = pb.start();
                ejecucionActiva = true;
                registrarShutdownHookUnaVez();
                iniciarHiloMonitorLog(logPath);
                LOGGER.info("Proceso de túnel Cloudflare iniciado en segundo plano hacia 127.0.0.1:" + puertoPublico);
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
     * Resuelve el puerto loopback del menú QR público al que debe apuntar el túnel Cloudflare.
     */
    public static int resolverPuertoPublicoMenu() {
        int activo = ApiServer.getInstance().getPuertoMenuPublicoActual();
        if (activo > 0 && activo <= 65535) {
            return activo;
        }
        int configurado = ApiServer.leerPuertoPublicoMenuConfigurado();
        return (configurado > 0 && configurado <= 65535) ? configurado : ApiServer.DEFAULT_PUBLIC_MENU_PORT;
    }

    private static Path validarScriptTunel(Path scriptPath) {
        try {
            Path real = scriptPath.toRealPath().normalize();
            Path fileName = real.getFileName();
            if (fileName == null
                    || !"start-tunnel.ps1".equals(fileName.toString())
                    || !SCRIPT_FILENAME_PATTERN.matcher(fileName.toString()).matches()) {
                return null;
            }
            String fullStr = real.toString();
            if (fullStr.indexOf('\0') >= 0 || fullStr.indexOf('\n') >= 0 || fullStr.indexOf('\r') >= 0
                    || fullStr.indexOf(';') >= 0 || fullStr.indexOf('&') >= 0 || fullStr.indexOf('|') >= 0) {
                return null;
            }
            return real;
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * Inicia únicamente la vigilancia en tiempo real sobre un archivo {@code tunnel.log} (útil en pruebas).
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

    public static String obtenerUrlPublicaActiva() {
        String url = URL_PUBLICA_ACTIVA.get();
        return url != null ? url : "";
    }

    public static boolean tieneUrlActiva() {
        return !obtenerUrlPublicaActiva().isBlank();
    }

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

    public static void establecerUrlPublicaManual(String url) {
        URL_PUBLICA_ACTIVA.set(normalizarUrlBase(url));
    }

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
                if (procActual != null && !procActual.isAlive()) {
                    // Evita bucle infinito si cloudflared terminó prematuramente: realiza una última lectura y sale.
                    leerUrlDesdeLog(logPath);
                    ejecucionActiva = false;
                    LOGGER.warning("Proceso de túnel Cloudflare finalizó antes de emitir URL pública (exit="
                            + procActual.exitValue() + ").");
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
