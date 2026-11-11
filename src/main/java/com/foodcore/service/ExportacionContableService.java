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
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
            double totalBase = 0.0;
            double totalIgv = 0.0;
            double totalVenta = 0.0;

            for (VentaRegistroSunat r : registros) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(r.periodo);
                row.createCell(1).setCellValue(String.format("M%06d", r.idVenta));
                row.createCell(2).setCellValue(r.fechaEmision);
                row.createCell(3).setCellValue(r.fechaVencimiento);
                row.createCell(4).setCellValue(r.tipoComprobante);
                row.createCell(5).setCellValue(r.serie);
                row.createCell(6).setCellValue(String.format("%08d", r.idVenta));
                row.createCell(7).setCellValue(r.tipoDocCliente);
                row.createCell(8).setCellValue(r.docCliente);
                row.createCell(9).setCellValue(r.nombreCliente);
                row.createCell(10).setCellValue(r.subtotal);
                row.createCell(11).setCellValue(r.igv);
                row.createCell(12).setCellValue(r.total);
                row.createCell(13).setCellValue(r.medioPago);
                row.createCell(14).setCellValue(r.estado);

                totalBase += r.subtotal;
                totalIgv += r.igv;
                totalVenta += r.total;
            }

            // Fila de totales
            Row totRow = sheet.createRow(rowIdx);
            totRow.createCell(9).setCellValue("TOTALES ACUMULADOS:");
            totRow.createCell(10).setCellValue(totalBase);
            totRow.createCell(11).setCellValue(totalIgv);
            totRow.createCell(12).setCellValue(totalVenta);

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
            double totBase = 0.0;
            double totIgv = 0.0;
            double totCompra = 0.0;

            for (Compra c : compras) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(c.getFechaEmision() != null ? c.getFechaEmision().format(DateTimeFormatter.ofPattern("yyyyMM")) : "");
                row.createCell(1).setCellValue(String.format("C%06d", c.getIdCompra()));
                row.createCell(2).setCellValue(c.getFechaEmision() != null ? c.getFechaEmision().toString() : "");
                row.createCell(3).setCellValue("01"); // Factura
                row.createCell(4).setCellValue(c.getSerie());
                row.createCell(5).setCellValue(c.getNumero());
                row.createCell(6).setCellValue("6"); // RUC
                row.createCell(7).setCellValue(c.getRucProveedor());
                row.createCell(8).setCellValue(c.getRazonSocialProveedor());
                row.createCell(9).setCellValue(c.getBaseImponible());
                row.createCell(10).setCellValue(c.getIgv());
                row.createCell(11).setCellValue(c.getTotal());
                row.createCell(12).setCellValue(c.getEstado());

                totBase += c.getBaseImponible();
                totIgv += c.getIgv();
                totCompra += c.getTotal();
            }

            Row totRow = sheet.createRow(rowIdx);
            totRow.createCell(8).setCellValue("TOTAL COMPRAS:");
            totRow.createCell(9).setCellValue(totBase);
            totRow.createCell(10).setCellValue(totIgv);
            totRow.createCell(11).setCellValue(totCompra);

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
                + "COALESCE((SELECT SUM(m.monto) FROM movimiento_caja m WHERE m.id_caja = c.id_caja AND m.tipo_movimiento = 'VENTA' AND COALESCE(m.medio_pago, 'EFECTIVO') = 'EFECTIVO'), 0.00) AS total_ventas_efectivo, "
                + "COALESCE((SELECT SUM(m.monto) FROM movimiento_caja m WHERE m.id_caja = c.id_caja AND m.tipo_movimiento = 'VENTA' AND m.medio_pago = 'TARJETA'), 0.00) AS total_ventas_tarjeta, "
                + "COALESCE((SELECT SUM(m.monto) FROM movimiento_caja m WHERE m.id_caja = c.id_caja AND m.tipo_movimiento = 'VENTA' AND m.medio_pago NOT IN ('EFECTIVO', 'TARJETA')), 0.00) AS total_otros_medios, "
                + "COALESCE((SELECT SUM(ABS(m.monto)) FROM movimiento_caja m WHERE m.id_caja = c.id_caja AND m.tipo_movimiento IN ('EGRESO', 'ANULACION')), 0.00) AS total_egresos "
                + "FROM caja c WHERE DATE(c.fecha_apertura) BETWEEN ? AND ? ORDER BY c.fecha_apertura DESC";

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(inicio));
            ps.setDate(2, Date.valueOf(fin));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TurnoZResumen t = new TurnoZResumen();
                    t.idTurno = rs.getInt("id_caja");
                    t.saldoInicial = rs.getDouble("monto_apertura");
                    t.totalVentasEfectivo = rs.getDouble("total_ventas_efectivo");
                    t.totalVentasTarjeta = rs.getDouble("total_ventas_tarjeta");
                    t.totalOtrosMedios = rs.getDouble("total_otros_medios");
                    t.totalEgresos = rs.getDouble("total_egresos");
                    t.saldoReal = rs.getDouble("monto_cierre");
                    t.diferencia = rs.getDouble("diferencia");
                    if (rs.getTimestamp("fecha_apertura") != null) {
                        t.fechaApertura = rs.getTimestamp("fecha_apertura").toLocalDateTime();
                    }
                    turnos.add(t);
                }
            }
        }

        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(archivoDestino));
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

        double sumEfec = 0.0;
        double sumDigit = 0.0;
        double sumEgr = 0.0;
        double sumReal = 0.0;
        double sumDif = 0.0;

        for (TurnoZResumen t : turnos) {
            table.addCell(new Phrase("#" + t.idTurno, fontB));
            table.addCell(new Phrase(t.fechaApertura != null ? t.fechaApertura.format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) : "-", fontB));
            table.addCell(new Phrase(String.format("S/ %.2f", t.saldoInicial), fontB));
            table.addCell(new Phrase(String.format("S/ %.2f", t.totalVentasEfectivo), fontB));
            double ventasDig = t.totalVentasTarjeta + t.totalOtrosMedios;
            table.addCell(new Phrase(String.format("S/ %.2f", ventasDig), fontB));
            table.addCell(new Phrase(String.format("S/ %.2f", t.totalEgresos), fontB));
            table.addCell(new Phrase(String.format("S/ %.2f", t.saldoReal), fontB));
            table.addCell(new Phrase(String.format("S/ %.2f", t.diferencia), fontB));

            sumEfec += t.totalVentasEfectivo;
            sumDigit += ventasDig;
            sumEgr += t.totalEgresos;
            sumReal += t.saldoReal;
            sumDif += t.diferencia;
        }

        document.add(table);

        Paragraph pTot = new Paragraph(String.format("%nTOTALES CONSOLIDADOS DEL MES:%n"
                + "Total Efectivo Recaudado: S/ %.2f | Total Canales Digitales: S/ %.2f%n"
                + "Total Egresos de Caja: S/ %.2f | Diferencia Neta de Arqueos: S/ %.2f", sumEfec, sumDigit, sumEgr, sumDif), fontTitulo);
        pTot.setSpacingBefore(15);
        document.add(pTot);

        document.close();
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
                c.setCellValue(encabezados.get(i));
                c.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (List<String> fila : filas) {
                Row r = sheet.createRow(rowIdx++);
                for (int col = 0; col < fila.size(); col++) {
                    r.createCell(col).setCellValue(fila.get(col));
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
        PdfWriter.getInstance(document, new FileOutputStream(archivoDestino));
        document.open();

        Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);
        Font fontH = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font fontB = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);

        Paragraph pTit = new Paragraph("FOODCORE ERP — " + tituloReporte.toUpperCase(), fontTitulo);
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
        document.close();
    }

    private List<String> sanitizarCamposCsv(List<String> valores) {
        List<String> out = new ArrayList<>();
        for (String v : valores) {
            if (v == null) {
                out.add("\"\"");
            } else {
                out.add("\"" + v.replace("\"", "\"\"") + "\"");
            }
        }
        return out;
    }

    private List<VentaRegistroSunat> obtenerVentasParaSunat(LocalDate inicio, LocalDate fin) throws Exception {
        List<VentaRegistroSunat> lista = new ArrayList<>();
        String sql = "SELECT v.*, p.cliente_nombre, p.tipo_pedido "
                + "FROM venta v "
                + "LEFT JOIN pedido p ON v.id_pedido = p.id_pedido "
                + "WHERE DATE(v.fecha_hora) BETWEEN ? AND ? "
                + "ORDER BY v.fecha_hora ASC";

        try (Connection conn = ConexionBD.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(inicio));
            ps.setDate(2, Date.valueOf(fin));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    VentaRegistroSunat r = new VentaRegistroSunat();
                    r.idVenta = rs.getInt("id_venta");
                    LocalDate f = rs.getTimestamp("fecha_hora").toLocalDateTime().toLocalDate();
                    r.periodo = f.format(DateTimeFormatter.ofPattern("yyyyMM"));
                    r.fechaEmision = f.toString();
                    r.fechaVencimiento = f.toString();
                    r.tipoComprobante = "03"; // Boleta / Ticket de venta
                    r.serie = "B001";
                    r.tipoDocCliente = "1"; // DNI / Varios
                    String cli = rs.getString("cliente_nombre");
                    r.nombreCliente = cli != null && !cli.isBlank() ? cli : "CLIENTE VARIOS";
                    r.docCliente = "00000000";
                    r.subtotal = rs.getDouble("subtotal");
                    r.igv = rs.getDouble("igv");
                    r.total = rs.getDouble("total");
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
        public String tipoDocCliente;
        public String docCliente;
        public String nombreCliente;
        public double subtotal;
        public double igv;
        public double total;
        public String medioPago;
        public String estado;
    }

    public static class TurnoZResumen {
        public int idTurno;
        public double saldoInicial;
        public double totalVentasEfectivo;
        public double totalVentasTarjeta;
        public double totalOtrosMedios;
        public double totalEgresos;
        public double saldoReal;
        public double diferencia;
        public LocalDateTime fechaApertura;
    }
}
