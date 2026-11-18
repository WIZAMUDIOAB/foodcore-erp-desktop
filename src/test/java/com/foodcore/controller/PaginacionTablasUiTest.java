package com.foodcore.controller;

import com.foodcore.controller.KardexController.FilaKardex;
import com.foodcore.model.Cliente;
import com.foodcore.service.ExportacionContableService.VentaRegistroSunat;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.UiScreenshotTestSupport;
import com.foodcore.support.UiScreenshotTestSupport.VistaCargada;
import com.foodcore.util.PaginadorTablaHelper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias y UI JavaFX para la paginación estándar de 50 registros (ÍTEM 6 v5.1.0)
 * en Reportes Contables (Ventas), Kardex y Clientes (Corporativos y Minoristas).
 */
class PaginacionTablasUiTest {

    @Test
    @DisplayName("PaginadorTablaHelper pagina bloques exactos de 50 registros y límites de navegación")
    void paginadorHelperDivideEnBloquesDe50() {
        ObservableList<Integer> vista = FXCollections.observableArrayList();
        PaginadorTablaHelper<Integer> paginador = new PaginadorTablaHelper<>(vista, null, null, null);

        List<Integer> datos = new ArrayList<>();
        for (int i = 1; i <= 125; i++) {
            datos.add(i);
        }
        paginador.setDatos(datos);

        assertEquals(3, paginador.getTotalPaginas());
        assertEquals(1, paginador.getPaginaActual());
        assertEquals("Página 1 de 3", paginador.getTextoIndicador());
        assertEquals(50, vista.size());
        assertEquals(1, vista.get(0));
        assertEquals(50, vista.get(49));

        paginador.paginaAnterior();
        assertEquals(1, paginador.getPaginaActual());

        paginador.paginaSiguiente();
        assertEquals(2, paginador.getPaginaActual());
        assertEquals("Página 2 de 3", paginador.getTextoIndicador());
        assertEquals(50, vista.size());
        assertEquals(51, vista.get(0));
        assertEquals(100, vista.get(49));

        paginador.paginaSiguiente();
        assertEquals(3, paginador.getPaginaActual());
        assertEquals("Página 3 de 3", paginador.getTextoIndicador());
        assertEquals(25, vista.size());
        assertEquals(101, vista.get(0));
        assertEquals(125, vista.get(24));

        paginador.paginaSiguiente();
        assertEquals(3, paginador.getPaginaActual());

        paginador.setDatos(List.of());
        assertEquals(1, paginador.getTotalPaginas());
        assertEquals("Página 1 de 1", paginador.getTextoIndicador());
        assertTrue(vista.isEmpty());
    }

    @Test
    @DisplayName("Ventas (ReportesContablesView) pagina 120 comprobantes en bloques de 50 y genera evidencia")
    void ventasSoportaPaginacion50Registros() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VistaCargada<ReportesContablesController> vista = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/ReportesContablesView.fxml", 1220, 780);
                List<VentaRegistroSunat> ventas = new ArrayList<>();
                for (int i = 1; i <= 120; i++) {
                    VentaRegistroSunat v = new VentaRegistroSunat();
                    v.idVenta = i;
                    v.serie = "B001";
                    v.fechaEmision = "2026-11-12";
                    v.nombreCliente = "Cliente Paginado #" + i;
                    v.subtotal = 100.0;
                    v.igv = 18.0;
                    v.total = 118.0;
                    v.medioPago = "YAPE";
                    v.estado = "1";
                    ventas.add(v);
                }

                ReportesContablesController ctrl = vista.controller();
                ctrl.cargarVentasDirectoParaPaginacion(ventas);

                @SuppressWarnings("unchecked")
                TableView<VentaRegistroSunat> tbl = (TableView<VentaRegistroSunat>) vista.root().lookup("#tblVentasSunat");
                Label lblPagina = (Label) vista.root().lookup("#lblPaginaVentas");
                Button btnPrev = (Button) vista.root().lookup("#btnAnteriorVentas");
                Button btnNext = (Button) vista.root().lookup("#btnSiguienteVentas");

                assertEquals("Página 1 de 3", lblPagina.getText());
                assertEquals(50, tbl.getItems().size());
                assertTrue(btnPrev.isDisabled());
                assertFalse(btnNext.isDisabled());

                ctrl.handlePaginaSiguiente();
                assertEquals("Página 2 de 3", lblPagina.getText());
                assertEquals(50, tbl.getItems().size());
                assertEquals(51, tbl.getItems().get(0).idVenta);
                assertFalse(btnPrev.isDisabled());
                assertFalse(btnNext.isDisabled());

                UiScreenshotTestSupport.capturarEvidencia(vista.root(), "01_paginacion_ventas_v510.png");

                ctrl.handlePaginaSiguiente();
                assertEquals("Página 3 de 3", lblPagina.getText());
                assertEquals(20, tbl.getItems().size());
                assertTrue(btnNext.isDisabled());

                ctrl.handlePaginaAnterior();
                assertEquals("Página 2 de 3", lblPagina.getText());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("KardexView pagina 115 movimientos en 3 páginas manteniendo el resumen total")
    void kardexSoportaPaginacion50Registros() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VistaCargada<KardexController> vista = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/KardexView.fxml", 1200, 760);
                List<FilaKardex> filas = new ArrayList<>();
                for (int i = 1; i <= 115; i++) {
                    filas.add(new FilaKardex(
                            "2026-11-12 10:00",
                            "SALIDA_VENTA",
                            "Insumo Demo " + i,
                            "-1 Und",
                            (200 - i) + " Und",
                            "admin"));
                }

                KardexController ctrl = vista.controller();
                ctrl.cargarMovimientosDirecto(filas);

                @SuppressWarnings("unchecked")
                TableView<FilaKardex> tbl = (TableView<FilaKardex>) vista.root().lookup("#tblKardex");
                Label lblPagina = (Label) vista.root().lookup("#lblPaginaKardex");
                Label lblResumen = (Label) vista.root().lookup("#lblResumenKardex");
                Button btnPrev = (Button) vista.root().lookup("#btnAnteriorKardex");
                Button btnNext = (Button) vista.root().lookup("#btnSiguienteKardex");

                assertEquals("Página 1 de 3", lblPagina.getText());
                assertEquals("Mostrando 115 movimientos", lblResumen.getText());
                assertEquals(50, tbl.getItems().size());
                assertTrue(btnPrev.isDisabled());
                assertFalse(btnNext.isDisabled());

                ctrl.handlePaginaSiguiente();
                assertEquals("Página 2 de 3", lblPagina.getText());
                assertEquals(50, tbl.getItems().size());
                assertEquals("Insumo Demo 51", tbl.getItems().get(0).insumo());

                ctrl.handlePaginaSiguiente();
                assertEquals("Página 3 de 3", lblPagina.getText());
                assertEquals(15, tbl.getItems().size());
                assertTrue(btnNext.isDisabled());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("ClientesView pagina tablas Corporativos y Minoristas de 65/62 clientes en bloques de 50")
    void clientesSoportaPaginacion50Registros() throws Exception {
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                VistaCargada<ClientesController> vista = UiScreenshotTestSupport.cargarVista(
                        "/com/foodcore/view/ClientesView.fxml", 1240, 780);
                List<Cliente> clientes = new ArrayList<>();
                for (int i = 1; i <= 65; i++) {
                    Cliente corp = new Cliente();
                    corp.setIdCliente(i);
                    corp.setTipoCliente("CORPORATIVO");
                    corp.setTipoDocumento("RUC");
                    corp.setNumeroDocumento(String.format("2060000%04d", i));
                    corp.setRazonSocial("Empresa Corporativa #" + i);
                    corp.setActivo(true);
                    clientes.add(corp);
                }
                for (int i = 1; i <= 62; i++) {
                    Cliente min = new Cliente();
                    min.setIdCliente(1000 + i);
                    min.setTipoCliente("MINORISTA");
                    min.setTipoDocumento("DNI");
                    min.setNumeroDocumento(String.format("7100%04d", i));
                    min.setRazonSocial("Cliente Minorista #" + i);
                    min.setActivo(true);
                    clientes.add(min);
                }

                ClientesController ctrl = vista.controller();
                ctrl.cargarClientesDirectoParaPaginacion(clientes);

                @SuppressWarnings("unchecked")
                TableView<Cliente> tblCorp = (TableView<Cliente>) vista.root().lookup("#tblClientes");
                @SuppressWarnings("unchecked")
                TableView<Cliente> tblMin = (TableView<Cliente>) vista.root().lookup("#tblClientesMinoristas");
                Label lblPagCorp = (Label) vista.root().lookup("#lblPaginaCorp");
                Label lblPagMin = (Label) vista.root().lookup("#lblPaginaMin");

                assertEquals("Página 1 de 2", lblPagCorp.getText());
                assertEquals(50, tblCorp.getItems().size());
                ctrl.handlePaginaSiguienteCorp();
                assertEquals("Página 2 de 2", lblPagCorp.getText());
                assertEquals(15, tblCorp.getItems().size());

                ctrl.seleccionarTabMinoristas();
                assertEquals("Página 1 de 2", lblPagMin.getText());
                assertEquals(50, tblMin.getItems().size());
                ctrl.handlePaginaSiguienteMin();
                assertEquals("Página 2 de 2", lblPagMin.getText());
                assertEquals(12, tblMin.getItems().size());
                assertEquals(62, ctrl.getListaMinoristas().size());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }
}
