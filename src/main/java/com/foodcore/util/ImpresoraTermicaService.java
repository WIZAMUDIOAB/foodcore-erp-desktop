package com.foodcore.util;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Pedido;
import com.foodcore.model.Venta;
import com.foodcore.model.enums.Permiso;
import com.foodcore.security.AutorizacionService;
import com.foodcore.security.SesionUsuario;

import javax.print.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servicio de formateo e impresión térmica ESC/POS de 80 mm (48 columnas)
 * conforme a SPEC-FOODCORE-v1.0 (§8.6 y §15.1).
 */
public class ImpresoraTermicaService {

    private static final Logger LOGGER = Logger.getLogger(ImpresoraTermicaService.class.getName());

    public static final int ANCHO_COLUMNAS = 48;
    private static final byte[] ESC_POS_INIT = new byte[]{0x1B, 0x40};
    private static final byte[] ESC_POS_CUT = new byte[]{0x1D, 0x56, 0x41, 0x10};
    private static final byte[] ESC_POS_ABRIR_CAJON = new byte[]{0x1B, 0x70, 0x00, 0x19, (byte) 0xFA};

    /**
     * Devuelve una copia defensiva de los bytes de pulso ESC/POS para abrir el cajón portamonedas.
     */
    public static byte[] getEscPosAbrirCajon() {
        return ESC_POS_ABRIR_CAJON.clone();
    }

    private static final String SEPARADOR_DOBLE = "=".repeat(ANCHO_COLUMNAS);
    private static final String SEPARADOR_SIMPLE = "-".repeat(ANCHO_COLUMNAS);
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final AutorizacionService autorizacionService;
    private final Path rutaLogFallback;

    public ImpresoraTermicaService() {
        this(new AutorizacionService(), Paths.get("logs", "tickets_emitidos.log"));
    }

    public ImpresoraTermicaService(AutorizacionService autorizacionService, Path rutaLogFallback) {
        this.autorizacionService = autorizacionService;
        this.rutaLogFallback = rutaLogFallback;
    }

    /**
     * Genera las líneas de texto exactamente ajustadas a 48 columnas (§8.6).
     * Ninguna línea devuelta excede {@link #ANCHO_COLUMNAS} caracteres.
     */
    public List<String> formatearLineasTicket(Venta venta,
                                              List<DetalleVenta> detalles,
                                              ComprobantePago comprobante,
                                              boolean esReimpresion) throws NegocioException {
        if (venta == null) {
            throw new NegocioException("Los datos de la venta son obligatorios para emitir el ticket.");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw new NegocioException("El ticket debe contener al menos un ítem de detalle.");
        }

        DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(venta.getTotal());
        List<String> lineas = new ArrayList<>();

        lineas.add(SEPARADOR_DOBLE);
        lineas.add(centrar("FOODCORE ERP - FAST FOOD S.A.C."));
        lineas.add(centrar("COMPROBANTE INTERNO NO FISCAL"));
        if (esReimpresion) {
            lineas.add(centrar("*** REIMPRESION AUTORIZADA ***"));
        }
        lineas.add(SEPARADOR_SIMPLE);

        String tipoDoc = (comprobante != null && comprobante.getTipoComprobante() != null)
            ? comprobante.getTipoComprobante() : "TICKET";
        String serieCorr = (comprobante != null && comprobante.getSerie() != null && comprobante.getCorrelativo() != null)
            ? comprobante.getSerie() + "-" + comprobante.getCorrelativo()
            : String.format("T001-%08d", venta.getIdVenta());

        lineas.add(ajustarLinea(tipoDoc + ": " + serieCorr));
        lineas.add(ajustarLinea("FECHA : " + LocalDateTime.now().format(FMT_FECHA)));
        if (comprobante != null && comprobante.getNombreCliente() != null) {
            lineas.add(ajustarLinea("CLIENTE: " + comprobante.getNombreCliente()));
        }
        if (comprobante != null && comprobante.getDocumentoCliente() != null) {
            lineas.add(ajustarLinea("DOC ID : " + comprobante.getDocumentoCliente()));
        }
        String medioPago = venta.getMetodoPago() != null ? venta.getMetodoPago() : "EFECTIVO";
        lineas.add(ajustarLinea("PAGO   : " + medioPago));
        lineas.add(SEPARADOR_SIMPLE);

        // Encabezado de columnas (48 chars: 5 + 1 + 30 + 1 + 11 = 48)
        lineas.add(alinearColumnas("CANT", "DESCRIPCION", "IMPORTE"));
        lineas.add(SEPARADOR_SIMPLE);

        for (DetalleVenta det : detalles) {
            String cantStr = String.format("%3dx", det.getCantidad());
            String baseDesc = det.getNombreProducto() != null ? det.getNombreProducto() : ("ITEM #" + det.getIdProducto());
            String descConVar = (det.getNombreVariante() != null && !det.getNombreVariante().isBlank())
                ? baseDesc + " (" + det.getNombreVariante().trim() + ")"
                : baseDesc;
            String subtotalStr = String.format("S/ %8s",
                DesgloseFiscal.normalizarMoneda(det.getSubtotal()).toPlainString());
            lineas.add(alinearColumnas(cantStr, descConVar, subtotalStr));
            String mods = FormatoPersonalizacionUtil.normalizarModificadoresDetalle(
                det.getModificadoresSeleccionados(), det.getModificadoresTexto());
            if (!mods.isEmpty()) {
                agregarLineasEnvolventes(lineas, "     + " + mods, "       ");
            }
        }

        lineas.add(SEPARADOR_SIMPLE);
        lineas.add(alinearEtiquetaMonto("OP. GRAVADA (SUBTOTAL):", "S/ " + desglose.getSubtotalGravado().toPlainString()));
        lineas.add(alinearEtiquetaMonto("I.G.V. (18%):", "S/ " + desglose.getIgv().toPlainString()));
        lineas.add(SEPARADOR_DOBLE);
        lineas.add(alinearEtiquetaMonto("TOTAL A PAGAR:", "S/ " + desglose.getTotal().toPlainString()));
        lineas.add(SEPARADOR_DOBLE);
        lineas.add(centrar("GRACIAS POR SU PREFERENCIA"));

        return lineas;
    }

    /**
     * Construye el payload binario ESC/POS completo incluyendo secuencia de inicialización
     * ({@code 0x1B 0x40}), cuerpo codificado en CP850 (o degradado a ASCII) y comando de corte
     * ({@code 0x1D 0x56 0x41 0x10}) según §8.6.
     */
    public byte[] generarBytesEscPos(List<String> lineas, boolean forzarDegradacionAscii) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(ESC_POS_INIT);

            Charset charset = obtenerCharsetImpresion(forzarDegradacionAscii);
            for (String linea : lineas) {
                String procesada = (forzarDegradacionAscii || charset.equals(StandardCharsets.US_ASCII))
                    ? degradarAAscii(linea)
                    : linea;
                String truncada = ajustarLinea(procesada);
                baos.write((truncada + "\n").getBytes(charset));
            }
            baos.write("\n\n".getBytes(StandardCharsets.US_ASCII));
            baos.write(ESC_POS_CUT);
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Error construyendo flujo ESC/POS.", ex);
        }
    }

    /**
     * Valida permiso de reimpresión si aplica e imprime en la impresora predeterminada,
     * o escribe en {@code logs/tickets_emitidos.log} si no hay impresora física conectada (§8.6).
     *
     * @return {@code true} si se envió al spooler físico, {@code false} si se derivó al log de respaldo.
     */
    public boolean emitirTicket(Venta venta,
                                List<DetalleVenta> detalles,
                                ComprobantePago comprobante,
                                boolean esReimpresion,
                                SesionUsuario sesion) throws NegocioException {
        if (esReimpresion) {
            autorizacionService.exigir(sesion, Permiso.TICKET_REIMPRIMIR);
        }

        List<String> lineas = formatearLineasTicket(venta, detalles, comprobante, esReimpresion);
        byte[] payload = generarBytesEscPos(lineas, false);

        PrintService printService = PrintServiceLookup.lookupDefaultPrintService();
        if (printService != null) {
            try {
                DocPrintJob job = printService.createPrintJob();
                Doc doc = new SimpleDoc(payload, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
                job.print(doc, null);
                return true;
            } catch (PrintException pe) {
                LOGGER.log(Level.WARNING, "AlertaImpresora: Fallo en spooler físico. Derivando a archivo de respaldo.", pe);
            }
        } else {
            LOGGER.warning("AlertaImpresora: No se detectó impresora térmica predeterminada. Derivando a archivo de respaldo.");
        }

        escribirFallbackEnLog(lineas);
        return false;
    }

    public void escribirFallbackEnLog(List<String> lineas) {
        try {
            Path parent = rutaLogFallback.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String contenido = String.join(System.lineSeparator(), lineas)
                + System.lineSeparator() + System.lineSeparator();
            Files.writeString(
                rutaLogFallback,
                contenido,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException ioEx) {
            LOGGER.log(Level.SEVERE, "Error escribiendo respaldo de ticket en " + rutaLogFallback, ioEx);
        }
    }

    /**
     * Degrada tildes y eñes a ASCII puro para impresoras sin soporte CP850 (§8.6).
     */
    public static String degradarAAscii(String texto) {
        if (texto == null) {
            return "";
        }
        String reemplazado = texto
            .replace('\u00f1', 'n')
            .replace('\u00d1', 'N');
        String normalizado = Normalizer.normalize(reemplazado, Normalizer.Form.NFD);
        return normalizado.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private Charset obtenerCharsetImpresion(boolean forzarAscii) {
        if (forzarAscii) {
            return StandardCharsets.US_ASCII;
        }
        try {
            return Charset.forName("CP850");
        } catch (Exception ex) {
            LOGGER.warning("Charset CP850 no soportado en esta JVM; degradando a US-ASCII.");
            return StandardCharsets.US_ASCII;
        }
    }

    public static String centrar(String texto) {
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.length() >= ANCHO_COLUMNAS) {
            return limpio.substring(0, ANCHO_COLUMNAS);
        }
        int espaciosIzq = (ANCHO_COLUMNAS - limpio.length()) / 2;
        int espaciosDer = ANCHO_COLUMNAS - limpio.length() - espaciosIzq;
        return " ".repeat(espaciosIzq) + limpio + " ".repeat(espaciosDer);
    }

    public static String ajustarLinea(String texto) {
        String limpio = texto == null ? "" : texto;
        if (limpio.length() > ANCHO_COLUMNAS) {
            return limpio.substring(0, ANCHO_COLUMNAS);
        }
        return String.format("%-" + ANCHO_COLUMNAS + "s", limpio);
    }

    public static String alinearColumnas(String colCant, String colDesc, String colImporte) {
        String c = colCant == null ? "" : colCant;
        String d = colDesc == null ? "" : colDesc;
        String i = colImporte == null ? "" : colImporte;

        String cantFija = c.length() > 5 ? c.substring(0, 5) : String.format("%-5s", c);
        String impFijo = i.length() > 11 ? i.substring(0, 11) : String.format("%11s", i);
        int anchoDesc = ANCHO_COLUMNAS - 5 - 1 - 1 - 11; // 30 caracteres
        String descFija = d.length() > anchoDesc ? d.substring(0, anchoDesc) : String.format("%-" + anchoDesc + "s", d);

        return cantFija + " " + descFija + " " + impFijo;
    }

    public static String alinearEtiquetaMonto(String etiqueta, String monto) {
        String etq = etiqueta == null ? "" : etiqueta;
        String mnt = monto == null ? "" : monto;
        int espacioDisponibleEtq = ANCHO_COLUMNAS - mnt.length() - 1;
        if (espacioDisponibleEtq < 1) {
            return ajustarLinea(etq + " " + mnt);
        }
        String etqFija = etq.length() > espacioDisponibleEtq
            ? etq.substring(0, espacioDisponibleEtq)
            : String.format("%" + espacioDisponibleEtq + "s", etq);
        return etqFija + " " + mnt;
    }

    /**
     * Envía pulso eléctrico ESC/POS al cajón monedero al cobrar en EFECTIVO (§4.1 SPEC-FOODCORE-v1.3).
     */
    public static boolean emitirPulsoAperturaCajon(String nombreImpresora) {
        try {
            javax.print.PrintService servicio = null;
            if (nombreImpresora != null && !nombreImpresora.isBlank()) {
                for (javax.print.PrintService ps : javax.print.PrintServiceLookup.lookupPrintServices(null, null)) {
                    if (ps.getName().equalsIgnoreCase(nombreImpresora.trim())) {
                        servicio = ps;
                        break;
                    }
                }
            }
            if (servicio == null) {
                servicio = javax.print.PrintServiceLookup.lookupDefaultPrintService();
            }
            if (servicio != null) {
                javax.print.DocPrintJob job = servicio.createPrintJob();
                javax.print.Doc doc = new javax.print.SimpleDoc(ESC_POS_ABRIR_CAJON, javax.print.DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
                job.print(doc, null);
                LOGGER.info("Pulso de apertura de cajón monedero enviado a impresora: " + servicio.getName());
                return true;
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "No se pudo enviar pulso físico de cajón (entorno sin hardware conectado).", ex);
        }
        return false;
    }

    /**
     * Formatea las líneas del ticket de pre-cuenta de consumo (§8 SPEC Sub-Fase 4C).
     */
    public List<String> formatearLineasPreCuenta(Pedido pedido, List<DetallePedido> detalles) throws NegocioException {
        if (pedido == null) {
            throw new NegocioException("Los datos del pedido son obligatorios para emitir la pre-cuenta.");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw new NegocioException("La pre-cuenta debe contener al menos un ítem.");
        }

        DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(pedido.getTotal());
        List<String> lineas = new ArrayList<>();

        lineas.add(SEPARADOR_DOBLE);
        lineas.add(centrar("FOODCORE RESTOBAR"));
        lineas.add(centrar("PRE-CUENTA DE CONSUMO"));
        lineas.add(centrar("*** NO ES COMPROBANTE DE PAGO ***"));
        lineas.add(SEPARADOR_SIMPLE);

        String mesaStr = pedido.getNumeroMesa() != null ? pedido.getNumeroMesa() : "01";
        lineas.add(ajustarLinea("MESA   : " + mesaStr));
        lineas.add(ajustarLinea("PEDIDO : #" + pedido.getIdPedido()));
        lineas.add(ajustarLinea("FECHA  : " + LocalDateTime.now().format(FMT_FECHA)));
        if (pedido.getClienteNombre() != null && !pedido.getClienteNombre().isBlank()) {
            lineas.add(ajustarLinea("CLIENTE: " + pedido.getClienteNombre()));
        }
        lineas.add(SEPARADOR_SIMPLE);

        lineas.add(alinearColumnas("CANT", "DESCRIPCION", "TOTAL"));
        lineas.add(SEPARADOR_SIMPLE);

        for (DetallePedido item : detalles) {
            String baseNom = item.getNombreProducto() != null ? item.getNombreProducto() : "Plato #" + item.getIdProducto();
            String nomVar = (item.getNombreVariante() != null && !item.getNombreVariante().isBlank())
                ? baseNom + " (" + item.getNombreVariante().trim() + ")"
                : baseNom;
            String totalFmt = String.format("S/ %.2f", item.getSubtotal());
            lineas.add(alinearColumnas(String.valueOf(item.getCantidad()), nomVar, totalFmt));
            String mods = FormatoPersonalizacionUtil.normalizarModificadoresDetalle(
                item.getModificadoresSeleccionados(), item.getModificadoresTexto());
            if (!mods.isEmpty()) {
                agregarLineasEnvolventes(lineas, "  + " + mods, "    ");
            }
            if (item.getNotasPreparacion() != null && !item.getNotasPreparacion().isBlank()) {
                agregarLineasEnvolventes(lineas, " * Nota: " + item.getNotasPreparacion(), "   ");
            }
        }

        lineas.add(SEPARADOR_SIMPLE);
        lineas.add(alinearEtiquetaMonto("OP. GRAVADA :", "S/ " + desglose.getSubtotalGravado().setScale(2, java.math.RoundingMode.HALF_UP)));
        lineas.add(alinearEtiquetaMonto("I.G.V. (18%):", "S/ " + desglose.getIgv().setScale(2, java.math.RoundingMode.HALF_UP)));
        lineas.add(SEPARADOR_DOBLE);
        lineas.add(alinearEtiquetaMonto("TOTAL ESTIMADO:", "S/ " + pedido.getTotal().setScale(2, java.math.RoundingMode.HALF_UP)));
        lineas.add(SEPARADOR_DOBLE);
        lineas.add(centrar("Solicite su Boleta o Factura"));
        lineas.add(centrar("al momento de pagar en caja"));
        lineas.add(centrar("¡Muchas gracias por su visita!"));
        return lineas;
    }

    /**
     * Formatea el ticket térmico de comanda para cocina/bar (48 columnas),
     * imprimiendo explícitamente tamaño/variante y extras (ej. {@code 1x Hamburguesa + Tocino + Extra Queso}).
     */
    public List<String> formatearLineasComandaCocina(Pedido pedido, List<DetallePedido> detalles) throws NegocioException {
        if (pedido == null) {
            throw new NegocioException("Los datos del pedido son obligatorios para imprimir la comanda de cocina.");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw new NegocioException("La comanda de cocina debe contener al menos un ítem.");
        }

        List<String> lineas = new ArrayList<>();
        lineas.add(SEPARADOR_DOBLE);
        lineas.add(centrar("COMANDA DE COCINA (KDS)"));
        lineas.add(SEPARADOR_SIMPLE);
        String mesaStr = pedido.getNumeroMesa() != null ? pedido.getNumeroMesa() : "01";
        lineas.add(ajustarLinea("MESA / CANAL : " + mesaStr));
        lineas.add(ajustarLinea("PEDIDO       : #" + pedido.getIdPedido()));
        lineas.add(ajustarLinea("FECHA        : " + LocalDateTime.now().format(FMT_FECHA)));
        lineas.add(SEPARADOR_SIMPLE);

        for (DetallePedido item : detalles) {
            String descCocina = FormatoPersonalizacionUtil.formatearDescripcionItem(item);
            agregarLineasEnvolventes(lineas, item.getCantidad() + "x " + descCocina, "   ");
            if (item.getNotasPreparacion() != null && !item.getNotasPreparacion().isBlank()) {
                agregarLineasEnvolventes(lineas, " * Nota: " + item.getNotasPreparacion().trim(), "   ");
            }
        }

        lineas.add(SEPARADOR_DOBLE);
        return lineas;
    }

    public boolean imprimirComandaCocina(Pedido pedido, List<DetallePedido> detalles, String nombreImpresora) {
        try {
            List<String> lineas = formatearLineasComandaCocina(pedido, detalles);
            escribirFallbackEnLog(lineas);
            if (nombreImpresora != null && !nombreImpresora.isBlank()) {
                try {
                    byte[] bytesEscPos = generarBytesEscPos(lineas, true);
                    PrintService printService = PrintServiceLookup.lookupDefaultPrintService();
                    if (printService != null) {
                        DocPrintJob job = printService.createPrintJob();
                        Doc doc = new SimpleDoc(bytesEscPos, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
                        job.print(doc, null);
                    }
                } catch (PrintException | RuntimeException pe) {
                    LOGGER.log(Level.WARNING, "No se pudo enviar comanda de cocina al spooler físico", pe);
                }
            }
            return true;
        } catch (NegocioException | RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Error al emitir ticket de comanda para cocina", ex);
            return false;
        }
    }

    private static void agregarLineasEnvolventes(List<String> destino, String texto, String prefijoContinuacion) {
        String restante = texto != null ? texto.stripTrailing() : "";
        boolean primera = true;
        while (!restante.isEmpty()) {
            String lineaBase = primera ? restante : prefijoContinuacion + restante.stripLeading();
            if (lineaBase.length() <= ANCHO_COLUMNAS) {
                destino.add(ajustarLinea(lineaBase));
                break;
            }
            int corte = lineaBase.lastIndexOf(' ', ANCHO_COLUMNAS);
            if (corte <= (primera ? 0 : prefijoContinuacion.length())) {
                corte = ANCHO_COLUMNAS;
            }
            destino.add(ajustarLinea(lineaBase.substring(0, corte)));
            restante = lineaBase.substring(corte).stripLeading();
            primera = false;
        }
    }

    public boolean imprimirPreCuenta(Pedido pedido, List<DetallePedido> detalles, String nombreImpresora) {
        try {
            List<String> lineas = formatearLineasPreCuenta(pedido, detalles);
            escribirFallbackEnLog(lineas);
            if (nombreImpresora != null && !nombreImpresora.isBlank()) {
                try {
                    byte[] bytesEscPos = generarBytesEscPos(lineas, true);
                    PrintService printService = PrintServiceLookup.lookupDefaultPrintService();
                    if (printService != null) {
                        DocPrintJob job = printService.createPrintJob();
                        Doc doc = new SimpleDoc(bytesEscPos, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
                        job.print(doc, null);
                    }
                } catch (Exception pe) {
                    LOGGER.log(Level.WARNING, "No se pudo enviar pre-cuenta al spooler físico", pe);
                }
            }
            return true;
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error al emitir pre-cuenta", ex);
            return false;
        }
    }
}
