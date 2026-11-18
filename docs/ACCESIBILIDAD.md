# Auditoría de Accesibilidad WCAG 2.1 Nivel AA — FoodCore ERP (v5.7.0)

Esta auditoría documenta el cumplimiento de accesibilidad del **Sistema de Diseño Cerrado v2.0** (Warm Neutral claro y OLED Zinc oscuro), las mejoras de pulido desktop **v2.2 / v5.6.0**, la semántica de lectores de pantalla en JavaFX (`accessibleRole` / `accessibleText`) y la navegación integral por teclado.

---

## 1. Matriz de Contraste Cromático (WCAG 2.1 AA)

Umbrales estándar WCAG 2.1 AA:
- **Texto normal**: mínimo **4.5:1**.
- **Texto grande / UI y controles interactivos**: mínimo **3.0:1**.

### 1.1 Modo Claro — *Warm Neutral* (`foodcore.css`)

| Uso / Token | Primer Plano | Fondo | Ratio Calculado | Nivel WCAG |
| :--- | :--- | :--- | :---: | :---: |
| Texto principal sobre Canvas (`-text-primary` / `-bg-canvas`) | `#1A1817` | `#F9F7F4` | **16.55 : 1** | AA / AAA |
| Texto principal sobre Superficie (`-text-primary` / `-bg-surface`) | `#1A1817` | `#FFFFFF` | **17.69 : 1** | AA / AAA |
| Texto secundario sobre Canvas (`-text-secondary` / `-bg-canvas`) | `#5C5650` | `#F9F7F4` | **6.77 : 1** | AA |
| Texto secundario en Sidebar (`-text-secondary` / `-bg-sidebar`) | `#5C5650` | `#F2EFEA` | **6.31 : 1** | AA |
| Error / Danger sobre Superficie (`-status-danger` / `-bg-surface`) | `#DC2626` | `#FFFFFF` | **4.83 : 1** | AA |
| Botón Primario Hover (`-text-inverse` / `-brand-hover`) | `#FFFFFF` | `#C2410C` | **5.18 : 1** | AA |
| Botón Primario Base / Controles (`-text-inverse` / `-brand-primary`) | `#FFFFFF` | `#EA580C` | **3.56 : 1** | AA (UI / Bold ≥ 3:1) |

### 1.2 Modo Oscuro — *OLED Zinc* (`dark-theme.css`)

| Uso / Token | Primer Plano | Fondo | Ratio Calculado | Nivel WCAG |
| :--- | :--- | :--- | :---: | :---: |
| Texto principal sobre Canvas (`-text-primary` / `-bg-canvas`) | `#FAFAFA` | `#09090B` | **19.06 : 1** | AA / AAA |
| Texto principal sobre Superficie (`-text-primary` / `-bg-surface`) | `#FAFAFA` | `#18181B` | **16.97 : 1** | AA / AAA |
| Texto secundario sobre Superficie (`-text-secondary` / `-bg-surface`) | `#A1A1AA` | `#18181B` | **6.91 : 1** | AA |
| Texto secundario en Sidebar (`-text-secondary` / `-bg-sidebar`) | `#A1A1AA` | `#121214` | **7.30 : 1** | AA / AAA |
| Éxito KDS / POS (`-status-success` / `-bg-surface`) | `#34D399` | `#18181B` | **9.22 : 1** | AA / AAA |
| Advertencia SLA (`-status-warning` / `-bg-surface`) | `#FBBF24` | `#18181B` | **10.61 : 1** | AA / AAA |
| Error / Danger (`-status-danger` / `-bg-surface`) | `#F87171` | `#18181B` | **6.40 : 1** | AA |
| Acento Terracota (`-brand-primary` / `-bg-surface`) | `#F97316` | `#18181B` | **6.32 : 1** | AA |

---

## 2. Etiquetado Semántico y Lectores de Pantalla (`accessibleRole` / `accessibleText`)

1. **Etiquetas estáticas obligatorias**: Cada `TextField`, `PasswordField`, `ComboBox` y `DatePicker` dispone de un `<Label styleClass="label-form">` o `<Label>` visible encima (verificado automáticamente en `RedisenoPremiumVisualE2ETest.verificarChecklistV22LayoutsYComponentes`).
2. **Atributos ARIA/Accessibility en JavaFX**:
   - **Login (`LoginView.fxml`)**:
     - `#txtUsername`: `accessibleText="Usuario"`
     - `#txtPassword`: `accessibleText="Contraseña"`
     - `#btnLogin`: `accessibleRole="BUTTON"`, `accessibleText="Ingresar al sistema"`
     - `#btnCancelarTotp` y `#btnVerificarTotp`: `accessibleRole="BUTTON"` y descripción de acción 2FA.
   - **Terminal POS (`VentaPOSView.fxml`)**:
     - Botones superiores y acciones de cobro (`#btnFidelizacion`, `#btnDividirCuenta`, `#btnDescuento`, `#btnAnularVenta`, `#btnEnviarCocina`, `#btnEfectivo`, `#btnTarjeta`, `#btnYape`, `#btnPreCuenta`) con `accessibleRole="BUTTON"` y `accessibleText` explícito incluyendo atajos (`F5`, `F6`, `F7`).

---

## 3. Navegación por Teclado y Foco Visible

- **Indicador de foco visible**: Todos los `.text-field:focused`, `.password-field:focused`, `.combo-box:focused` y `.date-picker:focused` muestran borde `-brand-primary` y halo Terracota (`rgba(234, 88, 12, 0.2)`).
- **Orden secuencial con `Tab` en Login**: `#txtUsername` -> `#txtPassword` -> `#btnLogin` (`defaultButton="true"` activable con `Enter`).
- **Atajos rápidos de caja en POS**: `F5` (Efectivo), `F6` (Tarjeta), `F7` (Yape/Plin) y `Esc` (limpiar orden con confirmación destructiva), además de `Ctrl+K` en el buscador global del sidebar.
- **Tamaño mínimo de objetivos táctiles**: `>= 40px` / `>= 48px` en botones primarios desktop y `>= 48px` / `56px` en PWA (`pin-login.html` y `kds`).

---

## 4. Verificación Automatizada

El método `FlujoCompletoAcoplamientoIT.auditoriaAccesibilidadWcagYTabNavigationLoginYPos` valida automáticamente en cada build:
- Orden y capacidad de enfoque con `Tab` en `LoginView.fxml` (`txtUsername` -> `txtPassword` -> `btnLogin`).
- Presencia de `AccessibleRole.BUTTON` y `accessibleText` no vacío en los botones críticos de `LoginView.fxml` y `VentaPOSView.fxml`.
- Existencia de reglas de foco visible en `foodcore.css`.
