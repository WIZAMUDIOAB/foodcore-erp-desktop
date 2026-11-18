package com.foodcore.exception;

import java.time.YearMonth;

/**
 * Excepción de dominio lanzada al intentar registrar o modificar operaciones financieras
 * (ventas, devoluciones o egresos de caja) dentro de un período contable mensual cerrado.
 */
public class PeriodoCerradoException extends NegocioException {

    private final int anio;
    private final int mes;

    public PeriodoCerradoException(int anio, int mes) {
        this(anio, mes,
             String.format("El período contable %04d-%02d se encuentra cerrado. No se permiten modificaciones.",
                 anio, mes));
    }

    public PeriodoCerradoException(int anio, int mes, String mensaje) {
        super(CodigoError.PERIODO_CERRADO, mensaje);
        this.anio = anio;
        this.mes = mes;
    }

    public PeriodoCerradoException(YearMonth ym) {
        this(ym != null ? ym.getYear() : 0, ym != null ? ym.getMonthValue() : 0);
    }

    public int getAnio() {
        return anio;
    }

    public int getMes() {
        return mes;
    }
}
