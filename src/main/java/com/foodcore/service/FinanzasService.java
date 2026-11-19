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
import com.foodcore.exception.PeriodoCerradoException;
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
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class FinanzasService {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final BigDecimal TOLERANCIA_REDONDEO = new BigDecimal("0.001");

    private final ProveedorDAO proveedorDAO;
    private final CuentaPorPagarDAO cuentaPorPagarDAO;
    private final ClienteDAO clienteDAO;
    private final CuentaPorCobrarDAO cuentaPorCobrarDAO;
    private final HistorialPrecioDAO historialPrecioDAO;
    private final InsumoDAO insumoDAO;
    private final ProductoDAO productoDAO;
    private final CajaDAO cajaDAO;
    private final VentaDAO ventaDAO;
    private final CierreContableService cierreContableService = new CierreContableService();

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

    private static BigDecimal validarMontoPositivo(double monto, String descripcion) {
        if (Double.isNaN(monto) || Double.isInfinite(monto) || monto <= 0.0) {
            throw new IllegalArgumentException("El " + descripcion + " debe ser un número finito mayor a 0");
        }
        return BigDecimal.valueOf(monto).setScale(2, RoundingMode.HALF_UP);
    }

    private void verificarPeriodoAbierto(Connection conn, LocalDate fecha) throws SQLException {
        try {
            cierreContableService.validarPeriodoAbierto(conn, fecha != null ? fecha : LocalDate.now(ZONA_LIMA));
        } catch (PeriodoCerradoException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    public boolean registrarPagoProveedor(int idCuentaPagar, double monto, String metodoPago,
                                          Integer idCaja, int idUsuario, String nota) throws SQLException {
        BigDecimal montoBd = validarMontoPositivo(monto, "monto a abonar");

        try (Connection conn = ConexionBD.getConnection()) {
            conn.setAutoCommit(false);
            try {
                verificarPeriodoAbierto(conn, LocalDate.now(ZONA_LIMA));

                Optional<CuentaPorPagar> cxpOpt = cuentaPorPagarDAO.buscarPorIdParaActualizar(conn, idCuentaPagar);
                if (cxpOpt.isEmpty()) {
                    throw new IllegalArgumentException("Cuenta por pagar no encontrada #" + idCuentaPagar);
                }
                CuentaPorPagar cxp = cxpOpt.get();
                BigDecimal totalCxp = BigDecimal.valueOf(cxp.getMontoTotal()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal pagadoCxp = BigDecimal.valueOf(cxp.getMontoPagado()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal saldoPendiente = totalCxp.subtract(pagadoCxp).setScale(2, RoundingMode.HALF_UP);
                if (montoBd.compareTo(saldoPendiente.add(TOLERANCIA_REDONDEO)) > 0) {
                    throw new IllegalArgumentException("El monto (S/ " + montoBd + ") excede el saldo pendiente (S/ " + saldoPendiente + ")");
                }

                PagoProveedor pago = new PagoProveedor();
                pago.setIdCuentaPagar(idCuentaPagar);
                pago.setIdCaja(idCaja);
                pago.setIdUsuario(idUsuario);
                pago.setMonto(montoBd.doubleValue());
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
                        mov.setMedioPago("EFECTIVO");
                        mov.setMonto(montoBd);
                        mov.setConcepto("PAGO PROVEEDOR: " + cxp.getRazonSocialProveedor() + " (Doc: " + cxp.getNumeroDocumento() + ")");
                        cajaDAO.registrarMovimiento(conn, mov);
                    }
                }

                conn.commit();
                return true;
            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public boolean registrarCobroCliente(int idCuentaCobrar, double monto, String metodoPago,
                                         Integer idCaja, int idUsuario, String nota) throws SQLException {
        BigDecimal montoBd = validarMontoPositivo(monto, "monto a cobrar");

        try (Connection conn = ConexionBD.getConnection()) {
            conn.setAutoCommit(false);
            try {
                verificarPeriodoAbierto(conn, LocalDate.now(ZONA_LIMA));

                Optional<CuentaPorCobrar> cxcOpt = cuentaPorCobrarDAO.buscarPorIdParaActualizar(conn, idCuentaCobrar);
                if (cxcOpt.isEmpty()) {
                    throw new IllegalArgumentException("Cuenta por cobrar no encontrada #" + idCuentaCobrar);
                }
                CuentaPorCobrar cxc = cxcOpt.get();
                BigDecimal totalCxc = BigDecimal.valueOf(cxc.getMontoTotal()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal cobradoCxc = BigDecimal.valueOf(cxc.getMontoCobrado()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal saldoAdeudado = totalCxc.subtract(cobradoCxc).setScale(2, RoundingMode.HALF_UP);
                if (montoBd.compareTo(saldoAdeudado.add(TOLERANCIA_REDONDEO)) > 0) {
                    throw new IllegalArgumentException("El monto (S/ " + montoBd + ") excede el saldo adeudado (S/ " + saldoAdeudado + ")");
                }

                CobroCliente cobro = new CobroCliente();
                cobro.setIdCuentaCobrar(idCuentaCobrar);
                cobro.setIdCaja(idCaja);
                cobro.setIdUsuario(idUsuario);
                cobro.setMonto(montoBd.doubleValue());
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
                        mov.setMedioPago("EFECTIVO");
                        mov.setMonto(montoBd);
                        mov.setConcepto("COBRO CLIENTE: " + cxc.getRazonSocialCliente() + " (Comp: " + cxc.getNumeroComprobante() + ")");
                        cajaDAO.registrarMovimiento(conn, mov);
                    }
                }

                conn.commit();
                return true;
            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public int registrarVentaCreditoCorporativo(Venta venta, int idCliente, int diasCredito) throws SQLException {
        if (venta == null || venta.getTotal() == null || venta.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El total de la venta a crédito debe ser mayor a 0");
        }
        if (idCliente <= 0) {
            throw new IllegalArgumentException("Debe seleccionar un cliente corporativo válido");
        }
        BigDecimal totalVtaBd = venta.getTotal().setScale(2, RoundingMode.HALF_UP);

        try (Connection conn = ConexionBD.getConnection()) {
            conn.setAutoCommit(false);
            try {
                LocalDate fechaRef = venta.getFechaHora() != null
                        ? venta.getFechaHora().toLocalDate()
                        : LocalDate.now(ZONA_LIMA);
                verificarPeriodoAbierto(conn, fechaRef);

                // Bloquear fila del cliente para evitar carreras sobre el límite de crédito
                try (PreparedStatement psLock = conn.prepareStatement(
                        "SELECT id_cliente FROM cliente WHERE id_cliente = ? FOR UPDATE")) {
                    psLock.setInt(1, idCliente);
                    try (ResultSet rsLock = psLock.executeQuery()) {
                        if (!rsLock.next()) {
                            throw new IllegalArgumentException("Cliente corporativo no encontrado #" + idCliente);
                        }
                    }
                }

                Optional<Cliente> clienteOpt = clienteDAO.buscarPorId(conn, idCliente);
                if (clienteOpt.isEmpty()) {
                    throw new IllegalArgumentException("Cliente corporativo no encontrado #" + idCliente);
                }
                Cliente cliente = clienteOpt.get();

                // Validar límite de crédito con precisión BigDecimal
                BigDecimal limiteCredito = BigDecimal.valueOf(cliente.getLimiteCredito()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal saldoDeudor = BigDecimal.valueOf(cliente.getSaldoDeudor()).setScale(2, RoundingMode.HALF_UP);
                if (limiteCredito.compareTo(BigDecimal.ZERO) > 0
                        && saldoDeudor.add(totalVtaBd).compareTo(limiteCredito) > 0) {
                    throw new IllegalStateException("Límite de crédito superado (Límite: S/ " + limiteCredito
                            + ", Deuda actual: S/ " + saldoDeudor + ", Total venta: S/ " + totalVtaBd + ")");
                }

                venta.setMetodoPago("CREDITO");
                venta.setMontoRecibido(BigDecimal.ZERO);
                venta.setVuelto(BigDecimal.ZERO);
                venta.setTotal(totalVtaBd);
                int idVenta = ventaDAO.insertarVenta(conn, venta);

                CuentaPorCobrar cxc = new CuentaPorCobrar();
                cxc.setIdCliente(idCliente);
                cxc.setIdVenta(idVenta);
                cxc.setNumeroComprobante("CR-" + String.format("%06d", idVenta));
                LocalDate hoy = LocalDate.now(ZONA_LIMA);
                cxc.setFechaEmision(hoy);
                int dias = diasCredito > 0 ? diasCredito : Math.max(1, cliente.getDiasCredito());
                cxc.setFechaVencimiento(hoy.plusDays(dias));
                cxc.setMontoTotal(totalVtaBd.doubleValue());
                cxc.setMontoCobrado(0.0);
                cxc.setEstado("PENDIENTE");
                cxc.setObservaciones("Venta POS a crédito corporativo. Cliente: " + cliente.getRazonSocial());

                cuentaPorCobrarDAO.insertar(conn, cxc);

                conn.commit();
                return idVenta;
            } catch (SQLException | RuntimeException ex) {
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
        if (tipoEntidad == null || tipoEntidad.isBlank()) {
            throw new IllegalArgumentException("El tipo de entidad es obligatorio");
        }
        if (Double.isNaN(precioAnterior) || Double.isInfinite(precioAnterior) || precioAnterior < 0.0) {
            throw new IllegalArgumentException("El precio anterior debe ser un número finito no negativo");
        }
        if (Double.isNaN(precioNuevo) || Double.isInfinite(precioNuevo) || precioNuevo <= 0.0) {
            throw new IllegalArgumentException("El precio nuevo debe ser un número finito mayor a 0");
        }
        if (motivo == null || motivo.trim().length() < 5) {
            throw new IllegalArgumentException("El motivo del cambio de precio es obligatorio (mínimo 5 caracteres)");
        }
        BigDecimal precioNuevoBd = BigDecimal.valueOf(precioNuevo).setScale(4, RoundingMode.HALF_UP);
        BigDecimal precioAnteriorBd = BigDecimal.valueOf(precioAnterior).setScale(4, RoundingMode.HALF_UP);

        try (Connection conn = ConexionBD.getConnection()) {
            conn.setAutoCommit(false);
            try {
                HistorialPrecio hist = new HistorialPrecio();
                hist.setTipoEntidad(tipoEntidad.trim().toUpperCase(Locale.ROOT));
                hist.setIdEntidad(idEntidad);
                hist.setNombreEntidad(nombreEntidad);
                hist.setPrecioAnterior(precioAnteriorBd.doubleValue());
                hist.setPrecioNuevo(precioNuevoBd.doubleValue());
                hist.setIdUsuario(idUsuario);
                hist.setNombreUsuario(nombreUsuario);
                hist.setMotivo(motivo.trim());

                historialPrecioDAO.insertar(conn, hist);

                if ("PRODUCTO".equalsIgnoreCase(tipoEntidad)) {
                    String sql = "UPDATE producto SET precio_venta = ? WHERE id_producto = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setBigDecimal(1, precioNuevoBd.setScale(2, RoundingMode.HALF_UP));
                        ps.setInt(2, idEntidad);
                        ps.executeUpdate();
                    }
                } else if ("INSUMO".equalsIgnoreCase(tipoEntidad)) {
                    String sql = "UPDATE insumo SET costo_unitario = ?, costo_unitario_actual = ? WHERE id_insumo = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setBigDecimal(1, precioNuevoBd);
                        ps.setBigDecimal(2, precioNuevoBd);
                        ps.setInt(3, idEntidad);
                        ps.executeUpdate();
                    }
                }

                conn.commit();
                return true;
            } catch (SQLException | RuntimeException ex) {
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

        try (FileOutputStream fos = new FileOutputStream(archivoDestino)) {
            Document document = new Document();
            PdfWriter.getInstance(document, fos);
            try {
                document.open();

                Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
                Font fontSubtitulo = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
                Font fontHeaderTable = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
                Font fontBodyTable = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

                Paragraph pTitulo = new Paragraph("FOODCORE ERP — ORDEN DE COMPRA", fontTitulo);
                pTitulo.setAlignment(Element.ALIGN_CENTER);
                document.add(pTitulo);

                Paragraph pSub = new Paragraph("Fecha de emisión: " + LocalDate.now(ZONA_LIMA).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
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
                    BigDecimal sAct = ins.getStockActualDisponible() != null ? ins.getStockActualDisponible() : BigDecimal.ZERO;
                    BigDecimal sMin = ins.getStockMinimo() != null ? ins.getStockMinimo() : BigDecimal.ZERO;
                    table.addCell(new Phrase(String.format(Locale.US, "%.2f", sAct), fontBodyTable));
                    table.addCell(new Phrase(String.format(Locale.US, "%.2f", sMin), fontBodyTable));
                    BigDecimal sugerida = sMin.multiply(BigDecimal.valueOf(2)).subtract(sAct).max(sMin);
                    table.addCell(new Phrase(String.format(Locale.US, "%.2f", sugerida), fontBodyTable));
                }

                document.add(table);

                Paragraph pFooter = new Paragraph("\nCondiciones: Entrega en almacén central de restaurante. Factura electrónica a emitirse a nombre de FoodCore S.A.C.", fontSubtitulo);
                pFooter.setSpacingBefore(20);
                document.add(pFooter);
            } finally {
                if (document.isOpen()) {
                    document.close();
                }
            }
        }
    }

    public void generarEstadoCuentaClientePdf(int idCliente, File archivoDestino) throws Exception {
        Optional<Cliente> cliOpt;
        List<CuentaPorCobrar> facturas;
        try (Connection conn = ConexionBD.getConnection()) {
            cliOpt = clienteDAO.buscarPorId(conn, idCliente);
            facturas = cuentaPorCobrarDAO.listarPorCliente(conn, idCliente);
        }
        Cliente cli = cliOpt.orElse(new Cliente());

        try (FileOutputStream fos = new FileOutputStream(archivoDestino)) {
            Document document = new Document();
            PdfWriter.getInstance(document, fos);
            try {
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
                        + "Límite de Crédito: S/ " + String.format(Locale.US, "%.2f", cli.getLimiteCredito()) + " | Saldo Deudor Total: S/ " + String.format(Locale.US, "%.2f", cli.getSaldoDeudor()) + "\n", fontSubtitulo);
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
                    table.addCell(new Phrase(String.format(Locale.US, "S/ %.2f", cxc.getMontoTotal()), fontBodyTable));
                    table.addCell(new Phrase(String.format(Locale.US, "S/ %.2f", cxc.getMontoCobrado()), fontBodyTable));
                    table.addCell(new Phrase(String.format(Locale.US, "S/ %.2f", cxc.getSaldo()), fontBodyTable));
                }

                document.add(table);
            } finally {
                if (document.isOpen()) {
                    document.close();
                }
            }
        }
    }
}
