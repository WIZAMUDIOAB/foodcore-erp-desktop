package com.foodcore.controller.config;

import com.foodcore.service.AlertaService.ConfiguracionAlertaEmail;
import com.foodcore.service.ConfiguracionSistemaService;
import com.foodcore.service.ConfiguracionSistemaService.DatosRestauranteConfig;
import com.foodcore.service.ConfiguracionSistemaService.RuteoImpresorasData;
import com.foodcore.service.DemoDataService;
import com.foodcore.service.DemoDataService.ResumenDemo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigCoordinatorsUnitTest {

    @TempDir
    Path tempDir;

    @Test
    void backupBuscaYValidaArchivosPermitidos() throws IOException {
        DefaultConfigBackupCoordinator coordinator = new DefaultConfigBackupCoordinator();
        assertTrue(coordinator.buscarUltimoBackupDisponible(tempDir).isEmpty());
        assertTrue(coordinator.buscarUltimoBackupDisponible(tempDir.resolve("inexistente")).isEmpty());
        assertTrue(coordinator.buscarUltimoBackupDisponible(null).isEmpty());

        Path antiguo = Files.createFile(tempDir.resolve("antiguo.sql"));
        Path reciente = Files.createFile(tempDir.resolve("reciente.sql.gz"));
        Files.setLastModifiedTime(antiguo, java.nio.file.attribute.FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(reciente, java.nio.file.attribute.FileTime.fromMillis(2_000));
        Files.createFile(tempDir.resolve("ignorado.txt"));
        assertEquals(Optional.of(reciente), coordinator.buscarUltimoBackupDisponible(tempDir));

        assertFalse(coordinator.validarArchivoBackup("").valido());
        assertFalse(coordinator.validarArchivoBackup(tempDir.resolve("faltante.sql").toString()).valido());
        Path texto = Files.createFile(tempDir.resolve("backup.txt"));
        assertFalse(coordinator.validarArchivoBackup(texto.toString()).valido());
        Path sql = Files.createFile(tempDir.resolve("valido.sql"));
        Path gzip = Files.createFile(tempDir.resolve("valido.sql.gz"));
        assertTrue(coordinator.validarArchivoBackup(sql.toString()).valido());
        assertTrue(coordinator.validarArchivoBackup(gzip.toString()).valido());
    }

    @Test
    void smtpValidaCamposYConstruyeTls() {
        DefaultConfigAlertasSmtpCoordinator coordinator = new DefaultConfigAlertasSmtpCoordinator();
        assertFalse(coordinator.validarYConstruirConfiguracion("correo", "smtp.test", "587", "u", "p", true, true).valido());
        assertFalse(coordinator.validarYConstruirConfiguracion("a@b.com", "smtp.test", "abc", "u", "p", true, true).valido());
        assertFalse(coordinator.validarYConstruirConfiguracion("a@b.com", "smtp.test", "65536", "u", "p", true, true).valido());
        ConfigAlertasSmtpCoordinator.ValidacionSmtp valido = coordinator.validarYConstruirConfiguracion(
                "destino@example.com", "smtp.example.com", "465", "usuario", "secreto", true, true);
        assertTrue(valido.valido());
        assertNotNull(valido.config());
        assertEquals(465, valido.config().smtpPort());
        assertTrue(valido.config().tlsEnabled());
        assertEquals("destino@example.com", valido.config().destinatario());
        assertFalse(coordinator.validarYConstruirConfiguracion("a@b.com", "", "0", "", "", true, false).valido());
    }

    @Test
    void impresorasIncluyenAliasYDeleganEnStub() throws SQLException {
        StubConfiguracionService service = new StubConfiguracionService();
        DefaultConfigImpresorasCoordinator coordinator = new DefaultConfigImpresorasCoordinator(service);
        Set<String> disponibles = coordinator.obtenerImpresorasDisponibles();
        assertTrue(disponibles.containsAll(Set.of("Cocina", "Barra", "Parrilla", "Caja")));
        assertEquals(service.ruteo, coordinator.cargarRuteoImpresoras());
        coordinator.asignarImpresoraCategoria(7, "Caja");
        assertEquals(7, service.idCategoria);
        assertEquals("Caja", service.nombreImpresora);
        coordinator.eliminarImpresoraCategoria(7);
        assertEquals(7, service.eliminada);
        assertEquals(service.config, coordinator.cargarConfiguracionRestaurante().orElseThrow());
        coordinator.guardarConfiguracionRestaurante(service.config);
        assertEquals(service.config, service.guardada);
    }

    @Test
    void demoFormateaYDelegaEnStub() {
        StubDemoService service = new StubDemoService();
        DefaultConfigDemoDataCoordinator coordinator = new DefaultConfigDemoDataCoordinator(service);
        assertEquals(new ConfigDemoDataCoordinator.EstadoBadgeUi("Sin datos de demo cargados", "badge-pill-neutral"),
                coordinator.formatearResumenDemo(null));
        assertEquals(new ConfigDemoDataCoordinator.EstadoBadgeUi("Sin datos de demo cargados", "badge-pill-neutral"),
                coordinator.formatearResumenDemo(new ResumenDemo(0, 0, 0)));
        assertEquals(new ConfigDemoDataCoordinator.EstadoBadgeUi("Demo activos: 2 clientes, 3 productos, 4 ventas", "badge-pill-success"),
                coordinator.formatearResumenDemo(new ResumenDemo(2, 3, 4)));
        assertEquals(service.resumen, coordinator.contarDatosDemo());
        assertEquals(service.resumen, coordinator.cargarDatosDemo());
        assertEquals(9, coordinator.borrarDatosDemo());
        assertEquals(1, service.contados);
        assertEquals(1, service.cargados);
        assertEquals(1, service.borrados);
    }

    private static final class StubConfiguracionService extends ConfiguracionSistemaService {
        private final RuteoImpresorasData ruteo = new RuteoImpresorasData(Map.of(1, "Bebidas"), List.of());
        private final DatosRestauranteConfig config = new DatosRestauranteConfig("Local", "Calle", "123", "RUC", "Caja");
        private int idCategoria;
        private String nombreImpresora;
        private int eliminada;
        private DatosRestauranteConfig guardada;

        @Override
        public RuteoImpresorasData cargarRuteoImpresoras() { return ruteo; }
        @Override
        public void asignarImpresoraCategoria(int id, String nombre) {
            idCategoria = id;
            nombreImpresora = nombre;
        }
        @Override
        public void eliminarImpresoraCategoria(int id) { eliminada = id; }
        @Override
        public Optional<DatosRestauranteConfig> cargarConfiguracionRestaurante() { return Optional.of(config); }
        @Override
        public void guardarConfiguracionRestaurante(DatosRestauranteConfig datos) { guardada = datos; }
    }

    private static final class StubDemoService extends DemoDataService {
        private final ResumenDemo resumen = new ResumenDemo(1, 2, 3);
        private int contados;
        private int cargados;
        private int borrados;

        @Override
        public ResumenDemo contarDatosDemo() {
            contados++;
            return resumen;
        }
        @Override
        public ResumenDemo cargarDatosDemo() {
            cargados++;
            return resumen;
        }
        @Override
        public int borrarDatosDemo() {
            borrados++;
            return 9;
        }
    }
}
