package com.foodcore.util;

import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Producto;
import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utilidad compartida de cálculo y formateo de variantes/modificadores para POS, Mozo, KDS y Tickets (v4.8.0).
 */
public final class FormatoPersonalizacionUtil {

    private FormatoPersonalizacionUtil() {
    }

    /**
     * Calcula el precio unitario final considerando variante seleccionada (o precio base) más extras.
     */
    public static BigDecimal calcularPrecioUnitario(Producto producto,
                                                    ProductoVariante variante,
                                                    List<ProductoModificador> modificadores) {
        BigDecimal base = producto != null && producto.getPrecioVenta() != null
                ? producto.getPrecioVenta()
                : BigDecimal.ZERO;
        BigDecimal precioVariante = variante != null
                ? variante.calcularPrecioEfectivo(base)
                : base;
        BigDecimal totalExtras = BigDecimal.ZERO;
        if (modificadores != null) {
            for (ProductoModificador mod : modificadores) {
                if (mod != null && mod.getPrecio() != null) {
                    totalExtras = totalExtras.add(mod.getPrecioNormalizado());
                }
            }
        }
        return precioVariante.add(totalExtras).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Formatea los modificadores seleccionados separados por {@code " + "} (ej. {@code "Tocino + Extra Queso"}).
     */
    public static String formatearTextoModificadores(List<ProductoModificador> modificadores) {
        if (modificadores == null || modificadores.isEmpty()) {
            return "";
        }
        List<String> nombres = new ArrayList<>();
        for (ProductoModificador mod : modificadores) {
            if (mod != null && mod.getNombre() != null && !mod.getNombre().isBlank()) {
                nombres.add(mod.getNombre().trim());
            }
        }
        return String.join(" + ", nombres);
    }

    /**
     * Construye el texto unificado de platillo + tamaño + modificadores para POS, KDS y Comandas
     * (ej. {@code "Hamburguesa + Tocino + Extra Queso"} o {@code "Pizza Artesanal (Familiar) + Extra Queso"}).
     */
    public static String formatearDescripcionCompleta(String nombreProducto,
                                                      String nombreVariante,
                                                      String modificadoresTexto) {
        StringBuilder sb = new StringBuilder();
        String base = (nombreProducto != null && !nombreProducto.isBlank()) ? nombreProducto.trim() : "Producto";
        sb.append(base);
        if (nombreVariante != null && !nombreVariante.isBlank()) {
            sb.append(" (").append(nombreVariante.trim()).append(")");
        }
        String mods = normalizarSeparadoresModificadores(modificadoresTexto);
        if (!mods.isEmpty()) {
            sb.append(" + ").append(mods);
        }
        return sb.toString();
    }

    public static String formatearDescripcionItem(DetallePedido detalle) {
        if (detalle == null) {
            return "";
        }
        String mods = normalizarModificadoresDetalle(detalle.getModificadoresSeleccionados(), detalle.getModificadoresTexto());
        return formatearDescripcionCompleta(detalle.getNombreProducto(), detalle.getNombreVariante(), mods);
    }

    public static String formatearDescripcionItem(DetalleVenta detalle) {
        if (detalle == null) {
            return "";
        }
        String mods = normalizarModificadoresDetalle(detalle.getModificadoresSeleccionados(), detalle.getModificadoresTexto());
        return formatearDescripcionCompleta(detalle.getNombreProducto(), detalle.getNombreVariante(), mods);
    }

    public static String normalizarModificadoresDetalle(List<ProductoModificador> seleccionados, String textoFallback) {
        if (seleccionados != null && !seleccionados.isEmpty()) {
            return formatearTextoModificadores(seleccionados);
        }
        return normalizarSeparadoresModificadores(textoFallback);
    }

    public static String normalizarSeparadoresModificadores(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String[] partes = texto.split("\\s*[+,]\\s*");
        List<String> limpias = new ArrayList<>();
        for (String p : partes) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                limpias.add(trimmed);
            }
        }
        return String.join(" + ", limpias);
    }

    /**
     * Compara si dos líneas representan exactamente el mismo producto, variante, extras y nota.
     */
    public static boolean mismaPersonalizacion(int prodA,
                                               Integer varA,
                                               String modsA,
                                               String notaA,
                                               int prodB,
                                               Integer varB,
                                               String modsB,
                                               String notaB) {
        return prodA == prodB
                && Objects.equals(varA, varB)
                && claveOrdenadaModificadores(modsA).equals(claveOrdenadaModificadores(modsB))
                && normalizarTextoSimple(notaA).equalsIgnoreCase(normalizarTextoSimple(notaB));
    }

    private static List<String> claveOrdenadaModificadores(String texto) {
        String norm = normalizarSeparadoresModificadores(texto);
        if (norm.isEmpty()) {
            return List.of();
        }
        String[] partes = norm.split("\\s*\\+\\s*");
        List<String> ordenadas = new ArrayList<>();
        for (String p : partes) {
            if (!p.isBlank()) {
                ordenadas.add(p.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        java.util.Collections.sort(ordenadas);
        return ordenadas;
    }

    private static String normalizarTextoSimple(String texto) {
        return texto == null ? "" : texto.trim();
    }

    /**
     * Resumen corto para tarjetas del catálogo POS y Mozo (ej. {@code "2 tam. · 3 ext."}).
     */
    public static String construirResumenOpciones(Producto producto) {
        if (producto == null) {
            return "";
        }
        return construirResumenOpciones(producto.getVariantes().size(), producto.getModificadores().size(), "");
    }

    /**
     * Resumen compacto a partir de los conteos de variantes y modificadores configurados.
     */
    public static String construirResumenOpciones(int numVars, int numMods, String valorSiVacio) {
        if (numVars <= 0 && numMods <= 0) {
            return valorSiVacio != null ? valorSiVacio : "";
        }
        if (numVars > 0 && numMods > 0) {
            return numVars + " tam. · " + numMods + " ext.";
        }
        if (numVars > 0) {
            return numVars + (numVars == 1 ? " tamaño" : " tamaños");
        }
        return numMods + (numMods == 1 ? " extra" : " extras");
    }
}
