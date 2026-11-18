package com.foodcore.util;

import com.foodcore.App;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.lang.ref.WeakReference;
import java.net.URL;

/**
 * Servicio centralizado de notificaciones Toast animadas (Sub-Fase F - F6)
 * con contenedor {@link HBox}, borde izquierdo semántico, icono Feather y botón de cierre.
 */
public final class ToastService {

    /** Tipos semánticos soportados para notificaciones Toast. */
    public enum TipoToast {
        EXITO,
        ERROR,
        ADVERTENCIA,
        INFO
    }

    private static volatile WeakReference<Pane> activeHostPaneRef;
    private static volatile String ultimoMensajeToast;
    private static volatile TipoToast ultimoTipoToast;

    private ToastService() {
    }

    /**
     * Registra el contenedor raíz de la vista activa para mostrar toasts sin pasar el host explícitamente.
     */
    public static void registrarHostPane(Pane hostPane) {
        activeHostPaneRef = hostPane != null ? new WeakReference<>(hostPane) : null;
    }

    public static String getUltimoMensajeToast() {
        return ultimoMensajeToast;
    }

    public static TipoToast getUltimoTipoToast() {
        return ultimoTipoToast;
    }

    /**
     * Muestra un Toast de éxito con borde izquierdo verde en el contenedor activo o en ventana flotante.
     */
    public static void mostrarExito(String mensaje) {
        mostrarToast(null, mensaje, TipoToast.EXITO);
    }

    /**
     * Muestra un Toast de éxito en el contenedor especificado.
     */
    public static void mostrarExito(Pane hostPane, String mensaje) {
        mostrarToast(hostPane, mensaje, TipoToast.EXITO);
    }

    /**
     * Muestra un Toast de error con borde izquierdo rojo en el contenedor activo o en ventana flotante.
     */
    public static void mostrarError(String mensaje) {
        mostrarToast(null, mensaje, TipoToast.ERROR);
    }

    /**
     * Muestra un Toast de error en el contenedor especificado.
     */
    public static void mostrarError(Pane hostPane, String mensaje) {
        mostrarToast(hostPane, mensaje, TipoToast.ERROR);
    }

    /**
     * Muestra un Toast de advertencia con borde izquierdo ámbar.
     */
    public static void mostrarAdvertencia(String mensaje) {
        mostrarToast(null, mensaje, TipoToast.ADVERTENCIA);
    }

    /**
     * Muestra un Toast de advertencia en el contenedor especificado.
     */
    public static void mostrarAdvertencia(Pane hostPane, String mensaje) {
        mostrarToast(hostPane, mensaje, TipoToast.ADVERTENCIA);
    }

    /**
     * Muestra un Toast informativo sobre el contenedor raíz activo.
     */
    public static void mostrarInfo(String mensaje) {
        mostrarToast(null, mensaje, TipoToast.INFO);
    }

    /**
     * Muestra un mensaje toast informativo sobre el contenedor especificado.
     */
    public static void mostrarInfo(Pane hostPane, String mensaje) {
        mostrarToast(hostPane, mensaje, TipoToast.EXITO);
    }

    /**
     * Despacha un Toast según un {@link Alert.AlertType} evitando bloquear al usuario con modales simples.
     */
    public static void mostrarPorAlertType(Alert.AlertType tipo, String mensaje) {
        if (tipo == Alert.AlertType.ERROR) {
            mostrarError(mensaje);
        } else if (tipo == Alert.AlertType.WARNING) {
            mostrarAdvertencia(mensaje);
        } else {
            mostrarExito(mensaje);
        }
    }

    /**
     * Conserva compatibilidad para avisos que explícitamente requieran modal vía {@link DialogService}.
     */
    public static void mostrarAlerta(Alert.AlertType tipo, String titulo, String contenido) {
        DialogService.mostrarAlerta(tipo, titulo, contenido);
    }

    /**
     * Construye el componente visual {@link HBox} del Toast con borde semántico, icono, texto y cierre manual.
     */
    public static HBox construirToast(String mensaje, TipoToast tipo, Runnable onClose) {
        TipoToast tipoEfectivo = tipo != null ? tipo : TipoToast.INFO;
        String iconLiteral;
        String claseSemantica;
        switch (tipoEfectivo) {
            case EXITO -> {
                iconLiteral = "fth-check-circle";
                claseSemantica = "toast-success";
            }
            case ERROR -> {
                iconLiteral = "fth-alert-circle";
                claseSemantica = "toast-error";
            }
            default -> {
                iconLiteral = "fth-alert-triangle";
                claseSemantica = "toast-warning";
            }
        }

        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(18);
        icon.getStyleClass().add("toast-icon");

        Label lbl = new Label(mensaje != null ? mensaje : "");
        lbl.getStyleClass().add("text-body");
        lbl.setWrapText(true);
        lbl.setMaxWidth(260.0);
        HBox.setHgrow(lbl, Priority.ALWAYS);

        FontIcon closeIcon = new FontIcon("fth-x");
        closeIcon.setIconSize(14);
        Button btnCerrar = new Button();
        btnCerrar.setGraphic(closeIcon);
        btnCerrar.getStyleClass().addAll("btn-ghost", "toast-close-btn");
        btnCerrar.setFocusTraversable(false);
        if (onClose != null) {
            btnCerrar.setOnAction(e -> onClose.run());
        }

        HBox toast = new HBox(12.0, icon, lbl, btnCerrar);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.getStyleClass().addAll("toast-container", "toast-card", claseSemantica);
        toast.setMaxWidth(380.0);
        toast.setMaxHeight(Region.USE_PREF_SIZE);
        aplicarEstilosToast(toast);
        return toast;
    }

    /**
     * Despliega el Toast animado ({@link TranslateTransition} + espera 4s + {@link FadeTransition}).
     */
    public static void mostrarToast(Pane hostPane, String mensaje, TipoToast tipo) {
        if (mensaje == null || mensaje.isBlank()) {
            return;
        }
        TipoToast tipoEfectivo = tipo != null ? tipo : TipoToast.INFO;
        ultimoMensajeToast = mensaje;
        ultimoTipoToast = tipoEfectivo;

        if (!Platform.isFxApplicationThread()) {
            try {
                Platform.runLater(() -> mostrarToast(hostPane, mensaje, tipoEfectivo));
            } catch (IllegalStateException ignored) {
                // Entorno unitario sin toolkit JavaFX inicializado
            }
            return;
        }

        Pane targetPane = hostPane != null ? hostPane : resolverHostPane();
        if (targetPane != null) {
            mostrarEnPane(targetPane, mensaje, tipoEfectivo);
            return;
        }

        Window targetWindow = resolverVentanaActiva();
        if (targetWindow != null) {
            mostrarEnPopup(targetWindow, mensaje, tipoEfectivo);
        }
    }

    private static void mostrarEnPane(Pane hostPane, String mensaje, TipoToast tipo) {
        final HBox[] toastHolder = new HBox[1];
        final SequentialTransition[] animHolder = new SequentialTransition[1];
        Runnable cerrar = () -> {
            if (animHolder[0] != null) {
                animHolder[0].stop();
            }
            if (toastHolder[0] != null) {
                hostPane.getChildren().remove(toastHolder[0]);
            }
        };

        HBox toast = construirToast(mensaje, tipo, cerrar);
        toastHolder[0] = toast;

        if (hostPane instanceof StackPane stack) {
            StackPane.setAlignment(toast, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(toast, new Insets(0.0, 24.0, 24.0, 0.0));
            stack.getChildren().add(toast);
        } else {
            hostPane.getChildren().add(toast);
        }

        animHolder[0] = crearAnimacionCicloVida(toast, () -> hostPane.getChildren().remove(toast));
        animHolder[0].play();
    }

    private static void mostrarEnPopup(Window owner, String mensaje, TipoToast tipo) {
        Popup popup = new Popup();
        popup.setAutoFix(true);
        final SequentialTransition[] animHolder = new SequentialTransition[1];
        Runnable cerrar = () -> {
            if (animHolder[0] != null) {
                animHolder[0].stop();
            }
            popup.hide();
        };

        HBox toast = construirToast(mensaje, tipo, cerrar);
        popup.getContent().add(toast);
        popup.show(owner);

        double ancho = Math.max(320.0, toast.prefWidth(-1));
        double alto = Math.max(54.0, toast.prefHeight(ancho));
        double x = owner.getX() + Math.max(24.0, owner.getWidth() - ancho - 24.0);
        double y = owner.getY() + Math.max(24.0, owner.getHeight() - alto - 24.0);
        popup.setX(x);
        popup.setY(y);

        animHolder[0] = crearAnimacionCicloVida(toast, popup::hide);
        animHolder[0].play();
    }

    private static SequentialTransition crearAnimacionCicloVida(HBox toast, Runnable alFinalizar) {
        toast.setOpacity(0.0);
        toast.setTranslateX(90.0);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(260), toast);
        slideIn.setFromX(90.0);
        slideIn.setToX(0.0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(220), toast);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition entrada = new ParallelTransition(slideIn, fadeIn);
        PauseTransition espera = new PauseTransition(Duration.seconds(4));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(220), toast);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition slideOut = new TranslateTransition(Duration.millis(220), toast);
        slideOut.setFromX(0.0);
        slideOut.setToX(40.0);
        slideOut.setInterpolator(Interpolator.EASE_IN);

        ParallelTransition salida = new ParallelTransition(fadeOut, slideOut);
        salida.setOnFinished(e -> {
            if (alFinalizar != null) {
                alFinalizar.run();
            }
        });

        return new SequentialTransition(entrada, espera, salida);
    }

    private static Pane resolverHostPane() {
        ObservableList<Window> windows = Window.getWindows();
        for (int i = windows.size() - 1; i >= 0; i--) {
            Window w = windows.get(i);
            if (w.isShowing() && w.getScene() != null && w.getScene().getRoot() instanceof StackPane sp) {
                return sp;
            }
        }
        WeakReference<Pane> ref = activeHostPaneRef;
        if (ref != null) {
            Pane host = ref.get();
            if (host != null) {
                return host;
            }
        }
        Stage primary = App.getPrimaryStage();
        if (primary != null && primary.getScene() != null) {
            Parent root = primary.getScene().getRoot();
            if (root instanceof StackPane sp) {
                return sp;
            }
        }
        return null;
    }

    private static Window resolverVentanaActiva() {
        ObservableList<Window> windows = Window.getWindows();
        for (int i = windows.size() - 1; i >= 0; i--) {
            Window w = windows.get(i);
            if (w.isShowing() && w.getScene() != null && !(w instanceof Popup)) {
                return w;
            }
        }
        return App.getPrimaryStage();
    }

    private static void aplicarEstilosToast(HBox toast) {
        URL light = ToastService.class.getResource("/css/foodcore.css");
        if (light != null) {
            String lightUrl = light.toExternalForm();
            if (!toast.getStylesheets().contains(lightUrl)) {
                toast.getStylesheets().add(lightUrl);
            }
        }
        if (App.isDarkModeActivo()) {
            URL dark = ToastService.class.getResource("/css/dark-theme.css");
            if (dark != null) {
                String darkUrl = dark.toExternalForm();
                if (!toast.getStylesheets().contains(darkUrl)) {
                    toast.getStylesheets().add(darkUrl);
                }
            }
        }
    }
}
