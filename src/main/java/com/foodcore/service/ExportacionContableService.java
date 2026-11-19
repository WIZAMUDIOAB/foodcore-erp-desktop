package com.foodcore.service;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.CajaDAOImpl;
import com.foodcore.dao.CompraDAO;
import com.foodcore.dao.VentaDAO;
import com.foodcore.dao.impl.CompraDAOImpl;
import com.foodcore.dao.impl.VentaDAOImpl;
import com.foodcore.model.Compra;
import com.foodcore.util.ConexionBD;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ExportacionContableService {

    private final VentaDAO ventaDAO;
    private final CompraDAO compraDAO;
    private final CajaDAO cajaDAO;

    public ExportacionContableService() {
        this.ventaDAO = new VentaDAOImpl();
        this.compraDAO = new CompraDAOImpl();
        this.cajaDAO = new CajaDAOImpl();
    }

    public ExportacionContableService(VentaDAO ventaDAO, CompraDAO compraDAO, CajaDAO cajaDAO) {
        this.ventaDAO = ventaDAO;
        this.compraDAO = compraDAO;
        this.cajaDAO = cajaDAO;
    }

    public void exportarVentasSunat14_1_Excel(LocalDate inicio, LocalDate fin, File archivoDestino) throws Exception {
        List<VentaRegistroSunat> registros = obtenerVentasParaSunat(inicio, fin);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("PLE_SUNAT_14.1_VENTAS");

            // Estilo Header
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            // Título
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("FORMATO 14.1: REGISTRO DE VENTAS E INGRESOS (PLE SUNAT) - FOODCORE ERP");

            Row periodRow = sheet.createRow(1);
            periodRow.createCell(0).setCellValue("Período: " + inicio + " al " + fin + " | RUC Empresa: 20601234567 | Razón Social: FOODCORE S.A.C.");

            String[] headers = {
                    "Periodo", "Correlativo", "Fecha Emisión", "Fecha Venc.", "Tipo Comp.", "Serie",
                    "Número", "Tipo Doc Cliente", "RUC/DNI Cliente", "Razón Social / Nombre",
                    "Base Imponible", "IGV (18%)", "Importe Total", "Medio Pago", "Estado"
            };

            Row headerRow = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 4;
            BigDecimal totalBase = BigDecimal.ZERO;
            BigDecimal totalIgv = BigDecimal.ZERO;
            BigDecimal totalVenta = BigDecimal.ZERO;

            for (VentaRegistroSunat r : registros) {
                boolean anulada = !"1".equals(r.estado);
                BigDecimal subFila = anulada ? BigDecimal.ZERO : normalizarMoneda(r.subtotalBd != null ? r.subtotalBd : BigDecimal.valueOf(r.subtotal));
                BigDecimal igvFila = anulada ? BigDecimal.ZERO : normalizarMoneda(r.igvBd != null ? r.igvBd : BigDecimal.valueOf(r.igv));
                BigDecimal totFila = anulada ? BigDecimal.ZERO : normalizarMoneda(r.totalBd != null ? r.totalBd : BigDecimal.valueOf(r.total));

                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(sanitizarFormulaCelda(r.periodo));
                row.createCell(1).setCellValue(String.format(Locale.US, "M%06d", r.idVenta));
                row.createCell(2).setCellValue(sanitizarFormulaCelda(r.fechaEmision));
                row.createCell(3).setCellValue(sanitizarFormulaCelda(r.fechaVencimiento));
                row.createCell(4).setCellValue(sanitizarFormulaCelda(r.tipoComprobante));
                row.createCell(5).setCellValue(sanitizarFormulaCelda(r.serie));
                String numeroDoc = (r.numero != null && !r.numero.isBlank())
                        ? r.numero : String.format(Locale.US, "%08d", r.idVenta);
                row.createCell(6).setCellValue(sanitizarFormulaCelda(numeroDoc));
                row.createCell(7).setCellValue(sanitizarFormulaCelda(r.tipoDocCliente));
                row.createCell(8).setCellValue(sanitizarFormulaCelda(r.docCliente));
                row.createCell(9).setCellValue(sanitizarFormulaCelda(r.nombreCliente));
                row.createCell(10).setCellValue(subFila.doubleValue());
                row.createCell(11).setCellValue(igvFila.doubleValue());
                row.createCell(12).setCellValue(totFila.doubleValue());
                row.createCell(13).setCellValue(sanitizarFormulaCelda(r.medioPago));
                row.createCell(14).setCellValue(sanitizarFormulaCelda(r.estado));

                if (!anulada) {
                    totalBase = totalBase.add(subFila);
                    totalIgv = totalIgv.add(igvFila);
                    totalVenta = totalVenta.add(totFila);
                }
            }

            // Fila de totales (excluyendo ventas ANULADAS, acumulado en BigDecimal)
            Row totRow = sheet.createRow(rowIdx);
            totRow.createCell(9).setCellValue("TOTALES ACUMULADOS:");
            totRow.createCell(10).setCellValue(normalizarMoneda(totalBase).doubleValue());
            totRow.createCell(11).setCellValue(normalizarMoneda(totalIgv).doubleValue());
            totRow.createCell(12).setCellValue(normalizarMoneda(totalVenta).doubleValue());

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fos = new FileOutputStream(archivoDestino)) {
                workbook.write(fos);
            }
        }
    }

    public void exportarComprasSunat8_1_Excel(LocalDate inicio, LocalDate fin, File archivoDestino) throws Exception {
        List<Compra> compras;
        try (Connection conn = ConexionBD.getConnection()) {
            compras = compraDAO.listarPorRangoFecha(conn, inicio, fin);
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("PLE_SUNAT_8.1_COMPRAS");

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("FORMATO 8.1: REGISTRO DE COMPRAS (PLE SUNAT) - FOODCORE ERP");

            Row periodRow = sheet.createRow(1);
            periodRow.createCell(0).setCellValue("Período: " + inicio + " al " + fin + " | RUC: 20601234567 | FOODCORE S.A.C.");

            String[] headers = {
                    "Periodo", "Correlativo", "Fecha Emisión", "Tipo Comp.", "Serie", "Número",
                    "Tipo Doc Proveedor", "RUC Proveedor", "Razón Social Proveedor",
                    "Base Imponible", "IGV (18%)", "Importe Total", "Estado"
            };

            Row headerRow = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 4;
            BigDecimal totBase = BigDecimal.ZERO;
            BigDecimal totIgv = BigDecimal.ZERO;
            BigDecimal totCompra = BigDecimal.ZERO;

            for (Compra c : compras) {
                boolean anulada = "ANULADA".equalsIgnoreCase(c.getEstado());
                BigDecimal baseBd = anulada ? BigDecimal.ZERO : normalizarMoneda(BigDecimal.valueOf(c.getBaseImponible()));
                BigDecimal igvBd = anulada ? BigDecimal.ZERO : normalizarMoneda(BigDecimal.valueOf(c.getIgv()));
                BigDecimal totBd = anulada ? BigDecimal.ZERO : normalizarMoneda(BigDecimal.valueOf(c.getTotal()));

                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(c.getFechaEmision() != null ? c.getFechaEmision().format(DateTimeFormatter.ofPattern("yyyyMM")) : "");
                row.createCell(1).setCellValue(String.format(Locale.US, "C%06d", c.getIdCompra()));
                row.createCell(2).setCellValue(c.getFechaEmision() != null ? c.getFechaEmision().toString() : "");
                row.createCell(3).setCellValue("01"); // Factura
                row.createCell(4).setCellValue(sanitizarFormulaCelda(c.getSerie()));
                row.createCell(5).setCellValue(sanitizarFormulaCelda(c.getNumero()));
                row.createCell(6).setCellValue("6"); // RUC
                row.createCell(7).setCellValue(sanitizarFormulaCelda(c.getRucProveedor()));
                row.createCell(8).setCellValue(sanitizarFormulaCelda(c.getRazonSocialProveedor()));
                row.createCell(9).setCellValue(baseBd.doubleValue());
                row.createCell(10).setCellValue(igvBd.doubleValue());
                row.createCell(11).setCellValue(totBd.doubleValue());
                row.createCell(12).setCellValue(sanitizarFormulaCelda(c.getEstado()));

                if (!anulada) {
                    totBase = totBase.add(baseBd);
                    totIgv = totIgv.add(igvBd);
                    totCompra = totCompra.add(totBd);
                }
            }

            Row totRow = sheet.createRow(rowIdx);
            totRow.createCell(8).setCellValue("TOTAL COMPRAS:");
            totRow.createCell(9).setCellValue(normalizarMoneda(totBase).doubleValue());
            totRow.createCell(10).setCellValue(normalizarMoneda(totIgv).doubleValue());
            totRow.createCell(11).setCellValue(normalizarMoneda(totCompra).doubleValue());

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fos = new FileOutputStream(archivoDestino)) {
                workbook.write(fos);
            }
        }
    }

    public void exportarResumenCajaMensualPdf(LocalDate inicio, LocalDate fin, File archivoDestino) throws Exception {
        List<TurnoZResumen> turnos = new ArrayList<>();
        String sql = "SELECT c.*, "
                + "COALESCE((SELECT SUM(CASE "
                + "  WHEN m.tipo_movimiento = 'VENTA' THEN m.monto "
                + "  WHEN m.tipo_movimiento IN ('ANULACION', 'DEVOLUCION') THEN -ABS(m.monto) "
                + "  ELSE 0.00 END) FROM movimiento_caja m "
                + "  WHERE m.id_caja = c.id_caja AND COALESCE(m.medio_pago, 'EFECTIVO') = 'EFECTIVO'), 0.00) AS total_ventas_efectivo, "
                + "COALESCE((SELECT SUM(CASE "
                + "  WHEN m.tipo_movimiento = 'VENTA' THEN m.monto "
                + "  WHEN m.tipo_movimiento IN ('ANULACION', 'DEVOLUCION') THEN -ABS(m.monto) "
                + "  ELSE 0.00 END) FROM movimiento_caja m "
                + "  WHERE m.id_caja = c.id_caja AND m.medio_pago = 'TARJETA'), 0.00) AS total_ventas_tarjeta, "
                + "COALESCE((SELECT SUM(CASE "
                + "  WHEN m.tipo_movimiento = 'VENTA' THEN m.monto "
                + "  WHEN m.tipo_movimiento IN ('ANULACION', 'DEVOLUCION') THEN -ABS(m.monto) "
                + "  ELSE 0.00 END) FROM movimiento_caja m "
                + "  WHERE m.id_caja = c.id_caja AND m.medio_pago NOT IN ('EFECTIVO', 'TARJETA')), 0.00) AS total_otros_medios, "
                + "COALESCE((SELECT SUM(ABS(m.monto)) FROM movimiento_caja m "
                + "  WHERE m.id_caja = c.id_caja "
                + "    AND m.tipo_movimiento IN ('EGRESO', 'ANULACION', 'DEVOLUCION') "
                + "    AND m.tipo_movimiento <> 'CIERRE_Z' "
                + "    AND COALESCE(m.concepto, '') NOT LIKE 'Cierre Z de turno%'), 0.00) AS total_egresos "
                + "FROM caja c "
                + "WHERE c.fecha_apertura >= ? AND c.fecha_apertura < ? "
                + "ORDER BY c.fecha_apertura DESC";

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(inicio.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(fin.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TurnoZResumen t = new TurnoZResumen();
                    t.idTurno = rs.getInt("id_caja");
                    t.saldoInicialBd = normalizarMoneda(rs.getBigDecimal("monto_apertura"));
                    t.totalVentasEfectivoBd = normalizarMoneda(rs.getBigDecimal("total_ventas_efectivo"));
                    t.totalVentasTarjetaBd = normalizarMoneda(rs.getBigDecimal("total_ventas_tarjeta"));
                    t.totalOtrosMediosBd = normalizarMoneda(rs.getBigDecimal("total_otros_medios"));
                    t.totalEgresosBd = normalizarMoneda(rs.getBigDecimal("total_egresos"));
                    t.saldoRealBd = normalizarMoneda(rs.getBigDecimal("monto_cierre"));
                    t.diferenciaBd = normalizarMoneda(rs.getBigDecimal("diferencia"));
                    if (rs.getTimestamp("fecha_apertura") != null) {
                        t.fechaApertura = rs.getTimestamp("fecha_apertura").toLocalDateTime();
                    }
                    turnos.add(t);
                }
            }
        }

        Document document = new Document();
        try (FileOutputStream fos = new FileOutputStream(archivoDestino)) {
            PdfWriter.getInstance(document, fos);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK);
            Font fontSub = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
            Font fontH = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
            Font fontB = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);

            Paragraph pTit = new Paragraph("FOODCORE ERP — RESUMEN MENSUAL DE CIERRES Z DE CAJA", fontTitulo);
            pTit.setAlignment(Element.ALIGN_CENTER);
            document.add(pTit);

            Paragraph pP = new Paragraph("Rango evaluado: " + inicio + " al " + fin + " | Generado automáticamente", fontSub);
            pP.setAlignment(Element.ALIGN_CENTER);
            pP.setSpacingAfter(15);
            document.add(pP);

            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.2f, 1.8f, 1.2f, 1.4f, 1.4f, 1.2f, 1.2f, 1.2f});

            String[] headers = {"Turno #", "Apertura", "Saldo Inic.", "Ventas Efec.", "Ventas Digit.", "Egresos", "Saldo Real", "Diferencia"};
            for (String h : headers) {
                PdfPCell c = new PdfPCell(new Phrase(h, fontH));
                c.setBackgroundColor(new Color(25, 42, 86));
                c.setPadding(5);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(c);
            }

            BigDecimal sumEfec = BigDecimal.ZERO;
            BigDecimal sumDigit = BigDecimal.ZERO;
            BigDecimal sumEgr = BigDecimal.ZERO;
            BigDecimal sumDif = BigDecimal.ZERO;

            for (TurnoZResumen t : turnos) {
                table.addCell(new Phrase("#" + t.idTurno, fontB));
                table.addCell(new Phrase(t.fechaApertura != null ? t.fechaApertura.format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) : "-", fontB));
                table.addCell(new Phrase(String.format(Locale.US, "S/ %.2f", t.saldoInicialBd), fontB));
                table.addCell(new Phrase(String.format(Locale.US, "S/ %.2f", t.totalVentasEfectivoBd), fontB));
                BigDecimal ventasDig = t.totalVentasTarjetaBd.add(t.totalOtrosMediosBd);
                table.addCell(new Phrase(String.format(Locale.US, "S/ %.2f", ventasDig), fontB));
                table.addCell(new Phrase(String.format(Locale.US, "S/ %.2f", t.totalEgresosBd), fontB));
                table.addCell(new Phrase(String.format(Locale.US, "S/ %.2f", t.saldoRealBd), fontB));
                table.addCell(new Phrase(String.format(Locale.US, "S/ %.2f", t.diferenciaBd), fontB));

                sumEfec = sumEfec.add(t.totalVentasEfectivoBd);
                sumDigit = sumDigit.add(ventasDig);
                sumEgr = sumEgr.add(t.totalEgresosBd);
                sumDif = sumDif.add(t.diferenciaBd);
            }

            document.add(table);

            Paragraph pTot = new Paragraph(String.format(Locale.US, "%nTOTALES CONSOLIDADOS DEL MES:%n"
                    + "Total Efectivo Recaudado: S/ %.2f | Total Canales Digitales: S/ %.2f%n"
                    + "Total Egresos de Caja: S/ %.2f | Diferencia Neta de Arqueos: S/ %.2f",
                    sumEfec, sumDigit, sumEgr, sumDif), fontTitulo);
            pTot.setSpacingBefore(15);
            document.add(pTot);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }

    public void exportarReporteGenericoCsv(List<String> encabezados, List<List<String>> filas, File archivoDestino) throws Exception {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(archivoDestino), StandardCharsets.UTF_8))) {
            // Escribir BOM para que Microsoft Excel lo abra correctamente en UTF-8
            bw.write("\ufeff");
            bw.write(String.join(",", sanitizarCamposCsv(encabezados)));
            bw.newLine();

            for (List<String> fila : filas) {
                bw.write(String.join(",", sanitizarCamposCsv(fila)));
                bw.newLine();
            }
        }
    }

    public void exportarReporteGenericoExcel(String tituloHoja, List<String> encabezados, List<List<String>> filas, File archivoDestino) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(tituloHoja != null ? tituloHoja : "REPORTE");

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < encabezados.size(); i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(sanitizarFormulaCelda(encabezados.get(i)));
                c.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (List<String> fila : filas) {
                Row r = sheet.createRow(rowIdx++);
                for (int col = 0; col < fila.size(); col++) {
                    r.createCell(col).setCellValue(sanitizarFormulaCelda(fila.get(col)));
                }
            }

            for (int i = 0; i < encabezados.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fos = new FileOutputStream(archivoDestino)) {
                workbook.write(fos);
            }
        }
    }

    public void exportarReporteGenericoPdf(String tituloReporte, List<String> encabezados, List<List<String>> filas, File archivoDestino) throws Exception {
        Document document = new Document();
        try (FileOutputStream fos = new FileOutputStream(archivoDestino)) {
            PdfWriter.getInstance(document, fos);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);
            Font fontH = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
            Font fontB = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);

            Paragraph pTit = new Paragraph("FOODCORE ERP — " + tituloReporte.toUpperCase(Locale.ROOT), fontTitulo);
            pTit.setAlignment(Element.ALIGN_CENTER);
            pTit.setSpacingAfter(10);
            document.add(pTit);

            PdfPTable table = new PdfPTable(encabezados.size());
            table.setWidthPercentage(100);

            for (String h : encabezados) {
                PdfPCell c = new PdfPCell(new Phrase(h, fontH));
                c.setBackgroundColor(new Color(25, 42, 86));
                c.setPadding(4);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(c);
            }

            for (List<String> fila : filas) {
                for (String val : fila) {
                    table.addCell(new Phrase(val != null ? val : "", fontB));
                }
            }

            document.add(table);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }

    /**
     * Neutraliza CSV/Excel Formula Injection prefijando apóstrofe {@code '} cuando el valor inicia con
     * {@code =}, {@code +}, {@code -}, {@code @}, {@code \t} o {@code \r}.
     */
    public static String sanitizarFormulaCelda(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        char first = valor.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + valor;
        }
        return valor;
    }

    private static BigDecimal normalizarMoneda(BigDecimal valor) {
        return valor != null ? valor.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private List<String> sanitizarCamposCsv(List<String> valores) {
        List<String> out = new ArrayList<>();
        for (String v : valores) {
            if (v == null) {
                out.add("\"\"");
            } else {
                String safe = sanitizarFormulaCelda(v);
                out.add("\"" + safe.replace("\"", "\"\"") + "\"");
            }
        }
        return out;
    }

    private List<VentaRegistroSunat> obtenerVentasParaSunat(LocalDate inicio, LocalDate fin) throws Exception {
        List<VentaRegistroSunat> lista = new ArrayList<>();
        String sql = "SELECT v.id_venta, v.fecha_hora, v.subtotal, v.igv, v.total, v.metodo_pago, v.estado, "
                + "cp.tipo_comprobante, cp.serie AS comp_serie, cp.numero AS comp_numero, "
                + "cli.tipo_documento AS cli_tipo_doc, cli.numero_documento AS cli_num_doc, "
                + "COALESCE(NULLIF(cli.razon_social, ''), NULLIF(cli.nombre_razon_social, ''), "
                + "         NULLIF(p.cliente_nombre, ''), 'CLIENTE VARIOS') AS nombre_cliente_final "
                + "FROM venta v "
                + "LEFT JOIN pedido p ON v.id_pedido = p.id_pedido "
                + "LEFT JOIN cliente cli ON COALESCE(v.id_cliente, p.id_cliente) = cli.id_cliente "
                + "LEFT JOIN comprobante_pago cp ON cp.id_comprobante = ("
                + "  SELECT MAX(cp2.id_comprobante) FROM comprobante_pago cp2 WHERE cp2.id_venta = v.id_venta"
                + ") "
                + "WHERE v.fecha_hora >= ? AND v.fecha_hora < ? "
                + "ORDER BY v.fecha_hora ASC, v.id_venta ASC";

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(inicio.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(fin.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    VentaRegistroSunat r = new VentaRegistroSunat();
                    r.idVenta = rs.getInt("id_venta");
                    LocalDate f = rs.getTimestamp("fecha_hora").toLocalDateTime().toLocalDate();
                    r.periodo = f.format(DateTimeFormatter.ofPattern("yyyyMM"));
                    r.fechaEmision = f.toString();
                    r.fechaVencimiento = f.toString();

                    String tipoCompRaw = rs.getString("tipo_comprobante");
                    if ("FACTURA".equalsIgnoreCase(tipoCompRaw)) {
                        r.tipoComprobante = "01";
                    } else if ("BOLETA".equalsIgnoreCase(tipoCompRaw)) {
                        r.tipoComprobante = "03";
                    } else if ("TICKET".equalsIgnoreCase(tipoCompRaw)) {
                        r.tipoComprobante = "12";
                    } else {
                        r.tipoComprobante = "03";
                    }

                    String serieRaw = rs.getString("comp_serie");
                    r.serie = (serieRaw != null && !serieRaw.isBlank()) ? serieRaw.trim() : "B001";
                    String numRaw = rs.getString("comp_numero");
                    r.numero = (numRaw != null && !numRaw.isBlank())
                            ? numRaw.trim() : String.format(Locale.US, "%08d", r.idVenta);

                    String tipoDocRaw = rs.getString("cli_tipo_doc");
                    String numDocRaw = rs.getString("cli_num_doc");
                    if ("RUC".equalsIgnoreCase(tipoDocRaw) || (numDocRaw != null && numDocRaw.trim().length() == 11)) {
                        r.tipoDocCliente = "6";
                    } else if ("DNI".equalsIgnoreCase(tipoDocRaw) || (numDocRaw != null && numDocRaw.trim().length() == 8)) {
                        r.tipoDocCliente = "1";
                    } else {
                        r.tipoDocCliente = "0";
                    }
                    r.docCliente = (numDocRaw != null && !numDocRaw.isBlank()) ? numDocRaw.trim() : "00000000";
                    r.nombreCliente = rs.getString("nombre_cliente_final");

                    r.subtotalBd = normalizarMoneda(rs.getBigDecimal("subtotal"));
                    r.igvBd = normalizarMoneda(rs.getBigDecimal("igv"));
                    r.totalBd = normalizarMoneda(rs.getBigDecimal("total"));
                    r.subtotal = r.subtotalBd.doubleValue();
                    r.igv = r.igvBd.doubleValue();
                    r.total = r.totalBd.doubleValue();
                    r.medioPago = rs.getString("metodo_pago");
                    r.estado = "COMPLETADA".equalsIgnoreCase(rs.getString("estado")) ? "1" : "2";
                    lista.add(r);
                }
            }
        }
        return lista;
    }

    public static class VentaRegistroSunat {
        public int idVenta;
        public String periodo;
        public String fechaEmision;
        public String fechaVencimiento;
        public String tipoComprobante;
        public String serie;
        public String numero;
        public String tipoDocCliente;
        public String docCliente;
        public String nombreCliente;
        public double subtotal;
        public double igv;
        public double total;
        public BigDecimal subtotalBd;
        public BigDecimal igvBd;
        public BigDecimal totalBd;
        public String medioPago;
        public String estado;
    }

    public static class TurnoZResumen {
        public int idTurno;
        public BigDecimal saldoInicialBd;
        public BigDecimal totalVentasEfectivoBd;
        public BigDecimal totalVentasTarjetaBd;
        public BigDecimal totalOtrosMediosBd;
        public BigDecimal totalEgresosBd;
        public BigDecimal saldoRealBd;
        public BigDecimal diferenciaBd;
        public LocalDateTime fechaApertura;
    }
}
