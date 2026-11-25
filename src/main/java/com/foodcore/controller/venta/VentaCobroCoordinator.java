package com.foodcore.controller.venta;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Pedido;
import com.foodcore.model.Usuario;
import com.foodcore.model.Venta;
import com.foodcore.model.dto.PagoSplit;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.SesionUsuario;
import com.foodcore.service.PedidoService;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

public interface VentaCobroCoordinator {
    String MODALIDAD_SPLIT_2 = "Dividir en 2 partes iguales (50% Efectivo / 50% Tarjeta)";
    String MODALIDAD_SPLIT_4 = "Dividir en 4 partes iguales (25% c/u)";
    String MODALIDAD_SPLIT_ITEMS = "Dividir por ítems (Subcuenta A y Subcuenta B)";

    record ResultadoEnvioComanda(Pedido pedido, boolean impresionOk) { }

    record ValidacionAnulacionSupervisor(boolean valido, int idVenta, String motivoFinal,
            String tituloError, String mensajeError) { }

    record ResultadoItemPedido(BigDecimal totalPedido, List<DetallePedido> detallesCompletos) { }

    List<PagoSplit> construirPagosPorModalidad(String modalidad, BigDecimal totalNeto,
            List<DetalleVenta> items);

    Venta construirVentaParaCobro(Integer pedidoActivoId, int idUsuario, Integer idCliente,
            BigDecimal basePreCanje, BigDecimal totalNeto, String metodoPago, BigDecimal descuentoActivo,
            BigDecimal descuentoPuntosActivo, int puntosARedimir) throws NegocioException;

    Venta registrarVentaDividida(Integer pedidoActivoId, int idUsuario, Integer idCliente,
            BigDecimal basePreCanje, BigDecimal totalNeto, BigDecimal descuentoActivo,
            BigDecimal descuentoPuntosActivo, int puntosARedimir, List<DetalleVenta> items,
            List<PagoSplit> pagos) throws Exception;

    void procesarCobroDirecto(Integer pedidoActivoId, Integer idCliente, BigDecimal basePreCanje,
            BigDecimal totalNeto, String metodo, BigDecimal descuentoActivo, BigDecimal descuentoPuntosActivo,
            int puntosARedimir, List<DetalleVenta> items) throws Exception;

    ResultadoEnvioComanda enviarComandaACocina(PedidoService pedidoServiceActual, Pedido pedido,
            List<DetallePedido> detallesCocina) throws Exception;

    ResultadoItemPedido agregarItemAPedidoExistente(PedidoService pedidoServiceActual, int idPedido,
            DetallePedido nuevoItem) throws NegocioException, SQLException;

    void imprimirPreCuenta(Pedido pedido, List<DetallePedido> detalles) throws Exception;

    ValidacionAnulacionSupervisor validarAnulacionSupervisor(String idRaw, String motivo);

    Usuario anularVentaConSupervisor(int idVenta, boolean yaPreparado, SesionUsuario sesion,
            String pinSupervisor, String motivo) throws Exception;

    void anularVentaDirecta(int idVenta, SesionUsuario sesion, String motivo) throws Exception;

    boolean tienePermiso(SesionUsuario sesion, Permiso permiso);

    SesionUsuario crearSesionActualPOS(Usuario usr);
}
