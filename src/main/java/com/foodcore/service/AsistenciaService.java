package com.foodcore.service;

import com.foodcore.dao.AsistenciaDAO;
import com.foodcore.dao.TurnoProgramadoDAO;
import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.impl.AsistenciaDAOImpl;
import com.foodcore.dao.impl.TurnoProgramadoDAOImpl;
import com.foodcore.dao.impl.UsuarioDAOImpl;
import com.foodcore.model.Asistencia;
import com.foodcore.model.CumplimientoTurnoFila;
import com.foodcore.model.FilaPlanilla;
import com.foodcore.model.ReporteCumplimientoTurno;
import com.foodcore.model.TurnoProgramado;
import com.foodcore.model.Usuario;
import com.foodcore.util.ConexionBD;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AsistenciaService {

    private static final Logger LOGGER = Logger.getLogger(AsistenciaService.class.getName());
    private static final int TOLERANCIA_ATRASO_MIN = 5;
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final AsistenciaDAO asistenciaDAO;
    private final UsuarioDAO usuarioDAO;
    private final TurnoProgramadoDAO turnoProgramadoDAO;

    public AsistenciaService() {
        this(new AsistenciaDAOImpl(), new UsuarioDAOImpl(), new TurnoProgramadoDAOImpl());
    }

    public AsistenciaService(AsistenciaDAO asistenciaDAO, UsuarioDAO usuarioDAO) {
        this(asistenciaDAO, usuarioDAO, new TurnoProgramadoDAOImpl());
    }

    public AsistenciaService(AsistenciaDAO asistenciaDAO, UsuarioDAO usuarioDAO, TurnoProgramadoDAO turnoProgramadoDAO) {
        this.asistenciaDAO = asistenciaDAO;
        this.usuarioDAO = usuarioDAO;
        this.turnoProgramadoDAO = turnoProgramadoDAO;
    }

    public static class ResultadoMarcaje {
        private final boolean exitoso;
        private final String tipo; // "ENTRADA" o "SALIDA"
        private final Usuario usuario;
        private final Asistencia asistencia;
        private final String mensaje;

        public ResultadoMarcaje(boolean exitoso, String tipo, Usuario usuario, Asistencia asistencia, String mensaje) {
            this.exitoso = exitoso;
            this.tipo = tipo;
            this.usuario = usuario;
            this.asistencia = asistencia;
            this.mensaje = mensaje;
        }

        public boolean isExitoso() {
            return exitoso;
        }

        public String getTipo() {
            return tipo;
        }

        public Usuario getUsuario() {
            return usuario;
        }

        public Asistencia getAsistencia() {
            return asistencia;
        }

        public String getMensaje() {
            return mensaje;
        }
    }

    public ResultadoMarcaje registrarMarcajeKiosco(String credencialDniOPin) throws SQLException {
        if (credencialDniOPin == null || credencialDniOPin.trim().isEmpty()) {
            return new ResultadoMarcaje(false, null, null, null, "Ingrese un DNI o PIN válido.");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            Optional<Usuario> userOpt = usuarioDAO.buscarPorDniOPin(conn, credencialDniOPin.trim());
            if (userOpt.isEmpty()) {
                return new ResultadoMarcaje(false, null, null, null, "Colaborador no encontrado para DNI/PIN: " + credencialDniOPin.trim());
            }

            Usuario user = userOpt.get();
            LocalDate hoy = LocalDate.now();
            LocalDateTime ahora = LocalDateTime.now();

            Optional<Asistencia> abiertaOpt = asistenciaDAO.buscarMarcajeAbierto(conn, user.getIdUsuario(), hoy);

            if (abiertaOpt.isPresent()) {
                // Registrar Salida
                Asistencia abierta = abiertaOpt.get();
                Duration dur = Duration.between(abierta.getHoraEntrada(), ahora);
                double horas = Math.round((dur.toMinutes() / 60.0) * 100.0) / 100.0;
                double horasExtra = horas > 8.0 ? Math.round((horas - 8.0) * 100.0) / 100.0 : 0.0;

                boolean ok = asistenciaDAO.registrarSalida(conn, abierta.getIdAsistencia(), ahora, horas, horasExtra);
                abierta.setHoraSalidaTimestamp(ahora);
                abierta.setHorasTrabajadas(horas);
                abierta.setHorasExtra(horasExtra);
                abierta.setEstado("FINALIZADO");

                String msg = String.format("Salida registrada para %s (%s). Horas laboradas: %.2f hrs (Extra: %.2f hrs)",
                        user.getNombreEmpleado() != null ? user.getNombreEmpleado() : user.getUsername(),
                        user.getNombreRol(), horas, horasExtra);
                return new ResultadoMarcaje(ok, "SALIDA", user, abierta, msg);
            } else {
                // Registrar Entrada
                LocalTime horaProg = user.getHoraEntradaProgramada() != null ? user.getHoraEntradaProgramada() : LocalTime.of(8, 0);
                LocalTime horaActual = ahora.toLocalTime();

                int tardanzaMin = 0;
                String estado = "PRESENTE";
                if (horaActual.isAfter(horaProg)) {
                    long minDif = Duration.between(horaProg, horaActual).toMinutes();
                    if (minDif > 5) {
                        tardanzaMin = (int) minDif;
                        estado = "TARDE";
                    }
                }

                String tipoMarcaje = credencialDniOPin.trim().length() <= 4 ? "PIN" : "DNI";

                Asistencia nueva = new Asistencia();
                nueva.setIdUsuario(user.getIdUsuario());
                nueva.setIdEmpleado(user.getIdUsuario());
                nueva.setFecha(hoy);
                nueva.setHoraEntrada(ahora);
                nueva.setTardanzaMinutos(tardanzaMin);
                nueva.setEstado(estado);
                nueva.setTipoMarcaje(tipoMarcaje);
                nueva.setObservaciones(tardanzaMin > 0 ? "Tardanza de " + tardanzaMin + " min" : "Marcaje puntual");

                int idAsist = asistenciaDAO.registrarEntrada(conn, nueva);
                nueva.setIdAsistencia(idAsist);

                String msg = String.format("Entrada registrada para %s (%s). %s",
                        user.getNombreEmpleado() != null ? user.getNombreEmpleado() : user.getUsername(),
                        user.getNombreRol(),
                        tardanzaMin > 0 ? "⚠ Alerta: Tardanza de " + tardanzaMin + " min" : "✓ Marcaje puntual");

                return new ResultadoMarcaje(idAsist > 0, "ENTRADA", user, nueva, msg);
            }
        }
    }

    public List<Asistencia> listarAsistenciaPorFecha(LocalDate fecha) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return asistenciaDAO.listarPorFecha(conn, fecha);
        }
    }

    public List<Asistencia> listarPersonalActivoAhora() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return asistenciaDAO.listarActivosAhora(conn);
        }
    }

    public List<FilaPlanilla> calcularPlanillaMensual(int anio, int mes) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return asistenciaDAO.calcularPlanillaMensual(conn, anio, mes);
        }
    }

    public void exportarPlanillaMensualExcel(int anio, int mes, File archivoDestino) throws Exception {
        List<FilaPlanilla> planilla = calcularPlanillaMensual(anio, mes);

        if (archivoDestino.getParentFile() != null) {
            java.nio.file.Files.createDirectories(archivoDestino.getParentFile().toPath());
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Planilla " + anio + "-" + String.format("%02d", mes));

            // Estilos
            CellStyle styleTitulo = workbook.createCellStyle();
            Font fontTitulo = workbook.createFont();
            fontTitulo.setBold(true);
            fontTitulo.setFontHeightInPoints((short) 14);
            fontTitulo.setColor(IndexedColors.DARK_BLUE.getIndex());
            styleTitulo.setFont(fontTitulo);

            CellStyle styleSub = workbook.createCellStyle();
            Font fontSub = workbook.createFont();
            fontSub.setItalic(true);
            fontSub.setFontHeightInPoints((short) 10);
            styleSub.setFont(fontSub);

            CellStyle styleHeader = workbook.createCellStyle();
            Font fontH = workbook.createFont();
            fontH.setBold(true);
            fontH.setColor(IndexedColors.WHITE.getIndex());
            styleHeader.setFont(fontH);
            styleHeader.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            styleHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            styleHeader.setAlignment(HorizontalAlignment.CENTER);
            styleHeader.setBorderBottom(BorderStyle.THIN);

            CellStyle styleData = workbook.createCellStyle();
            styleData.setBorderBottom(BorderStyle.THIN);
            styleData.setBorderTop(BorderStyle.THIN);
            styleData.setBorderLeft(BorderStyle.THIN);
            styleData.setBorderRight(BorderStyle.THIN);

            CellStyle styleCurrency = workbook.createCellStyle();
            styleCurrency.cloneStyleFrom(styleData);
            styleCurrency.setDataFormat(workbook.createDataFormat().getFormat("S/ #,##0.00"));
            styleCurrency.setAlignment(HorizontalAlignment.RIGHT);

            CellStyle styleTotal = workbook.createCellStyle();
            Font fontTot = workbook.createFont();
            fontTot.setBold(true);
            styleTotal.setFont(fontTot);
            styleTotal.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            styleTotal.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            styleTotal.setDataFormat(workbook.createDataFormat().getFormat("S/ #,##0.00"));
            styleTotal.setAlignment(HorizontalAlignment.RIGHT);
            styleTotal.setBorderTop(BorderStyle.DOUBLE);
            styleTotal.setBorderBottom(BorderStyle.DOUBLE);

            // Título
            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("FOODCORE FAST FOOD S.A.C. — PLANILLA MENSUAL DE REMUNERACIONES");
            c0.setCellStyle(styleTitulo);

            Row r1 = sheet.createRow(1);
            Cell c1 = r1.createCell(0);
            c1.setCellValue("Período Laboral: " + anio + "/" + String.format("%02d", mes) + " | Base de Cálculo: 8 hrs diarias");
            c1.setCellStyle(styleSub);

            // Encabezados
            String[] headers = {
                    "ID", "Colaborador", "DNI", "Rol / Cargo", "Días Trab.", "Horas Totales",
                    "Horas Extra", "Tardanza (min)", "Sueldo Base (S/)", "Desc. Tardanza (S/)",
                    "Bono H.Extra (S/)", "Neto a Pagar (S/)"
            };

            Row r3 = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = r3.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(styleHeader);
            }

            int rowIdx = 4;
            double sumSueldoBase = 0;
            double sumDescTardanza = 0;
            double sumBonoExtra = 0;
            double sumNeto = 0;

            for (FilaPlanilla p : planilla) {
                Row r = sheet.createRow(rowIdx++);
                Cell cId = r.createCell(0);
                cId.setCellValue(p.getIdUsuario());
                cId.setCellStyle(styleData);

                Cell cNom = r.createCell(1);
                cNom.setCellValue(p.getNombreEmpleado());
                cNom.setCellStyle(styleData);

                Cell cDni = r.createCell(2);
                cDni.setCellValue(p.getDni());
                cDni.setCellStyle(styleData);

                Cell cRol = r.createCell(3);
                cRol.setCellValue(p.getRol());
                cRol.setCellStyle(styleData);

                Cell cDias = r.createCell(4);
                cDias.setCellValue(p.getDiasTrabajados());
                cDias.setCellStyle(styleData);

                Cell cHoras = r.createCell(5);
                cHoras.setCellValue(p.getHorasTotales());
                cHoras.setCellStyle(styleData);

                Cell cExt = r.createCell(6);
                cExt.setCellValue(p.getHorasExtra());
                cExt.setCellStyle(styleData);

                Cell cTard = r.createCell(7);
                cTard.setCellValue(p.getTardanzaMinutos());
                cTard.setCellStyle(styleData);

                Cell cSb = r.createCell(8);
                cSb.setCellValue(p.getSueldoBase());
                cSb.setCellStyle(styleCurrency);

                Cell cDesc = r.createCell(9);
                cDesc.setCellValue(p.getDescuentoTardanza());
                cDesc.setCellStyle(styleCurrency);

                Cell cBono = r.createCell(10);
                cBono.setCellValue(p.getBonoHorasExtra());
                cBono.setCellStyle(styleCurrency);

                Cell cNeto = r.createCell(11);
                cNeto.setCellValue(p.getTotalNetoPagar());
                cNeto.setCellStyle(styleCurrency);

                sumSueldoBase += p.getSueldoBase();
                sumDescTardanza += p.getDescuentoTardanza();
                sumBonoExtra += p.getBonoHorasExtra();
                sumNeto += p.getTotalNetoPagar();
            }

            // Fila de Totales
            Row rTot = sheet.createRow(rowIdx);
            Cell cTotLbl = rTot.createCell(1);
            cTotLbl.setCellValue("TOTALES GENERALES:");
            cTotLbl.setCellStyle(styleTotal);

            Cell cTotSb = rTot.createCell(8);
            cTotSb.setCellValue(sumSueldoBase);
            cTotSb.setCellStyle(styleTotal);

            Cell cTotDesc = rTot.createCell(9);
            cTotDesc.setCellValue(sumDescTardanza);
            cTotDesc.setCellStyle(styleTotal);

            Cell cTotBono = rTot.createCell(10);
            cTotBono.setCellValue(sumBonoExtra);
            cTotBono.setCellStyle(styleTotal);

            Cell cTotNeto = rTot.createCell(11);
            cTotNeto.setCellValue(sumNeto);
            cTotNeto.setCellStyle(styleTotal);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fos = new FileOutputStream(archivoDestino)) {
                workbook.write(fos);
            }
        }
    }

    /**
     * Lista la asistencia de una fecha enriquecida con el turno programado publicado
     * y el cálculo de {@code cumplimientoTurno} (v5.2.0).
     */
    public List<Asistencia> listarAsistenciaConCumplimiento(LocalDate fecha) throws SQLException {
        LocalDate fechaRef = fecha != null ? fecha : LocalDate.now();
        String diaEs = diaSemanaEspanol(fechaRef.getDayOfWeek());
        try (Connection conn = ConexionBD.getConnection()) {
            List<Asistencia> asistencias = asistenciaDAO.listarPorFecha(conn, fechaRef);
            List<TurnoProgramado> turnosSemana = turnoProgramadoDAO.listarSemana(conn);

            Map<Integer, TurnoProgramado> turnoPorUsuario = new LinkedHashMap<>();
            for (TurnoProgramado tp : turnosSemana) {
                if (tp != null && tp.isPublicado() && diaEs.equalsIgnoreCase(tp.getDiaSemana())) {
                    turnoPorUsuario.putIfAbsent(tp.getIdUsuario(), tp);
                }
            }

            for (Asistencia a : asistencias) {
                TurnoProgramado tp = turnoPorUsuario.get(a.getIdUsuario());
                if (tp == null) {
                    a.setTurnoProgramadoTexto("Sin turno programado");
                    a.setCumplimientoTurno("SIN TURNO");
                } else {
                    String hIni = tp.getHoraInicio() != null ? tp.getHoraInicio().format(FMT_HORA) : "--:--";
                    String hFin = tp.getHoraFin() != null ? tp.getHoraFin().format(FMT_HORA) : "--:--";
                    a.setTurnoProgramadoTexto(hIni + " - " + hFin + " (" + tp.getTurno() + ")");

                    int atrasoMin = calcularAtrasoContraTurno(a.getHoraIngreso(), tp.getHoraInicio());
                    if (atrasoMin > TOLERANCIA_ATRASO_MIN) {
                        a.setCumplimientoTurno("ATRASO (" + atrasoMin + " min)");
                    } else if (a.getHoraSalida() != null && tp.getHoraFin() != null
                            && Duration.between(a.getHoraSalida(), tp.getHoraFin()).toMinutes() > TOLERANCIA_ATRASO_MIN) {
                        long salidaTemprana = Duration.between(a.getHoraSalida(), tp.getHoraFin()).toMinutes();
                        a.setCumplimientoTurno("SALIDA TEMPRANA (" + salidaTemprana + " min)");
                    } else {
                        a.setCumplimientoTurno("A TIEMPO");
                    }
                }
            }
            return asistencias;
        }
    }

    /**
     * Cruza {@code turno_programado} (turnos publicados) contra {@code asistencia} real
     * en un rango de fechas para identificar atrasos y faltas injustificadas (v5.2.0).
     */
    public ReporteCumplimientoTurno generarReporteAtrasosYFaltas(LocalDate desde, LocalDate hasta) throws SQLException {
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate ini = desde != null ? desde : fin.minusDays(6);
        if (ini.isAfter(fin)) {
            LocalDate tmp = ini;
            ini = fin;
            fin = tmp;
        }

        try (Connection conn = ConexionBD.getConnection()) {
            List<Usuario> usuariosActivos = usuarioDAO.listarTodosActivos(conn);
            Map<Integer, Usuario> usuariosPorId = new LinkedHashMap<>();
            for (Usuario u : usuariosActivos) {
                usuariosPorId.put(u.getIdUsuario(), u);
            }

            List<TurnoProgramado> turnosSemana = turnoProgramadoDAO.listarSemana(conn);
            List<Asistencia> asistenciasRango = asistenciaDAO.listarPorRango(conn, ini, fin);
            Map<String, Asistencia> asistenciaPorUsuarioFecha = new LinkedHashMap<>();
            for (Asistencia a : asistenciasRango) {
                if (a.getFecha() != null) {
                    asistenciaPorUsuarioFecha.putIfAbsent(a.getIdUsuario() + "|" + a.getFecha(), a);
                }
            }

            int totalEvaluados = 0;
            int totalPuntuales = 0;
            int totalAtrasos = 0;
            int totalFaltas = 0;
            List<CumplimientoTurnoFila> filasAtrasos = new ArrayList<>();
            List<CumplimientoTurnoFila> filasFaltas = new ArrayList<>();
            List<CumplimientoTurnoFila> todas = new ArrayList<>();

            for (LocalDate d = ini; !d.isAfter(fin); d = d.plusDays(1)) {
                String diaEs = diaSemanaEspanol(d.getDayOfWeek());
                for (TurnoProgramado tp : turnosSemana) {
                    if (tp == null || !tp.isPublicado() || !diaEs.equalsIgnoreCase(tp.getDiaSemana())) {
                        continue;
                    }
                    Usuario u = usuariosPorId.get(tp.getIdUsuario());
                    if (u == null) {
                        continue;
                    }
                    totalEvaluados++;
                    String nombreColab = u.getNombreEmpleado() != null && !u.getNombreEmpleado().isBlank()
                            ? u.getNombreEmpleado() : u.getUsername();
                    String rolColab = u.getNombreRol() != null ? u.getNombreRol() : (tp.getRol() != null ? tp.getRol() : "OPERATIVO");

                    Asistencia real = asistenciaPorUsuarioFecha.get(tp.getIdUsuario() + "|" + d);
                    if (real == null) {
                        totalFaltas++;
                        CumplimientoTurnoFila fila = new CumplimientoTurnoFila(
                                tp.getIdUsuario(), nombreColab, rolColab, d, diaEs, tp.getTurno(),
                                tp.getHoraInicio(), tp.getHoraFin(), null, null, 0, "FALTA"
                        );
                        filasFaltas.add(fila);
                        todas.add(fila);
                    } else {
                        LocalTime ingreso = real.getHoraIngreso();
                        LocalTime salida = real.getHoraSalida();
                        int atrasoMin = calcularAtrasoContraTurno(ingreso, tp.getHoraInicio());
                        String estado;
                        if (atrasoMin > TOLERANCIA_ATRASO_MIN) {
                            estado = "ATRASO (" + atrasoMin + " min)";
                            totalAtrasos++;
                        } else {
                            estado = "A TIEMPO";
                            totalPuntuales++;
                        }
                        CumplimientoTurnoFila fila = new CumplimientoTurnoFila(
                                tp.getIdUsuario(), nombreColab, rolColab, d, diaEs, tp.getTurno(),
                                tp.getHoraInicio(), tp.getHoraFin(), ingreso, salida, atrasoMin, estado
                        );
                        if (atrasoMin > TOLERANCIA_ATRASO_MIN) {
                            filasAtrasos.add(fila);
                        }
                        todas.add(fila);
                    }
                }
            }

            return new ReporteCumplimientoTurno(
                    ini, fin, totalEvaluados, totalPuntuales, totalAtrasos, totalFaltas,
                    filasAtrasos, filasFaltas, todas
            );
        }
    }

    /** Exporta a Excel el reporte consolidado de Atrasos y Faltas vs turnos programados (v5.2.0). */
    public void exportarReporteCumplimientoExcel(ReporteCumplimientoTurno reporte, File archivoDestino) throws Exception {
        if (archivoDestino.getParentFile() != null) {
            java.nio.file.Files.createDirectories(archivoDestino.getParentFile().toPath());
        }
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Atrasos y Faltas");
            Font fTitle = workbook.createFont();
            fTitle.setBold(true);
            fTitle.setFontHeightInPoints((short) 13);
            CellStyle stTitle = workbook.createCellStyle();
            stTitle.setFont(fTitle);

            Font fHdr = workbook.createFont();
            fHdr.setBold(true);
            fHdr.setColor(IndexedColors.WHITE.getIndex());
            CellStyle stHdr = workbook.createCellStyle();
            stHdr.setFont(fHdr);
            stHdr.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            stHdr.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            stHdr.setAlignment(HorizontalAlignment.CENTER);
            stHdr.setBorderBottom(BorderStyle.THIN);

            CellStyle stData = workbook.createCellStyle();
            stData.setBorderBottom(BorderStyle.THIN);

            Row r0 = sheet.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("REPORTE DE CUMPLIMIENTO DE TURNOS (ATRASOS Y FALTAS) - "
                    + reporte.desde() + " AL " + reporte.hasta());
            c0.setCellStyle(stTitle);

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Turnos Evaluados: " + reporte.totalTurnosEvaluados()
                    + " | Puntuales: " + reporte.totalPuntuales()
                    + " | Atrasos: " + reporte.totalAtrasos()
                    + " | Faltas: " + reporte.totalFaltas());

            String[] cols = {"Fecha", "Día", "Colaborador", "Rol", "Turno Prog.", "Ingreso Real", "Salida Real", "Atraso (min)", "Cumplimiento"};
            Row hdr = sheet.createRow(3);
            for (int i = 0; i < cols.length; i++) {
                Cell cell = hdr.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(stHdr);
            }

            int idx = 4;
            for (CumplimientoTurnoFila f : reporte.todasLasFilas()) {
                Row row = sheet.createRow(idx++);
                row.createCell(0).setCellValue(f.fecha() != null ? f.fecha().toString() : "");
                row.createCell(1).setCellValue(f.diaSemana() != null ? f.diaSemana() : "");
                row.createCell(2).setCellValue(f.nombreColaborador() != null ? f.nombreColaborador() : "");
                row.createCell(3).setCellValue(f.rol() != null ? f.rol() : "");
                row.createCell(4).setCellValue(f.horarioProgramadoTexto());
                row.createCell(5).setCellValue(f.horaIngresoReal() != null ? f.horaIngresoReal().format(FMT_HORA) : "Sin ingreso");
                row.createCell(6).setCellValue(f.horaSalidaReal() != null ? f.horaSalidaReal().format(FMT_HORA) : "--:--");
                row.createCell(7).setCellValue(f.minutosAtraso());
                row.createCell(8).setCellValue(f.estadoCumplimiento() != null ? f.estadoCumplimiento() : "");
                for (int c = 0; c < cols.length; c++) {
                    row.getCell(c).setCellStyle(stData);
                }
            }

            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }
            try (FileOutputStream fos = new FileOutputStream(archivoDestino)) {
                workbook.write(fos);
            }
        }
    }

    private int calcularAtrasoContraTurno(LocalTime ingresoReal, LocalTime inicioProgramado) {
        if (ingresoReal == null || inicioProgramado == null || !ingresoReal.isAfter(inicioProgramado)) {
            return 0;
        }
        return (int) Math.max(0L, Duration.between(inicioProgramado, ingresoReal).toMinutes());
    }

    public static String diaSemanaEspanol(DayOfWeek dow) {
        if (dow == null) {
            return "LUNES";
        }
        return switch (dow) {
            case MONDAY -> "LUNES";
            case TUESDAY -> "MARTES";
            case WEDNESDAY -> "MIERCOLES";
            case THURSDAY -> "JUEVES";
            case FRIDAY -> "VIERNES";
            case SATURDAY -> "SABADO";
            case SUNDAY -> "DOMINGO";
        };
    }
}
