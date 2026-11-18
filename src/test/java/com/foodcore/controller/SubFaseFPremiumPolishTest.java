package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.DialogService;
import com.foodcore.util.ToastService;
import com.foodcore.util.ToastService.TipoToast;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de pruebas para Sub-Fase F (v5.6.0): Premium Polish Desktop (F1, F2, F4, F6, F7, F8).
 */
class SubFaseFPremiumPolishTest {

    @BeforeAll
    static void inicializarInfraestructura() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
    }

    @Test
    @DisplayName("F1: LoginView incluye fondo animado Aurora, panel Glassmorphism y omite el chip de MySQL")
    void testLoginAuroraYGlassmorphismSinChipMysql() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/LoginView.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1080, 700);
                App.applyStylesheets(scene);
                root.applyCss();
                root.layout();

                LoginController ctrl = loader.getController();
                Node canvasNode = root.lookup("#auroraCanvas");
                assertTrue(canvasNode instanceof Canvas, "LoginView debe incluir Canvas #auroraCanvas");
                assertTrue(ctrl.isAuroraActiva(), "La animación Aurora debe iniciar activamente");

                Node card = root.lookup("#loginCardContainer");
                assertNotNull(card);
                assertTrue(card.getStyleClass().contains("login-glass-panel"),
                        "El contenedor de login debe aplicar clase .login-glass-panel");
                assertNull(root.lookup(".login-status-chip"),
                        "El chip de estado MySQL debe haberse eliminado de LoginView.fxml");
                ctrl.detenerAnimacionAurora();
            } catch (Exception e) {
                throw new IllegalStateException("Error validando Login F1", e);
            }
        });

        String fxml = Files.readString(
                Path.of("src", "main", "resources", "com", "foodcore", "view", "LoginView.fxml"),
                StandardCharsets.UTF_8);
        assertFalse(fxml.contains("MySQL 8.0 LAN"), "LoginView.fxml no debe contener 'MySQL 8.0 LAN'");

        String css = Files.readString(
                Path.of("src", "main", "resources", "css", "foodcore.css"),
                StandardCharsets.UTF_8);
        assertTrue(css.contains(".login-glass-panel") && css.contains("rgba(24, 24, 27, 0.65)"),
                "CSS debe definir fondo Glassmorphism rgba(24, 24, 27, 0.65)");
        assertTrue(css.contains("rgba(255, 255, 255, 0.10)"),
                "CSS Glassmorphism debe definir borde sutil rgba(255, 255, 255, 0.10)");
    }

    @Test
    @DisplayName("F2: SplashView anima el logo y ejecuta fade-out/fade-in hacia LoginView")
    void testSplashScreenAnimadoYTransicionALogin() throws Exception {
        CountDownLatch transicionCompleta = new CountDownLatch(1);
        AtomicReference<Scene> sceneRef = new AtomicReference<>();
        AtomicReference<Parent> loginRef = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                FXMLLoader splashLoader = new FXMLLoader(
                        getClass().getResource("/com/foodcore/view/SplashView.fxml"));
                Parent splashRoot = splashLoader.load();
                SplashController splashCtrl = splashLoader.getController();

                assertNotNull(splashCtrl.getBootstrapSplashRoot());
                assertNotNull(splashCtrl.getLogoContainer(), "El logo del Splash debe tener fx:id #logoContainer");
                assertNotNull(splashRoot.lookup("#progressBarSplash"));
                assertTrue(splashRoot.lookup("#progressBarSplash") instanceof ProgressBar);
                assertNotNull(splashRoot.lookup("#lblEstadoBootstrap"));

                FXMLLoader loginLoader = new FXMLLoader(
                        getClass().getResource("/com/foodcore/view/LoginView.fxml"));
                Parent loginRoot = loginLoader.load();
                LoginController loginCtrl = loginLoader.getController();
                loginCtrl.detenerAnimacionAurora();

                Scene scene = new Scene(splashRoot, 1080, 700);
                App.applyStylesheets(scene);
                sceneRef.set(scene);
                loginRef.set(loginRoot);

                App.transicionarSplashALogin(scene, splashRoot, loginRoot, transicionCompleta::countDown);
            } catch (Exception e) {
                throw new IllegalStateException("Error probando Splash F2", e);
            }
        });

        assertTrue(transicionCompleta.await(5, TimeUnit.SECONDS),
                "La transición fade-out de Splash y fade-in de Login debe completarse");
        AtomicBoolean rootActualizado = new AtomicBoolean(false);
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            rootActualizado.set(sceneRef.get().getRoot() == loginRef.get());
            assertEquals(1.0, loginRef.get().getOpacity(), 0.05);
        });
        assertTrue(rootActualizado.get(), "La escena debe mostrar LoginView al finalizar la transición");
    }

    @Test
    @DisplayName("F4: foodcore.css define micro-interacciones hover/pressed y focus Terracota")
    void testMicroInteraccionesEnCss() throws Exception {
        String css = Files.readString(
                Path.of("src", "main", "resources", "css", "foodcore.css"),
                StandardCharsets.UTF_8);
        assertTrue(css.contains(".button:hover") && css.contains("-fx-translate-y: -1px"),
                "CSS debe elevar botones -1px en hover");
        assertTrue(css.contains(".button:pressed")
                && css.contains("-fx-scale-x: 0.98")
                && css.contains("-fx-scale-y: 0.98"),
                "CSS debe escalar botones a 0.98 al presionar");
        assertTrue(css.contains(".text-field:focused")
                && css.contains(".combo-box:focused")
                && css.contains("-brand-primary")
                && css.contains("rgba(234, 88, 12, 0.2)"),
                "Inputs y ComboBox enfocados deben resaltar borde -brand-primary y halo Terracota");
    }

    @Test
    @DisplayName("F6: ToastService construye HBox semántico con icono, mensaje y botón de cierre")
    void testToastsAnimadosYReemplazoDeAlertas() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            AtomicBoolean cerrado = new AtomicBoolean(false);
            HBox toastExito = ToastService.construirToast("Venta registrada", TipoToast.EXITO, () -> cerrado.set(true));
            assertTrue(toastExito.getStyleClass().contains("toast-card"));
            assertTrue(toastExito.getStyleClass().contains("toast-success"));
            assertTrue(toastExito.getChildren().stream().anyMatch(FontIcon.class::isInstance));
            assertTrue(toastExito.getChildren().stream()
                    .anyMatch(n -> n instanceof Label l && "Venta registrada".equals(l.getText())));
            Button closeBtn = toastExito.getChildren().stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .findFirst()
                    .orElse(null);
            assertNotNull(closeBtn, "El Toast debe incluir botón de cierre");
            closeBtn.fire();
            assertTrue(cerrado.get(), "El botón de cierre del Toast debe ejecutar la acción de cierre");

            HBox toastError = ToastService.construirToast("Fallo", TipoToast.ERROR, null);
            assertTrue(toastError.getStyleClass().contains("toast-error"));

            HBox toastWarn = ToastService.construirToast("Cuidado", TipoToast.ADVERTENCIA, null);
            assertTrue(toastWarn.getStyleClass().contains("toast-warning"));

            StackPane host = new StackPane();
            host.setPrefSize(800, 600);
            new Scene(host, 800, 600);
            ToastService.mostrarExito(host, "Operación completada");
            assertEquals("Operación completada", ToastService.getUltimoMensajeToast());
            assertEquals(TipoToast.EXITO, ToastService.getUltimoTipoToast());
            assertEquals(1, host.getChildren().size(), "El Toast debe acoplarse al StackPane host");

            ToastService.mostrarError(host, "Error de prueba");
            assertEquals(TipoToast.ERROR, ToastService.getUltimoTipoToast());
            assertEquals(2, host.getChildren().size());
        });
    }

    @Test
    @DisplayName("F7: DialogService.crearConfirmacionDestructiva incluye botón rojo 'Sí, anular', 'Cancelar' y Shake 300ms")
    void testConfirmacionDestructivaConShake() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            Alert confirm = DialogService.crearConfirmacionDestructiva(
                    "Anular Venta #10",
                    "Esta acción es irreversible.");
            DialogPane pane = confirm.getDialogPane();
            assertNotNull(pane);
            assertTrue(pane.getButtonTypes().contains(DialogService.BTN_SI_ANULAR));
            assertTrue(pane.getButtonTypes().contains(DialogService.BTN_CANCELAR));

            Node siNode = pane.lookupButton(DialogService.BTN_SI_ANULAR);
            assertTrue(siNode instanceof Button);
            Button btnSi = (Button) siNode;
            assertEquals("Sí, anular", btnSi.getText());
            assertTrue(btnSi.getStyleClass().contains("btn-danger"),
                    "El botón destructivo debe usar estilo rojo (.btn-danger)");

            Node cancelNode = pane.lookupButton(DialogService.BTN_CANCELAR);
            assertTrue(cancelNode instanceof Button);
            Button btnCancel = (Button) cancelNode;
            assertEquals("Cancelar", btnCancel.getText());
            assertTrue(btnCancel.getStyleClass().contains("btn-secondary"),
                    "El botón Cancelar debe usar estilo secundario (.btn-secondary)");

            Object shakeObj = pane.getProperties().get(DialogService.PROP_SHAKE_TRANSITION);
            assertTrue(shakeObj instanceof TranslateTransition,
                    "El modal destructivo debe registrar un TranslateTransition Shake");
            TranslateTransition shake = (TranslateTransition) shakeObj;
            assertEquals(300.0, shake.getDuration().toMillis(), 0.01,
                    "La animación Shake debe durar 300ms");
        });
    }

    @Test
    @DisplayName("F8 + F-UX: MainLayout muestra Breadcrumbs, actualiza ruta al navegar y alterna tema desde el header izquierdo")
    void testBreadcrumbsEnMainLayoutYCambioDeVista() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            boolean modoInicial = App.isDarkModeActivo();
            try {
                App.setDarkModeActivo(false);
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MainLayout.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 1280, 800);
                App.applyStylesheets(scene);

                MainDashboardController ctrl = loader.getController();
                Node bar = root.lookup("#breadcrumbBar");
                Label lbl = (Label) root.lookup("#lblBreadcrumbs");
                assertNotNull(bar, "MainLayout debe incluir #breadcrumbBar");
                assertNotNull(lbl, "MainLayout debe incluir #lblBreadcrumbs");

                Button btnTema = (Button) root.lookup("#btnToggleTheme");
                assertNotNull(btnTema, "MainLayout debe incluir #btnToggleTheme en el header izquierdo");
                assertNotNull(btnTema.getTooltip());
                assertEquals("Cambiar Tema", btnTema.getTooltip().getText());
                assertTrue(btnTema.getGraphic() instanceof FontIcon);
                assertEquals("fth-moon", ((FontIcon) btnTema.getGraphic()).getIconLiteral());

                ctrl.handleToggleTheme(null);
                assertTrue(App.isDarkModeActivo(), "handleToggleTheme debe activar modo oscuro");
                assertEquals("fth-sun", ((FontIcon) btnTema.getGraphic()).getIconLiteral());
                assertTrue(scene.getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css")));

                ctrl.handleToggleTheme(null);
                assertFalse(App.isDarkModeActivo(), "handleToggleTheme debe restaurar modo claro");
                assertEquals("fth-moon", ((FontIcon) btnTema.getGraphic()).getIconLiteral());
                assertFalse(scene.getStylesheets().stream().anyMatch(s -> s.contains("dark-theme.css")));

                ctrl.actualizarBreadcrumbs("Inicio > Salón > Mesas");
                assertEquals("Inicio > Salón > Mesas", lbl.getText());

                ctrl.handleNavConfiguracion(null);
                assertEquals("Inicio > Sistema > Configuración del Sistema", lbl.getText());

                ctrl.handleNavSalon(null);
                assertEquals("Inicio > Salón > Mesas", lbl.getText());
                assertEquals("Inicio > Salón > Mesas", ctrl.getRutaBreadcrumbsActual());
            } catch (Exception e) {
                throw new IllegalStateException("Error probando Breadcrumbs F8 y Toggle Tema F-UX", e);
            } finally {
                App.setDarkModeActivo(modoInicial);
            }
        });
    }
}
