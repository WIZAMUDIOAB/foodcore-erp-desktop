package com.foodcore.dao;

import com.foodcore.model.RegistroAuditoria;
import com.foodcore.model.ResumenSeguridadHoy;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Acceso a datos para la bitácora inmutable de auditoría (§5.2 SPEC-FOODCORE-v1.3-ENTERPRISE).
 */
public interface AuditoriaDAO {

    void registrarEvento(Connection conn, int idUsuario, String accion, String entidadAfectada,
                         String datosAnteriores, String datosNuevos) throws SQLException;

    List<RegistroAuditoria> listarUltimosEventos(Connection conn, int limite) throws SQLException;

    List<RegistroAuditoria> listarEventosFiltrados(Connection conn, String usuario, String accion,
                                                   LocalDate desde, LocalDate hasta,
                                                   int offset, int limit) throws SQLException;

    int contarEventosFiltrados(Connection conn, String usuario, String accion,
                               LocalDate desde, LocalDate hasta) throws SQLException;

    List<String> listarUsuariosDistinct(Connection conn) throws SQLException;

    List<String> listarAccionesDistinct(Connection conn) throws SQLException;

    ResumenSeguridadHoy obtenerResumenSeguridadHoy(Connection conn) throws SQLException;
}
