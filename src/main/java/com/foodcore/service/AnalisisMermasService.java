package com.foodcore.service;

import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.MermaDAO;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.dao.impl.MermaDAOImpl;
import com.foodcore.model.Insumo;
import com.foodcore.model.KardexMovimiento;
import com.foodcore.model.Merma;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.UnidadMedidaFormatter;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AnalisisMermasService {

    private static final Logger LOGGER = Logger.getLogger(AnalisisMermasService.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private final MermaDAO mermaDAO;
    private final InsumoDAO insumoDAO;
    private final CierreContableService cierreContableService;

    public AnalisisMermasService() {
        this(new MermaDAOImpl(), new InsumoDAOImpl(), new CierreContableService());
    }

    public AnalisisMermasService(MermaDAO mermaDAO, InsumoDAO insumoDAO) {
        this(mermaDAO, insumoDAO, new CierreContableService());
    }

    public AnalisisMermasService(MermaDAO mermaDAO, InsumoDAO insumoDAO, CierreContableService cierreContableService) {
        this.mermaDAO = mermaDAO;
        this.insumoDAO = insumoDAO;
        this.cierreContableService = cierreContableService != null ? cierreContableService : new CierreContableService();
    }

    public int registrarMerma(long idInsumo, int idUsuario, BigDecimal cantidad, String motivo, String observacion) throws Exception {
        if (idInsumo <= 0) {
            throw new IllegalArgumentException("Debe seleccionar un insumo válido");
        }
        if (cantidad == null || cantidad.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cantidad mermada debe ser mayor a cero");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            conn.setAutoCommit(false);
            try {
                cierreContableService.validarPeriodoAbierto(conn, LocalDate.now(ZONA_LIMA));

                Insumo ins = insumoDAO.obtenerPorId(conn, idInsumo);
                if (ins == null) {
                    throw new IllegalArgumentException("No se encontró el insumo #" + idInsumo);
                }
                BigDecimal costoUnit = ins.getCostoUnitario() != null ? ins.getCostoUnitario() : BigDecimal.ZERO;
                BigDecimal costoTotal = cantidad.multiply(costoUnit);

                Merma m = new Merma();
                m.setIdInsumo(idInsumo);
                m.setIdUsuario(idUsuario > 0 ? idUsuario : 1);
                m.setCantidad(cantidad);
                m.setMotivo(motivo != null ? motivo : "Quemado");
                m.setCostoUnitario(costoUnit);
                m.setCostoTotal(costoTotal);
                m.setObservacion(observacion);
                m.setFechaHora(LocalDateTime.now(ZONA_LIMA));

                int idMerma = mermaDAO.registrarMerma(conn, m);

                // Descuento atómico de stock e inserción en kardex — cualquier fallo revierte toda la transacción
                insumoDAO.registrarMovimientoKardexAtomico(
                        conn, idInsumo, "MERMA", cantidad, idUsuario > 0 ? idUsuario : 1,
                        "Merma (" + m.getMotivo() + "): " + (observacion != null ? observacion : ""));

                conn.commit();
                return idMerma;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Registra una merma descontando el stock del insumo y creando el movimiento {@code MERMA}
     * en el Kardex de forma atómica (§3.3). Toda la lógica transaccional vive en la capa de servicios.
     */
    public KardexMovimiento registrarMermaKardexDirecto(long idInsumo, BigDecimal cantidad, String motivo) throws Exception {
        if (cantidad == null || cantidad.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cantidad de merma debe ser mayor a cero.");
        }
        String obs = (motivo != null && !motivo.isBlank()) ? motivo.trim() : "Merma operativa de cocina";
        long idUsuario = AuthService.getUsuarioSesionActual() != null
            ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1L;

        TransactionManager tx = new JdbcTransactionManager();
        return tx.executeChecked(conn -> {
            insumoDAO.registrarMovimientoInventario(conn, idInsumo, "MERMA", cantidad, obs, null);
            return insumoDAO.registrarMovimientoKardexAtomico(conn, idInsumo, "MERMA", cantidad, idUsuario, obs);
        });
    }

    public List<Merma> listarTodas() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return mermaDAO.listarTodas(conn);
        }
    }

    /**
     * Lista todos los insumos activos disponibles para el registro de mermas.
     *
     * @return insumos activos ordenados por nombre.
     * @throws SQLException si falla la consulta en base de datos.
     */
    public List<Insumo> listarTodosInsumos() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return insumoDAO.listarTodos(conn);
        }
    }

    public Map<String, BigDecimal> obtenerMermasPorMotivo(int anio, int mes) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return mermaDAO.obtenerMermasPorMotivo(conn, anio, mes);
        }
    }

    public Map<String, BigDecimal> obtenerMermasPorMes(int anio) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return mermaDAO.obtenerMermasPorMes(conn, anio);
        }
    }

    public List<Merma> obtenerTopInsumosPerdida(int anio, int mes, int limite) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return mermaDAO.obtenerTopInsumosPerdida(conn, anio, mes, limite);
        }
    }

    /**
     * Fila plana de merma para la vista de registro (§3.3): fecha, insumo, cantidad con unidad,
     * saldo posterior con unidad y motivo/observación.
     */
    public record FilaMermaRegistro(String fecha, String insumo, String cantidad, String saldo, String motivo) {
    }

    private static final String SQL_MOVIMIENTOS_MERMA =
            "SELECT k.created_at, i.nombre AS insumo_nombre, i.unidad_medida, "
                + "k.cantidad, k.saldo_posterior, k.observacion "
                + "FROM kardex_movimiento k JOIN insumo i ON k.id_insumo = i.id_insumo "
                + "WHERE k.tipo_movimiento LIKE 'MERMA%' ORDER BY k.id_kardex DESC LIMIT 100";

    /**
     * Consulta las mermas recientes del Kárdex junto con el listado de insumos activos.
     * Toda la apertura de conexión y el mapeo JDBC vive en la capa de servicios/DAO.
     */
    public DatosMermasCarga consultarDatosMermas() {
        List<Insumo> insumos;
        List<FilaMermaRegistro> filas;
        try (Connection conn = ConexionBD.getConnection()) {
            insumos = insumoDAO.listarTodos(conn);
            filas = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(SQL_MOVIMIENTOS_MERMA);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String fecha = String.valueOf(rs.getTimestamp("created_at")).substring(0, 16);
                    String nom = rs.getString("insumo_nombre");
                    String um = rs.getString("unidad_medida");
                    String cant = UnidadMedidaFormatter.formatear(rs.getBigDecimal("cantidad"), um, true);
                    String saldo = UnidadMedidaFormatter.formatear(rs.getBigDecimal("saldo_posterior"), um, false);
                    String obs = rs.getString("observacion");
                    filas.add(new FilaMermaRegistro(fecha, nom, cant, saldo,
                            obs != null ? obs : "Merma de cocina"));
                }
            }
        } catch (java.sql.SQLException | RuntimeException e) {
            LOGGER.log(Level.FINE, "Error cargando mermas", e);
            insumos = new ArrayList<>();
            filas = new ArrayList<>();
        }
        return new DatosMermasCarga(insumos, filas);
    }

    public List<Merma> listarMovimientosMermaRecientes(int limite) {
        try (Connection conn = ConexionBD.getConnection()) {
            return mermaDAO.listarMovimientosMermaRecientes(conn, limite);
        } catch (java.sql.SQLException e) {
            LOGGER.log(Level.FINE, "Error listando movimientos de merma recientes", e);
            return List.of();
        }
    }

    public record DatosMermasCarga(List<Insumo> insumos, List<FilaMermaRegistro> filas) {
    }

    public List<String> obtenerAlertasInsumosCriticos(int anio, int mes) throws SQLException {
        List<String> alertas = new ArrayList<>();
        try (Connection conn = ConexionBD.getConnection()) {
            List<Insumo> todos = insumoDAO.listarTodos(conn);
            for (Insumo ins : todos) {
                int cantMermas = mermaDAO.contarMermasPorInsumoEnMes(conn, ins.getIdInsumo(), anio, mes);
                if (cantMermas >= 3) {
                    alertas.add(String.format("Alerta Crítica: El insumo '%s' registra %d mermas en el mes actual. Revisar rotación y cocción.",
                            ins.getNombre(), cantMermas));
                }
            }
        }
        return alertas;
    }
}
