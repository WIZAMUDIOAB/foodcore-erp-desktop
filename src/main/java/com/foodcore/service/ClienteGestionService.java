package com.foodcore.service;

import com.foodcore.dao.ClienteDAO;
import com.foodcore.dao.impl.ClienteDAOImpl;
import com.foodcore.model.Cliente;
import com.foodcore.util.ConexionBD;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de gestión de clientes (SPEC-FOODCORE-v1.3-ENTERPRISE §3.3).
 * Encapsula la apertura de conexiones y las operaciones CRUD sobre {@link ClienteDAO}
 * para que los controladores no dependan directamente de JDBC.
 */
public class ClienteGestionService {

    private final ClienteDAO clienteDAO;

    public ClienteGestionService() {
        this(new ClienteDAOImpl());
    }

    public ClienteGestionService(ClienteDAO clienteDAO) {
        this.clienteDAO = clienteDAO;
    }

    /**
     * Lista todos los clientes registrados.
     *
     * @return clientes ordenados según la consulta del DAO.
     * @throws SQLException si falla la consulta en base de datos.
     */
    public List<Cliente> listarTodos() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return clienteDAO.listarTodos(conn);
        }
    }

    /**
     * Actualiza los datos de un cliente existente.
     *
     * @param c cliente con los cambios a persistir.
     * @throws SQLException si falla la actualización en base de datos.
     */
    public void actualizarCliente(Cliente c) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            clienteDAO.actualizar(conn, c);
        }
    }

    /**
     * Inserta un nuevo cliente.
     *
     * @param c cliente a registrar.
     * @throws SQLException si falla la inserción en base de datos.
     */
    public void insertarCliente(Cliente c) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            clienteDAO.insertar(conn, c);
        }
    }

    /**
     * Cambia el estado activo/inactivo de un cliente.
     *
     * @param idCliente identificador del cliente.
     * @param activo nuevo estado.
     * @throws SQLException si falla la actualización en base de datos.
     */
    public void cambiarEstado(int idCliente, boolean activo) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            clienteDAO.cambiarEstado(conn, idCliente, activo);
        }
    }

    /**
     * Busca un cliente por DNI o teléfono.
     *
     * @param criterio documento o teléfono a buscar.
     * @return cliente encontrado, o vacío si no existe.
     * @throws SQLException si falla la consulta en base de datos.
     */
    public Optional<Cliente> buscarPorDniOTelefono(String criterio) throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return clienteDAO.buscarPorDniOTelefono(conn, criterio);
        }
    }
}
