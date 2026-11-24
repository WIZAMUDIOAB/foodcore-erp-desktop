package com.foodcore.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodcore.controller.VentaController;
import com.foodcore.dao.CajaDAO;
import com.foodcore.dao.InsumoDAO;
import com.foodcore.dao.PedidoDAO;
import com.foodcore.dao.UsuarioDAO;
import com.foodcore.dao.impl.CajaDAOImpl;
import com.foodcore.dao.impl.InsumoDAOImpl;
import com.foodcore.dao.impl.PedidoDAOImpl;
import com.foodcore.dao.impl.UsuarioDAOImpl;
import com.foodcore.service.impl.AuthServiceImpl;
import com.foodcore.service.impl.CajaServiceImpl;
import com.foodcore.service.impl.PedidoServiceImpl;
import com.foodcore.service.impl.VentaServiceImpl;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    @Test
    void apiServerYControladoresRestSinJdbcDirectoYDelgados() throws IOException {
        Path apiRoot = source("api");
        for (String rel : new String[] {
                "ApiServer.java", "PedidoController.java", "SalonApiController.java",
                "CobroApiController.java", "KdsSyncApiController.java",
                "ApiRouteRegistrar.java", "PublicMenuServerHelper.java", "ApiTlsConfigurator.java"
        }) {
            Path file = apiRoot.resolve(rel);
            assertTrue(Files.exists(file), "Debe existir archivo modularizado en api: " + rel);
            String text = read(file);
            assertFalse(text.contains("PreparedStatement"), rel + " no debe usar PreparedStatement directo");
            assertFalse(text.contains("ResultSet"), rel + " no debe usar ResultSet directo");
            assertFalse(text.contains("ConexionBD.getConnection"), rel + " no debe invocar ConexionBD.getConnection");
        }

        for (String thinFile : new String[] {
                "ApiServer.java", "PedidoController.java",
                "SalonApiController.java", "CobroApiController.java", "KdsSyncApiController.java"
        }) {
            int loc = Files.readAllLines(apiRoot.resolve(thinFile)).size();
            assertTrue(loc <= 400, thinFile + " debe ser <= 400 LOC, actual=" + loc);
        }
    }

    @Test
    void sinCiclosDeClasesNiWrappersPasamanosNiCiclosDePaquetesP1() throws IOException {
        for (String duplicado : new String[] {
                "UsuarioDAOImpl.java", "CajaDAOImpl.java", "InsumoDAOImpl.java", "PedidoDAOImpl.java"
        }) {
            assertFalse(Files.exists(source("dao").resolve(duplicado)),
                    "No debe existir DAO duplicado pasamanos en com.foodcore.dao: " + duplicado);
        }

        assertTrue(AuthService.class.isInterface(), "AuthService debe ser interfaz");
        assertTrue(VentaService.class.isInterface(), "VentaService debe ser interfaz");
        assertTrue(CajaService.class.isInterface(), "CajaService debe ser interfaz");
        assertTrue(PedidoService.class.isInterface(), "PedidoService debe ser interfaz");

        Map<Class<?>, Class<?>> contratosCanonicamenteImplementados = Map.of(
                UsuarioDAOImpl.class, UsuarioDAO.class,
                CajaDAOImpl.class, CajaDAO.class,
                InsumoDAOImpl.class, InsumoDAO.class,
                PedidoDAOImpl.class, PedidoDAO.class,
                AuthServiceImpl.class, AuthService.class,
                CajaServiceImpl.class, CajaService.class,
                VentaServiceImpl.class, VentaService.class,
                PedidoServiceImpl.class, PedidoService.class
        );
        for (Map.Entry<Class<?>, Class<?>> entry : contratosCanonicamenteImplementados.entrySet()) {
            Class<?> impl = entry.getKey();
            Class<?> contrato = entry.getValue();
            assertTrue(contrato.isAssignableFrom(impl), impl.getSimpleName() + " debe implementar " + contrato.getSimpleName());
            assertTrue(impl.getSuperclass() == Object.class,
                    impl.getSimpleName() + " debe extender Object directamente sin herencia pasamanos");
        }

        String spotbugsExcludes = read(Path.of("config", "spotbugs-exclude.xml"));
        assertFalse(spotbugsExcludes.contains("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS"),
                "config/spotbugs-exclude.xml no debe suprimir NM_SAME_SIMPLE_NAME_AS_SUPERCLASS");
        assertFalse(spotbugsExcludes.contains("com.foodcore.service.CajaService"),
                "config/spotbugs-exclude.xml no debe referenciar com.foodcore.service.CajaService");

        Map<String, Set<String>> classDeps = new HashMap<>();
        Map<String, Set<String>> pkgDeps = new HashMap<>();
        cargarDependenciasEstaticas(classDeps, pkgDeps);

        List<String> classCycles = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : classDeps.entrySet()) {
            String a = entry.getKey();
            for (String b : entry.getValue()) {
                if (a.compareTo(b) < 0 && classDeps.getOrDefault(b, Set.of()).contains(a)) {
                    classCycles.add(a + " <-> " + b);
                }
            }
        }
        assertTrue(classCycles.isEmpty(), "No deben existir ciclos binarios entre clases: " + classCycles);

        List<String[]> paresSinCiclo = List.of(
                new String[] {"com.foodcore.dao", "com.foodcore.dao.impl"},
                new String[] {"com.foodcore.dao", "com.foodcore.service"},
                new String[] {"com.foodcore.service", "com.foodcore.service.caja"},
                new String[] {"com.foodcore.service", "com.foodcore.service.finanzas"},
                new String[] {"com.foodcore.service", "com.foodcore.service.impl"},
                new String[] {"com.foodcore.service", "com.foodcore.service.venta"},
                new String[] {"com.foodcore.controller", "com.foodcore.controller.reportes"}
        );
        for (String[] par : paresSinCiclo) {
            boolean aToB = pkgDeps.getOrDefault(par[0], Set.of()).contains(par[1]);
            boolean bToA = pkgDeps.getOrDefault(par[1], Set.of()).contains(par[0]);
            assertFalse(aToB && bToA, "Ciclo bidireccional P1 entre paquetes " + par[0] + " <-> " + par[1]);
        }
    }

    private void cargarDependenciasEstaticas(Map<String, Set<String>> classDeps,
                                             Map<String, Set<String>> pkgDeps) throws IOException {
        Pattern pkgPat = Pattern.compile("(?m)^package\\s+([^;]+);");
        Pattern impPat = Pattern.compile("(?m)^import\\s+(?:static\\s+)?(com\\.foodcore\\.[A-Za-z0-9_.]+);");
        Pattern fqnPat = Pattern.compile("\\b(com\\.foodcore\\.[a-z0-9_.]+\\.[A-Z][A-Za-z0-9_]*)");

        Path mainRoot = Path.of("src", "main", "java", "com", "foodcore");
        List<Path> archivos;
        try (var stream = Files.walk(mainRoot)) {
            archivos = stream.filter(p -> p.toString().endsWith(".java")).toList();
        }
        for (Path archivo : archivos) {
            String raw = read(archivo);
            Matcher pm = pkgPat.matcher(raw);
            if (!pm.find()) {
                continue;
            }
            String pkg = pm.group(1).trim();
            String simple = archivo.getFileName().toString().replace(".java", "");
            String fqcn = pkg + "." + simple;
            String cleaned = limpiarComentariosYLiterales(raw);
            Set<String> depsClase = classDeps.computeIfAbsent(fqcn, k -> new HashSet<>());
            Set<String> depsPkg = pkgDeps.computeIfAbsent(pkg, k -> new HashSet<>());

            registrarReferencias(fqcn, pkg, impPat.matcher(cleaned), depsClase, depsPkg);
            registrarReferencias(fqcn, pkg, fqnPat.matcher(cleaned), depsClase, depsPkg);
        }
    }

    private static void registrarReferencias(String fqcnOrigen, String pkgOrigen, Matcher matcher,
                                             Set<String> depsClase, Set<String> depsPkg) {
        while (matcher.find()) {
            String target = extraerClasePrincipal(matcher.group(1));
            if (target == null || target.equals(fqcnOrigen)) {
                continue;
            }
            depsClase.add(target);
            int lastDot = target.lastIndexOf('.');
            if (lastDot > 0) {
                String targetPkg = target.substring(0, lastDot);
                if (!targetPkg.equals(pkgOrigen)) {
                    depsPkg.add(targetPkg);
                }
            }
        }
    }

    private static String extraerClasePrincipal(String ref) {
        String[] parts = ref.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) {
                sb.append('.');
            }
            sb.append(part);
            if (!part.isEmpty() && Character.isUpperCase(part.charAt(0))) {
                return sb.toString();
            }
        }
        return null;
    }

    private static String limpiarComentariosYLiterales(String src) {
        String sinBloques = src.replaceAll("(?s)/\\*.*?\\*/", " ");
        String sinLineas = sinBloques.replaceAll("//[^\\r\\n]*", " ");
        return sinLineas.replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
    }
}
