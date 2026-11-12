package com.foodcore.service.impl;

import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.ProductoPersonalizacionDAO;
import com.foodcore.dao.impl.ProductoPersonalizacionDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Producto;
import com.foodcore.model.ProductoModificador;
import com.foodcore.model.ProductoVariante;
import com.foodcore.service.PersonalizacionProductoService;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementación canónica de {@link PersonalizacionProductoService} (v4.8.0).
 */
public class PersonalizacionProductoServiceImpl implements PersonalizacionProductoService {

    private static final Logger LOGGER = Logger.getLogger(PersonalizacionProductoServiceImpl.class.getName());
    private final TransactionManager tx;
    private final ProductoDAO productoDAO;
    private final ProductoPersonalizacionDAO personalizacionDAO;

    public PersonalizacionProductoServiceImpl() {
        this(new JdbcTransactionManager(), new ProductoDAOImpl(), new ProductoPersonalizacionDAOImpl());
    }

    public PersonalizacionProductoServiceImpl(TransactionManager tx,
                                              ProductoDAO productoDAO,
                                              ProductoPersonalizacionDAO personalizacionDAO) {
        this.tx = Objects.requireNonNull(tx);
        this.productoDAO = Objects.requireNonNull(productoDAO);
        this.personalizacionDAO = Objects.requireNonNull(personalizacionDAO);
    }

    @Override
    public List<Producto> listarProductosDisponiblesConOpciones() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return listarProductosDisponiblesConOpciones(conn);
        }
    }

    @Override
    public List<Producto> listarProductosDisponiblesConOpciones(Connection conn) throws SQLException {
        List<Producto> productos = productoDAO.listarProductosDisponibles(conn);
        enriquecerListaProductos(conn, productos);
        return productos;
    }

    @Override
    public List<Producto> listarTodosConOpciones() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return listarTodosConOpciones(conn);
        }
    }

    @Override
    public List<Producto> listarTodosConOpciones(Connection conn) throws SQLException {
        List<Producto> productos = productoDAO.listarTodos(conn);
        enriquecerListaProductos(conn, productos);
        return productos;
    }

    @Override
    public void enriquecerProductoConOpciones(Connection conn, Producto producto) throws SQLException {
        if (producto == null || producto.getIdProducto() <= 0) {
            return;
        }
        producto.setVariantes(personalizacionDAO.listarVariantesPorProducto(conn, producto.getIdProducto()));
        producto.setModificadores(personalizacionDAO.listarModificadoresPorProducto(conn, producto.getIdProducto()));
    }

    @Override
    public Producto resolverOpcionesProducto(Producto producto) {
        if (producto == null || producto.tieneOpcionesPersonalizacion()
                || producto.getIdProducto() <= 0
                || producto.getCodigo() == null || producto.getCodigo().isBlank()) {
            return producto;
        }
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                return producto;
            }
            Producto enBd = productoDAO.buscarPorCodigo(conn, producto.getCodigo().trim());
            if (enBd == null || enBd.getIdProducto() != producto.getIdProducto()) {
                return producto;
            }
            enriquecerProductoConOpciones(conn, producto);
        } catch (SQLException | RuntimeException ex) {
            LOGGER.log(Level.FINE, "Sin resolución BD para opciones de producto " + producto.getNombre(), ex);
        }
        return producto;
    }

    @Override
    public List<ProductoVariante> listarVariantesPorProducto(int idProducto) throws SQLException {
        if (idProducto <= 0) {
            return List.of();
        }
        try (Connection conn = ConexionBD.getConnection()) {
            return personalizacionDAO.listarVariantesPorProducto(conn, idProducto);
        }
    }

    @Override
    public void guardarConfiguracionProducto(int idProducto,
                                             List<ProductoVariante> variantes,
                                             List<Integer> idsModificadoresAsociados)
            throws NegocioException, SQLException {
        if (idProducto <= 0) {
            throw new NegocioException("Identificador de producto inválido para guardar personalización.");
        }
        if (variantes != null) {
            for (ProductoVariante v : variantes) {
                if (v == null || v.getNombreVariante() == null || v.getNombreVariante().isBlank()) {
                    throw new NegocioException("Toda variante debe tener un nombre válido (ej. Mediana, Familiar).");
                }
                if (v.getPrecioFijo() != null && v.getPrecioFijo().compareTo(BigDecimal.ZERO) < 0) {
                    throw new NegocioException("El precio fijo de la variante no puede ser negativo.");
                }
                if (v.getPrecioFijo() == null && v.getPrecioAdicional() != null
                        && v.getPrecioAdicional().compareTo(BigDecimal.ZERO) < 0) {
                    throw new NegocioException("El monto adicional de la variante no puede ser negativo.");
                }
            }
        }
        tx.executeVoidChecked(conn -> {
            personalizacionDAO.reemplazarVariantesProducto(conn, idProducto, variantes);
            personalizacionDAO.reemplazarModificadoresAsociados(conn, idProducto, idsModificadoresAsociados);
        });
    }

    @Override
    public List<ProductoModificador> listarModificadoresGlobales() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return personalizacionDAO.listarModificadoresGlobales(conn);
        }
    }

    @Override
    public ProductoModificador guardarModificadorGlobal(String nombre, BigDecimal precio)
            throws NegocioException, SQLException {
        if (nombre == null || nombre.isBlank()) {
            throw new NegocioException("El nombre del extra/modificador es obligatorio.");
        }
        BigDecimal precioNorm = precio != null
                ? precio.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (precioNorm.compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("El precio del extra/modificador no puede ser negativo.");
        }
        ProductoModificador mod = new ProductoModificador(0, nombre.trim(), precioNorm, true);
        return tx.executeChecked(conn -> personalizacionDAO.guardarModificadorGlobal(conn, mod));
    }

    @Override
    public void eliminarModificadorGlobal(int idModificador) throws NegocioException, SQLException {
        if (idModificador <= 0) {
            throw new NegocioException("Seleccione un modificador válido para eliminar.");
        }
        tx.executeVoidChecked(conn -> personalizacionDAO.desactivarModificadorGlobal(conn, idModificador));
    }

    @Override
    public List<ProductoModificador> listarModificadoresPorProducto(int idProducto) throws SQLException {
        if (idProducto <= 0) {
            return List.of();
        }
        try (Connection conn = ConexionBD.getConnection()) {
            return personalizacionDAO.listarModificadoresPorProducto(conn, idProducto);
        }
    }

    private void enriquecerListaProductos(Connection conn, List<Producto> productos) throws SQLException {
        if (productos == null || productos.isEmpty()) {
            return;
        }
        Map<Integer, List<ProductoVariante>> mapaVariantes = personalizacionDAO.listarVariantesPorProductos(conn);
        Map<Integer, List<ProductoModificador>> mapaMods = personalizacionDAO.listarModificadoresPorProductos(conn);
        for (Producto p : productos) {
            p.setVariantes(mapaVariantes.getOrDefault(p.getIdProducto(), List.of()));
            p.setModificadores(mapaMods.getOrDefault(p.getIdProducto(), List.of()));
        }
    }
}
