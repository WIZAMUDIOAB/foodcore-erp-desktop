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
