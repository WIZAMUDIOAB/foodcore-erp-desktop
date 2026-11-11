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

---

## Política de Análisis Estático (SpotBugs), Dependencias y Supply Chain

1. **Exclusiones AppSec en SpotBugs (`config/spotbugs-exclude.xml`):**
   - SpotBugs se ejecuta en la fase `verify` con `<effort>Max</effort>`, `<threshold>Medium</threshold>` y `<failOnError>true</failOnError>`.
   - **Prohibición estricta:** Ninguna regla de seguridad o higiene crítica (`SQL_INJECTION`, `SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING`, `SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE`, `XSS`, `HARD_CODE_PASSWORD`, `PATH_TRAVERSAL`, `PREDICTABLE_RANDOM`, `WEAK_TRUST_MANAGER`, `OBL_UNSATISFIED_OBLIGATION`, `ODR_OPEN_DATABASE_RESOURCE`, `REC_CATCH_EXCEPTION`, `IS2_INCONSISTENT_SYNC`, `NP_*`, `RV_*`, `DM_EXIT`) puede excluirse.
   - Toda exclusión debe quedar documentada con justificación técnica, revisor AppSec y fecha dentro de `config/spotbugs-exclude.xml`, limitada exclusivamente a falsos positivos verificados de diseño (`EI_EXPOSE_REP`/`2` en controladores JavaFX/DI/POJOs de dominio, `MS_EXPOSE_REP` en singletons gestionados y `Stage` principal, `MS_EXPOSE_REP`/`EI_EXPOSE_STATIC_REP2` en estado de sesión de `AuthService`, y `NM_SAME_SIMPLE_NAME_AS_SUPERCLASS` en adaptadores `com.foodcore.dao.impl`).
2. **Escaneo de Vulnerabilidades de Dependencias (OWASP Dependency-Check):**
   - El plugin `org.owasp:dependency-check-maven` se ejecuta en `mvn verify` con `<failBuildOnCVSS>7</failBuildOnCVSS>` (usando NVD CVE 2.0 Datafeed y CISA KEV; el analizador de Sonatype OSS Index viene deshabilitado por defecto vía `odc.ossindex.enabled=false` dado que OSS Index v3 exige credenciales autenticadas y responde HTTP 401 a consultas anónimas, pudiendo activarse con `-Dodc.ossindex.enabled=true` y `-DossIndexServerId=...`), fallando la compilación ante cualquier vulnerabilidad Alta o Crítica (CVSS >= 7.0).
   - Cualquier excepción temporal requiere actualización de la dependencia vulnerable como primera opción o supresión explícita justificada tras verificar no-explotabilidad en nuestro grafo de llamadas; en `com.mysql:mysql-connector-j` se excluye explícitamente la dependencia transitiva no utilizada `com.google.protobuf:protobuf-java` (X DevAPI no utilizado).
3. **Integridad de CI/CD:**
   - El workflow [`.github/workflows/e2e.yml`](.github/workflows/e2e.yml) fija todas las GitHub Actions a hashes SHA-1 de commit de 40 caracteres inmutables (con versión comentada) y restringe `permissions: contents: read`.

---

## Contacto de Seguridad y Divulgación Responsable

- Si detectas una vulnerabilidad de seguridad en **FoodCore ERP** (inyección SQL, omisión de autenticación/RBAC, exposición de secretos, problemas criptográficos o CVEs en dependencias), **no abras un issue público** con detalles explotables.
- Reporta el hallazgo de forma privada al equipo de ingeniería/AppSec mediante los canales privados del repositorio (GitHub *Private Vulnerability Reporting* / Security Advisories del repositorio o contacto directo con los mantenedores).
- Incluye pasos de reproducción reproducibles, impacto estimado y versión/commit afectado; el equipo acusará recibo en <= 48 horas hábiles y coordinará la remediación antes de su divulgación pública.
