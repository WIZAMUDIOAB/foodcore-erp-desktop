package com.foodcore.dao;

import com.foodcore.dao.impl.CompraDAOImpl;
import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.dao.impl.UsuarioDAOImpl;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.Usuario;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.EncriptadorPassword;
import com.foodcore.service.impl.AuthServiceImpl;
import com.foodcore.service.impl.PedidoServiceImpl;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de integración contra MySQL real para verificar inmunidad frente a inyección SQL
 * en autenticación, búsqueda de pedidos por estado, filtros de fecha y constructores dinámicos.
 */
class SqlInjectionPreventionIT {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final List<String> PAYLOADS_SQLI = List.of(
        "' OR '1'='1",
        "' OR 1=1 -- ",
        "admin' -- ",
        "REGISTRADO' OR '1'='1",
        "'; DROP TABLE pedido; --",
        "'; DROP TABLE usuario; --",
        "' UNION SELECT 1, 2, 3 -- "
    );

    private UsuarioDAOImpl usuarioDAO;
    private AuthServiceImpl authService;
    private PedidoDAOImpl pedidoDAO;
    private PedidoServiceImpl pedidoService;
    private Integer tempEmpleadoId;
    private Integer tempUsuarioId;
    private Integer tempCategoriaId;
    private Integer tempProductoId;
    private Integer tempPedidoId;

    @BeforeEach
    void setUp() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        usuarioDAO = new UsuarioDAOImpl();
        authService = new AuthServiceImpl(usuarioDAO, new EncriptadorPassword());
        pedidoDAO = new PedidoDAOImpl();
        pedidoService = new PedidoServiceImpl(new JdbcTransactionManager(), pedidoDAO, new AutorizacionService());
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            if (tempPedidoId != null) {
                try (PreparedStatement psDet = conn.prepareStatement("DELETE FROM detalle_pedido WHERE id_pedido = ?");
                     PreparedStatement psPed = conn.prepareStatement("DELETE FROM pedido WHERE id_pedido = ?")) {
                    psDet.setInt(1, tempPedidoId);
                    psDet.executeUpdate();
                    psPed.setInt(1, tempPedidoId);
                    psPed.executeUpdate();
                }
            }
            if (tempProductoId != null) {
                try (PreparedStatement psProd = conn.prepareStatement("DELETE FROM producto WHERE id_producto = ?")) {
                    psProd.setInt(1, tempProductoId);
                    psProd.executeUpdate();
                }
            }
            if (tempCategoriaId != null) {
                try (PreparedStatement psCat = conn.prepareStatement("DELETE FROM categoria WHERE id_categoria = ?")) {
                    psCat.setInt(1, tempCategoriaId);
                    psCat.executeUpdate();
                }
            }
            if (tempUsuarioId != null) {
                try (PreparedStatement psBlk = conn.prepareStatement("DELETE FROM token_blacklist WHERE id_usuario = ?");
                     PreparedStatement psAud = conn.prepareStatement("DELETE FROM auditoria WHERE id_usuario = ?");
                     PreparedStatement psUsr = conn.prepareStatement("DELETE FROM usuario WHERE id_usuario = ?")) {
                    psBlk.setInt(1, tempUsuarioId);
                    psBlk.executeUpdate();
                    psAud.setInt(1, tempUsuarioId);
                    psAud.executeUpdate();
                    psUsr.setInt(1, tempUsuarioId);
                    psUsr.executeUpdate();
                }
            }
            if (tempEmpleadoId != null) {
                try (PreparedStatement psEmp = conn.prepareStatement("DELETE FROM empleado WHERE id_empleado = ?")) {
                    psEmp.setInt(1, tempEmpleadoId);
                    psEmp.executeUpdate();
                }
            }
        }
    }

    @Test
    @DisplayName("SqlQueryBuilder: construye placeholders seguros y rechaza columnas fuera de whitelist")
    void sqlQueryBuilderRechazaValoresFueraDeListaBlanca() {
        assertEquals("?", SqlQueryBuilder.buildInPlaceholders(1));
        assertEquals("?,?,?", SqlQueryBuilder.buildInPlaceholders(3));
        assertThrows(IllegalArgumentException.class, () -> SqlQueryBuilder.buildInPlaceholders(0));
        assertThrows(IllegalArgumentException.class,
            () -> SqlQueryBuilder.buildInPlaceholders(SqlQueryBuilder.MAX_IN_PLACEHOLDERS + 1));

        Map<String, String> whitelist = Map.of("fecha", "p.fecha_hora", "total", "p.total");
        assertEquals("p.fecha_hora", SqlQueryBuilder.resolveWhitelistedIdentifier("fecha", whitelist));
        assertThrows(IllegalArgumentException.class,
            () -> SqlQueryBuilder.resolveWhitelistedIdentifier("fecha; DROP TABLE pedido;--", whitelist));
        assertThrows(IllegalArgumentException.class,
            () -> SqlQueryBuilder.resolveWhitelistedIdentifier(null, whitelist));
    }

    @Test
    @DisplayName("Login y lookup de usuario neutralizan payloads SQLi e intactan datos reales")
    void loginYBusquedaUsuarioNeutralizanSqlInjection() throws Exception {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String username = "sqli_usr_" + sufijo;
        String passwordPlano = "SqliPass#2026!";
        String dni = "9" + String.format("%07d", Math.abs(sufijo.hashCode()) % 10_000_000);
        String hash = EncriptadorPassword.generarHash(passwordPlano);

        try (Connection conn = ConexionBD.getConnection()) {
            try (PreparedStatement psEmp = conn.prepareStatement(
                    "INSERT INTO empleado (dni, nombres, apellidos, telefono, cargo, sueldo_base, fecha_ingreso, estado) "
                    + "VALUES (?, 'Test', 'Sqli', '999888777', 'Cajero', 1500.00, '2026-01-01', 1)",
                    Statement.RETURN_GENERATED_KEYS)) {
                psEmp.setString(1, dni);
                psEmp.executeUpdate();
                try (ResultSet rs = psEmp.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    tempEmpleadoId = rs.getInt(1);
                }
            }

            try (PreparedStatement psUsr = conn.prepareStatement(
                    "INSERT INTO usuario (id_empleado, id_rol, username, nombre_usuario, nombre_completo, "
                    + "password_hash, hash_password, codigo_rol, activo, intentos_fallidos, bloqueado_hasta, cambio_password_requerido) "
                    + "VALUES (?, 1, ?, ?, 'Usuario Prueba SQLi', ?, ?, 'ADMIN', 1, 0, NULL, 0)",
                    Statement.RETURN_GENERATED_KEYS)) {
                psUsr.setInt(1, tempEmpleadoId);
                psUsr.setString(2, username);
                psUsr.setString(3, username);
                psUsr.setString(4, hash);
                psUsr.setString(5, hash);
                psUsr.executeUpdate();
                try (ResultSet rs = psUsr.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    tempUsuarioId = rs.getInt(1);
                }
            }

            for (String payload : PAYLOADS_SQLI) {
                Optional<Usuario> lookup = usuarioDAO.buscarPorUsername(conn, payload);
                assertTrue(lookup.isEmpty(), "Payload no debe devolver usuario en buscarPorUsername: " + payload);

                Optional<Usuario> authDao = usuarioDAO.autenticar(conn, payload, passwordPlano);
                assertTrue(authDao.isEmpty(), "Payload no debe autenticar en UsuarioDAO: " + payload);

                NegocioException ex = assertThrows(NegocioException.class,
                    () -> authService.login(conn, payload, passwordPlano));
                assertEquals(CodigoError.CREDENCIALES_INVALIDAS, ex.getCodigoError());
            }

            // También probar payload en el parámetro de contraseña manteniendo usuario válido
            NegocioException exPassPayload = assertThrows(NegocioException.class,
                () -> authService.login(conn, username, "' OR '1'='1"));
            assertEquals(CodigoError.CREDENCIALES_INVALIDAS, exPassPayload.getCodigoError());

            // El usuario legítimo sigue operativo y la tabla usuario no fue alterada
            Usuario autenticado = authService.login(conn, username, passwordPlano);
            assertNotNull(autenticado);
            assertEquals(username, autenticado.getUsername());
        }
    }

    @Test
    @DisplayName("Búsqueda de pedidos por estado y filtros de fecha neutralizan SQLi sin dañar registros")
    void busquedaPedidosYFiltrosFechaNeutralizanSqlInjection() throws Exception {
        String sufijo = UUID.randomUUID().toString().substring(0, 4);
        String mesa = "SQ" + sufijo;

        try (Connection conn = ConexionBD.getConnection()) {
            try (PreparedStatement psCat = conn.prepareStatement(
                    "INSERT INTO categoria (nombre, descripcion) VALUES (?, 'SQLi IT')",
                    Statement.RETURN_GENERATED_KEYS)) {
                psCat.setString(1, "Cat SQLi " + mesa);
                psCat.executeUpdate();
                try (ResultSet rs = psCat.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    tempCategoriaId = rs.getInt(1);
                }
            }
            try (PreparedStatement psProd = conn.prepareStatement(
                    "INSERT INTO producto (id_categoria, nombre, precio_venta, stock_disponible) "
                    + "VALUES (?, ?, 25.00, 30)",
                    Statement.RETURN_GENERATED_KEYS)) {
                psProd.setInt(1, tempCategoriaId);
                psProd.setString(2, "Plato SQLi " + mesa);
                psProd.executeUpdate();
                try (ResultSet rs = psProd.getGeneratedKeys()) {
                    assertTrue(rs.next());
                    tempProductoId = rs.getInt(1);
                }
            }
        }

        Pedido p = new Pedido(0, 1, null, mesa, "REGISTRADO", new BigDecimal("25.00"));
        DetallePedido det = new DetallePedido(0, 0, tempProductoId, "Plato SQLi", 1,
            new BigDecimal("25.00"), new BigDecimal("25.00"), "");
        Pedido creado = pedidoService.crearPedido(p, List.of(det));
        tempPedidoId = creado.getIdPedido();

        LocalDateTime ahora = LocalDateTime.now(ZONA_LIMA);
        LocalDateTime desde = ahora.minusDays(1);
        LocalDateTime hasta = ahora.plusDays(1);

        try (Connection conn = ConexionBD.getConnection()) {
            for (String payload : PAYLOADS_SQLI) {
                List<Pedido> porEstado = pedidoDAO.buscarPorEstado(conn, payload);
                assertTrue(porEstado.isEmpty(), "Payload SQLi en estado no debe devolver pedidos: " + payload);

                List<Pedido> porEstadoYFechas = pedidoDAO.buscarPorEstadoYFechas(conn, payload, desde, hasta);
                assertTrue(porEstadoYFechas.isEmpty(), "Payload SQLi en filtro combinado no debe devolver pedidos: " + payload);
            }

            // Validar que un rango de fechas legítimo fuera de ventana no devuelve el pedido
            List<Pedido> fueraDeRango = pedidoDAO.buscarPorEstadoYFechas(
                conn, "REGISTRADO", ahora.plusDays(5), ahora.plusDays(10));
            assertFalse(fueraDeRango.stream().anyMatch(it -> it.getIdPedido() == tempPedidoId));

            // Validar que los datos reales siguen intactos y localizables mediante parámetros vinculados
            List<Pedido> encontrados = pedidoDAO.buscarPorEstado(conn, "REGISTRADO");
            assertTrue(encontrados.stream().anyMatch(it -> it.getIdPedido() == tempPedidoId));

            List<Pedido> enVentana = pedidoDAO.buscarPorEstadoYFechas(conn, "REGISTRADO", desde, hasta);
            assertTrue(enVentana.stream().anyMatch(it -> it.getIdPedido() == tempPedidoId));

            List<Pedido> soloFechas = pedidoDAO.buscarPorEstadoYFechas(conn, null, desde, hasta);
            assertTrue(soloFechas.stream().anyMatch(it -> it.getIdPedido() == tempPedidoId));

            // Verificar también consultas parametrizadas de fechas en compras e insumos
            CompraDAOImpl compraDAO = new CompraDAOImpl();
            assertNotNull(compraDAO.listarPorRangoFecha(conn, LocalDate.now(ZONA_LIMA).minusDays(7),
                LocalDate.now(ZONA_LIMA).plusDays(1)));

            InsumoDAOImpl insumoDAO = new InsumoDAOImpl();
            assertTrue(insumoDAO.lockearPorIds(conn, List.of()).isEmpty());
        }
    }
}
