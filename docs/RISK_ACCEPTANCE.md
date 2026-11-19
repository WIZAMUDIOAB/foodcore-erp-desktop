# REGISTRO FORMAL DE ACEPTACION DE RIESGOS — FoodCore ERP `v5.7.1-GA`

> **Alcance:** Riesgos residuales operativos y de arquitectura evaluados para el lanzamiento endurecido **FoodCore ERP `v5.7.1-GA`**. Cada riesgo listado cuenta con controles compensatorios obligatorios, condiciones de revocacion automatica y criterios de verificacion previos a produccion.

---

## Matriz de Riesgos Aceptados (`v5.7.1-GA`)

### R1 (`R-KDS-TLS-01`) — Canal Interno KDS TCP `9090` sin TLS en LAN Aislada
- **Estado:** Aceptado condicionalmente para despliegue LAN cableada aislada (`v5.7.1-GA`)
- **Descripcion del riesgo:** El canal interno TCP `9090` (`ServidorSockets.java`) transmite eventos de comandas KDS (mesa, producto, cantidad, notas de preparacion) sin cifrado TLS (`socket.kds.tls.enabled=false`).
- **Controles compensatorios vigentes (`v5.7.1-GA`):**
  - Registro de clientes en `clientesConectados` **exclusivamente** tras completar autenticacion de terminal `HELLO`/`AUTH_TERMINAL` con `sessionToken` vigente (expiracion verificada en `TerminalAuthService`).
  - Firma `HMAC-SHA256` por evento (`ServidorSockets.procesarTramaProtocolo`) y rechazo de tramas no autenticadas o desconocidas.
  - Lectura acotada por bytes en tiempo real con limite estricto `MAX_FRAME_BYTES = 1 MB` (proteccion anti-OOM), pool acotado de hilos + semaforo de concurrencia (`20`) y `socket.setSoTimeout(30_000)`.
  - Ausencia de PII de clientes o credenciales de pago en el payload de comandas KDS.
  - **Obligatorio:** Puerto `9090` restringido por firewall de host a loopback (`127.0.0.1`) en despliegues monopuesto o a subred LAN cableada aislada POS/KDS. **Prohibido exponer `9090` a Wi-Fi de invitados o Internet.**
- **Condicion de revocacion:** Si el puerto `9090` queda accesible fuera de la subred cableada POS/KDS, esta aceptacion queda revocada de inmediato.
- **Plan de remediacion futura:** Evaluar `SSLServerSocket`/`SSLSocket` con mTLS o truststore interno en rama mayor posterior (estimacion: ~180–260 LOC + aprovisionamiento de certificados en terminales KDS).

---

### R2 (`R-BACKUP-ENC-02`) — Respaldos Comprimidos `.sql.gz` Protegidos por ACL de Sistema de Archivos
- **Estado:** Aceptado condicionalmente para servidor dedicado (`v5.7.1-GA`)
- **Descripcion del riesgo:** Los respaldos generados por `scripts/backup-auto.ps1`, `scripts/backup.ps1`, `scripts/backup-auto.sh` y `scripts/backup.sh` se almacenan comprimidos en formato `.sql.gz` sin cifrado a nivel de aplicacion (PGP/age), dependiendo del cifrado de volumen del host (BitLocker/LUKS) y de permisos restrictivos del sistema de archivos.
- **Controles compensatorios vigentes (`v5.7.1-GA`):**
  - Eliminacion total de contrasenas embebidas y paso de credenciales MySQL exclusivamente via `--defaults-extra-file` temporal (`0600` en Linux, ACL `owner-only` via `icacls` en Windows) destruido en bloque `finally`/`trap`.
  - Bloqueo de permisos ACL en `backups/` y cada archivo `.sql.gz` (`SYSTEM`, `Administrators` y propietario en Windows; `chmod 700`/`600` en Linux) y bloqueo de ACL en `scripts/` antes de registrar la tarea programada diaria (`03:00 AM`).
  - Purga automatica por retencion (`> 30 dias`) y exclusion de `backups/` del control de versiones (`.gitignore`).
  - **Obligatorio:** Activar cifrado de disco completo (BitLocker en Windows / LUKS en Linux) en el servidor anfitrion y cifrar cualquier copia que salga del servidor hacia almacenamiento externo/nube.
- **Condicion de revocacion:** Almacenar archivos `.sql.gz` en carpetas compartidas de red, USB sin cifrar o repositorios Git revoca esta aceptacion.

---

### R4 (`R-TLS-LABCERT-04`) — Certificado Autofirmado de Laboratorio `foodcore.cer` en Repositorio de Desarrollo
- **Estado:** Aceptado solo para laboratorio/desarrollo local; **excluido de paquetes de produccion (`v5.7.1-GA`)**
- **Descripcion del riesgo:** El repositorio historico contiene un certificado publico X.509 autofirmado de laboratorio (`foodcore.cer`, sujeto `CN=localhost`, sin clave privada). Su reutilizacion en produccion provocaria advertencias TLS o suplantacion de identidad en la red LAN.
- **Controles compensatorios vigentes (`v5.7.1-GA`):**
  - `scripts/package-release.ps1`, `scripts/build-deb.sh` y `pom.xml` **excluyen** el certificado de laboratorio `foodcore.cer` y `seed_full_operational.sql` de los artefactos de distribucion de produccion.
  - `.gitignore` bloquea nuevos archivos `*.cer`, `*.jks`, `*.p12` y `*.keystore`.
  - `ApiServer` exige `FOODCORE_KEYSTORE_PASS` (>= 8 caracteres, fail-fast) y soporta `FOODCORE_KEYSTORE_PATH` / `FOODCORE_CERT_PATH` propios por sede, documentados en `keystore.jks.example`.
  - **Obligatorio:** Cada restaurante debe generar su propio par de claves y certificado (`keytool`) con SAN correspondiente a su IP/hostname LAN antes de distribuir `foodcore.cer` a las tablets PWA.
- **Condicion de revocacion:** Desplegar el certificado de laboratorio (`CN=localhost`) en produccion revoca esta aceptacion.

---

### R5 (`R-TUNNEL-PUBMENU-05`) — Exposicion Publica via Cloudflare Tunnel Limitada Exclusivamente al Menu Digital QR
- **Estado:** Aceptado condicionalmente con aislamiento estricto de conector y cabeceras (`v5.7.1-GA`)
- **Descripcion del riesgo:** Cuando se habilita el tunel `cloudflared` (`scripts/start-tunnel.ps1` / `TunnelService.java`), el servidor recibe trafico desde Internet a traves de Cloudflare para que los comensales escaneen la carta digital QR sin conectarse al Wi-Fi interno.
- **Controles compensatorios vigentes (`v5.7.1-GA`):**
  - Conector dedicado exclusivamente en loopback (`127.0.0.1:8081` por defecto) que expone **unicamente** las rutas publicas de solo lectura del menu digital (`/api/public/*`, `/menu-digital/*`, `/fonts/*`, `/uploads/platillos/*` confinado contra LFI).
  - Defensa en profundidad en `ApiSecurityFilter`: rechaza explicitamente cualquier ruta administrativa, POS, KDS, caja o autenticacion si llegan cabeceras de proxy Cloudflare (`CF-Ray` o `CF-Connecting-IP`).
  - `scripts/cloudflared.exe` excluido del repositorio Git (`scripts/*.exe` en `.gitignore`); el binario debe aprovisionarse desde la fuente oficial verificada de Cloudflare.
  - **Obligatorio:** Apuntar `cloudflared` unicamente al conector loopback de menu publico; nunca exponer los puertos `8080`, `8443`, `9090` ni `3306` a Internet.
- **Condicion de revocacion:** Apuntar el tunel al puerto principal de API/POS (`8080`/`8443`) o deshabilitar el filtro de cabeceras Cloudflare revoca esta aceptacion de inmediato.

---

## Verificacion Operativa Pre-Despliegue (`v5.7.1-GA`)

Antes de cada despliegue en produccion, el responsable de IT/Operaciones debe confirmar:

```powershell
# 1. Firewall puerto 9090 restringido a LAN POS/KDS (R1)
Get-NetFirewallRule -DisplayName "*FoodCore*9090*" | Format-Table DisplayName, Enabled, Direction, Action

# 2. ACLs restrictivas en scripts/ y backups/ (R2)
icacls .\scripts
icacls .\backups

# 3. Verificacion de TLS obligatorio y local_infile=0 en MySQL (config/my.ini)
# require_secure_transport=ON y local_infile=0

# 4. Verificacion de certificado propio de sede (R4) y tunel solo hacia conector publico (R5)
```

```bash
# Linux (R1 / R2)
sudo iptables -L INPUT -n | grep 9090
stat -c "%a %U:%G %n" scripts backups
```

---

## Bloque de Aprobacion Formal y Firma (`v5.7.1-GA`)

| Campo | Detalle |
|---|---|
| **Version cubierta** | `FoodCore ERP v5.7.1-GA` |
| **Riesgos cubiertos** | `R1 (R-KDS-TLS-01)`, `R2 (R-BACKUP-ENC-02)`, `R4 (R-TLS-LABCERT-04)`, `R5 (R-TUNNEL-PUBMENU-05)` |
| **Fecha de evaluacion** | `2026-11-18` |
| **Proxima revision obligatoria** | `2027-02-18` (90 dias o ante cambio de arquitectura de red) |
| **Estado de firma** | `[ ] PENDIENTE DE FIRMA` / `[ ] APROBADO` |

### Firmas de Aceptacion Requeridas para Go-Live

- **Responsable de Seguridad / AppSec:**
  - Nombre completo: `___________________________________________`
  - Cargo: `___________________________________________`
  - Firma y fecha: `___________________________` / `____-__-__`

- **Responsable de Operaciones / IT (Propietario del Sistema):**
  - Nombre completo: `___________________________________________`
  - Cargo: `___________________________________________`
  - Firma y fecha: `___________________________` / `____-__-__`
