# RISK ACCEPTANCE — R-KDS-TLS-01

## Identificación
- **ID:** R-KDS-TLS-01
- **Fecha de aceptación:** 2026-11-11
- **Aceptado por:** [NOMBRE DEL RESPONSABLE DE SEGURIDAD]
- **Revisión obligatoria antes de:** 2026-12-11 (30 días)

## Descripción del riesgo
El canal interno TCP 9090 (`ServidorSockets.java`) transmite eventos KDS (mesa, producto, cantidad, notas) sin cifrado TLS. `socket.kds.tls.enabled=false`.

## Controles compensatorios vigentes
- Autenticación de terminal por `HELLO`/`AUTH_TERMINAL`.
- HMAC-SHA256 por evento (`ServidorSockets.procesarTramaProtocolo`).
- Límite de trama `MAX_FRAME_BYTES = 1 MB`.
- Ausencia de PII de clientes en payload de comanda.
- **Obligatorio:** puerto 9090 restringido por firewall de host a loopback (`127.0.0.1`) o a subred LAN cableada aislada POS/KDS. **Prohibido exponer 9090 a Wi-Fi de invitados o Internet.**

## Condiciones de incumplimiento
Si el puerto 9090 se expone fuera de la subred aislada, esta aceptación queda **revocada** y el riesgo debe re-evaluarse.

## Plan de remediación
Implementar `SSLServerSocket`/`SSLSocket` con pinning o truststore interno en v4.4.x. Estimación: ~180–260 líneas + distribución de truststore a terminales POS/KDS.

## Verificación operativa
Antes de cada despliegue, el responsable de IT debe confirmar que la regla de firewall está activa:
```powershell
# Windows
Get-NetFirewallRule -DisplayName "*FoodCore*9090*" | Format-Table DisplayName, Enabled, Direction, Action
# Linux
sudo iptables -L INPUT -n | grep 9090
```
