package com.foodcore.service;

import com.foodcore.dao.CategoriaDAO;
import com.foodcore.dao.CategoriaDAOImpl;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.model.Categoria;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.Insumo;
import com.foodcore.model.Producto;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.UnidadMedidaFormatter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio de generación de plantillas Excel e importación masiva (.xlsx / .csv)
 * para Menú y Catálogo de Insumos con pre-validación y Upsert por {@code Codigo_SKU} (v4.6.0 Fase 5).
 */
public class ImportacionMasivaService {

    private static final Logger LOGGER = Logger.getLogger(ImportacionMasivaService.class.getName());

    public static final List<String> COLUMNAS_PLANTILLA_MENU = List.of(
            "Codigo_SKU", "Nombre_Platillo", "Categoria", "Precio_Venta", "Descripcion", "Estado");

    public static final List<String> COLUMNAS_PLANTILLA_INSUMOS = List.of(
            "Codigo_SKU", "Nombre_Insumo", "Unidad_Medida", "Stock_Actual", "Stock_Minimo", "Costo_Unitario");

    public enum TipoImportacion {
        MENU,
        INSUMOS
    }

    public record FilaMenuValida(
            int numeroFila,
            String sku,
            String nombre,
            String categoria,
            BigDecimal precioVenta,
            String descripcion,
            boolean activo) {
    }

    public record FilaInsumoValida(
            int numeroFila,
            String sku,
            String nombre,
            String unidadMedida,
            BigDecimal stockActual,
            BigDecimal stockMinimo,
            BigDecimal costoUnitario) {
    }

    public record ResultadoPrevalidacion<T>(
            List<T> filasValidas,
            List<String> errores) {
        public ResultadoPrevalidacion {
            filasValidas = filasValidas != null ? List.copyOf(filasValidas) : List.of();
            errores = errores != null ? List.copyOf(errores) : List.of();
        }
    }

    public record ResultadoImportacion(
            TipoImportacion tipo,
            int creados,
            int actualizados,
            int errores,
            List<String> detallesErrores) {
        public ResultadoImportacion {
            detallesErrores = detallesErrores != null ? List.copyOf(detallesErrores) : List.of();
        }

        public String construirMensajeResumen() {
            String entidad = tipo == TipoImportacion.MENU ? "productos" : "insumos";
            return String.format(
                    Locale.ROOT,
                    "Importación completada: %d %s creados, %d actualizados, %d errores.",
                    creados, entidad, actualizados, errores);
        }
    }

    private final ProductoDAO productoDAO;
    private final CategoriaDAO categoriaDAO;
    private final InsumoDAO insumoDAO;
    private final TransactionManager tx;

    public ImportacionMasivaService() {
        this(new ProductoDAOImpl(), new CategoriaDAOImpl(), new InsumoDAOImpl(), new JdbcTransactionManager());
    }

    public ImportacionMasivaService(
            ProductoDAO productoDAO,
            CategoriaDAO categoriaDAO,
            InsumoDAO insumoDAO,
            TransactionManager tx) {
        this.productoDAO = productoDAO;
        this.categoriaDAO = categoriaDAO;
        this.insumoDAO = insumoDAO;
        this.tx = tx;
    }

    public void exportarPlantillaMenu(File archivoDestino) throws IOException {
        List<List<Object>> ejemplos = List.of(
                List.of("SKU-HAM-101", "Burger Clásica Angus", "Hamburguesas", 24.50,
                        "Carne 150g, queso cheddar y salsa especial", "Activo"),
                List.of("SKU-BEB-101", "Limonada Frozen 500ml", "Bebidas", 9.50,
                        "Limonada artesanal granizada", "Activo")
        );
        generarPlantillaExcel("Plantilla_Menu", COLUMNAS_PLANTILLA_MENU, ejemplos, archivoDestino);
    }

    public void exportarPlantillaInsumos(File archivoDestino) throws IOException {
        List<List<Object>> ejemplos = List.of(
                List.of("INS-SKU-101", "Carne Molida Angus", "KG", 25.50, 5.00, 32.00),
                List.of("INS-SKU-102", "Pan Brioche Artesanal", "UND", 120, 24, 1.20)
        );
        generarPlantillaExcel("Plantilla_Insumos", COLUMNAS_PLANTILLA_INSUMOS, ejemplos, archivoDestino);
    }

    public ResultadoPrevalidacion<FilaMenuValida> prevalidarArchivoMenu(File archivo) throws IOException {
        TablaLeida tabla = leerTablaArchivo(archivo);
        int idxSku = buscarColumnaRequerida(tabla.headers(), "codigo_sku", "sku", "codigo");
        int idxNombre = buscarColumnaRequerida(tabla.headers(), "nombre_platillo", "nombre", "platillo");
        int idxCategoria = buscarColumnaRequerida(tabla.headers(), "categoria");
        int idxPrecio = buscarColumnaRequerida(tabla.headers(), "precio_venta", "precio");
        int idxDescripcion = buscarColumnaOpcional(tabla.headers(), "descripcion", "desc");
        int idxEstado = buscarColumnaOpcional(tabla.headers(), "estado", "disponible", "activo");

        Set<String> skusVistos = new HashSet<>();
        List<FilaMenuValida> validas = new ArrayList<>();
        List<String> errores = new ArrayList<>();

        for (FilaCruda fila : tabla.filas()) {
            try {
                String sku = validarSku(obtenerCelda(fila.valores(), idxSku), fila.numeroFila(), skusVistos);
                String nombre = validarTextoObligatorio(
                        obtenerCelda(fila.valores(), idxNombre), "Nombre_Platillo", 100, fila.numeroFila());
                String catRaw = obtenerCelda(fila.valores(), idxCategoria).trim();
                String categoria = catRaw.isEmpty() ? "General" : truncarTexto(catRaw, 60);
                BigDecimal precio = parseDecimalNoNegativo(
                        obtenerCelda(fila.valores(), idxPrecio), "Precio_Venta", 2, fila.numeroFila());
                String descripcion = idxDescripcion >= 0 ? obtenerCelda(fila.valores(), idxDescripcion).trim() : "";
                boolean activo = idxEstado < 0 || parseEstado(obtenerCelda(fila.valores(), idxEstado), fila.numeroFila());

                validas.add(new FilaMenuValida(fila.numeroFila(), sku, nombre, categoria, precio, descripcion, activo));
            } catch (IllegalArgumentException ex) {
                errores.add(ex.getMessage());
            }
        }
        return new ResultadoPrevalidacion<>(validas, errores);
    }

    public ResultadoPrevalidacion<FilaInsumoValida> prevalidarArchivoInsumos(File archivo) throws IOException {
        TablaLeida tabla = leerTablaArchivo(archivo);
        int idxSku = buscarColumnaRequerida(tabla.headers(), "codigo_sku", "sku", "codigo");
        int idxNombre = buscarColumnaRequerida(tabla.headers(), "nombre_insumo", "nombre", "insumo");
        int idxUnidad = buscarColumnaRequerida(tabla.headers(), "unidad_medida", "unidad");
        int idxStockActual = buscarColumnaRequerida(tabla.headers(), "stock_actual", "stock");
        int idxStockMinimo = buscarColumnaRequerida(tabla.headers(), "stock_minimo");
        int idxCosto = buscarColumnaRequerida(tabla.headers(), "costo_unitario", "costo");

        Set<String> skusVistos = new HashSet<>();
        List<FilaInsumoValida> validas = new ArrayList<>();
        List<String> errores = new ArrayList<>();

        for (FilaCruda fila : tabla.filas()) {
            try {
                String sku = validarSku(obtenerCelda(fila.valores(), idxSku), fila.numeroFila(), skusVistos);
                String nombre = validarTextoObligatorio(
                        obtenerCelda(fila.valores(), idxNombre), "Nombre_Insumo", 100, fila.numeroFila());
                String unidadCanon = UnidadMedidaFormatter.normalizarCodigo(obtenerCelda(fila.valores(), idxUnidad));
                BigDecimal stockActual = parseDecimalNoNegativo(
                        obtenerCelda(fila.valores(), idxStockActual), "Stock_Actual", DesgloseFiscal.ESCALA_STOCK, fila.numeroFila());
                BigDecimal stockMinimo = parseDecimalNoNegativo(
                        obtenerCelda(fila.valores(), idxStockMinimo), "Stock_Minimo", DesgloseFiscal.ESCALA_STOCK, fila.numeroFila());
                BigDecimal costoUnitario = parseDecimalNoNegativo(
                        obtenerCelda(fila.valores(), idxCosto), "Costo_Unitario", DesgloseFiscal.ESCALA_STOCK, fila.numeroFila());

                validas.add(new FilaInsumoValida(
                        fila.numeroFila(), sku, nombre, unidadCanon, stockActual, stockMinimo, costoUnitario));
            } catch (IllegalArgumentException ex) {
                errores.add(ex.getMessage());
            }
        }
        return new ResultadoPrevalidacion<>(validas, errores);
    }

    public ResultadoImportacion importarMenu(File archivo) throws Exception {
        ResultadoPrevalidacion<FilaMenuValida> pre = prevalidarArchivoMenu(archivo);
        List<String> detallesErrores = new ArrayList<>(pre.errores());

        if (pre.filasValidas().isEmpty()) {
            return new ResultadoImportacion(
                    TipoImportacion.MENU, 0, 0, detallesErrores.size(), detallesErrores);
        }

        return tx.executeChecked(conn -> {
            Map<String, Categoria> categoriasPorNombre = new HashMap<>();
            for (Categoria c : categoriaDAO.listarTodas(conn)) {
                if (c.getNombre() != null) {
                    categoriasPorNombre.put(normalizarClave(c.getNombre()), c);
                }
            }

            int creados = 0;
            int actualizados = 0;
            for (FilaMenuValida fila : pre.filasValidas()) {
                Savepoint sp = conn.setSavepoint("imp_menu_" + fila.numeroFila());
                try {
                    int idCategoria = resolverOCrearCategoria(conn, categoriasPorNombre, fila.categoria());
                    Producto existente = productoDAO.buscarPorCodigo(conn, fila.sku());
                    if (existente == null) {
                        Producto nuevo = new Producto(
                                0,
                                idCategoria,
                                fila.sku(),
                                fila.nombre(),
                                fila.descripcion(),
                                fila.precioVenta(),
                                BigDecimal.ZERO,
                                fila.activo());
                        productoDAO.crear(conn, nuevo);
                        creados++;
                    } else {
                        existente.setIdCategoria(idCategoria);
                        existente.setCodigo(fila.sku());
                        existente.setNombre(fila.nombre());
                        existente.setDescripcion(fila.descripcion());
                        existente.setPrecioVenta(fila.precioVenta());
                        existente.setDisponible(fila.activo());
                        productoDAO.actualizar(conn, existente);
                        productoDAO.reactivar(conn, existente.getIdProducto());
                        actualizados++;
                    }
                    conn.releaseSavepoint(sp);
                } catch (SQLException | RuntimeException ex) {
                    conn.rollback(sp);
                    LOGGER.log(Level.WARNING, "Error al importar fila de menú " + fila.numeroFila(), ex);
                    detallesErrores.add("Fila " + fila.numeroFila() + " (" + fila.sku() + "): " + ex.getMessage());
                }
            }
            return new ResultadoImportacion(
                    TipoImportacion.MENU, creados, actualizados, detallesErrores.size(), detallesErrores);
        });
    }

    public ResultadoImportacion importarInsumos(File archivo) throws Exception {
        ResultadoPrevalidacion<FilaInsumoValida> pre = prevalidarArchivoInsumos(archivo);
        List<String> detallesErrores = new ArrayList<>(pre.errores());

        if (pre.filasValidas().isEmpty()) {
            return new ResultadoImportacion(
                    TipoImportacion.INSUMOS, 0, 0, detallesErrores.size(), detallesErrores);
        }

        return tx.executeChecked(conn -> {
            int creados = 0;
            int actualizados = 0;
            for (FilaInsumoValida fila : pre.filasValidas()) {
                Savepoint sp = conn.setSavepoint("imp_ins_" + fila.numeroFila());
                try {
                    Insumo existente = insumoDAO.buscarPorCodigo(conn, fila.sku());
                    if (existente == null) {
                        Insumo nuevo = new Insumo(
                                0L,
                                fila.sku(),
                                fila.nombre(),
                                fila.unidadMedida(),
                                fila.stockActual(),
                                fila.stockMinimo(),
                                fila.costoUnitario());
                        insumoDAO.crear(conn, nuevo);
                        creados++;
                    } else {
                        existente.setCodigo(fila.sku());
                        existente.setNombre(fila.nombre());
                        existente.setUnidadMedida(fila.unidadMedida());
                        existente.setStockActualDisponible(fila.stockActual());
                        existente.setStockMinimo(fila.stockMinimo());
                        existente.setCostoUnitario(fila.costoUnitario());
                        insumoDAO.actualizar(conn, existente);
                        insumoDAO.reactivar(conn, existente.getIdInsumo());
                        actualizados++;
                    }
                    conn.releaseSavepoint(sp);
                } catch (SQLException | RuntimeException ex) {
                    conn.rollback(sp);
                    LOGGER.log(Level.WARNING, "Error al importar fila de insumo " + fila.numeroFila(), ex);
                    detallesErrores.add("Fila " + fila.numeroFila() + " (" + fila.sku() + "): " + ex.getMessage());
                }
            }
            return new ResultadoImportacion(
                    TipoImportacion.INSUMOS, creados, actualizados, detallesErrores.size(), detallesErrores);
        });
    }

    private int resolverOCrearCategoria(
            java.sql.Connection conn,
            Map<String, Categoria> cache,
            String nombreCategoria) throws SQLException {
        String clave = normalizarClave(nombreCategoria);
        Categoria cat = cache.get(clave);
        if (cat != null) {
            if (!cat.isActivo()) {
                cat.setActivo(true);
                categoriaDAO.actualizar(conn, cat);
            }
            return cat.getIdCategoria();
        }
        Categoria nueva = new Categoria(0, nombreCategoria, "Creada automáticamente vía importación", true);
        int id = categoriaDAO.crear(conn, nueva);
        nueva.setIdCategoria(id);
        cache.put(clave, nueva);
        return id;
    }

    private void generarPlantillaExcel(
            String nombreHoja,
            List<String> columnas,
            List<List<Object>> filasEjemplo,
            File archivoDestino) throws IOException {
        if (archivoDestino == null) {
            throw new IllegalArgumentException("El archivo destino no puede ser nulo.");
        }
        if (archivoDestino.getParentFile() != null) {
            Files.createDirectories(archivoDestino.getParentFile().toPath());
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(nombreHoja);

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < columnas.size(); c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(columnas.get(c));
                cell.setCellStyle(headerStyle);
            }

            for (int r = 0; r < filasEjemplo.size(); r++) {
                Row dataRow = sheet.createRow(r + 1);
                List<Object> vals = filasEjemplo.get(r);
                for (int c = 0; c < vals.size(); c++) {
                    Object val = vals.get(c);
                    Cell cell = dataRow.createCell(c);
                    if (val instanceof Number num) {
                        cell.setCellValue(num.doubleValue());
                    } else {
                        cell.setCellValue(val != null ? val.toString() : "");
                    }
                }
            }

            for (int c = 0; c < columnas.size(); c++) {
                sheet.autoSizeColumn(c);
            }

            try (FileOutputStream fos = new FileOutputStream(archivoDestino)) {
                workbook.write(fos);
            }
        }
    }

    private record FilaCruda(int numeroFila, List<String> valores) {
    }

    private record TablaLeida(Map<String, Integer> headers, List<FilaCruda> filas) {
    }

    private TablaLeida leerTablaArchivo(File archivo) throws IOException {
        if (archivo == null || !archivo.exists() || !archivo.isFile()) {
            throw new IllegalArgumentException("El archivo seleccionado no existe o no es válido.");
        }
        String nombreLower = archivo.getName().toLowerCase(Locale.ROOT);
        if (nombreLower.endsWith(".csv")) {
            return leerCsv(archivo);
        }
        if (nombreLower.endsWith(".xlsx") || nombreLower.endsWith(".xls")) {
            return leerExcel(archivo);
        }
        throw new IllegalArgumentException("Formato no soportado. Seleccione un archivo .xlsx o .csv.");
    }

    private TablaLeida leerExcel(File archivo) throws IOException {
        try (FileInputStream fis = new FileInputStream(archivo);
             Workbook workbook = WorkbookFactory.create(fis)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("El archivo Excel no contiene hojas.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            int headerRowIdx = sheet.getFirstRowNum();
            Row headerRow = sheet.getRow(headerRowIdx);
            if (headerRow == null) {
                throw new IllegalArgumentException("El archivo Excel no contiene fila de encabezados.");
            }

            Map<String, Integer> headers = new LinkedHashMap<>();
            int maxCol = headerRow.getLastCellNum();
            for (int c = 0; c < maxCol; c++) {
                String raw = formatter.formatCellValue(headerRow.getCell(c), evaluator);
                String norm = normalizarHeader(raw);
                if (!norm.isEmpty()) {
                    headers.putIfAbsent(norm, c);
                }
            }
            if (headers.isEmpty()) {
                throw new IllegalArgumentException("No se encontraron encabezados válidos en la fila 1.");
            }

            List<FilaCruda> filas = new ArrayList<>();
            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                List<String> valores = new ArrayList<>(maxCol);
                boolean vacia = true;
                for (int c = 0; c < maxCol; c++) {
                    String val = formatter.formatCellValue(row.getCell(c), evaluator).trim();
                    if (!val.isEmpty()) {
                        vacia = false;
                    }
                    valores.add(val);
                }
                if (!vacia) {
                    filas.add(new FilaCruda(r + 1, valores));
                }
            }
            return new TablaLeida(headers, filas);
        }
    }

    private TablaLeida leerCsv(File archivo) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(archivo), StandardCharsets.UTF_8))) {
            String firstLine = br.readLine();
            if (firstLine == null) {
                throw new IllegalArgumentException("El archivo CSV está vacío.");
            }
            if (firstLine.startsWith("\ufeff")) {
                firstLine = firstLine.substring(1);
            }
            char separador = detectarSeparadorCsv(firstLine);
            List<String> headerTokens = parsearLineaCsv(firstLine, separador);
            Map<String, Integer> headers = new LinkedHashMap<>();
            for (int i = 0; i < headerTokens.size(); i++) {
                String norm = normalizarHeader(headerTokens.get(i));
                if (!norm.isEmpty()) {
                    headers.putIfAbsent(norm, i);
                }
            }
            if (headers.isEmpty()) {
                throw new IllegalArgumentException("No se encontraron encabezados válidos en el CSV.");
            }

            List<FilaCruda> filas = new ArrayList<>();
            int rowNum = 2;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    rowNum++;
                    continue;
                }
                List<String> vals = parsearLineaCsv(line, separador);
                boolean vacia = vals.stream().allMatch(String::isBlank);
                if (!vacia) {
                    filas.add(new FilaCruda(rowNum, vals));
                }
                rowNum++;
            }
            return new TablaLeida(headers, filas);
        }
    }

    private static char detectarSeparadorCsv(String headerLine) {
        int comas = 0;
        int puntoComas = 0;
        for (int i = 0; i < headerLine.length(); i++) {
            char ch = headerLine.charAt(i);
            if (ch == ',') {
                comas++;
            } else if (ch == ';') {
                puntoComas++;
            }
        }
        return puntoComas > comas ? ';' : ',';
    }

    private static List<String> parsearLineaCsv(String line, char separador) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == separador && !inQuotes) {
                out.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        out.add(current.toString().trim());
        return out;
    }

    private static int buscarColumnaRequerida(Map<String, Integer> headers, String... aliases) {
        int idx = buscarColumnaOpcional(headers, aliases);
        if (idx < 0) {
            throw new IllegalArgumentException(
                    "Falta la columna obligatoria '" + aliases[0] + "' en el archivo.");
        }
        return idx;
    }

    private static int buscarColumnaOpcional(Map<String, Integer> headers, String... aliases) {
        for (String alias : aliases) {
            Integer idx = headers.get(normalizarHeader(alias));
            if (idx != null) {
                return idx;
            }
        }
        return -1;
    }

    private static String obtenerCelda(List<String> valores, int idx) {
        if (idx < 0 || idx >= valores.size() || valores.get(idx) == null) {
            return "";
        }
        return valores.get(idx);
    }

    private static String validarSku(String raw, int numeroFila, Set<String> skusVistos) {
        String sku = raw != null ? raw.trim() : "";
        if (sku.isEmpty()) {
            throw new IllegalArgumentException("Fila " + numeroFila + ": Codigo_SKU es obligatorio.");
        }
        if (sku.length() > 20) {
            throw new IllegalArgumentException(
                    "Fila " + numeroFila + ": Codigo_SKU excede 20 caracteres ('" + sku + "').");
        }
        String key = normalizarClave(sku);
        if (!skusVistos.add(key)) {
            throw new IllegalArgumentException(
                    "Fila " + numeroFila + ": Codigo_SKU duplicado en el archivo ('" + sku + "').");
        }
        return sku;
    }

    private static String validarTextoObligatorio(String raw, String campo, int maxLen, int numeroFila) {
        String txt = raw != null ? raw.trim() : "";
        if (txt.isEmpty()) {
            throw new IllegalArgumentException("Fila " + numeroFila + ": " + campo + " es obligatorio.");
        }
        if (txt.length() > maxLen) {
            throw new IllegalArgumentException(
                    "Fila " + numeroFila + ": " + campo + " excede " + maxLen + " caracteres.");
        }
        return txt;
    }

    private static BigDecimal parseDecimalNoNegativo(String raw, String campo, int escala, int numeroFila) {
        String txt = raw != null ? raw.trim() : "";
        if (txt.isEmpty()) {
            throw new IllegalArgumentException("Fila " + numeroFila + ": " + campo + " es obligatorio.");
        }
        txt = txt.replace("S/", "").replace("$", "").trim();
        if (txt.contains(",") && !txt.contains(".")) {
            txt = txt.replace(',', '.');
        } else if (txt.contains(",") && txt.contains(".")) {
            txt = txt.replace(",", "");
        }
        try {
            BigDecimal val = new BigDecimal(txt);
            if (val.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "Fila " + numeroFila + ": " + campo + " no puede ser negativo (" + txt + ").");
            }
            return val.setScale(escala, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Fila " + numeroFila + ": valor numérico inválido en " + campo + " ('" + raw + "').");
        }
    }

    private static boolean parseEstado(String raw, int numeroFila) {
        String s = normalizarClave(raw);
        if (s.isEmpty() || "ACTIVO".equals(s) || "TRUE".equals(s) || "1".equals(s)
                || "SI".equals(s) || "SÍ".equals(s) || "DISPONIBLE".equals(s)) {
            return true;
        }
        if ("INACTIVO".equals(s) || "FALSE".equals(s) || "0".equals(s)
                || "NO".equals(s) || "AGOTADO".equals(s)) {
            return false;
        }
        throw new IllegalArgumentException(
                "Fila " + numeroFila + ": Estado inválido ('" + raw + "'). Use Activo o Inactivo.");
    }

    private static String normalizarHeader(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('á', 'a')
                .replace('é', 'e')
                .replace('í', 'i')
                .replace('ó', 'o')
                .replace('ú', 'u');
    }

    private static String normalizarClave(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String truncarTexto(String txt, int maxLen) {
        return txt.length() <= maxLen ? txt : txt.substring(0, maxLen);
    }
}
