# FoodCore ERP Desktop v5.3.0 - Fast Food S.A.C.
Sistema ERP de escritorio, POS LAN/PWA, KDS, Finanzas BI, RRHH y CRM Minorista bajo arquitectura de **Costo Cero ($0.00) en Red Local (LAN)**.

---

## Instalación en PC Nueva (Desarrollador / Servidor)

### Requisitos previos
- Java 17 o 21 (JDK)
- MySQL 8.0+ corriendo en puerto `3306`
- Maven Wrapper incluido (`mvnw.cmd` / `./mvnw`)

### Variables de Entorno Obligatorias
Antes de iniciar la aplicación o ejecutar migraciones:
```powershell
$env:FOODCORE_DB_PASS="TuPasswordApp"
$env:FOODCORE_MIGRATOR_PASS="TuPasswordMigrador"
$env:FOODCORE_KEYSTORE_PASS="PasswordKeystore8Chars"
$env:FOODCORE_JWT_SECRET="SecretoJwtMinimoDeTreintaYDosCaracteres2026"
# Opcional para alertas por email SMTP:
$env:FOODCORE_SMTP_PASS="AppPasswordSmtp"
```
Consulta `keystore.jks.example` para generar `keystore.jks` y `foodcore.cer` para HTTPS en LAN (puerto `8443`).

---

## Empaquetado e Instalador (v5.3.0)

El paquete de distribución incluye todos los módulos (Operaciones, Inventario, Finanzas BI, RRHH, CRM Minorista), el runtime Java embebido y los recursos auxiliares (`pwa/`, `keystore.jks.example`, `scripts/`, `foodcore.cer`, `application.properties.example`).

### Windows (`dist/FoodCoreERP-5.3.0.msi`)
```powershell
.\mvnw.cmd -B clean package -Prelease -DskipTests
```
O después de `mvn package`:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/package-release.ps1
```
El instalador queda generado en `dist/FoodCoreERP-5.3.0.msi` junto con `dist/SHA256SUMS.txt`.

### Linux (`dist/FoodCoreERP-5.3.0.deb`)
```bash
./mvnw -B clean package -DskipTests
bash scripts/build-deb.sh
```

---

## Backups Automáticos (03:00 AM y Retención de 30 Días)

- **Windows**: `scripts/backup-auto.ps1`
  - Ejecuta `mysqldump --single-transaction --routines --triggers` sobre `foodcore`.
  - Comprime a `.sql.gz` en `backups/` con ACL restringida (`SYSTEM` / `Administrators`).
  - Purga automáticamente archivos de backup mayores a `30` días.
  - Con `-RegisterTask`, registra la tarea programada `FoodCoreERP_Backup_3AM` para las **03:00 AM**:
    ```powershell
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/backup-auto.ps1 -RegisterTask
    schtasks /Query /TN "FoodCoreERP_Backup_3AM" /V /FO LIST
    ```
- **Linux**: `scripts/backup-auto.sh`
  - Comprime en `.sql.gz` dentro de `backups/` (`chmod 700` directorio, `chmod 600` archivos), purga `+30` días e instala el cron `0 3 * * *` con `--install-cron`.

---

## Restauración Automatizada

- **Desde CLI**:
  - Windows: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/restore-auto.ps1 -BackupFile backups\foodcore_YYYYMMDD.sql.gz`
  - Linux: `bash scripts/restore-auto.sh backups/foodcore_YYYYMMDD.sql.gz`
  - Valida archivo `.sql.gz` / `.sql`, detiene servicios activos, restaura en MySQL y reinicia servicios.
- **Desde Backoffice**:
  - Ir a **Configuración del Sistema -> Respaldo y Restauración Automatizada**, verificar la ruta del backup y pulsar **Restaurar desde Backup** (solicita confirmación previa).

---

## Monitoreo y Alertas por Email

- **Estado del Sistema (`EstadoSistemaView.fxml`)**:
  - Disponible exclusivamente para rol `ADMIN` en la barra lateral (**Estado del Sistema**).
  - Auto-refresco cada **5 segundos** de: MySQL (`UP`/`DOWN`, versión, latencia ms), Javalin HTTP (`8080`), Javalin HTTPS (`8443`), Socket KDS (`9090` + clientes conectados), Cloudflare Tunnel (URL y estado), versión Flyway, Uptime, CPU %, RAM (usada/total) y Disco (libre/total).
- **Alertas SMTP (`AlertaService`)**:
  - Configurar `smtp.host`, `smtp.port`, `smtp.user`, `smtp.password` y `alertas.email.destinatario` en `application.properties` o desde **Configuración del Sistema**.
  - Envía alerta por email al dueño cuando MySQL no responde por **> 1 minuto**, el disco libre cae por debajo de **5 GB**, o la RAM se mantiene en **>= 90%** por **> 5 minutos**.
