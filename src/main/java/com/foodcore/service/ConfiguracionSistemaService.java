package com.foodcore.service;

import com.foodcore.dao.ConfiguracionImpresoraDAO;
import com.foodcore.dao.impl.ConfiguracionImpresoraDAOImpl;
import com.foodcore.model.ConfiguracionImpresora;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Servicio de acceso a la configuración general del sistema. */
public class ConfiguracionSistemaService {

    private static final String SQL_CARGAR_CONFIG = "SELECT nombre_restaurante, direccion, telefono, ruc, nombre_impresora "
            + "FROM configuracion_restaurante WHERE id_config = 1";
    private static final String SQL_GUARDAR_CONFIG = "INSERT INTO configuracion_restaurante "
            + "(id_config, nombre_restaurante, direccion, telefono, ruc, nombre_impresora) "
            + "VALUES (1, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE nombre_restaurante = VALUES(nombre_restaurante), "
            + "direccion = VALUES(direccion), telefono = VALUES(telefono), "
            + "ruc = VALUES(ruc), nombre_impresora = VALUES(nombre_impresora)";

    public record RuteoImpresorasData(Map<Integer, String> categorias,
            List<ConfiguracionImpresora> asignaciones) {
    }

    public record DatosRestauranteConfig(String nombreRestaurante, String direccion, String telefono,
            String ruc, String nombreImpresora) {
    }

    private final ConfiguracionImpresoraDAO configImpresoraDAO = new ConfiguracionImpresoraDAOImpl();

    public RuteoImpresorasData cargarRuteoImpresoras() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return new RuteoImpresorasData(configImpresoraDAO.obtenerMapaNombreCategorias(conn),
                    configImpresoraDAO.listarTodas(conn));
        }
    }

    public void asignarImpresoraCategoria(int idCategoria, String nombreImpresora) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            configImpresoraDAO.guardarOActualizar(conn, idCategoria, nombreImpresora);
        }
    }

    public void eliminarImpresoraCategoria(int idCategoria) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            configImpresoraDAO.eliminarPorCategoria(conn, idCategoria);
        }
    }

    public Optional<DatosRestauranteConfig> cargarConfiguracionRestaurante() throws SQLException {
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_CARGAR_CONFIG);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new DatosRestauranteConfig(rs.getString("nombre_restaurante"),
                    rs.getString("direccion"), rs.getString("telefono"), rs.getString("ruc"),
                    rs.getString("nombre_impresora")));
        }
    }

    public void guardarConfiguracionRestaurante(DatosRestauranteConfig cfg) throws SQLException {
        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_GUARDAR_CONFIG)) {
            ps.setString(1, cfg.nombreRestaurante());
            ps.setString(2, cfg.direccion());
            ps.setString(3, cfg.telefono());
            ps.setString(4, cfg.ruc());
            ps.setString(5, cfg.nombreImpresora());
            ps.executeUpdate();
        }
    }
}
