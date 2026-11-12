package com.foodcore.service;

import com.foodcore.service.ImportacionMasivaService.FilaInsumoValida;
import com.foodcore.service.ImportacionMasivaService.FilaMenuValida;
import com.foodcore.service.ImportacionMasivaService.ResultadoImportacion;
import com.foodcore.service.ImportacionMasivaService.ResultadoPrevalidacion;
import com.foodcore.service.ImportacionMasivaService.TipoImportacion;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias de Fase 5 (SPEC-FOODCORE-v4.6.0):
 * generación de plantillas Excel exactas, lectura Excel/CSV y pre-validación de SKUs duplicados/negativos.
 */
class ImportacionMasivaTest {

    private final ImportacionMasivaService service = new ImportacionMasivaService(null, null, null, null);

    @Test
    @DisplayName("5.2 Plantillas de Menú e Insumos generan columnas exactas en formato .xlsx")
    void debeGenerarPlantillasConColumnasExactas(@TempDir Path tempDir) throws Exception {
        File menuTemplate = tempDir.resolve("plantilla_menu.xlsx").toFile();
        File insumosTemplate = tempDir.resolve("plantilla_insumos.xlsx").toFile();

        service.exportarPlantillaMenu(menuTemplate);
        service.exportarPlantillaInsumos(insumosTemplate);

        assertTrue(menuTemplate.exists() && menuTemplate.length() > 1000);
        assertTrue(insumosTemplate.exists() && insumosTemplate.length() > 1000);

        try (Workbook wbMenu = WorkbookFactory.create(menuTemplate)) {
            Sheet sh = wbMenu.getSheetAt(0);
            Row header = sh.getRow(0);
            List<String> expectedMenu = List.of(
                    "Codigo_SKU", "Nombre_Platillo", "Categoria", "Precio_Venta", "Descripcion", "Estado");
            for (int i = 0; i < expectedMenu.size(); i++) {
                assertEquals(expectedMenu.get(i), header.getCell(i).getStringCellValue());
            }
        }

        try (Workbook wbIns = WorkbookFactory.create(insumosTemplate)) {
            Sheet sh = wbIns.getSheetAt(0);
            Row header = sh.getRow(0);
            List<String> expectedIns = List.of(
                    "Codigo_SKU", "Nombre_Insumo", "Unidad_Medida", "Stock_Actual", "Stock_Minimo", "Costo_Unitario");
            for (int i = 0; i < expectedIns.size(); i++) {
                assertEquals(expectedIns.get(i), header.getCell(i).getStringCellValue());
            }
        }
    }

    @Test
    @DisplayName("5.4 Pre-validación Menú rechaza SKUs duplicados, precios negativos y campos faltantes")
    void debePrevalidarArchivoMenuExcelYDetectaErrores(@TempDir Path tempDir) throws Exception {
        File xlsx = tempDir.resolve("menu_validacion.xlsx").toFile();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Menu");
            Row h = sh.createRow(0);
            String[] cols = {"Codigo_SKU", "Nombre_Platillo", "Categoria", "Precio_Venta", "Descripcion", "Estado"};
            for (int i = 0; i < cols.length; i++) {
                h.createCell(i).setCellValue(cols[i]);
            }

            // Fila 2: Válida
            Row r1 = sh.createRow(1);
            r1.createCell(0).setCellValue("SKU-101");
            r1.createCell(1).setCellValue("Burger Doble");
            r1.createCell(2).setCellValue("Hamburguesas");
            r1.createCell(3).setCellValue(28.50);
            r1.createCell(4).setCellValue("Doble carne y queso");
            r1.createCell(5).setCellValue("Activo");

            // Fila 3: SKU duplicado
            Row r2 = sh.createRow(2);
            r2.createCell(0).setCellValue("sku-101");
            r2.createCell(1).setCellValue("Duplicada");
            r2.createCell(2).setCellValue("Hamburguesas");
            r2.createCell(3).setCellValue(20.00);
            r2.createCell(4).setCellValue("");
            r2.createCell(5).setCellValue("Activo");

            // Fila 4: Precio negativo
            Row r3 = sh.createRow(3);
            r3.createCell(0).setCellValue("SKU-102");
            r3.createCell(1).setCellValue("Limonada Negativa");
            r3.createCell(2).setCellValue("Bebidas");
            r3.createCell(3).setCellValue(-5.00);
            r3.createCell(4).setCellValue("Error");
            r3.createCell(5).setCellValue("Inactivo");

            // Fila 5: Válida Inactiva sin categoría -> "General"
            Row r4 = sh.createRow(4);
            r4.createCell(0).setCellValue("SKU-103");
            r4.createCell(1).setCellValue("Chicha Morada");
            r4.createCell(2).setCellValue("");
            r4.createCell(3).setCellValue(10.00);
            r4.createCell(4).setCellValue("Jarra 1L");
            r4.createCell(5).setCellValue("Inactivo");

            try (FileOutputStream fos = new FileOutputStream(xlsx)) {
                wb.write(fos);
            }
        }

        ResultadoPrevalidacion<FilaMenuValida> res = service.prevalidarArchivoMenu(xlsx);
        assertEquals(2, res.filasValidas().size());
        assertEquals(2, res.errores().size());
        assertTrue(res.errores().get(0).contains("duplicado"));
        assertTrue(res.errores().get(1).contains("negativo"));

        FilaMenuValida f1 = res.filasValidas().get(0);
        assertEquals("SKU-101", f1.sku());
        assertEquals(new BigDecimal("28.50"), f1.precioVenta());
        assertTrue(f1.activo());

        FilaMenuValida f2 = res.filasValidas().get(1);
        assertEquals("General", f2.categoria());
        assertFalse(f2.activo());
    }

    @Test
    @DisplayName("5.4 Pre-validación Insumos soporta CSV UTF-8 con BOM y normaliza unidades")
    void debePrevalidarCsvInsumosYMensajeResumen(@TempDir Path tempDir) throws Exception {
        Path csv = tempDir.resolve("insumos.csv");
        String content = "\ufeffCodigo_SKU;Nombre_Insumo;Unidad_Medida;Stock_Actual;Stock_Minimo;Costo_Unitario\n"
                + "INS-01;Carne Molida;KG;15.5;4.0;30.00\n"
                + "INS-02;Pan Brioche;UND;100;20;1.10\n"
                + "INS-03;;LT;10;2;5.00\n"
                + "INS-04;Aceite Vegetal;LT;-3;2;8.50\n";
        Files.writeString(csv, content, StandardCharsets.UTF_8);

        ResultadoPrevalidacion<FilaInsumoValida> res = service.prevalidarArchivoInsumos(csv.toFile());
        assertEquals(2, res.filasValidas().size());
        assertEquals(2, res.errores().size());

        assertEquals("KG", res.filasValidas().get(0).unidadMedida());
        assertEquals("UNIDAD", res.filasValidas().get(1).unidadMedida());
        assertEquals(new BigDecimal("15.5000"), res.filasValidas().get(0).stockActual());

        ResultadoImportacion resumenMenu = new ResultadoImportacion(
                TipoImportacion.MENU, 45, 120, 2, List.of());
        assertEquals("Importación completada: 45 productos creados, 120 actualizados, 2 errores.",
                resumenMenu.construirMensajeResumen());

        File archivoInvalido = tempDir.resolve("bad.csv").toFile();
        Files.writeString(archivoInvalido.toPath(), "ColA,ColB\n1,2\n", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> service.prevalidarArchivoMenu(archivoInvalido));
    }
}
