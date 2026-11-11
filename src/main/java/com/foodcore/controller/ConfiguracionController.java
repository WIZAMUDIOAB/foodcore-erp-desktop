package com.foodcore.controller;

import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.UiAsyncExecutor;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Configuración del Restaurante e Impresora Térmica según SPEC-FOODCORE-v1.2 (§2.4):
 * Persiste Nombre, Dirección, Teléfono, RUC opcional y Nombre de Impresora para el
 * Comprobante Interno No Fiscal.
 */
public class ConfiguracionController {

    private static final Logger LOGGER = Logger.getLogger(ConfiguracionController.class.getName());

    @FXML private StackPane rootConfigStack;
    @FXML private TextField txtNombreRestaurante;
    @FXML private TextField txtDireccion;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtRuc;
    @FXML private TextField txtImpresora;
    @FXML private Button btnGuardarConfig;

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnGuardarConfig);
        cargarConfiguracion();
    }

    public void cargarConfiguracion() {
        String sql = "SELECT nombre_restaurante, direccion, telefono, ruc, nombre_impresora "
            + "FROM configuracion_restaurante WHERE id_config = 1";
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new String[] {
                            rs.getString("nombre_restaurante"),
                            rs.getString("direccion"),
                            rs.getString("telefono"),
                            rs.getString("ruc"),
                            rs.getString("nombre_impresora")
                        };
                    }
                    return null;
                }
            },
            datos -> {
                if (datos != null) {
                    txtNombreRestaurante.setText(datos[0]);
                    txtDireccion.setText(datos[1]);
                    txtTelefono.setText(datos[2]);
                    txtRuc.setText(datos[3] != null ? datos[3] : "");
                    txtImpresora.setText(datos[4]);
                }
            },
            e -> LOGGER.log(Level.FINE, "Usando configuración por defecto", e)
        );
    }

    @FXML
    public void handleGuardarConfiguracion(ActionEvent event) {
        final String nombre = txtNombreRestaurante.getText();
        final String direccion = txtDireccion.getText();
        final String telefono = txtTelefono.getText();
        final String ruc = txtRuc.getText();
        final String impresora = txtImpresora.getText();
        final String sql = "INSERT INTO configuracion_restaurante (id_config, nombre_restaurante, direccion, telefono, ruc, nombre_impresora) "
            + "VALUES (1, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE nombre_restaurante = VALUES(nombre_restaurante), direccion = VALUES(direccion), "
            + "telefono = VALUES(telefono), ruc = VALUES(ruc), nombre_impresora = VALUES(nombre_impresora)";

        if (btnGuardarConfig != null) {
            btnGuardarConfig.setDisable(true);
        }
        UiAsyncExecutor.ejecutarVoidAsync(
            () -> {
                try (Connection conn = ConexionBD.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, nombre);
                    ps.setString(2, direccion);
                    ps.setString(3, telefono);
                    ps.setString(4, ruc);
                    ps.setString(5, impresora);
                    ps.executeUpdate();
                }
            },
            () -> {
                if (btnGuardarConfig != null) {
                    btnGuardarConfig.setDisable(false);
                }
                AnimationUtils.mostrarToast(rootConfigStack, "Configuración del restaurante e impresora guardada.", false);
            },
            e -> {
                if (btnGuardarConfig != null) {
                    btnGuardarConfig.setDisable(false);
                }
                AnimationUtils.mostrarToast(rootConfigStack, "Error al guardar configuración: " + (e != null ? e.getMessage() : "desconocido"), true);
            }
        );
    }
}
