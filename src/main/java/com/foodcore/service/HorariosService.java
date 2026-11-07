package com.foodcore.service;

import com.foodcore.dao.TurnoProgramadoDAO;
import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.impl.TurnoProgramadoDAOImpl;
import com.foodcore.dao.impl.UsuarioDAOImpl;
import com.foodcore.model.TurnoProgramado;
import com.foodcore.model.Usuario;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HorariosService {

    private final TurnoProgramadoDAO turnoDAO;
    private final UsuarioDAO usuarioDAO;

    public HorariosService() {
        this(new TurnoProgramadoDAOImpl(), new UsuarioDAOImpl());
    }

    public HorariosService(TurnoProgramadoDAO turnoDAO, UsuarioDAO usuarioDAO) {
        this.turnoDAO = turnoDAO;
        this.usuarioDAO = usuarioDAO;
    }

    public List<TurnoProgramado> listarSemana() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return turnoDAO.listarSemana(conn);
        }
    }

    public List<TurnoProgramado> listarPorUsuario(int idUsuario) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return turnoDAO.listarPorUsuario(conn, idUsuario);
        }
    }

    public int asignarTurno(int idUsuario, String diaSemana, String turno, LocalTime inicio, LocalTime fin) throws SQLException {
        if (idUsuario <= 0) {
            throw new IllegalArgumentException("Debe seleccionar un colaborador válido");
        }
        if (diaSemana == null || diaSemana.isBlank()) {
            throw new IllegalArgumentException("El día de la semana es obligatorio");
        }

        LocalTime horaIni = inicio != null ? inicio : obtenerHoraInicioPredeterminada(turno);
        LocalTime horaFin = fin != null ? fin : obtenerHoraFinPredeterminada(turno);

        TurnoProgramado tp = new TurnoProgramado();
        tp.setIdUsuario(idUsuario);
        tp.setDiaSemana(diaSemana.toUpperCase());
        tp.setTurno(turno != null ? turno.toUpperCase() : "MANANA");
        tp.setHoraInicio(horaIni);
        tp.setHoraFin(horaFin);
        tp.setPublicado(true);

        try (Connection conn = ConexionBD.getConnection()) {
            return turnoDAO.asignarTurno(conn, tp);
        }
    }

    public boolean eliminarTurno(int idTurnoProg) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return turnoDAO.eliminarTurno(conn, idTurnoProg);
        }
    }

    public boolean publicarHorariosSemana() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return turnoDAO.publicarHorariosSemana(conn);
        }
    }

    public List<Usuario> listarColaboradoresDisponibles() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return usuarioDAO.listarTodosActivos(conn);
        }
    }

    /**
     * Evalúa la cobertura por día y turno.
     * Retorna lista de alertas cuando un turno no cuenta con el personal requerido.
     */
    public List<String> evaluarAlertasCobertura() throws SQLException {
        List<String> alertas = new ArrayList<>();
        String[] dias = {"LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO"};
        String[] turnos = {"MANANA", "TARDE", "NOCHE"};

        try (Connection conn = ConexionBD.getConnection()) {
            for (String d : dias) {
                for (String t : turnos) {
                    int cant = turnoDAO.contarCobertura(conn, d, t);
                    if (cant == 0) {
                        if ("VIERNES".equals(d) || "SABADO".equals(d) || "DOMINGO".equals(d)) {
                            alertas.add("Déficit Crítico: " + d + " Turno " + t + " no tiene personal asignado");
                        }
                    } else if (cant < 2 && ("VIERNES".equals(d) || "SABADO".equals(d))) {
                        alertas.add("Atención: " + d + " Turno " + t + " cuenta con solo 1 persona (Alta demanda)");
                    }
                }
            }
        }
        return alertas;
    }

    private LocalTime obtenerHoraInicioPredeterminada(String turno) {
        if ("TARDE".equalsIgnoreCase(turno)) {
            return LocalTime.of(14, 0);
        } else if ("NOCHE".equalsIgnoreCase(turno)) {
            return LocalTime.of(18, 0);
        }
        return LocalTime.of(8, 0);
    }

    private LocalTime obtenerHoraFinPredeterminada(String turno) {
        if ("TARDE".equalsIgnoreCase(turno)) {
            return LocalTime.of(22, 0);
        } else if ("NOCHE".equalsIgnoreCase(turno)) {
            return LocalTime.of(2, 0);
        }
        return LocalTime.of(16, 0);
    }
}
