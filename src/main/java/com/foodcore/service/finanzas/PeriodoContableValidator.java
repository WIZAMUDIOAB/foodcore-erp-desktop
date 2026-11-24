package com.foodcore.service.finanzas;

import com.foodcore.exception.PeriodoCerradoException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Puerto funcional para validar que una fecha pertenezca a un período contable abierto.
 */
@FunctionalInterface
public interface PeriodoContableValidator {

    void validarPeriodoAbierto(Connection conn, LocalDate fecha) throws PeriodoCerradoException, SQLException;
}
