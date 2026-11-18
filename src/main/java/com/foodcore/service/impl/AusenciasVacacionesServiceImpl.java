package com.foodcore.service.impl;

import com.foodcore.dao.ExpedienteUsuarioDAO;
import com.foodcore.dao.SolicitudAusenciaDAO;
import com.foodcore.dao.VacacionesDAO;
import com.foodcore.dao.impl.ExpedienteUsuarioDAOImpl;
import com.foodcore.dao.impl.SolicitudAusenciaDAOImpl;
import com.foodcore.dao.impl.VacacionesDAOImpl;
import com.foodcore.model.SolicitudAusencia;
import com.foodcore.model.Usuario;
import com.foodcore.model.Vacaciones;
import com.foodcore.service.AusenciasVacacionesService;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementación de {@link AusenciasVacacionesService}.
 */
public class AusenciasVacacionesServiceImpl implements AusenciasVacacionesService {

    private static final double DIAS_POR_MES = 2.5;
    private static final int TOPE_ANUAL_DIAS = 30;
    private static final int MINIMO_REFERENCIAL_DIAS = 15;

    private final SolicitudAusenciaDAO solicitudDao;
    private final VacacionesDAO vacacionesDao;
    private final ExpedienteUsuarioDAO expedienteUsuarioDao;

    public AusenciasVacacionesServiceImpl() {
        this(new SolicitudAusenciaDAOImpl(), new VacacionesDAOImpl(), new ExpedienteUsuarioDAOImpl());
    }

    public AusenciasVacacionesServiceImpl(SolicitudAusenciaDAO solicitudDao,
                                          VacacionesDAO vacacionesDao,
                                          ExpedienteUsuarioDAO expedienteUsuarioDao) {
        this.solicitudDao = Objects.requireNonNull(solicitudDao, "solicitudDao requerido");
        this.vacacionesDao = Objects.requireNonNull(vacacionesDao, "vacacionesDao requerido");
        this.expedienteUsuarioDao = Objects.requireNonNull(expedienteUsuarioDao, "expedienteUsuarioDao requerido");
    }

    @Override
    public Vacaciones obtenerSaldoVacaciones(int idUsuario, int anio) {
        if (idUsuario <= 0) {
            throw new IllegalArgumentException("Identificador de colaborador inválido.");
        }
        int periodo = anio > 0 ? anio : LocalDate.now().getYear();
        try (Connection conn = ConexionBD.getConnection()) {
            return obtenerOAsegurarSaldoVacaciones(conn, idUsuario, periodo);
        } catch (SQLException e) {
            throw new IllegalStateException("Error al calcular saldo de vacaciones: " + e.getMessage(), e);
        }
    }

    @Override
    public int calcularDiasSolicitados(LocalDate inicio, LocalDate fin) {
        if (inicio == null || fin == null) {
            throw new IllegalArgumentException("Las fechas de inicio y fin son obligatorias.");
        }
        if (fin.isBefore(inicio)) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la fecha de inicio.");
        }
        return (int) ChronoUnit.DAYS.between(inicio, fin) + 1;
    }

    @Override
    public SolicitudAusencia registrarSolicitud(int idUsuario,
                                              String tipo,
                                              LocalDate inicio,
                                              LocalDate fin,
                                              String motivo) {
        if (idUsuario <= 0) {
            throw new IllegalArgumentException("Seleccione un colaborador válido.");
        }
        String tipoNorm = (tipo == null || tipo.isBlank()) ? "VACACIONES" : tipo.trim().toUpperCase(Locale.ROOT);
        int dias = calcularDiasSolicitados(inicio, fin);
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Ingrese un motivo o justificación para la solicitud.");
        }

        try (Connection conn = ConexionBD.getConnection()) {
            if ("VACACIONES".equals(tipoNorm)) {
                Vacaciones saldo = obtenerOAsegurarSaldoVacaciones(conn, idUsuario, inicio.getYear());
                if (dias > saldo.getDiasDisponibles()) {
                    throw new IllegalArgumentException(String.format(
                            "Solicita %d días, pero solo cuenta con %d días de vacaciones disponibles.",
                            dias, saldo.getDiasDisponibles()));
                }
            }
            SolicitudAusencia sol = new SolicitudAusencia(0, idUsuario, tipoNorm, inicio, fin, motivo.trim(), "PENDIENTE");
            sol.setDiasSolicitados(dias);
            solicitudDao.crear(conn, sol);
            return solicitudDao.buscarPorId(conn, sol.getIdSolicitud()).orElse(sol);
        } catch (SQLException e) {
            throw new IllegalStateException("Error al registrar solicitud de ausencia: " + e.getMessage(), e);
        }
    }

    @Override
    public List<SolicitudAusencia> listarTodas() {
        try (Connection conn = ConexionBD.getConnection()) {
            return solicitudDao.listarTodas(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Error al listar solicitudes: " + e.getMessage(), e);
        }
    }

    @Override
    public List<SolicitudAusencia> listarPorUsuario(int idUsuario) {
        try (Connection conn = ConexionBD.getConnection()) {
            return solicitudDao.listarPorUsuario(conn, idUsuario);
        } catch (SQLException e) {
            throw new IllegalStateException("Error al listar solicitudes del colaborador: " + e.getMessage(), e);
        }
    }

    @Override
    public void aprobarSolicitud(int idSolicitud, String observacion, Integer idAprobador) {
        if (idSolicitud <= 0) {
            throw new IllegalArgumentException("Identificador de solicitud inválido.");
        }
        try (Connection conn = ConexionBD.getConnection()) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                SolicitudAusencia sol = solicitudDao.buscarPorId(conn, idSolicitud)
                        .orElseThrow(() -> new IllegalArgumentException("No existe la solicitud #" + idSolicitud));
                if (!"PENDIENTE".equalsIgnoreCase(sol.getEstado())) {
                    throw new IllegalStateException("La solicitud #" + idSolicitud + " ya fue procesada.");
                }

                String obs = (observacion == null || observacion.isBlank())
                        ? "Aprobado por Gerencia"
                        : observacion.trim();
                solicitudDao.actualizarEstado(conn, idSolicitud, "APROBADA", obs, idAprobador);

                if ("VACACIONES".equalsIgnoreCase(sol.getTipo())) {
                    int anio = sol.getFechaInicio() != null
                            ? sol.getFechaInicio().getYear()
                            : LocalDate.now().getYear();
                    obtenerOAsegurarSaldoVacaciones(conn, sol.getIdUsuario(), anio);
                    vacacionesDao.incrementarDiasGozados(conn, sol.getIdUsuario(), anio, sol.getDiasSolicitados());
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                if (ex instanceof RuntimeException rt) {
                    throw rt;
                }
                throw new IllegalStateException("No se pudo aprobar la solicitud: " + ex.getMessage(), ex);
            } finally {
                conn.setAutoCommit(prevAutoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error transaccional al aprobar solicitud: " + e.getMessage(), e);
        }
    }

    @Override
    public void rechazarSolicitud(int idSolicitud, String observacion, Integer idAprobador) {
        if (idSolicitud <= 0) {
            throw new IllegalArgumentException("Identificador de solicitud inválido.");
        }
        try (Connection conn = ConexionBD.getConnection()) {
            SolicitudAusencia sol = solicitudDao.buscarPorId(conn, idSolicitud)
                    .orElseThrow(() -> new IllegalArgumentException("No existe la solicitud #" + idSolicitud));
            if (!"PENDIENTE".equalsIgnoreCase(sol.getEstado())) {
                throw new IllegalStateException("La solicitud #" + idSolicitud + " ya fue procesada.");
            }
            String obs = (observacion == null || observacion.isBlank())
                    ? "Rechazado por cobertura de turno"
                    : observacion.trim();
            solicitudDao.actualizarEstado(conn, idSolicitud, "RECHAZADA", obs, idAprobador);
        } catch (SQLException e) {
            throw new IllegalStateException("Error al rechazar solicitud: " + e.getMessage(), e);
        }
    }

    private Vacaciones obtenerOAsegurarSaldoVacaciones(Connection conn, int idUsuario, int anio) throws SQLException {
        Optional<Vacaciones> existente = vacacionesDao.buscarPorUsuarioYAnio(conn, idUsuario, anio);
        if (existente.isPresent()) {
            return existente.get();
        }
        Optional<Usuario> usuarioOpt = expedienteUsuarioDao.buscarPorId(conn, idUsuario);
        LocalDate fechaIngreso = usuarioOpt.map(Usuario::getFechaIngreso).orElse(null);
        int asignados = calcularDiasAsignadosPorAntiguedad(fechaIngreso, LocalDate.now());

        Vacaciones nuevo = new Vacaciones();
        nuevo.setIdUsuario(idUsuario);
        nuevo.setPeriodoAnio(anio);
        nuevo.setDiasAsignados(asignados);
        nuevo.setDiasGozados(0);
        nuevo.setFechaCorte(LocalDate.now());
        nuevo.setObservaciones("Cálculo automático por antigüedad administrativa");
        vacacionesDao.guardarOActualizar(conn, nuevo);
        return vacacionesDao.buscarPorUsuarioYAnio(conn, idUsuario, anio).orElse(nuevo);
    }

    public static int calcularDiasAsignadosPorAntiguedad(LocalDate fechaIngreso, LocalDate fechaReferencia) {
        if (fechaIngreso == null || fechaReferencia == null || !fechaIngreso.isBefore(fechaReferencia)) {
            return MINIMO_REFERENCIAL_DIAS;
        }
        long meses = ChronoUnit.MONTHS.between(fechaIngreso, fechaReferencia);
        int calculado = (int) Math.floor(meses * DIAS_POR_MES);
        return Math.min(TOPE_ANUAL_DIAS, Math.max(MINIMO_REFERENCIAL_DIAS, calculado));
    }
}
