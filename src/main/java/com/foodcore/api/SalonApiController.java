package com.foodcore.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodcore.model.Producto;
import com.foodcore.service.api.ApiOperacionException;
import com.foodcore.service.api.ApiOperacionesPort;
import io.javalin.http.Context;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador REST delgado para catálogo de productos, carta pública, imágenes confinadas y mesas de salón.
 */
public class SalonApiController {

    private static final Logger LOGGER = Logger.getLogger(SalonApiController.class.getName());

    private final ApiState state;
    private final ApiOperacionesPort operaciones;

    public SalonApiController(ApiState state, ApiOperacionesPort operaciones) {
        this.state = state;
        this.operaciones = operaciones;
    }

    public void handleListarProductos(Context ctx) {
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        ctx.header("Pragma", "no-cache");

        ArrayNode array = state.mapper.createArrayNode();
        for (Producto p : obtenerCatalogoProductos(false)) {
            array.add(ApiPedidoJsonMapper.construirJsonProducto(state.mapper, p));
        }
        ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
    }

    public void handleListarProductosDisponibles(Context ctx) {
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        ctx.header("Pragma", "no-cache");

        ArrayNode array = state.mapper.createArrayNode();
        for (Producto p : obtenerCatalogoProductos(true)) {
            array.add(ApiPedidoJsonMapper.construirJsonProducto(state.mapper, p));
        }
        ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
    }

    public void handleListarProductosPublicos(Context ctx) {
        ctx.header("Cache-Control", "public, max-age=30");
        ArrayNode array = state.mapper.createArrayNode();
        for (Producto p : obtenerCatalogoProductos(false)) {
            array.add(ApiPedidoJsonMapper.construirJsonProductoPublico(state.mapper, p));
        }
        ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
    }

    public void handleListarProductosDisponiblesPublicos(Context ctx) {
        ctx.header("Cache-Control", "public, max-age=30");
        ArrayNode array = state.mapper.createArrayNode();
        for (Producto p : obtenerCatalogoProductos(true)) {
            array.add(ApiPedidoJsonMapper.construirJsonProductoPublico(state.mapper, p));
        }
        ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
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

            try {
                operaciones.actualizarDisponibilidadProducto(idProducto, nuevoEstado);
            } catch (ApiOperacionException exOp) {
                state.responderError(ctx, exOp.getStatusCode(), exOp.getMessage());
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

    public void handleImagenProducto(Context ctx) {
        try {
            int idProd = Integer.parseInt(ctx.pathParam("id"));
            if (idProd <= 0) {
                ctx.status(400);
                return;
            }
            Optional<String> optRuta = operaciones.obtenerImagenUrlProducto(idProd);
            String rutaImg = optRuta.orElse(null);
            if (rutaImg == null || rutaImg.isBlank()) {
                ctx.status(404);
                return;
            }
            if (ApiPedidoJsonMapper.esRutaImagenSospechosa(rutaImg)) {
                LOGGER.warning(
                        "Intento LFI / extensión prohibida bloqueada en handleImagenProducto para producto #"
                        + idProd);
                ctx.status(403);
                return;
            }
            Optional<Path> confinada = ApiPedidoJsonMapper.resolverArchivoImagenConfinado(
                    ApiPedidoJsonMapper.UPLOADS_PLATILLOS_DIR, rutaImg);
            if (confinada.isEmpty()) {
                ctx.status(404);
                return;
            }
            ApiPedidoJsonMapper.servirArchivoImagen(ctx, confinada.get());
        } catch (NumberFormatException e) {
            ctx.status(400);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "No se pudo servir imagen de producto", e);
            ctx.status(404);
        }
    }

    public void handleServirUploadPlatillo(Context ctx) {
        String rawArchivo = ctx.pathParam("archivo");
        if (ApiPedidoJsonMapper.esRutaImagenSospechosa(rawArchivo)) {
            ctx.status(403);
            return;
        }
        Optional<Path> target = ApiPedidoJsonMapper.resolverArchivoImagenConfinado(
                ApiPedidoJsonMapper.UPLOADS_PLATILLOS_DIR, rawArchivo);
        if (target.isEmpty()) {
            ctx.status(404);
            return;
        }
        ApiPedidoJsonMapper.servirArchivoImagen(ctx, target.get());
    }

    public void handleListarMesas(Context ctx) {
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        ctx.header("Pragma", "no-cache");
        ArrayNode array = state.mapper.createArrayNode();

        ApiOperacionesPort.EstadoMesasSnapshot snapshot = operaciones.obtenerEstadoMesas();
        Map<String, String> estadosPorMesa = new LinkedHashMap<>(snapshot.estadosPorMesa());
        Map<String, BigDecimal> totalesPorMesa = snapshot.totalesPorMesa();

        if (estadosPorMesa.isEmpty() && ApiState.isDemoFallbackEnabled()) {
            for (int i = 1; i <= 12; i++) {
                estadosPorMesa.put(String.format("%02d", i), "LIBRE");
            }
        }

        int idx = 1;
        for (Map.Entry<String, String> entry : estadosPorMesa.entrySet()) {
            String numero = entry.getKey();
            String estado = entry.getValue() != null ? entry.getValue() : "LIBRE";
            BigDecimal totalActual = totalesPorMesa.getOrDefault(numero, BigDecimal.ZERO);
            ObjectNode mesaNode = state.mapper.createObjectNode();
            mesaNode.put("id", idx);
            mesaNode.put("numero", numero);
            mesaNode.put("capacidad", (idx % 3 == 0) ? 6 : ((idx % 2 == 0) ? 4 : 2));
            mesaNode.put("zona", idx <= 8 ? "Salón Principal" : "Terraza Lounge");
            mesaNode.put("estado", estado);
            mesaNode.put("total_actual", totalActual.doubleValue());
            array.add(mesaNode);
            idx++;
        }

        ctx.status(200).contentType("application/json; charset=UTF-8").result(array.toString());
    }

    private List<Producto> obtenerCatalogoProductos(boolean soloDisponibles) {
        List<Producto> productos = operaciones.listarCatalogoProductos(soloDisponibles);
        if ((productos == null || productos.isEmpty()) && ApiState.isDemoFallbackEnabled()) {
            productos = ApiPedidoJsonMapper.obtenerCatalogoRespaldo(state);
        }
        if (productos == null) {
            return List.of();
        }
        if (soloDisponibles) {
            return productos.stream().filter(Producto::isDisponible).toList();
        }
        return productos;
    }
}
