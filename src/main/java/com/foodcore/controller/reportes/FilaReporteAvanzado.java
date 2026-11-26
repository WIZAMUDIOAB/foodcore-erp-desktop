package com.foodcore.controller.reportes;

/**
 * Fila tabular inmutable de cinco columnas para los reportes avanzados de BI.
 */
public class FilaReporteAvanzado {
    public final String c1;
    public final String c2;
    public final String c3;
    public final String c4;
    public final String c5;

    public FilaReporteAvanzado(String c1, String c2, String c3, String c4, String c5) {
        this.c1 = c1;
        this.c2 = c2;
        this.c3 = c3;
        this.c4 = c4;
        this.c5 = c5;
    }
}
