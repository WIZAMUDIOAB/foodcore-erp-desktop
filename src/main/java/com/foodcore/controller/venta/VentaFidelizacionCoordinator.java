package com.foodcore.controller.venta;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.Cliente;
import com.foodcore.service.FidelizacionService;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VentaFidelizacionCoordinator {
    record EstadoCumpleanosPos(boolean esCumpleanos, String mensajeAlerta, String textoBadge) { }

    Optional<Cliente> buscarClientePorCriterio(String criterio) throws SQLException;

    Cliente registrarClienteRapido(String dni, String nombreCompleto, String telefono,
            LocalDate fechaNacimiento) throws NegocioException;

    Cliente buscarORegistrarClienteRapidoDirecto(String dni, String nombreCompleto, String telefono,
            LocalDate fechaNacimiento);

    EstadoCumpleanosPos evaluarCumpleanos(Cliente cli);

    BigDecimal calcularDescuentoCumpleanos(BigDecimal totalBruto);

    FidelizacionService.BeneficioCanje calcularCanjePorBloques(int puntosCliente, int bloques,
            BigDecimal basePreCanje);

    List<String> construirOpcionesCanje(FidelizacionService.BeneficioCanje check);
}
