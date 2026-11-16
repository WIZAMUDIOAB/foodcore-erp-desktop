package com.foodcore.service;

import com.foodcore.dao.impl.AuditoriaDAOImpl;
import com.foodcore.util.ConexionBD;
import org.flywaydb.core.internal.jdbc.JdbcTemplate;
import org.flywaydb.core.internal.jdbc.Results;
import org.flywaydb.database.mysql.MySQLDatabaseType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestión transaccional de datos de demostración descartables (Sub-Fase E - E3).
 * Carga el script {@code scripts/seed-demo.sql} y depura exclusivamente los registros con {@code es_demo = 1}.
 */
public class DemoDataService {

    private static final Path DEFAULT_SCRIPT_PATH = Path.of("scripts", "seed-demo.sql");
    private final AuditoriaDAOImpl auditoriaDAO = new AuditoriaDAOImpl();

    public record ResumenDemo(int clientes, int productos, int ventas) {
        public int total() {
            return clientes + productos + ventas;
        }
    }

    public ResumenDemo cargarDatosDemo() throws IOException, SQLException {
        return cargarDatosDemo(DEFAULT_SCRIPT_PATH);
    }

    public ResumenDemo cargarDatosDemo(Path scriptPath) throws IOException, SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                throw new SQLException("Sin conexión activa a base de datos.");
            }
            return cargarDatosDemo(conn, scriptPath);
        }
    }

    public ResumenDemo cargarDatosDemo(Connection conn, Path scriptPath) throws IOException, SQLException {
        if (conn == null) {
            throw new SQLException("La conexión SQL es obligatoria.");
        }
        Path ruta = (scriptPath != null ? scriptPath : DEFAULT_SCRIPT_PATH).toAbsolutePath().normalize();
        if (!Files.isRegularFile(ruta)) {
            throw new IOException("No se encontró el script SQL de demostración en: " + ruta);
        }

        List<String> sentencias = parsearSentenciasSql(Files.readString(ruta, StandardCharsets.UTF_8));
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            JdbcTemplate jdbc = new JdbcTemplate(conn, new MySQLDatabaseType());
            for (String stmt : sentencias) {
                Results results = jdbc.executeStatement(stmt);
                if (results.getException() != null) {
                    throw results.getException();
                }
            }
            conn.commit();
        } catch (SQLException | RuntimeException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }

        ResumenDemo resumen = contarDatosDemo(conn);
        auditoriaDAO.registrarEventoBestEffort(
                resolverUsuarioAuditoria(),
                "CARGAR_DATOS_DEMO",
                "DEMO",
                null,
                "Datos demo activos: clientes=" + resumen.clientes()
                        + ", productos=" + resumen.productos()
                        + ", ventas=" + resumen.ventas());
        return resumen;
    }

    public int borrarDatosDemo() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                throw new SQLException("Sin conexión activa a base de datos.");
            }
            return borrarDatosDemo(conn);
        }
    }

    public int borrarDatosDemo(Connection conn) throws SQLException {
        if (conn == null) {
            throw new SQLException("La conexión SQL es obligatoria.");
        }
        boolean prevAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        int eliminados = 0;
        try {
            eliminados += ejecutarDelete(conn,
                    "DELETE FROM detalle_venta WHERE es_demo = 1 "
                    + "OR id_venta IN (SELECT id_venta FROM venta WHERE es_demo = 1)");
            eliminados += ejecutarDelete(conn,
                    "DELETE FROM comprobante_pago WHERE id_venta IN (SELECT id_venta FROM venta WHERE es_demo = 1)");
            eliminados += ejecutarDelete(conn,
                    "DELETE FROM fidelizacion_movimiento WHERE id_venta IN (SELECT id_venta FROM venta WHERE es_demo = 1) "
                    + "OR id_cliente IN (SELECT id_cliente FROM cliente WHERE es_demo = 1)");
            eliminados += ejecutarDelete(conn,
                    "DELETE FROM historial_puntos WHERE id_venta IN (SELECT id_venta FROM venta WHERE es_demo = 1) "
                    + "OR id_cliente IN (SELECT id_cliente FROM cliente WHERE es_demo = 1)");
            eliminados += ejecutarDelete(conn,
                    "DELETE FROM cuenta_por_cobrar WHERE id_venta IN (SELECT id_venta FROM venta WHERE es_demo = 1) "
                    + "OR id_cliente IN (SELECT id_cliente FROM cliente WHERE es_demo = 1)");
            eliminados += ejecutarDelete(conn,
                    "DELETE FROM cupon_cumpleanos WHERE id_cliente IN (SELECT id_cliente FROM cliente WHERE es_demo = 1)");
            eliminados += ejecutarDelete(conn,
                    "DELETE FROM venta WHERE es_demo = 1");
            eliminados += ejecutarDelete(conn,
                    "DELETE FROM producto WHERE es_demo = 1 "
                    + "AND NOT EXISTS (SELECT 1 FROM detalle_venta dv WHERE dv.id_producto = producto.id_producto)");
            eliminados += ejecutarDelete(conn,
                    "DELETE FROM cliente WHERE es_demo = 1 "
                    + "AND NOT EXISTS (SELECT 1 FROM venta v WHERE v.id_cliente = cliente.id_cliente)");
            eliminados += ejecutarDelete(conn,
                    "DELETE FROM categoria WHERE es_demo = 1 "
                    + "AND NOT EXISTS (SELECT 1 FROM producto p WHERE p.id_categoria = categoria.id_categoria)");
            eliminados += ejecutarDelete(conn,
                    "DELETE FROM caja WHERE es_demo = 1 "
                    + "AND NOT EXISTS (SELECT 1 FROM venta v WHERE v.id_caja = caja.id_caja)");
            conn.commit();
        } catch (SQLException | RuntimeException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }

        auditoriaDAO.registrarEventoBestEffort(
                resolverUsuarioAuditoria(),
                "BORRAR_DATOS_DEMO",
                "DEMO",
                null,
                "Registros demo eliminados: " + eliminados);
        return eliminados;
    }

    public ResumenDemo contarDatosDemo() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                return new ResumenDemo(0, 0, 0);
            }
            return contarDatosDemo(conn);
        }
    }

    public ResumenDemo contarDatosDemo(Connection conn) throws SQLException {
        if (conn == null) {
            return new ResumenDemo(0, 0, 0);
        }
        int clientes = consultarConteo(conn, "SELECT COUNT(*) FROM cliente WHERE es_demo = 1");
        int productos = consultarConteo(conn, "SELECT COUNT(*) FROM producto WHERE es_demo = 1");
        int ventas = consultarConteo(conn, "SELECT COUNT(*) FROM venta WHERE es_demo = 1");
        return new ResumenDemo(clientes, productos, ventas);
    }

    static List<String> parsearSentenciasSql(String contenido) {
        List<String> sentencias = new ArrayList<>();
        if (contenido == null || contenido.isBlank()) {
            return sentencias;
        }
        StringBuilder limpio = new StringBuilder();
        for (String linea : contenido.split("\\R")) {
            String trimmed = linea.trim();
            if (!trimmed.startsWith("--")) {
                limpio.append(linea).append('\n');
            }
        }
        for (String bloque : limpio.toString().split(";")) {
            String sql = bloque.trim();
            if (!sql.isEmpty()) {
                sentencias.add(sql);
            }
        }
        return sentencias;
    }

    private static int ejecutarDelete(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            return ps.executeUpdate();
        }
    }

    private static int consultarConteo(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static int resolverUsuarioAuditoria() {
        return AuthService.getUsuarioSesionActual() != null
                ? AuthService.getUsuarioSesionActual().getIdUsuario()
                : 1;
    }
}
