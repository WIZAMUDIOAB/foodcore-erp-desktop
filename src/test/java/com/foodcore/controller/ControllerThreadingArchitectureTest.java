package com.foodcore.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guardas arquitectónicas de seguridad y concurrencia para Sub-Fase A (v5.1.0):
 * - ÍTEM 1: Sin bypass en memoria (pinsMemoria / AuthTestBypassConfig).
 * - ÍTEM 2: Sin contraseña de keystore hardcodeada.
 * - ÍTEM 3: Todos los hilos asíncronos de controladores unificados en UiAsyncExecutor (0 "new Thread").
 */
class ControllerThreadingArchitectureTest {

    @Test
    @DisplayName("ÍTEM 3: Ningún controlador JavaFX instancia 'new Thread' directamente")
    void ningunControladorUsaNewThreadManual() throws IOException {
        Path controllersDir = Path.of("src", "main", "java", "com", "foodcore", "controller");
        List<String> violaciones = new ArrayList<>();

        try (Stream<Path> files = Files.walk(controllersDir)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        if (lines.get(i).contains("new Thread")) {
                            violaciones.add(path.getFileName() + ":" + (i + 1) + " -> " + lines.get(i).trim());
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertTrue(violaciones.isEmpty(),
                "Los controladores deben usar UiAsyncExecutor y no 'new Thread': " + violaciones);
    }

    @Test
    @DisplayName("ÍTEM 1 & 2: Cero referencias a bypass en memoria o clave keystore en texto plano")
    void ceroReferenciasABypassOKeystoreHardcodeado() throws IOException {
        Path srcDir = Path.of("src");
        List<String> violaciones = new ArrayList<>();

        try (Stream<Path> files = Files.walk(srcDir)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                String pStr = path.toString();
                if (pStr.endsWith(".png") || pStr.endsWith(".jpg") || pStr.endsWith(".ttf")
                        || pStr.endsWith(".woff2") || pStr.endsWith(".cer") || pStr.endsWith(".jks")) {
                    return;
                }
                try {
                    String content = Files.readString(path, StandardCharsets.UTF_8);
                    if (path.getFileName().toString().equals("ControllerThreadingArchitectureTest.java")) {
                        return;
                    }
                    if (content.contains("pins" + "Memoria")
                            || content.contains("AuthTest" + "BypassConfig")
                            || content.contains("esCredencial" + "OperativaValida")
                            || content.contains("foodcore" + "123")) {
                        violaciones.add(path.toString());
                    }
                } catch (IOException ignored) {
                    // Ignorar binarios no UTF-8
                }
            });
        }

        assertTrue(violaciones.isEmpty(), "Archivos con referencias prohibidas: " + violaciones);
    }
}
