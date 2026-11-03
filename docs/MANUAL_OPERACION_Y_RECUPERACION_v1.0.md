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
   - Usuario de aplicación de mínimo privilegio: `foodcore_app`@`localhost` con `GRANT SELECT, INSERT, UPDATE, DELETE ON foodcore.*`.
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
2. **Apertura de Turno de Caja (`R-02.4`)**:
   - El rol `CAJERO` registra el fondo inicial en efectivo (`turno_caja` en estado `ABIERTO`).
3. **Toma de Pedidos y Despacho KDS (`R-02.5`, `R-08`, `R-13`)**:
   - El rol `MOZO` selecciona la mesa (protegida por bloqueo optimista `version`) y registra el pedido con `idempotency_key`.
   - Cada línea (`detalle_pedido`) persiste su propio `enviado_en` en zona horaria `America/Lima`.
   - En la pantalla `COCINA (KDS)`, cada tarjeta calcula su cronómetro individualmente por ítem:
     - **Verde**: `< 5 min` (A tiempo)
     - **Ámbar**: `5 a 10 min` (Atención)
     - **Rojo**: `> 10 min` (Crítico)
4. **Cobro POS, Descuento de Inventario y Cierre Z (`R-02.1`, `R-02.2`, `R-02.3`, `R-09`, `R-12`)**:
   - El cobro (`F5`) deshabilita el botón de acción durante la transacción evitando cobros duplicados.
   - `VentaServiceImpl` bloquea los insumos de la receta en orden ascendente de `id_insumo` con `SELECT ... FOR UPDATE`, descuenta `stock_actual_disponible` y registra el movimiento firmado en `kardex_movimiento`.
   - Al finalizar el turno, el cajero ingresa el efectivo físico contado y el sistema emite el Cierre Z (`saldo_final_teorico`, `saldo_final_contado`, `diferencia`, estado `CERRADO`).

---

## 3. Procedimiento de Respaldo y Recuperación (`R-16`)

### 3.1 Ejecución de Backup Diario
- **Windows (PowerShell)**:
  ```powershell
  .\scripts\backup.ps1 -DbName foodcore
  ```
- **Linux (Bash)**:
  ```bash
  ./scripts/backup.sh foodcore
  ```
  Genera un volcado consistente transaccional (`--single-transaction --routines --triggers`) comprimido en `backups/foodcore_YYYYMMDD.sql.gz`.

### 3.2 Restauración ante Desastres y Verificación de Integridad
- **Windows (PowerShell)**:
  ```powershell
  .\scripts\restore.ps1 -BackupFile .\backups\foodcore_YYYYMMDD.sql.gz -DbName foodcore
  ```
- **Linux (Bash)**:
  ```bash
  ./scripts/restore.sh backups/foodcore_YYYYMMDD.sql.gz foodcore
  ```
- La bitácora de auditoría queda registrada en `logs/restore_YYYYMMDD.log`. Tras la restauración, verifique el conteo de filas de `usuario`, `insumo`, `kardex_movimiento`, `turno_caja` y `pedido`.
