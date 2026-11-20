package com.foodcore.model;

import java.time.LocalDate;

/**
 * Alerta de vencimiento de contrato laboral, carné de sanidad o documento de personal
 * dentro de los próximos 30 días (SPEC-FOODCORE v4.4.0 §4).
 *
 * @param colaborador     Nombre del colaborador.
 * @param cargo           Cargo o rol del colaborador.
 * @param documento       Tipo de documento o contrato que vence.
 * @param fechaVencimiento Fecha de vencimiento en zona America/Lima.
 * @param diasRestantes   Días restantes hasta el vencimiento (0 a 30).
 */
public record AlertaVencimiento(
        String colaborador,
        String cargo,
        String documento,
        LocalDate fechaVencimiento,
        long diasRestantes) {
}
