package com.foodcore.util;

import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Helper reutilizable para paginar listas y tablas JavaFX en bloques de tamaño fijo
 * (50 registros por defecto) manteniendo intacta la colección completa para filtros
 * o exportaciones.
 *
 * @param <T> Tipo de fila mostrada en la tabla.
 */
public final class PaginadorTablaHelper<T> {

    public static final int TAMANO_PAGINA_ESTANDAR = 50;

    private final ObservableList<T> vistaPagina;
    private final int tamanoPagina;
    private final Consumer<String> indicadorUpdater;
    private final Consumer<Boolean> anteriorDisableUpdater;
    private final Consumer<Boolean> siguienteDisableUpdater;

    private List<T> todosLosDatos = Collections.emptyList();
    private int paginaActual = 1;

    /**
     * Crea un paginador con el tamaño de página estándar de 50 registros.
     *
     * @param vistaPagina  Lista observable enlazada al {@code TableView}.
     * @param lblPagina    Etiqueta opcional para mostrar {@code Página X de Y}.
     * @param btnAnterior  Botón opcional para retroceder de página.
     * @param btnSiguiente Botón opcional para avanzar de página.
     */
    public PaginadorTablaHelper(ObservableList<T> vistaPagina,
            Label lblPagina,
            Button btnAnterior,
            Button btnSiguiente) {
        this(vistaPagina, TAMANO_PAGINA_ESTANDAR, lblPagina, btnAnterior, btnSiguiente);
    }

    /**
     * Crea un paginador con tamaño de página configurable.
     *
     * @param vistaPagina  Lista observable enlazada al {@code TableView}.
     * @param tamanoPagina Cantidad máxima de registros por página (&gt;= 1).
     * @param lblPagina    Etiqueta opcional para mostrar {@code Página X de Y}.
     * @param btnAnterior  Botón opcional para retroceder de página.
     * @param btnSiguiente Botón opcional para avanzar de página.
     */
    public PaginadorTablaHelper(ObservableList<T> vistaPagina,
            int tamanoPagina,
            Label lblPagina,
            Button btnAnterior,
            Button btnSiguiente) {
        if (tamanoPagina < 1) {
            throw new IllegalArgumentException("El tamaño de página debe ser >= 1");
        }
        this.vistaPagina = Objects.requireNonNull(vistaPagina, "vistaPagina no puede ser null");
        this.tamanoPagina = tamanoPagina;
        this.indicadorUpdater = lblPagina != null ? lblPagina::setText : texto -> {};
        this.anteriorDisableUpdater = btnAnterior != null ? btnAnterior::setDisable : deshabilitado -> {};
        this.siguienteDisableUpdater = btnSiguiente != null ? btnSiguiente::setDisable : deshabilitado -> {};
    }

    /**
     * Reemplaza el conjunto completo de datos y reinicia la vista a la página 1.
     *
     * @param datos Colección completa a paginar (puede ser null).
     */
    public void setDatos(List<T> datos) {
        this.todosLosDatos = datos == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(datos));
        this.paginaActual = 1;
        refrescarVista();
    }

    /**
     * Avanza a la página siguiente si existe.
     */
    public void paginaSiguiente() {
        if (paginaActual < getTotalPaginas()) {
            paginaActual++;
            refrescarVista();
        }
    }

    /**
     * Retrocede a la página anterior si existe.
     */
    public void paginaAnterior() {
        if (paginaActual > 1) {
            paginaActual--;
            refrescarVista();
        }
    }

    /**
     * Navega directamente a una página específica (ajustada a rango válido).
     *
     * @param pagina Número de página 1-based.
     */
    public void irAPagina(int pagina) {
        int totalPaginas = getTotalPaginas();
        this.paginaActual = Math.max(1, Math.min(pagina, totalPaginas));
        refrescarVista();
    }

    /**
     * @return Número de página actual (1-based).
     */
    public int getPaginaActual() {
        return paginaActual;
    }

    /**
     * @return Cantidad total de páginas (mínimo 1).
     */
    public int getTotalPaginas() {
        if (todosLosDatos.isEmpty()) {
            return 1;
        }
        return (int) Math.ceil((double) todosLosDatos.size() / tamanoPagina);
    }

    /**
     * @return Cantidad total de registros sin paginar.
     */
    public int getTotalRegistros() {
        return todosLosDatos.size();
    }

    /**
     * @return Texto formateado {@code Página X de Y}.
     */
    public String getTextoIndicador() {
        return "Página " + paginaActual + " de " + getTotalPaginas();
    }

    /**
     * @return Copia inmutable de todos los registros cargados en el paginador.
     */
    public List<T> getTodosLosDatos() {
        return Collections.unmodifiableList(new ArrayList<>(todosLosDatos));
    }

    private void refrescarVista() {
        int totalPaginas = getTotalPaginas();
        if (paginaActual > totalPaginas) {
            paginaActual = totalPaginas;
        }
        if (todosLosDatos.isEmpty()) {
            vistaPagina.clear();
        } else {
            int desde = (paginaActual - 1) * tamanoPagina;
            int hasta = Math.min(desde + tamanoPagina, todosLosDatos.size());
            vistaPagina.setAll(todosLosDatos.subList(desde, hasta));
        }

        indicadorUpdater.accept(getTextoIndicador());
        anteriorDisableUpdater.accept(paginaActual <= 1);
        siguienteDisableUpdater.accept(paginaActual >= totalPaginas || todosLosDatos.isEmpty());
    }
}
