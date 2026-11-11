# MANUAL DE OPERACIÓN Y RECUPERACIÓN ANTE DESASTRES — FOODCORE ERP v1.0-GA

| Campo | Valor |
|---|---|
| Documento | `MANUAL_OPERACION_Y_RECUPERACION_v1.0` |
| Versión del Sistema | `v1.0.0-GA` |
| Referencia Normativa | `SPEC-FOODCORE-v1.0` (§17, §19.13, §19.14) / `REMEDIATION-PLAN-FOODCORE-v1.0` |

---

## 1. Requisitos de Despliegue y Topología de Red Segura (`R-15`)

1. **Base de Datos MySQL 8.0**:
   - Escucha restringida exclusivamente a loopback local (`bind-address=127.0.0.1` en `config/my.ini`).
   - **Separación de Ambientes Producción vs CI/Testing (`N3`)**:
     - **Producción**: El usuario de aplicación `foodcore_app`@`localhost` tiene privilegios de mínimo privilegio exclusivamente sobre la base operativa `foodcore.*`:
       ```sql
       GRANT SELECT, INSERT, UPDATE, DELETE ON foodcore.* TO 'foodcore_app'@'localhost';
       ```
     - **CI / Testing Automatizado**: El grant sobre la base de pruebas `foodcore_test.*` debe ejecutarse únicamente dentro del pipeline de CI/staging antes de correr las suites de integración, **nunca** en el script de aprovisionamiento de producción:
       ```sql
       -- Ejecutar SOLO en pipelines de CI / entornos de desarrollo
       GRANT SELECT, INSERT, UPDATE, DELETE ON foodcore_test.* TO 'foodcore_app'@'localhost';
       ```
   - Usuario de migración (exclusivo de bootstrap): `foodcore_migrator`@`localhost` para la ejecución de `FlywayMigrator.migrate()` (`V1__init.sql` a `V4__seed_terminales.sql`).
   - Variable de entorno obligatoria en producción: `FOODCORE_DB_PASS`.
2. **Servidor de Sockets KDS LAN**:
   - Puerto TCP `9090/tcp` abierto únicamente para la subred LAN de terminales autorizadas (`KDS-01`, `POS-01`).
   - Límite duro de trama: `1 MB` (`1048576` bytes).
   - Handshake mutuo `HELLO / WELCOME / RENEW` validado con BCrypt costo 12 contra `terminal.secreto_hash` y firma por trama `HMAC-SHA256`.

---

## 2. Flujo Operativo Diario por Roles

1. **Primer Ingreso del Administrador (`R-18`)**:
   - Al iniciar con la cuenta semilla `admin`, el sistema detecta `cambio_password_requerido = 1` y exige actualizar la contraseña antes de habilitar cualquier operación.
   - Cinco (5) intentos fallidos consecutivos bloquean la cuenta automáticamente durante **15 minutos** (`bloqueado_hasta`).
   - Toda sesión expira tras **30 minutos** (`SesionUsuario.expiraEn`).
2. **Apertura de Turno de Caja (`R-02.4`, `B4`)**:
   - El rol `CAJERO` registra el fondo inicial en efectivo (`turno_caja` en estado `ABIERTO`).
3. **Toma de Pedidos y Despacho KDS (`R-02.5`, `R-08`, `R-13`, `B5`, `B6`)**:
   - El rol `MOZO` selecciona la mesa (protegida por bloqueo optimista `version`) y registra el pedido con `idempotency_key`.
   - Cada línea (`detalle_pedido`) persiste su propio `enviado_en` en zona horaria `America/Lima`.
   - En la pantalla `COCINA (KDS)`, cada tarjeta calcula su cronómetro individualmente por ítem:
     - **Verde**: `< 5 min` (A tiempo)
     - **Ámbar**: `5 a 10 min` (Atención)
     - **Rojo**: `> 10 min` (Crítico)
4. **Cobro POS, Descuento de Inventario y Cierre Z (`R-02.1`, `R-02.2`, `R-02.3`, `R-09`, `R-12`, `B6`)**:
   - El cobro (`F5`) deshabilita el botón de acción durante la transacción evitando cobros duplicados.
   - `VentaServiceImpl` bloquea los insumos de la receta en orden ascendente de `id_insumo` con `SELECT ... FOR UPDATE`, descuenta `stock_actual_disponible` y registra el movimiento firmado en `kardex_movimiento`.
   - Al finalizar el turno, el cajero ingresa el efectivo físico contado y el sistema emite el Cierre Z (`saldo_final_teorico`, `saldo_final_contado`, `diferencia`, estado `CERRADO`).

---

## 3. Procedimiento de Respaldo y Recuperación (`R-16`, `N4`)

### 3.1 Ejecución de Backup Diario
- **Windows (PowerShell)**:
  ```powershell
  .\scripts\backup.ps1 -DbName foodcore
  ```
- **Linux (Bash)**:
  ```bash
  ./scripts/backup.sh foodcore
  ```
  Genera un volcado consistente transaccional (`--single-transaction --routines --triggers --no-tablespaces`) comprimido en `backups/foodcore_YYYYMMDD.sql.gz`.

### 3.2 Restauración ante Desastres y Verificación de Integridad
- **Windows (PowerShell)**:
  ```powershell
  .\scripts\restore.ps1 -BackupFile .\backups\foodcore_YYYYMMDD.sql.gz -DbName foodcore
  ```
- **Linux (Bash)**:
  ```bash
  ./scripts/restore.sh backups/foodcore_YYYYMMDD.sql.gz foodcore
  ```
- La bitácora de auditoría queda registrada en `logs/restore_YYYYMMDD.log`. Tras la restauración, verifique el conteo completo de todas las tablas operativas del negocio:
  `venta`, `detalle_venta`, `kardex_movimiento`, `outbox_evento`, `pedido`, `detalle_pedido`, `turno_caja`, `movimiento_caja`, `insumo`, `rol`, `usuario`, `terminal` y `flyway_schema_history`.


---

## 4. Instalación de Certificado SSL Local (HTTPS) en Tablets Android e iOS (v4.2.0 Sub-Fase B)

Para que las tablets de los mozos operen la PWA con Service Worker activo y sin advertencias de seguridad en el navegador (mostrando el candado seguro de HTTPS), se debe instalar la Autoridad de Certificación autofirmada `foodcore.cer` en cada dispositivo táctil.

### 4.1 Ubicación y Descarga del Certificado
- **Archivo en el servidor**: `keystore.jks` (almacén privado PKCS12/JKS protegido con la contraseña definida en `FOODCORE_KEYSTORE_PASS`, mínimo 8 caracteres; ver `keystore.jks.example`) y `foodcore.cer` (certificado público X.509).
- **Descarga directa en la LAN**:
  Con la tablet conectada a la misma red Wi-Fi del restaurante, abrir el navegador y acceder a:
  ```text
  http://<IP_SERVIDOR>:8080/foodcore.cer
  o
  https://<IP_SERVIDOR>:8443/foodcore.cer
  ```

### 4.2 Instalación en Dispositivos Android (Samsung Galaxy Tab, Xiaomi Pad, Lenovo Tab)
1. **Descarga**: Abrir Chrome en la tablet y descargar `foodcore.cer`.
2. **Instalación como CA**:
   - Abrir **Ajustes** (Configuración) -> **Seguridad y privacidad**.
   - Ingresar a **Otros ajustes de seguridad** -> **Instalar desde almacenamiento del dispositivo** -> **Certificado de CA**.
   - Ante la advertencia del sistema operativo, pulsar **Instalar de todas formas** (ingresar el PIN o patrón si lo solicita).
   - Seleccionar el archivo `foodcore.cer` ubicado en la carpeta *Descargas*.
3. **Validación**:
   - Asignar nombre `FoodCore CA`.
   - Abrir Chrome y navegar a `https://<IP_SERVIDOR>:8443/`.
   - Verificar que la barra de direcciones muestra el **candado de conexión segura**, sin carteles rojos de advertencia.
   - En el menú de Chrome (3 puntos), seleccionar **Instalar aplicación** / **Agregar a pantalla principal**.

### 4.3 Instalación en Apple iPad / iOS (iPadOS)
1. **Descarga de Perfil**:
   - En **Safari**, ingresar a `http://<IP_SERVIDOR>:8080/foodcore.cer`.
   - Al mostrar *"Este sitio web está intentando descargar un perfil de configuración"*, presionar **Permitir**.
2. **Instalación del Perfil**:
   - Abrir **Ajustes** -> en la parte superior tocar **Perfil descargado** (o **General** -> **VPN y gestión de dispositivos**).
   - Tocar el perfil `FoodCore Local SSL CA` y presionar **Instalar** (esquina superior derecha).
   - Introducir el código de desbloqueo del iPad y confirmar **Instalar**.
3. **Habilitación de Confianza Plena (Obligatorio en iOS)**:
   - Ir a **Ajustes** -> **General** -> **Información** -> desplazarse al final y entrar a **Ajustes de confianza de los certificados**.
   - En la sección *"Confiar plenamente en los certificados raíz"*, activar el interruptor para `FoodCore Local SSL CA`.
   - Presionar **Continuar** en el diálogo de confirmación.
4. **Validación**:
   - En Safari, navegar a `https://<IP_SERVIDOR>:8443/`. Aparecerá el candado de conexión cifrada.
   - Tocar el botón de **Compartir** -> **Agregar a pantalla de inicio** para fijar la PWA táctil de mozos.
