package com.foodcore.service;

import com.foodcore.exception.NegocioException;
import com.foodcore.model.Combo;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de negocio para gestión de Combos y Empaquetado (v4.8.0 Módulo 3).
 */
public interface ComboService {

    List<Combo> listarCombosActivos() throws SQLException;

    Optional<Combo> obtenerComboPorProducto(int idProducto) throws SQLException;

    Combo guardarCombo(Combo combo) throws NegocioException, SQLException;

    void desactivarCombo(int idCombo) throws NegocioException, SQLException;
}
