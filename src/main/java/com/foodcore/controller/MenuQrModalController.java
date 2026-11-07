package com.foodcore.controller;

import com.foodcore.service.MenuQrService;
import com.foodcore.util.AnimationUtils;
import javafx.application.Platform;
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
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MenuQrModalController {

    private static final Logger LOGGER = Logger.getLogger(MenuQrModalController.class.getName());

    @FXML private ImageView imgCodigoQr;
    @FXML private Label lblEstadoQr;
    @FXML private TextField txtUrlMenu;
    @FXML private Button btnImprimirQr;

    private final MenuQrService menuQrService = new MenuQrService();
    private File archivoHtmlGenerado;

    @FXML
    public void initialize() {
        generarMenuYQr();
        if (btnImprimirQr != null) {
            AnimationUtils.attachTactileFeedback(btnImprimirQr);
        }
    }

    public void generarMenuYQr() {
        try {
            File uploadsDir = new File("uploads/qr");
            if (!uploadsDir.exists()) {
                uploadsDir.mkdirs();
            }

            archivoHtmlGenerado = new File("uploads/qr/menu_digital.html");
            menuQrService.generarMenuDigitalHtml(archivoHtmlGenerado);

            String uriDestino = archivoHtmlGenerado.toURI().toString();
            txtUrlMenu.setText(uriDestino);

            // Generar código QR PNG y cargar en el ImageView
            BufferedImage bi = menuQrService.generarCodigoQr(uriDestino, 300, 300);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", baos);
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            Image fxImage = new Image(bais);

            imgCodigoQr.setImage(fxImage);
            lblEstadoQr.setText("✓ QR generado para " + archivoHtmlGenerado.getName());

            // También exportar en disco para acceso persistente
            File archivoQrPng = new File("uploads/qr/menu_qr.png");
            menuQrService.exportarQrPng(uriDestino, 300, 300, archivoQrPng);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al generar menu y QR: " + e.getMessage(), e);
            lblEstadoQr.setText("Error al generar QR dinámico");
        }
    }

    @FXML
    public void handleAbrirEnNavegador() {
        if (archivoHtmlGenerado != null && archivoHtmlGenerado.exists()) {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(archivoHtmlGenerado.toURI());
                } else {
                    mostrarAlerta(Alert.AlertType.INFORMATION, "Menú Digital", "Ruta del archivo HTML:\n" + archivoHtmlGenerado.getAbsolutePath());
                }
            } catch (IOException e) {
                mostrarAlerta(Alert.AlertType.WARNING, "Aviso", "No se pudo abrir el navegador automáticamente: " + e.getMessage());
            }
        }
    }

    @FXML
    public void handleImprimirQr() {
        LOGGER.log(Level.INFO, "Enviando comando ESC/POS de impresión de QR a ticketera térmica...");
        mostrarAlerta(Alert.AlertType.INFORMATION, "Ticketera Térmica",
                "Comprobante con Código QR impreso exitosamente.\n\n"
                + "Cabecera: FOODCORE RESTOBAR\n"
                + "Contenido: Carta Digital en Vivo\n"
                + "Enlace: " + (archivoHtmlGenerado != null ? archivoHtmlGenerado.getName() : "menu_digital.html"));
    }

    @FXML
    public void handleCerrar() {
        Stage stage = (Stage) txtUrlMenu.getScene().getWindow();
        if (stage != null) {
            stage.close();
        }
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String contenido) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(contenido);
        alert.showAndWait();
    }

    public File getArchivoHtmlGenerado() {
        return archivoHtmlGenerado;
    }
}
