package com.foodcore.service;

import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.FidelizacionDAO;
import com.foodcore.dao.impl.ClienteDAOImpl;
import com.foodcore.dao.impl.FidelizacionDAOImpl;
import com.foodcore.model.Cliente;
import com.foodcore.model.CuponCumpleanos;
import com.foodcore.model.FidelizacionMovimiento;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FidelizacionService {

    private static final Logger LOGGER = Logger.getLogger(FidelizacionService.class.getName());
    public static final int PUNTOS_POR_SOL = 1; // S/ 1 = 1 punto
    public static final int PUNTOS_POR_SOL_DESCUENTO = 10; // 10 puntos = S/ 1 de descuento

    private final FidelizacionDAO fidelizacionDAO;
    private final ClienteDAO clienteDAO;

    public FidelizacionService() {
        this(new FidelizacionDAOImpl(), new ClienteDAOImpl());
    }

    public FidelizacionService(FidelizacionDAO fidelizacionDAO, ClienteDAO clienteDAO) {
        this.fidelizacionDAO = fidelizacionDAO;
        this.clienteDAO = clienteDAO;
    }

    /**
     * Acumula puntos para un cliente tras una venta exitosa (S/ 1 = 1 punto).
     */
    public int acumularPuntosPorVenta(int idCliente, Integer idVenta, BigDecimal totalVenta) {
        if (idCliente <= 0 || totalVenta == null || totalVenta.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        int puntosGanados = totalVenta.intValue() * PUNTOS_POR_SOL;
        if (puntosGanados <= 0) {
            return 0;
        }

        try (Connection conn = ConexionBD.getConnection()) {
            int puntosActuales = fidelizacionDAO.obtenerPuntosActuales(conn, idCliente);
            int nuevosPuntos = puntosActuales + puntosGanados;

            fidelizacionDAO.actualizarPuntosCliente(conn, idCliente, nuevosPuntos);

            FidelizacionMovimiento mov = new FidelizacionMovimiento(
                    0, idCliente, idVenta, "ACUMULACION", puntosGanados,
                    "Acumulacion de puntos por consumo total de S/ " + totalVenta,
                    LocalDateTime.now()
            );
            fidelizacionDAO.registrarMovimiento(conn, mov);

            return puntosGanados;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error al acumular puntos para cliente {0}: {1}", new Object[]{idCliente, e.getMessage()});
            return 0;
        }
    }

    /**
     * Canjea puntos por un descuento monetario en el POS.
     * Cada 10 puntos = S/ 1.00 de descuento.
     */
    public BigDecimal canjearPuntos(int idCliente, int puntosACanjear, BigDecimal totalVenta) {
        if (idCliente <= 0 || puntosACanjear <= 0 || totalVenta == null) {
            return BigDecimal.ZERO;
        }

        try (Connection conn = ConexionBD.getConnection()) {
            int puntosDisponibles = fidelizacionDAO.obtenerPuntosActuales(conn, idCliente);
            if (puntosACanjear > puntosDisponibles) {
                puntosACanjear = puntosDisponibles;
            }

            BigDecimal descuentoCalculado = BigDecimal.valueOf(puntosACanjear)
                    .divide(BigDecimal.valueOf(PUNTOS_POR_SOL_DESCUENTO), 2, RoundingMode.HALF_UP);

            if (descuentoCalculado.compareTo(totalVenta) > 0) {
                descuentoCalculado = totalVenta;
                puntosACanjear = totalVenta.multiply(BigDecimal.valueOf(PUNTOS_POR_SOL_DESCUENTO)).intValue();
            }

            int nuevosPuntos = puntosDisponibles - puntosACanjear;
            fidelizacionDAO.actualizarPuntosCliente(conn, idCliente, nuevosPuntos);

            FidelizacionMovimiento mov = new FidelizacionMovimiento(
                    0, idCliente, null, "CANJE", -puntosACanjear,
                    "Canje de " + puntosACanjear + " puntos por descuento de S/ " + descuentoCalculado,
                    LocalDateTime.now()
            );
            fidelizacionDAO.registrarMovimiento(conn, mov);

            return descuentoCalculado;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error al canjear puntos para cliente {0}: {1}", new Object[]{idCliente, e.getMessage()});
            return BigDecimal.ZERO;
        }
    }

    /**
     * Verifica si el cliente cumple años en el mes actual y genera automáticamente
     * un cupón del 15% OFF si aún no tiene uno activo para este mes.
     */
    public Optional<CuponCumpleanos> verificarYGenerarCuponCumpleanos(int idCliente) {
        if (idCliente <= 0) {
            return Optional.empty();
        }

        try (Connection conn = ConexionBD.getConnection()) {
            Optional<Cliente> optCliente = clienteDAO.buscarPorId(conn, idCliente);
            if (optCliente.isEmpty()) {
                return Optional.empty();
            }

            Cliente c = optCliente.get();
            LocalDate hoy = LocalDate.now();
            int mesActual = hoy.getMonthValue();

            // Verificar si cumple años en el mes actual
            if (c.getFechaNacimiento() == null || c.getFechaNacimiento().getMonthValue() != mesActual) {
                return Optional.empty();
            }

            // Buscar si ya tiene cupón generado para el mes
            Optional<CuponCumpleanos> existente = fidelizacionDAO.buscarCuponCumpleanosActivo(conn, idCliente, mesActual);
            if (existente.isPresent()) {
                return existente;
            }

            // Generar nuevo cupón 15% off
            String codigo = "BDAY-CLI" + idCliente + "-M" + mesActual + "-" + hoy.getYear();
            CuponCumpleanos nuevo = new CuponCumpleanos(
                    0, idCliente, codigo, new BigDecimal("15.00"), mesActual, false, LocalDateTime.now()
            );
            fidelizacionDAO.generarCuponCumpleanos(conn, nuevo);
            return Optional.of(nuevo);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error al verificar cupon de cumpleanos para cliente {0}: {1}", new Object[]{idCliente, e.getMessage()});
            return Optional.empty();
        }
    }

    public boolean marcarCuponUsado(String codigoCupon) {
        try (Connection conn = ConexionBD.getConnection()) {
            return fidelizacionDAO.marcarCuponUsado(conn, codigoCupon);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error al marcar cupon como usado: {0}", e.getMessage());
            return false;
        }
    }

    public Optional<CuponCumpleanos> buscarCuponPorCodigo(String codigo) {
        try (Connection conn = ConexionBD.getConnection()) {
            return fidelizacionDAO.buscarCuponPorCodigo(conn, codigo);
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public List<FidelizacionMovimiento> listarHistorialCliente(int idCliente) {
        try (Connection conn = ConexionBD.getConnection()) {
            return fidelizacionDAO.listarPorCliente(conn, idCliente);
        } catch (SQLException e) {
            return List.of();
        }
    }

    public int obtenerPuntosCliente(int idCliente) {
        try (Connection conn = ConexionBD.getConnection()) {
            return fidelizacionDAO.obtenerPuntosActuales(conn, idCliente);
        } catch (SQLException e) {
            return 0;
        }
    }
}
