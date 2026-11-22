package com.foodcore.controller.config;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Contrato para la coordinación de respaldo y restauración de la base de datos,
 * aislado del controlador de configuración de la interfaz gráfica.
 */
public interface ConfigBackupCoordinator {

    /**
     * Resultado de validar un archivo de backup candidato a restauración.
     *
     * @param valido      true si el archivo cumple las condiciones de respaldo.
     * @param archivo     ruta evaluada (puede ser nula o no existente).
     * @param badgeTexto  texto para el badge de estado.
     * @param badgeClass  clase css del badge de estado.
     * @param mensajeToast mensaje informativo para el usuario.
     */
    record ValidacionBackup(boolean valido, Path archivo, String badgeTexto,
                            String badgeClass, String mensajeToast) {
    }

    /**
     * Evalúa en modo fail-closed si el operador autenticado puede ejecutar
     * respaldos y restauraciones de base de datos.
     *
     * @return true solo si el permiso CONFIG_RED está vigente.
     */
    boolean tienePermisoBackupRestauracion();

    /**
     * Busca el archivo de respaldo más reciente en el directorio indicado.
     *
     * @param backupsDir directorio de respaldos.
     * @return el archivo .sql.gz o .sql más reciente, o {@link Optional#empty()}.
     */
    Optional<Path> buscarUltimoBackupDisponible(Path backupsDir);

    /**
     * Valida la ruta indicada como candidato a restauración.
     *
     * @param rutaTexto ruta textual del archivo de backup.
     * @return validación con textos y clases exactos para la interfaz.
     */
    ValidacionBackup validarArchivoBackup(String rutaTexto);

    /**
     * Ejecuta el script de restauración del sistema sobre el backup indicado.
     *
     * @param backupFile archivo de respaldo a restaurar.
     * @return true si el script finalizó exitosamente.
     */
    boolean ejecutarScriptRestauracionSistema(Path backupFile);

    /**
     * Ejecuta el script de respaldo del sistema.
     *
     * @return el archivo de backup generado, o null si el proceso falló.
     */
    Path ejecutarScriptBackupSistema();
}
