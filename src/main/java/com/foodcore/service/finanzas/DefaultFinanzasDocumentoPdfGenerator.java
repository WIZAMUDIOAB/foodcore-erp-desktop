package com.foodcore.service.finanzas;

import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.CuentaPorCobrarDAO;
import com.foodcore.dao.ProveedorDAO;
import com.foodcore.model.Cliente;
import com.foodcore.model.CuentaPorCobrar;
import com.foodcore.model.Insumo;
import com.foodcore.model.Proveedor;
import com.foodcore.tx.impl.JdbcTransactionManager;
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
import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class DefaultFinanzasDocumentoPdfGenerator implements FinanzasDocumentoPdfGenerator {
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private final ProveedorDAO proveedorDAO;
    private final ClienteDAO clienteDAO;
    private final CuentaPorCobrarDAO cuentaPorCobrarDAO;
    private final JdbcTransactionManager.ConnectionSupplier connectionSupplier;

    public DefaultFinanzasDocumentoPdfGenerator(ProveedorDAO proveedorDAO,
                                                ClienteDAO clienteDAO,
                                                CuentaPorCobrarDAO cuentaPorCobrarDAO,
                                                JdbcTransactionManager.ConnectionSupplier connectionSupplier) {
        this.proveedorDAO = proveedorDAO;
        this.clienteDAO = clienteDAO;
        this.cuentaPorCobrarDAO = cuentaPorCobrarDAO;
        this.connectionSupplier = connectionSupplier == null ? ConexionBD::getConnection : connectionSupplier;
    }

    public DefaultFinanzasDocumentoPdfGenerator(ProveedorDAO proveedorDAO,
                                                ClienteDAO clienteDAO,
                                                CuentaPorCobrarDAO cuentaPorCobrarDAO) {
        this(proveedorDAO, clienteDAO, cuentaPorCobrarDAO, ConexionBD::getConnection);
    }

    @Override
    public void generarOrdenCompraPdf(int idProveedor, List<Insumo> insumosRequeridos, File archivoDestino) throws Exception {
        Proveedor proveedor;
        try (Connection conn = connectionSupplier.getConnection()) {
            proveedor = proveedorDAO.buscarPorId(conn, idProveedor).orElse(new Proveedor());
        }
        List<Insumo> insumos = insumosRequeridos == null ? Collections.emptyList() : insumosRequeridos;
        try (FileOutputStream fos = new FileOutputStream(archivoDestino)) {
            Document d = new Document();
            PdfWriter.getInstance(d, fos);
            try {
                d.open();
                Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
                Font sub = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
                Font header = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
                Font body = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
                Paragraph p = new Paragraph("FOODCORE ERP — ORDEN DE COMPRA", titulo);
                p.setAlignment(Element.ALIGN_CENTER);
                d.add(p);
                p = new Paragraph("Fecha de emisión: " + LocalDate.now(ZONA_LIMA).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        + "\nReabastecimiento automático por Stock Mínimo Crítico", sub);
                p.setAlignment(Element.ALIGN_CENTER);
                p.setSpacingAfter(15);
                d.add(p);
                p = new Paragraph("DATOS DEL PROVEEDOR:\nRazón Social: " + proveedor.getRazonSocial()
                        + "\nRUC: " + proveedor.getRuc()
                        + "\nContacto: " + (proveedor.getContacto() != null ? proveedor.getContacto() : "Ventas")
                        + " | Teléfono: " + (proveedor.getTelefono() != null ? proveedor.getTelefono() : "N/D")
                        + "\nDías de Crédito Acordados: " + proveedor.getDiasCredito() + " días\n", sub);
                p.setSpacingAfter(15);
                d.add(p);
                PdfPTable t = new PdfPTable(5);
                t.setWidthPercentage(100);
                t.setWidths(new float[]{1.5f, 4f, 1.5f, 1.5f, 1.5f});
                for (String h : new String[]{"Código", "Insumo Requerido", "Stock Actual", "Stock Mínimo", "Cantidad Pedida"}) {
                    addHeader(t, h, header);
                }
                for (Insumo i : insumos) {
                    t.addCell(new Phrase(i.getCodigo(), body));
                    t.addCell(new Phrase(i.getNombre(), body));
                    BigDecimal actual = i.getStockActualDisponible() != null ? i.getStockActualDisponible() : BigDecimal.ZERO;
                    BigDecimal min = i.getStockMinimo() != null ? i.getStockMinimo() : BigDecimal.ZERO;
                    t.addCell(new Phrase(String.format(Locale.US, "%.2f", actual), body));
                    t.addCell(new Phrase(String.format(Locale.US, "%.2f", min), body));
                    t.addCell(new Phrase(String.format(Locale.US, "%.2f", min.multiply(BigDecimal.valueOf(2)).subtract(actual).max(min)), body));
                }
                d.add(t);
                p = new Paragraph("\nCondiciones: Entrega en almacén central de restaurante. Factura electrónica a emitirse a nombre de FoodCore S.A.C.", sub);
                p.setSpacingBefore(20);
                d.add(p);
            } finally {
                if (d.isOpen()) {
                    d.close();
                }
            }
        }
    }

    @Override
    public void generarEstadoCuentaClientePdf(int idCliente, File archivoDestino) throws Exception {
        Cliente cliente;
        List<CuentaPorCobrar> facturas;
        try (Connection conn = connectionSupplier.getConnection()) {
            cliente = clienteDAO.buscarPorId(conn, idCliente).orElse(new Cliente());
            facturas = cuentaPorCobrarDAO.listarPorCliente(conn, idCliente);
        }
        try (FileOutputStream fos = new FileOutputStream(archivoDestino)) {
            Document d = new Document();
            PdfWriter.getInstance(d, fos);
            try {
                d.open();
                Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
                Font sub = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
                Font header = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
                Font body = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
                Paragraph p = new Paragraph("ESTADO DE CUENTA CORPORATIVO — FOODCORE", titulo);
                p.setAlignment(Element.ALIGN_CENTER);
                d.add(p);
                p = new Paragraph("\nCLIENTE: " + cliente.getRazonSocial() + " (" + cliente.getTipoDocumento() + ": "
                        + cliente.getNumeroDocumento() + ")\nContacto: " + cliente.getContacto()
                        + " | Teléfono: " + cliente.getTelefono()
                        + "\nLímite de Crédito: S/ " + String.format(Locale.US, "%.2f", cliente.getLimiteCredito())
                        + " | Saldo Deudor Total: S/ " + String.format(Locale.US, "%.2f", cliente.getSaldoDeudor()) + "\n", sub);
                p.setSpacingAfter(15);
                d.add(p);
                PdfPTable t = new PdfPTable(6);
                t.setWidthPercentage(100);
                t.setWidths(new float[]{2, 2, 2, 2, 2, 2});
                for (String h : new String[]{"Comprobante", "Emisión", "Vencimiento", "Total S/", "Cobrado S/", "Saldo S/"}) {
                    addHeader(t, h, header);
                }
                for (CuentaPorCobrar c : facturas) {
                    t.addCell(new Phrase(c.getNumeroComprobante(), body));
                    t.addCell(new Phrase(c.getFechaEmision() != null ? c.getFechaEmision().toString() : "-", body));
                    t.addCell(new Phrase(c.getFechaVencimiento() != null ? c.getFechaVencimiento().toString() : "-", body));
                    t.addCell(new Phrase(String.format(Locale.US, "S/ %.2f", c.getMontoTotal()), body));
                    t.addCell(new Phrase(String.format(Locale.US, "S/ %.2f", c.getMontoCobrado()), body));
                    t.addCell(new Phrase(String.format(Locale.US, "S/ %.2f", c.getSaldo()), body));
                }
                d.add(t);
            } finally {
                if (d.isOpen()) {
                    d.close();
                }
            }
        }
    }

    private static void addHeader(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new Color(25, 42, 86));
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }
}
