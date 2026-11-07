package com.foodcore.service;

import com.foodcore.dao.ReservaDAO;
import com.foodcore.dao.impl.ReservaDAOImpl;
import com.foodcore.model.ReservaMesa;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public class ReservasService {

    private final ReservaDAO reservaDAO;

    public ReservasService() {
        this(new ReservaDAOImpl());
    }

    public ReservasService(ReservaDAO reservaDAO) {
        this.reservaDAO = reservaDAO;
    }

    public int registrarReserva(ReservaMesa r) throws SQLException {
        if (r.getClienteNombre() == null || r.getClienteNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del cliente es obligatorio");
        }
        if (r.getClienteTelefono() == null || r.getClienteTelefono().trim().isEmpty()) {
            throw new IllegalArgumentException("El teléfono/WhatsApp es obligatorio para confirmación");
        }
        if (r.getFecha() == null) {
            r.setFecha(LocalDate.now());
        }
        if (r.getHora() == null) {
            r.setHora(LocalTime.now());
        }

        try (Connection conn = ConexionBD.getConnection()) {
            return reservaDAO.crearReserva(conn, r);
        }
    }

    public List<ReservaMesa> listarTodas() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return reservaDAO.listarTodas(conn);
        }
    }

    public List<ReservaMesa> listarPorFecha(LocalDate fecha) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return reservaDAO.listarPorFecha(conn, fecha);
        }
    }

    public List<ReservaMesa> listarHoy() throws SQLException {
        return listarPorFecha(LocalDate.now());
    }

    public Optional<ReservaMesa> buscarReservaProximaParaMesa(String numeroMesa, int minutosMargen) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return reservaDAO.buscarReservaProximaParaMesa(conn, numeroMesa, LocalDate.now(), LocalTime.now(), minutosMargen);
        }
    }

    public boolean actualizarEstado(int idReserva, String estado) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return reservaDAO.actualizarEstado(conn, idReserva, estado);
        }
    }

    public boolean eliminarReserva(int idReserva) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return reservaDAO.eliminarReserva(conn, idReserva);
        }
    }
}
