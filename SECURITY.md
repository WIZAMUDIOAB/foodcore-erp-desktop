# Política de Seguridad — FoodCore ERP

Este documento establece los controles de seguridad de aplicación (AppSec) obligatorios para desarrollo, pruebas y despliegue de **FoodCore ERP**.

---

## SQL y Prevención de Inyección SQL

1. **Sentencias parametrizadas obligatorias (`PreparedStatement`):**
   - Toda consulta, inserción, actualización o eliminación SQL en `com.foodcore.dao`, `com.foodcore.offline`, `com.foodcore.service`, `com.foodcore.api` y `com.foodcore.controller` debe ejecutarse exclusivamente mediante `java.sql.PreparedStatement` vinculando valores de entrada con marcadores `?` (`setString`, `setInt`, `setBigDecimal`, `setTimestamp`, `setDate`).
   - Queda prohibido el uso de `java.sql.Statement` (`conn.createStatement()`) y la concatenación o interpolación de entradas externas (`+`, `StringBuilder`, `String.format`) dentro de cadenas SQL.
2. **Cláusulas estructurales dinámicas (`IN (...)`, `ORDER BY`):**
   - Las listas `IN (...)` de longitud variable deben construirse mediante `SqlQueryBuilder.buildInPlaceholders(int count)`, que genera únicamente marcadores `?` y vincula cada valor con `PreparedStatement`.
   - Cualquier identificador estructural (`ORDER BY`, nombre de columna o dirección) debe validarse contra una **whitelist estricta** vía `SqlQueryBuilder.resolveWhitelistedIdentifier(...)`, rechazando cualquier valor fuera de lista con `IllegalArgumentException`.
3. **Verificación automatizada:**
   - SpotBugs ejecuta sin exclusiones para `SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING` y `SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE`.
   - La suite de integración `SqlInjectionPreventionIT` verifica payloads clásicos de inyección (`' OR '1'='1`, `'; DROP TABLE ...; --`, `' UNION SELECT ...`) contra login, búsqueda de pedidos por estado y filtros de fecha en MySQL real.

---

## Gestión de Secretos y Cifrado Offline (SQLite)

1. **Variables de entorno obligatorias (Fail-Fast, sin fallbacks hardcodeados):**
   - `FOODCORE_DB_PASS`, `FOODCORE_MIGRATOR_PASS`, `FOODCORE_JWT_SECRET` y `FOODCORE_OFFLINE_DB_KEY` deben configurarse explícitamente en variables de entorno o propiedades JVM (`-D...`).
   - `ConexionBD` y `OfflineCryptoService` fallan explícitamente con `IllegalStateException` ante credenciales o claves ausentes, vacías o placeholders `${...}` sin resolver.
2. **Protección criptográfica en reposo (`com.foodcore.offline`):**
   - Los datos personales de clientes (`cliente_nombre`, `cliente_telefono`, `cliente_direccion`, `cliente_referencia`) y los payloads del outbox (`outbox_sync.payload_json`) almacenados en `terminal_local.db` se cifran registro a registro con **AES-256-GCM** (`AES/GCM/NoPadding`, etiqueta de autenticación de 128 bits).
   - Cada operación de cifrado deriva una clave de 256 bits mediante **`PBKDF2WithHmacSHA256`** (65,536 iteraciones) con **salt de 16 bytes e IV de 12 bytes aleatorios** generados por `SecureRandom`, serializados como `v1:<b64salt>:<b64iv>:<b64cipher>`.
3. **Permisos de sistema de archivos y purga automática:**
   - `LocalDatabaseManager` inicializa la base SQLite con permisos restrictivos exclusivos del propietario (`rw-------` / `600` en POSIX y ACL limitada únicamente al propietario en Windows NTFS).
   - Al confirmarse la sincronización con MySQL central en `SyncWorker.procesarOutboxSync()`, los registros `SINCRONIZADO` y sus filas locales en `outbox_sync`, `detalle_pedido` y `pedido` se purgan automáticamente.
