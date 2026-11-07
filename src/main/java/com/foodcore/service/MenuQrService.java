package com.foodcore.service;

import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.model.Categoria;
import com.foodcore.model.Producto;
import com.foodcore.util.ConexionBD;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MenuQrService {

    private static final Logger LOGGER = Logger.getLogger(MenuQrService.class.getName());
    private final ProductoDAO productoDAO;

    public MenuQrService() {
        this(new ProductoDAOImpl());
    }

    public MenuQrService(ProductoDAO productoDAO) {
        this.productoDAO = productoDAO;
    }

    /**
     * Genera un BufferedImage con el código QR para el texto/URL provisto.
     */
    public BufferedImage generarCodigoQr(String contenido, int ancho, int alto) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(contenido, BarcodeFormat.QR_CODE, ancho, alto);
            return MatrixToImageWriter.toBufferedImage(bitMatrix);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error al generar imagen de codigo QR: " + e.getMessage(), e);
            throw new RuntimeException("Fallo al generar QR: " + e.getMessage(), e);
        }
    }

    /**
     * Exporta el código QR generado a un archivo PNG en disco.
     */
    public File exportarQrPng(String contenido, int ancho, int alto, File archivoDestino) throws IOException {
        if (archivoDestino.getParentFile() != null && !archivoDestino.getParentFile().exists()) {
            boolean created = archivoDestino.getParentFile().mkdirs();
            if (!created) {
                LOGGER.log(Level.FINE, "Directorio para QR ya existia o fue creado.");
            }
        }
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(contenido, BarcodeFormat.QR_CODE, ancho, alto);
            MatrixToImageWriter.writeToPath(bitMatrix, "PNG", archivoDestino.toPath());
            return archivoDestino;
        } catch (Exception e) {
            throw new IOException("Error al escribir archivo QR PNG: " + e.getMessage(), e);
        }
    }

    /**
     * Genera la página web estática responsive del menú digital en HTML.
     */
    public File generarMenuDigitalHtml(File destinoHtml) throws IOException {
        List<Producto> productos = new ArrayList<>();
        
        try (Connection conn = ConexionBD.getConnection()) {
            productos = productoDAO.listarProductosDisponibles(conn);
                    } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error al consultar BD para menu digital, generando con datos demo: " + e.getMessage());
        }

        if (productos.isEmpty()) {
            productos = List.of(
                    new Producto(1, 1, "HAM-01", "Hamburguesa Clásica", "Carne 180g, queso cheddar fundido, cebolla caramelizada", new java.math.BigDecimal("18.50"), java.math.BigDecimal.ZERO, true),
                    new Producto(2, 2, "ALI-01", "Alitas BBQ x6", "Crujientes alitas con salsa barbacoa artesanal", new java.math.BigDecimal("22.00"), java.math.BigDecimal.ZERO, true),
                    new Producto(3, 3, "BEB-01", "Gaseosa Personal 500ml", "Inca Kola o Coca Cola helada", new java.math.BigDecimal("5.00"), java.math.BigDecimal.ZERO, true)
            );
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
            .append("<html lang=\"es\">\n")
            .append("<head>\n")
            .append("  <meta charset=\"UTF-8\">\n")
            .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
            .append("  <title>FoodCore Gastronomía · Menú Digital</title>\n")
            .append("  <style>\n")
            .append("    :root { --primary: #FF5A1F; --dark: #1E293B; --card-bg: #FFFFFF; --bg: #F8FAFC; --text: #334155; }\n")
            .append("    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }\n")
            .append("    body { background-color: var(--bg); color: var(--text); padding-bottom: 40px; }\n")
            .append("    header { background: linear-gradient(135deg, #1E293B 0%, #0F172A 100%); color: white; padding: 32px 20px; text-align: center; border-bottom: 4px solid var(--primary); }\n")
            .append("    header h1 { font-size: 26px; font-weight: 800; letter-spacing: -0.5px; }\n")
            .append("    header p { font-size: 14px; color: #94A3B8; margin-top: 6px; }\n")
            .append("    .badge-live { display: inline-block; background: #10B981; color: white; font-size: 11px; font-weight: 700; padding: 4px 10px; border-radius: 20px; margin-top: 10px; text-transform: uppercase; }\n")
            .append("    .container { max-width: 800px; margin: 0 auto; padding: 20px; }\n")
            .append("    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; margin-top: 20px; }\n")
            .append("    .card { background: var(--card-bg); border-radius: 14px; padding: 18px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); border: 1px solid #E2E8F0; display: flex; flex-direction: column; justify-content: space-between; transition: transform 0.2s; }\n")
            .append("    .card:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(0,0,0,0.08); }\n")
            .append("    .card-title { font-size: 17px; font-weight: 700; color: var(--dark); margin-bottom: 6px; }\n")
            .append("    .card-desc { font-size: 13px; color: #64748B; line-height: 1.4; margin-bottom: 12px; flex-grow: 1; }\n")
            .append("    .card-footer { display: flex; justify-content: space-between; align-items: center; border-top: 1px solid #F1F5F9; padding-top: 10px; }\n")
            .append("    .price { font-size: 18px; font-weight: 800; color: var(--primary); }\n")
            .append("    .btn-pedir { background: #EEF2F6; color: var(--dark); border: none; padding: 6px 14px; border-radius: 8px; font-size: 12px; font-weight: 600; cursor: pointer; }\n")
            .append("    footer { text-align: center; margin-top: 40px; font-size: 12px; color: #94A3B8; }\n")
            .append("  </style>\n")
            .append("</head>\n")
            .append("<body>\n")
            .append("  <header>\n")
            .append("    <h1>FOODCORE RESTOBAR & GRILL</h1>\n")
            .append("    <p>Carta Gastronómica Oficial · Actualizada en Tiempo Real</p>\n")
            .append("    <div class=\"badge-live\">● Disponible para Pedidos</div>\n")
            .append("  </header>\n")
            .append("  <div class=\"container\">\n")
            .append("    <div class=\"grid\">\n");

        for (Producto p : productos) {
            html.append("      <div class=\"card\">\n")
                .append("        <div>\n")
                .append("          <div class=\"card-title\">").append(escapeHtml(p.getNombre())).append("</div>\n")
                .append("          <div class=\"card-desc\">").append(escapeHtml(p.getDescripcion() != null ? p.getDescripcion() : "Plato elaborado con ingredientes frescos")).append("</div>\n")
                .append("        </div>\n")
                .append("        <div class=\"card-footer\">\n")
                .append("          <span class=\"price\">S/ ").append(p.getPrecioVenta()).append("</span>\n")
                .append("          <button class=\"btn-pedir\">Especialidad</button>\n")
                .append("        </div>\n")
                .append("      </div>\n");
        }

        html.append("    </div>\n")
            .append("  </div>\n")
            .append("  <footer>FoodCore ERP v2.0 Enterprise · Todos los derechos reservados</footer>\n")
            .append("</body>\n")
            .append("</html>\n");

        if (destinoHtml.getParentFile() != null && !destinoHtml.getParentFile().exists()) {
            boolean created = destinoHtml.getParentFile().mkdirs();
            if (!created) {
                LOGGER.log(Level.FINE, "Directorio para menu digital ya existia o fue creado.");
            }
        }
        Files.writeString(destinoHtml.toPath(), html.toString(), StandardCharsets.UTF_8);
        return destinoHtml;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
