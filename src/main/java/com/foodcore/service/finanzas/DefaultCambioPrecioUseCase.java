package com.foodcore.service.finanzas;

import com.foodcore.dao.HistorialPrecioDAO;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.HistorialPrecio;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;

public final class DefaultCambioPrecioUseCase implements CambioPrecioUseCase {
    private static final String SQL_UPDATE_PRECIO_PRODUCTO = "UPDATE producto SET precio_venta = ? WHERE id_producto = ?";
    private static final String SQL_UPDATE_COSTO_INSUMO = "UPDATE insumo SET costo_unitario = ?, costo_unitario_actual = ? WHERE id_insumo = ?";
    private final TransactionManager tx;
    private final HistorialPrecioDAO historialPrecioDAO;
    public DefaultCambioPrecioUseCase(TransactionManager tx, HistorialPrecioDAO historialPrecioDAO) { this.tx = tx == null ? new JdbcTransactionManager() : tx; this.historialPrecioDAO = historialPrecioDAO; }
    @Override public boolean registrarCambioPrecioConMotivo(String tipoEntidad, int idEntidad, String nombreEntidad, BigDecimal precioAnterior, BigDecimal precioNuevo, int idUsuario, String nombreUsuario, String motivo) throws SQLException {
        if (tipoEntidad == null || tipoEntidad.isBlank()) throw new IllegalArgumentException("El tipo de entidad es obligatorio"); String tipoNorm = tipoEntidad.trim().toUpperCase(Locale.ROOT); if (!"PRODUCTO".equals(tipoNorm) && !"INSUMO".equals(tipoNorm)) throw new IllegalArgumentException("Tipo de entidad no soportado: " + tipoEntidad);
        if (precioAnterior == null || precioAnterior.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("El precio anterior debe ser un importe no negativo");
        if (precioNuevo == null || precioNuevo.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("El precio nuevo debe ser un importe mayor a 0");
        if (motivo == null || motivo.trim().length() < 5) throw new IllegalArgumentException("El motivo del cambio de precio es obligatorio (mínimo 5 caracteres)");
        BigDecimal nuevo = precioNuevo.setScale(4, RoundingMode.HALF_UP), anterior = precioAnterior.setScale(4, RoundingMode.HALF_UP);
        try { return tx.executeChecked(conn -> {
            HistorialPrecio hist = new HistorialPrecio(); hist.setTipoEntidad(tipoNorm); hist.setIdEntidad(idEntidad); hist.setNombreEntidad(nombreEntidad); hist.setPrecioAnterior(anterior); hist.setPrecioNuevo(nuevo); hist.setIdUsuario(idUsuario); hist.setNombreUsuario(nombreUsuario); hist.setMotivo(motivo.trim()); historialPrecioDAO.insertar(conn, hist);
            if ("PRODUCTO".equalsIgnoreCase(tipoNorm)) try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_PRECIO_PRODUCTO)) { ps.setBigDecimal(1, nuevo.setScale(2, RoundingMode.HALF_UP)); ps.setInt(2, idEntidad); ps.executeUpdate(); }
            else if ("INSUMO".equalsIgnoreCase(tipoNorm)) try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_COSTO_INSUMO)) { ps.setBigDecimal(1, nuevo); ps.setBigDecimal(2, nuevo); ps.setInt(3, idEntidad); ps.executeUpdate(); }
            return true;
        }); } catch (NegocioException ex) { throw new IllegalStateException(ex.getMessage(), ex); }
    }
}
