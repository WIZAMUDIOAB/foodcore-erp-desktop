package com.foodcore.service;

import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.model.Insumo;
import com.foodcore.model.KardexMovimiento;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.service.AuthService;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.UnidadMedidaFormatter;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class InventarioGestionService {
    private final InsumoDAO insumoDAO = new InsumoDAOImpl();
    private final TransactionManager tx = new JdbcTransactionManager();

    public List<Insumo> listarTodosInsumos() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return insumoDAO.listarTodos(conn);
        }
    }

    public Insumo crearNuevoInsumoTransaccional(String codigo, String nombre, String unidadMedida,
            BigDecimal stockInicial, BigDecimal stockMinimo, BigDecimal costoUnitario) throws Exception {
        String umCanon = UnidadMedidaFormatter.normalizarCodigo(unidadMedida);
        long idUsuario = AuthService.getUsuarioSesionActual() != null
                ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1L;
        return tx.executeChecked(conn -> {
            Insumo nuevo = new Insumo(0L,
                    codigo != null && !codigo.isBlank() ? codigo.trim()
                            : "INS-" + (System.currentTimeMillis() % 100000),
                    nombre.trim(), umCanon, BigDecimal.ZERO,
                    stockMinimo != null ? stockMinimo : BigDecimal.ZERO,
                    costoUnitario != null ? costoUnitario : BigDecimal.ZERO);
            long id = insumoDAO.crear(conn, nuevo);
            if (stockInicial != null && stockInicial.compareTo(BigDecimal.ZERO) > 0) {
                insumoDAO.registrarCompraInsumo(conn, id, stockInicial, costoUnitario, idUsuario,
                        "Inventario Inicial de Insumo");
            }
            return insumoDAO.obtenerPorId(conn, id);
        });
    }

    public KardexMovimiento registrarCompraTransaccional(long idInsumo, BigDecimal cantidadComprada,
            BigDecimal costoUnitarioCompra, String observacion) throws Exception {
        long idUsuario = AuthService.getUsuarioSesionActual() != null
                ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1L;
        return tx.executeChecked(conn -> insumoDAO.registrarCompraInsumo(conn, idInsumo, cantidadComprada,
                costoUnitarioCompra, idUsuario, observacion));
    }

    public void eliminarInsumo(long idInsumo) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            insumoDAO.eliminar(conn, idInsumo);
        }
    }
}
