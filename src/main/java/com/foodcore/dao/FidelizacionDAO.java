package com.foodcore.dao;

import com.foodcore.model.CompraClienteResumen;
import com.foodcore.model.CuponCumpleanos;
import com.foodcore.model.FidelizacionMovimiento;
import com.foodcore.model.HistorialPuntos;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface FidelizacionDAO {
    int registrarMovimiento(Connection conn, FidelizacionMovimiento mov) throws SQLException;
    long registrarHistorialPuntos(Connection conn, HistorialPuntos entry) throws SQLException;
    List<FidelizacionMovimiento> listarPorCliente(Connection conn, int idCliente) throws SQLException;
    List<HistorialPuntos> listarHistorialPuntos(Connection conn, int idCliente) throws SQLException;
    List<CompraClienteResumen> listarUltimasComprasCliente(Connection conn, int idCliente, int limite) throws SQLException;
    int obtenerPuntosActuales(Connection conn, int idCliente) throws SQLException;
    boolean actualizarPuntosCliente(Connection conn, int idCliente, int nuevosPuntos) throws SQLException;
    Optional<CuponCumpleanos> buscarCuponCumpleanosActivo(Connection conn, int idCliente, int mesActual) throws SQLException;
    int generarCuponCumpleanos(Connection conn, CuponCumpleanos cupon) throws SQLException;
    boolean marcarCuponUsado(Connection conn, String codigoCupon) throws SQLException;
    Optional<CuponCumpleanos> buscarCuponPorCodigo(Connection conn, String codigoCupon) throws SQLException;
}
