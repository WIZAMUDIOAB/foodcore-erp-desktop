package com.foodcore.service;

import com.foodcore.dao.DocumentoEmpleadoDAO;
import com.foodcore.dao.ExpedienteUsuarioDAO;
import com.foodcore.dao.impl.DocumentoEmpleadoDAOImpl;
import com.foodcore.dao.impl.ExpedienteUsuarioDAOImpl;
import com.foodcore.model.DocumentoEmpleado;
import com.foodcore.model.Usuario;
import com.foodcore.service.impl.ExpedienteEmpleadoServiceImpl;
import com.foodcore.support.MySqlIntegrationTestSupport;
import com.foodcore.util.ConexionBD;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias e integración para {@link ExpedienteEmpleadoServiceImpl} y sus DAOs.
 */
class ExpedienteEmpleadoServiceTest {

    @BeforeAll
    static void initDb() {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
    }

    @Test
    @DisplayName("Expediente: validaciones y actualización de datos personales y laborales")
    void testGuardarDatosPersonalesYLaborales(@TempDir Path tempDir) throws Exception {
        ExpedienteUsuarioDAO usuarioDao = new ExpedienteUsuarioDAOImpl();
        DocumentoEmpleadoDAO docDao = new DocumentoEmpleadoDAOImpl();
        ExpedienteEmpleadoService service = new ExpedienteEmpleadoServiceImpl(usuarioDao, docDao, tempDir);

        List<Usuario> lista = service.listarExpedientes(true);
        assertFalse(lista.isEmpty(), "Debe existir personal activo semilla");

        Usuario admin = service.buscarExpediente(1).orElseThrow();
        String direccionOrig = admin.getDireccion();
        String telEmergOrig = admin.getTelefonoEmergencia();
        double sueldoOrig = admin.getSueldoBase();
        String cargoOrig = admin.getCargo();

        try {
            admin.setTipoDocumento("DNI");
            admin.setDni("12345");
            assertThrows(IllegalArgumentException.class, () -> service.guardarDatosPersonales(admin));

            admin.setDni("44556677");
            admin.setFechaNacimiento(LocalDate.now().plusDays(1));
            assertThrows(IllegalArgumentException.class, () -> service.guardarDatosPersonales(admin));

            admin.setFechaNacimiento(LocalDate.of(1990, 4, 12));
            admin.setDireccion("Av. Arequipa 2450, Lince");
            admin.setTelefonoEmergencia("999888777");
            admin.setContactoEmergencia("Elena Mendoza");
            service.guardarDatosPersonales(admin);

            Usuario actualizado = service.buscarExpediente(1).orElseThrow();
            assertEquals("Av. Arequipa 2450, Lince", actualizado.getDireccion());
            assertEquals("999888777", actualizado.getTelefonoEmergencia());

            actualizado.setSueldoBase(-10.0);
            assertThrows(IllegalArgumentException.class, () -> service.guardarDatosLaborales(actualizado));

            actualizado.setSueldoBase(3200.00);
            actualizado.setFechaIngreso(LocalDate.now().minusMonths(6));
            actualizado.setFechaVencimientoContrato(LocalDate.now().minusMonths(12));
            assertThrows(IllegalArgumentException.class, () -> service.guardarDatosLaborales(actualizado));

            actualizado.setFechaVencimientoContrato(LocalDate.now().plusDays(180));
            actualizado.setCargo("Gerente General");
            actualizado.setTipoContrato("INDETERMINADO");
            actualizado.setHoraEntradaProgramada(LocalTime.of(8, 30));
            actualizado.setHoraSalidaProgramada(LocalTime.of(17, 30));
            service.guardarDatosLaborales(actualizado);

            Usuario laboralActualizado = service.buscarExpediente(1).orElseThrow();
            assertEquals("Gerente General", laboralActualizado.getCargo());
            assertEquals(3200.00, laboralActualizado.getSueldoBase(), 0.01);
        } finally {
            admin.setDireccion(direccionOrig);
            admin.setTelefonoEmergencia(telEmergOrig);
            admin.setFechaNacimiento(LocalDate.of(1990, 4, 12));
            admin.setDni("10000001");
            try (Connection conn = ConexionBD.getConnection()) {
                usuarioDao.actualizarDatosPersonales(conn, admin);
                admin.setSueldoBase(sueldoOrig);
                admin.setCargo(cargoOrig);
                admin.setFechaVencimientoContrato(null);
                usuarioDao.actualizarDatosLaborales(conn, admin);
            }
        }
    }

    @Test
    @DisplayName("Documentos PDF: valida cabecera %PDF-, almacena archivo y detecta alertas a 30 días")
    void testSubirListarYEliminarPdfYAlertas(@TempDir Path tempDir) throws Exception {
        ExpedienteEmpleadoService service = new ExpedienteEmpleadoServiceImpl(
                new ExpedienteUsuarioDAOImpl(), new DocumentoEmpleadoDAOImpl(), tempDir.resolve("repo"));

        Path txtFalso = tempDir.resolve("falso.txt");
        Files.writeString(txtFalso, "no es pdf", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> service.subirDocumentoPdf(
                2, "CONTRATO_TRABAJO", txtFalso.toFile(), LocalDate.now(), LocalDate.now().plusDays(15), "test"));

        Path pdfHeaderInvalido = tempDir.resolve("corrupto.pdf");
        Files.writeString(pdfHeaderInvalido, "HTML FAKE CONTENT", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> service.subirDocumentoPdf(
                2, "CONTRATO_TRABAJO", pdfHeaderInvalido.toFile(),
                LocalDate.now(), LocalDate.now().plusDays(15), "test"));

        Path pdfValido = tempDir.resolve("carnet_valido.pdf");
        Files.write(pdfValido, "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII));

        assertThrows(IllegalArgumentException.class, () -> service.subirDocumentoPdf(
                2, "CARNET_SANIDAD", pdfValido.toFile(),
                LocalDate.now(), LocalDate.now().minusDays(2), "fechas invertidas"));

        DocumentoEmpleado subido = service.subirDocumentoPdf(
                2,
                "CARNET_SANIDAD",
                pdfValido.toFile(),
                LocalDate.now().minusMonths(11),
                LocalDate.now().plusDays(10),
                "Carnet de prueba junit");

        try {
            assertTrue(subido.getIdDocumento() > 0);
            assertTrue(Files.exists(Path.of(subido.getRutaArchivo())),
                    "El archivo PDF debe copiarse al directorio de expedientes");

            List<DocumentoEmpleado> docsUsuario = service.listarDocumentos(2);
            assertTrue(docsUsuario.stream().anyMatch(d -> d.getIdDocumento() == subido.getIdDocumento()));

            List<DocumentoEmpleado> porVencer = service.listarDocumentosProximosAVencer(30);
            assertTrue(porVencer.stream().anyMatch(d -> d.getIdDocumento() == subido.getIdDocumento()),
                    "El documento que vence en 10 días debe aparecer en alertas a 30 días");
            assertNotNull(porVencer.get(0).getNombreEmpleado());
        } finally {
            service.eliminarDocumento(subido.getIdDocumento());
        }

        assertFalse(Files.exists(Path.of(subido.getRutaArchivo())),
                "Al eliminar el registro debe eliminarse el archivo físico temporal");
        assertTrue(service.listarDocumentos(2).stream()
                .noneMatch(d -> d.getIdDocumento() == subido.getIdDocumento()));
    }
}
