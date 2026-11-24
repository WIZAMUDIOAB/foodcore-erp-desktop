package com.foodcore.service.finanzas;

import java.math.BigDecimal;
import java.sql.SQLException;

public interface CambioPrecioUseCase {
    boolean registrarCambioPrecioConMotivo(String tipoEntidad, int idEntidad, String nombreEntidad, BigDecimal precioAnterior, BigDecimal precioNuevo, int idUsuario, String nombreUsuario, String motivo) throws SQLException;
}
