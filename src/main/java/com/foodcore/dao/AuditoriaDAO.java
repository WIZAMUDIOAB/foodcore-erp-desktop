package com.foodcore.dao;

import com.foodcore.model.RegistroAuditoria;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Acceso a datos para la bitácora inmutable de auditoría (§5.2 SPEC-FOODCORE-v1.3-ENTERPRISE).
 */
public interface AuditoriaDAO {

    void registrarEvento(Connection conn, int idUsuario, String accion, String entidadAfectada,
                         String datosAnteriores, String datosNuevos) throws SQLException;

    List<RegistroAuditoria> listarUltimosEventos(Connection conn, int limite) throws SQLException;
}
