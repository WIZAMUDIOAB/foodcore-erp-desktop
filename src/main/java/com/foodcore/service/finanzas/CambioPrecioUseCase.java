package com.foodcore.service.finanzas;

import java.sql.SQLException;

public interface CambioPrecioUseCase {
    boolean registrarCambioPrecioConMotivo(String tipoEntidad, int idEntidad, String nombreEntidad, double precioAnterior, double precioNuevo, int idUsuario, String nombreUsuario, String motivo) throws SQLException;
}
