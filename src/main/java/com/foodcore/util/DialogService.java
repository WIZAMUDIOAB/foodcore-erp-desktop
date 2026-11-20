package com.foodcore.util;

import com.foodcore.App;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.URL;
import java.util.Collection;
import java.util.List;

/**
 * Servicio centralizado de diálogos, alertas estilizadas y modales de formulario
 * conforme al estándar cerrado UX/UI v2.2 (§2.1 y §2.2).
 */
public final class DialogService {

    public static final ButtonType BTN_SI_ANULAR =
            new ButtonType("Sí, anular", ButtonBar.ButtonData.OK_DONE);
    public static final ButtonType BTN_CANCELAR =
            new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
    public static final String PROP_SHAKE_TRANSITION = "fc_shake_transition";

    private static final String LIGHT_CSS = "/css/foodcore.css";
    private static final String DARK_CSS = "/css/dark-theme.css";

    private DialogService() {
    }

    /**
     * Inyecta la hoja de estilos oscura y la clase {@code card} sobre cualquier {@link Dialog}.
     */
    public static void aplicarEstilosDialogo(Dialog<?> dialog) {
        if (dialog == null || dialog.getDialogPane() == null) {
            return;
        }
        DialogPane pane = dialog.getDialogPane();
        agregarStylesheet(pane, LIGHT_CSS);
        if (App.isDarkModeActivo()) {
            agregarStylesheet(pane, DARK_CSS);
        } else {
            pane.getStylesheets().removeIf(s -> s.contains("dark-theme.css"));
        }
        if (!pane.getStyleClass().contains("card")) {
            pane.getStyleClass().add("card");
        }
        estilizarBotonesDialogo(pane);
        pane.getButtonTypes().addListener((ListChangeListener<ButtonType>) c -> estilizarBotonesDialogo(pane));
    }

    private static void agregarStylesheet(DialogPane pane, String resourcePath) {
        URL url = DialogService.class.getResource(resourcePath);
        if (url != null) {
            String ext = url.toExternalForm();
            if (!pane.getStylesheets().contains(ext)) {
                pane.getStylesheets().add(ext);
            }
        }
    }

    private static void estilizarBotonesDialogo(DialogPane pane) {
        for (ButtonType bt : pane.getButtonTypes()) {
            Node node = pane.lookupButton(bt);
            if (node instanceof Button btn) {
                btn.setGraphicTextGap(8.0);
                btn.getStyleClass().removeAll("btn-primary", "btn-secondary");
                ButtonBar.ButtonData data = bt.getButtonData();
                if (data == ButtonBar.ButtonData.CANCEL_CLOSE || data == ButtonBar.ButtonData.NO) {
                    btn.getStyleClass().add("btn-secondary");
                } else {
                    btn.getStyleClass().add("btn-primary");
                }
            }
        }
    }

    /**
     * Crea una alerta estilizada sin encabezado redundante.
     */
    public static Alert crearAlerta(Alert.AlertType tipo, String titulo, String contenido) {
        return crearAlerta(tipo, titulo, null, contenido);
    }

    /**
     * Crea una alerta estilizada con título, encabezado opcional y contenido.
     */
    public static Alert crearAlerta(Alert.AlertType tipo, String titulo, String header, String contenido) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(header);
        alert.setContentText(contenido);
        aplicarEstilosDialogo(alert);
        return alert;
    }

    /**
     * Crea una alerta de confirmación o mensaje con botones explícitos.
     */
    public static Alert crearAlerta(Alert.AlertType tipo, String titulo, String contenido, ButtonType... botones) {
        Alert alert = new Alert(tipo, contenido, botones);
        if (titulo != null) {
            alert.setTitle(titulo);
        }
        alert.setHeaderText(null);
        aplicarEstilosDialogo(alert);
        return alert;
    }

    /**
     * Muestra de forma modal una alerta estilizada.
     */
    public static void mostrarAlerta(Alert.AlertType tipo, String titulo, String contenido) {
        crearAlerta(tipo, titulo, contenido).showAndWait();
    }

    /**
     * Crea un modal de confirmación destructiva con botón "Sí, anular" en rojo ({@code --status-danger}),
     * botón "Cancelar" en gris y animación horizontal Shake de 300ms (Sub-Fase F - F7).
     */
    public static Alert crearConfirmacionDestructiva(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.WARNING, mensaje, BTN_CANCELAR, BTN_SI_ANULAR);
        alert.setTitle(titulo != null ? titulo : "Confirmar acción destructiva");
        alert.setHeaderText(titulo != null ? titulo : "Confirmar acción destructiva");
        aplicarEstilosDialogo(alert);

        DialogPane pane = alert.getDialogPane();
        Node btnAnularNode = pane.lookupButton(BTN_SI_ANULAR);
        if (btnAnularNode instanceof Button btnAnular) {
            btnAnular.getStyleClass().removeAll("btn-primary", "btn-secondary");
            if (!btnAnular.getStyleClass().contains("btn-danger")) {
                btnAnular.getStyleClass().add("btn-danger");
            }
            if (!btnAnular.getStyleClass().contains("btn-destructive")) {
                btnAnular.getStyleClass().add("btn-destructive");
            }
        }
        Node btnCancelarNode = pane.lookupButton(BTN_CANCELAR);
        if (btnCancelarNode instanceof Button btnCancelar) {
            btnCancelar.getStyleClass().removeAll("btn-primary", "btn-danger", "btn-destructive");
            if (!btnCancelar.getStyleClass().contains("btn-secondary")) {
                btnCancelar.getStyleClass().add("btn-secondary");
            }
        }

        TranslateTransition shake = aplicarShake(pane);
        if (shake != null) {
            alert.setOnShown(e -> shake.playFromStart());
            shake.playFromStart();
        }
        return alert;
    }

    /**
     * Aplica una vibración horizontal (Shake) de 300ms sobre el nodo indicado.
     */
    public static TranslateTransition aplicarShake(Node node) {
        if (node == null) {
            return null;
        }
        TranslateTransition shake = new TranslateTransition(Duration.millis(300), node);
        shake.setFromX(-10.0);
        shake.setToX(10.0);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setInterpolator(Interpolator.EASE_BOTH);
        shake.setOnFinished(e -> node.setTranslateX(0.0));
        node.getProperties().put(PROP_SHAKE_TRANSITION, shake);
        return shake;
    }

    /**
     * Crea un {@link Dialog} genérico con hojas de estilo aplicadas.
     */
    public static <R> Dialog<R> crearDialogo(String titulo, String header) {
        Dialog<R> dialog = new Dialog<>();
        dialog.setTitle(titulo);
        dialog.setHeaderText(header);
        aplicarEstilosDialogo(dialog);
        return dialog;
    }

    /**
     * Crea un {@link TextInputDialog} estilizado según v2.2.
     */
    public static TextInputDialog crearTextInputDialog(String valorInicial,
                                                       String titulo,
                                                       String header,
                                                       String contenido) {
        TextInputDialog dialog = new TextInputDialog(valorInicial != null ? valorInicial : "");
        dialog.setTitle(titulo);
        dialog.setHeaderText(header);
        dialog.setContentText(contenido);
        aplicarEstilosDialogo(dialog);
        if (dialog.getEditor() != null && !dialog.getEditor().getStyleClass().contains("input-field")) {
            dialog.getEditor().getStyleClass().add("input-field");
        }
        return dialog;
    }

    /**
     * Crea un {@link ChoiceDialog} estilizado según v2.2.
     */
    public static <T> ChoiceDialog<T> crearChoiceDialog(T valorInicial,
                                                        Collection<T> opciones,
                                                        String titulo,
                                                        String header,
                                                        String contenido) {
        ChoiceDialog<T> dialog = new ChoiceDialog<>(valorInicial, opciones);
        dialog.setTitle(titulo);
        dialog.setHeaderText(header);
        dialog.setContentText(contenido);
        aplicarEstilosDialogo(dialog);
        return dialog;
    }

    /**
     * Construye un bloque de campo con etiqueta estática superior según §1 y §2.1.
     */
    public static VBox construirCampoModal(String labelText, Control input) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("label-form");
        if (input instanceof TextField && !input.getStyleClass().contains("input-field")) {
            input.getStyleClass().add("input-field");
        } else if (input instanceof ComboBox<?> && !input.getStyleClass().contains("form-combo-box")) {
            input.getStyleClass().add("form-combo-box");
        }
        input.setMaxWidth(Double.MAX_VALUE);
        return new VBox(8.0, lbl, input);
    }

    /**
     * Construye una fila de dos campos alineados con crecimiento equitativo según §2.1.
     */
    public static HBox construirFilaDobleModal(Node campo1, Node campo2) {
        HBox row = new HBox(16.0, campo1, campo2);
        HBox.setHgrow(campo1, Priority.ALWAYS);
        HBox.setHgrow(campo2, Priority.ALWAYS);
        return row;
    }

    /**
     * Construye el footer estándar de modal con Cancelar (gris) y Guardar (naranja) a la derecha (§2.1).
     */
    public static HBox construirFooterModal(Button btnCancelar, Button btnGuardar) {
        if (btnCancelar != null) {
            if (!btnCancelar.getStyleClass().contains("btn-secondary")) {
                btnCancelar.getStyleClass().add("btn-secondary");
            }
            btnCancelar.setGraphicTextGap(8.0);
        }
        if (btnGuardar != null) {
            if (!btnGuardar.getStyleClass().contains("btn-primary")) {
                btnGuardar.getStyleClass().add("btn-primary");
            }
            btnGuardar.setGraphicTextGap(8.0);
        }
        HBox footer = new HBox(12.0, btnCancelar, btnGuardar);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(16.0, 24.0, 24.0, 24.0));
        footer.getStyleClass().add("modal-footer");
        return footer;
    }

    /**
     * Construye un modal de formulario con estructura Header / ScrollPane Body / Footer exacta de §2.1.
     */
    public static VBox construirModalFormulario(double prefWidth,
                                              String titulo,
                                              String subtitulo,
                                              List<Node> campos,
                                              HBox footer) {
        VBox card = new VBox(0.0);
        card.setPrefWidth(prefWidth);
        card.setMaxWidth(prefWidth);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.getStyleClass().addAll("card", "modal-dialog-surface");
        card.setStyle("-fx-padding: 0;");

        VBox header = new VBox(4.0);
        header.getStyleClass().add("modal-header");
        header.setPadding(new Insets(24.0, 24.0, 16.0, 24.0));
        Label lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("h2");
        header.getChildren().add(lblTitulo);
        if (subtitulo != null && !subtitulo.isBlank()) {
            Label lblSub = new Label(subtitulo);
            lblSub.getStyleClass().add("text-secondary");
            lblSub.setWrapText(true);
            header.getChildren().add(lblSub);
        }

        VBox bodyBox = new VBox(16.0);
        bodyBox.setPadding(new Insets(0.0, 24.0, 24.0, 24.0));
        if (campos != null) {
            bodyBox.getChildren().addAll(campos);
        }

        ScrollPane scroll = new ScrollPane(bodyBox);
        scroll.setFitToWidth(true);
        scroll.setMaxHeight(460.0);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        card.getChildren().addAll(header, scroll, footer);
        return card;
    }
}
