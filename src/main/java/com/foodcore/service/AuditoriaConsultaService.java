package com.foodcore.service;

import com.foodcore.dao.AuditoriaDAO;
import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.model.RegistroAuditoria;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio de consulta de la bitácora de auditoría (SPEC-FOODCORE-v1.3-ENTERPRISE §5.2).
 * Centraliza la apertura de conexiones y las consultas de paginación, filtros y
 * exportación para que los controladores no dependan directamente de JDBC.
 */
public class AuditoriaConsultaService {

    private static final Logger LOGGER = Logger.getLogger(AuditoriaConsultaService.class.getName());
    private static final int LIMITE_EXPORTACION = 10_000;
    private static final int TAMANO_PAGINA_POR_DEFECTO = 50;

    private final AuditoriaDAO auditoriaDAO;

    public AuditoriaConsultaService() {
        this(new AuditoriaDAOImpl());
    }

    public AuditoriaConsultaService(AuditoriaDAO auditoriaDAO) {
        this.auditoriaDAO = auditoriaDAO;
    }

    /**
     * Resultado de una consulta paginada de auditoría.
     */
    public record PaginaAuditoriaData(int total, List<RegistroAuditoria> items,
            List<String> usuarios, List<String> acciones) {
        public PaginaAuditoriaData {
            items = items != null ? List.copyOf(items) : List.of();
            usuarios = usuarios != null ? List.copyOf(usuarios) : List.of();
            acciones = acciones != null ? List.copyOf(acciones) : List.of();
        }
    }

    /**
     * Consulta una página de eventos de auditoría junto con los valores distintos
     * de usuario y acción para los filtros de la vista.
     *
     * @param usuario filtro por usuario; {@code null} o vacío para todos.
     * @param accion filtro por acción; {@code null} o vacío para todas.
     * @param desde fecha inicial inclusive; puede ser {@code null}.
     * @param hasta fecha final inclusive; puede ser {@code null}.
     * @param limite tamaño de página solicitado.
     * @param offset desplazamiento solicitado.
     * @return página de auditoría con total de registros y opciones de filtro.
     * @throws SQLException si falla alguna consulta en base de datos.
     */
    public PaginaAuditoriaData consultarPaginaAuditoria(String usuario, String accion,
            LocalDate desde, LocalDate hasta, int limite, int offset) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                return new PaginaAuditoriaData(0, List.of(), List.of(), List.of());
            }
            int tamanoPagina = limite > 0 ? limite : TAMANO_PAGINA_POR_DEFECTO;
            int total = auditoriaDAO.contarEventosFiltrados(conn, usuario, accion, desde, hasta);
            int maxPagina = Math.max(1, (int) Math.ceil((double) total / tamanoPagina));
            int offsetEfectivo = offset >= total
                    ? Math.max(0, (maxPagina - 1) * tamanoPagina)
                    : Math.max(0, offset);
            List<RegistroAuditoria> items = auditoriaDAO.listarEventosFiltrados(
                    conn, usuario, accion, desde, hasta, offsetEfectivo, tamanoPagina);
            List<String> usuarios = auditoriaDAO.listarUsuariosDistinct(conn);
            List<String> acciones = auditoriaDAO.listarAccionesDistinct(conn);
            return new PaginaAuditoriaData(total, items, usuarios, acciones);
        }
    }

    /**
     * Lista los eventos de auditoría que coinciden con los filtros para exportación.
     *
     * @param usuario filtro por usuario; {@code null} o vacío para todos.
     * @param accion filtro por acción; {@code null} o vacío para todas.
     * @param desde fecha inicial inclusive; puede ser {@code null}.
     * @param hasta fecha final inclusive; puede ser {@code null}.
     * @return eventos filtrados, hasta el límite máximo de exportación.
     * @throws SQLException si falla la consulta en base de datos.
     */
    public List<RegistroAuditoria> listarParaExportacion(String usuario, String accion,
            LocalDate desde, LocalDate hasta) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                return List.of();
            }
            return auditoriaDAO.listarEventosFiltrados(
                    conn, usuario, accion, desde, hasta, 0, LIMITE_EXPORTACION);
        }
    }

    /**
     * Registra un evento de auditoría de forma best-effort gestionando la conexión desde la capa de servicio.
     */
    public void registrarEventoBestEffort(int idUsuario, String accion, String entidadAfectada,
                                          String datosAnteriores, String datosNuevos) {
        try (Connection conn = ConexionBD.getConnection()) {
            auditoriaDAO.registrarEventoSeguro(conn, idUsuario, accion, entidadAfectada, datosAnteriores, datosNuevos);
        } catch (SQLException | RuntimeException ex) {
            LOGGER.log(Level.FINE, "Sin conexión para registrar evento de auditoría (" + accion + ")", ex);
        }
    }
}
