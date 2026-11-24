package com.foodcore.service.venta;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.Usuario;
import com.foodcore.model.dto.ResultadoDevolucionParcial;
import com.foodcore.security.SesionUsuario;

import java.sql.SQLException;
import java.util.List;

public interface AnularDevolverVentaUseCase {
    void anularVenta(int idVenta, boolean platoYaPreparado, SesionUsuario sesion, String motivo)
            throws NegocioException, SQLException;

    Usuario anularVentaConSupervisor(int idVenta, boolean platoYaPreparado, SesionUsuario sesionCajero,
                                     String pinSupervisor, String motivo)
            throws NegocioException, SQLException;

    ResultadoDevolucionParcial devolverItemsParcial(int idVenta,
                                                                 List<Integer> idsDetalleADevolver,
                                                                 SesionUsuario sesion, String motivo)
            throws NegocioException, SQLException;
}