package com.foodcore.service.impl;

import com.foodcore.dao.PedidoDAO;
import com.foodcore.exception.ConflictoEstadoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.PedidoService;
import com.foodcore.tx.TransactionManager;

import java.sql.SQLException;
import java.util.List;

/**
 * Implementación canónica de {@link PedidoService} en paquete {@code com.foodcore.service.impl}
 * según SPEC-FOODCORE-v1.0 (§15.4, R-01, R-02.5).
 */
public class PedidoServiceImpl extends PedidoService {

    public PedidoServiceImpl() {
        super();
    }

    public PedidoServiceImpl(TransactionManager tx, PedidoDAO pedidoDAO, AutorizacionService autorizacionService) {
        super(tx, pedidoDAO, autorizacionService);
    }

    @Override
    public Pedido crearPedido(Pedido pedido, List<DetallePedido> detalles) throws NegocioException, SQLException {
        return super.crearPedido(pedido, detalles);
    }

    @Override
    public Pedido registrarPedidoYEnviarComanda(Pedido pedido, List<DetallePedido> detalles) throws NegocioException, SQLException {
        return super.registrarPedidoYEnviarComanda(pedido, detalles);
    }

    @Override
    public void actualizarEstadoMesaOptimista(long idMesa, String nuevoEstado, int versionEsperada)
            throws ConflictoEstadoException, SQLException {
        super.actualizarEstadoMesaOptimista(idMesa, nuevoEstado, versionEsperada);
    }

    @Override
    public Pedido adicionarItemsPostEntrega(int idPedido, List<DetallePedido> nuevosItems, SesionUsuario sesion)
            throws NegocioException, SQLException {
        return super.adicionarItemsPostEntrega(idPedido, nuevosItems, sesion);
    }
}
