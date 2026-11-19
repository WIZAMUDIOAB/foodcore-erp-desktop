package com.foodcore.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.api.security.ApiSecurityFilter;
import com.foodcore.api.security.JwtService;
import com.foodcore.dao.MesaDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.ProductoDAO;
import com.foodcore.exception.CajaCerradaException;
import com.foodcore.exception.ConflictoEstadoException;
import com.foodcore.exception.InsumoNoEncontradoException;
import com.foodcore.exception.NegocioException;
import com.foodcore.exception.StockInsuficienteException;
import com.foodcore.model.Caja;
import com.foodcore.model.ComprobantePago;
import com.foodcore.model.DesgloseFiscal;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.DetalleVenta;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import com.foodcore.model.Venta;
import com.foodcore.service.CajaService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.VentaService;
import com.foodcore.util.ConexionBD;
import com.foodcore.util.ServidorSockets;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.javalin.http.Context;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador Javalin para catálogo de productos, imágenes confinadas, estado de mesas,
 * comandas de salón con precios autoritativos en BD y cobro real de caja vía {@link VentaService}
 * (v5.7.1 Hardening).
 */
public class PedidoController {

    private static final Logger LOGGER = Logger.getLogger(PedidoController.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final Path UPLOADS_PLATILLOS_DIR = Path.of("uploads", "platillos");
    private static final Set<String> EXTENSIONES_IMAGEN_PERMITIDAS = Set.of(".png", ".jpg", ".jpeg", ".webp");
    private static final Set<String> TIPOS_COMPROBANTE_VALIDOS = Set.of("BOLETA", "FACTURA", "TICKET");
    private static final Set<String> MEDIOS_PAGO_VALIDOS =
            Set.of("EFECTIVO", "TARJETA", "YAPE", "PLIN", "YAPE_PLIN", "TRANSFERENCIA", "MIXTO");
    private static final Set<String> ESTADOS_PEDIDO_VALIDOS = Set.of(
            "PENDIENTE", "REGISTRADO", "ABIERTO", "EN_PREPARACION", "LISTO",
            "ENTREGADO", "ITEMS_ADICIONALES", "POR_COBRAR", "COBRADO", "PAGADO", "CANCELADO", "ANULADO"
    );
    private static final Set<String> ESTADOS_PEDIDO_TERMINALES = Set.of(
            "COBRADO", "PAGADO", "CERRADO", "CANCELADO", "ANULADO"
    );

    private final ApiState state;
    private final ProductoDAO productoDAO;
    private final MesaDAO mesaDAO;
    private final PedidoDAO pedidoDAO;
    private final PedidoService pedidoService;
    private final CajaService cajaService;
    private final VentaService ventaService;

    public PedidoController(ApiState state,
                            ProductoDAO productoDAO,
                            MesaDAO mesaDAO,
                            PedidoDAO pedidoDAO,
                            PedidoService pedidoService,
                            CajaService cajaService) {
        this(state, productoDAO, mesaDAO, pedidoDAO, pedidoService, cajaService, new VentaService());
    }

    public PedidoController(ApiState state,
                            ProductoDAO productoDAO,
                            MesaDAO mesaDAO,
                            PedidoDAO pedidoDAO,
                            PedidoService pedidoService,
                            CajaService cajaService,
                            VentaService ventaService) {
        this.state = state;
        this.productoDAO = productoDAO;
        this.mesaDAO = mesaDAO;
        this.pedidoDAO = pedidoDAO;
        this.pedidoService = pedidoService;
        this.cajaService = cajaService;
        this.ventaService = ventaService != null ? ventaService : new VentaService();
    }

    public void handleListarProductos(Context ctx) {
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        ctx.header("Pragma", "no-cache");

        ArrayNode array = state.mapper.createArrayNode();
        for (Producto p : obtenerCatalogoProductos(false)) {
            array.add(construirJsonProducto(p));
        }
        ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
    }

    public void handleListarProductosDisponibles(Context ctx) {
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        ctx.header("Pragma", "no-cache");

        ArrayNode array = state.mapper.createArrayNode();
        for (Producto p : obtenerCatalogoProductos(true)) {
            array.add(construirJsonProducto(p));
        }
        ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
    }

    /**
     * GET /api/public/productos - Solo lectura pública del catálogo (carta digital sin datos internos).
     */
    public void handleListarProductosPublicos(Context ctx) {
        ctx.header("Cache-Control", "public, max-age=30");
        ArrayNode array = state.mapper.createArrayNode();
        for (Producto p : obtenerCatalogoProductos(false)) {
            array.add(construirJsonProductoPublico(p));
        }
        ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
    }

    /**
     * GET /api/public/productos/disponibles - Solo lectura pública de platos disponibles.
     */
    public void handleListarProductosDisponiblesPublicos(Context ctx) {
        ctx.header("Cache-Control", "public, max-age=30");
        ArrayNode array = state.mapper.createArrayNode();
        for (Producto p : obtenerCatalogoProductos(true)) {
            array.add(construirJsonProductoPublico(p));
        }
        ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
    }

    private List<Producto> obtenerCatalogoProductos(boolean soloDisponibles) {
        List<Producto> productos = new ArrayList<>();
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                productos = soloDisponibles
                        ? productoDAO.listarProductosDisponibles(conn)
                        : productoDAO.listarTodos(conn);
                if (!soloDisponibles && productos.isEmpty()) {
                    productos = productoDAO.listarProductosDisponibles(conn);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error consultando catálogo de productos en BD", e);
        }

        if (productos.isEmpty() && ApiState.isDemoFallbackEnabled()) {
            productos = obtenerCatalogoRespaldo();
        }
        if (soloDisponibles) {
            productos = productos.stream().filter(Producto::isDisponible).toList();
        }
        return productos;
    }

    public void handleActualizarDisponibilidad(Context ctx) {
        try {
            int idProducto = Integer.parseInt(ctx.pathParam("id"));
            if (idProducto <= 0) {
                state.responderError(ctx, 400, "ID de producto inválido.");
                return;
            }
            boolean nuevoEstado = false;

            if (ctx.body() != null && !ctx.body().isBlank()) {
                JsonNode body = state.mapper.readTree(ctx.body());
                if (body.has("disponible")) {
                    nuevoEstado = body.get("disponible").asBoolean();
                } else if (body.has("agotado")) {
                    nuevoEstado = !body.get("agotado").asBoolean();
                }
            } else if (ctx.queryParam("disponible") != null) {
                nuevoEstado = Boolean.parseBoolean(ctx.queryParam("disponible"));
            }

            try (Connection conn = ConexionBD.getConnection()) {
                if (conn == null) {
                    state.responderError(ctx, 503, "Servicio de base de datos no disponible.");
                    return;
                }
                productoDAO.cambiarDisponibilidad(conn, idProducto, nuevoEstado);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error SQL actualizando disponibilidad de producto #" + idProducto, e);
                state.responderError(ctx, 503, "No se pudo actualizar la disponibilidad en base de datos.");
                return;
            }

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("success", true);
            resp.put("id_producto", idProducto);
            resp.put("disponible", nuevoEstado);
            resp.put("mensaje", nuevoEstado ? "Producto disponible en catálogo" : "Producto marcado como AGOTADO");
            ctx.status(200).json(resp);
        } catch (NumberFormatException | JsonProcessingException e) {
            state.responderError(ctx, 400, "Parámetros de disponibilidad inválidos.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al actualizar disponibilidad de producto", e);
            state.responderError(ctx, 500, "Error interno al actualizar disponibilidad.");
        }
    }

    /**
     * GET /api/productos/{id}/imagen y /api/public/productos/{id}/imagen
     * Confinamiento estricto dentro de {@code uploads/platillos} mediante {@code toRealPath().normalize()}
     * y lista blanca de extensiones ({@code .png, .jpg, .jpeg, .webp}); bloquea {@code .svg} y path traversal.
     */
    public void handleImagenProducto(Context ctx) {
        try {
            int idProd = Integer.parseInt(ctx.pathParam("id"));
            if (idProd <= 0) {
                ctx.status(400);
                return;
            }
            String rutaImg = null;
            try (Connection conn = ConexionBD.getConnection()) {
                if (conn != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT imagen_url FROM producto WHERE id_producto = ?")) {
                        ps.setInt(1, idProd);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                rutaImg = rs.getString("imagen_url");
                            }
                        }
                    }
                }
            }
            if (rutaImg == null || rutaImg.isBlank()) {
                ctx.status(404);
                return;
            }
            if (esRutaImagenSospechosa(rutaImg)) {
                LOGGER.warning("Intento LFI / extensión prohibida bloqueada en handleImagenProducto para producto #" + idProd);
                ctx.status(403);
                return;
            }
            Optional<Path> confinada = resolverArchivoImagenConfinado(UPLOADS_PLATILLOS_DIR, rutaImg);
            if (confinada.isEmpty()) {
                ctx.status(404);
                return;
            }
            servirArchivoImagen(ctx, confinada.get());
        } catch (NumberFormatException e) {
            ctx.status(400);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "No se pudo servir imagen de producto", e);
            ctx.status(404);
        }
    }

    public void handleServirUploadPlatillo(Context ctx) {
        String rawArchivo = ctx.pathParam("archivo");
        if (esRutaImagenSospechosa(rawArchivo)) {
            ctx.status(403);
            return;
        }
        Optional<Path> target = resolverArchivoImagenConfinado(UPLOADS_PLATILLOS_DIR, rawArchivo);
        if (target.isEmpty()) {
            ctx.status(404);
            return;
        }
        servirArchivoImagen(ctx, target.get());
    }

    /**
     * Valida si un nombre o ruta de imagen contiene traversal, rutas absolutas o extensiones prohibidas (como .svg).
     */
    static boolean esRutaImagenSospechosa(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return true;
        }
        String limpio = rawInput.trim().replace('\\', '/');
        if (limpio.contains("\0") || limpio.contains("..")) {
            return true;
        }
        String relativo = limpiarPrefijoUploads(limpio);
        if (relativo.isBlank() || relativo.startsWith("/") || relativo.contains("/") || relativo.contains(":")) {
            return true;
        }
        String lower = relativo.toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONES_IMAGEN_PERMITIDAS) {
            if (lower.endsWith(ext)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resuelve de forma segura un archivo de imagen estrictamente confinado dentro de {@code baseDirRaw}
     * usando {@code toRealPath().normalize().startsWith(baseDir.toRealPath().normalize())}.
     */
    static Optional<Path> resolverArchivoImagenConfinado(Path baseDirRaw, String rawInput) {
        if (baseDirRaw == null || esRutaImagenSospechosa(rawInput)) {
            return Optional.empty();
        }
        String limpio = rawInput.trim().replace('\\', '/');
        String nombreArchivo = limpiarPrefijoUploads(limpio);
        if (!nombreArchivo.matches("^[a-zA-Z0-9._-]+$")) {
            return Optional.empty();
        }
        try {
            if (!Files.exists(baseDirRaw) || !Files.isDirectory(baseDirRaw)) {
                return Optional.empty();
            }
            Path baseReal = baseDirRaw.toRealPath().normalize();
            Path candidato = baseReal.resolve(nombreArchivo).normalize();
            if (!candidato.startsWith(baseReal) || !Files.exists(candidato) || !Files.isRegularFile(candidato)) {
                return Optional.empty();
            }
            Path candidatoReal = candidato.toRealPath().normalize();
            if (!candidatoReal.startsWith(baseReal)) {
                return Optional.empty();
            }
            return Optional.of(candidatoReal);
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static String limpiarPrefijoUploads(String rutaConSlashes) {
        if (rutaConSlashes.startsWith("/uploads/platillos/")) {
            return rutaConSlashes.substring("/uploads/platillos/".length());
        }
        if (rutaConSlashes.startsWith("uploads/platillos/")) {
            return rutaConSlashes.substring("uploads/platillos/".length());
        }
        return rutaConSlashes;
    }

    public void handleListarMesas(Context ctx) {
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        ctx.header("Pragma", "no-cache");
        ArrayNode array = state.mapper.createArrayNode();
        Map<String, String> estadosPorMesa = new LinkedHashMap<>();
        Map<String, Double> totalesPorMesa = new LinkedHashMap<>();

        try (Connection conn = ConexionBD.getConnection()) {
            if (conn != null) {
                Map<String, String> dbEstados = mesaDAO.obtenerEstadoTodasMesas(conn);
                for (Map.Entry<String, String> entry : dbEstados.entrySet()) {
                    estadosPorMesa.put(entry.getKey(), entry.getValue());
                }

                String sqlPed = "SELECT numero_mesa, total, estado FROM pedido "
                        + "WHERE estado NOT IN ('COBRADO', 'CERRADO', 'ANULADO', 'CANCELADO', 'PAGADO')";
                try (PreparedStatement ps = conn.prepareStatement(sqlPed);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String num = rs.getString("numero_mesa");
                        double tot = rs.getDouble("total");
                        if (num != null && !num.isBlank()) {
                            estadosPorMesa.put(num, "OCUPADA");
                            totalesPorMesa.put(num, tot);
                        }
                    }
                }
            }
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.FINE, "No se pudo consultar tabla mesa en GET /api/mesas", e);
        }

        if (estadosPorMesa.isEmpty() && ApiState.isDemoFallbackEnabled()) {
            for (int i = 1; i <= 12; i++) {
                estadosPorMesa.put(String.format("%02d", i), "LIBRE");
            }
        }

        int idx = 1;
        for (Map.Entry<String, String> entry : estadosPorMesa.entrySet()) {
            String numero = entry.getKey();
            String estado = entry.getValue() != null ? entry.getValue() : "LIBRE";
            ObjectNode mesaNode = state.mapper.createObjectNode();
            mesaNode.put("id", idx);
            mesaNode.put("numero", numero);
            mesaNode.put("capacidad", (idx % 3 == 0) ? 6 : ((idx % 2 == 0) ? 4 : 2));
            mesaNode.put("zona", idx <= 8 ? "Salón Principal" : "Terraza Lounge");
            mesaNode.put("estado", estado);
            mesaNode.put("total_actual", totalesPorMesa.getOrDefault(numero, 0.0));
            array.add(mesaNode);
            idx++;
        }

        ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
    }

    /**
     * POST /api/pedidos — Carga precios y disponibilidad autoritativos desde {@link ProductoDAO},
     * valida {@code cantidad > 0} y persiste en MySQL sin swallows a IDs ficticios en memoria.
     */
    public void handleCrearPedido(Context ctx) {
        JwtService.JwtClaims claims = ctx.attribute(ApiSecurityFilter.ATTR_AUTH_CLAIMS);
        if (claims == null || claims.idUsuario() <= 0) {
            state.responderError(ctx, 401, "Autenticación requerida.");
            return;
        }
        try {
            JsonNode body = state.mapper.readTree(ctx.body());
            String uuidLocal = state.extraerTexto(body, "uuid_local", "uuidLocal", "idempotency_key");
            if (uuidLocal == null || uuidLocal.isBlank()) {
                uuidLocal = UUID.randomUUID().toString();
            } else if (uuidLocal.length() > 36) {
                state.responderError(ctx, 400, "El campo 'uuid_local' no puede exceder 36 caracteres.");
                return;
            }

            String numeroMesa = state.extraerTexto(body, "numero_mesa", "numeroMesa", "mesa");
            if (numeroMesa == null || numeroMesa.isBlank()) {
                state.responderError(ctx, 400, "El campo 'numero_mesa' es obligatorio.");
                return;
            }
            if (numeroMesa.length() == 1 && Character.isDigit(numeroMesa.charAt(0))) {
                numeroMesa = "0" + numeroMesa;
            }

            JsonNode itemsNode = body.has("items") ? body.get("items") : body.get("detalles");
            if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) {
                state.responderError(ctx, 400, "El pedido debe incluir al menos un ítem en 'items'.");
                return;
            }

            int idUsuario = claims.idUsuario();
            int idUsuarioCreador = claims.idUsuario();

            List<DetallePedido> detalles = new ArrayList<>();
            BigDecimal totalCalculado = BigDecimal.ZERO;

            try (Connection conn = ConexionBD.getConnection()) {
                if (conn == null) {
                    state.responderError(ctx, 503, "Base de datos no disponible para registrar pedidos.");
                    return;
                }

                // 1. Verificar idempotencia real en MySQL
                Optional<Pedido> optExistente = pedidoDAO.buscarPorIdempotencyKey(conn, uuidLocal);
                if (optExistente.isPresent()) {
                    Pedido existente = optExistente.get();
                    List<DetallePedido> detExistentes = pedidoDAO.listarDetallesPorPedido(conn, existente.getIdPedido());
                    ObjectNode respIdem = construirRespuestaPedido(existente, detExistentes, true);
                    ctx.status(200).contentType("application/json; charset=UTF-8").result(respIdem.toString());
                    return;
                }

                // 2. Cargar catálogo autoritativo desde ProductoDAO (bloquea price tampering y productos agotados)
                Map<Integer, Producto> catalogoPorId = new HashMap<>();
                for (Producto p : productoDAO.listarTodos(conn)) {
                    catalogoPorId.put(p.getIdProducto(), p);
                }

                for (JsonNode item : itemsNode) {
                    int idProd = extraerIdProducto(item);
                    if (idProd <= 0) {
                        state.responderError(ctx, 400, "Cada ítem debe especificar un 'id_producto' válido.");
                        return;
                    }
                    if (!item.hasNonNull("cantidad") || !item.get("cantidad").isInt()) {
                        state.responderError(ctx, 400, "Cada ítem debe indicar una 'cantidad' entera positiva.");
                        return;
                    }
                    int cantidad = item.get("cantidad").asInt(0);
                    if (cantidad <= 0) {
                        state.responderError(ctx, 400, "La cantidad de cada ítem debe ser mayor a cero.");
                        return;
                    }

                    Producto prodAutoritativo = catalogoPorId.get(idProd);
                    if (prodAutoritativo == null) {
                        state.responderError(ctx, 422, "Producto no encontrado en catálogo: #" + idProd);
                        return;
                    }
                    if (!prodAutoritativo.isDisponible()) {
                        state.responderError(ctx, 422, "Producto no disponible actualmente: " + prodAutoritativo.getNombre());
                        return;
                    }
                    BigDecimal precioAutoritativo = prodAutoritativo.getPrecioVenta();
                    if (precioAutoritativo == null || precioAutoritativo.compareTo(BigDecimal.ZERO) <= 0) {
                        state.responderError(ctx, 422, "El producto #" + idProd + " no tiene un precio válido configurado.");
                        return;
                    }

                    BigDecimal precioUnit = precioAutoritativo.setScale(2, RoundingMode.HALF_UP);
                    BigDecimal subtotal = precioUnit.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
                    String notas = state.extraerTexto(item, "notas", "notas_preparacion", "notasPreparacion");

                    DetallePedido det = new DetallePedido(
                            0, 0, idProd, prodAutoritativo.getNombre(), cantidad, precioUnit, subtotal,
                            notas != null ? notas : "");
                    detalles.add(det);
                    totalCalculado = totalCalculado.add(subtotal);
                }
            } catch (SQLException exSql) {
                LOGGER.log(Level.WARNING, "Error SQL validando catálogo/idempotencia en POST /api/pedidos", exSql);
                state.responderError(ctx, 503, "Servicio de base de datos no disponible.");
                return;
            }

            Pedido nuevoPedido = new Pedido();
            nuevoPedido.setIdUsuario(idUsuario);
            nuevoPedido.setIdUsuarioCreador(idUsuarioCreador);
            nuevoPedido.setNumeroMesa(numeroMesa);
            try {
                int idMesaNum = Integer.parseInt(numeroMesa.replaceAll("[^0-9]", ""));
                if (idMesaNum > 0) {
                    nuevoPedido.setIdMesa(idMesaNum);
                }
            } catch (NumberFormatException e) {
                LOGGER.log(Level.FINEST, "Número de mesa alfanumérico sin id numérico", e);
            }
            nuevoPedido.setTotal(totalCalculado.setScale(2, RoundingMode.HALF_UP));
            nuevoPedido.setIdempotencyKey(uuidLocal);

            Pedido guardado;
            try {
                guardado = pedidoService.registrarPedidoYEnviarComanda(nuevoPedido, detalles);
            } catch (ConflictoEstadoException exConf) {
                state.responderError(ctx, 409, exConf.getMessage() != null ? exConf.getMessage() : "Conflicto de estado del pedido.");
                return;
            } catch (NegocioException exNeg) {
                state.responderError(ctx, 422, exNeg.getMessage() != null ? exNeg.getMessage() : "Validación de negocio fallida.");
                return;
            } catch (SQLException exDb) {
                LOGGER.log(Level.WARNING, "Error persistiendo pedido en POST /api/pedidos", exDb);
                state.responderError(ctx, 503, "No se pudo persistir el pedido en base de datos.");
                return;
            }

            try (Connection conn = ConexionBD.getConnection()) {
                if (conn != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE mesa SET estado = 'OCUPADA', id_pedido_actual = ? WHERE numero = ?")) {
                        ps.setInt(1, guardado.getIdPedido());
                        ps.setString(2, numeroMesa);
                        ps.executeUpdate();
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Aviso al sincronizar estado de mesa en POST /api/pedidos", e);
            }

            emitirEventoDirectoKds(guardado, detalles);

            ObjectNode resp = construirRespuestaPedido(guardado, detalles, false);
            ctx.status(201).contentType("application/json; charset=UTF-8").result(resp.toString());

        } catch (JsonProcessingException e) {
            state.responderError(ctx, 400, "Cuerpo JSON inválido en solicitud de pedido.");
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error procesando POST /api/pedidos", e);
            state.responderError(ctx, 500, "No se pudo registrar el pedido.");
        }
    }

    /**
     * GET /api/pedidos — Lee pedidos activos directamente desde MySQL (sin caché unbounded en memoria).
     */
    public void handleListarPedidos(Context ctx) {
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        ArrayNode array = state.mapper.createArrayNode();
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                state.responderError(ctx, 503, "Base de datos no disponible.");
                return;
            }
            List<Pedido> pedidos = pedidoDAO.listarPedidosActivos(conn);
            Map<Integer, List<DetallePedido>> detallesPorPedido = cargarDetallesEnLote(conn, pedidos);
            for (Pedido p : pedidos) {
                List<DetallePedido> dets = detallesPorPedido.getOrDefault(p.getIdPedido(), List.of());
                array.add(construirRespuestaPedido(p, dets, false));
            }
            ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error consultando GET /api/pedidos en BD", e);
            state.responderError(ctx, 503, "No se pudieron consultar los pedidos en base de datos.");
        }
    }

    /**
     * GET /api/pedidos/{uuid} — Busca el pedido por {@code idempotency_key} directamente en MySQL.
     */
    public void handleObtenerPedidoPorUuid(Context ctx) {
        String uuid = ctx.pathParam("uuid");
        if (uuid == null || uuid.isBlank()) {
            state.responderError(ctx, 400, "UUID de pedido inválido.");
            return;
        }
        try (Connection conn = ConexionBD.getConnection()) {
            if (conn == null) {
                state.responderError(ctx, 503, "Base de datos no disponible.");
                return;
            }
            Optional<Pedido> opt = pedidoDAO.buscarPorIdempotencyKey(conn, uuid.trim());
            if (opt.isEmpty()) {
                state.responderError(ctx, 404, "Pedido no encontrado.");
                return;
            }
            Pedido pedido = opt.get();
            List<DetallePedido> detalles = pedidoDAO.listarDetallesPorPedido(conn, pedido.getIdPedido());
            ObjectNode resp = construirRespuestaPedido(pedido, detalles, false);
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error consultando GET /api/pedidos/{uuid} en BD", e);
            state.responderError(ctx, 503, "No se pudo consultar el pedido en base de datos.");
        }
    }

    public void handleActualizarEstadoPedido(Context ctx) {
        try {
            int idPedido = Integer.parseInt(ctx.pathParam("id"));
            if (idPedido <= 0) {
                state.responderError(ctx, 400, "ID de pedido inválido.");
                return;
            }
            JsonNode body = state.mapper.readTree(ctx.body());
            if (!body.hasNonNull("estado") || body.get("estado").asText().isBlank()) {
                state.responderError(ctx, 400, "El campo 'estado' es obligatorio.");
                return;
            }
            String nuevoEstado = body.get("estado").asText().trim().toUpperCase(Locale.ROOT);
            if (!ESTADOS_PEDIDO_VALIDOS.contains(nuevoEstado)) {
                state.responderError(ctx, 400, "Estado de pedido no permitido: " + nuevoEstado);
                return;
            }

            try (Connection conn = ConexionBD.getConnection()) {
                if (conn == null) {
                    state.responderError(ctx, 503, "Base de datos no disponible.");
                    return;
                }
                Optional<Pedido> actualOpt = pedidoDAO.obtenerPorId(conn, idPedido);
                if (actualOpt.isEmpty()) {
                    state.responderError(ctx, 404, "Pedido no encontrado: #" + idPedido);
                    return;
                }
                String estadoActual = actualOpt.get().getEstado() != null
                        ? actualOpt.get().getEstado().trim().toUpperCase(Locale.ROOT)
                        : "";
                if (ESTADOS_PEDIDO_TERMINALES.contains(estadoActual) && !estadoActual.equals(nuevoEstado)) {
                    state.responderError(ctx, 409,
                            "No se puede cambiar el estado de un pedido en estado terminal (" + estadoActual + ").");
                    return;
                }
                pedidoDAO.actualizarEstadoPedido(conn, idPedido, nuevoEstado);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error SQL actualizando estado de pedido #" + idPedido, e);
                state.responderError(ctx, 503, "No se pudo actualizar el estado del pedido en base de datos.");
                return;
            }

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("id_pedido", idPedido);
            resp.put("nuevo_estado", nuevoEstado);
            resp.put("mensaje", "Estado del pedido actualizado a " + nuevoEstado);
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());

        } catch (NumberFormatException | JsonProcessingException e) {
            state.responderError(ctx, 400, "Solicitud de actualización de estado inválida.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al actualizar estado de pedido", e);
            state.responderError(ctx, 500, "Error interno al actualizar estado del pedido.");
        }
    }

    public void handleCajaEstado(Context ctx) {
        try {
            Optional<Caja> optCaja = Optional.empty();
            try {
                optCaja = cajaService.obtenerCajaActiva();
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Aviso consultando caja activa en BD", e);
            }

            if (optCaja.isPresent()) {
                Caja c = optCaja.get();
                BigDecimal ventas = BigDecimal.ZERO;
                BigDecimal gastos = BigDecimal.ZERO;
                try {
                    ventas = cajaService.calcularVentasTurno(c.getIdCaja());
                    gastos = cajaService.calcularGastosTurno(c.getIdCaja());
                } catch (Exception ex) {
                    LOGGER.log(Level.FINE, "Aviso consultando totales del turno en BD", ex);
                }
                BigDecimal saldo = c.getMontoApertura().add(ventas).subtract(gastos);

                ObjectNode resp = state.mapper.createObjectNode();
                resp.put("abierta", true);
                resp.put("id_caja", c.getIdCaja());
                resp.put("nombre_caja", c.getNombreCaja());
                resp.put("id_usuario", c.getIdUsuarioApertura());
                resp.put("monto_apertura", c.getMontoApertura().doubleValue());
                resp.put("total_ventas", ventas.doubleValue());
                resp.put("total_gastos", gastos.doubleValue());
                resp.put("saldo_esperado", saldo.doubleValue());
                resp.put("estado", c.getEstado());
                ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());
                return;
            }

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("abierta", false);
            resp.put("mensaje", "No hay ningún turno de caja abierto actualmente.");
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al consultar estado de caja", e);
            state.responderError(ctx, 500, "Error interno al consultar estado de caja.");
        }
    }

    /**
     * POST /api/caja/abrir — Toma {@code id_usuario} estrictamente de los claims JWT y persiste en BD.
     */
    public void handleCajaAbrir(Context ctx) {
        JwtService.JwtClaims claims = ctx.attribute(ApiSecurityFilter.ATTR_AUTH_CLAIMS);
        if (claims == null || claims.idUsuario() <= 0) {
            state.responderError(ctx, 401, "Autenticación requerida.");
            return;
        }
        try {
            JsonNode body = state.mapper.readTree(ctx.body());
            int idUsuario = claims.idUsuario();
            String nombreCaja = body.hasNonNull("nombre_caja") && !body.get("nombre_caja").asText().isBlank()
                    ? body.get("nombre_caja").asText().trim()
                    : "Caja Principal";
            double montoRaw = body.hasNonNull("monto_inicial") ? body.get("monto_inicial").asDouble(Double.NaN) : 0.0;
            if (Double.isNaN(montoRaw) || Double.isInfinite(montoRaw) || montoRaw < 0.0) {
                state.responderError(ctx, 400, "El monto inicial de caja debe ser un número válido mayor o igual a cero.");
                return;
            }
            BigDecimal montoInicial = BigDecimal.valueOf(montoRaw).setScale(2, RoundingMode.HALF_UP);

            int idCaja;
            try {
                idCaja = cajaService.abrirTurnoCaja(idUsuario, nombreCaja, montoInicial);
            } catch (NegocioException exNeg) {
                String msg = exNeg.getMessage() != null ? exNeg.getMessage() : "No se pudo abrir la caja.";
                int status = msg.toLowerCase(Locale.ROOT).contains("ya existe una caja abierta") ? 409 : 422;
                state.responderError(ctx, status, msg);
                return;
            } catch (SQLException exSql) {
                LOGGER.log(Level.WARNING, "Error SQL abriendo caja", exSql);
                state.responderError(ctx, 503, "No se pudo abrir la caja en base de datos.");
                return;
            }

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("id_caja", idCaja);
            resp.put("id_usuario", idUsuario);
            resp.put("nombre_caja", nombreCaja);
            resp.put("monto_inicial", montoInicial.doubleValue());
            resp.put("mensaje", "Turno de caja #" + idCaja + " abierto exitosamente con fondo S/ " + montoInicial);
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());

        } catch (JsonProcessingException e) {
            state.responderError(ctx, 400, "Solicitud JSON inválida al abrir caja.");
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error al abrir turno de caja", e);
            state.responderError(ctx, 500, "Error interno al abrir turno de caja.");
        }
    }

    /**
     * POST /api/caja/cerrar — Toma {@code id_usuario} estrictamente de los claims JWT y ejecuta cierre real en BD.
     */
    public void handleCajaCerrar(Context ctx) {
        JwtService.JwtClaims claims = ctx.attribute(ApiSecurityFilter.ATTR_AUTH_CLAIMS);
        if (claims == null || claims.idUsuario() <= 0) {
            state.responderError(ctx, 401, "Autenticación requerida.");
            return;
        }
        try {
            JsonNode body = state.mapper.readTree(ctx.body());
            int idUsuario = claims.idUsuario();
            int idCaja = body.hasNonNull("id_caja") ? body.get("id_caja").asInt(0) : 0;
            if (!body.hasNonNull("efectivo_fisico_contado")) {
                state.responderError(ctx, 400, "El campo 'efectivo_fisico_contado' es obligatorio.");
                return;
            }
            double contadoRaw = body.get("efectivo_fisico_contado").asDouble(Double.NaN);
            if (Double.isNaN(contadoRaw) || Double.isInfinite(contadoRaw) || contadoRaw < 0.0) {
                state.responderError(ctx, 400, "El efectivo físico contado debe ser un número válido no negativo.");
                return;
            }
            BigDecimal contado = BigDecimal.valueOf(contadoRaw).setScale(2, RoundingMode.HALF_UP);

            BigDecimal esperado;
            try {
                Optional<Caja> activaOpt = cajaService.obtenerCajaActiva();
                if (activaOpt.isEmpty()) {
                    state.responderError(ctx, 409, "No hay ningún turno de caja abierto para cerrar.");
                    return;
                }
                Caja activa = activaOpt.get();
                if (idCaja <= 0) {
                    idCaja = activa.getIdCaja();
                } else if (activa.getIdCaja() != idCaja) {
                    state.responderError(ctx, 409, "La caja #" + idCaja + " no corresponde al turno abierto actual.");
                    return;
                }
                BigDecimal ventasTurno = cajaService.calcularVentasTurno(idCaja);
                BigDecimal gastosTurno = cajaService.calcularGastosTurno(idCaja);
                esperado = activa.getMontoApertura().add(ventasTurno).subtract(gastosTurno)
                        .setScale(2, RoundingMode.HALF_UP);

                cajaService.cerrarTurnoCaja(idCaja, idUsuario, contado);
            } catch (CajaCerradaException exClosed) {
                state.responderError(ctx, 409, exClosed.getMessage() != null ? exClosed.getMessage() : "La caja ya está cerrada.");
                return;
            } catch (NegocioException exNeg) {
                state.responderError(ctx, 422, exNeg.getMessage() != null ? exNeg.getMessage() : "Validación fallida al cerrar caja.");
                return;
            } catch (SQLException exSql) {
                LOGGER.log(Level.WARNING, "Error SQL cerrando caja", exSql);
                state.responderError(ctx, 503, "No se pudo cerrar la caja en base de datos.");
                return;
            }

            BigDecimal diferencia = contado.subtract(esperado).setScale(2, RoundingMode.HALF_UP);
            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("id_caja", idCaja);
            resp.put("id_usuario", idUsuario);
            resp.put("monto_esperado", esperado.doubleValue());
            resp.put("monto_contado", contado.doubleValue());
            resp.put("diferencia", diferencia.doubleValue());
            resp.put("mensaje", "Turno de caja cerrado exitosamente (Cierre Z).");
            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());

        } catch (JsonProcessingException e) {
            state.responderError(ctx, 400, "Solicitud JSON inválida al cerrar caja.");
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error al cerrar turno de caja", e);
            state.responderError(ctx, 500, "Error interno al cerrar turno de caja.");
        }
    }

    /**
     * POST /api/caja/cobrar — Toma {@code id_usuario} estrictamente de los claims JWT,
     * resuelve ítems y montos autoritativos desde MySQL y persiste una {@link Venta} real vía {@link VentaService}.
     */
    public void handleCajaCobrar(Context ctx) {
        JwtService.JwtClaims claims = ctx.attribute(ApiSecurityFilter.ATTR_AUTH_CLAIMS);
        if (claims == null || claims.idUsuario() <= 0) {
            state.responderError(ctx, 401, "Autenticación requerida.");
            return;
        }
        try {
            JsonNode body = state.mapper.readTree(ctx.body());
            int idUsuario = claims.idUsuario();
            int idPedido = body.hasNonNull("id_pedido") ? body.get("id_pedido").asInt(0) : 0;
            String numeroMesa = body.hasNonNull("numero_mesa") ? body.get("numero_mesa").asText().trim() : "";
            String tipoComprobante = body.hasNonNull("tipo_comprobante")
                    ? body.get("tipo_comprobante").asText().trim().toUpperCase(Locale.ROOT)
                    : "BOLETA";
            String medioPago = body.hasNonNull("medio_pago")
                    ? body.get("medio_pago").asText().trim().toUpperCase(Locale.ROOT)
                    : "EFECTIVO";
            String idempotencyKey = state.extraerTexto(body, "idempotency_key", "uuid_local");
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                idempotencyKey = UUID.randomUUID().toString();
            } else if (idempotencyKey.length() > 36) {
                state.responderError(ctx, 400, "El campo 'idempotency_key' no puede exceder 36 caracteres.");
                return;
            }

            if (!TIPOS_COMPROBANTE_VALIDOS.contains(tipoComprobante)) {
                state.responderError(ctx, 400, "Tipo de comprobante inválido: " + tipoComprobante);
                return;
            }
            if (!MEDIOS_PAGO_VALIDOS.contains(medioPago)) {
                state.responderError(ctx, 400, "Medio de pago inválido: " + medioPago);
                return;
            }

            List<DetalleVenta> detallesVenta = new ArrayList<>();
            BigDecimal totalAutoritativo = BigDecimal.ZERO;
            Integer idPedidoPersistido = null;
            int siguienteCorrelativo;

            try (Connection conn = ConexionBD.getConnection()) {
                if (conn == null) {
                    state.responderError(ctx, 503, "Base de datos no disponible para procesar cobro.");
                    return;
                }
                if (idPedido > 0) {
                    Optional<Pedido> pedOpt = pedidoDAO.obtenerPorId(conn, idPedido);
                    if (pedOpt.isEmpty()) {
                        state.responderError(ctx, 404, "Pedido no encontrado para cobrar: #" + idPedido);
                        return;
                    }
                    Pedido pedido = pedOpt.get();
                    String estPed = pedido.getEstado() != null ? pedido.getEstado().trim().toUpperCase(Locale.ROOT) : "";
                    if (ESTADOS_PEDIDO_TERMINALES.contains(estPed)) {
                        state.responderError(ctx, 409, "El pedido #" + idPedido + " ya se encuentra en estado " + estPed + ".");
                        return;
                    }
                    if (numeroMesa.isBlank() && pedido.getNumeroMesa() != null) {
                        numeroMesa = pedido.getNumeroMesa();
                    }
                    List<DetallePedido> detsPed = pedidoDAO.listarDetallesPorPedido(conn, idPedido);
                    if (detsPed.isEmpty()) {
                        state.responderError(ctx, 422, "El pedido #" + idPedido + " no tiene ítems registrados.");
                        return;
                    }
                    for (DetallePedido dp : detsPed) {
                        BigDecimal precioU = dp.getPrecioUnitario().setScale(2, RoundingMode.HALF_UP);
                        BigDecimal sub = precioU.multiply(BigDecimal.valueOf(dp.getCantidad())).setScale(2, RoundingMode.HALF_UP);
                        DetalleVenta dv = new DetalleVenta(0, 0, dp.getIdProducto(), dp.getNombreProducto(),
                                dp.getCantidad(), precioU, sub);
                        dv.setIdVariante(dp.getIdVariante());
                        dv.setNombreVariante(dp.getNombreVariante());
                        dv.setModificadoresTexto(dp.getModificadoresTexto());
                        dv.setIdCombo(dp.getIdCombo());
                        dv.setComboComponentesTexto(dp.getComboComponentesTexto());
                        detallesVenta.add(dv);
                        totalAutoritativo = totalAutoritativo.add(sub);
                    }
                    idPedidoPersistido = idPedido;
                } else {
                    JsonNode itemsNode = body.has("items") ? body.get("items") : body.get("detalles");
                    if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) {
                        state.responderError(ctx, 400, "Debe proporcionar un 'id_pedido' válido o una lista de 'items'.");
                        return;
                    }
                    Map<Integer, Producto> catalogoPorId = new HashMap<>();
                    for (Producto p : productoDAO.listarTodos(conn)) {
                        catalogoPorId.put(p.getIdProducto(), p);
                    }
                    for (JsonNode item : itemsNode) {
                        int idProd = extraerIdProducto(item);
                        int cantidad = item.hasNonNull("cantidad") ? item.get("cantidad").asInt(0) : 0;
                        if (idProd <= 0 || cantidad <= 0) {
                            state.responderError(ctx, 400, "Cada ítem a cobrar requiere 'id_producto' y 'cantidad > 0'.");
                            return;
                        }
                        Producto prod = catalogoPorId.get(idProd);
                        if (prod == null || !prod.isDisponible() || prod.getPrecioVenta() == null) {
                            state.responderError(ctx, 422, "Producto no válido o agotado: #" + idProd);
                            return;
                        }
                        BigDecimal precioU = prod.getPrecioVenta().setScale(2, RoundingMode.HALF_UP);
                        BigDecimal sub = precioU.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
                        detallesVenta.add(new DetalleVenta(0, 0, idProd, prod.getNombre(), cantidad, precioU, sub));
                        totalAutoritativo = totalAutoritativo.add(sub);
                    }
                }
                siguienteCorrelativo = obtenerSiguienteCorrelativoComprobante(conn);
            } catch (SQLException exSql) {
                LOGGER.log(Level.WARNING, "Error SQL preparando cobro en POST /api/caja/cobrar", exSql);
                state.responderError(ctx, 503, "Servicio de base de datos no disponible.");
                return;
            }

            BigDecimal bTotal = totalAutoritativo.setScale(2, RoundingMode.HALF_UP);
            BigDecimal bMontoRecibido = bTotal;
            if (body.hasNonNull("monto_recibido")) {
                double recibidoRaw = body.get("monto_recibido").asDouble(Double.NaN);
                if (Double.isNaN(recibidoRaw) || Double.isInfinite(recibidoRaw) || recibidoRaw < 0.0) {
                    state.responderError(ctx, 400, "El monto recibido es inválido.");
                    return;
                }
                bMontoRecibido = BigDecimal.valueOf(recibidoRaw).setScale(2, RoundingMode.HALF_UP);
                if ("EFECTIVO".equals(medioPago) && bMontoRecibido.compareTo(bTotal) < 0) {
                    state.responderError(ctx, 422, "El monto recibido (S/ " + bMontoRecibido
                            + ") es menor al total a cobrar (S/ " + bTotal + ").");
                    return;
                }
            }
            BigDecimal bVuelto = bMontoRecibido.subtract(bTotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            String serie = "FACTURA".equals(tipoComprobante) ? "F001"
                    : ("BOLETA".equals(tipoComprobante) ? "B001" : "T001");
            String correlativo = String.format("%06d", siguienteCorrelativo);
            String nroComprobante = serie + "-" + correlativo;

            Venta ventaPersistida;
            try {
                DesgloseFiscal desglose = DesgloseFiscal.desdeTotalConImpuestos(bTotal);
                Venta venta = new Venta(0, idPedidoPersistido, 0, idUsuario, null,
                        desglose.getSubtotalGravado(), desglose.getIgv(), desglose.getTotal(),
                        medioPago, bMontoRecibido, bVuelto);
                venta.setAplicarFidelizacion(false);
                venta.setIdempotencyKey(idempotencyKey);

                ComprobantePago comprobante = new ComprobantePago(0, 0, tipoComprobante, serie, correlativo, desglose.getTotal());
                ventaPersistida = ventaService.registrarVenta(venta, detallesVenta, comprobante);
            } catch (CajaCerradaException exClosed) {
                state.responderError(ctx, 409, exClosed.getMessage() != null ? exClosed.getMessage() : "Debe abrir un turno de caja antes de cobrar.");
                return;
            } catch (StockInsuficienteException | InsumoNoEncontradoException | NegocioException exNeg) {
                state.responderError(ctx, 422, exNeg.getMessage() != null ? exNeg.getMessage() : "No se pudo completar el cobro por validación de negocio.");
                return;
            } catch (SQLException exSql) {
                LOGGER.log(Level.WARNING, "Error SQL persistiendo venta en POST /api/caja/cobrar", exSql);
                state.responderError(ctx, 503, "No se pudo persistir la venta en base de datos.");
                return;
            }

            boolean mesaLiberada = false;
            if (!numeroMesa.isBlank()) {
                try (Connection conn = ConexionBD.getConnection()) {
                    if (conn != null) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE mesa SET estado = 'LIBRE', id_pedido_actual = NULL WHERE numero = ?")) {
                            ps.setString(1, numeroMesa);
                            mesaLiberada = ps.executeUpdate() > 0;
                        }
                    }
                } catch (SQLException exMesa) {
                    LOGGER.log(Level.FINE, "Aviso liberando mesa tras cobro #" + ventaPersistida.getIdVenta(), exMesa);
                }
            }

            ObjectNode resp = state.mapper.createObjectNode();
            resp.put("status", "OK");
            resp.put("id_venta", ventaPersistida.getIdVenta());
            resp.put("id_pedido", idPedidoPersistido != null ? idPedidoPersistido : 0);
            resp.put("id_usuario", idUsuario);
            resp.put("numero_mesa", numeroMesa);
            resp.put("tipo_comprobante", tipoComprobante);
            resp.put("numero_comprobante", nroComprobante);
            resp.put("medio_pago", medioPago);
            resp.put("subtotal", ventaPersistida.getSubtotal().doubleValue());
            resp.put("igv", ventaPersistida.getIgv().doubleValue());
            resp.put("total", ventaPersistida.getTotal().doubleValue());
            resp.put("monto_recibido", bMontoRecibido.doubleValue());
            resp.put("vuelto", bVuelto.doubleValue());
            resp.put("mesa_liberada", mesaLiberada || !numeroMesa.isBlank());
            resp.put("fecha_emision", LocalDateTime.now(ZONA_LIMA).toString());

            ctx.status(200).contentType("application/json; charset=UTF-8").result(resp.toString());

        } catch (JsonProcessingException e) {
            state.responderError(ctx, 400, "Solicitud JSON inválida al procesar cobro.");
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Error al procesar cobro en caja", e);
            state.responderError(ctx, 500, "Error interno al procesar cobro.");
        }
    }

    private int obtenerSiguienteCorrelativoComprobante(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COALESCE(MAX(id_comprobante), 0) + 1 FROM comprobante_pago");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Math.max(1, rs.getInt(1));
            }
        }
        return state.secuenciaComprobantes.incrementAndGet();
    }

    private Map<Integer, List<DetallePedido>> cargarDetallesEnLote(Connection conn, List<Pedido> pedidos) throws SQLException {
        Map<Integer, List<DetallePedido>> mapa = new HashMap<>();
        if (pedidos == null || pedidos.isEmpty()) {
            return mapa;
        }
        String sql = "SELECT dp.*, p.nombre AS nombre_prod, ped.numero_mesa "
                + "FROM detalle_pedido dp "
                + "JOIN producto p ON dp.id_producto = p.id_producto "
                + "JOIN pedido ped ON dp.id_pedido = ped.id_pedido "
                + "WHERE ped.estado IN ('ABIERTO', 'PENDIENTE', 'REGISTRADO', 'EN_PREPARACION', 'LISTO', 'ENTREGADO', 'ITEMS_ADICIONALES', 'POR_COBRAR') "
                + "ORDER BY dp.id_pedido ASC, dp.id_detalle_pedido ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                DetallePedido dp = new DetallePedido();
                dp.setIdDetallePedido(rs.getInt("id_detalle_pedido"));
                dp.setIdPedido(rs.getInt("id_pedido"));
                dp.setIdProducto(rs.getInt("id_producto"));
                dp.setNombreProducto(rs.getString("nombre_prod"));
                dp.setCantidad(rs.getInt("cantidad"));
                dp.setPrecioUnitario(rs.getBigDecimal("precio_unitario"));
                dp.setSubtotal(rs.getBigDecimal("subtotal"));
                dp.setNotasPreparacion(rs.getString("notas_preparacion"));
                dp.setEstadoCocina(rs.getString("estado_cocina"));
                mapa.computeIfAbsent(dp.getIdPedido(), k -> new ArrayList<>()).add(dp);
            }
        }
        return mapa;
    }

    private ObjectNode construirRespuestaPedido(Pedido pedido, List<DetallePedido> detalles, boolean idempotente) {
        String numComanda = "COM-" + String.format("%04d", pedido.getIdPedido());
        ObjectNode resp = state.mapper.createObjectNode();
        resp.put("status", "OK");
        resp.put("id_pedido", pedido.getIdPedido());
        resp.put("numero_comanda", numComanda);
        resp.put("uuid_local", pedido.getIdempotencyKey() != null ? pedido.getIdempotencyKey() : "");
        resp.put("numero_mesa", pedido.getNumeroMesa());
        resp.put("estado", "PENDIENTE");
        resp.put("estado_pedido", pedido.getEstado() != null ? pedido.getEstado() : "REGISTRADO");
        resp.put("estado_comanda", "PENDIENTE");
        resp.put("notificado_kds", true);
        resp.put("total", pedido.getTotal() != null ? pedido.getTotal().doubleValue() : 0.0);
        resp.put("id_usuario", pedido.getIdUsuario());
        resp.put("id_usuario_creador", pedido.getIdUsuarioCreador() != null ? pedido.getIdUsuarioCreador() : pedido.getIdUsuario());
        resp.put("idempotente", idempotente);

        ArrayNode itemsResp = resp.putArray("items");
        for (DetallePedido d : detalles) {
            ObjectNode detNode = itemsResp.addObject();
            detNode.put("id_detalle_pedido", d.getIdDetallePedido());
            detNode.put("id_producto", d.getIdProducto());
            detNode.put("nombre", d.getNombreProducto());
            detNode.put("cantidad", d.getCantidad());
            detNode.put("precio_unitario", d.getPrecioUnitario() != null ? d.getPrecioUnitario().doubleValue() : 0.0);
            detNode.put("subtotal", d.getSubtotal() != null ? d.getSubtotal().doubleValue() : 0.0);
            detNode.put("notas", d.getNotasPreparacion() != null ? d.getNotasPreparacion() : "");
            detNode.put("estado_cocina", d.getEstadoCocina() != null ? d.getEstadoCocina() : "PENDIENTE");
        }
        return resp;
    }

    private static int extraerIdProducto(JsonNode item) {
        if (item.hasNonNull("id_producto")) {
            return item.get("id_producto").asInt(0);
        }
        if (item.hasNonNull("idProducto")) {
            return item.get("idProducto").asInt(0);
        }
        if (item.hasNonNull("id")) {
            return item.get("id").asInt(0);
        }
        return 0;
    }

    private ObjectNode construirJsonProducto(Producto p) {
        ObjectNode item = state.mapper.createObjectNode();
        double precio = p.getPrecioVenta() != null
                ? p.getPrecioVenta().setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        String img = normalizarImagenUrl(p);
        String cat = (p.getNombreCategoria() != null && !p.getNombreCategoria().isBlank())
                ? p.getNombreCategoria()
                : resolverNombreCategoria(p.getIdCategoria());

        item.put("id", p.getIdProducto());
        item.put("idProducto", p.getIdProducto());
        item.put("codigo", p.getCodigo() != null ? p.getCodigo() : "PROD-" + p.getIdProducto());
        item.put("nombre", p.getNombre());
        item.put("descripcion", p.getDescripcion() != null ? p.getDescripcion() : "Especialidad de la casa");
        item.put("precio", precio);
        item.put("imagen_url", img);
        item.put("categoria", cat);
        item.put("disponible", p.isDisponible());
        item.put("es_menu_del_dia", p.isEsMenuDelDia());
        return item;
    }

    private ObjectNode construirJsonProductoPublico(Producto p) {
        ObjectNode item = state.mapper.createObjectNode();
        double precio = p.getPrecioVenta() != null
                ? p.getPrecioVenta().setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        String img = normalizarImagenUrl(p);
        if (img.startsWith("/api/productos/")) {
            img = "/api/public/productos/" + img.substring("/api/productos/".length());
        }
        String cat = (p.getNombreCategoria() != null && !p.getNombreCategoria().isBlank())
                ? p.getNombreCategoria()
                : resolverNombreCategoria(p.getIdCategoria());

        item.put("id", p.getIdProducto());
        item.put("nombre", p.getNombre());
        item.put("descripcion", p.getDescripcion() != null ? p.getDescripcion() : "Especialidad de la casa");
        item.put("precio", precio);
        item.put("imagen_url", img);
        item.put("categoria", cat);
        item.put("disponible", p.isDisponible());
        item.put("es_menu_del_dia", p.isEsMenuDelDia());
        return item;
    }

    private void servirArchivoImagen(Context ctx, Path file) {
        try {
            if (file == null || !Files.isRegularFile(file)) {
                ctx.status(404);
                return;
            }
            Path fileNamePath = file.getFileName();
            if (fileNamePath == null) {
                ctx.status(404);
                return;
            }
            String name = fileNamePath.toString().toLowerCase(Locale.ROOT);
            String mime;
            if (name.endsWith(".png")) {
                mime = "image/png";
            } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                mime = "image/jpeg";
            } else if (name.endsWith(".webp")) {
                mime = "image/webp";
            } else {
                ctx.status(403);
                return;
            }
            ctx.header("Cache-Control", "no-cache");
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.status(200).contentType(mime).result(Files.readAllBytes(file));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error leyendo archivo de imagen", e);
            ctx.status(404);
        }
    }

    private void emitirEventoDirectoKds(Pedido pedido, List<DetallePedido> detalles) {
        try {
            JsonObject msg = new JsonObject();
            msg.addProperty("accion", "NUEVA_COMANDA");
            msg.addProperty("origen", "PWA_TABLET");
            msg.addProperty("idPedido", pedido.getIdPedido());
            msg.addProperty("numeroComanda", "COM-" + String.format("%04d", pedido.getIdPedido()));
            msg.addProperty("mesa", pedido.getNumeroMesa());

            JsonArray items = new JsonArray();
            for (DetallePedido d : detalles) {
                JsonObject item = new JsonObject();
                item.addProperty("producto", d.getNombreProducto());
                item.addProperty("cantidad", d.getCantidad());
                item.addProperty("notas", d.getNotasPreparacion() != null ? d.getNotasPreparacion() : "");
                items.add(item);
            }
            msg.add("items", items);
            ServidorSockets.getInstance().emitirMensaje(msg);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Sin clientes KDS conectados al emitir desde PedidoController", e);
        }
    }

    private List<Producto> obtenerCatalogoRespaldo() {
        if (state.catalogoRespaldoCache.isEmpty()) {
            LocalDate hoy = LocalDate.now(ZONA_LIMA);
            state.catalogoRespaldoCache.add(new Producto(1, 1, "HAM-01", "Hamburguesa Royal Doble",
                    "Doble carne angus 180g, huevo, queso cheddar y papas", new BigDecimal("24.50"), new BigDecimal("9.50"), true, null, true, hoy));
            state.catalogoRespaldoCache.add(new Producto(2, 1, "HAM-02", "Cheese Burger Clásica",
                    "Carne 180g, queso cheddar fundido, lechuga y tomate", new BigDecimal("18.00"), new BigDecimal("6.80"), true, null, false, null));
            state.catalogoRespaldoCache.add(new Producto(3, 2, "ALI-01", "Alitas BBQ Picantes x6",
                    "6 alitas bañadas en salsa artesanal BBQ ligeramente picante", new BigDecimal("22.00"), new BigDecimal("8.50"), true, null, false, null));
            state.catalogoRespaldoCache.add(new Producto(4, 2, "CRIS-01", "Combo Pollo Crispy 3 Pzs",
                    "3 piezas broaster crocantes con papas amarillas", new BigDecimal("26.00"), new BigDecimal("10.20"), true, null, true, hoy));
            state.catalogoRespaldoCache.add(new Producto(5, 3, "BEB-01", "Limonada Frozen 1L",
                    "Limonada natural frappeada con hierbabuena", new BigDecimal("14.00"), new BigDecimal("3.50"), true, null, true, hoy));
            state.catalogoRespaldoCache.add(new Producto(6, 3, "BEB-02", "Chicha Morada 1L",
                    "Receta tradicional con manzana y canela", new BigDecimal("12.00"), new BigDecimal("3.00"), true, null, false, null));
            state.catalogoRespaldoCache.add(new Producto(7, 4, "COM-01", "Papas Fritas Supremas",
                    "Porción gigante de papas con queso fundido y tocino", new BigDecimal("11.50"), new BigDecimal("4.00"), true, null, false, null));
        }
        return new ArrayList<>(state.catalogoRespaldoCache);
    }

    private String resolverNombreCategoria(int idCategoria) {
        return switch (idCategoria) {
            case 1 -> "Hamburguesas";
            case 2 -> "Alitas & Pollo";
            case 3 -> "Bebidas";
            case 4 -> "Guarniciones";
            default -> "Especialidades";
        };
    }

    private String normalizarImagenUrl(Producto p) {
        if (p == null || p.getImagenUrl() == null || p.getImagenUrl().isBlank()) {
            return "";
        }
        String raw = p.getImagenUrl().trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return raw;
        }
        String cleanSlashes = raw.replace('\\', '/');
        if (cleanSlashes.startsWith("/uploads/platillos/") && !esRutaImagenSospechosa(cleanSlashes)) {
            return cleanSlashes;
        }
        if (cleanSlashes.startsWith("uploads/platillos/") && !esRutaImagenSospechosa(cleanSlashes)) {
            return "/" + cleanSlashes;
        }
        if (resolverArchivoImagenConfinado(UPLOADS_PLATILLOS_DIR, cleanSlashes).isPresent()) {
            return "/api/productos/" + p.getIdProducto() + "/imagen";
        }
        return "";
    }
}
