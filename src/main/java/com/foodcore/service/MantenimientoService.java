package com.foodcore.service;

import com.foodcore.dao.*;
import com.foodcore.exception.InsumoNoEncontradoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.*;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Servicio de Mantenimiento de Catálogo, Insumos, Recetas, Kárdex (§8.2) y Diagnóstico de BD.
 */
public class MantenimientoService {

    private final TransactionManager tx;
    private final CategoriaDAO categoriaDAO = new CategoriaDAOImpl();
    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final InsumoDAO insumoDAO = new InsumoDAOImpl();
    private final RecetaDAO recetaDAO = new RecetaDAOImpl();

    public MantenimientoService() {
        this(new JdbcTransactionManager());
    }

    public MantenimientoService(TransactionManager tx) {
        this.tx = tx;
    }

    public List<Categoria> listarCategorias() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return categoriaDAO.listarTodas(conn);
        }
    }

    public void guardarCategoria(Categoria c) throws NegocioException, SQLException {
        if (c.getNombre() == null || c.getNombre().trim().isEmpty()) {
            throw new NegocioException("El nombre de la categoría es obligatorio.");
        }
        try (Connection conn = ConexionBD.getConnection()) {
            if (c.getIdCategoria() == 0) {
                categoriaDAO.crear(conn, c);
            } else {
                categoriaDAO.actualizar(conn, c);
            }
        }
    }

    public List<Producto> listarProductos() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return productoDAO.listarTodos(conn);
        }
    }

    public void guardarProducto(Producto p) throws NegocioException, SQLException {
        if (p.getNombre() == null || p.getNombre().trim().isEmpty()) {
            throw new NegocioException("El nombre del plato/producto es obligatorio.");
        }
        if (p.getCodigo() == null || p.getCodigo().trim().isEmpty()) {
            throw new NegocioException("El código del producto es obligatorio (ej: HAM-01).");
        }
        if (p.getPrecioVenta() == null || p.getPrecioVenta().compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("El precio de venta debe ser mayor a 0.");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            if (p.getIdProducto() == 0) {
                productoDAO.crear(conn, p);
            } else {
                productoDAO.actualizar(conn, p);
            }
        }
    }

    public void alternarDisponibilidadProducto(int idProducto, boolean disponible) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            productoDAO.cambiarDisponibilidad(conn, idProducto, disponible);
        }
    }

    public void eliminarProducto(int idProducto) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            productoDAO.eliminar(conn, idProducto);
        }
    }

    public List<Insumo> listarInsumos() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return insumoDAO.listarTodos(conn);
        }
    }

    public void guardarInsumo(Insumo i) throws NegocioException, SQLException {
        if (i.getNombre() == null || i.getNombre().trim().isEmpty()) {
            throw new NegocioException("El nombre del insumo es obligatorio.");
        }
        if (i.getCodigo() == null || i.getCodigo().trim().isEmpty()) {
            throw new NegocioException("El código del insumo es obligatorio (ej: INS-001).");
        }
        if (i.getUnidadMedida() == null || i.getUnidadMedida().trim().isEmpty()) {
            throw new NegocioException("Seleccione una unidad de medida (GR, UND, ML, KG, LT).");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            if (i.getIdInsumo() == 0L) {
                insumoDAO.crear(conn, i);
            } else {
                insumoDAO.actualizar(conn, i);
            }
        }
    }

    /**
     * Registra un ajuste de stock (fijando nuevo saldo total) sincronizando {@code kardex_movimiento} (§8.2).
     */
    public void registrarAjusteStock(long idInsumo, BigDecimal nuevoStock, String motivo) throws NegocioException, SQLException {
        if (nuevoStock == null || nuevoStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("El stock no puede ser negativo.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new NegocioException("La observación es obligatoria para todo ajuste de Kárdex (§8.2).");
        }
        BigDecimal stockNorm = DesgloseFiscal.normalizarStock(nuevoStock);

        tx.executeVoidChecked(conn -> {
            Map<Long, BigDecimal> bloqueados = insumoDAO.lockearPorIds(conn, List.of(idInsumo));
            if (!bloqueados.containsKey(idInsumo)) {
                throw new InsumoNoEncontradoException(idInsumo);
            }
            BigDecimal saldoAnterior = bloqueados.get(idInsumo);
            BigDecimal delta = stockNorm.subtract(saldoAnterior).setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);

            insumoDAO.actualizarStock(conn, idInsumo, stockNorm);
            insumoDAO.registrarMovimientoInventario(conn, idInsumo, "AJUSTE", delta, motivo, null);

            Insumo ins = insumoDAO.obtenerPorId(conn, idInsumo);
            BigDecimal costo = ins != null && ins.getCostoUnitario() != null ? ins.getCostoUnitario() : BigDecimal.ZERO;
            KardexMovimiento km = new KardexMovimiento(
                0L, idInsumo, "AJUSTE", delta, saldoAnterior, stockNorm, costo, "AJUSTE_MANUAL", null, 1L, motivo, LocalDateTime.now()
            );
            insumoDAO.insertarKardex(conn, km);
        });
    }

    /**
     * Registra un movimiento de Kárdex (ENTRADA, SALIDA, MERMA, DEVOLUCION, AJUSTE por delta)
     * garantizando cantidad firmada según §8.2 y {@code saldo_posterior == stock_actual_disponible}.
     */
    public KardexMovimiento registrarMovimientoKardex(long idInsumo,
                                                      String tipoMovimiento,
                                                      BigDecimal magnitud,
                                                      long idUsuario,
                                                      String observacion) throws NegocioException, SQLException {
        if (magnitud == null || magnitud.compareTo(BigDecimal.ZERO) == 0) {
            throw new NegocioException("La cantidad del movimiento de Kárdex no puede ser cero ni nula.");
        }
        String tipo = tipoMovimiento != null ? tipoMovimiento.trim().toUpperCase() : "";
        if ("AJUSTE".equals(tipo) && (observacion == null || observacion.isBlank())) {
            throw new NegocioException("La observación es obligatoria para movimientos de tipo AJUSTE (§8.2).");
        }

        BigDecimal magNorm = DesgloseFiscal.normalizarStock(magnitud.abs());
        BigDecimal cantidadFirmada;
        switch (tipo) {
            case "ENTRADA", "DEVOLUCION" -> cantidadFirmada = magNorm;
            case "SALIDA", "MERMA" -> cantidadFirmada = magNorm.negate();
            case "AJUSTE" -> cantidadFirmada = DesgloseFiscal.normalizarStock(magnitud);
            default -> throw new NegocioException("Tipo de movimiento de Kárdex desconocido: " + tipo);
        }

        return tx.executeChecked(conn -> {
            Map<Long, BigDecimal> bloqueados = insumoDAO.lockearPorIds(conn, List.of(idInsumo));
            if (!bloqueados.containsKey(idInsumo)) {
                throw new InsumoNoEncontradoException(idInsumo);
            }
            BigDecimal saldoAnterior = bloqueados.get(idInsumo);
            BigDecimal saldoPosterior = saldoAnterior.add(cantidadFirmada)
                .setScale(DesgloseFiscal.ESCALA_STOCK, DesgloseFiscal.MODO_REDONDEO);

            if (saldoPosterior.compareTo(BigDecimal.ZERO) < 0) {
                throw new StockInsuficienteException(idInsumo, cantidadFirmada.abs());
            }

            insumoDAO.actualizarStock(conn, idInsumo, saldoPosterior);
            Insumo ins = insumoDAO.obtenerPorId(conn, idInsumo);
            BigDecimal costo = ins != null && ins.getCostoUnitario() != null ? ins.getCostoUnitario() : BigDecimal.ZERO;

            KardexMovimiento km = new KardexMovimiento(
                0L, idInsumo, tipo, cantidadFirmada, saldoAnterior, saldoPosterior,
                costo, tipo, null, idUsuario, observacion, LocalDateTime.now()
            );
            insumoDAO.insertarKardex(conn, km);
            return km;
        });
    }

    public List<Receta> listarRecetaPorProducto(int idProducto) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return recetaDAO.listarPorProducto(conn, idProducto);
        }
    }

    public void asociarInsumoAReceta(int idProducto, long idInsumo, BigDecimal cantidad) throws NegocioException, SQLException {
        if (cantidad == null || cantidad.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("La cantidad necesaria del insumo debe ser mayor a 0.");
        }
        try (Connection conn = ConexionBD.getConnection()) {
            recetaDAO.asociarInsumo(conn, idProducto, idInsumo, cantidad);
        }
    }

    public void eliminarInsumoDeReceta(int idReceta) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            recetaDAO.eliminarComponente(conn, idReceta);
        }
    }

    public boolean verificarSaludBD() {
        return ConexionBD.testConexion();
    }

    public String obtenerMetricasPool() {
        return ConexionBD.getEstadoPool();
    }
}
