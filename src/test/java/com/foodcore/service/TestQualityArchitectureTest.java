package com.foodcore.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guardrail arquitectónico permanente sobre {@code src/test/java}:
 * prohíbe esperas ciegas ({@code Thread.sleep}, {@code TimeUnit.*.sleep}) y acoplamiento por orden
 * ({@code @Order}, {@code @TestMethodOrder}) en toda la suite de pruebas.
 */
class TestQualityArchitectureTest {

    private static final Path TEST_ROOT = Path.of("src", "test", "java");

    private static final List<PatronProhibido> PATRONES_PROHIBIDOS = List.of(
            new PatronProhibido("Thread.sleep(...)", Pattern.compile("\\bThread\\s*\\.\\s*sleep\\s*\\(")),
            new PatronProhibido("TimeUnit.*.sleep(...)",
                    Pattern.compile("\\bTimeUnit\\s*\\.\\s*[A-Z_]+\\s*\\.\\s*sleep\\s*\\(")),
            new PatronProhibido("@Order(...)", Pattern.compile("@(?:org\\.junit\\.jupiter\\.api\\.)?Order\\s*\\(")),
            new PatronProhibido("@TestMethodOrder(...)",
                    Pattern.compile("@(?:org\\.junit\\.jupiter\\.api\\.)?TestMethodOrder\\s*\\("))
    );

    @Test
    @DisplayName("Ninguna prueba en src/test/java usa Thread.sleep, TimeUnit.*.sleep, @Order ni @TestMethodOrder")
    void sinThreadSleepNiOrderEnTests() throws IOException {
        assertTrue(Files.isDirectory(TEST_ROOT), "Debe existir src/test/java");

        List<String> violaciones = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(TEST_ROOT)) {
            List<Path> archivosJava = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .toList();

            for (Path archivo : archivosJava) {
                String codigoOriginal = Files.readString(archivo, StandardCharsets.UTF_8);
                String codigoLimpio = limpiarComentariosYLiterales(codigoOriginal);
                buscarViolaciones(archivo, codigoLimpio, violaciones);
            }
        }

        assertTrue(
                violaciones.isEmpty(),
                () -> "Se encontraron patrones prohibidos en pruebas:\n" + String.join("\n", violaciones)
        );
    }

    @Test
    @DisplayName("El escáner ignora comentarios/cadenas literales y detecta usos reales")
    void escanerDiferenciaCodigoRealDeComentariosYTextos() {
        String muestra = """
                // Thread.sleep(100); @Order(1) @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
                /* TimeUnit.MILLISECONDS.sleep(50); */
                String doc = "Thread.sleep(500); @Order(2)";
                String bloque = \"\"\"
                        @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
                        Thread.sleep(10);
                        \"\"\";
                void realMethod() {
                    Thread.sleep(25);
                }
                """;
        String limpio = limpiarComentariosYLiterales(muestra);
        List<String> hallazgos = new ArrayList<>();
        buscarViolaciones(Path.of("MuestraTest.java"), limpio, hallazgos);

        assertEquals(1, hallazgos.size());
        assertTrue(hallazgos.get(0).contains("MuestraTest.java:9 -> Thread.sleep(...)"));
    }

    private static void buscarViolaciones(Path archivo, String codigoLimpio, List<String> violaciones) {
        String rutaNormalizada = archivo.toString().replace('\\', '/');
        for (PatronProhibido patron : PATRONES_PROHIBIDOS) {
            Matcher matcher = patron.regex().matcher(codigoLimpio);
            while (matcher.find()) {
                int linea = calcularNumeroLinea(codigoLimpio, matcher.start());
                violaciones.add(rutaNormalizada + ":" + linea + " -> " + patron.etiqueta());
            }
        }
    }

    private static int calcularNumeroLinea(String texto, int posicion) {
        int linea = 1;
        for (int i = 0; i < posicion && i < texto.length(); i++) {
            if (texto.charAt(i) == '\n') {
                linea++;
            }
        }
        return linea;
    }

    /**
     * Escáner iterativo lineal O(n) que neutraliza comentarios y literales preservando saltos de línea.
     */
    static String limpiarComentariosYLiterales(String source) {
        int len = source.length();
        StringBuilder sb = new StringBuilder(len);
        int i = 0;
        while (i < len) {
            char c = source.charAt(i);
            char next = i + 1 < len ? source.charAt(i + 1) : '\0';

            if (c == '/' && next == '/') {
                sb.append("  ");
                i += 2;
                while (i < len && source.charAt(i) != '\n') {
                    sb.append(' ');
                    i++;
                }
            } else if (c == '/' && next == '*') {
                sb.append("  ");
                i += 2;
                while (i < len) {
                    char cur = source.charAt(i);
                    if (cur == '*' && i + 1 < len && source.charAt(i + 1) == '/') {
                        sb.append("  ");
                        i += 2;
                        break;
                    }
                    sb.append(cur == '\n' ? '\n' : ' ');
                    i++;
                }
            } else if (c == '"' && next == '"' && i + 2 < len && source.charAt(i + 2) == '"') {
                sb.append("   ");
                i += 3;
                while (i < len) {
                    char cur = source.charAt(i);
                    if (cur == '\\' && i + 1 < len) {
                        char escaped = source.charAt(i + 1);
                        sb.append(' ').append(escaped == '\n' ? '\n' : ' ');
                        i += 2;
                        continue;
                    }
                    if (cur == '"' && i + 2 < len && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                        sb.append("   ");
                        i += 3;
                        break;
                    }
                    sb.append(cur == '\n' ? '\n' : ' ');
                    i++;
                }
            } else if (c == '"') {
                sb.append(' ');
                i++;
                while (i < len) {
                    char cur = source.charAt(i);
                    if (cur == '\\' && i + 1 < len) {
                        char escaped = source.charAt(i + 1);
                        sb.append(' ').append(escaped == '\n' ? '\n' : ' ');
                        i += 2;
                        continue;
                    }
                    sb.append(cur == '\n' ? '\n' : ' ');
                    i++;
                    if (cur == '"') {
                        break;
                    }
                }
            } else if (c == '\'') {
                sb.append(' ');
                i++;
                while (i < len) {
                    char cur = source.charAt(i);
                    if (cur == '\\' && i + 1 < len) {
                        sb.append("  ");
                        i += 2;
                        continue;
                    }
                    sb.append(cur == '\n' ? '\n' : ' ');
                    i++;
                    if (cur == '\'') {
                        break;
                    }
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private record PatronProhibido(String etiqueta, Pattern regex) {
    }
}
