package com.foodcore.service;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Servicio de alertas por correo electrónico SMTP ante fallos críticos de infraestructura
 * según Sub-Fase C Ítem 6 (v5.3.0):
 * <ul>
 *   <li>MySQL sin respuesta por más de 1 minuto (&gt; 60 s).</li>
 *   <li>Espacio libre en disco por debajo de 5 GB.</li>
 *   <li>Uso de RAM &ge; 90% durante más de 5 minutos (&gt; 300 s).</li>
 * </ul>
 */
public final class AlertaService {

    private static final Logger LOGGER = Logger.getLogger(AlertaService.class.getName());

    public static final long UMBRAL_MYSQL_CAIDO_MS = 60_000L;
    public static final long UMBRAL_DISCO_LIBRE_BYTES = 5L * 1024L * 1024L * 1024L; // 5 GB
    public static final double UMBRAL_RAM_PORCENTAJE = 90.0;
    public static final long UMBRAL_RAM_ALTA_MS = 5L * 60_000L; // 5 minutos
    public static final int MAX_HISTORIAL_ALERTAS = 500;
    private static final int SMTP_TIMEOUT_MS = 8_000;
    private static final Pattern EMAIL_SEGURO = Pattern.compile(
            "^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,253}\\.[A-Za-z]{2,63}$");
    private static final Pattern HOST_SEGURO = Pattern.compile("^[A-Za-z0-9._:-]{1,253}$");

    private static final AlertaService GLOBAL = new AlertaService();

    /**
     * Configuración SMTP y destinatario de alertas del dueño.
     */
    public record ConfiguracionAlertaEmail(
            String smtpHost,
            int smtpPort,
            String smtpUser,
            String smtpPassword,
            boolean tlsEnabled,
            String destinatario,
            boolean habilitado) {
    }

    /**
     * Registro inmutable de una alerta despachada.
     */
    public record AlertaEmitida(
            TipoAlerta tipo,
            String destinatario,
            String asunto,
            String cuerpo,
            long timestampMs,
            boolean enviadoPorRed) {
    }

    /**
     * Categorías de alerta crítica monitoreadas.
     */
    public enum TipoAlerta {
        MYSQL_CAIDO,
        DISCO_BAJO,
        RAM_ALTA,
        PRUEBA
    }

    /**
     * Contrato funcional desacoplado para despacho SMTP (productivo o simulado en pruebas).
     */
    @FunctionalInterface
    public interface EmailSender {
        boolean enviar(ConfiguracionAlertaEmail config, String destinatario, String asunto, String cuerpo)
                throws IOException;
    }

    private final AtomicReference<ConfiguracionAlertaEmail> configActual = new AtomicReference<>();
    private final Deque<AlertaEmitida> historialAlertas = new ArrayDeque<>();
    private final Object monitorLock = new Object();

    private record PendienteEnvio(TipoAlerta tipo, String asunto, String cuerpo, long timestampMs) {
    }

    private LongSupplier clockMillis;
    private EmailSender emailSender;

    private long inicioFalloMysqlMs = -1L;
    private boolean alertaMysqlEnviada = false;
    private boolean alertaDiscoEnviada = false;
    private long inicioRamAltaMs = -1L;
    private boolean alertaRamEnviada = false;

    public AlertaService() {
        this(System::currentTimeMillis, AlertaService::enviarCorreoSmtpNativo);
    }

    public AlertaService(LongSupplier clockMillis, EmailSender emailSender) {
        this.clockMillis = clockMillis != null ? clockMillis : System::currentTimeMillis;
        this.emailSender = emailSender != null ? emailSender : AlertaService::enviarCorreoSmtpNativo;
        this.configActual.set(cargarConfiguracionDesdeClasspath());
    }

    public static ConfiguracionAlertaEmail getConfiguracionGlobal() {
        return GLOBAL.getConfiguracion();
    }

    public static void actualizarConfiguracionGlobal(ConfiguracionAlertaEmail nuevaConfig, boolean persistirEnDisco) {
        GLOBAL.actualizarConfiguracion(nuevaConfig, persistirEnDisco);
    }

    public static List<AlertaEmitida> evaluarGlobalSnapshot(SistemaMonitorService.EstadoSistemaSnapshot snapshot) {
        return GLOBAL.evaluarSnapshot(snapshot);
    }

    public static AlertaEmitida enviarAlertaPruebaGlobal() {
        return GLOBAL.enviarAlertaPrueba();
    }

    public static List<AlertaEmitida> getHistorialAlertasGlobal() {
        return GLOBAL.getHistorialAlertas();
    }

    public static void setEmailSenderGlobal(EmailSender emailSender) {
        GLOBAL.setEmailSender(emailSender);
    }

    public static void setClockMillisGlobal(LongSupplier clockMillis) {
        GLOBAL.setClockMillis(clockMillis);
    }

    public static void reiniciarEstadoGlobal() {
        GLOBAL.reiniciarEstado();
    }

    public ConfiguracionAlertaEmail getConfiguracion() {
        return configActual.get();
    }

    /**
     * Actualiza la configuración SMTP en memoria y opcionalmente la persiste en
     * {@code logs/alertas-smtp.properties} para mantener los ajustes entre reinicios.
     */
    public void actualizarConfiguracion(ConfiguracionAlertaEmail nuevaConfig, boolean persistirEnDisco) {
        Objects.requireNonNull(nuevaConfig, "La configuración de alertas no puede ser nula.");
        if (nuevaConfig.destinatario() != null && !nuevaConfig.destinatario().isBlank()) {
            validarEmailSeguro(nuevaConfig.destinatario(), "destinatario");
        }
        if (nuevaConfig.smtpUser() != null && !nuevaConfig.smtpUser().isBlank()
                && nuevaConfig.smtpUser().contains("@")) {
            validarEmailSeguro(nuevaConfig.smtpUser(), "remitente SMTP");
        }
        if (nuevaConfig.smtpHost() != null && !nuevaConfig.smtpHost().isBlank()) {
            validarHostSeguro(nuevaConfig.smtpHost());
        }
        configActual.set(nuevaConfig);
        if (persistirEnDisco) {
            guardarConfiguracionEnArchivo(Path.of("logs", "alertas-smtp.properties"), nuevaConfig);
        }
    }

    public List<AlertaEmitida> getHistorialAlertas() {
        synchronized (monitorLock) {
            return List.copyOf(historialAlertas);
        }
    }

    public void setClockMillis(LongSupplier clockMillis) {
        synchronized (monitorLock) {
            this.clockMillis = clockMillis != null ? clockMillis : System::currentTimeMillis;
        }
    }

    public void setEmailSender(EmailSender emailSender) {
        synchronized (monitorLock) {
            this.emailSender = emailSender != null ? emailSender : AlertaService::enviarCorreoSmtpNativo;
        }
    }

    /**
     * Restaura contadores de incidentes, reloj, sender y configuración inicial desde classpath.
     */
    public void reiniciarEstado() {
        synchronized (monitorLock) {
            this.clockMillis = System::currentTimeMillis;
            this.emailSender = AlertaService::enviarCorreoSmtpNativo;
            this.inicioFalloMysqlMs = -1L;
            this.alertaMysqlEnviada = false;
            this.alertaDiscoEnviada = false;
            this.inicioRamAltaMs = -1L;
            this.alertaRamEnviada = false;
            this.historialAlertas.clear();
            this.configActual.set(cargarConfiguracionDesdeClasspath());
        }
    }

    /**
     * Evalúa métricas actuales contra los 3 umbrales críticos y dispara alertas SMTP cuando corresponda.
     * Libera {@code monitorLock} durante la E/S de red y solo marca cada alerta como enviada si la entrega
     * SMTP tuvo éxito (permitiendo reintento automático si falló la red).
     *
     * @return lista de alertas nuevas emitidas durante esta evaluación.
     */
    public List<AlertaEmitida> evaluar(boolean mysqlUp, double ramPorcentaje, long discoLibreBytes) {
        List<PendienteEnvio> pendientes = new ArrayList<>();
        EmailSender senderSnapshot;
        ConfiguracionAlertaEmail cfgSnapshot;

        synchronized (monitorLock) {
            long ahoraMs = clockMillis.getAsLong();
            senderSnapshot = this.emailSender;
            cfgSnapshot = this.configActual.get();

            // 1. MySQL caído por más de 1 minuto (> 60_000 ms)
            if (!mysqlUp) {
                if (inicioFalloMysqlMs < 0L) {
                    inicioFalloMysqlMs = ahoraMs;
                } else if (!alertaMysqlEnviada && (ahoraMs - inicioFalloMysqlMs) > UMBRAL_MYSQL_CAIDO_MS) {
                    long segsCaido = (ahoraMs - inicioFalloMysqlMs) / 1000L;
                    pendientes.add(new PendienteEnvio(
                            TipoAlerta.MYSQL_CAIDO,
                            "[FoodCore ERP ALERTA] MySQL no responde hace " + segsCaido + "s",
                            "El servidor MySQL de FoodCore ERP no responde hace " + segsCaido
                                    + " segundos (superó el umbral de 1 minuto). Instante: "
                                    + Instant.ofEpochMilli(ahoraMs) + ".",
                            ahoraMs
                    ));
                }
            } else {
                inicioFalloMysqlMs = -1L;
                alertaMysqlEnviada = false;
            }

            // 2. Espacio libre en disco < 5 GB
            if (discoLibreBytes >= 0L && discoLibreBytes < UMBRAL_DISCO_LIBRE_BYTES) {
                if (!alertaDiscoEnviada) {
                    double gbLibres = discoLibreBytes / (1024.0 * 1024.0 * 1024.0);
                    String gbTexto = String.format(Locale.US, "%.2f", gbLibres);
                    pendientes.add(new PendienteEnvio(
                            TipoAlerta.DISCO_BAJO,
                            "[FoodCore ERP ALERTA] Espacio en disco crítico (" + gbTexto + " GB libres)",
                            "El volumen principal del servidor tiene solo " + gbTexto
                                    + " GB disponibles (umbral mínimo requerido: 5.00 GB). Libere espacio o archive backups.",
                            ahoraMs
                    ));
                }
            } else if (discoLibreBytes >= UMBRAL_DISCO_LIBRE_BYTES) {
                alertaDiscoEnviada = false;
            }

            // 3. Uso de RAM >= 90% durante más de 5 minutos (> 300_000 ms)
            if (ramPorcentaje >= UMBRAL_RAM_PORCENTAJE) {
                if (inicioRamAltaMs < 0L) {
                    inicioRamAltaMs = ahoraMs;
                } else if (!alertaRamEnviada && (ahoraMs - inicioRamAltaMs) > UMBRAL_RAM_ALTA_MS) {
                    long minAlta = (ahoraMs - inicioRamAltaMs) / 60_000L;
                    String ramTexto = String.format(Locale.US, "%.1f", ramPorcentaje);
                    pendientes.add(new PendienteEnvio(
                            TipoAlerta.RAM_ALTA,
                            "[FoodCore ERP ALERTA] Uso de RAM al " + ramTexto + "% por más de 5 min",
                            "La memoria RAM del servidor se mantuvo en " + ramTexto
                                    + "% durante " + minAlta + " minutos (umbral: >= 90% por > 5 minutos).",
                            ahoraMs
                    ));
                }
            } else {
                inicioRamAltaMs = -1L;
                alertaRamEnviada = false;
            }
        }

        List<AlertaEmitida> nuevas = new ArrayList<>();
        for (PendienteEnvio p : pendientes) {
            AlertaEmitida emitida = despacharAlertaSinLock(cfgSnapshot, senderSnapshot, p);
            if (emitida != null) {
                nuevas.add(emitida);
                synchronized (monitorLock) {
                    registrarEnHistorialLocked(emitida);
                    if (emitida.enviadoPorRed()) {
                        marcarBanderaEnviadaLocked(p.tipo());
                    }
                }
            }
        }
        return nuevas;
    }

    /**
     * Evalúa una instantánea del sistema capturada por {@link SistemaMonitorService}.
     */
    public List<AlertaEmitida> evaluarSnapshot(SistemaMonitorService.EstadoSistemaSnapshot snapshot) {
        if (snapshot == null) {
            return List.of();
        }
        return evaluar(snapshot.mysqlUp(), snapshot.ramPorcentaje(), snapshot.discoLibreBytes());
    }

    /**
     * Envía un correo de prueba inmediato con la configuración SMTP vigente sin retener {@code monitorLock}
     * durante la E/S de red.
     */
    public AlertaEmitida enviarAlertaPrueba() {
        long ahoraMs;
        EmailSender senderSnapshot;
        ConfiguracionAlertaEmail cfgSnapshot;
        synchronized (monitorLock) {
            ahoraMs = clockMillis.getAsLong();
            senderSnapshot = this.emailSender;
            cfgSnapshot = this.configActual.get();
        }
        PendienteEnvio p = new PendienteEnvio(
                TipoAlerta.PRUEBA,
                "[FoodCore ERP] Prueba de Alertas SMTP v5.3.0",
                "Mensaje de verificación de alertas de infraestructura FoodCore ERP v5.3.0 emitido el "
                        + Instant.ofEpochMilli(ahoraMs) + ".",
                ahoraMs
        );
        AlertaEmitida emitida = despacharAlertaSinLock(cfgSnapshot, senderSnapshot, p);
        if (emitida != null) {
            synchronized (monitorLock) {
                registrarEnHistorialLocked(emitida);
            }
        }
        return emitida;
    }

    private AlertaEmitida despacharAlertaSinLock(ConfiguracionAlertaEmail cfg,
                                                 EmailSender sender,
                                                 PendienteEnvio p) {
        if (cfg == null || !cfg.habilitado() || cfg.destinatario() == null || cfg.destinatario().isBlank()) {
            return null;
        }
        String destinatarioLimpio = cfg.destinatario().trim();
        boolean enviadoRed = false;
        try {
            validarEmailSeguro(destinatarioLimpio, "destinatario");
            enviadoRed = sender.enviar(cfg, destinatarioLimpio, p.asunto(), p.cuerpo());
        } catch (IOException | RuntimeException ex) {
            LOGGER.log(Level.WARNING, "No se pudo entregar correo SMTP a " + sanitizarCabecera(destinatarioLimpio)
                    + ": " + ex.getMessage(), ex);
        }
        return new AlertaEmitida(p.tipo(), destinatarioLimpio, p.asunto(), p.cuerpo(), p.timestampMs(), enviadoRed);
    }

    private void registrarEnHistorialLocked(AlertaEmitida registro) {
        while (historialAlertas.size() >= MAX_HISTORIAL_ALERTAS) {
            historialAlertas.removeFirst();
        }
        historialAlertas.addLast(registro);
    }

    private void marcarBanderaEnviadaLocked(TipoAlerta tipo) {
        switch (tipo) {
            case MYSQL_CAIDO -> alertaMysqlEnviada = true;
            case DISCO_BAJO -> alertaDiscoEnviada = true;
            case RAM_ALTA -> alertaRamEnviada = true;
            case PRUEBA -> {
                // Sin bandera persistente de supresión
            }
        }
    }

    /**
     * Cliente SMTP mínimo con JDK estándar (soporta SMTP plano, SMTPS puerto 465 y STARTTLS puerto 587)
     * con verificación estricta de hostname TLS ({@code HTTPS}) y prevención de SMTP CRLF Injection.
     */
    static boolean enviarCorreoSmtpNativo(
            ConfiguracionAlertaEmail cfg, String destinatario, String asunto, String cuerpo) throws IOException {
        if (cfg.smtpHost() == null || cfg.smtpHost().isBlank()) {
            throw new IOException("Host SMTP no configurado");
        }
        String hostLimpio = validarHostSeguro(cfg.smtpHost());
        String destLimpio = validarEmailSeguro(destinatario, "destinatario");
        String remitente = (cfg.smtpUser() != null && !cfg.smtpUser().isBlank())
                ? cfg.smtpUser().trim()
                : "alertas@foodcore.pe";
        remitente = validarEmailSeguro(remitente, "remitente SMTP");

        try (Socket socket = abrirSocketSmtp(cfg, hostLimpio)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            leerRespuestaSmtp(reader, 220);
            enviarComando(writer, "EHLO foodcore.local");
            leerRespuestaSmtp(reader, 250);

            if (cfg.tlsEnabled() && cfg.smtpPort() != 465) {
                enviarComando(writer, "STARTTLS");
                leerRespuestaSmtp(reader, 220);
                SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                try (SSLSocket tlsSocket = (SSLSocket) sslFactory.createSocket(
                        socket, hostLimpio, cfg.smtpPort(), true)) {
                    habilitarVerificacionHostnameTls(tlsSocket);
                    tlsSocket.startHandshake();
                    BufferedReader tlsReader = new BufferedReader(
                            new InputStreamReader(tlsSocket.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter tlsWriter = new BufferedWriter(
                            new OutputStreamWriter(tlsSocket.getOutputStream(), StandardCharsets.UTF_8));
                    enviarComando(tlsWriter, "EHLO foodcore.local");
                    leerRespuestaSmtp(tlsReader, 250);
                    autenticarYEnviarMensaje(tlsReader, tlsWriter, cfg, remitente, destLimpio, asunto, cuerpo);
                    return true;
                }
            }

            autenticarYEnviarMensaje(reader, writer, cfg, remitente, destLimpio, asunto, cuerpo);
            return true;
        }
    }

    private static Socket abrirSocketSmtp(ConfiguracionAlertaEmail cfg, String hostLimpio) throws IOException {
        if (cfg.tlsEnabled() && cfg.smtpPort() == 465) {
            Socket raw = new Socket();
            raw.connect(new InetSocketAddress(hostLimpio, cfg.smtpPort()), SMTP_TIMEOUT_MS);
            raw.setSoTimeout(SMTP_TIMEOUT_MS);
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) factory.createSocket(raw, hostLimpio, cfg.smtpPort(), true);
            sslSocket.setSoTimeout(SMTP_TIMEOUT_MS);
            habilitarVerificacionHostnameTls(sslSocket);
            try {
                sslSocket.startHandshake();
                return sslSocket;
            } catch (IOException | RuntimeException ex) {
                try {
                    sslSocket.close();
                } catch (IOException suppressed) {
                    ex.addSuppressed(suppressed);
                }
                throw ex;
            }
        }
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(hostLimpio, cfg.smtpPort()), SMTP_TIMEOUT_MS);
        socket.setSoTimeout(SMTP_TIMEOUT_MS);
        return socket;
    }

    private static void habilitarVerificacionHostnameTls(SSLSocket sslSocket) {
        SSLParameters params = sslSocket.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        sslSocket.setSSLParameters(params);
    }

    /**
     * Valida estrictamente formato de dirección de correo rechazando CR/LF y metacaracteres SMTP.
     */
    public static String validarEmailSeguro(String email, String campo) {
        if (email == null) {
            throw new IllegalArgumentException("El campo " + campo + " no puede ser nulo.");
        }
        if (contieneCrlf(email)) {
            throw new IllegalArgumentException("Caracteres CR/LF prohibidos detectados en " + campo + ".");
        }
        String trimmed = email.trim();
        if (!EMAIL_SEGURO.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Formato de correo inválido en " + campo + ".");
        }
        return trimmed;
    }

    private static String validarHostSeguro(String host) {
        if (host == null || contieneCrlf(host)) {
            throw new IllegalArgumentException("Host SMTP inválido.");
        }
        String trimmed = host.trim();
        if (!HOST_SEGURO.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Host SMTP contiene caracteres no permitidos.");
        }
        return trimmed;
    }

    private static boolean contieneCrlf(String valor) {
        return valor != null && (valor.indexOf('\r') >= 0 || valor.indexOf('\n') >= 0 || valor.indexOf('\0') >= 0);
    }

    private static void autenticarYEnviarMensaje(
            BufferedReader reader,
            BufferedWriter writer,
            ConfiguracionAlertaEmail cfg,
            String remitente,
            String destinatario,
            String asunto,
            String cuerpo) throws IOException {
        if (cfg.smtpUser() != null && !cfg.smtpUser().isBlank()
                && cfg.smtpPassword() != null && !cfg.smtpPassword().isBlank()) {
            enviarComando(writer, "AUTH LOGIN");
            leerRespuestaSmtp(reader, 334);
            enviarComando(writer, Base64.getEncoder().encodeToString(cfg.smtpUser().trim().getBytes(StandardCharsets.UTF_8)));
            leerRespuestaSmtp(reader, 334);
            enviarComando(writer, Base64.getEncoder().encodeToString(cfg.smtpPassword().getBytes(StandardCharsets.UTF_8)));
            leerRespuestaSmtp(reader, 235);
        }

        enviarComando(writer, "MAIL FROM:<" + remitente + ">");
        leerRespuestaSmtp(reader, 250);
        enviarComando(writer, "RCPT TO:<" + destinatario + ">");
        leerRespuestaSmtp(reader, 250);
        enviarComando(writer, "DATA");
        leerRespuestaSmtp(reader, 354);

        writer.write("From: FoodCore ERP <" + remitente + ">\r\n");
        writer.write("To: <" + destinatario + ">\r\n");
        writer.write("Subject: " + sanitizarCabecera(asunto) + "\r\n");
        writer.write("Content-Type: text/plain; charset=UTF-8\r\n");
        writer.write("\r\n");
        writer.write(cuerpo.replace("\r\n.", "\r\n..").replace("\n.", "\n.."));
        writer.write("\r\n.\r\n");
        writer.flush();
        leerRespuestaSmtp(reader, 250);

        enviarComando(writer, "QUIT");
    }

    private static void enviarComando(BufferedWriter writer, String comando) throws IOException {
        writer.write(comando);
        writer.write("\r\n");
        writer.flush();
    }

    private static void leerRespuestaSmtp(BufferedReader reader, int codigoEsperado) throws IOException {
        String ultimaLinea = null;
        String linea;
        while ((linea = reader.readLine()) != null) {
            ultimaLinea = linea;
            if (linea.length() < 4 || linea.charAt(3) != '-') {
                break;
            }
        }
        if (ultimaLinea == null || ultimaLinea.length() < 3) {
            throw new IOException("Respuesta SMTP vacía o incompleta");
        }
        int codigoRecibido;
        try {
            codigoRecibido = Integer.parseInt(ultimaLinea.substring(0, 3));
        } catch (NumberFormatException ex) {
            throw new IOException("Respuesta SMTP inesperada: " + ultimaLinea, ex);
        }
        if (codigoRecibido != codigoEsperado) {
            throw new IOException("Código SMTP esperado " + codigoEsperado + " pero recibió " + ultimaLinea);
        }
    }

    private static String sanitizarCabecera(String valor) {
        return valor == null ? "" : valor.replace('\r', ' ').replace('\n', ' ');
    }

    public static ConfiguracionAlertaEmail cargarConfiguracionDesdeClasspath() {
        Properties props = new Properties();
        cargarPropsRecurso(props, "/application.properties");
        // Solo cargar application-test.properties cuando el perfil de pruebas está explícitamente habilitado
        if ("test".equalsIgnoreCase(System.getProperty("foodcore.profile"))
                || "true".equalsIgnoreCase(System.getProperty("foodcore.test.mode"))) {
            cargarPropsRecurso(props, "/application-test.properties");
        }

        String host = resolverValor(props.getProperty("smtp.host", "smtp.gmail.com"));
        int port = parseIntSeguro(resolverValor(props.getProperty("smtp.port", "587")), 587);
        String user = resolverValor(props.getProperty("smtp.user", "alertas@foodcore.pe"));
        String pass = resolverValor(props.getProperty("smtp.password", ""));
        boolean tls = Boolean.parseBoolean(resolverValor(props.getProperty("smtp.tls.enabled", "true")));
        String dest = resolverValor(props.getProperty("alertas.email.destinatario", "dueno@foodcore.pe"));
        boolean hab = Boolean.parseBoolean(resolverValor(props.getProperty("alertas.email.habilitado", "true")));
        return new ConfiguracionAlertaEmail(host, port, user, pass, tls, dest, hab);
    }

    private static void cargarPropsRecurso(Properties dest, String resourcePath) {
        try (InputStream is = AlertaService.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    dest.load(reader);
                }
            }
        } catch (IOException ignored) {
            // Se usan valores por defecto si el recurso opcional no está presente
        }
    }

    private static void guardarConfiguracionEnArchivo(Path path, ConfiguracionAlertaEmail cfg) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Properties p = new Properties();
            p.setProperty("smtp.host", cfg.smtpHost() != null ? cfg.smtpHost() : "");
            p.setProperty("smtp.port", String.valueOf(cfg.smtpPort()));
            p.setProperty("smtp.user", cfg.smtpUser() != null ? cfg.smtpUser() : "");
            p.setProperty("smtp.tls.enabled", String.valueOf(cfg.tlsEnabled()));
            p.setProperty("alertas.email.destinatario", cfg.destinatario() != null ? cfg.destinatario() : "");
            p.setProperty("alertas.email.habilitado", String.valueOf(cfg.habilitado()));
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                p.store(writer, "FoodCore ERP SMTP Alerts");
            }
        } catch (IOException ex) {
            LOGGER.log(Level.FINE, "No se pudo guardar alertas-smtp.properties: " + ex.getMessage(), ex);
        }
    }

    static String resolverValor(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            String inner = trimmed.substring(2, trimmed.length() - 1);
            int idx = inner.indexOf(':');
            String varName = idx >= 0 ? inner.substring(0, idx) : inner;
            String defVal = idx >= 0 ? inner.substring(idx + 1) : "";
            String env = System.getenv(varName);
            if (env != null && !env.isBlank()) {
                return env.trim();
            }
            String sys = System.getProperty(varName);
            if (sys != null && !sys.isBlank()) {
                return sys.trim();
            }
            return defVal;
        }
        return trimmed;
    }

    private static int parseIntSeguro(String val, int fallback) {
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
