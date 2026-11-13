package com.foodcore.controller;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.impl.ClienteDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Cliente;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Venta;
import com.foodcore.service.AuthService;
import com.foodcore.service.VentaService;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.UiAsyncExecutor;
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

    private static final Logger LOGGER = Logger.getLogger(CobroModalController.class.getName());

    @FXML private Label lblTotalCobro;
    @FXML private RadioButton rbTicket;
    @FXML private RadioButton rbBoleta;
    @FXML private RadioButton rbFactura;
    @FXML private TextField txtDocCliente;
    @FXML private TextField txtNombreCliente;
    @FXML private ComboBox<String> cmbMetodoPago;
    @FXML private TextField txtMontoRecibido;
    @FXML private Label lblVuelto;

    private Integer idPedidoAsociado = null;
    private BigDecimal totalVenta = BigDecimal.ZERO;
    private BigDecimal descuentoComercial = BigDecimal.ZERO;
    private BigDecimal descuentoPuntos = BigDecimal.ZERO;
    private int puntosACanjear = 0;
    private Cliente clienteAsociado = null;
    private List<DetalleVenta> itemsVenta;
    private Runnable onVentaExitosa;
    private final VentaService ventaService = new VentaService();
    private final ClienteDAO clienteDAO = new ClienteDAOImpl();

    @FXML
    public void initialize() {
        cmbMetodoPago.setItems(FXCollections.observableArrayList("EFECTIVO", "TARJETA", "YAPE_PLIN", "CREDITO_CORPORATIVO"));
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
        initData(total, BigDecimal.ZERO, BigDecimal.ZERO, 0, null, metodoPreseleccionado, items, onExitosa);
    }

    public void initData(BigDecimal total,
                         BigDecimal descuentoComercial,
                         BigDecimal descuentoPuntos,
                         int puntosACanjear,
                         Cliente clienteAsociado,
                         String metodoPreseleccionado,
                         List<DetalleVenta> items,
                         Runnable onExitosa) {
        this.totalVenta = total.setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO);
        this.descuentoComercial = descuentoComercial != null ? descuentoComercial : BigDecimal.ZERO;
        this.descuentoPuntos = descuentoPuntos != null ? descuentoPuntos : BigDecimal.ZERO;
        this.puntosACanjear = Math.max(0, puntosACanjear);
        this.clienteAsociado = clienteAsociado;
        this.itemsVenta = items;
        this.onVentaExitosa = onExitosa;

        lblTotalCobro.setText("S/ " + this.totalVenta);
        if (metodoPreseleccionado != null) {
            cmbMetodoPago.getSelectionModel().select(metodoPreseleccionado);
        }
        if (clienteAsociado != null) {
            if (clienteAsociado.getNumeroDocumento() != null && !clienteAsociado.getNumeroDocumento().isBlank()) {
                txtDocCliente.setText(clienteAsociado.getNumeroDocumento().trim());
                if ("RUC".equalsIgnoreCase(clienteAsociado.getTipoDocumento())) {
                    rbFactura.setSelected(true);
                } else if (clienteAsociado.getNumeroDocumento().trim().length() == 8) {
                    rbBoleta.setSelected(true);
                }
            }
            if (clienteAsociado.getRazonSocial() != null && !clienteAsociado.getRazonSocial().isBlank()) {
                txtNombreCliente.setText(clienteAsociado.getRazonSocial().trim());
            }
        }
        txtMontoRecibido.setText(this.totalVenta.toString());
        handleCalcularVuelto();
    }

    public void setIdPedidoAsociado(Integer idPedidoAsociado) {
        this.idPedidoAsociado = idPedidoAsociado;
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
            BigDecimal vuelto = recibido.subtract(totalVenta).setScale(2, RoundingMode.HALF_UP);

            lblVuelto.setText("S/ " + vuelto);
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
            String metodo = cmbMetodoPago.getValue();
            BigDecimal recibido = new BigDecimal(txtMontoRecibido.getText().trim())
                .setScale(DesgloseFiscal.ESCALA_MONETARIA, DesgloseFiscal.MODO_REDONDEO);

            // Desglose e invariantes mediante Value Object de Dominio (IGV se calcula antes del canje de puntos)
            BigDecimal baseAntesCanje = totalVenta.add(descuentoPuntos != null ? descuentoPuntos : BigDecimal.ZERO);
            DesgloseFiscal desgloseFiscal = DesgloseFiscal.desdeTotalConImpuestos(baseAntesCanje);
            DesgloseFiscal desgloseCobro = totalVenta.compareTo(BigDecimal.ZERO) > 0
                    ? DesgloseFiscal.desdeTotalConImpuestos(totalVenta)
                    : desgloseFiscal;
            BigDecimal vuelto = totalVenta.compareTo(BigDecimal.ZERO) > 0
                    ? desgloseCobro.calcularVuelto(recibido, metodo)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            String tipoComp = "TICKET";
            String serie = "TCK";
            if (rbBoleta.isSelected()) {
                tipoComp = "BOLETA";
                serie = "B001";
            } else if (rbFactura.isSelected()) {
                tipoComp = "FACTURA";
                serie = "F001";
            }

            // Validar requisitos tributarios SUNAT (RUC 11 dígitos en Factura, DNI 8 en Boleta)
            DesgloseFiscal.validarRequisitosComprobante(tipoComp, txtDocCliente.getText(), txtNombreCliente.getText());

            String numero = String.format("%06d", (int) (System.currentTimeMillis() % 1000000));
            int idUsuario = AuthService.getUsuarioSesionActual() != null ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1;
            Integer idClienteVenta = clienteAsociado != null && clienteAsociado.getIdCliente() > 0
                    ? clienteAsociado.getIdCliente()
                    : null;

            Venta venta = new Venta(
                0,
                idPedidoAsociado,
                1,
                idUsuario,
                idClienteVenta,
                desgloseFiscal.getSubtotalGravado(),
                desgloseFiscal.getIgv(),
                totalVenta,
                metodo,
                recibido,
                vuelto
            );
            venta.setDescuento(descuentoComercial);
            venta.setDescuentoPuntos(descuentoPuntos);
            venta.setPuntosACanjear(puntosACanjear);

            ComprobantePago comprobante = new ComprobantePago(0, 0, tipoComp, serie, numero, totalVenta);

            // Registro transaccional ACID en BD, descuento de insumos, puntos CRM y cierre de comanda si aplica
            final String serieFinal = serie;
            final String docCliente = txtDocCliente.getText() != null ? txtDocCliente.getText().trim() : "";
            final String nombreCliente = txtNombreCliente.getText() != null ? txtNombreCliente.getText().trim() : "";
            final List<DetalleVenta> itemsCopia = new java.util.ArrayList<>(itemsVenta);
            final javafx.scene.Node triggerNode = event != null && event.getSource() instanceof javafx.scene.Node n ? n : null;
            if (triggerNode != null) {
                triggerNode.setDisable(true);
            }

            UiAsyncExecutor.ejecutarVoidAsync(
                () -> {
                    if (venta.getIdCliente() == null && !docCliente.isBlank() && !"00000000".equals(docCliente)) {
                        try (java.sql.Connection connCli = ConexionBD.getConnection()) {
                            java.util.Optional<Cliente> optCli = clienteDAO.buscarPorDniOTelefono(connCli, docCliente);
                            optCli.ifPresent(c -> venta.setIdCliente(c.getIdCliente()));
                        } catch (java.sql.SQLException ignored) {
                        }
                    }
                    ventaService.registrarVenta(venta, itemsCopia, comprobante);

                    if ("CREDITO_CORPORATIVO".equalsIgnoreCase(metodo)) {
                        try (java.sql.Connection conn = ConexionBD.getConnection()) {
                            com.foodcore.dao.CuentaPorCobrarDAO cxcDAO = new com.foodcore.dao.impl.CuentaPorCobrarDAOImpl();
                            java.util.Optional<Cliente> cliOpt = clienteDAO.buscarPorDocumento(conn, docCliente);
                            int idCli = cliOpt.map(Cliente::getIdCliente).orElse(idClienteVenta != null ? idClienteVenta : 1);
                            com.foodcore.model.CuentaPorCobrar cxc = new com.foodcore.model.CuentaPorCobrar();
                            cxc.setIdCliente(idCli);
                            cxc.setIdVenta(venta.getIdVenta());
                            cxc.setNumeroComprobante(serieFinal + "-" + numero);
                            cxc.setFechaEmision(java.time.LocalDate.now());
                            cxc.setFechaVencimiento(java.time.LocalDate.now().plusDays(30));
                            cxc.setMontoTotal(totalVenta.doubleValue());
                            cxc.setMontoCobrado(0.0);
                            cxc.setEstado("PENDIENTE");
                            cxc.setObservaciones("Venta POS a credito corporativo. Cliente: " + nombreCliente);
                            cxcDAO.insertar(conn, cxc);
                        } catch (java.sql.SQLException | RuntimeException ex) {
                            LOGGER.log(Level.WARNING, "Aviso al vincular credito corporativo", ex);
                        }
                    }
                },
                () -> {
                    if (triggerNode != null) {
                        triggerNode.setDisable(false);
                    }
                    mostrarTicketImpresion(comprobante, venta, recibido, vuelto);
                    if (onVentaExitosa != null) {
                        onVentaExitosa.run();
                    }
                    Stage stage = (Stage) lblTotalCobro.getScene().getWindow();
                    stage.close();
                },
                error -> {
                    if (triggerNode != null) {
                        triggerNode.setDisable(false);
                    }
                    if (error instanceof NegocioException) {
                        mostrarAlerta("Validación de Negocio / Fiscal", error.getMessage(), Alert.AlertType.WARNING);
                    } else {
                        mostrarAlerta("Error al Registrar Venta", error != null ? error.getMessage() : "desconocido", Alert.AlertType.ERROR);
                    }
                }
            );

        } catch (NumberFormatException e) {
            mostrarAlerta("Validación", "Ingrese un monto recibido numérico válido.", Alert.AlertType.WARNING);
        } catch (NegocioException ne) {
            mostrarAlerta("Validación de Negocio / Fiscal", ne.getMessage(), Alert.AlertType.WARNING);
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
        sb.append(String.format("%-28s %4s %9s%n", "PRODUCTO", "CANT", "TOTAL"));
        sb.append("----------------------------------------------------\n");
        for (DetalleVenta dv : itemsVenta) {
            sb.append(String.format("%-28s %4d  S/ %6.2f%n", dv.getNombreProducto(), dv.getCantidad(), dv.getSubtotal()));
        }
        sb.append("----------------------------------------------------\n");
        sb.append(String.format("%-34s S/ %7.2f%n", "SUBTOTAL:", venta.getSubtotal()));
        sb.append(String.format("%-34s S/ %7.2f%n", "IGV (18%):", venta.getIgv()));
        if (venta.getDescuento() != null && venta.getDescuento().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format("%-34s -S/ %6.2f%n", "DESC. APLICADO:", venta.getDescuento()));
        }
        if (venta.getDescuentoPuntos() != null && venta.getDescuentoPuntos().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format("%-34s -S/ %6.2f%n", "CANJE PUNTOS (SIN IGV):", venta.getDescuentoPuntos()));
        }
        sb.append(String.format("%-34s S/ %7.2f%n", "TOTAL A PAGAR:", venta.getTotal()));
        sb.append("----------------------------------------------------\n");
        sb.append(String.format("%-34s %s%n", "MÉTODO DE PAGO:", venta.getMetodoPago()));
        sb.append(String.format("%-34s S/ %7.2f%n", "IMPORTE RECIBIDO:", recibido));
        sb.append(String.format("%-34s S/ %7.2f%n", "VUELTO / CAMBIO:", vuelto));
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
