package com.foodcore.util;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Constructor y sincronizador del componente visual EmptyState para tablas vacías (§2.3).
 */
public final class EmptyStateHelper {

    private EmptyStateHelper() {
    }

    /**
     * Crea un contenedor {@link VBox} centrado con icono Feather {@code fth-inbox} y texto estándar.
     */
    public static VBox crearEmptyState() {
        return crearEmptyState("No hay registros",
                "No se encontraron datos para el período seleccionado.");
    }

    /**
     * Crea un contenedor {@link VBox} centrado con título y subtítulo personalizados.
     */
    public static VBox crearEmptyState(String titulo, String subtitulo) {
        FontIcon icon = new FontIcon("fth-inbox");
        icon.setIconSize(48);
        icon.getStyleClass().add("text-muted");

        Label lblTitle = new Label(titulo);
        lblTitle.getStyleClass().add("h3");

        Label lblSub = new Label(subtitulo);
        lblSub.getStyleClass().add("text-secondary");
        lblSub.setWrapText(true);

        VBox box = new VBox(12.0, icon, lblTitle, lblSub);
        box.setAlignment(Pos.CENTER);
        box.setMouseTransparent(true);
        return box;
    }

    /**
     * Configura el placeholder estándar EmptyState directamente sobre una {@link TableView}.
     */
    public static void aplicarPlaceholder(TableView<?> tabla) {
        if (tabla != null) {
            tabla.setPlaceholder(crearEmptyState());
        }
    }

    /**
     * Sincroniza la visibilidad de un nodo {@code emptyState} superpuesto en {@code StackPane}
     * con el estado vacío de {@code tabla.getItems()}.
     */
    public static <T> void vincularATabla(TableView<T> tabla, VBox emptyState) {
        if (tabla == null || emptyState == null) {
            return;
        }
        emptyState.setMouseTransparent(true);
        Runnable sync = () -> {
            ObservableList<T> items = tabla.getItems();
            boolean vacia = items == null || items.isEmpty();
            emptyState.setVisible(vacia);
            emptyState.setManaged(vacia);
        };
        if (tabla.getItems() != null) {
            tabla.getItems().addListener((ListChangeListener<T>) c -> sync.run());
        }
        tabla.itemsProperty().addListener((obs, oldList, newList) -> {
            if (newList != null) {
                newList.addListener((ListChangeListener<T>) c -> sync.run());
            }
            sync.run();
        });
        sync.run();
    }
}
