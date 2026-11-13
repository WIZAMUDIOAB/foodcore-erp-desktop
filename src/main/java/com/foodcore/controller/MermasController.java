package com.foodcore.controller;

import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.model.Insumo;
import com.foodcore.model.KardexMovimiento;
import com.foodcore.service.AuthService;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.UiAsyncExecutor;
import com.foodcore.util.UnidadMedidaFormatter;
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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
    @FXML private TableColumn<FilaMerma, String> colFecha;
    @FXML private TableColumn<FilaMerma, String> colInsumo;
    @FXML private TableColumn<FilaMerma, String> colCantidad;
    @FXML private TableColumn<FilaMerma, String> colSaldo;
    @FXML private TableColumn<FilaMerma, String> colMotivo;

    private final InsumoDAO insumoDAO = new InsumoDAOImpl();
    private final TransactionManager tx = new JdbcTransactionManager();
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

        cargarDatosMermas();
    }

    private record DatosMermasCarga(List<Insumo> insumos, List<FilaMerma> filas) {
    }

    public void cargarDatosMermas() {
        UiAsyncExecutor.ejecutarAsync(
            () -> {
                List<Insumo> ins = new ArrayList<>();
                List<FilaMerma> filas = new ArrayList<>();
                String sql = "SELECT k.created_at, i.nombre, i.unidad_medida, k.cantidad, k.saldo_posterior, k.observacion "
                    + "FROM kardex_movimiento k JOIN insumo i ON k.id_insumo = i.id_insumo "
                    + "WHERE k.tipo_movimiento LIKE 'MERMA%' ORDER BY k.id_kardex DESC LIMIT 100";
                try (Connection conn = ConexionBD.getConnection()) {
                    ins = insumoDAO.listarTodos(conn);
                    try (PreparedStatement ps = conn.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String fecha = String.valueOf(rs.getTimestamp("created_at")).substring(0, 16);
                            String nom = rs.getString("nombre");
                            String um = rs.getString("unidad_medida");
                            String cant = UnidadMedidaFormatter.formatear(rs.getBigDecimal("cantidad"), um, true);
                            String saldo = UnidadMedidaFormatter.formatear(rs.getBigDecimal("saldo_posterior"), um, false);
                            String obs = rs.getString("observacion");
                            filas.add(new FilaMerma(fecha, nom, cant, saldo, obs != null ? obs : "Merma de cocina"));
                        }
                    }
                } catch (java.sql.SQLException | RuntimeException e) {
                    LOGGER.log(Level.FINE, "Error cargando mermas", e);
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
        if (cantidad == null || cantidad.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cantidad de merma debe ser mayor a cero.");
        }
        String obs = (motivo != null && !motivo.isBlank()) ? motivo.trim() : "Merma operativa de cocina";
        long idUsuario = AuthService.getUsuarioSesionActual() != null
            ? AuthService.getUsuarioSesionActual().getIdUsuario() : 1L;

        return tx.executeChecked(conn -> {
            insumoDAO.registrarMovimientoInventario(conn, idInsumo, "MERMA", cantidad, obs, null);
            return insumoDAO.registrarMovimientoKardexAtomico(conn, idInsumo, "MERMA", cantidad, idUsuario, obs);
        });
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
