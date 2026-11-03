package com.foodcore.service;

import com.foodcore.controller.ComandaCardController;
import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.exception.CodigoError;
import com.foodcore.exception.ConflictoEstadoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.impl.PedidoServiceImpl;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de integración de concurrencia optimista en mesas, idempotencia de pedidos,
 * adición post-entrega y cronómetro KDS por ítem según SPEC-FOODCORE-v1.0
 * (§6.2, §8.5, §9.1, §9.2, §15.2; REMEDIATION R-02.5, R-08).
 */
class PedidoServiceTest {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private PedidoServiceImpl pedidoService;
    private PedidoDAOImpl pedidoDAO;
    private int idProductoPrueba;

    @BeforeEach
    void setUp() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        pedidoDAO = new PedidoDAOImpl();
        pedidoService = new PedidoServiceImpl(new JdbcTransactionManager(), pedidoDAO, new AutorizacionService());

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement psCat = conn.prepareStatement(
                 "INSERT INTO categoria (nombre, descripcion) VALUES ('Platos KDS', 'Cocina')",
                 Statement.RETURN_GENERATED_KEYS)) {
            psCat.executeUpdate();
            int idCat;
            try (ResultSet rs = psCat.getGeneratedKeys()) {
                rs.next();
                idCat = rs.getInt(1);
            }
            try (PreparedStatement psProd = conn.prepareStatement(
                    "INSERT INTO producto (id_categoria, nombre, precio_venta, stock_disponible) VALUES (?, 'Alitas BBQ x6', 28.00, 50)",
                    Statement.RETURN_GENERATED_KEYS)) {
                psProd.setInt(1, idCat);
                psProd.executeUpdate();
                try (ResultSet rsP = psProd.getGeneratedKeys()) {
                    rsP.next();
                    idProductoPrueba = rsP.getInt(1);
                }
            }
        }
    }

    @Test
    @DisplayName("R-02.5 (1/4): Concurrencia optimista en mesa (version) -> 1 éxito y 1 ConflictoEstadoException")
    void debeDetectarConflictoOptimistaEnActualizacionSimultaneaDeMesa() throws Exception {
        long idMesa = 1L; // M01 creada en V1__init.sql con version = 0

        // Primera actualización con versionEsperada = 0 tiene éxito e incrementa version a 1
        pedidoService.actualizarEstadoMesaOptimista(idMesa, "OCUPADA", 0);

        // Segunda actualización concurrente que leyó version = 0 debe lanzar ConflictoEstadoException
        assertThrows(
            ConflictoEstadoException.class,
            () -> pedidoService.actualizarEstadoMesaOptimista(idMesa, "OCUPADA", 0)
        );
    }

    @Test
    @DisplayName("R-02.5 (2/4): Idempotencia de comanda -> dos crearPedido con mismo idempotency_key producen exactamente 1 pedido")
    void dosCrearPedidoConMismoIdempotencyKeyDebenProducirUnSoloPedido() throws Exception {
        String idemKey = UUID.randomUUID().toString();

        Pedido p1 = new Pedido(0, 1, null, "M01", "REGISTRADO", new BigDecimal("28.00"));
        p1.setIdempotencyKey(idemKey);
        DetallePedido d1 = new DetallePedido(0, 0, idProductoPrueba, "Alitas BBQ x6", 1,
            new BigDecimal("28.00"), new BigDecimal("28.00"), "Sin picante");

        Pedido p2 = new Pedido(0, 1, null, "M01", "REGISTRADO", new BigDecimal("28.00"));
        p2.setIdempotencyKey(idemKey);
        DetallePedido d2 = new DetallePedido(0, 0, idProductoPrueba, "Alitas BBQ x6", 1,
            new BigDecimal("28.00"), new BigDecimal("28.00"), "Sin picante");

        Pedido creado1 = pedidoService.crearPedido(p1, List.of(d1));
        Pedido creado2 = pedidoService.crearPedido(p2, List.of(d2));

        assertEquals(creado1.getIdPedido(), creado2.getIdPedido(), "Deben retornar el mismo id_pedido");

        try (Connection conn = ConexionBD.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM pedido")) {
            rs.next();
            assertEquals(1, rs.getInt(1), "Debe existir exactamente 1 pedido en base de datos");
        }
    }

    @Test
    @DisplayName("R-02.5 (3/4): Transición ENTREGADO -> ITEMS_ADICIONALES bloqueada sin permiso y permitida con PEDIDO_ADICIONAR_POST_ENTREGA")
    void debeBloquearAdicionPostEntregaSinPermisoYPermitirlaConPermiso() throws Exception {
        Pedido p = new Pedido(0, 1, null, "M02", "REGISTRADO", new BigDecimal("28.00"));
        DetallePedido detInicial = new DetallePedido(0, 0, idProductoPrueba, "Alitas BBQ x6", 1,
            new BigDecimal("28.00"), new BigDecimal("28.00"), "");
        Pedido creado = pedidoService.crearPedido(p, List.of(detInicial));

        try (Connection conn = ConexionBD.getConnection()) {
            pedidoDAO.actualizarEstadoPedido(conn, creado.getIdPedido(), "ENTREGADO");
        }

        SesionUsuario sesionSinPermiso = new SesionUsuario(
            2L, "Mozo Básico", "MOZO",
            EnumSet.of(Permiso.PEDIDO_CREAR),
            "POS-01", LocalDateTime.now(ZONA_LIMA)
        );
        DetallePedido itemExtra = new DetallePedido(0, creado.getIdPedido(), idProductoPrueba, "Alitas BBQ x6", 1,
            new BigDecimal("28.00"), new BigDecimal("28.00"), "Adicional");

        NegocioException exDenegado = assertThrows(
            NegocioException.class,
            () -> pedidoService.adicionarItemsPostEntrega(creado.getIdPedido(), List.of(itemExtra), sesionSinPermiso)
        );
        assertEquals(CodigoError.ACCESO_DENEGADO, exDenegado.getCodigoError());

        SesionUsuario sesionAutorizada = new SesionUsuario(
            1L, "Supervisor Salón", "ADMIN",
            Set.of(Permiso.PEDIDO_ADICIONAR_POST_ENTREGA),
            "POS-01", LocalDateTime.now(ZONA_LIMA)
        );
        Pedido actualizado = pedidoService.adicionarItemsPostEntrega(
            creado.getIdPedido(), List.of(itemExtra), sesionAutorizada
        );
        assertEquals("ITEMS_ADICIONALES", actualizado.getEstado());
        assertEquals(2, actualizado.getDetalles().size());
    }

    @Test
    @DisplayName("R-08 & R-02.5 (4/4): Comanda con 2 ítems (uno de 3 min y otro de 12 min) -> primera tarjeta GREEN, segunda tarjeta RED")
    void comandaConDosItemsDeDistintoTiempoDebeAsignarColoresIndependientesPorItem() throws Exception {
        LocalDateTime ahora = LocalDateTime.now(ZONA_LIMA);
        Pedido pedido = new Pedido(0, 1, null, "M03", "REGISTRADO", new BigDecimal("56.00"));

        DetallePedido itemReciente3Min = new DetallePedido(0, 0, idProductoPrueba, "Alitas BBQ x6", 1,
            new BigDecimal("28.00"), new BigDecimal("28.00"), "Ítem hace 3 min");
        itemReciente3Min.setEnviadoEn(ahora.minusMinutes(3));

        DetallePedido itemCritico12Min = new DetallePedido(0, 0, idProductoPrueba, "Alitas BBQ x6", 1,
            new BigDecimal("28.00"), new BigDecimal("28.00"), "Ítem hace 12 min");
        itemCritico12Min.setEnviadoEn(ahora.minusMinutes(12));

        pedidoService.crearPedido(pedido, List.of(itemReciente3Min, itemCritico12Min));

        try (Connection conn = ConexionBD.getConnection()) {
            List<DetallePedido> itemsKds = pedidoDAO.listarItemsPendientesCocina(conn);
            assertEquals(2, itemsKds.size(), "KDS debe listar 2 tarjetas independientes por detalle_pedido");

            DetallePedido recuperadoReciente = itemsKds.stream()
                .filter(i -> "Ítem hace 3 min".equals(i.getNotasPreparacion()))
                .findFirst().orElseThrow();
            DetallePedido recuperadoCritico = itemsKds.stream()
                .filter(i -> "Ítem hace 12 min".equals(i.getNotasPreparacion()))
                .findFirst().orElseThrow();

            long segReciente = recuperadoReciente.calcularSegundosDesdeEnvio(ahora);
            long segCritico = recuperadoCritico.calcularSegundosDesdeEnvio(ahora);

            assertTrue(segReciente >= 170 && segReciente <= 195);
            assertTrue(segCritico >= 710 && segCritico <= 735);

            assertEquals("GREEN", ComandaCardController.determinarColorPorSegundos(segReciente),
                "El ítem de 3 min debe evaluarse como GREEN (< 5 min)");
            assertEquals("RED", ComandaCardController.determinarColorPorSegundos(segCritico),
                "El ítem de 12 min dentro del mismo pedido debe evaluarse como RED (> 10 min)");
        }
    }
}
