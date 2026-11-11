package com.foodcore.offline;

import com.foodcore.model.DetallePedido;
import com.foodcore.model.Pedido;
import com.foodcore.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.AEADBadTagException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de cifrado AES-256-GCM en SQLite offline, rechazo fail-fast sin clave,
 * permisos restrictivos de archivo y purga automática tras sincronización.
 */
class OfflineEncryptionTest {

    private static final String TEST_KEY = "FoodCore-Offline-Key-Test#2026!";

    @TempDir
    Path tempDir;

    private Path dbPath;
    private String previousOfflineKey;

    @BeforeEach
    void setUp() {
        previousOfflineKey = System.getProperty(OfflineCryptoService.ENV_OFFLINE_DB_KEY);
        dbPath = tempDir.resolve("terminal_local.db");
        LocalDatabaseManager.setDbPath(dbPath.toString());
        System.clearProperty(OfflineCryptoService.ENV_OFFLINE_DB_KEY);
    }

    @AfterEach
    void tearDown() {
        SyncWorker.getInstancia().setSimularDesconexionRed(false);
        LocalDatabaseManager.setDbPath(null);
        if (previousOfflineKey != null) {
            System.setProperty(OfflineCryptoService.ENV_OFFLINE_DB_KEY, previousOfflineKey);
        } else {
            System.clearProperty(OfflineCryptoService.ENV_OFFLINE_DB_KEY);
        }
    }

    @Test
    @DisplayName("Falla explícitamente si FOODCORE_OFFLINE_DB_KEY falta o está vacía (sin fallback)")
    void fallaSinClaveOfflineObligatoria() {
        assertFalse(OfflineCryptoService.isKeyConfigured());
        assertThrows(IllegalStateException.class, OfflineCryptoService::requireKey);
        assertThrows(IllegalStateException.class, LocalDatabaseManager::getLocalConnection);
        assertThrows(IllegalStateException.class, LocalDatabaseManager::inicializarEsquemaLocal);
        assertThrows(IllegalStateException.class, () -> SyncWorker.getInstancia().setSimularDesconexionRed(true));
        assertThrows(IllegalStateException.class, () -> OfflineCryptoService.encrypt("   ", "dato"));

        Pedido p = construirPedidoPii();
        List<DetallePedido> dets = construirDetalles();
        assertThrows(IllegalStateException.class,
                () -> SyncWorker.getInstancia().guardarPedidoEnOutboxLocal(p, dets));
        assertThrows(IllegalStateException.class,
                () -> SyncWorker.getInstancia().procesarOutboxSync());
        assertFalse(Files.exists(dbPath), "No debe crearse la base local si falta la clave obligatoria");
    }

    @Test
    @DisplayName("AES-256-GCM usa salt e IV aleatorios por registro y rechaza clave incorrecta o alteración")
    void cifradoUnicoPorRegistroYRechazaClaveIncorrecta() {
        String secreto = "Av. José Pardo 450 - Miraflores · 998877665";
        String c1 = OfflineCryptoService.encrypt(TEST_KEY, secreto);
        String c2 = OfflineCryptoService.encrypt(TEST_KEY, secreto);

        assertTrue(c1.startsWith("v1:"));
        assertTrue(c2.startsWith("v1:"));
        assertNotEquals(c1, c2, "Cada cifrado debe usar salt e IV distintos");
        assertEquals(secreto, OfflineCryptoService.decrypt(TEST_KEY, c1));
        assertEquals(secreto, OfflineCryptoService.decrypt(TEST_KEY, c2));

        IllegalStateException exWrong = assertThrows(IllegalStateException.class,
                () -> OfflineCryptoService.decrypt("OtraClaveIncorrecta#2026", c1));
        assertTrue(exWrong.getCause() instanceof AEADBadTagException);

        String tampered = c1.substring(0, c1.length() - 2) + (c1.endsWith("AA") ? "BB" : "AA");
        assertThrows(IllegalStateException.class,
                () -> OfflineCryptoService.decrypt(TEST_KEY, tampered));
        assertThrows(IllegalStateException.class,
                () -> OfflineCryptoService.decrypt(TEST_KEY, "texto-plano-sin-v1"));
    }

    @Test
    @DisplayName("SQLite local cifra PII/payload, aplica permisos owner-only y purga filas tras sincronizar")
    void sqlLiteCifraPermisosRestrictivosYAutoPurgaTrasSync() throws Exception {
        MySqlIntegrationTestSupport.inicializarEntornoMySql();
        System.setProperty(OfflineCryptoService.ENV_OFFLINE_DB_KEY, TEST_KEY);

        SyncWorker syncWorker = SyncWorker.getInstancia();
        syncWorker.setSimularDesconexionRed(true);

        Pedido pedido = construirPedidoPii();
        List<DetallePedido> detalles = construirDetalles();
        int idPedidoLocal = syncWorker.guardarPedidoEnOutboxLocal(pedido, detalles);
        assertTrue(idPedidoLocal > 0);
        assertTrue(Files.exists(dbPath));

        verificarPermisosRestrictivosEnArchivo(dbPath);

        // Inspección directa de SQLite sin pasar por SyncWorker
        try (Connection rawConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement psPed = rawConn.prepareStatement(
                     "SELECT cliente_nombre, cliente_telefono, cliente_direccion, cliente_referencia FROM pedido WHERE id_pedido_local = ?");
             PreparedStatement psOut = rawConn.prepareStatement(
                     "SELECT payload_json, estado FROM outbox_sync WHERE id_pedido_local = ?")) {
            psPed.setInt(1, idPedidoLocal);
            try (ResultSet rs = psPed.executeQuery()) {
                assertTrue(rs.next());
                String nomCif = rs.getString("cliente_nombre");
                String telCif = rs.getString("cliente_telefono");
                String dirCif = rs.getString("cliente_direccion");
                String refCif = rs.getString("cliente_referencia");

                assertTrue(nomCif.startsWith("v1:"));
                assertTrue(telCif.startsWith("v1:"));
                assertTrue(dirCif.startsWith("v1:"));
                assertTrue(refCif.startsWith("v1:"));

                assertFalse(nomCif.contains("Lucía"));
                assertFalse(telCif.contains("998877665"));
                assertFalse(dirCif.contains("Sauces"));
                assertFalse(refCif.contains("Portón"));

                assertEquals(pedido.getClienteNombre(), OfflineCryptoService.decrypt(TEST_KEY, nomCif));
                assertEquals(pedido.getClienteTelefono(), OfflineCryptoService.decrypt(TEST_KEY, telCif));
                assertEquals(pedido.getClienteDireccion(), OfflineCryptoService.decrypt(TEST_KEY, dirCif));
                assertEquals(pedido.getClienteReferencia(), OfflineCryptoService.decrypt(TEST_KEY, refCif));
            }

            psOut.setInt(1, idPedidoLocal);
            try (ResultSet rs = psOut.executeQuery()) {
                assertTrue(rs.next());
                String payloadCif = rs.getString("payload_json");
                assertEquals("PENDIENTE", rs.getString("estado"));
                assertTrue(payloadCif.startsWith("v1:"));
                assertFalse(payloadCif.contains("Lucía"));
                assertFalse(payloadCif.contains("Lomo Saltado"));
                assertTrue(OfflineCryptoService.decrypt(TEST_KEY, payloadCif).contains("Lucía Vargas Mendoza"));
            }
        }

        // Reconectar y sincronizar -> debe descifrar, enviar al servidor y purgar localmente
        syncWorker.setSimularDesconexionRed(false);
        int sincronizados = syncWorker.procesarOutboxSync();
        assertEquals(1, sincronizados, "Debe sincronizar exactamente el pedido pendiente");
        assertEquals(0, syncWorker.contarPedidosPendientes());

        try (Connection rawConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            assertEquals(0, contarFilas(rawConn, "outbox_sync"), "outbox_sync debe quedar purgada tras sincronizar");
            assertEquals(0, contarFilas(rawConn, "pedido"), "pedido local debe quedar purgado tras sincronizar");
            assertEquals(0, contarFilas(rawConn, "detalle_pedido"), "detalle_pedido local debe quedar purgado tras sincronizar");

            // Simular fila heredada SINCRONIZADO y validar purga explícita
            try (PreparedStatement psPed = rawConn.prepareStatement(
                    "INSERT INTO pedido (id_usuario, numero_mesa, tipo_pedido, idempotency_key) VALUES (1, 'M01', 'MESA', 'legacy-uuid-1')");
                 PreparedStatement psOut = rawConn.prepareStatement(
                    "INSERT INTO outbox_sync (id_pedido_local, idempotency_key, payload_json, estado) VALUES (last_insert_rowid(), 'legacy-uuid-1', 'v1:a:b:c', 'SINCRONIZADO')")) {
                psPed.executeUpdate();
                psOut.executeUpdate();
            }
        }

        int purgados = syncWorker.purgarRegistrosSincronizados();
        assertEquals(1, purgados);
        try (Connection rawConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            assertEquals(0, contarFilas(rawConn, "outbox_sync"));
            assertEquals(0, contarFilas(rawConn, "pedido"));
        }
    }

    private void verificarPermisosRestrictivosEnArchivo(Path file) throws Exception {
        PosixFileAttributeView posixView = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posixView != null) {
            String perms = PosixFilePermissions.toString(posixView.readAttributes().permissions());
            assertEquals("rw-------", perms, "En POSIX el archivo SQLite debe tener permisos 600");
            return;
        }
        AclFileAttributeView aclView = Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (aclView != null) {
            UserPrincipal owner = aclView.getOwner();
            List<AclEntry> acl = aclView.getAcl();
            assertFalse(acl.isEmpty());
            assertTrue(acl.stream().allMatch(entry -> entry.principal().equals(owner)),
                    "En Windows NTFS la ACL del archivo SQLite solo debe conceder acceso al propietario");
        }
    }

    private int contarFilas(Connection conn, String tabla) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + tabla);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private Pedido construirPedidoPii() {
        Pedido p = new Pedido();
        p.setIdMesa(1);
        p.setNumeroMesa("DELIV-99");
        p.setTipoPedido("DELIVERY");
        p.setClienteNombre("Lucía Vargas Mendoza");
        p.setClienteTelefono("998877665");
        p.setClienteDireccion("Calle Los Sauces 420 Surco");
        p.setClienteReferencia("Portón verde");
        p.setCostoEnvio(new BigDecimal("6.00"));
        p.setTotal(new BigDecimal("42.00"));
        p.setIdempotencyKey(UUID.randomUUID().toString());
        return p;
    }

    private List<DetallePedido> construirDetalles() {
        DetallePedido det = new DetallePedido();
        det.setIdProducto(1);
        det.setNombreProducto("Lomo Saltado Especial");
        det.setCantidad(1);
        det.setPrecioUnitario(new BigDecimal("36.00"));
        det.setSubtotal(new BigDecimal("36.00"));
        det.setNotasPreparacion("Sin cebolla");
        return List.of(det);
    }
}
