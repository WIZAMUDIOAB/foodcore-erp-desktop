package com.foodcore.service;

import com.foodcore.api.ApiServer;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ServidorSockets;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio de recolección de telemetría en tiempo real para el Dashboard de Monitoreo
 * ({@code EstadoSistemaView.fxml}) según Sub-Fase C Ítems 4, 5 y 6 (v5.3.0).
 */
public class SistemaMonitorService {

    private static final Logger LOGGER = Logger.getLogger(SistemaMonitorService.class.getName());
    private static final int PORT_PROBE_TIMEOUT_MS = 250;

    /**
     * Instantánea inmutable de salud de servicios y recursos del sistema.
     */
    public record EstadoSistemaSnapshot(
            boolean mysqlUp,
            String mysqlVersion,
            long mysqlLatenciaMs,
            boolean javalinHttpUp,
            int javalinHttpPort,
            boolean javalinHttpsUp,
            int javalinHttpsPort,
            boolean socketKdsUp,
            int socketKdsPort,
            int socketKdsClientes,
            boolean tunnelActivo,
            String tunnelUrl,
            String flywayVersion,
            long uptimeMillis,
            double cpuPorcentaje,
            long ramUsadaBytes,
            long ramTotalBytes,
            double ramPorcentaje,
            long discoLibreBytes,
            long discoTotalBytes,
            double discoUsadoPorcentaje) {

        public String uptimeFormateado() {
            return formatearDuracion(uptimeMillis);
        }
    }

    /**
     * Recopila el estado actual de MySQL, Javalin HTTP/HTTPS, Socket KDS, Cloudflare Tunnel,
     * Flyway, Uptime y métricas de CPU/RAM/Disco, y evalúa alertas SMTP.
     */
    public EstadoSistemaSnapshot capturarSnapshot() {
        boolean mysqlUp = false;
        String mysqlVersion = "N/D";
        long mysqlLatenciaMs = -1L;
        String flywayVersion = "N/D";

        long t0 = System.nanoTime();
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null && conn.isValid(2)) {
                mysqlLatenciaMs = Math.max(1L, (System.nanoTime() - t0) / 1_000_000L);
                mysqlUp = true;
                mysqlVersion = consultarMysqlVersion(conn);
                flywayVersion = consultarFlywayVersion(conn);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "MySQL no disponible durante sondeo de monitoreo: " + ex.getMessage(), ex);
        }

        ApiServer api = ApiServer.getInstance();
        int httpPort = api.isRunning() ? api.getPuertoActual() : ApiServer.DEFAULT_PORT;
        int httpsPort = api.isRunning() ? api.getPuertoHttpsActual() : ApiServer.DEFAULT_HTTPS_PORT;
        boolean httpUp = (api.isRunning() && puertoLocalAbierto(httpPort)) || puertoLocalAbierto(httpPort);
        boolean httpsUp = httpsPort > 0 && puertoLocalAbierto(httpsPort);

        ServidorSockets kds = ServidorSockets.getInstance();
        boolean kdsUp = kds.isCorriendo() || puertoLocalAbierto(ServidorSockets.PUERTO_TCP);
        int kdsPort = kds.isCorriendo() ? kds.getPuertoActual() : ServidorSockets.PUERTO_TCP;
        int kdsClientes = kds.getCantidadClientesConectados();

        boolean tunnelActivo = TunnelService.tieneUrlActiva();
        String tunnelUrl = tunnelActivo ? TunnelService.obtenerUrlPublicaActiva() : "Sin túnel activo";

        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();

        double cpuPct = leerCpuPorcentaje();
        long[] ram = leerRamBytes();
        long ramUsada = ram[0];
        long ramTotal = Math.max(1L, ram[1]);
        double ramPct = Math.min(100.0, Math.max(0.0, (ramUsada * 100.0) / ramTotal));

        File rootDir = Path.of(".").toAbsolutePath().toFile();
        long discoTotal = Math.max(1L, rootDir.getTotalSpace());
        long discoLibre = Math.max(0L, rootDir.getUsableSpace());
        long discoUsado = Math.max(0L, discoTotal - discoLibre);
        double discoUsadoPct = Math.min(100.0, Math.max(0.0, (discoUsado * 100.0) / discoTotal));

        EstadoSistemaSnapshot snapshot = new EstadoSistemaSnapshot(
                mysqlUp, mysqlVersion, mysqlLatenciaMs,
                httpUp, httpPort,
                httpsUp, httpsPort,
                kdsUp, kdsPort, kdsClientes,
                tunnelActivo, tunnelUrl,
                flywayVersion, uptimeMs,
                cpuPct,
                ramUsada, ramTotal, ramPct,
                discoLibre, discoTotal, discoUsadoPct);

        AlertaService.evaluarGlobalSnapshot(snapshot);
        return snapshot;
    }

    private String consultarMysqlVersion(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT VERSION()");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (Exception ignored) {
            return "N/D";
        }
        return "N/D";
    }

    private String consultarFlywayVersion(Connection conn) {
        String sql = "SELECT version, description FROM flyway_schema_history "
                + "WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String ver = rs.getString("version");
                String desc = rs.getString("description");
                return "V" + ver + (desc != null && !desc.isBlank() ? " (" + desc + ")" : "");
            }
        } catch (Exception ignored) {
            return "N/D";
        }
        return "N/D";
    }

    static boolean puertoLocalAbierto(int puerto) {
        if (puerto <= 0) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", puerto), PORT_PROBE_TIMEOUT_MS);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    static double leerCpuPorcentaje() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            double cpuLoad = sunOs.getCpuLoad();
            if (cpuLoad < 0.0) {
                cpuLoad = sunOs.getProcessCpuLoad();
            }
            if (cpuLoad >= 0.0) {
                return Math.min(100.0, Math.max(0.0, cpuLoad * 100.0));
            }
        }
        double loadAvg = os.getSystemLoadAverage();
        if (loadAvg >= 0.0 && os.getAvailableProcessors() > 0) {
            return Math.min(100.0, Math.max(0.0, (loadAvg / os.getAvailableProcessors()) * 100.0));
        }
        return 0.0;
    }

    static long[] leerRamBytes() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            long total = sunOs.getTotalMemorySize();
            long libre = sunOs.getFreeMemorySize();
            if (total > 0L) {
                long usada = Math.max(0L, total - libre);
                return new long[] { usada, total };
            }
        }
        Runtime rt = Runtime.getRuntime();
        long totalJvm = rt.totalMemory();
        long usadaJvm = Math.max(0L, totalJvm - rt.freeMemory());
        return new long[] { usadaJvm, Math.max(1L, totalJvm) };
    }

    public static String formatearDuracion(long millis) {
        Duration d = Duration.ofMillis(Math.max(0L, millis));
        long dias = d.toDays();
        long horas = d.toHoursPart();
        long mins = d.toMinutesPart();
        long segs = d.toSecondsPart();
        if (dias > 0) {
            return String.format("%dd %02dh %02dm %02ds", dias, horas, mins, segs);
        }
        return String.format("%02dh %02dm %02ds", horas, mins, segs);
    }

    public static String formatearBytesGb(long bytes) {
        double gb = Math.max(0L, bytes) / (1024.0 * 1024.0 * 1024.0);
        return String.format(java.util.Locale.US, "%.2f GB", gb);
    }
}
