package com.foodcore.service;

import com.foodcore.model.MovimientoPago;
import com.foodcore.model.PlanillaConsolidada;
import com.foodcore.model.Usuario;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Servicio administrativo para consolidado de pagos y movimientos mensuales
 * ({@code Sueldo Base + Bonos + Comisiones - Adelantos - Descuentos = Neto a Pagar}).
 */
public interface PlanillaService {

    List<Usuario> listarColaboradoresActivos();

    MovimientoPago registrarMovimiento(int idUsuario,
                                       String tipoMovimiento,
                                       String concepto,
                                       BigDecimal monto,
                                       LocalDate fecha,
                                       Integer registradoPor,
                                       String observaciones);

    void eliminarMovimiento(long idMovimiento);

    List<MovimientoPago> listarMovimientosPorPeriodo(String periodoMes);

    List<PlanillaConsolidada> calcularPlanillaConsolidada(String periodoMes);

    File exportarPlanillaExcel(List<PlanillaConsolidada> filas, String periodoMes, File destino);

    File exportarPlanillaPdf(List<PlanillaConsolidada> filas, String periodoMes, File destino);
}
