package com.foodcore.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    @DisplayName("Ola 1: Ningún controlador JavaFX usa JDBC o ConexionBD directamente")
    void ningunControladorUsaJdbcDirecto() throws IOException {
        Path controllersDir = Path.of("src", "main", "java", "com", "foodcore", "controller");
        List<String> prohibidos = List.of(
                "ConexionBD",
                "java.sql.Connection",
                "PreparedStatement",
                "ResultSet",
                "DriverManager");
        List<String> violaciones = new ArrayList<>();

        try (Stream<Path> files = Files.walk(controllersDir)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        for (String prohibido : prohibidos) {
                            if (lines.get(i).contains(prohibido)) {
                                violaciones.add(path.getFileName() + ":" + (i + 1)
                                        + " -> " + lines.get(i).trim());
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertTrue(violaciones.isEmpty(),
                "Los controladores deben delegar el acceso JDBC en servicios: " + violaciones);
    }

    @Test
    @DisplayName("Ola 1: Cero referencias a Gson en src y pom.xml")
    void ceroReferenciasAGsonEnCodigoFuente() throws IOException {
        String firmaGson = "com.google." + "gson";
        List<String> violaciones = new ArrayList<>();

        try (Stream<Path> files = Files.walk(Path.of("src"))) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    String content = Files.readString(path, StandardCharsets.UTF_8);
                    if (content.contains(firmaGson)) {
                        violaciones.add(path.toString());
                    }
                } catch (IOException ignored) {
                    // Ignorar archivos no legibles como UTF-8
                }
            });
        }

        Path pom = Path.of("pom.xml");
        if (Files.exists(pom) && Files.readString(pom, StandardCharsets.UTF_8).contains(firmaGson)) {
            violaciones.add(pom.toString());
        }

        assertTrue(violaciones.isEmpty(), "Referencias a Gson prohibidas: " + violaciones);
    }

    @Test
    @DisplayName("PR-12: 100% de los controladores con I/O usan UiAsyncExecutor sin exenciones para Lote 1 ni Lote 2")
    void todosLosControladoresConServiciosUsanUiAsyncExecutor() throws IOException {
        Path controllersDir = Path.of("src", "main", "java", "com", "foodcore", "controller");
        Set<String> sinIoPermitidos = Set.of(
                "MainDashboardController.java",
                "SplashController.java",
                "PersonalizacionProductoController.java");

        List<String> sinAsync = new ArrayList<>();
        List<String> allowlistViolations = new ArrayList<>();
        try (Stream<Path> files = Files.list(controllersDir)) {
            for (Path path : files.filter(p -> !Files.isDirectory(p) && p.toString().endsWith(".java")).toList()) {
                String nombre = path.getFileName().toString();
                String content = Files.readString(path, StandardCharsets.UTF_8);
                if (sinIoPermitidos.contains(nombre)) {
                    if (content.contains("java.sql.")
                            || content.contains("SQLException")
                            || content.contains("FileOutputStream")
                            || content.contains("Files.")) {
                        allowlistViolations.add(nombre);
                    }
                    continue;
                }
                if ("VentaController.java".equals(nombre)) {
                    Path ventaPkg = controllersDir.resolve("venta");
                    for (String helper : List.of(
                            "VentaPosOrdenOperacionesHelper.java",
                            "VentaPosMesasCatalogoHelper.java",
                            "VentaPosFidelizacionHelper.java")) {
                        String helperSrc = Files.readString(ventaPkg.resolve(helper), StandardCharsets.UTF_8);
                        assertTrue(helperSrc.contains("UiAsyncExecutor"),
                                "Helper POS delegado debe usar UiAsyncExecutor: " + helper);
                    }
                    continue;
                }
                if (!content.contains("UiAsyncExecutor")) {
                    sinAsync.add(nombre);
                }
                assertFalse(content.contains("com.foodcore.dao.impl."),
                        "Controlador no debe referenciar dao.impl: " + nombre);
                assertFalse(content.contains("com.foodcore.service.impl."),
                        "Controlador no debe referenciar service.impl: " + nombre);
            }
        }

        assertTrue(allowlistViolations.isEmpty(),
                "Controladores declarados sin I/O no deben ejecutar I/O bloqueante: " + allowlistViolations);
        assertTrue(sinAsync.isEmpty(),
                "Todos los controladores con I/O/servicios deben usar UiAsyncExecutor: " + sinAsync);
    }
}
