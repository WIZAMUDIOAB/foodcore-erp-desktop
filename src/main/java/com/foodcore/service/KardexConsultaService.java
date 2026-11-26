package com.foodcore.service;

import com.foodcore.util.ConexionBD;
import com.foodcore.util.UnidadMedidaFormatter;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio de consulta del Kárdex de insumos según UI-REDESIGN-SPEC-FOODCORE-v1.1 (§10)
 * y SPEC-FOODCORE-v1.2 (§1.1): centraliza el acceso JDBC, el formateo con
 * {@link UnidadMedidaFormatter} y los datos semilla de demostración.
 */
public class KardexConsultaService {

    private static final Logger LOGGER = Logger.getLogger(KardexConsultaService.class.getName());

    private static final String SQL_NOMBRES_INSUMOS = "SELECT nombre FROM insumo ORDER BY nombre";

    private static final String SQL_MOVIMIENTOS_KARDEX =
            "SELECT k.created_at, k.tipo_movimiento, k.referencia_tipo, i.nombre AS insumo_nombre, i.unidad_medida, "
                + "k.cantidad, k.saldo_posterior, COALESCE(u.username, 'admin') AS usuario "
                + "FROM kardex_movimiento k "
                + "JOIN insumo i ON k.id_insumo = i.id_insumo "
                + "LEFT JOIN usuario u ON k.id_usuario = u.id_usuario "
                + "ORDER BY k.created_at DESC, k.id_kardex DESC LIMIT 5000";

    /**
     * Fila plana del Kárdex para la vista: fecha, tipo, insumo, cantidad con unidad,
     * saldo posterior con unidad y usuario que registró el movimiento.
     */
    public record FilaKardexData(String fecha, String tipo, String insumo, String cantidad, String saldo, String usuario) {
    }

    public record KardexCargaData(List<String> insumos, List<FilaKardexData> filas) {
        public KardexCargaData {
            insumos = insumos != null ? List.copyOf(insumos) : List.of();
            filas = filas != null ? List.copyOf(filas) : List.of();
        }
    }

    /**
     * Consulta los movimientos del Kárdex junto con el listado de nombres de insumos.
     * Toda la apertura de conexión y el mapeo JDBC vive en la capa de servicios.
     */
    public KardexCargaData consultarMovimientosKardex() {
        List<String> listaInsumos = new ArrayList<>();
        listaInsumos.add("Todos los insumos");
        List<FilaKardexData> filas = new ArrayList<>();

        try (Connection conn = ConexionBD.getConnection()) {
            try (PreparedStatement psIns = conn.prepareStatement(SQL_NOMBRES_INSUMOS);
                 ResultSet rsI = psIns.executeQuery()) {
                while (rsI.next()) {
                    listaInsumos.add(rsI.getString("nombre"));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(SQL_MOVIMIENTOS_KARDEX);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String fecha = String.valueOf(rs.getTimestamp("created_at")).substring(0, 16);
                    String tipoRaw = rs.getString("tipo_movimiento");
                    String refTipo = rs.getString("referencia_tipo");
                    String tipo = resolverEtiquetaTipo(tipoRaw, refTipo);
                    String ins = rs.getString("insumo_nombre");
                    String um = rs.getString("unidad_medida");
                    BigDecimal cantBd = rs.getBigDecimal("cantidad");
                    BigDecimal saldoBd = rs.getBigDecimal("saldo_posterior");
                    String cant = UnidadMedidaFormatter.formatear(cantBd, um, true);
                    String saldo = UnidadMedidaFormatter.formatear(saldoBd, um, false);
                    String usr = rs.getString("usuario");
                    filas.add(new FilaKardexData(fecha, tipo, ins, cant, saldo, usr));
                }
            }
        } catch (java.sql.SQLException | RuntimeException e) {
            LOGGER.log(Level.FINE, "Cargando datos semilla de Kardex", e);
        }

        if (filas.isEmpty()) {
            filas.add(new FilaKardexData("2026-11-04 18:10", "ENTRADA_COMPRA", "Carne de Res Angus 150g",
                    UnidadMedidaFormatter.formatear(new BigDecimal("50.0000"), "UNIDAD", true),
                    UnidadMedidaFormatter.formatear(new BigDecimal("120.0000"), "UNIDAD", false),
                    "admin · Almacén"));
            filas.add(new FilaKardexData("2026-11-04 18:42", "SALIDA_VENTA", "Carne de Res Angus 150g",
                    UnidadMedidaFormatter.formatear(new BigDecimal("-4.0000"), "UNIDAD", true),
                    UnidadMedidaFormatter.formatear(new BigDecimal("116.0000"), "UNIDAD", false),
                    "cajero · POS-01"));
            filas.add(new FilaKardexData("2026-11-04 19:05", "SALIDA_VENTA", "Papa Amarilla Bastón",
                    UnidadMedidaFormatter.formatear(new BigDecimal("-1.8000"), "KG", true),
                    UnidadMedidaFormatter.formatear(new BigDecimal("38.2000"), "KG", false),
                    "cajero · POS-01"));
            filas.add(new FilaKardexData("2026-11-04 19:18", "AJUSTE_INV", "Queso Cheddar Laminado",
                    UnidadMedidaFormatter.formatear(new BigDecimal("10.0000"), "UNIDAD", true),
                    UnidadMedidaFormatter.formatear(new BigDecimal("85.0000"), "UNIDAD", false),
                    "admin · Auditoría"));
            filas.add(new FilaKardexData("2026-11-04 19:35", "MERMA", "Pan Brioche Artesanal",
                    UnidadMedidaFormatter.formatear(new BigDecimal("-2.0000"), "UNIDAD", true),
                    UnidadMedidaFormatter.formatear(new BigDecimal("64.0000"), "UNIDAD", false),
                    "cocinero · KDS"));
            filas.add(new FilaKardexData("2026-11-04 19:50", "SALIDA_VENTA", "Alitas de Pollo Frescas",
                    UnidadMedidaFormatter.formatear(new BigDecimal("-6.0000"), "UNIDAD", true),
                    UnidadMedidaFormatter.formatear(new BigDecimal("94.0000"), "UNIDAD", false),
                    "cajero · POS-01"));
            if (listaInsumos.size() == 1) {
                listaInsumos.add("Carne de Res Angus 150g");
                listaInsumos.add("Papa Amarilla Bastón");
                listaInsumos.add("Queso Cheddar Laminado");
                listaInsumos.add("Pan Brioche Artesanal");
                listaInsumos.add("Alitas de Pollo Frescas");
            }
        }
        return new KardexCargaData(listaInsumos, filas);
    }

    private String resolverEtiquetaTipo(String tipoRaw, String refTipo) {
        if (tipoRaw == null) {
            return "MOVIMIENTO";
        }
        if ("SALIDA".equalsIgnoreCase(tipoRaw) && "VENTA".equalsIgnoreCase(refTipo)) {
            return "SALIDA_VENTA";
        }
        if ("ENTRADA".equalsIgnoreCase(tipoRaw) && "COMPRA".equalsIgnoreCase(refTipo)) {
            return "ENTRADA_COMPRA";
        }
        return tipoRaw;
    }
}
