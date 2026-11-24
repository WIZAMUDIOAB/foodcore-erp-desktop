package com.foodcore.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.model.Producto;
import io.javalin.http.Context;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilidades puras de mapeo JSON, respaldo demo controlado y confinamiento de imágenes para controladores REST.
 */
final class ApiPedidoJsonMapper {

    private static final Logger LOGGER = Logger.getLogger(ApiPedidoJsonMapper.class.getName());
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    static final Path UPLOADS_PLATILLOS_DIR = Path.of("uploads", "platillos");
    private static final Set<String> EXTENSIONES_IMAGEN_PERMITIDAS = Set.of(".png", ".jpg", ".jpeg", ".webp");

    private ApiPedidoJsonMapper() {
    }

    static ObjectNode construirRespuestaPedido(ObjectMapper mapper, Pedido pedido,
            List<DetallePedido> detalles, boolean idempotente) {
        String numComanda = "COM-" + String.format("%04d", pedido.getIdPedido());
        ObjectNode resp = mapper.createObjectNode();
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
        resp.put("id_usuario_creador",
                pedido.getIdUsuarioCreador() != null ? pedido.getIdUsuarioCreador() : pedido.getIdUsuario());
        resp.put("idempotente", idempotente);

        ArrayNode itemsResp = resp.putArray("items");
        for (DetallePedido d : detalles) {
            ObjectNode detNode = itemsResp.addObject();
            detNode.put("id_detalle_pedido", d.getIdDetallePedido());
            detNode.put("id_producto", d.getIdProducto());
            detNode.put("nombre", d.getNombreProducto());
            detNode.put("cantidad", d.getCantidad());
            detNode.put("precio_unitario",
                    d.getPrecioUnitario() != null ? d.getPrecioUnitario().doubleValue() : 0.0);
            detNode.put("subtotal", d.getSubtotal() != null ? d.getSubtotal().doubleValue() : 0.0);
            detNode.put("notas", d.getNotasPreparacion() != null ? d.getNotasPreparacion() : "");
            detNode.put("estado_cocina", d.getEstadoCocina() != null ? d.getEstadoCocina() : "PENDIENTE");
        }
        return resp;
    }

    static int extraerIdProducto(JsonNode item) {
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

    static ObjectNode construirJsonProducto(ObjectMapper mapper, Producto p) {
        ObjectNode item = mapper.createObjectNode();
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

    static ObjectNode construirJsonProductoPublico(ObjectMapper mapper, Producto p) {
        ObjectNode item = mapper.createObjectNode();
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
            LOGGER.log(Level.FINE, "Ruta de imagen descartada por confinamiento", ex);
            return Optional.empty();
        }
    }

    static void servirArchivoImagen(Context ctx, Path file) {
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

    static List<Producto> obtenerCatalogoRespaldo(ApiState state) {
        if (state.catalogoRespaldoCache.isEmpty()) {
            LocalDate hoy = LocalDate.now(ZONA_LIMA);
            state.catalogoRespaldoCache.add(new Producto(1, 1, "HAM-01", "Hamburguesa Royal Doble",
                    "Doble carne angus 180g, huevo, queso cheddar y papas",
                    new BigDecimal("24.50"), new BigDecimal("9.50"), true, null, true, hoy));
            state.catalogoRespaldoCache.add(new Producto(2, 1, "HAM-02", "Cheese Burger Clásica",
                    "Carne 180g, queso cheddar fundido, lechuga y tomate",
                    new BigDecimal("18.00"), new BigDecimal("6.80"), true, null, false, null));
            state.catalogoRespaldoCache.add(new Producto(3, 2, "ALI-01", "Alitas BBQ Picantes x6",
                    "6 alitas bañadas en salsa artesanal BBQ ligeramente picante",
                    new BigDecimal("22.00"), new BigDecimal("8.50"), true, null, false, null));
            state.catalogoRespaldoCache.add(new Producto(4, 2, "CRIS-01", "Combo Pollo Crispy 3 Pzs",
                    "3 piezas broaster crocantes con papas amarillas",
                    new BigDecimal("26.00"), new BigDecimal("10.20"), true, null, true, hoy));
            state.catalogoRespaldoCache.add(new Producto(5, 3, "BEB-01", "Limonada Frozen 1L",
                    "Limonada natural frappeada con hierbabuena",
                    new BigDecimal("14.00"), new BigDecimal("3.50"), true, null, true, hoy));
            state.catalogoRespaldoCache.add(new Producto(6, 3, "BEB-02", "Chicha Morada 1L",
                    "Receta tradicional con manzana y canela",
                    new BigDecimal("12.00"), new BigDecimal("3.00"), true, null, false, null));
            state.catalogoRespaldoCache.add(new Producto(7, 4, "COM-01", "Papas Fritas Supremas",
                    "Porción gigante de papas con queso fundido y tocino",
                    new BigDecimal("11.50"), new BigDecimal("4.00"), true, null, false, null));
        }
        return new ArrayList<>(state.catalogoRespaldoCache);
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

    private static String resolverNombreCategoria(int idCategoria) {
        return switch (idCategoria) {
            case 1 -> "Hamburguesas";
            case 2 -> "Alitas & Pollo";
            case 3 -> "Bebidas";
            case 4 -> "Guarniciones";
            default -> "Especialidades";
        };
    }

    private static String normalizarImagenUrl(Producto p) {
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
