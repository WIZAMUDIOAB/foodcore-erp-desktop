package com.foodcore.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodcore.controller.VentaController;
import com.foodcore.service.impl.AuthServiceImpl;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    @Test
    void authServiceEsInterfazSinCicloNiEstadoEstaticoDuplicado() throws IOException {
        assertTrue(AuthService.class.isInterface(), "AuthService debe ser interfaz para romper ciclo con AuthServiceImpl");
        assertTrue(AuthService.class.isAssignableFrom(AuthServiceImpl.class));
        assertTrue(AuthServiceImpl.class.getSuperclass() == Object.class,
                "AuthServiceImpl debe implementar AuthService sin herencia circular");

        for (Field field : AuthService.class.getDeclaredFields()) {
            assertFalse("usuarioSesionActual".equals(field.getName()),
                    "AuthService no debe duplicar estado de sesión propio");
            int mods = field.getModifiers();
            assertTrue(Modifier.isPublic(mods) && Modifier.isStatic(mods) && Modifier.isFinal(mods),
                    "AuthService solo debe declarar constantes public static final: " + field.getName());
        }

        String authSource = read(source("service").resolve("AuthService.java"));
        assertFalse(authSource.contains("AuthServiceImpl"),
                "AuthService no debe referenciar ni instanciar AuthServiceImpl");
        assertFalse(authSource.contains("usuarioSesionActual"),
                "AuthService no debe declarar ni referenciar usuarioSesionActual");

        String spotbugsExcludes = read(Path.of("config", "spotbugs-exclude.xml"));
        assertFalse(spotbugsExcludes.contains("com.foodcore.service.AuthService"),
                "config/spotbugs-exclude.xml no debe suprimir hallazgos sobre AuthService");
    }

    @Test
    void ventaControllerSinCiclosNiHooksDeTest() throws IOException {
        Path ventaDir = source("controller").resolve("venta");
        try (var files = Files.walk(ventaDir)) {
            List<Path> fuentes = files.filter(path -> path.toString().endsWith(".java")).toList();
            assertFalse(fuentes.isEmpty());
            for (Path file : fuentes) {
                String text = read(file);
                assertFalse(text.contains("VentaController"),
                        "Archivo en controller.venta no debe referenciar VentaController: " + file.getFileName());
            }
        }

        for (Field field : VentaController.class.getDeclaredFields()) {
            String name = field.getName();
            assertFalse(name.endsWith("Test"), "VentaController no debe tener campo de prueba: " + name);
            assertFalse("confirmacionEscapeAutomatica".equals(name),
                    "VentaController no debe tener flag confirmacionEscapeAutomatica");
        }
        for (Method method : VentaController.class.getDeclaredMethods()) {
            String name = method.getName();
            assertFalse(name.endsWith("Test"), "VentaController no debe tener método de prueba: " + name);
            assertFalse("simularEstadoTransaccionEnCurso".equals(name),
                    "VentaController no debe tener hook simularEstadoTransaccionEnCurso");
            assertFalse("setConfirmacionEscapeAutomatica".equals(name),
                    "VentaController no debe tener hook setConfirmacionEscapeAutomatica");
        }

        Path ventaCtrlPath = source("controller").resolve("VentaController.java");
        List<String> lineas = Files.readAllLines(ventaCtrlPath);
        assertTrue(lineas.size() <= 800, "VentaController.java debe ser <= 800 LOC, actual=" + lineas.size());
        assertFalse(String.join("\n", lineas).contains("buscarYAsociarClientePorCriterioSincrono"),
                "VentaController no debe exponer búsqueda síncrona bloqueante de clientes");
    }
}
