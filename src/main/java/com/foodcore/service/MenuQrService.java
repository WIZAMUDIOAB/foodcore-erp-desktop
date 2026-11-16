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
            .append("  <link rel=\"stylesheet\" href=\"/vendor/fonts/fonts.css\">\n")
            .append("  <style>\n")
            .append("    :root {\n")
            .append("      --bg-canvas: #FAF9F6;\n")
            .append("      --bg-sidebar: #F3F2EE;\n")
            .append("      --bg-surface: #FFFFFF;\n")
            .append("      --bg-surface-hover: #F5F4F0;\n")
            .append("      --bg-glass-header: rgba(250, 249, 246, 0.85);\n")
            .append("      --bg-glass-summary: rgba(255, 255, 255, 0.94);\n")
            .append("      --border-subtle: #E7E5E4;\n")
            .append("      --border-strong: #D6D3D1;\n")
            .append("      --text-primary: #1C1917;\n")
            .append("      --text-secondary: #57534E;\n")
            .append("      --text-muted: #A8A29E;\n")
            .append("      --text-on-brand: #FFFFFF;\n")
            .append("      --brand-primary: #EA580C;\n")
            .append("      --brand-hover: #C2410C;\n")
            .append("      --brand-soft: rgba(234, 88, 12, 0.10);\n")
            .append("      --status-success: #059669;\n")
            .append("      --status-success-soft: rgba(5, 150, 105, 0.12);\n")
            .append("      --status-warning: #D97706;\n")
            .append("      --status-danger: #DC2626;\n")
            .append("      --hero-bg-start: #1C1917;\n")
            .append("      --hero-bg-end: #C2410C;\n")
            .append("      --hero-overlay-start: rgba(18, 18, 17, 0.78);\n")
            .append("      --hero-overlay-end: rgba(18, 18, 17, 0.28);\n")
            .append("      --shadow-sm: 0 1px 3px rgba(28, 25, 23, 0.06);\n")
            .append("      --shadow-md: 0 6px 18px rgba(28, 25, 23, 0.08);\n")
            .append("      --shadow-lg: 0 -8px 28px rgba(28, 25, 23, 0.10);\n")
            .append("      --radius-md: 10px;\n")
            .append("      --radius-lg: 14px;\n")
            .append("    }\n")
            .append("    .dark, [data-theme=\"dark\"] {\n")
            .append("      --bg-canvas: #121211;\n")
            .append("      --bg-sidebar: #161615;\n")
            .append("      --bg-surface: #1E1D1B;\n")
            .append("      --bg-surface-hover: #292825;\n")
            .append("      --bg-glass-header: rgba(18, 18, 17, 0.85);\n")
            .append("      --bg-glass-summary: rgba(30, 29, 27, 0.94);\n")
            .append("      --border-subtle: #2E2D2A;\n")
            .append("      --border-strong: #44403C;\n")
            .append("      --text-primary: #F5F5F4;\n")
            .append("      --text-secondary: #A8A29E;\n")
            .append("      --text-muted: #78716C;\n")
            .append("      --text-on-brand: #FFFFFF;\n")
            .append("      --brand-primary: #FB923C;\n")
            .append("      --brand-hover: #F97316;\n")
            .append("      --brand-soft: rgba(251, 146, 60, 0.15);\n")
            .append("      --status-success: #34D399;\n")
            .append("      --status-success-soft: rgba(52, 211, 153, 0.14);\n")
            .append("      --status-warning: #FBBF24;\n")
            .append("      --status-danger: #F87171;\n")
            .append("      --hero-bg-start: #121211;\n")
            .append("      --hero-bg-end: #EA580C;\n")
            .append("      --shadow-sm: 0 1px 3px rgba(0, 0, 0, 0.28);\n")
            .append("      --shadow-md: 0 8px 22px rgba(0, 0, 0, 0.36);\n")
            .append("      --shadow-lg: 0 -8px 28px rgba(0, 0, 0, 0.44);\n")
            .append("    }\n")
            .append("    @media (prefers-color-scheme: dark) {\n")
            .append("      :root:not([data-theme=\"light\"]):not(.light) {\n")
            .append("        --bg-canvas: #121211;\n")
            .append("        --bg-sidebar: #161615;\n")
            .append("        --bg-surface: #1E1D1B;\n")
            .append("        --bg-surface-hover: #292825;\n")
            .append("        --bg-glass-header: rgba(18, 18, 17, 0.85);\n")
            .append("        --bg-glass-summary: rgba(30, 29, 27, 0.94);\n")
            .append("        --border-subtle: #2E2D2A;\n")
            .append("        --border-strong: #44403C;\n")
            .append("        --text-primary: #F5F5F4;\n")
            .append("        --text-secondary: #A8A29E;\n")
            .append("        --text-muted: #78716C;\n")
            .append("        --brand-primary: #FB923C;\n")
            .append("        --brand-hover: #F97316;\n")
            .append("        --brand-soft: rgba(251, 146, 60, 0.15);\n")
            .append("        --status-success: #34D399;\n")
            .append("        --status-success-soft: rgba(52, 211, 153, 0.14);\n")
            .append("        --status-warning: #FBBF24;\n")
            .append("        --status-danger: #F87171;\n")
            .append("        --hero-bg-start: #121211;\n")
            .append("        --hero-bg-end: #EA580C;\n")
            .append("      }\n")
            .append("    }\n")
            .append("    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; touch-action: manipulation; }\n")
            .append("    body { background-color: var(--bg-canvas); color: var(--text-primary); padding-bottom: 104px; line-height: 1.5; transition: background-color 0.2s ease, color 0.2s ease; }\n")
            .append("    header, header.glass-header { position: sticky; top: 0; z-index: 100; background: var(--bg-glass-header); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); color: var(--text-primary); padding: 14px 20px; text-align: center; border-bottom: 1px solid var(--border-subtle); box-shadow: var(--shadow-sm); }\n")
            .append("    .header-top-bar { max-width: 960px; margin: 0 auto 8px; display: flex; justify-content: space-between; align-items: center; gap: 12px; }\n")
            .append("    .header-brand-kicker { font-size: 11px; font-weight: 800; letter-spacing: 1.2px; color: var(--brand-primary); text-transform: uppercase; }\n")
            .append("    .header-actions { display: inline-flex; align-items: center; gap: 8px; }\n")
            .append("    .lang-toggle { display: inline-flex; gap: 6px; }\n")
            .append("    .lang-btn, .theme-toggle-btn { min-width: 48px; min-height: 48px; padding: 0 12px; border-radius: var(--radius-md); border: 1px solid var(--border-subtle); background: var(--bg-surface); color: var(--text-secondary); font-size: 12px; font-weight: 700; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; transition: background-color 0.15s, border-color 0.15s, transform 0.1s; }\n")
            .append("    .lang-btn:hover, .theme-toggle-btn:hover { background: var(--bg-surface-hover); color: var(--text-primary); border-color: var(--border-strong); }\n")
            .append("    .lang-btn:active, .theme-toggle-btn:active, .category-tab:active, .btn-pedir:active, .size-pill:active, .btn-reset-preview:active { transform: scale(0.98); }\n")
            .append("    .lang-btn.active { background: var(--brand-primary); color: var(--text-on-brand); border-color: var(--brand-primary); }\n")
            .append("    header h1 { font-size: 24px; font-weight: 800; letter-spacing: -0.02em; color: var(--text-primary); }\n")
            .append("    header p { font-size: 14px; color: var(--text-secondary); margin-top: 4px; }\n")
            .append("    .badge-live { display: inline-flex; align-items: center; gap: 6px; background: var(--status-success-soft); color: var(--status-success); border: 1px solid var(--status-success); font-size: 11px; font-weight: 700; padding: 4px 12px; border-radius: 999px; margin-top: 10px; text-transform: uppercase; font-variant-numeric: tabular-nums; }\n")
            .append("    .container { max-width: 960px; margin: 0 auto; padding: 16px 20px 24px; }\n")
            .append("    .hero-banner { position: relative; width: min(920px, calc(100% - 32px)); height: 144px; margin: 16px auto 4px; border-radius: var(--radius-lg); overflow: hidden; background: linear-gradient(125deg, var(--hero-bg-start) 0%, var(--hero-bg-end) 100%); box-shadow: var(--shadow-md); }\n")
            .append("    .hero-parallax-layer { position: absolute; inset: -40px 0; background: linear-gradient(125deg, var(--hero-bg-start) 0%, var(--hero-bg-end) 100%); will-change: transform; }\n")
            .append("    .hero-banner-overlay { position: relative; z-index: 2; height: 100%; display: flex; flex-direction: column; justify-content: center; gap: 4px; padding: 0 24px; color: var(--text-on-brand); background: linear-gradient(90deg, var(--hero-overlay-start) 0%, var(--hero-overlay-end) 100%); }\n")
            .append("    .hero-kicker { font-size: 11px; font-weight: 700; color: var(--brand-primary); letter-spacing: 1px; text-transform: uppercase; }\n")
            .append("    .hero-title { font-size: 20px; font-weight: 800; letter-spacing: -0.02em; color: var(--text-on-brand); }\n")
            .append("    .category-tabs { display: flex; flex-wrap: nowrap; overflow-x: auto; gap: 8px; margin: 6px 0 18px; padding: 4px 0; scrollbar-width: none; -webkit-overflow-scrolling: touch; }\n")
            .append("    .category-tabs::-webkit-scrollbar { display: none; }\n")
            .append("    .category-tab { flex: 0 0 auto; white-space: nowrap; min-width: 48px; min-height: 48px; padding: 10px 18px; border-radius: 999px; border: 1px solid var(--border-subtle); background: var(--bg-surface); color: var(--text-secondary); font-size: 13px; font-weight: 600; cursor: pointer; box-shadow: var(--shadow-sm); transition: background-color 0.2s, color 0.2s, border-color 0.2s, transform 0.1s; }\n")
            .append("    .category-tab:hover { background: var(--bg-surface-hover); color: var(--text-primary); border-color: var(--border-strong); }\n")
            .append("    .category-tab.active { background: var(--brand-primary); color: var(--text-on-brand); border-color: var(--brand-primary); }\n")
            .append("    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; margin-top: 8px; }\n")
            .append("    .card { background: var(--bg-surface); border-radius: var(--radius-lg); padding: 16px; box-shadow: var(--shadow-sm); border: 1px solid var(--border-subtle); display: flex; flex-direction: column; justify-content: space-between; gap: 10px; transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s; will-change: transform, opacity, filter; }\n")
            .append("    .card:hover { transform: translateY(-2px); box-shadow: var(--shadow-md); border-color: var(--border-strong); }\n")
            .append("    .card-hidden { display: none !important; }\n")
            .append("    .card-img-wrap { position: relative; aspect-ratio: 4 / 3; overflow: hidden; border-radius: var(--radius-md); margin-bottom: 10px; background: linear-gradient(135deg, var(--hero-bg-start) 0%, var(--hero-bg-end) 100%); }\n")
            .append("    .card-img-wrap .parallax-img { position: relative; width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: var(--text-on-brand); will-change: transform; }\n")
            .append("    .card-cat-badge { display: inline-block; align-self: flex-start; font-size: 11px; font-weight: 700; color: var(--brand-primary); background: var(--brand-soft); padding: 4px 10px; border-radius: 999px; margin-bottom: 6px; text-transform: uppercase; letter-spacing: 0.04em; }\n")
            .append("    .card-title { font-size: 16px; font-weight: 700; color: var(--text-primary); margin-bottom: 4px; letter-spacing: -0.01em; }\n")
            .append("    .card-desc { font-size: 13px; color: var(--text-secondary); line-height: 1.45; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }\n")
            .append("    .card-footer { display: flex; justify-content: space-between; align-items: center; border-top: 1px solid var(--border-subtle); padding-top: 12px; gap: 10px; }\n")
            .append("    .price { font-size: 18px; font-weight: 800; color: var(--brand-primary); font-variant-numeric: tabular-nums; }\n")
            .append("    .btn-pedir { min-width: 48px; min-height: 48px; background: var(--bg-surface-hover); color: var(--text-primary); border: 1px solid var(--border-subtle); padding: 8px 16px; border-radius: var(--radius-md); font-size: 13px; font-weight: 600; cursor: pointer; transition: background-color 0.15s, border-color 0.15s, transform 0.1s; }\n")
            .append("    .btn-pedir:hover { background: var(--brand-soft); color: var(--brand-primary); border-color: var(--brand-primary); }\n")
            .append("    .size-pills { display: flex; flex-wrap: wrap; gap: 6px; }\n")
            .append("    .size-pill { min-width: 48px; min-height: 48px; padding: 6px 12px; border-radius: var(--radius-md); border: 1px solid var(--border-subtle); background: var(--bg-surface); color: var(--text-secondary); font-size: 12px; font-weight: 600; cursor: pointer; font-variant-numeric: tabular-nums; transition: background-color 0.15s, color 0.15s, border-color 0.15s, transform 0.1s; }\n")
            .append("    .size-pill:hover { background: var(--bg-surface-hover); color: var(--text-primary); border-color: var(--border-strong); }\n")
            .append("    .size-pill.active { background: var(--brand-soft); border-color: var(--brand-primary); color: var(--brand-primary); }\n")
            .append("    .order-summary-bar { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: min(920px, 100%); border-radius: 16px 16px 0 0; z-index: 95; background: var(--bg-glass-summary); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); border: 1px solid var(--border-subtle); border-bottom: none; box-shadow: var(--shadow-lg); color: var(--text-primary); padding: 12px 20px; }\n")
            .append("    .order-summary-inner { max-width: 880px; margin: 0 auto; display: flex; justify-content: space-between; align-items: center; gap: 12px; }\n")
            .append("    .summary-caption { display: block; font-size: 11px; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.05em; }\n")
            .append("    .summary-count { font-size: 14px; font-weight: 700; color: var(--text-primary); font-variant-numeric: tabular-nums; }\n")
            .append("    .summary-right { display: flex; align-items: center; gap: 12px; }\n")
            .append("    .btn-reset-preview { min-width: 48px; min-height: 48px; padding: 8px 16px; border-radius: var(--radius-md); border: 1px solid var(--border-subtle); background: var(--bg-surface-hover); color: var(--text-primary); font-size: 12px; font-weight: 700; cursor: pointer; }\n")
            .append("    .btn-reset-preview:hover { border-color: var(--brand-primary); color: var(--brand-primary); }\n")
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
            .append("    footer { text-align: center; margin-top: 32px; font-size: 12px; color: var(--text-muted); }\n")
            .append("  </style>\n")
            .append("</head>\n")
            .append("<body>\n")
            .append("  <header class=\"glass-header\">\n")
            .append("    <div class=\"header-top-bar\"><span class=\"header-brand-kicker\">QR MENU</span><div class=\"header-actions\"><button type=\"button\" id=\"btnThemeToggle\" class=\"theme-toggle-btn\" aria-label=\"Cambiar tema\" aria-pressed=\"false\"><svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z\"/></svg></button><div class=\"lang-toggle\"><button type=\"button\" id=\"btnLangEs\" class=\"lang-btn active\" data-lang=\"es\">ES</button><button type=\"button\" id=\"btnLangEn\" class=\"lang-btn\" data-lang=\"en\">EN</button></div></div></div>\n")
            .append("    <h1>FOODCORE RESTOBAR & GRILL</h1>\n")
            .append("    <p id=\"headerSubtitle\">Carta Gastronómica Oficial · Actualizada en Tiempo Real</p>\n")
            .append("    <div id=\"badgeLive\" class=\"badge-live\">● Disponible para Pedidos</div>\n")
            .append("  </header>\n")
            .append("  <section class=\"hero-banner\" aria-hidden=\"true\">\n")
            .append("    <div class=\"hero-parallax-layer parallax-img\"></div>\n")
            .append("    <div class=\"hero-banner-overlay\"><span id=\"heroKicker\" class=\"hero-kicker\">EXPERIENCIA CULINARIA</span><h2 id=\"heroTitle\" class=\"hero-title\">Sabores Artesanales a la Parrilla</h2></div>\n")
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
            java.math.BigDecimal basePrecio = p.getPrecioVenta() != null ? p.getPrecioVenta() : java.math.BigDecimal.ZERO;
            String pReg = basePrecio.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
            String pGra = basePrecio.add(new java.math.BigDecimal("5.00")).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
            String pDob = basePrecio.add(new java.math.BigDecimal("9.00")).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
            html.append("      <div class=\"card morph-in\" data-category=\"").append(escapeHtml(catName))
                .append("\" data-base-price=\"").append(pReg).append("\">\n")
                .append("        <div>\n")
                .append("          <div class=\"card-img-wrap\"><div class=\"parallax-img\" role=\"img\" aria-label=\"").append(escapeHtml(p.getNombre())).append("\"><svg width=\"36\" height=\"36\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.6\" stroke-linecap=\"round\" stroke-linejoin=\"round\" opacity=\"0.7\"><path d=\"M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2\"/><path d=\"M7 2v20\"/><path d=\"M21 15V2v0a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3Zm0 0v7\"/></svg></div></div>\n")
                .append("          <span class=\"card-cat-badge\" data-category=\"").append(escapeHtml(catName)).append("\">").append(escapeHtml(catName)).append("</span>\n")
                .append("          <div class=\"card-title\">").append(escapeHtml(p.getNombre())).append("</div>\n")
                .append("          <div class=\"card-desc\">")
                .append(escapeHtml((p.getDescripcion() != null ? p.getDescripcion() : "Plato elaborado con ingredientes frescos") + opcionesHtml))
                .append("</div>\n")
                .append("        </div>\n")
                .append("        <div class=\"size-pills\">\n")
                .append("          <button type=\"button\" class=\"size-pill active\" data-size-key=\"Regular\" data-price=\"").append(pReg).append("\">Regular</button>\n")
                .append("          <button type=\"button\" class=\"size-pill\" data-size-key=\"Grande\" data-price=\"").append(pGra).append("\">Grande (+S/ 5)</button>\n")
                .append("          <button type=\"button\" class=\"size-pill\" data-size-key=\"Doble\" data-price=\"").append(pDob).append("\">Doble (+S/ 9)</button>\n")
                .append("        </div>\n")
                .append("        <div class=\"card-footer\">\n")
                .append("          <span class=\"price\" data-value=\"").append(pReg).append("\">S/ ").append(pReg).append("</span>\n")
                .append("          <button type=\"button\" class=\"btn-pedir\">Especialidad</button>\n")
                .append("        </div>\n")
                .append("      </div>\n");
        }

        html.append("    </div>\n")
            .append("  </div>\n")
            .append("  <footer id=\"menuFooter\">FoodCore ERP v5.4.0 Enterprise · Todos los derechos reservados</footer>\n")
            .append("  <aside id=\"orderSummaryBar\" class=\"order-summary-bar\">\n")
            .append("    <div class=\"order-summary-inner\">\n")
            .append("      <div><span id=\"lblSummaryCaption\" class=\"summary-caption\">Total estimado</span><strong id=\"orderTotalCount\" class=\"summary-count\">0 ítems</strong></div>\n")
            .append("      <div class=\"summary-right\">\n")
            .append("        <span id=\"orderPreviewTotal\" class=\"price\" data-value=\"0.00\">S/ 0.00</span>\n")
            .append("        <button type=\"button\" id=\"btnResetPreview\" class=\"btn-reset-preview\">Limpiar</button>\n")
            .append("      </div>\n")
            .append("    </div>\n")
            .append("  </aside>\n")
            .append("  <script>\n")
            .append("    (function () {\n")
            .append("      const THEME_STORAGE_KEY = 'fc_theme';\n")
            .append("      const LANG_STORAGE_KEY = 'fc_menu_qr_lang';\n")
            .append("      function resolverTemaInicial() {\n")
            .append("        try {\n")
            .append("          const saved = window.localStorage.getItem(THEME_STORAGE_KEY);\n")
            .append("          if (saved === 'dark' || saved === 'light') return saved;\n")
            .append("        } catch (_e) {}\n")
            .append("        return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';\n")
            .append("      }\n")
            .append("      function aplicarTemaMenuQr(tema) {\n")
            .append("        const esOscuro = tema === 'dark';\n")
            .append("        document.documentElement.classList.toggle('dark', esOscuro);\n")
            .append("        document.documentElement.classList.toggle('light', !esOscuro);\n")
            .append("        document.documentElement.setAttribute('data-theme', esOscuro ? 'dark' : 'light');\n")
            .append("        const btn = document.getElementById('btnThemeToggle');\n")
            .append("        if (btn) {\n")
            .append("          btn.setAttribute('aria-pressed', esOscuro ? 'true' : 'false');\n")
            .append("          btn.innerHTML = esOscuro\n")
            .append("            ? '<svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><circle cx=\"12\" cy=\"12\" r=\"5\"/><path d=\"M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42\"/></svg>'\n")
            .append("            : '<svg width=\"18\" height=\"18\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z\"/></svg>';\n")
            .append("        }\n")
            .append("      }\n")
            .append("      function toggleTheme() {\n")
            .append("        const next = document.documentElement.classList.contains('dark') ? 'light' : 'dark';\n")
            .append("        try { window.localStorage.setItem(THEME_STORAGE_KEY, next); } catch (_e) {}\n")
            .append("        aplicarTemaMenuQr(next);\n")
            .append("        return next;\n")
            .append("      }\n")
            .append("      aplicarTemaMenuQr(resolverTemaInicial());\n")
            .append("      const btnTheme = document.getElementById('btnThemeToggle');\n")
            .append("      if (btnTheme) btnTheme.addEventListener('click', toggleTheme);\n")
            .append("      const I18N_FALLBACK = {\n")
            .append("        es: { pageTitle: 'FoodCore Gastronomía · Menú Digital', subtitle: 'Carta Gastronómica Oficial · Actualizada en Tiempo Real', badgeLive: '● Disponible para Pedidos', heroKicker: 'EXPERIENCIA CULINARIA', heroTitle: 'Sabores Artesanales a la Parrilla', cardAction: 'Especialidad', summaryLabel: 'Total estimado', resetLabel: 'Limpiar', itemSingular: 'ítem', itemPlural: 'ítems', footerText: 'FoodCore ERP v5.4.0 Enterprise · Todos los derechos reservados', categories: { Todos: 'Todos', Hamburguesas: 'Hamburguesas', 'Alitas & Pollo': 'Alitas & Pollo', Bebidas: 'Bebidas', Guarniciones: 'Guarniciones', Especialidades: 'Especialidades' }, sizes: { Regular: 'Regular', Grande: 'Grande (+S/ 5)', Doble: 'Doble (+S/ 9)' } },\n")
            .append("        en: { pageTitle: 'FoodCore Gastronomy · Digital Menu', subtitle: 'Official Gastronomy Menu · Real-Time Updates', badgeLive: '● Available for Orders', heroKicker: 'LIVE CULINARY EXPERIENCE', heroTitle: 'Artisan Flame-Grilled Flavors', cardAction: 'Specialty', summaryLabel: 'Estimated total', resetLabel: 'Clear', itemSingular: 'item', itemPlural: 'items', footerText: 'FoodCore ERP v5.4.0 Enterprise · All rights reserved', categories: { Todos: 'All', Hamburguesas: 'Burgers', 'Alitas & Pollo': 'Wings & Chicken', Bebidas: 'Drinks', Guarniciones: 'Sides', Especialidades: 'Specialties' }, sizes: { Regular: 'Regular', Grande: 'Large (+S/ 5)', Doble: 'Double (+S/ 9)' } }\n")
            .append("      };\n")
            .append("      let activeLang = 'es';\n")
            .append("      let i18nDict = I18N_FALLBACK.es;\n")
            .append("      let previewCount = 0;\n")
            .append("      let previewTotal = 0;\n")
            .append("      function animatePrice(element, startValue, endValue, duration = 400) {\n")
            .append("        if (!element) return;\n")
            .append("        const from = Number(startValue) || 0;\n")
            .append("        const to = Number(endValue) || 0;\n")
            .append("        const totalDuration = Number(duration) > 0 ? Number(duration) : 400;\n")
            .append("        const startTime = performance.now();\n")
            .append("        function step(now) {\n")
            .append("          const p = Math.min((now - startTime) / totalDuration, 1);\n")
            .append("          const cur = from + (to - from) * (1 - Math.pow(1 - p, 3));\n")
            .append("          element.dataset.value = cur.toFixed(2);\n")
            .append("          element.textContent = 'S/ ' + cur.toFixed(2);\n")
            .append("          if (p < 1) requestAnimationFrame(step);\n")
            .append("        }\n")
            .append("        requestAnimationFrame(step);\n")
            .append("      }\n")
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
            .append("      cards.forEach(card => {\n")
            .append("        const priceEl = card.querySelector('.price');\n")
            .append("        card.querySelectorAll('.size-pill').forEach(pill => {\n")
            .append("          pill.addEventListener('click', () => {\n")
            .append("            card.querySelectorAll('.size-pill').forEach(x => x.classList.remove('active'));\n")
            .append("            pill.classList.add('active');\n")
            .append("            animatePrice(priceEl, Number(priceEl.dataset.value || 0), Number(pill.dataset.price), 400);\n")
            .append("          });\n")
            .append("        });\n")
            .append("        const btn = card.querySelector('.btn-pedir');\n")
            .append("        if (btn) {\n")
            .append("          btn.addEventListener('click', () => {\n")
            .append("            const unit = Number((card.querySelector('.size-pill.active') || {}).dataset?.price || priceEl.dataset.value || 0);\n")
            .append("            const prev = previewTotal;\n")
            .append("            previewTotal += unit;\n")
            .append("            previewCount += 1;\n")
            .append("            animatePrice(document.getElementById('orderPreviewTotal'), prev, previewTotal, 400);\n")
            .append("            const cnt = document.getElementById('orderTotalCount');\n")
            .append("            if (cnt) cnt.textContent = previewCount + ' ' + (previewCount === 1 ? i18nDict.itemSingular : i18nDict.itemPlural);\n")
            .append("          });\n")
            .append("        }\n")
            .append("      });\n")
            .append("      const resetBtn = document.getElementById('btnResetPreview');\n")
            .append("      if (resetBtn) {\n")
            .append("        resetBtn.addEventListener('click', () => {\n")
            .append("          const prev = previewTotal;\n")
            .append("          previewTotal = 0;\n")
            .append("          previewCount = 0;\n")
            .append("          animatePrice(document.getElementById('orderPreviewTotal'), prev, 0, 400);\n")
            .append("          const cnt = document.getElementById('orderTotalCount');\n")
            .append("          if (cnt) cnt.textContent = '0 ' + i18nDict.itemPlural;\n")
            .append("        });\n")
            .append("      }\n")
            .append("      function applyTranslations() {\n")
            .append("        document.documentElement.lang = activeLang;\n")
            .append("        if (i18nDict.pageTitle) document.title = i18nDict.pageTitle;\n")
            .append("        const setT = (id, v) => { const el = document.getElementById(id); if (el && v) el.textContent = v; };\n")
            .append("        setT('headerSubtitle', i18nDict.subtitle); setT('badgeLive', i18nDict.badgeLive);\n")
            .append("        setT('heroKicker', i18nDict.heroKicker); setT('heroTitle', i18nDict.heroTitle);\n")
            .append("        setT('lblSummaryCaption', i18nDict.summaryLabel); setT('btnResetPreview', i18nDict.resetLabel);\n")
            .append("        setT('menuFooter', i18nDict.footerText);\n")
            .append("        document.querySelectorAll('.lang-btn').forEach(b => b.classList.toggle('active', b.dataset.lang === activeLang));\n")
            .append("        tabs.forEach(t => { t.textContent = (i18nDict.categories && i18nDict.categories[t.dataset.category]) || t.dataset.category; });\n")
            .append("        document.querySelectorAll('.card-cat-badge').forEach(b => { if (b.dataset.category) b.textContent = (i18nDict.categories && i18nDict.categories[b.dataset.category]) || b.dataset.category; });\n")
            .append("        document.querySelectorAll('.btn-pedir').forEach(b => { b.textContent = i18nDict.cardAction; });\n")
            .append("        document.querySelectorAll('.size-pill').forEach(p => { if (p.dataset.sizeKey && i18nDict.sizes) p.textContent = i18nDict.sizes[p.dataset.sizeKey] || p.textContent; });\n")
            .append("        const cnt = document.getElementById('orderTotalCount');\n")
            .append("        if (cnt) cnt.textContent = previewCount + ' ' + (previewCount === 1 ? i18nDict.itemSingular : i18nDict.itemPlural);\n")
            .append("      }\n")
            .append("      async function setLanguage(lang) {\n")
            .append("        activeLang = lang === 'en' ? 'en' : 'es';\n")
            .append("        try { window.localStorage.setItem(LANG_STORAGE_KEY, activeLang); } catch (_e) {}\n")
            .append("        let loaded = null;\n")
            .append("        for (const u of ['/menu-digital/' + activeLang + '.json', activeLang + '.json']) {\n")
            .append("          try { const r = await fetch(u); if (r.ok) { loaded = await r.json(); break; } } catch (_e) {}\n")
            .append("        }\n")
            .append("        i18nDict = loaded && loaded.categories ? loaded : I18N_FALLBACK[activeLang];\n")
            .append("        applyTranslations();\n")
            .append("      }\n")
            .append("      document.querySelectorAll('.lang-btn').forEach(b => b.addEventListener('click', () => setLanguage(b.dataset.lang)));\n")
            .append("      try { const saved = window.localStorage.getItem(LANG_STORAGE_KEY); if (saved === 'en' || saved === 'es') setLanguage(saved); } catch (_e) {}\n")
            .append("      function updateParallax() {\n")
            .append("        const scrollY = window.scrollY || window.pageYOffset || 0;\n")
            .append("        const heroY = (scrollY * 0.5).toFixed(2);\n")
            .append("        const cardY = Math.max(-36, Math.min(36, scrollY * 0.5)).toFixed(2);\n")
            .append("        document.querySelectorAll('.hero-parallax-layer').forEach(el => { el.style.transform = 'translateY(' + heroY + 'px)'; });\n")
            .append("        document.querySelectorAll('.card-img-wrap .parallax-img').forEach(img => { img.style.transform = 'translateY(' + cardY + 'px)'; });\n")
            .append("      }\n")
            .append("      window.addEventListener('scroll', updateParallax, { passive: true });\n")
            .append("      window.FoodCoreMenuQr = { switchCategory, updateParallax, animatePrice, setLanguage, toggleTheme };\n")
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
