package com.foodcore.model.dto;

import com.foodcore.model.Caja;
import com.foodcore.model.InsumoCriticoTurno;
import java.io.File;
import java.math.BigDecimal;
import java.util.List;

/** Resultado integral del Cierre Z con resumen de stock crítico y PDF de relevo (v5.2.0). */
public record ResultadoCierreConActa(
        Caja cajaCerrada,
        BigDecimal montoEsperado,
        BigDecimal efectivoContado,
        BigDecimal diferencia,
        List<InsumoCriticoTurno> insumosCriticos,
        File archivoActaPdf
) {
    public ResultadoCierreConActa {
        cajaCerrada = copiarCaja(cajaCerrada);
        insumosCriticos = insumosCriticos != null ? List.copyOf(insumosCriticos) : List.of();
    }

    @Override
    public Caja cajaCerrada() {
        return copiarCaja(cajaCerrada);
    }

    private static Caja copiarCaja(Caja origen) {
        if (origen == null) {
            return null;
        }
        Caja copia = new Caja();
        copia.setIdCaja(origen.getIdCaja());
        copia.setIdUsuarioApertura(origen.getIdUsuarioApertura());
        copia.setIdUsuarioCierre(origen.getIdUsuarioCierre());
        copia.setNombreCaja(origen.getNombreCaja());
        copia.setMontoApertura(origen.getMontoApertura());
        copia.setMontoCierre(origen.getMontoCierre());
        copia.setFechaApertura(origen.getFechaApertura());
        copia.setFechaCierre(origen.getFechaCierre());
        copia.setEstado(origen.getEstado());
        copia.setDiferencia(origen.getDiferencia());
        return copia;
    }
}
