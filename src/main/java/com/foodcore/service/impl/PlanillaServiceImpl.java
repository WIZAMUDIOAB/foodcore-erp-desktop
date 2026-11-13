package com.foodcore.service.impl;

import com.foodcore.dao.ExpedienteUsuarioDAO;
import com.foodcore.dao.MovimientoPagoDAO;
import com.foodcore.dao.impl.ExpedienteUsuarioDAOImpl;
import com.foodcore.dao.impl.MovimientoPagoDAOImpl;
import com.foodcore.model.MovimientoPago;
import com.foodcore.model.PlanillaConsolidada;
import com.foodcore.model.Usuario;
import com.foodcore.service.PlanillaService;
import com.foodcore.util.ConexionBD;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementación de {@link PlanillaService}.
 */
public class PlanillaServiceImpl implements PlanillaService {

    private static final Set<String> TIPOS_VALIDOS = Set.of("BONO", "COMISION", "ADELANTO", "DESCUENTO");

    private final ExpedienteUsuarioDAO expedienteUsuarioDao;
    private final MovimientoPagoDAO movimientoDao;

    public PlanillaServiceImpl() {
        this(new ExpedienteUsuarioDAOImpl(), new MovimientoPagoDAOImpl());
    }

    public PlanillaServiceImpl(ExpedienteUsuarioDAO expedienteUsuarioDao, MovimientoPagoDAO movimientoDao) {
        this.expedienteUsuarioDao = Objects.requireNonNull(expedienteUsuarioDao, "expedienteUsuarioDao requerido");
        this.movimientoDao = Objects.requireNonNull(movimientoDao, "movimientoDao requerido");
    }

    @Override
    public List<Usuario> listarColaboradoresActivos() {
        try (Connection conn = ConexionBD.getConnection()) {
            return expedienteUsuarioDao.listarTodos(conn, true);
        } catch (SQLException e) {
            throw new IllegalStateException("Error al listar colaboradores para planilla: " + e.getMessage(), e);
        }
    }

    @Override
    public MovimientoPago registrarMovimiento(int idUsuario,
                                              String tipoMovimiento,
                                              String concepto,
                                              BigDecimal monto,
                                              LocalDate fecha,
                                              Integer registradoPor,
                                              String observaciones) {
        if (idUsuario <= 0) {
            throw new IllegalArgumentException("Seleccione un colaborador válido.");
        }
        String tipoNorm = tipoMovimiento != null ? tipoMovimiento.trim().toUpperCase(Locale.ROOT) : "";
        if (!TIPOS_VALIDOS.contains(tipoNorm)) {
            throw new IllegalArgumentException("Tipo de movimiento inválido. Use BONO, COMISION, ADELANTO o DESCUENTO.");
        }
        if (concepto == null || concepto.isBlank()) {
            throw new IllegalArgumentException("Ingrese un concepto para el movimiento.");
        }
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a S/ 0.00.");
        }
        LocalDate fechaEfectiva = fecha != null ? fecha : LocalDate.now();
        String periodoMes = YearMonth.from(fechaEfectiva).toString();

        MovimientoPago mov = new MovimientoPago();
        mov.setIdUsuario(idUsuario);
        mov.setPeriodoMes(periodoMes);
        mov.setTipoMovimiento(tipoNorm);
        mov.setConcepto(concepto.trim());
        mov.setMonto(monto.setScale(2, RoundingMode.HALF_UP));
        mov.setFechaMovimiento(fechaEfectiva);
        mov.setRegistradoPor(registradoPor);
        mov.setObservaciones(observaciones != null && !observaciones.isBlank() ? observaciones.trim() : null);

        try (Connection conn = ConexionBD.getConnection()) {
            movimientoDao.insertar(conn, mov);
            return mov;
        } catch (SQLException e) {
            throw new IllegalStateException("Error al registrar movimiento de pago: " + e.getMessage(), e);
        }
    }

    @Override
    public void eliminarMovimiento(long idMovimiento) {
        if (idMovimiento <= 0) {
            throw new IllegalArgumentException("Movimiento inválido.");
        }
        try (Connection conn = ConexionBD.getConnection()) {
            if (!movimientoDao.eliminar(conn, idMovimiento)) {
                throw new IllegalArgumentException("No se encontró el movimiento #" + idMovimiento);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error al eliminar movimiento: " + e.getMessage(), e);
        }
    }

    @Override
    public List<MovimientoPago> listarMovimientosPorPeriodo(String periodoMes) {
        String periodo = normalizarPeriodo(periodoMes);
        try (Connection conn = ConexionBD.getConnection()) {
            return movimientoDao.listarPorPeriodo(conn, periodo);
        } catch (SQLException e) {
            throw new IllegalStateException("Error al listar movimientos de planilla: " + e.getMessage(), e);
        }
    }

    @Override
    public List<PlanillaConsolidada> calcularPlanillaConsolidada(String periodoMes) {
        String periodo = normalizarPeriodo(periodoMes);
        try (Connection conn = ConexionBD.getConnection()) {
            List<Usuario> colaboradores = expedienteUsuarioDao.listarTodos(conn, true);
            List<MovimientoPago> movimientos = movimientoDao.listarPorPeriodo(conn, periodo);
            Map<Integer, List<MovimientoPago>> porUsuario = movimientos.stream()
                    .collect(Collectors.groupingBy(MovimientoPago::getIdUsuario));

            List<PlanillaConsolidada> resultado = new ArrayList<>();
            for (Usuario u : colaboradores) {
                BigDecimal sueldoBase = BigDecimal.valueOf(u.getSueldoBase()).setScale(2, RoundingMode.HALF_UP);
                List<MovimientoPago> movsUsr = porUsuario.getOrDefault(u.getIdUsuario(), List.of());

                BigDecimal bonos = sumarPorTipo(movsUsr, "BONO");
                BigDecimal comisiones = sumarPorTipo(movsUsr, "COMISION");
                BigDecimal adelantos = sumarPorTipo(movsUsr, "ADELANTO");
                BigDecimal descuentos = sumarPorTipo(movsUsr, "DESCUENTO");

                String nombre = u.getNombreEmpleado() != null && !u.getNombreEmpleado().isBlank()
                        ? u.getNombreEmpleado()
                        : u.getUsername();
                String cargo = u.getCargo() != null && !u.getCargo().isBlank()
                        ? u.getCargo()
                        : Optional.ofNullable(u.getNombreRol()).orElse("PERSONAL");

                resultado.add(new PlanillaConsolidada(
                        u.getIdUsuario(), nombre, cargo, periodo,
                        sueldoBase, bonos, comisiones, adelantos, descuentos));
            }
            return resultado;
        } catch (SQLException e) {
            throw new IllegalStateException("Error al calcular planilla consolidada: " + e.getMessage(), e);
        }
    }

    @Override
    public File exportarPlanillaExcel(List<PlanillaConsolidada> filas, String periodoMes, File destino) {
        Objects.requireNonNull(destino, "Archivo destino requerido");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Planilla_" + normalizarPeriodo(periodoMes));
            Row header = sheet.createRow(0);
            String[] cols = {"Colaborador", "Cargo", "Sueldo Base (S/)", "Bonos (S/)",
                    "Comisiones (S/)", "Adelantos (S/)", "Descuentos (S/)", "Neto a Pagar (S/)"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }

            int r = 1;
            for (PlanillaConsolidada p : filas) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(p.nombreEmpleado());
                row.createCell(1).setCellValue(p.cargo());
                row.createCell(2).setCellValue(p.sueldoBase().doubleValue());
                row.createCell(3).setCellValue(p.bonos().doubleValue());
                row.createCell(4).setCellValue(p.comisiones().doubleValue());
                row.createCell(5).setCellValue(p.adelantos().doubleValue());
                row.createCell(6).setCellValue(p.descuentos().doubleValue());
                row.createCell(7).setCellValue(p.netoPagar().doubleValue());
            }

            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }
            try (FileOutputStream out = new FileOutputStream(destino)) {
                wb.write(out);
            }
            return destino;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo exportar planilla Excel: " + e.getMessage(), e);
        }
    }

    @Override
    public File exportarPlanillaPdf(List<PlanillaConsolidada> filas, String periodoMes, File destino) {
        Objects.requireNonNull(destino, "Archivo destino requerido");
        Document document = new Document();
        try (FileOutputStream out = new FileOutputStream(destino)) {
            PdfWriter.getInstance(document, out);
            document.open();

            Font fTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new Color(15, 23, 42));
            Font fSub = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(71, 85, 105));
            Font fHead = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            Font fCell = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

            Paragraph pTit = new Paragraph("FOODCORE ERP - PLANILLA ADMINISTRATIVA INTERNA", fTitulo);
            document.add(pTit);
            Paragraph pSub = new Paragraph(
                    "Período: " + normalizarPeriodo(periodoMes)
                            + "  |  Fórmula: Sueldo Base + Bonos/Comisiones - Adelantos/Descuentos",
                    fSub);
            pSub.setSpacingAfter(12);
            document.add(pSub);

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2.5f, 1.5f, 1.2f, 1.4f, 1.4f, 1.3f});

            String[] headers = {"Colaborador", "Cargo", "Base", "+ Bonos/Com.", "- Adel./Desc.", "Neto Pagar"};
            for (String h : headers) {
                PdfPCell c = new PdfPCell(new Phrase(h, fHead));
                c.setBackgroundColor(new Color(30, 41, 59));
                c.setPadding(6);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(c);
            }

            BigDecimal totalNeto = BigDecimal.ZERO;
            for (PlanillaConsolidada p : filas) {
                table.addCell(new Phrase(p.nombreEmpleado(), fCell));
                table.addCell(new Phrase(p.cargo(), fCell));
                table.addCell(new Phrase("S/ " + p.sueldoBase(), fCell));
                table.addCell(new Phrase("S/ " + p.totalIngresosAdicionales(), fCell));
                table.addCell(new Phrase("S/ " + p.totalEgresos(), fCell));
                table.addCell(new Phrase("S/ " + p.netoPagar(), fCell));
                totalNeto = totalNeto.add(p.netoPagar());
            }
            document.add(table);

            Paragraph pTot = new Paragraph(
                    "\nTOTAL NETO A PAGAR DEL PERÍODO: S/ " + totalNeto.setScale(2, RoundingMode.HALF_UP),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(21, 128, 61)));
            pTot.setSpacingBefore(10);
            document.add(pTot);

            document.close();
            return destino;
        } catch (DocumentException | IOException e) {
            if (document.isOpen()) {
                document.close();
            }
            throw new IllegalStateException("No se pudo exportar planilla PDF: " + e.getMessage(), e);
        }
    }

    private BigDecimal sumarPorTipo(List<MovimientoPago> movs, String tipo) {
        return movs.stream()
                .filter(m -> tipo.equalsIgnoreCase(m.getTipoMovimiento()))
                .map(MovimientoPago::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizarPeriodo(String periodoMes) {
        if (periodoMes == null || periodoMes.isBlank()) {
            return YearMonth.now().toString();
        }
        return YearMonth.parse(periodoMes.trim()).toString();
    }
}
