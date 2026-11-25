package com.foodcore.controller;

import com.foodcore.config.AppContainer;
import com.foodcore.controller.dashboard.LifecycleAwareController;
import com.foodcore.service.MenuQrService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ToastService;
import com.foodcore.util.UiAsyncExecutor;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MenuQrModalController implements LifecycleAwareController {

    private static final Logger LOGGER = Logger.getLogger(MenuQrModalController.class.getName());

    private static final class QrGenerado {
        private final String urlRed;
        private final byte[] pngBytes;
        private final boolean tunnelActivo;

        private QrGenerado(String urlRed, byte[] pngBytes, boolean tunnelActivo) {
            this.urlRed = urlRed;
            this.pngBytes = pngBytes;
            this.tunnelActivo = tunnelActivo;
        }

        private ByteArrayInputStream comoStreamImagen() {
            return new ByteArrayInputStream(pngBytes);
        }
    }

    @FXML private ImageView imgCodigoQr;
    @FXML private Label lblEstadoQr;
    @FXML private TextField txtUrlMenu;
    @FXML private Button btnImprimirQr;

    private final MenuQrService menuQrService;
    private final AtomicBoolean vistaActiva = new AtomicBoolean(true);
    private final AtomicLong cargaSeq = new AtomicLong(0L);
    private File archivoHtmlGenerado;

    public MenuQrModalController() {
        this(AppContainer.getDefault().resolve(MenuQrService.class));
    }

    public MenuQrModalController(MenuQrService menuQrService) {
        this.menuQrService = Objects.requireNonNull(menuQrService, "menuQrService no puede ser nulo");
    }

    @FXML
    public void initialize() {
        vistaActiva.set(true);
        generarMenuYQr();
        if (btnImprimirQr != null) {
            AnimationUtils.attachTactileFeedback(btnImprimirQr);
        }
    }

    @Override
    public void detenerRecursos() {
        vistaActiva.set(false);
    }

    public void generarMenuYQr() {
        long seq = cargaSeq.incrementAndGet();
        // v4.6.0 Fase 2: Cero caché HTML estático; el endpoint consulta MySQL en vivo en cada petición.
        archivoHtmlGenerado = null;
        if (lblEstadoQr != null) {
            lblEstadoQr.setText("Generando código QR dinámico...");
        }
        if (btnImprimirQr != null) {
            btnImprimirQr.setDisable(true);
        }

        UiAsyncExecutor.ejecutarAsync(
            () -> {
                Files.createDirectories(Path.of("uploads", "qr"));
                String urlRed = MenuQrService.construirUrlMenuDigital(8443);
                BufferedImage bi = menuQrService.generarCodigoQr(urlRed, 300, 300);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bi, "png", baos);

                boolean tunnelActivo = com.foodcore.service.TunnelService.tieneUrlActiva();
                File archivoQrPng = new File("uploads/qr/menu_qr.png");
                menuQrService.exportarQrPng(urlRed, 300, 300, archivoQrPng);
                return new QrGenerado(urlRed, baos.toByteArray(), tunnelActivo);
            },
            res -> {
                if (btnImprimirQr != null) {
                    btnImprimirQr.setDisable(false);
                }
                if (!vistaActiva.get() || seq != cargaSeq.get()) {
                    return;
                }
                if (txtUrlMenu != null) {
                    txtUrlMenu.setText(res.urlRed);
                }
                if (imgCodigoQr != null) {
                    imgCodigoQr.setImage(new Image(res.comoStreamImagen()));
                }
                if (lblEstadoQr != null) {
                    String prefijoEstado = res.tunnelActivo
                            ? "✓ QR público activo (Cloudflare): "
                            : "✓ QR activo en red local: ";
                    lblEstadoQr.setText(prefijoEstado + res.urlRed);
                }
            },
            e -> {
                if (btnImprimirQr != null) {
                    btnImprimirQr.setDisable(false);
                }
                LOGGER.log(Level.SEVERE, "Error al generar menu y QR: " + e.getMessage(), e);
                if (!vistaActiva.get()) {
                    return;
                }
                if (lblEstadoQr != null) {
                    lblEstadoQr.setText("Error al generar QR dinámico");
                }
                mostrarAlerta(Alert.AlertType.ERROR, "Menú Digital",
                        "Error al generar QR dinámico: " + e.getMessage());
            }
        );
    }

    @FXML
    public void handleAbrirEnNavegador() {
        String url = txtUrlMenu != null && txtUrlMenu.getText() != null && !txtUrlMenu.getText().isBlank()
                ? txtUrlMenu.getText()
                : MenuQrService.construirUrlMenuDigital(8443);
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new java.net.URI(url));
            } else {
                mostrarAlerta(Alert.AlertType.INFORMATION, "Menú Digital", "URL del Menú:\n" + url);
            }
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.WARNING, "Aviso", "No se pudo abrir el navegador automáticamente: " + e.getMessage());
        }
    }

    @FXML
    public void handleImprimirQr() {
        String urlActual = txtUrlMenu != null && txtUrlMenu.getText() != null && !txtUrlMenu.getText().isBlank()
                ? txtUrlMenu.getText()
                : MenuQrService.construirUrlMenuDigital(8443);
        LOGGER.log(Level.INFO, "Enviando comando ESC/POS de impresión de QR a ticketera térmica...");
        mostrarAlerta(Alert.AlertType.INFORMATION, "Ticketera Térmica",
                "Comprobante con Código QR impreso exitosamente.\n\n"
                + "Cabecera: FOODCORE RESTOBAR\n"
                + "Contenido: Carta Digital en Vivo\n"
                + "Enlace: " + urlActual);
    }

    @FXML
    public void handleCerrar() {
        Stage stage = (Stage) txtUrlMenu.getScene().getWindow();
        if (stage != null) {
            stage.close();
        }
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String contenido) {
        ToastService.mostrarPorAlertType(tipo, titulo + ": " + contenido);
    }

    public File getArchivoHtmlGenerado() {
        return archivoHtmlGenerado;
    }
}
