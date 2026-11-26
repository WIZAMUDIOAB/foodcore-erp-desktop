package com.foodcore.controller.config;

import com.foodcore.model.Usuario;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionManager;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.AuthService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Implementación por defecto del {@link ConfigBackupCoordinator} que centraliza la
 * lógica de respaldo y restauración del sistema, ejecutando los scripts
 * backup-auto/restore-auto y sin componentes de interfaz gráfica.
 */
public class DefaultConfigBackupCoordinator implements ConfigBackupCoordinator {

    private static final Logger LOGGER =
            Logger.getLogger(DefaultConfigBackupCoordinator.class.getName());

    private static final int SCRIPT_TIMEOUT_SECONDS = 120;

    private static final String EXTENSION_GZIP = ".sql.gz";

    private static final String EXTENSION_SQL = ".sql";

    /**
     * Constructor público sin argumentos.
     */
    public DefaultConfigBackupCoordinator() {
        // Sin estado inicial; las dependencias de seguridad son servicios globales.
    }

    @Override
    public boolean tienePermisoBackupRestauracion() {
        SesionUsuario sesion = SesionManager.getSesionActiva();
        Usuario actual = AuthService.getUsuarioSesionActual();
        if (actual != null) {
            if (sesion != null && sesion.getIdUsuario() == actual.getIdUsuario()
                    && actual.getNombreRol() != null
                    && actual.getNombreRol().equalsIgnoreCase(sesion.getCodigoRol())) {
                return sesion.tienePermiso(Permiso.CONFIG_RED);
            }
            if (actual.getNombreRol() == null || actual.getNombreRol().isBlank()) {
                return false;
            }
            return AutorizacionService.permisosPorDefectoParaRol(actual.getNombreRol())
                    .contains(Permiso.CONFIG_RED);
        }
        return sesion != null && sesion.tienePermiso(Permiso.CONFIG_RED);
    }

    @Override
    public Optional<Path> buscarUltimoBackupDisponible(Path backupsDir) {
        if (backupsDir == null || !Files.isDirectory(backupsDir)) {
            return Optional.empty();
        }
        try (Stream<Path> walk = Files.list(backupsDir)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(EXTENSION_GZIP) || n.endsWith(EXTENSION_SQL);
                    })
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()));
        } catch (IOException falloListado) {
            LOGGER.log(Level.FINE, "No se pudo listar el directorio de backups", falloListado);
            return Optional.empty();
        }
    }

    @Override
    public ValidacionBackup validarArchivoBackup(String rutaTexto) {
        String ruta = rutaTexto == null ? "" : rutaTexto.trim();
        if (ruta.isBlank()) {
            return new ValidacionBackup(
                    false,
                    null,
                    "Seleccione o indique la ruta de un backup válido",
                    "badge-pill-danger",
                    "Seleccione o indique la ruta de un backup válido."
            );
        }
        Path archivoBackup = Path.of(ruta);
        if (!Files.isRegularFile(archivoBackup)) {
            return new ValidacionBackup(
                    false,
                    archivoBackup,
                    "Archivo no encontrado",
                    "badge-pill-danger",
                    "El archivo de backup especificado no existe."
            );
        }
        Path fnBackup = archivoBackup.getFileName();
        String nombreLower = fnBackup != null
                ? fnBackup.toString().toLowerCase(Locale.ROOT) : "";
        if (!nombreLower.endsWith(EXTENSION_GZIP) && !nombreLower.endsWith(EXTENSION_SQL)) {
            return new ValidacionBackup(
                    false,
                    archivoBackup,
                    "Formato de backup no permitido",
                    "badge-pill-danger",
                    "Solo se permiten archivos .sql.gz o .sql."
            );
        }
        return new ValidacionBackup(
                true,
                archivoBackup,
                "Backup listo: " + fnBackup,
                "badge-pill-success",
                "Archivo de backup válido: " + fnBackup
        );
    }

    @Override
    public boolean ejecutarScriptRestauracionSistema(Path backupFile) {
        if (!tienePermisoBackupRestauracion() || backupFile == null
                || !Files.isRegularFile(backupFile)) {
            return false;
        }
        Path fnBackup = backupFile.getFileName();
        String fileName = fnBackup != null
                ? fnBackup.toString().toLowerCase(Locale.ROOT) : "";
        boolean esGzip = fileName.endsWith(EXTENSION_GZIP);
        boolean esSql = fileName.endsWith(EXTENSION_SQL);
        if (!esGzip && !esSql) {
            return false;
        }
        Path stagingFile = esGzip
                ? Path.of("backups", ".restore_staging.sql.gz")
                : Path.of("backups", ".restore_staging.sql");
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder pb;
        if (osName.contains("win")) {
            Path script = Path.of("scripts", "restore-auto.ps1").toAbsolutePath().normalize();
            if (!Files.isRegularFile(script)) {
                return false;
            }
            if (esGzip) {
                pb = new ProcessBuilder(
                        "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                        "-File", "scripts\\restore-auto.ps1",
                        "-BackupFile", "backups\\.restore_staging.sql.gz",
                        "-SkipServiceRestart"
                );
            } else {
                pb = new ProcessBuilder(
                        "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                        "-File", "scripts\\restore-auto.ps1",
                        "-BackupFile", "backups\\.restore_staging.sql",
                        "-SkipServiceRestart"
                );
            }
        } else {
            Path script = Path.of("scripts", "restore-auto.sh").toAbsolutePath().normalize();
            if (!Files.isRegularFile(script)) {
                return false;
            }
            pb = esGzip
                    ? new ProcessBuilder("bash", "scripts/restore-auto.sh",
                            "backups/.restore_staging.sql.gz")
                    : new ProcessBuilder("bash", "scripts/restore-auto.sh",
                            "backups/.restore_staging.sql");
        }
        pb.redirectErrorStream(true);
        Process proc = null;
        try {
            Files.createDirectories(Path.of("backups"));
            Path backupReal = backupFile.toRealPath();
            Files.copy(backupReal, stagingFile, StandardCopyOption.REPLACE_EXISTING);
            proc = pb.start();
            try (var reader = proc.getInputStream()) {
                reader.readAllBytes();
            }
            boolean finished = proc.waitFor(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return finished && proc.exitValue() == 0;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.log(Level.WARNING, "Error ejecutando restore-auto: " + ex.getMessage(), ex);
            return false;
        } finally {
            if (proc != null && proc.isAlive()) {
                proc.destroyForcibly();
            }
            try {
                Files.deleteIfExists(stagingFile);
            } catch (IOException exDel) {
                LOGGER.log(Level.FINE,
                        "No se pudo eliminar archivo temporal de staging de restauración", exDel);
            }
        }
    }

    @Override
    public Path ejecutarScriptBackupSistema() {
        if (!tienePermisoBackupRestauracion()) {
            return null;
        }
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder pb;
        if (osName.contains("win")) {
            Path script = Path.of("scripts", "backup-auto.ps1").toAbsolutePath().normalize();
            if (!Files.isRegularFile(script)) {
                return null;
            }
            pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", "scripts\\backup-auto.ps1"
            );
        } else {
            Path script = Path.of("scripts", "backup-auto.sh").toAbsolutePath().normalize();
            if (!Files.isRegularFile(script)) {
                return null;
            }
            pb = new ProcessBuilder("bash", "scripts/backup-auto.sh");
        }
        pb.redirectErrorStream(true);
        Process proc = null;
        try {
            proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = proc.waitFor(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished || proc.exitValue() != 0) {
                return null;
            }
            Path backupsDir = Path.of("backups").toAbsolutePath().normalize();
            if (!Files.isDirectory(backupsDir)) {
                return null;
            }
            String[] lines = output.split("\\R");
            String nombreReportado = null;
            for (int i = lines.length - 1; i >= 0; i--) {
                String candidate = lines[i].trim();
                if (!candidate.isEmpty()) {
                    int sep = Math.max(candidate.lastIndexOf('/'), candidate.lastIndexOf('\\'));
                    nombreReportado = sep >= 0 ? candidate.substring(sep + 1) : candidate;
                    break;
                }
            }
            try (Stream<Path> files = Files.list(backupsDir)) {
                List<Path> backups = files
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            Path fn = p.getFileName();
                            if (fn == null) {
                                return false;
                            }
                            String name = fn.toString();
                            return name.endsWith(EXTENSION_GZIP) || name.endsWith(EXTENSION_SQL);
                        })
                        .toList();
                if (nombreReportado != null) {
                    for (Path p : backups) {
                        Path fn = p.getFileName();
                        if (fn != null && fn.toString().equals(nombreReportado)) {
                            return p;
                        }
                    }
                }
                return backups.stream()
                        .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                        .orElse(null);
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.log(Level.WARNING, "Error ejecutando backup-auto: " + ex.getMessage(), ex);
            return null;
        } finally {
            if (proc != null && proc.isAlive()) {
                proc.destroyForcibly();
            }
        }
    }
}
