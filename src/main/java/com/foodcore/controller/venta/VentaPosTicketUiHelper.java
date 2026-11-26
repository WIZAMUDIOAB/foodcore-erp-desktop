package com.foodcore.controller.venta;

import com.foodcore.model.Cliente;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Usuario;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.SesionUsuario;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConfiguracionFidelizacion;
import com.foodcore.util.FormatoPersonalizacionUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;

/**
 * Helper de renderizado y configuración visual del ticket y panel de fidelización en POS.
 */
public final class VentaPosTicketUiHelper {

    private VentaPosTicketUiHelper() {
    }

    public static void configurarTablaDetalle(
            TableView<DetalleVenta> tblDetalle,
            TableColumn<DetalleVenta, String> colProducto,
            TableColumn<DetalleVenta, Integer> colCantidad,
            TableColumn<DetalleVenta, BigDecimal> colPrecio,
            TableColumn<DetalleVenta, BigDecimal> colSubtotal,
            ObservableList<DetalleVenta> itemsVenta,
            Consumer<DetalleVenta> onDoubleClick) {
        if (colProducto != null) {
            colProducto.setCellValueFactory(c -> {
                String nom = FormatoPersonalizacionUtil.formatearDescripcionItem(c.getValue());
                if (c.getValue().getNotasPreparacion() != null && !c.getValue().getNotasPreparacion().isBlank()) {
                    nom += " [Nota: " + c.getValue().getNotasPreparacion().trim() + "]";
                }
                return new SimpleStringProperty(nom);
            });
        }
        if (colCantidad != null) {
            colCantidad.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCantidad()));
            colCantidad.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(String.valueOf(item));
                        agregarEstiloSiFalta(this, "text-mono-sm");
                    }
                }
            });
        }
        if (colPrecio != null) {
            colPrecio.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrecioUnitario()));
            colPrecio.setCellFactory(col -> crearCeldaMoneda("text-mono-sm"));
        }
        if (colSubtotal != null) {
            colSubtotal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getSubtotal()));
            colSubtotal.setCellFactory(col -> crearCeldaMoneda("text-mono"));
        }
        if (tblDetalle != null) {
            tblDetalle.setItems(itemsVenta);
            tblDetalle.setRowFactory(tv -> {
                TableRow<DetalleVenta> row = new TableRow<>();
                row.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && !row.isEmpty() && onDoubleClick != null) {
                        onDoubleClick.accept(row.getItem());
                    }
                });
                return row;
            });
        }
    }

    private static TableCell<DetalleVenta, BigDecimal> crearCeldaMoneda(String styleClass) {
        return new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText("S/ " + item);
                    agregarEstiloSiFalta(this, styleClass);
                }
            }
        };
    }

    private static void agregarEstiloSiFalta(TableCell<DetalleVenta, ?> cell, String styleClass) {
        if (!cell.getStyleClass().contains(styleClass)) {
            cell.getStyleClass().add(styleClass);
        }
    }

    public static void actualizarHeaderUsuario(Label lblMozoHeader, Usuario usr) {
        if (usr == null || lblMozoHeader == null) {
            return;
        }
        String nombre = usr.getNombreEmpleado();
        if (nombre == null || nombre.isBlank()) {
            nombre = usr.getUsername();
        }
        lblMozoHeader.setText("Mozo/Cajero: " + nombre + " · Terminal POS-01");
    }

    public static void aplicarRestriccionesRolPos(
            Usuario usr,
            VentaCobroCoordinator cobroCoordinator,
            Button btnEfectivo,
            Button btnTarjeta,
            Button btnYape,
            Button btnDividirCuenta,
            Button btnDescuento,
            Button btnAnularVenta) {
        if (usr == null) {
            return;
        }
        String rol = usr.getNombreRol() != null ? usr.getNombreRol().toUpperCase(Locale.ROOT) : "";
        if (rol.contains("MOZO")) {
            ocultarBoton(btnEfectivo);
            ocultarBoton(btnTarjeta);
            ocultarBoton(btnYape);
            ocultarBoton(btnDividirCuenta);
            ocultarBoton(btnDescuento);
            ocultarBoton(btnAnularVenta);
        } else if (rol.contains("CAJERO")) {
            SesionUsuario sesion = cobroCoordinator.crearSesionActualPOS(usr);
            if (btnDescuento != null && !cobroCoordinator.tienePermiso(sesion, Permiso.VENTA_DESCUENTO)) {
                btnDescuento.setDisable(true);
            }
            if (btnAnularVenta != null) {
                btnAnularVenta.setDisable(false);
                if (!cobroCoordinator.tienePermiso(sesion, Permiso.VENTA_ANULAR)) {
                    btnAnularVenta.setTooltip(new Tooltip(
                            "Modo Supervisor: requiere PIN de SUPERVISOR/ADMIN para anular"));
                }
            }
        }
    }

    private static void ocultarBoton(Button btn) {
        if (btn != null) {
            btn.setVisible(false);
            btn.setManaged(false);
        }
    }

    public static void actualizarTotalesUi(
            VentaCarritoHandler.ResumenTotalesVenta totales,
            BigDecimal descuentoActivo,
            BigDecimal descuentoPuntosActivo,
            Label lblSubtotal,
            Label lblIgv,
            Label lblTotal,
            HBox rowDescuento,
            Label lblDescuento,
            HBox rowDescuentoPuntos,
            Label lblDescuentoPuntos) {
        if (lblSubtotal != null) {
            lblSubtotal.setText("S/ " + totales.subtotalGravado());
        }
        if (lblIgv != null) {
            lblIgv.setText("S/ " + totales.igv());
        }
        if (lblTotal != null) {
            lblTotal.setText("S/ " + totales.totalNeto());
        }
        if (rowDescuento != null && lblDescuento != null) {
            rowDescuento.setVisible(totales.hayDescuento());
            rowDescuento.setManaged(totales.hayDescuento());
            if (totales.hayDescuento()) {
                lblDescuento.setText("-S/ " + descuentoActivo.setScale(2, RoundingMode.HALF_UP));
            }
        }
        if (rowDescuentoPuntos != null && lblDescuentoPuntos != null) {
            rowDescuentoPuntos.setVisible(totales.hayDescuentoPuntos());
            rowDescuentoPuntos.setManaged(totales.hayDescuentoPuntos());
            if (totales.hayDescuentoPuntos()) {
                lblDescuentoPuntos.setText("-S/ " + descuentoPuntosActivo.setScale(2, RoundingMode.HALF_UP));
            }
        }
    }

    public static void actualizarPanelClienteUi(
            Cliente cli,
            TextField txtBuscarClientePos,
            HBox rowClienteAsociadoPos,
            Button btnLimpiarClientePos,
            Label lblClientePosNombre,
            Label lblClientePosPuntos,
            Button btnCanjearPuntosPos) {
        if (txtBuscarClientePos != null) {
            txtBuscarClientePos.setText(cli.getNumeroDocumento());
        }
        if (rowClienteAsociadoPos != null) {
            rowClienteAsociadoPos.setVisible(true);
            rowClienteAsociadoPos.setManaged(true);
        }
        if (btnLimpiarClientePos != null) {
            btnLimpiarClientePos.setVisible(true);
            btnLimpiarClientePos.setManaged(true);
        }
        if (lblClientePosNombre != null) {
            lblClientePosNombre.setText(cli.getRazonSocial() + " · "
                    + cli.getTipoDocumento() + " " + cli.getNumeroDocumento());
        }
        int pts = cli.getPuntosAcumulados();
        if (lblClientePosPuntos != null) {
            lblClientePosPuntos.setText(pts + " pts");
        }
        if (btnCanjearPuntosPos != null) {
            boolean mostrarCanje = pts > 0;
            btnCanjearPuntosPos.setVisible(mostrarCanje);
            btnCanjearPuntosPos.setManaged(mostrarCanje);
            btnCanjearPuntosPos.setDisable(pts < ConfiguracionFidelizacion.getPuntosPorCanje());
            btnCanjearPuntosPos.setText("Canjear Puntos");
        }
    }

    public static void actualizarBannerCumpleanosUi(
            VentaFidelizacionCoordinator.EstadoCumpleanosPos estadoCumple,
            HBox rowAlertaCumplePos,
            Label lblAlertaCumplePos,
            Button btnAplicarCumplePos) {
        boolean esCumple = estadoCumple.esCumpleanos();
        if (rowAlertaCumplePos != null) {
            rowAlertaCumplePos.setVisible(esCumple);
            rowAlertaCumplePos.setManaged(esCumple);
        }
        if (esCumple) {
            if (lblAlertaCumplePos != null) {
                lblAlertaCumplePos.setText(estadoCumple.textoBadge());
            }
            if (btnAplicarCumplePos != null) {
                btnAplicarCumplePos.setText("Aplicar 10%");
                btnAplicarCumplePos.setDisable(false);
            }
        }
    }

    public static void marcarCumpleanosAplicadoUi(Label lblAlertaCumplePos, Button btnAplicarCumplePos) {
        if (lblAlertaCumplePos != null) {
            lblAlertaCumplePos.setText("Cumpleaños · -10% aplicado");
        }
        if (btnAplicarCumplePos != null) {
            btnAplicarCumplePos.setText("Aplicado");
            btnAplicarCumplePos.setDisable(true);
        }
    }

    public static void limpiarPanelClienteUi(
            TextField txtBuscarClientePos,
            HBox rowClienteAsociadoPos,
            Button btnLimpiarClientePos,
            Button btnCanjearPuntosPos,
            HBox rowAlertaCumplePos,
            Button btnAplicarCumplePos,
            HBox rowDescuentoPuntos) {
        if (txtBuscarClientePos != null) {
            txtBuscarClientePos.clear();
        }
        if (rowClienteAsociadoPos != null) {
            rowClienteAsociadoPos.setVisible(false);
            rowClienteAsociadoPos.setManaged(false);
        }
        if (btnLimpiarClientePos != null) {
            btnLimpiarClientePos.setVisible(false);
            btnLimpiarClientePos.setManaged(false);
        }
        if (btnCanjearPuntosPos != null) {
            btnCanjearPuntosPos.setVisible(false);
            btnCanjearPuntosPos.setManaged(false);
            btnCanjearPuntosPos.setDisable(false);
        }
        if (rowAlertaCumplePos != null) {
            rowAlertaCumplePos.setVisible(false);
            rowAlertaCumplePos.setManaged(false);
        }
        if (btnAplicarCumplePos != null) {
            btnAplicarCumplePos.setText("Aplicar 10%");
            btnAplicarCumplePos.setDisable(false);
        }
        if (rowDescuentoPuntos != null) {
            rowDescuentoPuntos.setVisible(false);
            rowDescuentoPuntos.setManaged(false);
        }
    }

    public static void actualizarEstadoBusquedaClienteUi(Button btnBuscarClientePos, boolean buscando) {
        if (btnBuscarClientePos != null) {
            btnBuscarClientePos.setDisable(buscando);
            btnBuscarClientePos.setText(buscando ? "Buscando..." : "Buscar");
        }
    }

    public record AtajosHandle(Scene escena, EventHandler<KeyEvent> filtro) {
    }

    public static AtajosHandle registrarAtajosTeclado(
            TableView<DetalleVenta> tblDetalle,
            AtajosHandle actual,
            Runnable onF5,
            Runnable onF6,
            Runnable onF7,
            Runnable onEscape) {
        desregistrarAtajosTeclado(actual);
        if (tblDetalle == null || tblDetalle.getScene() == null) {
            return null;
        }
        Scene escena = tblDetalle.getScene();
        EventHandler<KeyEvent> filter = event -> {
            if (event.getCode() == KeyCode.F5 && onF5 != null) {
                onF5.run();
                event.consume();
            } else if (event.getCode() == KeyCode.F6 && onF6 != null) {
                onF6.run();
                event.consume();
            } else if (event.getCode() == KeyCode.F7 && onF7 != null) {
                onF7.run();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE && onEscape != null) {
                onEscape.run();
                event.consume();
            }
        };
        escena.addEventFilter(KeyEvent.KEY_PRESSED, filter);
        return new AtajosHandle(escena, filter);
    }

    public static void desregistrarAtajosTeclado(AtajosHandle actual) {
        if (actual != null && actual.escena() != null && actual.filtro() != null) {
            actual.escena().removeEventFilter(KeyEvent.KEY_PRESSED, actual.filtro());
        }
    }

    public static void adjuntarFeedbackTactil(Button... botones) {
        if (botones == null) {
            return;
        }
        for (Button boton : botones) {
            AnimationUtils.attachTactileFeedback(boton);
        }
    }
}
