package com.foodcore.util;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Carga y expone los parámetros configurables del programa de fidelización CRM (v5.0.0)
 * desde {@code application.properties} / {@code application-test.properties}.
 */
public final class ConfiguracionFidelizacion {

    private static final Logger LOGGER = Logger.getLogger(ConfiguracionFidelizacion.class.getName());

    private static volatile Integer overridePuntosPorSol;
    private static volatile Integer overridePuntosPorCanje;
    private static volatile BigDecimal overrideDescuentoPorCanjeSoles;
    private static volatile BigDecimal overrideCumpleanosDescuentoPct;

    private ConfiguracionFidelizacion() {
    }

    /**
     * Puntos otorgados por cada S/ 1.00 gastado ({@code fidelizacion.puntosPorSol}, defecto {@code 1}).
     */
    public static int getPuntosPorSol() {
        if (overridePuntosPorSol != null) {
            return Math.max(1, overridePuntosPorSol);
        }
        return parseIntPositivo(obtenerPropiedad("fidelizacion.puntosPorSol", "FOODCORE_LOYALTY_POINTS_PER_SOL"), 1);
    }

    /**
     * Cantidad de puntos requerida por bloque de canje ({@code fidelizacion.puntosPorCanje}, defecto {@code 100}).
     */
    public static int getPuntosPorCanje() {
        if (overridePuntosPorCanje != null) {
            return Math.max(1, overridePuntosPorCanje);
        }
        return parseIntPositivo(obtenerPropiedad("fidelizacion.puntosPorCanje", "FOODCORE_LOYALTY_REDEEM_POINTS"), 100);
    }

    /**
     * Descuento en soles otorgado por cada bloque de canje ({@code fidelizacion.descuentoPorCanjeSoles}, defecto {@code 5.00}).
     */
    public static BigDecimal getDescuentoPorCanjeSoles() {
        if (overrideDescuentoPorCanjeSoles != null) {
            return overrideDescuentoPorCanjeSoles.setScale(2, RoundingMode.HALF_UP);
        }
        return parseDecimalPositivo(
                obtenerPropiedad("fidelizacion.descuentoPorCanjeSoles", "FOODCORE_LOYALTY_REDEEM_SOLES"),
                new BigDecimal("5.00")
        );
    }

    /**
     * Porcentaje de descuento por cumpleaños en el POS ({@code fidelizacion.cumpleanosDescuentoPorcentaje}, defecto {@code 10.00}).
     */
    public static BigDecimal getCumpleanosDescuentoPorcentaje() {
        if (overrideCumpleanosDescuentoPct != null) {
            return overrideCumpleanosDescuentoPct.setScale(2, RoundingMode.HALF_UP);
        }
        return parseDecimalPositivo(
                obtenerPropiedad("fidelizacion.cumpleanosDescuentoPorcentaje", "FOODCORE_LOYALTY_BIRTHDAY_PCT"),
                new BigDecimal("10.00")
        );
    }

    public static void setOverridesForTest(Integer puntosPorSol,
                                           Integer puntosPorCanje,
                                           BigDecimal descuentoPorCanjeSoles,
                                           BigDecimal cumpleanosDescuentoPct) {
        overridePuntosPorSol = puntosPorSol;
        overridePuntosPorCanje = puntosPorCanje;
        overrideDescuentoPorCanjeSoles = descuentoPorCanjeSoles;
        overrideCumpleanosDescuentoPct = cumpleanosDescuentoPct;
    }

    public static void clearOverridesForTest() {
        overridePuntosPorSol = null;
        overridePuntosPorCanje = null;
        overrideDescuentoPorCanjeSoles = null;
        overrideCumpleanosDescuentoPct = null;
    }

    private static String obtenerPropiedad(String propKey, String envKey) {
        String sys = System.getProperty(propKey);
        if (sys != null && !sys.isBlank()) {
            return sys.trim();
        }
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        Properties props = cargarProperties();
        String val = props.getProperty(propKey);
        return val != null ? val.trim() : null;
    }

    private static Properties cargarProperties() {
        Properties props = new Properties();
        cargar(props, "/application.properties");
        cargar(props, "/application-test.properties");
        return props;
    }

    private static void cargar(Properties props, String path) {
        try (InputStream is = ConfiguracionFidelizacion.class.getResourceAsStream(path)) {
            if (is != null) {
                props.load(is);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "No se pudo leer " + path, e);
        }
    }

    private static int parseIntPositivo(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int val = Integer.parseInt(raw.trim());
            return val > 0 ? val : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static BigDecimal parseDecimalPositivo(String raw, BigDecimal fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            BigDecimal val = new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
            return val.compareTo(BigDecimal.ZERO) > 0 ? val : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
