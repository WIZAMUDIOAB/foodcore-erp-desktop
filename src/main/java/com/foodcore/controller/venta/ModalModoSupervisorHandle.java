package com.foodcore.controller.venta;

import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Manejador estructurado del diálogo modal de autorización de Modo Supervisor (v5.2.0).
 */
public record ModalModoSupervisorHandle(
        Dialog<ButtonType> dialog,
        VBox rootContenido,
        TextField txtIdVenta,
        PasswordField txtPinSupervisor,
        TextField txtMotivo,
        CheckBox chkPlatoPreparado) {
}
