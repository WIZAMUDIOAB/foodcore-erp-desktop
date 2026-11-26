package com.foodcore.controller.venta;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.Cliente;
import com.foodcore.service.FidelizacionService;
import com.foodcore.service.VentaPosConsultaService;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class DefaultVentaFidelizacionCoordinator implements VentaFidelizacionCoordinator {
    private final Function<String, Optional<Cliente>> buscarCliente;
    private final RegistrarClienteFn registrarCliente;
    private final BiFunction<Cliente, Void, Boolean> cumpleanos;
    private final Function<BigDecimal, BigDecimal> descuentoCumpleanos;
    private final CalcularCanjeFn calcularCanje;

    @FunctionalInterface
    public interface RegistrarClienteFn {
        Cliente registrar(String dni, String nombre, String telefono, LocalDate nacimiento);
    }

    @FunctionalInterface
    public interface CalcularCanjeFn {
        FidelizacionService.BeneficioCanje calcular(int puntos, int bloques, BigDecimal base);
    }

    public DefaultVentaFidelizacionCoordinator(VentaPosConsultaService posConsultaService,
            FidelizacionService fidelizacionService) {
        this(criterio -> {
            try {
                return posConsultaService.buscarClientePorDniOTelefono(criterio);
            } catch (SQLException ex) {
                throw new IllegalStateException(ex);
            }
        }, posConsultaService::buscarORegistrarClienteMinoristaRapido,
                cli -> fidelizacionService.esCumpleanosEnMesActual(cli),
                fidelizacionService::calcularDescuentoCumpleanosPos,
                fidelizacionService::calcularCanjePorBloques);
    }

    public DefaultVentaFidelizacionCoordinator(Function<String, Optional<Cliente>> buscarCliente,
            RegistrarClienteFn registrarCliente, Function<Cliente, Boolean> cumpleanos,
            Function<BigDecimal, BigDecimal> descuentoCumpleanos, CalcularCanjeFn calcularCanje) {
        this.buscarCliente = Objects.requireNonNull(buscarCliente);
        this.registrarCliente = Objects.requireNonNull(registrarCliente);
        Objects.requireNonNull(cumpleanos);
        this.cumpleanos = (cli, unused) -> cumpleanos.apply(cli);
        this.descuentoCumpleanos = Objects.requireNonNull(descuentoCumpleanos);
        this.calcularCanje = Objects.requireNonNull(calcularCanje);
    }

    @Override
    public Optional<Cliente> buscarClientePorCriterio(String criterio) throws SQLException {
        try {
            return buscarCliente.apply(criterio);
        } catch (IllegalStateException ex) {
            if (ex.getCause() instanceof SQLException sqlException) {
                throw sqlException;
            }
            throw ex;
        }
    }

    @Override
    public Cliente registrarClienteRapido(String dni, String nombreCompleto, String telefono,
            LocalDate fechaNacimiento) throws NegocioException {
        if (dni == null || !dni.trim().matches("\\d{8}")) {
            throw new NegocioException("El DNI del cliente minorista debe tener exactamente 8 dígitos.");
        }
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            throw new NegocioException("El nombre completo del cliente minorista es obligatorio.");
        }
        Cliente cliente = registrarCliente.registrar(dni.trim(), nombreCompleto.trim(), telefono, fechaNacimiento);
        if (cliente == null) {
            throw new NegocioException("No se pudo registrar el cliente minorista en la base de datos.");
        }
        return cliente;
    }

    @Override
    public Cliente buscarORegistrarClienteRapidoDirecto(String dni, String nombreCompleto, String telefono,
            LocalDate fechaNacimiento) {
        try {
            return registrarClienteRapido(dni, nombreCompleto, telefono, fechaNacimiento);
        } catch (NegocioException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    @Override
    public EstadoCumpleanosPos evaluarCumpleanos(Cliente cli) {
        boolean cumple = cli != null && Boolean.TRUE.equals(cumpleanos.apply(cli, null));
        if (!cumple) {
            return new EstadoCumpleanosPos(false, null, null);
        }
        return new EstadoCumpleanosPos(true,
                "¡Hoy es el cumpleaños de " + cli.getRazonSocial() + "! ¿Deseas aplicar un 10% de descuento?",
                "Cumpleaños de " + cli.getRazonSocial() + " (10%)");
    }

    @Override
    public BigDecimal calcularDescuentoCumpleanos(BigDecimal totalBruto) {
        return descuentoCumpleanos.apply(totalBruto);
    }

    @Override
    public FidelizacionService.BeneficioCanje calcularCanjePorBloques(int puntosCliente, int bloques,
            BigDecimal basePreCanje) {
        return calcularCanje.calcular(puntosCliente, bloques, basePreCanje);
    }

    @Override
    public List<String> construirOpcionesCanje(FidelizacionService.BeneficioCanje check) {
        List<String> opciones = new ArrayList<>();
        for (int bloque = 1; bloque <= check.bloquesDisponibles(); bloque++) {
            int puntos = bloque * check.puntosPorBloque();
            BigDecimal descuento = check.descuentoPorBloqueSoles().multiply(BigDecimal.valueOf(bloque))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            opciones.add(bloque + " bloque(s): " + puntos + " pts -> -S/ " + descuento);
        }
        return opciones;
    }
}
