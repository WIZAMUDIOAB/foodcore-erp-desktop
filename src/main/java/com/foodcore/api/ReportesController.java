package com.foodcore.api;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.service.MenuQrService;
import com.foodcore.util.ConexionBD;
import io.javalin.http.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador Javalin para el Dashboard Ejecutivo del Dueño y reportes gerenciales (v5.7.1 Hardening):
 * filtros diarios SARGable por fecha actual, unión real con {@code comprobante_pago}
 * y datos demo estrictamente acotados a {@code -Dfoodcore.demoFallback=true}.
 */
public class ReportesController {

    private static final Logger LOGGER = Logger.getLogger(ReportesController.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private final ApiState state;
    private final MenuQrService menuQrService;

    public ReportesController(ApiState state) {
        this(state, new MenuQrService());
    }

    public ReportesController(ApiState state, MenuQrService menuQrService) {
        this.state = state;
        this.menuQrService = menuQrService != null ? menuQrService : new MenuQrService();
    }

    public void handleDashboardKpis(Context ctx) {
        BigDecimal totalVentas = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int transacciones = 0;
        BigDecimal ticketPromedio = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        String platoTop = "";
        int platoTopCant = 0;
        int mesasOcupadas = 0;
        int mesasTotales = 12;
        boolean bdConsultada = false;

        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                bdConsultada = true;
                String sqlKpi = "SELECT COALESCE(SUM(total), 0.00) AS suma, COUNT(*) AS tx, COALESCE(AVG(total), 0.00) AS prom "
                        + "FROM venta "
                        + "WHERE estado = 'COMPLETADA' "
                        + "  AND fecha_hora >= CURDATE() "
                        + "  AND fecha_hora < DATE_ADD(CURDATE(), INTERVAL 1 DAY)";
                try (PreparedStatement ps = conn.prepareStatement(sqlKpi);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        BigDecimal s = rs.getBigDecimal("suma");
                        totalVentas = s != null ? s.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                        transacciones = rs.getInt("tx");
                        BigDecimal p = rs.getBigDecimal("prom");
                        ticketPromedio = (transacciones > 0 && p != null)
                                ? p.setScale(2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    }
                }

                String sqlTop = "SELECT p.nombre, COALESCE(SUM(dv.cantidad), 0) AS unidades "
                        + "FROM detalle_venta dv "
                        + "JOIN producto p ON dv.id_producto = p.id_producto "
                        + "JOIN venta v ON dv.id_venta = v.id_venta "
                        + "WHERE v.estado = 'COMPLETADA' "
                        + "  AND v.fecha_hora >= CURDATE() "
                        + "  AND v.fecha_hora < DATE_ADD(CURDATE(), INTERVAL 1 DAY) "
                        + "GROUP BY p.id_producto, p.nombre "
                        + "ORDER BY unidades DESC LIMIT 1";
                try (PreparedStatement ps = conn.prepareStatement(sqlTop);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        platoTop = rs.getString("nombre");
                        platoTopCant = rs.getInt("unidades");
                    }
                }

                String sqlMesas = "SELECT COUNT(*) AS total, "
                        + "COALESCE(SUM(CASE WHEN UPPER(estado) = 'OCUPADA' THEN 1 ELSE 0 END), 0) AS ocupadas "
                        + "FROM mesa";
                try (PreparedStatement ps = conn.prepareStatement(sqlMesas);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int totalDb = rs.getInt("total");
                        if (totalDb > 0) {
                            mesasTotales = totalDb;
                        }
                        mesasOcupadas = rs.getInt("ocupadas");
                    }
                }
            }
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.FINE, "Aviso consultando KPIs del día en BD", e);
        }

        if (!bdConsultada && ApiState.isDemoFallbackEnabled()) {
            totalVentas = new BigDecimal("450.00");
            transacciones = 6;
            ticketPromedio = new BigDecimal("75.00");
            platoTop = "Hamburguesa Royal Doble";
            platoTopCant = 14;
            mesasOcupadas = 2;
        }

        ObjectNode resp = state.mapper.createObjectNode();
        resp.put("ventas_dia", totalVentas.doubleValue());
        resp.put("transacciones", transacciones);
        resp.put("ticket_promedio", ticketPromedio.doubleValue());
        resp.put("plato_top", platoTop);
        resp.put("plato_top_unidades", platoTopCant);
        resp.put("mesas_ocupadas", mesasOcupadas);
        resp.put("mesas_totales", mesasTotales);
        resp.put("mesas_libres", Math.max(0, mesasTotales - mesasOcupadas));

        ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
    }

    public void handleDashboardVentasPorHora(Context ctx) {
        ArrayNode root = state.mapper.createArrayNode();
        Map<String, BigDecimal> ventasPorHora = new TreeMap<>();

        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                String sqlHora = "SELECT DATE_FORMAT(fecha_hora, '%H:00') AS hora, COALESCE(SUM(total), 0.00) AS monto "
                        + "FROM venta "
                        + "WHERE estado = 'COMPLETADA' "
                        + "  AND fecha_hora >= CURDATE() "
                        + "  AND fecha_hora < DATE_ADD(CURDATE(), INTERVAL 1 DAY) "
                        + "GROUP BY DATE_FORMAT(fecha_hora, '%H:00') "
                        + "ORDER BY hora ASC";
                try (PreparedStatement ps = conn.prepareStatement(sqlHora);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ventasPorHora.put(rs.getString("hora"), rs.getBigDecimal("monto"));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Aviso consultando ventas por hora en BD", e);
        }

        if (ventasPorHora.isEmpty() && ApiState.isDemoFallbackEnabled()) {
            ventasPorHora.put("12:00", new BigDecimal("180.00"));
            ventasPorHora.put("13:00", new BigDecimal("345.50"));
            ventasPorHora.put("14:00", new BigDecimal("490.00"));
            ventasPorHora.put("19:00", new BigDecimal("390.00"));
            ventasPorHora.put("20:00", new BigDecimal("620.00"));
        }

        for (Map.Entry<String, BigDecimal> entry : ventasPorHora.entrySet()) {
            ObjectNode h = root.addObject();
            h.put("hora", entry.getKey());
            h.put("monto", entry.getValue().doubleValue());
        }

        ctx.status(200).contentType("application/json; charset=UTF-8").result(root.toString());
    }

    public void handleDashboardTopPlatos(Context ctx) {
        ArrayNode root = state.mapper.createArrayNode();

        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                String sqlTop = "SELECT p.id_producto, p.nombre, COALESCE(SUM(dv.cantidad), 0) AS unidades, "
                        + "COALESCE(SUM(dv.subtotal), 0.00) AS ingresos "
                        + "FROM detalle_venta dv "
                        + "JOIN producto p ON dv.id_producto = p.id_producto "
                        + "JOIN venta v ON dv.id_venta = v.id_venta "
                        + "WHERE v.estado = 'COMPLETADA' "
                        + "  AND v.fecha_hora >= CURDATE() "
                        + "  AND v.fecha_hora < DATE_ADD(CURDATE(), INTERVAL 1 DAY) "
                        + "GROUP BY p.id_producto, p.nombre "
                        + "ORDER BY unidades DESC LIMIT 5";
                try (PreparedStatement ps = conn.prepareStatement(sqlTop);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ObjectNode item = root.addObject();
                        item.put("id_producto", rs.getInt("id_producto"));
                        item.put("nombre", rs.getString("nombre"));
                        item.put("unidades", rs.getInt("unidades"));
                        item.put("ingresos", rs.getBigDecimal("ingresos").doubleValue());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Aviso consultando top platos del día en BD", e);
        }

        if (root.isEmpty() && ApiState.isDemoFallbackEnabled()) {
            ObjectNode p1 = root.addObject();
            p1.put("id_producto", 1);
            p1.put("nombre", "Hamburguesa Royal Doble");
            p1.put("unidades", 14);
            p1.put("ingresos", 343.00);
        }

        ctx.status(200).contentType("application/json; charset=UTF-8").result(root.toString());
    }

    public void handleDashboardVentasRecientes(Context ctx) {
        ArrayNode root = state.mapper.createArrayNode();

        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                String sql = "SELECT v.id_venta, "
                        + "COALESCE(CONCAT(cp.serie, '-', cp.numero), CONCAT('V-', LPAD(v.id_venta, 6, '0'))) AS numero_comprobante, "
                        + "COALESCE(cp.tipo_comprobante, 'TICKET') AS tipo_comprobante, "
                        + "v.total, "
                        + "v.metodo_pago AS medio_pago, "
                        + "v.fecha_hora, "
                        + "COALESCE(u.username, 'sistema') AS mozo "
                        + "FROM venta v "
                        + "LEFT JOIN comprobante_pago cp ON cp.id_venta = v.id_venta "
                        + "LEFT JOIN usuario u ON v.id_usuario = u.id_usuario "
                        + "WHERE v.estado = 'COMPLETADA' "
                        + "  AND v.fecha_hora >= CURDATE() "
                        + "  AND v.fecha_hora < DATE_ADD(CURDATE(), INTERVAL 1 DAY) "
                        + "ORDER BY v.id_venta DESC LIMIT 10";
                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ObjectNode item = root.addObject();
                        item.put("id_venta", rs.getInt("id_venta"));
                        item.put("numero_comprobante", rs.getString("numero_comprobante"));
                        item.put("tipo_comprobante", rs.getString("tipo_comprobante"));
                        item.put("total", rs.getBigDecimal("total").doubleValue());
                        item.put("medio_pago", rs.getString("medio_pago"));
                        item.put("fecha_hora", rs.getString("fecha_hora"));
                        item.put("mozo", rs.getString("mozo"));
                    }
                }
            }
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.FINE, "Aviso consultando ventas recientes en BD", e);
        }

        if (root.isEmpty() && ApiState.isDemoFallbackEnabled()) {
            ObjectNode demo = root.addObject();
            demo.put("id_venta", 101);
            demo.put("numero_comprobante", "B001-000101");
            demo.put("tipo_comprobante", "BOLETA");
            demo.put("total", 62.50);
            demo.put("medio_pago", "EFECTIVO");
            demo.put("fecha_hora", LocalDateTime.now(ZONA_LIMA).minusMinutes(12).toString());
            demo.put("mozo", "demo");
        }

        ctx.status(200).contentType("application/json; charset=UTF-8").result(root.toString());
    }

    /**
     * Endpoint GET /api/public/menu-digital (y /api/menu-digital/view):
     * consulta MySQL en vivo y sirve el HTML dinámico del Menú Digital sin caché estático (v4.6.0 Fase 2).
     */
    public void handleMenuDigitalView(Context ctx) {
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        ctx.header("Pragma", "no-cache");
        ctx.header("Expires", "0");
        ctx.contentType("text/html; charset=utf-8");
        try {
            String htmlDinamico = menuQrService.construirMenuDigitalHtmlDinamico();
            ctx.status(200).result(htmlDinamico);
        } catch (IllegalStateException e) {
            LOGGER.log(Level.FINE, "BD no inicializada al servir menú digital; devolviendo plantilla vacía segura", e);
            ctx.status(200).result(
                    "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">"
                    + "<title>FOODCORE RESTOBAR &amp; GRILL</title></head>"
                    + "<body><h1>FOODCORE RESTOBAR &amp; GRILL</h1>"
                    + "<p>Carta gastronómica temporalmente sin conexión a base de datos.</p></body></html>"
            );
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al servir menú digital dinámico", e);
            ctx.status(500).result("Error interno al cargar menú digital");
        }
    }
}
