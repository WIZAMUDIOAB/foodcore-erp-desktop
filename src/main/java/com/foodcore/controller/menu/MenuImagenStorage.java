package com.foodcore.controller.menu;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public interface MenuImagenStorage {
    Path resolverImagenSegura(Path baseDir, String imagenUrl);

    String resolverExtensionSegura(String nombreArchivo);

    String copiarImagen(Path baseDir, File archivoOrigen) throws IOException;
}
