package com.foodcore.controller;

import com.foodcore.model.Insumo;
import com.foodcore.model.KardexMovimiento;
import com.foodcore.service.AnalisisMermasService;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.EmptyStateHelper;
import com.foodcore.util.UiAsyncExecutor;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador de Mermas e Inventario según SPEC-FOODCORE-v1.2 (§3.3):
 * Descuenta stock atómicamente y genera un movimiento {@code MERMA} en el Kardex.
 */
public class MermasController {

    private static final Logger LOGGER = Logger.getLogger(MermasController.class.getName());

    public record FilaMerma(String fecha, String insumo, String cantidad, String saldo, String motivo) {
    }

    @FXML private StackPane rootMermasStack;
    @FXML private ComboBox<Insumo> cmbInsumoMerma;
    @FXML private TextField txtCantidadMerma;
    @FXML private TextField txtMotivoMerma;
    @FXML private Button btnRegistrarMerma;

    @FXML private TableView<FilaMerma> tblMermas;
    @FXML private VBox emptyState;
    @FXML private TableColumn<FilaMerma, String> colFecha;
    @FXML private TableColumn<FilaMerma, String> colInsumo;
    @FXML private TableColumn<FilaMerma, String> colCantidad;
    @FXML private TableColumn<FilaMerma, String> colSaldo;
    @FXML private TableColumn<FilaMerma, String> colMotivo;

    private final AnalisisMermasService mermasService = new AnalisisMermasService();
    private final ObservableList<Insumo> listaInsumos = FXCollections.observableArrayList();
    private final ObservableList<FilaMerma> listaMermas = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnRegistrarMerma);
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().fecha()));
        colInsumo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().insumo()));
        colCantidad.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().cantidad()));
        colSaldo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().saldo()));
        colMotivo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().motivo()));

        cmbInsumoMerma.setItems(listaInsumos);
        tblMermas.setItems(listaMermas);
        EmptyStateHelper.aplicarPlaceholder(tblMermas);
        EmptyStateHelper.vincularATabla(tblMermas, emptyState);

        cargarDatosMermas();
    }

    private record DatosMermasCarga(List<Insumo> insumos, List<FilaMerma> filas) {
    }

    public void cargarDatosMermas() {
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                AnalisisMermasService.DatosMermasCarga datos = mermasService.consultarDatosMermas();
                List<Insumo> ins = new ArrayList<>(datos.insumos());
                List<FilaMerma> filas = new ArrayList<>();
                for (AnalisisMermasService.FilaMermaRegistro f : datos.filas()) {
                    filas.add(new FilaMerma(f.fecha(), f.insumo(), f.cantidad(), f.saldo(), f.motivo()));
                }
                return new DatosMermasCarga(ins, filas);
            },
            datos -> {
                listaInsumos.setAll(datos.insumos());
                if (!listaInsumos.isEmpty() && cmbInsumoMerma.getValue() == null) {
                    cmbInsumoMerma.getSelectionModel().selectFirst();
                }
                listaMermas.setAll(datos.filas());
            },
            e -> LOGGER.log(Level.FINE, "Error cargando mermas", e)
        );
    }

    /**
     * Registra una merma transaccional descontando el stock del insumo y creando el movimiento {@code MERMA} en el Kardex (§3.3).
     */
    public KardexMovimiento registrarMermaTransaccional(long idInsumo, BigDecimal cantidad, String motivo) throws Exception {
        return mermasService.registrarMermaKardexDirecto(idInsumo, cantidad, motivo);
    }

    @FXML
    public void handleRegistrarMerma(ActionEvent event) {
        Insumo ins = cmbInsumoMerma.getValue();
        if (ins == null) {
            return;
        }
        try {
            BigDecimal cant = new BigDecimal(txtCantidadMerma.getText().trim());
            String motivo = txtMotivoMerma.getText();
            registrarMermaTransaccional(ins.getIdInsumo(), cant, motivo);
            txtCantidadMerma.clear();
            txtMotivoMerma.clear();
            AnimationUtils.mostrarToast(rootMermasStack, "Merma registrada y descontada en Kardex.", false);
            cargarDatosMermas();
        } catch (Exception e) {
            AnimationUtils.mostrarToast(rootMermasStack, "Error al registrar merma: " + e.getMessage(), true);
        }
    }
}
