# 🍽️ MANUAL RÁPIDO DE USUARIO — FOODCORE ERP v4.3.0
**Guía Práctica Día a Día para el Personal del Restaurante**

Bienvenido a **FoodCore ERP**. Esta guía explica paso a paso y en lenguaje sencillo cómo realizar tus tareas diarias según tu puesto en el restaurante.

---

## 1. 👔 Para el Administrador y Cajero (Computadora Principal)

### 1.1 ¿Cómo abrir el local al iniciar el día?
1. Enciende la computadora principal de caja (esta computadora conecta automáticamente todas las tablets y celulares del local).
2. Haz doble clic en el ícono **FoodCore ERP** en el escritorio.
3. Ingresa tu usuario (por ejemplo, `admin`) y tu contraseña, y presiona **Iniciar Sesión**.
4. Ve al menú lateral izquierdo y entra en **Arqueo de Caja**.
5. Haz clic en **Abrir Turno de Caja**, escribe el dinero en efectivo con el que empiezas el día (sencillo/fondo inicial, ej. `S/ 100.00`) y confirma. ¡El restaurante ya está listo para vender!

### 1.2 ¿Cómo configurar o cambiar el PIN de un Mozo?
Cada mozo tiene un código secreto de 4 a 6 números (PIN) para entrar rápido en las tablets del salón:
1. En el menú lateral izquierdo, haz clic en **Usuarios y Accesos**.
2. Busca al trabajador en la tabla (verás la columna **PIN Configurado** con un check verde ✅ si ya tiene uno).
3. Selecciona al trabajador y haz clic en el botón **Configurar PIN**.
4. Escribe el nuevo número de 4 dígitos (por ejemplo `1234` o `2026`) y presiona **Guardar**. El cambio es inmediato.

### 1.3 ¿Cómo crear platos nuevos o marcar el "Menú del Día" / "Agotado"?
1. En el menú lateral, entra a **Carta y Menú**.
2. **Para agregar un plato nuevo:** Haz clic en **Nuevo Producto**, escribe su nombre, elige su categoría (Hamburguesas, Bebidas, etc.), pon su precio de venta, sube una foto si deseas y guarda.
3. **Para destacar un plato en "Menú del Día":** Activa el interruptor **Menú del Día** al lado del plato. Aparecerá de inmediato en la pestaña especial de las tablets de los mozos.
4. **Si un plato se acaba en cocina:** Desactiva el interruptor **Disponible** (quedará como **Agotado**). En menos de 30 segundos, las tablets de los mozos bloquearán ese plato para evitar que lo vendan por error.

### 1.4 ¿Cómo ver los reportes y cerrar la caja en la noche?
- **Ver ganancias y mermas:** Entra a **Reportes Avanzados** o **Análisis de Mermas** en el menú izquierdo. Podrás ver cuánto se vendió por categoría, qué platos salieron más y exportar el resumen en **PDF** o **Excel** con un solo clic.
- **Revisar quién atendió cada mesa:** En **Auditoría**, usa el filtro **Por Mozo** para ver todos los pedidos tomados por cada trabajador.
- **Cerrar el día:** Al terminar la jornada, entra a **Arqueo de Caja**, cuenta los billetes y monedas del cajón, escribe el monto en **Efectivo Físico Contado** y presiona **Cerrar Turno (Cierre Z)**.

---

## 2. 📱 Para el Mozo (Tablets de Salón)

### 2.1 ¿Cómo encender la tablet e ingresar con tu PIN?
1. Enciende la tablet y asegúrate de que esté conectada al Wi-Fi del restaurante.
2. Toca el ícono **FoodCore Mozo** en la pantalla de inicio (o abre el navegador en `https://IP-DEL-SERVIDOR:8443`).
3. Toca la pestaña **Ingresar con PIN**.
4. Toca tu foto/nombre en la lista de mozos y marca tu **PIN de 4 dígitos** en el teclado grande en pantalla.

### 2.2 ¿Cómo tomar un pedido y enviarlo a cocina?
1. **Elige la mesa:** Al entrar verás el plano del salón. Las mesas **Verdes** están libres y las **Naranjas** están ocupadas. Toca la mesa que vas a atender (ej. **Mesa 04**).
2. **Agrega los platos:**
   - Usa los botones superiores para filtrar por categoría (*Menú del Día*, *Hamburguesas*, *Bebidas*) o escribe en el buscador.
   - Toca el botón **+ Agregar** en cada plato que pida el cliente.
   - Si un plato dice **AGOTADO**, significa que ya no queda en cocina.
3. **Notas para cocina:** En el resumen del pedido (lado derecho), puedes escribir indicaciones especiales como *"Sin cebolla"*, *"Bien cocido"* o *"Helada"*.
4. **Enviar el pedido:** Presiona el botón verde **Confirmar y Enviar a Cocina**. ¡Listo! El pedido saldrá al instante en la pantalla de los cocineros.
   - *Nota:* Si el Wi-Fi falla por un momento, no te preocupes: la tablet guarda el pedido automáticamente y lo envía sola apenas vuelva la señal.

### 2.3 ¿Compartes la tablet con otro compañero? (Cambio rápido de mozo)
- No necesitas cerrar todo ni borrar el pedido que estás armando.
- Toca el botón **Cambiar Mozo** arriba a la derecha, ingresa tu PIN de 4 dígitos y el pedido quedará a tu nombre al instante.

---

## 3. 👨‍🍳 Para la Cocina (Pantalla KDS en Pared)

### 3.1 ¿Cómo leer las comandas que llegan?
En la tablet grande de cocina (`https://IP-DEL-SERVIDOR:8443/kds`) verás la pantalla dividida en **3 columnas**:
1. **PENDIENTE (Izquierda):** Pedidos nuevos que acaban de enviar los mozos.
2. **EN PREPARACIÓN (Centro):** Platos que ya están en el fuego o armándose.
3. **LISTO PARA RECOGER (Derecha):** Platos terminados esperando que el mozo los lleve a la mesa.

### 3.2 ¿Qué significan los colores del reloj en cada pedido?
Cada tarjeta tiene un cronómetro automático para ayudarte a priorizar:
- 🟢 **Verde (menos de 5 min):** Pedido recién llegado, en tiempo óptimo.
- 🟡 **Ámbar (de 5 a 10 min):** Atención, el cliente ya lleva varios minutos esperando.
- 🔴 **Rojo parpadeante (más de 10 min):** **¡Prioridad urgente!** Atender de inmediato.

### 3.3 ¿Cómo avanzar un plato hasta marcarlo como "Listo"?
1. Cuando empieces a cocinar un plato de la columna **Pendiente**, toca su botón grande **🔥 Iniciar**. El plato pasará al centro (*En Preparación*).
2. Cuando el plato esté servido en el pasaplatos, toca el botón verde **✅ Marcar Listo**. Pasará a la columna derecha (*Listo*).
3. Cuando el mozo recoja el plato para llevarlo a la mesa, toca **🍽️ Entregado** para limpiar la tarjeta de la pantalla.

---

## 4. 📊 Para el Dueño o Gerente (Desde el Celular)

### 4.1 ¿Cómo abrir el Dashboard en tu celular?
1. Conecta tu celular al Wi-Fi del restaurante.
2. Abre el navegador de tu teléfono (Chrome en Android o Safari en iPhone) e ingresa a:
   `https://IP-DEL-SERVIDOR:8443/dashboard`
   *(Tip: También puedes entrar desde la pantalla principal tocando el botón **📊 Dashboard**).*
3. Para tenerlo como una aplicación en tu celular, abre el menú del navegador y elige **"Agregar a la pantalla de inicio"**.

### 4.2 ¿Qué información puedes ver en vivo?
Apenas abras el Dashboard en tu celular verás 4 tarjetas principales actualizadas al instante:
- **Ventas del Día:** Cuánto dinero va facturando el restaurante hoy y el porcentaje de mejora respecto a ayer.
- **Ticket Promedio:** Cuánto gasta en promedio cada mesa atendida.
- **Transacciones:** Cantidad total de ventas cobradas en el día.
- **Plato Estrella:** El plato más pedido del turno y cuántas porciones lleva vendidas.

Más abajo podrás deslizar con el dedo para revisar:
- **Curva de Ventas por Hora:** Gráfico suave que muestra cuáles fueron las horas punta del almuerzo y la cena.
- **Ocupación del Salón:** Cuántas mesas están ocupadas y libres en este preciso momento.
- **Top 5 Platos y Últimas Ventas:** Los platos que más dinero generan hoy y los últimos comprobantes emitidos en caja.
- **Menú Lateral (☰):** Toca las tres líneas arriba a la izquierda para saltar rápidamente al Mapa de Mesas, al KDS de Cocina o al Catálogo.
