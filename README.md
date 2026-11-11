# FoodCore ERP Desktop - Fast Food S.A.C.
Sistema ERP de escritorio para restaurantes de comida rápida bajo arquitectura de **Costo Cero ($0.00) en Red Local (LAN)**.

## 🚀 Instalación en PC Nueva (Desarrollador)

### Requisitos previos
- Java 17 o 21 (JDK)
- MySQL 8.0 corriendo en puerto 3306
- Maven Wrapper incluido (`mvnw.cmd`)

### Pasos

1. **Preparar MySQL** (una sola vez):
   ```bash
   mysql -u root -p < init.sql
   ```

2. **Configurar variables de entorno** en PowerShell:
   ```powershell
   $env:FOODCORE_DB_PASS="FoodCoreApp123!"
   $env:FOODCORE_MIGRATOR_PASS="FoodCoreMigrator123!"
   $env:FOODCORE_JWT_SECRET="UnaClaveDeMasDe32CaracteresParaJWTseguro2026"
   $env:FOODCORE_KEYSTORE_PASS="FoodCoreKeystore2026"
   $env:FOODCORE_OFFLINE_DB_KEY="UnaClaveLargaParaSQLiteAES256GCM2026X"
   ```

3. **Ejecutar la aplicación**:
   ```powershell
   .\mvnw.cmd clean javafx:run
   ```

   Flyway aplicará automáticamente todas las migraciones (V1 a V14+). La app arrancará sin errores.

### Login inicial
- Usuario: `admin`
- Contraseña: la configurada en el seed Flyway (ver `V3__seed_admin.sql`)

