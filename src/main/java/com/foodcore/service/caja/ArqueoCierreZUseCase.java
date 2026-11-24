package com.foodcore.service.caja;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.Insumo;
import com.foodcore.model.InsumoCriticoTurno;
import com.foodcore.model.dto.ResultadoCierreConActa;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface ArqueoCierreZUseCase {
    List<InsumoCriticoTurno> obtenerInsumosCriticosTurno() throws SQLException;

    List<InsumoCriticoTurno> obtenerInsumosCriticosTurno(Connection conn) throws SQLException;

    List<InsumoCriticoTurno> clasificarInsumosCriticos(List<Insumo> insumos);

    ResultadoCierreConActa cerrarTurnoCaja(int idCaja, int idUsuarioCierre,
                                                       BigDecimal efectivoFisicoContado)
            throws NegocioException, SQLException;

    ResultadoCierreConActa cerrarTurnoConActaPdf(int idCaja, int idUsuarioCierre,
                                                             BigDecimal efectivoFisicoContado,
                                                             String responsableSalida,
                                                             String responsableEntrada,
                                                             File archivoDestinoResuelto)
            throws NegocioException, SQLException;
}