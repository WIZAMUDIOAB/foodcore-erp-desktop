package com.foodcore.service;

import com.foodcore.model.Caja;
import com.foodcore.model.InsumoCriticoTurno;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Generador PDF del Acta de Entrega de Turno al cierre de caja (Cierre Z),
 * consolidando arqueo y resumen de insumos agotados o en stock mínimo (v5.2.0).
 */
public class ActaEntregaTurnoService {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public File generarActaEntregaTurnoPdf(Caja caja,
                                            BigDecimal efectivoContado,
                                            BigDecimal diferencia,
                                            List<InsumoCriticoTurno> criticos,
                                            String responsableSalida,
                                            String responsableEntrada,
                                            File archivoDestino) throws Exception {
        Objects.requireNonNull(archivoDestino, "El archivo destino del Acta PDF es obligatorio.");
        if (archivoDestino.getParentFile() != null && !archivoDestino.getParentFile().exists()) {
            if (!archivoDestino.getParentFile().mkdirs() && !archivoDestino.getParentFile().exists()) {
                throw new IllegalStateException("No se pudo crear el directorio para el Acta: " + archivoDestino);
            }
        }

        List<InsumoCriticoTurno> listaCriticos = criticos != null ? criticos : List.of();
        BigDecimal contadoNorm = (efectivoContado != null ? efectivoContado : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal difNorm = (diferencia != null ? diferencia : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        Document document = new Document(PageSize.A4, 36, 36, 40, 40);
        try (FileOutputStream fos = new FileOutputStream(archivoDestino)) {
            PdfWriter.getInstance(document, fos);
            document.open();
            try {

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new Color(17, 24, 39));
            Font fontSub = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(37, 99, 235));
            Font fontBody = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, new Color(31, 41, 55));
            Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, Color.WHITE);

            Paragraph titulo = new Paragraph("FOODCORE ERP - ACTA DE ENTREGA DE TURNO Y RELEVO", fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);

            Paragraph sub = new Paragraph(
                    "Emitido el " + LocalDateTime.now(ZONA_LIMA).format(FMT_FECHA) + " (Hora Lima)",
                    fontBody
            );
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(14f);
            document.add(sub);

            PdfPTable tablaTurno = new PdfPTable(2);
            tablaTurno.setWidthPercentage(100);
            tablaTurno.setSpacingAfter(14f);
            agregarFilaClaveValor(tablaTurno, "Caja / Turno:",
                    (caja != null ? caja.getNombreCaja() + " (Turno #" + caja.getIdCaja() + ")" : "Turno Principal"),
                    fontHeader, fontBody);
            agregarFilaClaveValor(tablaTurno, "Efectivo Contado (Cierre Z):", "S/ " + contadoNorm, fontHeader, fontBody);
            agregarFilaClaveValor(tablaTurno, "Diferencia de Arqueo:", "S/ " + difNorm, fontHeader, fontBody);
            agregarFilaClaveValor(tablaTurno, "Turno Saliente:",
                    responsableSalida != null && !responsableSalida.isBlank() ? responsableSalida : "Cajero Saliente",
                    fontHeader, fontBody);
            agregarFilaClaveValor(tablaTurno, "Turno Entrante:",
                    responsableEntrada != null && !responsableEntrada.isBlank() ? responsableEntrada : "Por asignar",
                    fontHeader, fontBody);
            document.add(tablaTurno);

            long agotados = listaCriticos.stream().filter(InsumoCriticoTurno::isAgotado).count();
            long bajos = listaCriticos.size() - agotados;
            Paragraph secInv = new Paragraph(
                    "Inventario Critico al Relevo (" + agotados + " agotados, " + bajos + " en stock minimo):",
                    fontSub
            );
            secInv.setSpacingAfter(8f);
            document.add(secInv);

            PdfPTable tablaInv = new PdfPTable(6);
            tablaInv.setWidthPercentage(100);
            tablaInv.setWidths(new float[]{1.2f, 2.8f, 0.9f, 1.3f, 1.3f, 1.4f});
            tablaInv.setSpacingAfter(24f);

            String[] headers = {"Codigo", "Insumo", "UM", "Stock Actual", "Stock Min.", "Estado"};
            for (String h : headers) {
                PdfPCell c = new PdfPCell(new Phrase(h, fontHeader));
                c.setBackgroundColor(new Color(31, 41, 55));
                c.setPadding(5f);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                tablaInv.addCell(c);
            }

            if (listaCriticos.isEmpty()) {
                PdfPCell okCell = new PdfPCell(new Phrase(
                        "Sin insumos agotados o por debajo del stock minimo al cierre del turno.", fontBody));
                okCell.setColspan(6);
                okCell.setPadding(8f);
                okCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                tablaInv.addCell(okCell);
            } else {
                for (InsumoCriticoTurno ic : listaCriticos) {
                    tablaInv.addCell(celdaTexto(ic.codigo(), fontBody));
                    tablaInv.addCell(celdaTexto(ic.nombre(), fontBody));
                    tablaInv.addCell(celdaTexto(ic.unidadMedida(), fontBody));
                    tablaInv.addCell(celdaTexto(ic.stockActual() != null ? ic.stockActual().toPlainString() : "0", fontBody));
                    tablaInv.addCell(celdaTexto(ic.stockMinimo() != null ? ic.stockMinimo().toPlainString() : "0", fontBody));
                    tablaInv.addCell(celdaTexto(ic.nivel(), fontBody));
                }
            }
            document.add(tablaInv);

            PdfPTable firmas = new PdfPTable(2);
            firmas.setWidthPercentage(100);
            firmas.setSpacingBefore(18f);
            firmas.addCell(celdaFirma("ENTREGA CONFORME (TURNO SALIENTE)\n\n"
                    + "_______________________________\nNombre y Firma", fontBody));
            firmas.addCell(celdaFirma("RECIBE CONFORME (TURNO ENTRANTE)\n\n"
                    + "_______________________________\nNombre y Firma", fontBody));
            document.add(firmas);
            } finally {
                if (document.isOpen()) {
                    document.close();
                }
            }
        }
        return archivoDestino;
    }

    private void agregarFilaClaveValor(PdfPTable tabla, String clave, String valor, Font fHeader, Font fBody) {
        PdfPCell c1 = new PdfPCell(new Phrase(clave, fHeader));
        c1.setBackgroundColor(new Color(55, 65, 81));
        c1.setPadding(5f);
        tabla.addCell(c1);

        PdfPCell c2 = new PdfPCell(new Phrase(valor != null ? valor : "", fBody));
        c2.setPadding(5f);
        tabla.addCell(c2);
    }

    private PdfPCell celdaTexto(String txt, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(txt != null ? txt : "", font));
        c.setPadding(4f);
        return c;
    }

    private PdfPCell celdaFirma(String txt, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(txt, font));
        c.setBorderWidth(0f);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setPadding(12f);
        return c;
    }
}
