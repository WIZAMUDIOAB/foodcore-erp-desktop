package com.foodcore.service;

import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.CajaDAOImpl;
import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.CuentaPorCobrarDAO;
import com.foodcore.dao.CuentaPorPagarDAO;
import com.foodcore.dao.HistorialPrecioDAO;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.InsumoDAOImpl;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.ProveedorDAO;
import com.foodcore.dao.VentaDAO;
import com.foodcore.dao.impl.ClienteDAOImpl;
import com.foodcore.dao.impl.CuentaPorCobrarDAOImpl;
import com.foodcore.dao.impl.CuentaPorPagarDAOImpl;
import com.foodcore.dao.impl.HistorialPrecioDAOImpl;
import com.foodcore.dao.impl.ProveedorDAOImpl;
import com.foodcore.dao.impl.VentaDAOImpl;
import com.foodcore.model.Caja;
import com.foodcore.model.Cliente;
import com.foodcore.model.CobroCliente;
import com.foodcore.model.CuentaPorCobrar;
import com.foodcore.model.CuentaPorPagar;
import com.foodcore.model.HistorialPrecio;
import com.foodcore.model.Insumo;
import com.foodcore.model.MovimientoCaja;
import com.foodcore.model.PagoProveedor;
import com.foodcore.model.Proveedor;
import com.foodcore.model.Venta;
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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class FinanzasService {

    private final ProveedorDAO proveedorDAO;
    private final CuentaPorPagarDAO cuentaPorPagarDAO;
    private final ClienteDAO clienteDAO;
    private final CuentaPorCobrarDAO cuentaPorCobrarDAO;
    private final HistorialPrecioDAO historialPrecioDAO;
    private final InsumoDAO insumoDAO;
    private final ProductoDAO productoDAO;
    private final CajaDAO cajaDAO;
    private final VentaDAO ventaDAO;

    public FinanzasService() {
        this.proveedorDAO = new ProveedorDAOImpl();
        this.cuentaPorPagarDAO = new CuentaPorPagarDAOImpl();
        this.clienteDAO = new ClienteDAOImpl();
        this.cuentaPorCobrarDAO = new CuentaPorCobrarDAOImpl();
        this.historialPrecioDAO = new HistorialPrecioDAOImpl();
        this.insumoDAO = new InsumoDAOImpl();
        this.productoDAO = new ProductoDAOImpl();
        this.cajaDAO = new CajaDAOImpl();
        this.ventaDAO = new VentaDAOImpl();
    }

    public FinanzasService(ProveedorDAO proveedorDAO, CuentaPorPagarDAO cuentaPorPagarDAO,
                           ClienteDAO clienteDAO, CuentaPorCobrarDAO cuentaPorCobrarDAO,
                           HistorialPrecioDAO historialPrecioDAO, InsumoDAO insumoDAO,
                           ProductoDAO productoDAO, CajaDAO cajaDAO, VentaDAO ventaDAO) {
        this.proveedorDAO = proveedorDAO;
        this.cuentaPorPagarDAO = cuentaPorPagarDAO;
        this.clienteDAO = clienteDAO;
        this.cuentaPorCobrarDAO = cuentaPorCobrarDAO;
        this.historialPrecioDAO = historialPrecioDAO;
        this.insumoDAO = insumoDAO;
        this.productoDAO = productoDAO;
        this.cajaDAO = cajaDAO;
        this.ventaDAO = ventaDAO;
    }

    public boolean registrarPagoProveedor(int idCuentaPagar, double monto, String metodoPago,
                                          Integer idCaja, int idUsuario, String nota) throws SQLException {
        if (monto <= 0) {
            throw new IllegalArgumentException("El monto a abonar debe ser mayor a 0");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Optional<CuentaPorPagar> cxpOpt = cuentaPorPagarDAO.buscarPorId(conn, idCuentaPagar);
                if (cxpOpt.isEmpty()) {
                    throw new IllegalArgumentException("Cuenta por pagar no encontrada #" + idCuentaPagar);
                }
                CuentaPorPagar cxp = cxpOpt.get();
                if (monto > cxp.getSaldo() + 0.01) {
                    throw new IllegalArgumentException("El monto (S/ " + monto + ") excede el saldo pendiente (S/ " + cxp.getSaldo() + ")");
                }

                PagoProveedor pago = new PagoProveedor();
                pago.setIdCuentaPagar(idCuentaPagar);
                pago.setIdCaja(idCaja);
                pago.setIdUsuario(idUsuario);
                pago.setMonto(monto);
                pago.setMetodoPago(metodoPago);
                pago.setNota(nota);

                boolean pagoOk = cuentaPorPagarDAO.registrarPago(conn, pago);
                if (!pagoOk) {
                    conn.rollback();
                    return false;
                }

                // Si hay caja abierta y se paga con efectivo, registrar egreso en caja
                if (idCaja != null && "EFECTIVO".equalsIgnoreCase(metodoPago)) {
                    Optional<Caja> cajaOpt = cajaDAO.obtenerCajaAbierta(conn);
                    if (cajaOpt.isPresent()) {
                        MovimientoCaja mov = new MovimientoCaja();
                        mov.setIdCaja(cajaOpt.get().getIdCaja());
                        mov.setIdUsuario(idUsuario);
                        mov.setTipoMovimiento("EGRESO");
                        mov.setMonto(BigDecimal.valueOf(monto));
                        mov.setConcepto("PAGO PROVEEDOR: " + cxp.getRazonSocialProveedor() + " (Doc: " + cxp.getNumeroDocumento() + ")");
                        cajaDAO.registrarMovimiento(conn, mov);
                    }
                }

                conn.commit();
                return true;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public boolean registrarCobroCliente(int idCuentaCobrar, double monto, String metodoPago,
                                         Integer idCaja, int idUsuario, String nota) throws SQLException {
        if (monto <= 0) {
            throw new IllegalArgumentException("El monto a cobrar debe ser mayor a 0");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Optional<CuentaPorCobrar> cxcOpt = cuentaPorCobrarDAO.buscarPorId(conn, idCuentaCobrar);
                if (cxcOpt.isEmpty()) {
                    throw new IllegalArgumentException("Cuenta por cobrar no encontrada #" + idCuentaCobrar);
                }
                CuentaPorCobrar cxc = cxcOpt.get();
                if (monto > cxc.getSaldo() + 0.01) {
                    throw new IllegalArgumentException("El monto (S/ " + monto + ") excede el saldo adeudado (S/ " + cxc.getSaldo() + ")");
                }

                CobroCliente cobro = new CobroCliente();
                cobro.setIdCuentaCobrar(idCuentaCobrar);
                cobro.setIdCaja(idCaja);
                cobro.setIdUsuario(idUsuario);
                cobro.setMonto(monto);
                cobro.setMetodoPago(metodoPago);
                cobro.setNota(nota);

                boolean cobroOk = cuentaPorCobrarDAO.registrarCobro(conn, cobro);
                if (!cobroOk) {
                    conn.rollback();
                    return false;
                }

                // Si se cobra en efectivo a caja abierta, registrar ingreso
                if (idCaja != null && "EFECTIVO".equalsIgnoreCase(metodoPago)) {
                    Optional<Caja> cajaOpt = cajaDAO.obtenerCajaAbierta(conn);
                    if (cajaOpt.isPresent()) {
                        MovimientoCaja mov = new MovimientoCaja();
                        mov.setIdCaja(cajaOpt.get().getIdCaja());
                        mov.setIdUsuario(idUsuario);
                        mov.setTipoMovimiento("INGRESO");
                        mov.setMonto(BigDecimal.valueOf(monto));
                        mov.setConcepto("COBRO CLIENTE: " + cxc.getRazonSocialCliente() + " (Comp: " + cxc.getNumeroComprobante() + ")");
                        cajaDAO.registrarMovimiento(conn, mov);
                    }
                }

                conn.commit();
                return true;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public int registrarVentaCreditoCorporativo(Venta venta, int idCliente, int diasCredito) throws SQLException {
        if (idCliente <= 0) {
            throw new IllegalArgumentException("Debe seleccionar un cliente corporativo válido");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Optional<Cliente> clienteOpt = clienteDAO.buscarPorId(conn, idCliente);
                if (clienteOpt.isEmpty()) {
                    throw new IllegalArgumentException("Cliente corporativo no encontrado #" + idCliente);
                }
                Cliente cliente = clienteOpt.get();

                // Validar límite de crédito
                double totalVta = venta.getTotal() != null ? venta.getTotal().doubleValue() : 0.0;
                if (cliente.getLimiteCredito() > 0 && (cliente.getSaldoDeudor() + totalVta) > cliente.getLimiteCredito()) {
                    throw new IllegalStateException("Límite de crédito superado (Límite: S/ " + cliente.getLimiteCredito()
                            + ", Deuda actual: S/ " + cliente.getSaldoDeudor() + ", Total venta: S/ " + totalVta + ")");
                }

                venta.setMetodoPago("CREDITO");
                venta.setMontoRecibido(BigDecimal.ZERO);
                venta.setVuelto(BigDecimal.ZERO);
                int idVenta = ventaDAO.insertarVenta(conn, venta);

                CuentaPorCobrar cxc = new CuentaPorCobrar();
                cxc.setIdCliente(idCliente);
                cxc.setIdVenta(idVenta);
                cxc.setNumeroComprobante("CR-" + String.format("%06d", idVenta));
                cxc.setFechaEmision(LocalDate.now());
                int dias = diasCredito > 0 ? diasCredito : cliente.getDiasCredito();
                cxc.setFechaVencimiento(LocalDate.now().plusDays(dias));
                cxc.setMontoTotal(totalVta);
                cxc.setMontoCobrado(0.0);
                cxc.setEstado("PENDIENTE");
                cxc.setObservaciones("Venta POS a crédito corporativo. Cliente: " + cliente.getRazonSocial());

                cuentaPorCobrarDAO.insertar(conn, cxc);

                conn.commit();
                return idVenta;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public boolean registrarCambioPrecioConMotivo(String tipoEntidad, int idEntidad, String nombreEntidad,
                                                  double precioAnterior, double precioNuevo,
                                                  int idUsuario, String nombreUsuario, String motivo) throws SQLException {
        if (motivo == null || motivo.trim().length() < 5) {
            throw new IllegalArgumentException("El motivo del cambio de precio es obligatorio (mínimo 5 caracteres)");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            conn.setAutoCommit(false);
            try {
                HistorialPrecio hist = new HistorialPrecio();
                hist.setTipoEntidad(tipoEntidad.toUpperCase());
                hist.setIdEntidad(idEntidad);
                hist.setNombreEntidad(nombreEntidad);
                hist.setPrecioAnterior(precioAnterior);
                hist.setPrecioNuevo(precioNuevo);
                hist.setIdUsuario(idUsuario);
                hist.setNombreUsuario(nombreUsuario);
                hist.setMotivo(motivo.trim());

                historialPrecioDAO.insertar(conn, hist);

                if ("PRODUCTO".equalsIgnoreCase(tipoEntidad)) {
                    String sql = "UPDATE producto SET precio_venta = ? WHERE id_producto = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setBigDecimal(1, BigDecimal.valueOf(precioNuevo));
                        ps.setInt(2, idEntidad);
                        ps.executeUpdate();
                    }
                } else if ("INSUMO".equalsIgnoreCase(tipoEntidad)) {
                    String sql = "UPDATE insumo SET costo_unitario = ?, costo_unitario_actual = ? WHERE id_insumo = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setBigDecimal(1, BigDecimal.valueOf(precioNuevo));
                        ps.setBigDecimal(2, BigDecimal.valueOf(precioNuevo));
                        ps.setInt(3, idEntidad);
                        ps.executeUpdate();
                    }
                }

                conn.commit();
                return true;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void generarOrdenCompraPdf(int idProveedor, List<Insumo> insumosRequeridos, File archivoDestino) throws Exception {
        Optional<Proveedor> provOpt;
        try (Connection conn = ConexionBD.getConnection()) {
            provOpt = proveedorDAO.buscarPorId(conn, idProveedor);
        }
        Proveedor prov = provOpt.orElse(new Proveedor());

        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(archivoDestino));
        document.open();

        Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
        Font fontSubtitulo = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
        Font fontHeaderTable = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        Font fontBodyTable = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

        Paragraph pTitulo = new Paragraph("FOODCORE ERP — ORDEN DE COMPRA", fontTitulo);
        pTitulo.setAlignment(Element.ALIGN_CENTER);
        document.add(pTitulo);

        Paragraph pSub = new Paragraph("Fecha de emisión: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + "\nReabastecimiento automático por Stock Mínimo Crítico", fontSubtitulo);
        pSub.setAlignment(Element.ALIGN_CENTER);
        pSub.setSpacingAfter(15);
        document.add(pSub);

        Paragraph pProv = new Paragraph("DATOS DEL PROVEEDOR:\n"
                + "Razón Social: " + prov.getRazonSocial() + "\n"
                + "RUC: " + prov.getRuc() + "\n"
                + "Contacto: " + (prov.getContacto() != null ? prov.getContacto() : "Ventas") + " | Teléfono: " + (prov.getTelefono() != null ? prov.getTelefono() : "N/D") + "\n"
                + "Días de Crédito Acordados: " + prov.getDiasCredito() + " días\n", fontSubtitulo);
        pProv.setSpacingAfter(15);
        document.add(pProv);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.5f, 4.0f, 1.5f, 1.5f, 1.5f});

        String[] headers = {"Código", "Insumo Requerido", "Stock Actual", "Stock Mínimo", "Cantidad Pedida"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, fontHeaderTable));
            cell.setBackgroundColor(new Color(25, 42, 86));
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        for (Insumo ins : insumosRequeridos) {
            table.addCell(new Phrase(ins.getCodigo(), fontBodyTable));
            table.addCell(new Phrase(ins.getNombre(), fontBodyTable));
            double sAct = ins.getStockActualDisponible() != null ? ins.getStockActualDisponible().doubleValue() : 0.0;
            double sMin = ins.getStockMinimo() != null ? ins.getStockMinimo().doubleValue() : 0.0;
            table.addCell(new Phrase(String.format("%.2f", sAct), fontBodyTable));
            table.addCell(new Phrase(String.format("%.2f", sMin), fontBodyTable));
            double cantidadSugerida = Math.max(sMin * 2 - sAct, sMin);
            table.addCell(new Phrase(String.format("%.2f", cantidadSugerida), fontBodyTable));
        }

        document.add(table);

        Paragraph pFooter = new Paragraph("\nCondiciones: Entrega en almacén central de restaurante. Factura electrónica a emitirse a nombre de FoodCore S.A.C.", fontSubtitulo);
        pFooter.setSpacingBefore(20);
        document.add(pFooter);

        document.close();
    }

    public void generarEstadoCuentaClientePdf(int idCliente, File archivoDestino) throws Exception {
        Optional<Cliente> cliOpt;
        List<CuentaPorCobrar> facturas;
        try (Connection conn = ConexionBD.getConnection()) {
            cliOpt = clienteDAO.buscarPorId(conn, idCliente);
            facturas = cuentaPorCobrarDAO.listarPorCliente(conn, idCliente);
        }
        Cliente cli = cliOpt.orElse(new Cliente());

        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(archivoDestino));
        document.open();

        Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
        Font fontSubtitulo = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
        Font fontHeaderTable = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        Font fontBodyTable = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

        Paragraph pTitulo = new Paragraph("ESTADO DE CUENTA CORPORATIVO — FOODCORE", fontTitulo);
        pTitulo.setAlignment(Element.ALIGN_CENTER);
        document.add(pTitulo);

        Paragraph pCli = new Paragraph("\nCLIENTE: " + cli.getRazonSocial() + " (" + cli.getTipoDocumento() + ": " + cli.getNumeroDocumento() + ")\n"
                + "Contacto: " + cli.getContacto() + " | Teléfono: " + cli.getTelefono() + "\n"
                + "Límite de Crédito: S/ " + String.format("%.2f", cli.getLimiteCredito()) + " | Saldo Deudor Total: S/ " + String.format("%.2f", cli.getSaldoDeudor()) + "\n", fontSubtitulo);
        pCli.setSpacingAfter(15);
        document.add(pCli);

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.0f, 2.0f, 2.0f, 2.0f, 2.0f, 2.0f});

        String[] headers = {"Comprobante", "Emisión", "Vencimiento", "Total S/", "Cobrado S/", "Saldo S/"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, fontHeaderTable));
            cell.setBackgroundColor(new Color(25, 42, 86));
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        for (CuentaPorCobrar cxc : facturas) {
            table.addCell(new Phrase(cxc.getNumeroComprobante(), fontBodyTable));
            table.addCell(new Phrase(cxc.getFechaEmision() != null ? cxc.getFechaEmision().toString() : "-", fontBodyTable));
            table.addCell(new Phrase(cxc.getFechaVencimiento() != null ? cxc.getFechaVencimiento().toString() : "-", fontBodyTable));
            table.addCell(new Phrase(String.format("S/ %.2f", cxc.getMontoTotal()), fontBodyTable));
            table.addCell(new Phrase(String.format("S/ %.2f", cxc.getMontoCobrado()), fontBodyTable));
            table.addCell(new Phrase(String.format("S/ %.2f", cxc.getSaldo()), fontBodyTable));
        }

        document.add(table);
        document.close();
    }
}
