package com.foodcore.controller.venta;

import com.foodcore.model.Cliente;
import com.foodcore.util.DialogService;
import java.time.LocalDate;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

public final class VentaPosModalHelper {
    private VentaPosModalHelper() {
    }

    @FunctionalInterface
    public interface RegistroClienteRapidoFn {
        Cliente registrar(String dni, String nombreCompleto, String telefono, LocalDate fechaNacimiento);
    }

    public static ModalModoSupervisorHandle construirModalModoSupervisor(int idVentaPrellenado) {
        Dialog<ButtonType> dialog = DialogService.crearDialogo(
                "Modo Supervisor — Autorización de Anulación", null);
        dialog.initModality(Modality.APPLICATION_MODAL);
        VBox root = new VBox(16);
        root.setPadding(new Insets(16, 20, 16, 20));
        root.setPrefWidth(460);
        Label badge = new Label("AUTORIZACIÓN DE SUPERVISOR / ADMIN REQUERIDA");
        badge.getStyleClass().add("badge-pill-warning");
        Label lblSub = new Label("Su rol actual no tiene permiso directo para anular ventas. "
                + "Solicite a un Supervisor o Administrador ingresar su PIN para autorizar y auditar la operación.");
        lblSub.setWrapText(true);
        lblSub.getStyleClass().add("text-secondary");
        TextField txtId = new TextField(idVentaPrellenado > 0 ? String.valueOf(idVentaPrellenado) : "");
        txtId.setPromptText("Ej. 1024");
        txtId.setId("txtIdVentaSupervisor");
        PasswordField txtPin = new PasswordField();
        txtPin.setPromptText("PIN secreto");
        txtPin.setId("txtPinSupervisor");
        TextField txtMotivo = new TextField();
        txtMotivo.setPromptText("Motivo de anulación (obligatorio)");
        txtMotivo.setId("txtMotivoSupervisor");
        CheckBox chkMerma = new CheckBox("Plato ya preparado en cocina (registrar MERMA)");
        root.getChildren().addAll(badge, lblSub,
                DialogService.construirFilaDobleModal(
                        DialogService.construirCampoModal("ID de Venta:", txtId),
                        DialogService.construirCampoModal("PIN Supervisor:", txtPin)),
                DialogService.construirCampoModal("Motivo:", txtMotivo), chkMerma);
        ButtonType btnConfirmar = new ButtonType("Autorizar y Anular Venta", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().getButtonTypes().setAll(btnConfirmar, ButtonType.CANCEL);
        return new ModalModoSupervisorHandle(dialog, root, txtId, txtPin, txtMotivo, chkMerma);
    }

    public static Optional<Cliente> mostrarDialogoRegistroRapidoCliente(String prefill,
            RegistroClienteRapidoFn registrador) {
        Dialog<Cliente> dialog = DialogService.crearDialogo(
                "Registro Rápido Cliente Minorista (POS)",
                "Asociar cliente por DNI para acumulación y canje de puntos");
        ButtonType btnRegistrar = new ButtonType("Registrar y Asociar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnRegistrar, ButtonType.CANCEL);
        TextField txtDni = new TextField(prefill != null && prefill.matches("\\d{8}") ? prefill : "");
        txtDni.setPromptText("8 dígitos (ej. 70112233)");
        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Nombre completo del cliente");
        TextField txtTel = new TextField(prefill != null && prefill.matches("\\d{9}") ? prefill : "");
        txtTel.setPromptText("Opcional (9 dígitos)");
        DatePicker dpNacimiento = new DatePicker();
        dpNacimiento.setPromptText("Opcional (cumpleaños)");
        VBox form = new VBox(16.0);
        form.setPadding(new Insets(16, 24, 24, 24));
        form.setPrefWidth(460.0);
        form.getChildren().addAll(
                DialogService.construirFilaDobleModal(
                        DialogService.construirCampoModal("DNI *:", txtDni),
                        DialogService.construirCampoModal("Teléfono:", txtTel)),
                DialogService.construirCampoModal("Nombre Completo *:", txtNombre),
                DialogService.construirCampoModal("Fecha Nacimiento:", dpNacimiento));
        dialog.getDialogPane().setContent(form);
        dialog.setResultConverter(btn -> {
            if (btn != btnRegistrar) {
                return null;
            }
            String dni = txtDni.getText().trim();
            String nombre = txtNombre.getText().trim();
            if (!dni.matches("\\d{8}") || nombre.isEmpty()) {
                return null;
            }
            return registrador.registrar(dni, nombre, txtTel.getText().trim(), dpNacimiento.getValue());
        });
        return dialog.showAndWait();
    }
}
