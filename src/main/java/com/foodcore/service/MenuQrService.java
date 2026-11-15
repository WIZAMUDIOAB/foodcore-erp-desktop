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
     * Retorna dinámicamente la IP de red local del servidor para acceso desde smartphones (v4.3.2).
     */
    public static String obtenerIpServidor() {
        try {
            java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
            String host = localHost.getHostAddress();
            if (host != null && !host.equals("127.0.0.1") && !host.startsWith("127.")) {
                return host;
            }
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            return host != null ? host : "127.0.0.1";
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    /**
     * Construye la URL de red oficial para acceder al Menú Digital desde smartphones (v4.3.2).
     */
    public static String construirUrlMenuDigital(int puertoHttps) {
        String urlTunel = TunnelService.construirUrlPublicaEndpoint("/api/public/menu-digital");
        if (!urlTunel.isBlank()) {
            return urlTunel;
        }
        String ip = obtenerIpServidor();
        return "https://" + ip + ":" + puertoHttps + "/api/public/menu-digital";
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
     * Construye el HTML responsive del menú digital consultando MySQL en vivo (v4.6.0 Fase 2 — cero caché estático).
     *
     * @return documento HTML completo con precios y disponibilidad actualizados en tiempo real.
     */
    public String construirMenuDigitalHtmlDinamico() {
        List<Producto> productos = new ArrayList<>();
        
        try (Connection conn = ConexionBD.getConnection()) {
            productos = new com.foodcore.service.impl.PersonalizacionProductoServiceImpl(
                new com.foodcore.tx.impl.JdbcTransactionManager(),
                productoDAO,
                new com.foodcore.dao.impl.ProductoPersonalizacionDAOImpl()
            ).listarProductosDisponiblesConOpciones(conn);
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

        java.util.LinkedHashSet<String> categorias = new java.util.LinkedHashSet<>();
        categorias.add("Todos");
        for (Producto p : productos) {
            categorias.add(resolverCategoriaProducto(p));
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
            .append("<html lang=\"es\">\n")
            .append("<head>\n")
            .append("  <meta charset=\"UTF-8\">\n")
            .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
            .append("  <title>FoodCore Gastronomía · Menú Digital</title>\n")
            .append("  <style>\n")
            .append("    :root { --primary: #FF5A1F; --dark: #1E293B; --card-bg: #FFFFFF; --bg: #F8FAFC; --text: #334155; --border: #E2E8F0; }\n")
            .append("    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; touch-action: manipulation; }\n")
            .append("    body { background-color: var(--bg); color: var(--text); padding-bottom: 64px; }\n")
            .append("    header, header.glass-header { position: sticky; top: 0; z-index: 100; background: rgba(15, 23, 42, 0.78); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); color: white; padding: 22px 20px; text-align: center; border-bottom: 1px solid rgba(255, 255, 255, 0.14); box-shadow: 0 8px 32px rgba(15, 23, 42, 0.25); }\n")
            .append("    header h1 { font-size: 26px; font-weight: 800; letter-spacing: -0.5px; }\n")
            .append("    header p { font-size: 14px; color: #94A3B8; margin-top: 6px; }\n")
            .append("    .badge-live { display: inline-block; background: #10B981; color: white; font-size: 11px; font-weight: 700; padding: 4px 10px; border-radius: 20px; margin-top: 10px; text-transform: uppercase; }\n")
            .append("    .container { max-width: 860px; margin: 0 auto; padding: 20px; }\n")
            .append("    .hero-banner { position: relative; max-width: 860px; height: 140px; margin: 16px auto 4px; border-radius: 16px; overflow: hidden; }\n")
            .append("    .hero-parallax-layer { position: absolute; top: -80px; left: 0; width: 100%; height: 300px; object-fit: cover; will-change: transform; }\n")
            .append("    .hero-banner-overlay { position: relative; z-index: 2; height: 100%; display: flex; flex-direction: column; justify-content: center; padding: 0 24px; color: #FFFFFF; background: linear-gradient(90deg, rgba(15, 23, 42, 0.78) 0%, rgba(15, 23, 42, 0.25) 100%); }\n")
            .append("    .card-img-wrap { position: relative; height: 108px; overflow: hidden; border-radius: 10px; margin-bottom: 10px; background: #0F172A; }\n")
            .append("    .parallax-img { width: 100%; height: 220px; margin-top: -56px; object-fit: cover; display: block; will-change: transform; }\n")
            .append("    .category-tabs { display: flex; flex-wrap: wrap; gap: 10px; margin: 8px 0 18px; }\n")
            .append("    .category-tab { min-width: 48px; min-height: 48px; padding: 10px 16px; border-radius: 999px; border: 1px solid var(--border); background: var(--card-bg); color: var(--dark); font-size: 13px; font-weight: 600; cursor: pointer; transition: background-color 0.2s, color 0.2s, transform 0.1s; }\n")
            .append("    .category-tab.active { background: var(--primary); color: #FFFFFF; border-color: var(--primary); }\n")
            .append("    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; margin-top: 16px; }\n")
            .append("    .card { background: var(--card-bg); border-radius: 14px; padding: 18px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); border: 1px solid #E2E8F0; display: flex; flex-direction: column; justify-content: space-between; transition: transform 0.2s; will-change: transform, opacity, filter; }\n")
            .append("    .card:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(0,0,0,0.08); }\n")
            .append("    .card-hidden { display: none !important; }\n")
            .append("    .card-cat-badge { display: inline-block; align-self: flex-start; font-size: 11px; font-weight: 700; color: var(--primary); background: rgba(255, 90, 31, 0.12); padding: 3px 9px; border-radius: 999px; margin-bottom: 8px; text-transform: uppercase; }\n")
            .append("    .card-title { font-size: 17px; font-weight: 700; color: var(--dark); margin-bottom: 6px; }\n")
            .append("    .card-desc { font-size: 13px; color: #64748B; line-height: 1.4; margin-bottom: 12px; flex-grow: 1; }\n")
            .append("    .card-footer { display: flex; justify-content: space-between; align-items: center; border-top: 1px solid #F1F5F9; padding-top: 10px; }\n")
            .append("    .price { font-size: 18px; font-weight: 800; color: var(--primary); font-variant-numeric: tabular-nums; }\n")
            .append("    .btn-pedir { min-width: 48px; min-height: 48px; background: #EEF2F6; color: var(--dark); border: none; padding: 8px 14px; border-radius: 8px; font-size: 12px; font-weight: 600; cursor: pointer; }\n")
            .append("    @keyframes morphIn { 0% { opacity: 0; transform: scale(0.94) translateY(10px); filter: blur(4px); } 100% { opacity: 1; transform: scale(1) translateY(0); filter: blur(0); } }\n")
            .append("    @keyframes morphOut { 0% { opacity: 1; transform: scale(1); filter: blur(0); } 100% { opacity: 0; transform: scale(0.94) translateY(-6px); filter: blur(4px); } }\n")
            .append("    .card.morph-in { animation: morphIn 0.34s cubic-bezier(0.22, 1, 0.36, 1) both; }\n")
            .append("    .card.morph-out { animation: morphOut 0.2s cubic-bezier(0.4, 0, 1, 1) both; pointer-events: none; }\n")
            .append("    .card.morph-in:nth-child(1) { animation-delay: 0ms; }\n")
            .append("    .card.morph-in:nth-child(2) { animation-delay: 50ms; }\n")
            .append("    .card.morph-in:nth-child(3) { animation-delay: 100ms; }\n")
            .append("    .card.morph-in:nth-child(4) { animation-delay: 150ms; }\n")
            .append("    .card.morph-in:nth-child(5) { animation-delay: 200ms; }\n")
            .append("    .card.morph-in:nth-child(6) { animation-delay: 250ms; }\n")
            .append("    footer { text-align: center; margin-top: 40px; font-size: 12px; color: #94A3B8; }\n")
            .append("  </style>\n")
            .append("</head>\n")
            .append("<body>\n")
            .append("  <header class=\"glass-header\">\n")
            .append("    <h1>FOODCORE RESTOBAR & GRILL</h1>\n")
            .append("    <p>Carta Gastronómica Oficial · Actualizada en Tiempo Real</p>\n")
            .append("    <div class=\"badge-live\">● Disponible para Pedidos</div>\n")
            .append("  </header>\n")
            .append("  <section class=\"hero-banner\" aria-hidden=\"true\">\n")
            .append("    <img class=\"hero-parallax-layer parallax-img\" src=\"data:image/svg+xml;utf8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1200 300'%3E%3Cdefs%3E%3ClinearGradient id='g' x1='0' y1='0' x2='1' y2='1'%3E%3Cstop offset='0%25' stop-color='%231E293B'/%3E%3Cstop offset='100%25' stop-color='%23FF5A1F'/%3E%3C/linearGradient%3E%3C/defs%3E%3Crect width='1200' height='300' fill='url(%23g)'/%3E%3C/svg%3E\" alt=\"\">\n")
            .append("    <div class=\"hero-banner-overlay\"><span style=\"font-size:11px;font-weight:700;color:#FDBA74;letter-spacing:1px;\">EXPERIENCIA CULINARIA</span><h2 style=\"font-size:20px;font-weight:800;\">Sabores Artesanales a la Parrilla</h2></div>\n")
            .append("  </section>\n")
            .append("  <div class=\"container\">\n")
            .append("    <nav id=\"categoryTabs\" class=\"category-tabs\" aria-label=\"Categorías\">\n");

        for (String cat : categorias) {
            String activeCls = "Todos".equals(cat) ? "category-tab active" : "category-tab";
            html.append("      <button type=\"button\" class=\"").append(activeCls)
                .append("\" data-category=\"").append(escapeHtml(cat)).append("\">")
                .append(escapeHtml(cat)).append("</button>\n");
        }

        html.append("    </nav>\n")
            .append("    <div id=\"menuGrid\" class=\"grid\">\n");

        for (Producto p : productos) {
            String catName = resolverCategoriaProducto(p);
            StringBuilder opcionesHtml = new StringBuilder();
            if (!p.getVariantes().isEmpty()) {
                List<String> vars = new ArrayList<>();
                for (com.foodcore.model.ProductoVariante v : p.getVariantes()) {
                    vars.add(v.getNombreVariante() + " S/ " + v.calcularPrecioEfectivo(p.getPrecioVenta()));
                }
                opcionesHtml.append(" · Tamaños: ").append(String.join(" | ", vars));
            }
            if (!p.getModificadores().isEmpty()) {
                opcionesHtml.append(" · +Extras configurables");
            }
            html.append("      <div class=\"card morph-in\" data-category=\"").append(escapeHtml(catName))
                .append("\" data-base-price=\"").append(p.getPrecioVenta()).append("\">\n")
                .append("        <div>\n")
                .append("          <div class=\"card-img-wrap\"><img class=\"parallax-img\" loading=\"lazy\" src=\"data:image/svg+xml;utf8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 400 220'%3E%3Cdefs%3E%3ClinearGradient id='g' x1='0' y1='0' x2='1' y2='1'%3E%3Cstop offset='0%25' stop-color='%231E293B'/%3E%3Cstop offset='100%25' stop-color='%23FF5A1F'/%3E%3C/linearGradient%3E%3C/defs%3E%3Crect width='400' height='220' fill='url(%23g)'/%3E%3C/svg%3E\" alt=\"").append(escapeHtml(p.getNombre())).append("\"></div>\n")
                .append("          <span class=\"card-cat-badge\">").append(escapeHtml(catName)).append("</span>\n")
                .append("          <div class=\"card-title\">").append(escapeHtml(p.getNombre())).append("</div>\n")
                .append("          <div class=\"card-desc\">")
                .append(escapeHtml((p.getDescripcion() != null ? p.getDescripcion() : "Plato elaborado con ingredientes frescos") + opcionesHtml))
                .append("</div>\n")
                .append("        </div>\n")
                .append("        <div class=\"card-footer\">\n")
                .append("          <span class=\"price\" data-value=\"").append(p.getPrecioVenta()).append("\">S/ ").append(p.getPrecioVenta()).append("</span>\n")
                .append("          <button type=\"button\" class=\"btn-pedir\">Especialidad</button>\n")
                .append("        </div>\n")
                .append("      </div>\n");
        }

        html.append("    </div>\n")
            .append("  </div>\n")
            .append("  <footer>FoodCore ERP v5.4.0 Enterprise · Todos los derechos reservados</footer>\n")
            .append("  <script>\n")
            .append("    (function () {\n")
            .append("      const tabs = document.querySelectorAll('.category-tab');\n")
            .append("      const cards = Array.from(document.querySelectorAll('#menuGrid .card'));\n")
            .append("      function switchCategory(cat) {\n")
            .append("        tabs.forEach(t => t.classList.toggle('active', t.dataset.category === cat));\n")
            .append("        cards.forEach(c => { c.classList.remove('morph-in'); c.classList.add('morph-out'); });\n")
            .append("        window.setTimeout(() => {\n")
            .append("          let visIdx = 0;\n")
            .append("          cards.forEach(c => {\n")
            .append("            const show = cat === 'Todos' || c.dataset.category === cat;\n")
            .append("            c.classList.toggle('card-hidden', !show);\n")
            .append("            c.classList.remove('morph-out');\n")
            .append("            if (show) {\n")
            .append("              c.style.animationDelay = (visIdx < 6 ? visIdx * 50 : 0) + 'ms';\n")
            .append("              visIdx++;\n")
            .append("              void c.offsetWidth;\n")
            .append("              c.classList.add('morph-in');\n")
            .append("            }\n")
            .append("          });\n")
            .append("        }, 180);\n")
            .append("      }\n")
            .append("      tabs.forEach(t => t.addEventListener('click', () => switchCategory(t.dataset.category)));\n")
            .append("      function updateParallax() {\n")
            .append("        const scrollY = window.scrollY || window.pageYOffset || 0;\n")
            .append("        const ty = (scrollY * 0.5).toFixed(2);\n")
            .append("        document.querySelectorAll('.parallax-img, .hero-parallax-layer').forEach(img => { img.style.transform = 'translateY(' + ty + 'px)'; });\n")
            .append("      }\n")
            .append("      window.addEventListener('scroll', updateParallax, { passive: true });\n")
            .append("      window.FoodCoreMenuQr = { switchCategory, updateParallax };\n")
            .append("    })();\n")
            .append("  </script>\n")
            .append("</body>\n")
            .append("</html>\n");

        return html.toString();
    }

    private String resolverCategoriaProducto(Producto p) {
        if (p.getNombreCategoria() != null && !p.getNombreCategoria().isBlank()) {
            return p.getNombreCategoria().trim();
        }
        return switch (p.getIdCategoria()) {
            case 1 -> "Hamburguesas";
            case 2 -> "Alitas & Pollo";
            case 3 -> "Bebidas";
            case 4 -> "Guarniciones";
            default -> "Especialidades";
        };
    }

    /**
     * Exporta a archivo el HTML responsive del menú digital generado en vivo desde MySQL.
     */
    public File generarMenuDigitalHtml(File destinoHtml) throws IOException {
        if (destinoHtml.getParentFile() != null && !destinoHtml.getParentFile().exists()) {
            boolean created = destinoHtml.getParentFile().mkdirs();
            if (!created) {
                LOGGER.log(Level.FINE, "Directorio para menu digital ya existia o fue creado.");
            }
        }
        Files.writeString(destinoHtml.toPath(), construirMenuDigitalHtmlDinamico(), StandardCharsets.UTF_8);
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
