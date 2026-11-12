package com.foodcore.service;

import atlantafx.base.theme.PrimerLight;
import com.foodcore.App;
import com.foodcore.controller.MenuController;
import com.foodcore.dao.CategoriaDAO;
import com.foodcore.dao.CategoriaDAOImpl;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.model.Categoria;
import com.foodcore.model.Insumo;
import com.foodcore.model.Producto;
import com.foodcore.service.ImportacionMasivaService.ResultadoImportacion;
import com.foodcore.support.JavaFxTestSupport;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ExportFileChooserHelper;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de integración MySQL para Fase 5 (SPEC-FOODCORE-v4.6.0):
 * Upsert de Menú e Insumos por Codigo_SKU, creación automática de categorías,
 * conteo exacto de creados/actualizados/errores y ejecución UI asíncrona en MenuController.
 */
class ImportacionMasivaIT {

    private final ImportacionMasivaService service = new ImportacionMasivaService();
    private final ProductoDAO productoDAO = new ProductoDAOImpl();
    private final CategoriaDAO categoriaDAO = new CategoriaDAOImpl();
    private final InsumoDAO insumoDAO = new InsumoDAOImpl();

    private String prevImportDir;

    @BeforeAll
    static void setUpClass() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        JavaFxTestSupport.iniciarToolkit();
    }

    @BeforeEach
    void prepararYLimpiar() throws Exception {
        Preferences prefs = Preferences.userNodeForPackage(ExportFileChooserHelper.class);
        prevImportDir = prefs.get(ExportFileChooserHelper.PREF_KEY_ULTIMA_CARPETA_IMPORT, null);
        ExportFileChooserHelper.clearTestSelectorOverride();
        limpiarRegistrosPrueba();
    }

    @AfterEach
    void restaurarEstadoYLimpiar() throws Exception {
        ExportFileChooserHelper.clearTestSelectorOverride();
        Preferences prefs = Preferences.userNodeForPackage(ExportFileChooserHelper.class);
        if (prevImportDir == null) {
            prefs.remove(ExportFileChooserHelper.PREF_KEY_ULTIMA_CARPETA_IMPORT);
        } else {
            prefs.put(ExportFileChooserHelper.PREF_KEY_ULTIMA_CARPETA_IMPORT, prevImportDir);
        }
        limpiarRegistrosPrueba();
    }

    private void limpiarRegistrosPrueba() throws Exception {
        try (Connection conn = ConexionBD.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM producto WHERE codigo LIKE 'T5-%'")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM insumo WHERE codigo LIKE 'T5INS-%'")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM categoria WHERE nombre IN ('CatFase5Nueva', 'CatFase5Actualizada')")) {
                ps.executeUpdate();
            }
        }
    }

    @Test
    @DisplayName("5.4 Upsert de Menú crea categorías faltantes, inserta nuevos SKUs y actualiza existentes en MySQL")
    void debeHacerUpsertMenuYCrearCategoriasFaltantes(@TempDir Path tempDir) throws Exception {
        File excel1 = crearExcelMenu(
                tempDir.resolve("menu_lote1.xlsx").toFile(),
                List.of(
                        new Object[]{"T5-001", "Smash Burger v1", "CatFase5Nueva", 22.00, "Desc 1", "Activo"},
                        new Object[]{"T5-002", "Limonada Fase5", "CatFase5Nueva", 8.50, "Desc 2", "Activo"},
                        new Object[]{"T5-001", "Duplicado en archivo", "CatFase5Nueva", 19.00, "Dup", "Activo"},
                        new Object[]{"T5-ERR", "Precio Negativo", "CatFase5Nueva", -1.50, "Err", "Activo"}
                ));

        ResultadoImportacion r1 = service.importarMenu(excel1);
        assertEquals(2, r1.creados());
        assertEquals(0, r1.actualizados());
        assertEquals(2, r1.errores());
        assertEquals("Importación completada: 2 productos creados, 0 actualizados, 2 errores.",
                r1.construirMensajeResumen());

        try (Connection conn = ConexionBD.getConnection()) {
            Producto p1 = productoDAO.buscarPorCodigo(conn, "T5-001");
            assertNotNull(p1);
            assertEquals("Smash Burger v1", p1.getNombre());
            assertEquals(new BigDecimal("22.00"), p1.getPrecioVenta());
            assertTrue(p1.isDisponible());
            assertEquals("CatFase5Nueva", p1.getNombreCategoria());

            List<Categoria> cats = categoriaDAO.listarTodas(conn);
            assertTrue(cats.stream().anyMatch(c -> "CatFase5Nueva".equals(c.getNombre())));
        }

        // Segundo archivo: actualiza T5-001 (precio, nueva categoría y estado Inactivo) y crea T5-003
        File excel2 = crearExcelMenu(
                tempDir.resolve("menu_lote2.xlsx").toFile(),
                List.of(
                        new Object[]{"T5-001", "Smash Burger v2", "CatFase5Actualizada", 29.90, "Desc v2", "Inactivo"},
                        new Object[]{"T5-003", "Te Helado Fase5", "CatFase5Actualizada", 7.00, "Desc 3", "Activo"}
                ));

        ResultadoImportacion r2 = service.importarMenu(excel2);
        assertEquals(1, r2.creados());
        assertEquals(1, r2.actualizados());
        assertEquals(0, r2.errores());

        try (Connection conn = ConexionBD.getConnection()) {
            Producto p1Upd = productoDAO.buscarPorCodigo(conn, "T5-001");
            assertNotNull(p1Upd);
            assertEquals("Smash Burger v2", p1Upd.getNombre());
            assertEquals(new BigDecimal("29.90"), p1Upd.getPrecioVenta());
            assertFalse(p1Upd.isDisponible());
            assertEquals("CatFase5Actualizada", p1Upd.getNombreCategoria());
        }
    }

    @Test
    @DisplayName("5.4 Upsert de Insumos inserta, actualiza por Codigo_SKU y reporta errores en MySQL")
    void debeHacerUpsertInsumosEnMySql(@TempDir Path tempDir) throws Exception {
        File excel1 = crearExcelInsumos(
                tempDir.resolve("insumos_lote1.xlsx").toFile(),
                List.of(
                        new Object[]{"T5INS-001", "Queso Gouda", "KG", 12.50, 3.00, 28.00},
                        new Object[]{"T5INS-002", "Servilletas", "UND", 500, 100, 0.05},
                        new Object[]{"T5INS-ERR", "Stock Negativo", "LT", -5.00, 1.00, 10.00}
                ));

        ResultadoImportacion r1 = service.importarInsumos(excel1);
        assertEquals(2, r1.creados());
        assertEquals(0, r1.actualizados());
        assertEquals(1, r1.errores());
        assertEquals("Importación completada: 2 insumos creados, 0 actualizados, 1 errores.",
                r1.construirMensajeResumen());

        File excel2 = crearExcelInsumos(
                tempDir.resolve("insumos_lote2.xlsx").toFile(),
                List.of(
                        new Object[]{"T5INS-001", "Queso Gouda Premium", "KG", 30.00, 5.00, 31.50},
                        new Object[]{"T5INS-003", "Salsa Especial", "LT", 18.00, 4.00, 14.00}
                ));

        ResultadoImportacion r2 = service.importarInsumos(excel2);
        assertEquals(1, r2.creados());
        assertEquals(1, r2.actualizados());
        assertEquals(0, r2.errores());

        try (Connection conn = ConexionBD.getConnection()) {
            Insumo ins1 = insumoDAO.buscarPorCodigo(conn, "T5INS-001");
            assertNotNull(ins1);
            assertEquals("Queso Gouda Premium", ins1.getNombre());
            assertEquals(new BigDecimal("30.0000"), ins1.getStockActualDisponible());
            assertEquals(new BigDecimal("31.5000"), ins1.getCostoUnitario());
        }
    }

    @Test
    @DisplayName("5.3 MenuController ejecuta importación asíncrona con FileChooser, ProgressIndicator y resumen final")
    void debeEjecutarImportacionAsincronaDesdeMenuController(@TempDir Path tempDir) throws Exception {
        File excel = crearExcelMenu(
                tempDir.resolve("ui_menu_import.xlsx").toFile(),
                List.<Object[]>of(
                        new Object[]{"T5-UI-01", "UI Import Platillo", "CatFase5Nueva", 16.50, "Test UI", "Activo"}
                ));

        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<MenuController> ctrlRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThreadAndWait(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                App.cargarFuentesSistema();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/foodcore/view/MenuView.fxml"));
                Parent root = loader.load();
                new Scene(root, 1240, 780);
                rootRef.set(root);
                ctrlRef.set(loader.getController());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        try {
            ExportFileChooserHelper.setTestSelectorOverride(req -> Optional.of(excel));
            JavaFxTestSupport.runOnFxThreadAndWait(() -> ctrlRef.get().handleImportarDesdeExcel(null));

            Label lblResumen = assertInstanceOf(Label.class, rootRef.get().lookup("#lblResumenImportacionMenu"));
            ProgressIndicator pb = assertInstanceOf(
                    ProgressIndicator.class, rootRef.get().lookup("#pbImportacionMenu"));

            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            String[] text = new String[1];
            boolean[] visible = new boolean[1];
            while (System.nanoTime() < deadline) {
                JavaFxTestSupport.runOnFxThreadAndWait(() -> {
                    text[0] = lblResumen.getText();
                    visible[0] = pb.isVisible();
                });
                if (text[0] != null && text[0].startsWith("Importación completada:")) {
                    break;
                }
                Thread.sleep(60L);
            }

            assertEquals("Importación completada: 1 productos creados, 0 actualizados, 0 errores.", text[0]);
            assertFalse(visible[0], "ProgressIndicator debe ocultarse al concluir");
        } finally {
            try (Connection conn = ConexionBD.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM producto WHERE codigo = 'T5-UI-01'")) {
                ps.executeUpdate();
            }
        }
    }

    private static File crearExcelMenu(File destino, List<Object[]> filas) throws Exception {
        String[] headers = {"Codigo_SKU", "Nombre_Platillo", "Categoria", "Precio_Venta", "Descripcion", "Estado"};
        return escribirExcel(destino, "Menu", headers, filas);
    }

    private static File crearExcelInsumos(File destino, List<Object[]> filas) throws Exception {
        String[] headers = {
                "Codigo_SKU", "Nombre_Insumo", "Unidad_Medida", "Stock_Actual", "Stock_Minimo", "Costo_Unitario"
        };
        return escribirExcel(destino, "Insumos", headers, filas);
    }

    private static File escribirExcel(File destino, String sheetName, String[] headers, List<Object[]> filas)
            throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet(sheetName);
            Row h = sh.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                h.createCell(c).setCellValue(headers[c]);
            }
            for (int r = 0; r < filas.size(); r++) {
                Row row = sh.createRow(r + 1);
                Object[] vals = filas.get(r);
                for (int c = 0; c < vals.length; c++) {
                    Object v = vals[c];
                    if (v instanceof Number n) {
                        row.createCell(c).setCellValue(n.doubleValue());
                    } else {
                        row.createCell(c).setCellValue(v != null ? v.toString() : "");
                    }
                }
            }
            try (FileOutputStream fos = new FileOutputStream(destino)) {
                wb.write(fos);
            }
        }
        return destino;
    }
}
