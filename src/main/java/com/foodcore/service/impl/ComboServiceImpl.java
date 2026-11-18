package com.foodcore.service.impl;

import com.foodcore.dao.CategoriaDAO;
import com.foodcore.dao.CategoriaDAOImpl;
import com.foodcore.dao.ComboDAO;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.dao.ProductoDAOImpl;
import com.foodcore.dao.impl.ComboDAOImpl;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.Categoria;
import com.foodcore.model.Combo;
import com.foodcore.model.ComboDetalle;
import com.foodcore.model.Producto;
import com.foodcore.service.ComboService;
import com.foodcore.tx.TransactionManager;
import com.foodcore.tx.impl.JdbcTransactionManager;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.FormatoPersonalizacionUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementación transaccional de {@link ComboService} que sincroniza el catálogo unificado
 * ({@code producto.es_combo = 1}) con {@code combo} y {@code combo_detalle} (v4.8.0 Módulo 3).
 */
public class ComboServiceImpl implements ComboService {

    private final TransactionManager tx;
    private final ComboDAO comboDAO;
    private final ProductoDAO productoDAO;
    private final CategoriaDAO categoriaDAO;

    public ComboServiceImpl() {
        this(new JdbcTransactionManager(), new ComboDAOImpl(), new ProductoDAOImpl(), new CategoriaDAOImpl());
    }

    public ComboServiceImpl(TransactionManager tx,
                            ComboDAO comboDAO,
                            ProductoDAO productoDAO,
                            CategoriaDAO categoriaDAO) {
        this.tx = Objects.requireNonNull(tx);
        this.comboDAO = Objects.requireNonNull(comboDAO);
        this.productoDAO = Objects.requireNonNull(productoDAO);
        this.categoriaDAO = Objects.requireNonNull(categoriaDAO);
    }

    @Override
    public List<Combo> listarCombosActivos() throws SQLException {
        try (Connection conn = ConexionBD.getConnection()) {
            return comboDAO.listarCombosActivos(conn);
        }
    }

    @Override
    public Optional<Combo> obtenerComboPorProducto(int idProducto) throws SQLException {
        if (idProducto <= 0) {
            return Optional.empty();
        }
        try (Connection conn = ConexionBD.getConnection()) {
            return comboDAO.obtenerPorIdProducto(conn, idProducto);
        }
    }

    @Override
    public Combo guardarCombo(Combo combo) throws NegocioException, SQLException {
        if (combo == null || combo.getNombre() == null || combo.getNombre().isBlank()) {
            throw new NegocioException("El nombre del combo es obligatorio.");
        }
        BigDecimal precio = combo.getPrecioCombo() != null
                ? combo.getPrecioCombo().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        if (precio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegocioException("El precio final del combo debe ser mayor a S/ 0.00.");
        }
        List<ComboDetalle> detallesEntrada = combo.getDetalles();
        if (detallesEntrada == null || detallesEntrada.isEmpty()) {
            throw new NegocioException("Debe agregar al menos un producto componente al combo.");
        }

        Map<Integer, ComboDetalle> consolidados = new LinkedHashMap<>();
        for (ComboDetalle d : detallesEntrada) {
            if (d == null || d.getIdProducto() <= 0) {
                throw new NegocioException("Todos los componentes del combo deben ser productos válidos.");
            }
            if (d.getCantidad() <= 0) {
                throw new NegocioException("La cantidad de cada componente del combo debe ser mayor a cero.");
            }
            ComboDetalle previo = consolidados.get(d.getIdProducto());
            if (previo == null) {
                ComboDetalle copia = new ComboDetalle(0, combo.getIdCombo(), d.getIdProducto(), d.getCantidad());
                copia.setNombreProducto(d.getNombreProducto());
                copia.setCodigoProducto(d.getCodigoProducto());
                consolidados.put(d.getIdProducto(), copia);
            } else {
                previo.setCantidad(previo.getCantidad() + d.getCantidad());
            }
        }
        List<ComboDetalle> detallesLimpios = new ArrayList<>(consolidados.values());
        String nombreLimpio = combo.getNombre().trim();

        return tx.executeChecked(conn -> {
            validarComponentesNoSonCombos(conn, combo.getIdProducto(), detallesLimpios);
            int idCategoriaCombos = combo.getIdCategoria() > 0
                    ? combo.getIdCategoria()
                    : resolverOCrearCategoriaCombos(conn);

            String resumenDesc = FormatoPersonalizacionUtil.formatearResumenComponentesCombo(detallesLimpios);
            Producto respaldoProd = sincronizarProductoCombo(conn, combo, idCategoriaCombos, nombreLimpio, resumenDesc, precio);

            combo.setIdProducto(respaldoProd.getIdProducto());
            combo.setIdCategoria(idCategoriaCombos);
            combo.setCodigo(respaldoProd.getCodigo());
            combo.setNombre(nombreLimpio);
            combo.setPrecioCombo(precio);

            int idCombo = combo.getIdCombo();
            if (idCombo <= 0) {
                Optional<Combo> existente = comboDAO.obtenerPorIdProducto(conn, respaldoProd.getIdProducto());
                if (existente.isPresent()) {
                    idCombo = existente.get().getIdCombo();
                    combo.setIdCombo(idCombo);
                }
            }
            if (idCombo > 0) {
                comboDAO.actualizarCombo(conn, combo);
            } else {
                idCombo = comboDAO.crearCombo(conn, combo);
            }
            comboDAO.reemplazarDetalles(conn, idCombo, detallesLimpios);
            return comboDAO.obtenerPorId(conn, idCombo).orElse(combo);
        });
    }

    @Override
    public void desactivarCombo(int idCombo) throws NegocioException, SQLException {
        if (idCombo <= 0) {
            throw new NegocioException("Identificador de combo inválido.");
        }
        tx.executeVoidChecked(conn -> {
            Optional<Combo> opt = comboDAO.obtenerPorId(conn, idCombo);
            if (opt.isEmpty()) {
                throw new NegocioException("El combo #" + idCombo + " no existe.");
            }
            comboDAO.desactivarCombo(conn, idCombo);
            Combo c = opt.get();
            if (c.getIdProducto() != null && c.getIdProducto() > 0) {
                productoDAO.cambiarDisponibilidad(conn, c.getIdProducto(), false);
                productoDAO.eliminar(conn, c.getIdProducto());
            }
        });
    }

    private void validarComponentesNoSonCombos(Connection conn,
                                             Integer idProductoPropio,
                                             List<ComboDetalle> detalles) throws SQLException, NegocioException {
        for (ComboDetalle d : detalles) {
            if (idProductoPropio != null && idProductoPropio == d.getIdProducto()) {
                throw new NegocioException("Un combo no puede incluirse a sí mismo como componente.");
            }
            String sql = "SELECT nombre, es_combo FROM producto WHERE id_producto = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, d.getIdProducto());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new NegocioException("No se encontró el producto componente #" + d.getIdProducto());
                    }
                    if (rs.getBoolean("es_combo")) {
                        throw new NegocioException("No se permite anidar un combo dentro de otro combo: "
                                + rs.getString("nombre"));
                    }
                    if (d.getNombreProducto() == null || d.getNombreProducto().isBlank()) {
                        d.setNombreProducto(rs.getString("nombre"));
                    }
                }
            }
        }
    }

    private int resolverOCrearCategoriaCombos(Connection conn) throws SQLException {
        List<Categoria> categorias = categoriaDAO.listarTodas(conn);
        for (Categoria c : categorias) {
            if (c.getNombre() != null && "combos".equalsIgnoreCase(c.getNombre().trim())) {
                return c.getIdCategoria();
            }
        }
        Categoria nueva = new Categoria(0, "Combos", "Paquetes y combos promocionales con empaquetado", true);
        return categoriaDAO.crear(conn, nueva);
    }

    private Producto sincronizarProductoCombo(Connection conn,
                                              Combo combo,
                                              int idCategoria,
                                              String nombre,
                                              String descripcion,
                                              BigDecimal precio) throws SQLException {
        Integer idProd = combo.getIdProducto();
        String codigo = combo.getCodigo() != null && !combo.getCodigo().isBlank()
                ? combo.getCodigo().trim().toUpperCase(java.util.Locale.ROOT)
                : ("CMB-" + (System.currentTimeMillis() % 100000));

        if (idProd != null && idProd > 0) {
            Producto existente = new Producto(
                    idProd, idCategoria, codigo, nombre, descripcion,
                    precio, BigDecimal.ZERO, combo.isActivo(), null, false, null);
            existente.setEsCombo(true);
            productoDAO.actualizar(conn, existente);
            return existente;
        }

        Producto porCodigo = productoDAO.buscarPorCodigo(conn, codigo);
        if (porCodigo != null && porCodigo.isEsCombo()) {
            porCodigo.setIdCategoria(idCategoria);
            porCodigo.setNombre(nombre);
            porCodigo.setDescripcion(descripcion);
            porCodigo.setPrecioVenta(precio);
            porCodigo.setDisponible(combo.isActivo());
            porCodigo.setEsCombo(true);
            productoDAO.reactivar(conn, porCodigo.getIdProducto());
            productoDAO.actualizar(conn, porCodigo);
            return porCodigo;
        }

        Producto nuevo = new Producto(
                0, idCategoria, codigo, nombre, descripcion,
                precio, BigDecimal.ZERO, combo.isActivo(), null, false, null);
        nuevo.setEsCombo(true);
        productoDAO.crear(conn, nuevo);
        return nuevo;
    }
}
