# Credenciales Demo y Roles Semilla — FoodCore ERP v1.1

Las cuentas semilla se inicializan mediante las migraciones Flyway (`V2__seed_roles_permisos.sql` y `V3__seed_admin.sql`). Por políticas de diseño y seguridad (`UI-REDESIGN-SPEC-FOODCORE-v1.1` §4.2), estas credenciales no se muestran en la interfaz gráfica de `LoginView`.

---

## 1. Cuentas de Acceso Iniciales

| Usuario | Contraseña Inicial | Rol Asignado | `cambio_password_requerido` | Alcance en el Sistema |
|---|---|---|---|---|
| `admin` | `Admin#2026!` | `ADMIN` | `1` (Obligatorio en primer login) | Acceso total: Salón, POS, KDS, Caja y Cierre Z, Kardex, Reportes y Estado del Sistema. |
| `cajero` | `Admin#2026!` | `CAJERO` | `0` | Punto de Venta (POS), Salón, Apertura/Arqueo/Cierre Z de Caja y emisión de comprobantes. |
| `mozo` | `Admin#2026!` | `MOZO` | `0` | Mapa de Salón y Comedores, apertura de mesas y envío de comandas a cocina. |
| `cocinero` | `Admin#2026!` | `COCINERO` | `0` | Tablero Kanban de Línea de Cocina (KDS) en tiempo real (`Iniciar`, `Listo`, `Entregado`). |

---

## 2. Flujo de Cambio de Contraseña Obligatorio (`admin`)

1. Al iniciar sesión por primera vez con `admin` / `Admin#2026!`, el sistema detecta `cambio_password_requerido = 1` y abre automáticamente el modal **`PasswordChangeDialog`**.
2. La nueva contraseña debe cumplir la política de seguridad:
   - Mínimo **8 caracteres**.
   - Al menos **1 letra mayúscula**, **1 letra minúscula** y **1 número**.
   - Debe ser **diferente** a la contraseña actual.
3. Al pulsar **"Cambiar y entrar"**, el sistema actualiza el hash `BCrypt` (cost factor 12), marca `cambio_password_requerido = 0` en la base de datos e ingresa directamente al panel principal sin volver a pedir login.
4. En inicios de sesión posteriores con la nueva contraseña, el diálogo ya no aparece.
