package com.foodcore.controller;

import com.foodcore.App;
import com.foodcore.model.Producto;
import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;
import com.foodcore.util.AnimationUtils;
import com.foodcore.util.FormatoPersonalizacionUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Modal de selección de Variantes (Tamaños) y Modificadores / Extras para POS y Mozo (v4.8.0 Módulos 1 y 2).
 */
public class PersonalizacionProductoController {

    private static final Logger LOGGER = Logger.getLogger(PersonalizacionProductoController.class.getName());

    /**
     * Resultado inmutable confirmado por el cajero o mozo desde el selector.
     */
    public record ResultadoPersonalizacion(
            Producto producto,
            ProductoVariante variante,
            List<ProductoModificador> modificadores,
            int cantidad,
            String notaEspecial,
            BigDecimal precioUnitario,
            BigDecimal subtotal) {

        public ResultadoPersonalizacion {
            modificadores = modificadores != null ? List.copyOf(modificadores) : List.of();
        }
    }

    @FXML private VBox rootPersonalizacion;
    @FXML private Label lblNombrePlatillo;
    @FXML private Label lblBadgePrecioBase;
    @FXML private Label lblDescripcionPlatillo;
    @FXML private VBox vboxSeccionVariantes;
    @FXML private VBox vboxVariantes;
    @FXML private VBox vboxSeccionModificadores;
    @FXML private VBox vboxModificadores;
    @FXML private Button btnMenos;
    @FXML private Label lblCantidad;
    @FXML private Button btnMas;
    @FXML private TextField txtNotaEspecial;
    @FXML private Label lblResumenSeleccion;
    @FXML private Label lblPrecioUnitario;
    @FXML private Label lblTotalCalculado;
    @FXML private Button btnCancelar;
    @FXML private Button btnConfirmarPersonalizacion;

    private Producto producto;
    private final ToggleGroup grupoVariantes = new ToggleGroup();
    private final List<CheckBox> checkboxesModificadores = new ArrayList<>();
    private int cantidad = 1;
    private ResultadoPersonalizacion resultadoConfirmado;

    @FXML
    public void initialize() {
        AnimationUtils.attachTactileFeedback(btnMenos);
        AnimationUtils.attachTactileFeedback(btnMas);
        AnimationUtils.attachTactileFeedback(btnCancelar);
        AnimationUtils.attachTactileFeedback(btnConfirmarPersonalizacion);
        grupoVariantes.selectedToggleProperty().addListener((obs, oldVal, newVal) -> actualizarResumenYPrecios());
    }

    /**
     * Inicializa el selector con el producto y sus opciones activas de tamaños y extras.
     */
    public void initData(Producto producto,
                         List<ProductoVariante> variantes,
                         List<ProductoModificador> modificadores) {
        this.producto = producto;
        this.cantidad = 1;
        this.resultadoConfirmado = null;

        BigDecimal base = producto != null && producto.getPrecioVenta() != null
                ? producto.getPrecioVenta().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        lblNombrePlatillo.setText(producto != null ? producto.getNombre() : "Personalizar Platillo");
        lblBadgePrecioBase.setText("Base S/ " + base.toPlainString());
        String desc = producto != null && producto.getDescripcion() != null && !producto.getDescripcion().isBlank()
                ? producto.getDescripcion()
                : "Elige el tamaño y extras deseados para cocina";
        lblDescripcionPlatillo.setText(desc);

        renderizarVariantes(variantes != null ? variantes : List.of(), base);
        renderizarModificadores(modificadores != null ? modificadores : List.of());

        lblCantidad.setText(String.valueOf(cantidad));
        actualizarResumenYPrecios();
    }

    private void renderizarVariantes(List<ProductoVariante> variantes, BigDecimal precioBase) {
        vboxVariantes.getChildren().clear();
        grupoVariantes.getToggles().clear();

        boolean tieneVariantes = !variantes.isEmpty();
        vboxSeccionVariantes.setVisible(tieneVariantes);
        vboxSeccionVariantes.setManaged(tieneVariantes);

        for (int i = 0; i < variantes.size(); i++) {
            ProductoVariante var = variantes.get(i);
            HBox fila = new HBox(10.0);
            fila.setAlignment(Pos.CENTER_LEFT);
            fila.setPadding(new Insets(8, 10, 8, 10));
            fila.getStyleClass().add("surface-card-subtle");
            fila.setStyle("-fx-cursor: hand; -fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-background-radius: 8;");

            RadioButton rb = new RadioButton(var.getNombreVariante());
            rb.setId("rbVariante_" + var.getIdVariante());
            rb.setUserData(var);
            rb.setToggleGroup(grupoVariantes);
            rb.getStyleClass().add("text-body-strong");
            if (i == 0) {
                rb.setSelected(true);
            }

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label lblPrecioVar = new Label(var.formatearEtiquetaPrecio(precioBase));
            lblPrecioVar.getStyleClass().add("text-mono-accent");

            fila.getChildren().addAll(rb, spacer, lblPrecioVar);
            fila.setOnMouseClicked(ev -> rb.setSelected(true));
            vboxVariantes.getChildren().add(fila);
        }
    }

    private void renderizarModificadores(List<ProductoModificador> modificadores) {
        vboxModificadores.getChildren().clear();
        checkboxesModificadores.clear();

        boolean tieneModificadores = !modificadores.isEmpty();
        vboxSeccionModificadores.setVisible(tieneModificadores);
        vboxSeccionModificadores.setManaged(tieneModificadores);

        for (ProductoModificador mod : modificadores) {
            HBox fila = new HBox(10.0);
            fila.setAlignment(Pos.CENTER_LEFT);
            fila.setPadding(new Insets(7, 10, 7, 10));
            fila.getStyleClass().add("surface-card-subtle");
            fila.setStyle("-fx-cursor: hand; -fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-background-radius: 8;");

            CheckBox chk = new CheckBox(mod.getNombre());
            chk.setId("chkMod_" + mod.getIdModificador());
            chk.setUserData(mod);
            chk.getStyleClass().add("text-body-strong");
            chk.selectedProperty().addListener((obs, oldVal, newVal) -> actualizarResumenYPrecios());

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            BigDecimal precioMod = mod.getPrecioNormalizado();
            Label lblExtra = new Label(precioMod.compareTo(BigDecimal.ZERO) > 0
                    ? "+S/ " + precioMod.toPlainString()
                    : "S/ 0.00");
            lblExtra.getStyleClass().add(precioMod.compareTo(BigDecimal.ZERO) > 0 ? "text-mono-accent" : "text-caption-muted");

            fila.getChildren().addAll(chk, spacer, lblExtra);
            fila.setOnMouseClicked(ev -> {
                if (ev.getTarget() != chk) {
                    chk.setSelected(!chk.isSelected());
                }
            });
            checkboxesModificadores.add(chk);
            vboxModificadores.getChildren().add(fila);
        }
    }

    public ProductoVariante getVarianteSeleccionada() {
        Toggle sel = grupoVariantes.getSelectedToggle();
        if (sel != null && sel.getUserData() instanceof ProductoVariante pv) {
            return pv;
        }
        return null;
    }

    public List<ProductoModificador> getModificadoresSeleccionados() {
        List<ProductoModificador> seleccionados = new ArrayList<>();
        for (CheckBox chk : checkboxesModificadores) {
            if (chk.isSelected() && chk.getUserData() instanceof ProductoModificador pm) {
                seleccionados.add(pm);
            }
        }
        return seleccionados;
    }

    public void actualizarResumenYPrecios() {
        ProductoVariante variante = getVarianteSeleccionada();
        List<ProductoModificador> mods = getModificadoresSeleccionados();
        BigDecimal unitario = FormatoPersonalizacionUtil.calcularPrecioUnitario(producto, variante, mods);
        BigDecimal total = unitario.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);

        String textoMods = FormatoPersonalizacionUtil.formatearTextoModificadores(mods);
        String resumen = FormatoPersonalizacionUtil.formatearDescripcionCompleta(
                producto != null ? producto.getNombre() : "Producto",
                variante != null ? variante.getNombreVariante() : null,
                textoMods
        );

        if (lblResumenSeleccion != null) {
            lblResumenSeleccion.setText(cantidad + "x " + resumen);
        }
        if (lblPrecioUnitario != null) {
            lblPrecioUnitario.setText("P. Unitario: S/ " + unitario.toPlainString());
        }
        if (lblTotalCalculado != null) {
            lblTotalCalculado.setText("S/ " + total.toPlainString());
        }
        if (btnConfirmarPersonalizacion != null) {
            btnConfirmarPersonalizacion.setText("Agregar al Pedido · S/ " + total.toPlainString());
        }
    }

    @FXML
    public void handleAumentarCantidad(ActionEvent event) {
        cantidad++;
        lblCantidad.setText(String.valueOf(cantidad));
        actualizarResumenYPrecios();
    }

    @FXML
    public void handleDisminuirCantidad(ActionEvent event) {
        if (cantidad > 1) {
            cantidad--;
            lblCantidad.setText(String.valueOf(cantidad));
            actualizarResumenYPrecios();
        }
    }

    @FXML
    public void handleConfirmar(ActionEvent event) {
        this.resultadoConfirmado = construirResultadoActual();
        cerrarModal();
    }

    @FXML
    public void handleCancelar(ActionEvent event) {
        this.resultadoConfirmado = null;
        cerrarModal();
    }

    public ResultadoPersonalizacion construirResultadoActual() {
        ProductoVariante var = getVarianteSeleccionada();
        List<ProductoModificador> mods = getModificadoresSeleccionados();
        BigDecimal unitario = FormatoPersonalizacionUtil.calcularPrecioUnitario(producto, var, mods);
        BigDecimal subtotal = unitario.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
        String nota = txtNotaEspecial != null && txtNotaEspecial.getText() != null
                ? txtNotaEspecial.getText().trim()
                : "";
        return new ResultadoPersonalizacion(producto, var, mods, cantidad, nota, unitario, subtotal);
    }

    private void cerrarModal() {
        if (rootPersonalizacion != null && rootPersonalizacion.getScene() != null
                && rootPersonalizacion.getScene().getWindow() instanceof Stage stage) {
            stage.close();
        }
    }

    public Optional<ResultadoPersonalizacion> getResultadoConfirmado() {
        return Optional.ofNullable(resultadoConfirmado);
    }

    /**
     * Abre el diálogo modal de personalización para el producto dado y espera la selección del usuario.
     */
    public static Optional<ResultadoPersonalizacion> mostrarModal(Window owner,
                                                                  Producto producto,
                                                                  List<ProductoVariante> variantes,
                                                                  List<ProductoModificador> modificadores) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    PersonalizacionProductoController.class.getResource("/com/foodcore/view/PersonalizacionProductoView.fxml"));
            Parent root = loader.load();
            PersonalizacionProductoController ctrl = loader.getController();
            ctrl.initData(producto, variantes, modificadores);

            Stage stage = new Stage();
            if (owner != null) {
                stage.initOwner(owner);
            }
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Personalizar · " + (producto != null ? producto.getNombre() : "Producto"));
            Scene scene = new Scene(root);
            App.applyStylesheets(scene);
            stage.setScene(scene);
            stage.setResizable(false);
            stage.sizeToScene();
            stage.showAndWait();
            return ctrl.getResultadoConfirmado();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "No se pudo abrir el selector de variantes/modificadores", e);
            return Optional.empty();
        }
    }
}
