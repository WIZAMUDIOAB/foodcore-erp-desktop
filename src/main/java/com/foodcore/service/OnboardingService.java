package com.foodcore.service;

import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio de configuración inicial guiada (Onboarding Wizard - Sub-Fase E E2).
 * Verifica si {@code configuracion_restaurante} está vacía y valida/persiste los 4 pasos.
 */
public class OnboardingService {

    private static final Logger LOGGER = Logger.getLogger(OnboardingService.class.getName());
    private static final List<String> CATEGORIAS_SUGERIDAS = List.of(
            "Hamburguesas", "Platos de Fondo", "Bebidas", "Entradas", "Postres"
    );

    public boolean necesitaOnboarding() {
        try (Connection conn = ConexionBD.getConnection()) {
            return necesitaOnboarding(conn);
        } catch (SQLException | RuntimeException ex) {
            LOGGER.log(Level.FINE, "No se pudo consultar configuracion_restaurante para onboarding", ex);
            return false;
        }
    }

    public boolean necesitaOnboarding(Connection conn) throws SQLException {
        if (conn == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM configuracion_restaurante";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) == 0;
        }
    }

    public List<String> listarCategoriasSugeridas() {
        Set<String> categorias = new LinkedHashSet<>();
        String sql = "SELECT nombre FROM categoria WHERE activo = 1 ORDER BY nombre ASC";
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String n = rs.getString("nombre");
                        if (n != null && !n.isBlank()) {
                            categorias.add(n.trim());
                        }
                    }
                }
            }
        } catch (SQLException | RuntimeException ex) {
            LOGGER.log(Level.FINE, "Usando categorías sugeridas por defecto en onboarding", ex);
        }
        categorias.addAll(CATEGORIAS_SUGERIDAS);
        return new ArrayList<>(categorias);
    }

    public static void validarPasoRestaurante(String nombre, String ruc, String direccion, String telefono) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Ingrese el nombre del restaurante.");
        }
        if (nombre.trim().length() > 120) {
            throw new IllegalArgumentException("El nombre del restaurante no debe exceder 120 caracteres.");
        }
        if (ruc == null || !ruc.trim().matches("\\d{8,15}")) {
            throw new IllegalArgumentException("Ingrese un RUC válido (solo dígitos, entre 8 y 15 cifras).");
        }
        if (direccion == null || direccion.isBlank()) {
            throw new IllegalArgumentException("Ingrese la dirección del local.");
        }
        if (telefono == null || telefono.isBlank()) {
            throw new IllegalArgumentException("Ingrese el teléfono de contacto.");
        }
    }

    public static BigDecimal validarPasoPlatillo(String nombre, String precioTexto, String categoria) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Ingrese el nombre del primer platillo.");
        }
        if (categoria == null || categoria.isBlank()) {
            throw new IllegalArgumentException("Seleccione o ingrese una categoría para el platillo.");
        }
        if (precioTexto == null || precioTexto.isBlank()) {
            throw new IllegalArgumentException("Ingrese el precio de venta del platillo.");
        }
        try {
            BigDecimal precio = new BigDecimal(precioTexto.trim().replace(",", "."));
            if (precio.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("El precio del platillo debe ser mayor a 0.");
            }
            return precio;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Precio inválido: ingrese un número positivo (ej. 24.50).");
        }
    }

    public static int validarPasoMesa(String numeroMesa, String capacidadTexto) {
        if (numeroMesa == null || numeroMesa.isBlank()) {
            throw new IllegalArgumentException("Ingrese el número o código de la primera mesa.");
        }
        if (numeroMesa.trim().length() > 10) {
            throw new IllegalArgumentException("El número de mesa no debe exceder 10 caracteres.");
        }
        if (capacidadTexto == null || capacidadTexto.isBlank()) {
            throw new IllegalArgumentException("Ingrese la capacidad de la mesa.");
        }
        try {
            int capacidad = Integer.parseInt(capacidadTexto.trim());
            if (capacidad < 1 || capacidad > 50) {
                throw new IllegalArgumentException("La capacidad de la mesa debe estar entre 1 y 50 personas.");
            }
            return capacidad;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Capacidad inválida: ingrese un número entero (ej. 4).");
        }
    }

    /**
     * Crea la categoría si no existe y registra o actualiza el primer platillo del wizard.
     */
    public int guardarPrimerPlatillo(Connection conn, int idProductoExistente,
                                     String nombre, BigDecimal precio, String categoriaNombre) throws SQLException {
        BigDecimal precioValidado = validarPasoPlatillo(
                nombre, precio != null ? precio.toPlainString() : "", categoriaNombre);
        int idCategoria = resolverOCrearCategoria(conn, categoriaNombre.trim());
        String nombreLimpio = nombre.trim();
        BigDecimal costoEstimado = precioValidado.multiply(new BigDecimal("0.40"));

        if (idProductoExistente > 0) {
            String sqlUpd = "UPDATE producto SET id_categoria = ?, nombre = ?, precio_venta = ?, "
                    + "costo_estimado = ?, disponible = 1, activo = 1 WHERE id_producto = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlUpd)) {
                ps.setInt(1, idCategoria);
                ps.setString(2, nombreLimpio);
                ps.setBigDecimal(3, precioValidado);
                ps.setBigDecimal(4, costoEstimado);
                ps.setInt(5, idProductoExistente);
                if (ps.executeUpdate() > 0) {
                    return idProductoExistente;
                }
            }
        }

        String codigo = "ONB-" + String.format("%05d", Math.floorMod(System.nanoTime(), 100_000L));
        String sqlIns = "INSERT INTO producto (id_categoria, codigo, nombre, descripcion, precio_venta, "
                + "costo_estimado, disponible, activo) VALUES (?, ?, ?, ?, ?, ?, 1, 1)";
        try (PreparedStatement ps = conn.prepareStatement(sqlIns, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, idCategoria);
            ps.setString(2, codigo);
            ps.setString(3, nombreLimpio);
            ps.setString(4, "Platillo inicial creado en Onboarding Wizard");
            ps.setBigDecimal(5, precioValidado);
            ps.setBigDecimal(6, costoEstimado);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("No se pudo generar el ID del platillo en Onboarding.");
    }

    /**
     * Registra o actualiza la primera mesa con su capacidad en BD.
     */
    public void guardarPrimeraMesa(Connection conn, String numeroMesa, int capacidad) throws SQLException {
        validarPasoMesa(numeroMesa, String.valueOf(capacidad));
        String sql = "INSERT INTO mesa (numero, capacidad, estado, version, activo) "
                + "VALUES (?, ?, 'LIBRE', 0, 1) "
                + "ON DUPLICATE KEY UPDATE capacidad = VALUES(capacidad), activo = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, numeroMesa.trim());
            ps.setInt(2, capacidad);
            ps.executeUpdate();
        }
    }

    /**
     * Persiste la configuración inicial del restaurante en {@code configuracion_restaurante}.
     */
    public void finalizarOnboarding(Connection conn, String nombre, String ruc,
                                    String direccion, String telefono) throws SQLException {
        validarPasoRestaurante(nombre, ruc, direccion, telefono);
        String sql = "INSERT INTO configuracion_restaurante "
                + "(id_config, nombre_restaurante, direccion, telefono, ruc, nombre_impresora) "
                + "VALUES (1, ?, ?, ?, ?, 'EPSON-TM-T20III') "
                + "ON DUPLICATE KEY UPDATE "
                + "nombre_restaurante = VALUES(nombre_restaurante), "
                + "direccion = VALUES(direccion), "
                + "telefono = VALUES(telefono), "
                + "ruc = VALUES(ruc)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombre.trim());
            ps.setString(2, direccion.trim());
            ps.setString(3, telefono.trim());
            ps.setString(4, ruc.trim());
            ps.executeUpdate();
        }
    }

    private int resolverOCrearCategoria(Connection conn, String categoriaNombre) throws SQLException {
        String sqlBuscar = "SELECT id_categoria FROM categoria WHERE UPPER(nombre) = UPPER(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sqlBuscar)) {
            ps.setString(1, categoriaNombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id_categoria");
                }
            }
        }
        String sqlInsert = "INSERT INTO categoria (nombre, descripcion, activo) VALUES (?, ?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, categoriaNombre);
            ps.setString(2, "Categoría creada en configuración inicial");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("No se pudo crear la categoría '" + categoriaNombre + "'.");
    }
}
