package com.foodcore.controller.menu;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class LocalMenuImagenStorage implements MenuImagenStorage {
    private static final String PREFIJO_RELATIVO = "uploads/platillos/";

    public LocalMenuImagenStorage() {
    }

    @Override
    public Path resolverImagenSegura(Path baseDir, String imagenUrl) {
        if (baseDir == null || imagenUrl == null || imagenUrl.isBlank()) {
            return null;
        }
        String limpio = imagenUrl.trim().replace('\\', '/');
        if (limpio.startsWith("/")) {
            limpio = limpio.substring(1);
        }
        String nombreArchivo;
        if (limpio.startsWith(PREFIJO_RELATIVO)) {
            nombreArchivo = limpio.substring(PREFIJO_RELATIVO.length());
        } else if (!limpio.contains("/")) {
            nombreArchivo = limpio;
        } else {
            return null;
        }
        if (nombreArchivo.contains("..")
                || !nombreArchivo.matches("^[A-Za-z0-9._-]+\\.(?i)(png|jpg|jpeg|webp)$")) {
            return null;
        }
        try {
            Path baseNormalizada = baseDir.toAbsolutePath().normalize();
            if (!Files.isDirectory(baseNormalizada)) {
                return null;
            }
            Path baseReal = baseNormalizada.toRealPath();
            try (Stream<Path> entradas = Files.list(baseReal)) {
                Optional<Path> match = entradas
                        .filter(p -> p.getFileName() != null && p.getFileName().toString().equals(nombreArchivo))
                        .filter(Files::isRegularFile)
                        .findFirst();
                if (match.isEmpty()) {
                    return null;
                }
                Path candidatoReal = match.get().toRealPath();
                return candidatoReal.startsWith(baseReal) ? candidatoReal : null;
            }
        } catch (IOException ex) {
            return null;
        }
    }

    @Override
    public String resolverExtensionSegura(String nombreArchivo) {
        if (nombreArchivo == null) {
            return null;
        }
        String lower = nombreArchivo.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return ".png";
        }
        if (lower.endsWith(".jpg")) {
            return ".jpg";
        }
        if (lower.endsWith(".jpeg")) {
            return ".jpeg";
        }
        if (lower.endsWith(".webp")) {
            return ".webp";
        }
        return null;
    }

    @Override
    public String copiarImagen(Path baseDir, File archivoOrigen) throws IOException {
        if (baseDir == null) {
            throw new IOException("Ruta base de imágenes no válida.");
        }
        if (archivoOrigen == null || !archivoOrigen.exists() || !archivoOrigen.isFile()) {
            throw new IOException("El archivo de imagen seleccionado no existe.");
        }
        String extSegura = resolverExtensionSegura(archivoOrigen.getName());
        if (extSegura == null) {
            throw new IOException("Formato de imagen no permitido. Use PNG, JPG, JPEG o WEBP.");
        }
        Files.createDirectories(baseDir);
        Path baseReal = baseDir.toAbsolutePath().normalize().toRealPath();
        String nombreSeguro = "platillo_" + UUID.randomUUID() + extSegura;
        Path destino = baseReal.resolve(nombreSeguro).normalize();
        if (!destino.startsWith(baseReal)) {
            throw new IOException("Ruta destino fuera del directorio permitido de platillos.");
        }
        Files.copy(archivoOrigen.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);
        return PREFIJO_RELATIVO + nombreSeguro;
    }
}
