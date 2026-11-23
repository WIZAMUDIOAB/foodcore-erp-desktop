package com.foodcore.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ServiceSolidArchitectureTest {
    private Path source(String relative) {
        return Path.of("src", "main", "java", "com", "foodcore", relative);
    }

    private String read(Path path) throws IOException {
        return Files.readString(path);
    }

    @Test
    void facadesRemainThinAndDoNotContainJdbcQueries() throws IOException {
        Path root = source("service");
        Path venta = root.resolve("VentaService.java");
        Path finanzas = root.resolve("FinanzasService.java");
        Path caja = root.resolve("CajaService.java");
        assertTrue(Files.readAllLines(venta).size() <= 350);
        assertTrue(Files.readAllLines(finanzas).size() <= 200);
        assertTrue(Files.readAllLines(caja).size() <= 220);
        assertFalse(read(venta).contains("PreparedStatement"));
        assertFalse(read(venta).contains("ResultSet"));
        assertFalse(read(finanzas).contains("PreparedStatement"));
        assertFalse(read(finanzas).contains("ResultSet"));
    }

    @Test
    void useCaseInterfacesAndDefaultsExist() {
        Path root = source("service");
        for (String relative : new String[] {
                "venta/VentaCalculoValidator.java", "venta/DefaultVentaCalculoValidator.java",
                "venta/VentaInventarioKardexHandler.java", "venta/DefaultVentaInventarioKardexHandler.java",
                "venta/RegistrarVentaUseCase.java", "venta/DefaultRegistrarVentaUseCase.java",
                "venta/AnularDevolverVentaUseCase.java", "venta/DefaultAnularDevolverVentaUseCase.java",
                "finanzas/CuentaCorrienteUseCase.java", "finanzas/DefaultCuentaCorrienteUseCase.java",
                "finanzas/CambioPrecioUseCase.java", "finanzas/DefaultCambioPrecioUseCase.java",
                "finanzas/FinanzasDocumentoPdfGenerator.java", "finanzas/DefaultFinanzasDocumentoPdfGenerator.java",
                "caja/CajaTurnoOperacionesUseCase.java", "caja/DefaultCajaTurnoOperacionesUseCase.java",
                "caja/ArqueoCierreZUseCase.java", "caja/DefaultArqueoCierreZUseCase.java"}) {
            assertTrue(Files.exists(root.resolve(relative)), "Falta contrato o implementación: " + relative);
        }
    }

    @Test
    void serviceSourcesAvoidThreadsAndConsoleOutput() throws IOException {
        for (String area : new String[] {"venta", "finanzas", "caja"}) {
            Path directory = source("service").resolve(area);
            try (var files = Files.walk(directory)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String text = read(file);
                    assertFalse(text.contains("new Thread"));
                    assertFalse(text.contains("System.out.print"));
                    assertFalse(text.contains("System.err.print"));
                }
            }
        }
    }

    @Test
    void socketSyncDoNotConstructJsonWithStringBuilder() throws IOException {
        Path root = Path.of("src", "main", "java", "com", "foodcore", "util", "ServidorSockets.java");
        Path sync = Path.of("src", "main", "java", "com", "foodcore", "offline", "SyncWorker.java");
        assertFalse(read(root).contains("new StringBuilder"));
        assertFalse(read(sync).contains("new StringBuilder"));
    }

    @Test
    void pomDeclaresJacocoGatesForSolidServicePackages() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("com/foodcore/service/venta"));
        assertTrue(pom.contains("com/foodcore/service/finanzas"));
        assertTrue(pom.contains("com/foodcore/service/caja"));
    }
}
