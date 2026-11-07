package com.foodcore.dao.impl;

import com.foodcore.dao.AsistenciaDAO;
import com.foodcore.model.Asistencia;
import com.foodcore.model.FilaPlanilla;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AsistenciaDAOImpl implements AsistenciaDAO {

    @Override
    public Optional<Asistencia> buscarMarcajeAbierto(Connection conn, int idUsuario, LocalDate fecha) throws SQLException {
        String sql = "SELECT a.*, u.username, COALESCE(u.nombre_completo, u.username) AS nombre_empleado, u.dni, r.nombre AS rol_nombre "
                + "FROM asistencia a "
                + "JOIN usuario u ON a.id_usuario = u.id_usuario "
                + "LEFT JOIN rol r ON u.id_rol = r.id_rol "
                + "WHERE a.id_usuario = ? AND a.fecha = ? AND a.hora_salida IS NULL "
                + "ORDER BY a.id_asistencia DESC LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.setDate(2, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearAsistencia(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public int registrarEntrada(Connection conn, Asistencia a) throws SQLException {
        String sql = "INSERT INTO asistencia (id_usuario, id_empleado, fecha, hora_entrada, hora_ingreso, "
                + "tardanza_minutos, estado, tipo_marcaje, observaciones) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, a.getIdUsuario());
            ps.setInt(2, a.getIdEmpleado());
            ps.setDate(3, Date.valueOf(a.getFecha()));
            Timestamp tsEntrada = a.getHoraEntrada() != null ? Timestamp.valueOf(a.getHoraEntrada()) : Timestamp.valueOf(LocalDateTime.now());
            ps.setTimestamp(4, tsEntrada);
            ps.setTime(5, Time.valueOf(tsEntrada.toLocalDateTime().toLocalTime()));
            ps.setInt(6, a.getTardanzaMinutos());
            ps.setString(7, a.getEstado());
            ps.setString(8, a.getTipoMarcaje() != null ? a.getTipoMarcaje() : "DNI");
            ps.setString(9, a.getObservaciones());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    a.setIdAsistencia(rs.getInt(1));
                    return a.getIdAsistencia();
                }
            }
        }
        return 0;
    }

    @Override
    public boolean registrarSalida(Connection conn, int idAsistencia, LocalDateTime horaSalida,
                                   double horasTrabajadas, double horasExtra) throws SQLException {
        String sql = "UPDATE asistencia SET hora_salida = ?, horas_trabajadas = ?, horas_extra = ?, estado = 'FINALIZADO' "
                + "WHERE id_asistencia = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(horaSalida));
            ps.setDouble(2, horasTrabajadas);
            ps.setDouble(3, horasExtra);
            ps.setInt(4, idAsistencia);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public List<Asistencia> listarPorFecha(Connection conn, LocalDate fecha) throws SQLException {
        String sql = "SELECT a.*, u.username, COALESCE(u.nombre_completo, u.username) AS nombre_empleado, u.dni, r.nombre AS rol_nombre "
                + "FROM asistencia a "
                + "JOIN usuario u ON a.id_usuario = u.id_usuario "
                + "LEFT JOIN rol r ON u.id_rol = r.id_rol "
                + "WHERE a.fecha = ? "
                + "ORDER BY a.hora_entrada DESC";

        List<Asistencia> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearAsistencia(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public List<Asistencia> listarPorRango(Connection conn, LocalDate inicio, LocalDate fin) throws SQLException {
        String sql = "SELECT a.*, u.username, COALESCE(u.nombre_completo, u.username) AS nombre_empleado, u.dni, r.nombre AS rol_nombre "
                + "FROM asistencia a "
                + "JOIN usuario u ON a.id_usuario = u.id_usuario "
                + "LEFT JOIN rol r ON u.id_rol = r.id_rol "
                + "WHERE a.fecha BETWEEN ? AND ? "
                + "ORDER BY a.fecha DESC, a.hora_entrada DESC";

        List<Asistencia> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(inicio));
            ps.setDate(2, Date.valueOf(fin));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearAsistencia(rs));
                }
            }
        }
        return lista;
    }

    @Override
    public List<Asistencia> listarActivosAhora(Connection conn) throws SQLException {
        String sql = "SELECT a.*, u.username, COALESCE(u.nombre_completo, u.username) AS nombre_empleado, u.dni, r.nombre AS rol_nombre "
                + "FROM asistencia a "
                + "JOIN usuario u ON a.id_usuario = u.id_usuario "
                + "LEFT JOIN rol r ON u.id_rol = r.id_rol "
                + "WHERE a.fecha = CURRENT_DATE() AND a.hora_salida IS NULL "
                + "ORDER BY a.hora_entrada DESC";

        List<Asistencia> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearAsistencia(rs));
            }
        }
        return lista;
    }

    @Override
    public List<FilaPlanilla> calcularPlanillaMensual(Connection conn, int anio, int mes) throws SQLException {
        String sql = "SELECT u.id_usuario, u.username, COALESCE(u.nombre_completo, u.username) AS nombre_empleado, u.dni, u.sueldo_base, r.nombre AS rol_nombre, "
                + "COUNT(DISTINCT a.fecha) AS dias_trabajados, "
                + "COALESCE(SUM(a.horas_trabajadas), 0.0) AS horas_totales, "
                + "COALESCE(SUM(a.horas_extra), 0.0) AS horas_extra, "
                + "COALESCE(SUM(a.tardanza_minutos), 0) AS tardanza_minutos "
                + "FROM usuario u "
                + "LEFT JOIN rol r ON u.id_rol = r.id_rol "
                + "LEFT JOIN asistencia a ON u.id_usuario = a.id_usuario AND YEAR(a.fecha) = ? AND MONTH(a.fecha) = ? "
                + "WHERE u.activo = 1 "
                + "GROUP BY u.id_usuario, u.username, u.nombre_completo, u.dni, u.sueldo_base, r.nombre "
                + "ORDER BY u.id_usuario ASC";

        List<FilaPlanilla> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, anio);
            ps.setInt(2, mes);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FilaPlanilla p = new FilaPlanilla();
                    p.setIdUsuario(rs.getInt("id_usuario"));
                    String nom = rs.getString("nombre_empleado");
                    p.setNombreEmpleado(nom != null && !nom.isBlank() ? nom : rs.getString("username"));
                    p.setDni(rs.getString("dni") != null ? rs.getString("dni") : "-");
                    p.setRol(rs.getString("rol_nombre") != null ? rs.getString("rol_nombre") : "PERSONAL");
                    p.setDiasTrabajados(rs.getInt("dias_trabajados"));
                    p.setHorasTotales(rs.getDouble("horas_totales"));
                    p.setHorasExtra(rs.getDouble("horas_extra"));
                    p.setTardanzaMinutos(rs.getInt("tardanza_minutos"));
                    double sueldoBase = rs.getDouble("sueldo_base");
                    p.setSueldoBase(sueldoBase > 0 ? sueldoBase : 1200.00);

                    // Fórmulas de cálculo de nómina:
                    // Valor por minuto = sueldoBase / 30 días / 8 horas / 60 minutos
                    double valorMinuto = p.getSueldoBase() / (30.0 * 8.0 * 60.0);
                    double descTardanza = Math.round(p.getTardanzaMinutos() * valorMinuto * 100.0) / 100.0;
                    p.setDescuentoTardanza(descTardanza);

                    // Hora extra con sobretasa legal 50%
                    double valorHoraExtra = (p.getSueldoBase() / (30.0 * 8.0)) * 1.5;
                    double bonoExtra = Math.round(p.getHorasExtra() * valorHoraExtra * 100.0) / 100.0;
                    p.setBonoHorasExtra(bonoExtra);

                    double neto = p.getSueldoBase() - descTardanza + bonoExtra;
                    p.setTotalNetoPagar(Math.round(neto * 100.0) / 100.0);

                    lista.add(p);
                }
            }
        }
        return lista;
    }

    private Asistencia mapearAsistencia(ResultSet rs) throws SQLException {
        Asistencia a = new Asistencia();
        a.setIdAsistencia(rs.getInt("id_asistencia"));
        a.setIdUsuario(rs.getInt("id_usuario"));
        a.setIdEmpleado(rs.getInt("id_usuario"));
        a.setFecha(rs.getDate("fecha").toLocalDate());

        Timestamp tsEntrada = rs.getTimestamp("hora_entrada");
        if (tsEntrada != null) {
            a.setHoraEntrada(tsEntrada.toLocalDateTime());
        }
        Timestamp tsSalida = rs.getTimestamp("hora_salida");
        if (tsSalida != null) {
            a.setHoraSalidaTimestamp(tsSalida.toLocalDateTime());
        }

        a.setHorasTrabajadas(rs.getDouble("horas_trabajadas"));
        a.setHorasExtra(rs.getDouble("horas_extra"));
        a.setTardanzaMinutos(rs.getInt("tardanza_minutos"));
        a.setEstado(rs.getString("estado"));
        a.setTipoMarcaje(rs.getString("tipo_marcaje"));
        a.setObservaciones(rs.getString("observaciones"));

        a.setNombreUsuario(rs.getString("username"));
        a.setNombreEmpleado(rs.getString("nombre_empleado"));
        a.setDni(rs.getString("dni"));
        a.setRol(rs.getString("rol_nombre"));
        return a;
    }
}
