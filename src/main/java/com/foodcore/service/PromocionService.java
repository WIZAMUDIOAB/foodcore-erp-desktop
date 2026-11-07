package com.foodcore.service;

import com.foodcore.dao.PromocionDAO;
import com.foodcore.dao.impl.PromocionDAOImpl;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Promocion;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PromocionService {

    private static final Logger LOGGER = Logger.getLogger(PromocionService.class.getName());
    private final PromocionDAO promocionDAO;

    public PromocionService() {
        this(new PromocionDAOImpl());
    }

    public PromocionService(PromocionDAO promocionDAO) {
        this.promocionDAO = promocionDAO;
    }

    public List<Promocion> listarTodas() {
        try (Connection conn = ConexionBD.getConnection()) {
            return promocionDAO.listarTodas(conn);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error al listar promociones: {0}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Promocion> listarActivas() {
        try (Connection conn = ConexionBD.getConnection()) {
            return promocionDAO.listarActivas(conn);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error al listar promociones activas: {0}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public int crearPromocion(Promocion promo) {
        try (Connection conn = ConexionBD.getConnection()) {
            return promocionDAO.crear(conn, promo);
        } catch (SQLException e) {
            throw new RuntimeException("Error al crear promocion: " + e.getMessage(), e);
        }
    }

    public boolean actualizarPromocion(Promocion promo) {
        try (Connection conn = ConexionBD.getConnection()) {
            return promocionDAO.actualizar(conn, promo);
        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar promocion: " + e.getMessage(), e);
        }
    }

    public boolean cambiarEstado(int idPromocion, boolean activo) {
        try (Connection conn = ConexionBD.getConnection()) {
            return promocionDAO.cambiarEstado(conn, idPromocion, activo);
        } catch (SQLException e) {
            throw new RuntimeException("Error al cambiar estado de promocion: " + e.getMessage(), e);
        }
    }

    public Optional<Promocion> buscarPorCodigo(String codigo) {
        try (Connection conn = ConexionBD.getConnection()) {
            return promocionDAO.buscarPorCodigo(conn, codigo);
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    /**
     * Evalúa automáticamente las promociones activas para los ítems y subtotal dados.
     */
    public ResultadoEvaluacion evaluarPromociones(List<DetalleVenta> items, BigDecimal subtotalTotal) {
        return evaluarPromociones(items, subtotalTotal, LocalDate.now().getDayOfWeek(), LocalTime.now());
    }

    public ResultadoEvaluacion evaluarPromociones(List<DetalleVenta> items, BigDecimal subtotalTotal,
                                                 DayOfWeek diaActual, LocalTime horaActual) {
        List<Promocion> activas = listarActivas();
        BigDecimal descuentoTotal = BigDecimal.ZERO;
        List<String> aplicadas = new ArrayList<>();

        if (items == null || items.isEmpty() || subtotalTotal == null || subtotalTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return new ResultadoEvaluacion(BigDecimal.ZERO, subtotalTotal != null ? subtotalTotal : BigDecimal.ZERO, aplicadas);
        }

        for (Promocion promo : activas) {
            if (!aplicaDiaYHora(promo, diaActual, horaActual)) {
                continue;
            }

            BigDecimal desc = BigDecimal.ZERO;

            if ("DOS_POR_UNO".equalsIgnoreCase(promo.getTipoPromocion())) {
                // 2x1: por cada 2 unidades del producto aplicable, se descuenta 1 unidad
                for (DetalleVenta d : items) {
                    if (promo.getIdProductoAplica() != null && promo.getIdProductoAplica() == d.getIdProducto()) {
                        int pares = d.getCantidad() / 2;
                        if (pares > 0) {
                            BigDecimal ahorroItem = d.getPrecioUnitario().multiply(BigDecimal.valueOf(pares));
                            desc = desc.add(ahorroItem);
                        }
                    }
                }
            } else if ("TRES_POR_DOS".equalsIgnoreCase(promo.getTipoPromocion())) {
                // 3x2: por cada 3 unidades, se descuenta 1 unidad
                for (DetalleVenta d : items) {
                    if (promo.getIdProductoAplica() != null && promo.getIdProductoAplica() == d.getIdProducto()) {
                        int trios = d.getCantidad() / 3;
                        if (trios > 0) {
                            BigDecimal ahorroItem = d.getPrecioUnitario().multiply(BigDecimal.valueOf(trios));
                            desc = desc.add(ahorroItem);
                        }
                    }
                }
            } else if ("PORCENTAJE".equalsIgnoreCase(promo.getTipoPromocion())) {
                BigDecimal porcentaje = promo.getValorDescuento().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
                if ("PRODUCTO".equalsIgnoreCase(promo.getAplicaA()) && promo.getIdProductoAplica() != null) {
                    for (DetalleVenta d : items) {
                        if (promo.getIdProductoAplica() == d.getIdProducto()) {
                            desc = desc.add(d.getSubtotal().multiply(porcentaje));
                        }
                    }
                } else if ("TOTAL_ORDEN".equalsIgnoreCase(promo.getAplicaA())) {
                    if (promo.getMontoMinimo() == null || subtotalTotal.compareTo(promo.getMontoMinimo()) >= 0) {
                        desc = subtotalTotal.multiply(porcentaje);
                    }
                } else if ("CATEGORIA".equalsIgnoreCase(promo.getAplicaA())) {
                    java.util.Map<Integer, Integer> mapCat = obtenerMapaProductoCategoria();
                    for (DetalleVenta d : items) {
                        Integer idCatProd = mapCat.get(d.getIdProducto());
                        if (idCatProd != null && promo.getIdCategoriaAplica() != null && idCatProd.equals(promo.getIdCategoriaAplica())) {
                            desc = desc.add(d.getSubtotal().multiply(porcentaje));
                        }
                    }
                }
            } else if ("MONTO_FIJO".equalsIgnoreCase(promo.getTipoPromocion())) {
                if (promo.getMontoMinimo() == null || subtotalTotal.compareTo(promo.getMontoMinimo()) >= 0) {
                    desc = promo.getValorDescuento();
                }
            }

            if (desc.compareTo(BigDecimal.ZERO) > 0) {
                desc = desc.setScale(2, RoundingMode.HALF_UP);
                descuentoTotal = descuentoTotal.add(desc);
                aplicadas.add(promo.getNombre() + " (-S/ " + desc + ")");
            }
        }

        // El descuento no puede exceder el subtotal
        if (descuentoTotal.compareTo(subtotalTotal) > 0) {
            descuentoTotal = subtotalTotal;
        }

        BigDecimal totalFinal = subtotalTotal.subtract(descuentoTotal).setScale(2, RoundingMode.HALF_UP);
        return new ResultadoEvaluacion(descuentoTotal.setScale(2, RoundingMode.HALF_UP), totalFinal, aplicadas);
    }

    private boolean aplicaDiaYHora(Promocion p, DayOfWeek dia, LocalTime hora) {
        if (p.getDiasSemana() != null && !p.getDiasSemana().isBlank()) {
            String diaAbrev = traducirDia(dia);
            if (!p.getDiasSemana().toUpperCase().contains(diaAbrev)) {
                return false;
            }
        }
        if (p.getHoraInicio() != null && hora.isBefore(p.getHoraInicio())) {
            return false;
        }
        if (p.getHoraFin() != null && hora.isAfter(p.getHoraFin())) {
            return false;
        }
        return true;
    }

    private String traducirDia(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "LUN";
            case TUESDAY -> "MAR";
            case WEDNESDAY -> "MIE";
            case THURSDAY -> "JUE";
            case FRIDAY -> "VIE";
            case SATURDAY -> "SAB";
            case SUNDAY -> "DOM";
        };
    }

    public static class ResultadoEvaluacion {
        private final BigDecimal descuentoTotal;
        private final BigDecimal totalFinal;
        private final List<String> promocionesAplicadas;

        public ResultadoEvaluacion(BigDecimal descuentoTotal, BigDecimal totalFinal, List<String> promocionesAplicadas) {
            this.descuentoTotal = descuentoTotal;
            this.totalFinal = totalFinal;
            this.promocionesAplicadas = promocionesAplicadas;
        }

        public BigDecimal getDescuentoTotal() {
            return descuentoTotal;
        }

        public BigDecimal getTotalFinal() {
            return totalFinal;
        }

        public List<String> getPromocionesAplicadas() {
            return promocionesAplicadas;
        }

        public boolean tieneDescuento() {
            return descuentoTotal.compareTo(BigDecimal.ZERO) > 0;
        }

        public String getResumenDetallado() {
            if (!tieneDescuento()) {
                return "Sin promociones aplicadas";
            }
            return String.join(" | ", promocionesAplicadas);
        }
    }

    private java.util.Map<Integer, Integer> obtenerMapaProductoCategoria() {
        java.util.Map<Integer, Integer> map = new java.util.HashMap<>();
        // Valores base por defecto
        map.put(1, 1); // Hamburguesa Clásica -> Cat 1
        map.put(2, 2); // Alitas BBQ -> Cat 2
        map.put(3, 3); // Gaseosa -> Cat 3
        try (Connection conn = ConexionBD.getConnection()) {
            com.foodcore.dao.ProductoDAO dao = new com.foodcore.dao.ProductoDAOImpl();
            for (com.foodcore.model.Producto p : dao.listarTodos(conn)) {
                map.put(p.getIdProducto(), p.getIdCategoria());
            }
        } catch (Exception ignored) {}
        return map;
    }

}
