package com.foodcore.controller;

import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Venta;
import com.foodcore.service.AuthService;
import com.foodcore.service.VentaService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CobroModalController {

    @FXML private Label lblTotalCobro;
    @FXML private RadioButton rbTicket;
    @FXML private RadioButton rbBoleta;
    @FXML private RadioButton rbFactura;
    @FXML private TextField txtDocCliente;
    @FXML private TextField txtNombreCliente;
    @FXML private ComboBox<String> cmbMetodoPago;
    @FXML private TextField txtMontoRecibido;
    @FXML private Label lblVuelto;

    private BigDecimal totalVenta = BigDecimal.ZERO;
    private List<DetalleVenta> itemsVenta;
    private Runnable onVentaExitosa;
    private final VentaService ventaService = new VentaService();

    @FXML
    public void initialize() {
        cmbMetodoPago.setItems(FXCollections.observableArrayList("EFECTIVO", "TARJETA", "YAPE_PLIN"));
        cmbMetodoPago.getSelectionModel().select("EFECTIVO");

        cmbMetodoPago.setOnAction(e -> {
            String sel = cmbMetodoPago.getValue();
            if (!"EFECTIVO".equals(sel)) {
                txtMontoRecibido.setText(totalVenta.toString());
                lblVuelto.setText("S/ 0.00");
                lblVuelto.setStyle("-fx-text-fill: #2a9d8f; -fx-font-weight: bold;");
            }
        });

        rbFactura.setOnAction(e -> {
            if (rbFactura.isSelected() && "00000000".equals(txtDocCliente.getText())) {
                txtDocCliente.clear();
                txtNombreCliente.clear();
                txtDocCliente.setPromptText("RUC (11 dígitos)");
                txtNombreCliente.setPromptText("Razón Social");
            }
        });
    }

    public void initData(BigDecimal total, String metodoPreseleccionado, List<DetalleVenta> items, Runnable onExitosa) {
        this.totalVenta = total;
        this.itemsVenta = items;
        this.onVentaExitosa = onExitosa;

        lblTotalCobro.setText("S/ " + total.toString());
        if (metodoPreseleccionado != null) {
            cmbMetodoPago.getSelectionModel().select(metodoPreseleccionado);
        }
        txtMontoRecibido.setText(total.toString());
        handleCalcularVuelto();
    }

    @FXML
    public void handleMontoExacto() {
        txtMontoRecibido.setText(totalVenta.toString());
        handleCalcularVuelto();
    }

    @FXML
    public void handleMontoRapido20() {
        txtMontoRecibido.setText("20.00");
        handleCalcularVuelto();
    }

    @FXML
    public void handleMontoRapido50() {
        txtMontoRecibido.setText("50.00");
        handleCalcularVuelto();
    }

    @FXML
    public void handleMontoRapido100() {
        txtMontoRecibido.setText("100.00");
        handleCalcularVuelto();
    }

    @FXML
    public void handleCalcularVuelto() {
        try {
            String texto = txtMontoRecibido.getText().trim();
            if (texto.isEmpty()) {
                lblVuelto.setText("S/ 0.00");
                return;
            }
            BigDecimal recibido = new BigDecimal(texto);
            BigDecimal vuelto = recibido.subtract(totalVenta);

            lblVuelto.setText("S/ " + vuelto.setScale(2, RoundingMode.HALF_UP));
            if (vuelto.compareTo(BigDecimal.ZERO) < 0) {
                lblVuelto.setStyle("-fx-text-fill: #e63946; -fx-font-weight: bold;");
            } else {
                lblVuelto.setStyle("-fx-text-fill: #2a9d8f; -fx-font-weight: bold;");
            }
        } catch (NumberFormatException e) {
            lblVuelto.setText("Inválido");
        }
    }

    @FXML
    public void handleConfirmarCobro(ActionEvent event) {
        try {
            BigDecimal recibido = new BigDecimal(txtMontoRecibido.getText().trim());
            if (recibido.compareTo(totalVenta) < 0 && "EFECTIVO".equals(cmbMetodoPago.getValue())) {
                mostrarAlerta("Monto Insuficiente", "El monto recibido (S/ " + recibido + ") es menor al total a pagar (S/ " + totalVenta + ").", Alert.AlertType.WARNING);
                return;
            }

            BigDecimal vuelto = recibido.subtract(totalVenta);
            if (vuelto.compareTo(BigDecimal.ZERO) < 0) vuelto = BigDecimal.ZERO;

            // Desglose tributario IGV al 18%
            BigDecimal divisor = new BigDecimal("1.18");
            BigDecimal subtotal = totalVenta.divide(divisor, 2, RoundingMode.HALF_UP);
            BigDecimal igv = totalVenta.subtract(subtotal);

            String tipoComp = "TICKET";
            String serie = "TCK";
            if (rbBoleta.isSelected()) {
                tipoComp = "BOLETA";
                serie = "B001";
            } else if (rbFactura.isSelected()) {
                tipoComp = "FACTURA";
                serie = "F001";
            }

            String numero = String.format("%06d", (int) (System.currentTimeMillis() % 1000000));
            int idUsuario = AuthService.getUsuarioSesionActual() != null ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;

            Venta venta = new Venta(
                0,
                null,
                1,
                idUsuario,
                1,
                subtotal,
                igv,
                totalVenta,
                cmbMetodoPago.getValue(),
                recibido,
                vuelto
            );

            ComprobantePago comprobante = new ComprobantePago(0, 0, tipoComp, serie, numero, totalVenta);

            // Registro transaccional en BD y descuento de insumos
            ventaService.registrarVenta(venta, itemsVenta, comprobante);

            // Mostrar el comprobante generado
            mostrarTicketImpresion(comprobante, venta, recibido, vuelto);

            if (onVentaExitosa != null) {
                onVentaExitosa.run();
            }

            // Cerrar ventana modal
            Stage stage = (Stage) lblTotalCobro.getScene().getWindow();
            stage.close();

        } catch (NumberFormatException e) {
            mostrarAlerta("Validación", "Ingrese un monto recibido válido.", Alert.AlertType.WARNING);
        } catch (Exception e) {
            mostrarAlerta("Error al Registrar Venta", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void mostrarTicketImpresion(ComprobantePago comp, Venta venta, BigDecimal recibido, BigDecimal vuelto) {
        StringBuilder sb = new StringBuilder();
        sb.append("====================================================\n");
        sb.append("               FAST FOOD S.A.C.\n");
        sb.append("         RUC: 20608912345 - TELF: 987654321\n");
        sb.append("            Local: Av. Principal 123 - Lima\n");
        sb.append("====================================================\n");
        sb.append(comp.getTipoComprobante()).append(": ").append(comp.getNumeroCompleto()).append("\n");
        sb.append("Fecha: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append("\n");
        sb.append("Cliente: ").append(txtNombreCliente.getText().trim()).append("\n");
        sb.append("Documento: ").append(txtDocCliente.getText().trim()).append("\n");
        sb.append("----------------------------------------------------\n");
        sb.append(String.format("%-28s %4s %9s\n", "PRODUCTO", "CANT", "TOTAL"));
        sb.append("----------------------------------------------------\n");
        for (DetalleVenta dv : itemsVenta) {
            sb.append(String.format("%-28s %4d  S/ %6.2f\n", dv.getNombreProducto(), dv.getCantidad(), dv.getSubtotal()));
        }
        sb.append("----------------------------------------------------\n");
        sb.append(String.format("%-34s S/ %7.2f\n", "SUBTOTAL:", venta.getSubtotal()));
        sb.append(String.format("%-34s S/ %7.2f\n", "IGV (18%):", venta.getIgv()));
        sb.append(String.format("%-34s S/ %7.2f\n", "TOTAL A PAGAR:", venta.getTotal()));
        sb.append("----------------------------------------------------\n");
        sb.append(String.format("%-34s %s\n", "MÉTODO DE PAGO:", venta.getMetodoPago()));
        sb.append(String.format("%-34s S/ %7.2f\n", "IMPORTE RECIBIDO:", recibido));
        sb.append(String.format("%-34s S/ %7.2f\n", "VUELTO / CAMBIO:", vuelto));
        sb.append("====================================================\n");
        sb.append("           ¡GRACIAS POR SU PREFERENCIA!\n");
        sb.append("         Insumos descontados de inventario\n");
        sb.append("====================================================\n");

        Alert ticketAlert = new Alert(Alert.AlertType.INFORMATION);
        ticketAlert.setTitle("Comprobante Electrónico Emitido");
        ticketAlert.setHeaderText("Venta Exitosa #" + venta.getIdVenta() + " - " + comp.getNumeroCompleto());

        TextArea textArea = new TextArea(sb.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");
        textArea.setPrefWidth(420);
        textArea.setPrefHeight(380);

        ticketAlert.getDialogPane().setContent(textArea);
        ticketAlert.showAndWait();
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert a = new Alert(tipo);
        a.setTitle(titulo);
        a.setHeaderText(null);
        a.setContentText(mensaje);
        a.showAndWait();
    }
}
