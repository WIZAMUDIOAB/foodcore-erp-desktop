# FoodCore ERP Desktop - Fast Food S.A.C.

Sistema ERP de escritorio para restaurantes de comida rápida bajo arquitectura de **Costo Cero ($0.00) en Red Local (LAN)**.

---

## 📌 1. Stack Tecnológico

| Componente | Tecnología Seleccionada | Versión |
| :--- | :--- | :--- |
| **Lenguaje** | Java OpenJDK / Temurin | 17 / 21 LTS |
| **Interfaz Gráfica (GUI)** | JavaFX + FXML (Scene Builder) | 21.0.2 |
| **Librería de Componentes** | AtlantaFX (Tema PrimerLight / PrimerDark) | 2.0.1 |
| **Iconografía** | Ikonli FontAwesome 5 | 12.3.1 |
| **Base de Datos** | MySQL Server (Motor InnoDB) | 8.0+ |
| **Pool de Conexiones** | HikariCP | 5.1.0 |
| **Driver BD** | MySQL Connector/J | 8.3.0 |
| **Sockets LAN** | Java Sockets TCP + Gson | Puerto 9090 |
| **Gestor de Construcción** | Apache Maven (incluye Maven Wrapper `mvnw`) | 3.9+ |

---

## 👥 2. Asignación de Roles y Funcionalidades (5 Integrantes)

Para avanzar en paralelo sin pisarse ramas ni generar conflictos de código, cada integrante asume la responsabilidad de un módulo con sus capas completas (**Vista FXML, Controlador, Servicio y DAO**):

### 🔹 Integrante 1 — Arquitectura de Datos y Persistencia Base (Líder Técnico de BD)
* **Rama:** `feature/db-core-models`
* **Responsabilidad:** Script SQL DDL de las 23 tablas en MySQL 8.0 con datos semilla (categorías, platos e insumos), configuración de `ConexionBD` con HikariCP y clases de entidad POJO (`com.foodcore.model`).

### 🔹 Integrante 2 — Módulo Punto de Venta (POS) y Arqueo de Caja
* **Rama:** `feature/pos-caja-ventas`
* **Responsabilidad:** Pantalla táctil de cobro `VentaPOSView.fxml` (IU06), cálculo de subtotales e IGV al 18%, formulario de arqueo de caja `ArqueoCajaView.fxml` (IU13), y servicio transaccional `VentaService` que ejecuta el cobro y descuenta insumos mediante recetas.

### 🔹 Integrante 3 — Módulo de Salón y Toma de Pedidos (Mozo)
* **Rama:** `feature/salon-pedidos-mozo`
* **Responsabilidad:** Selector interactivo de mesas `SalonMesasView.fxml`, formulario rápido de comanda `MozoOrderView.fxml` (IU07) con notas de preparación ("sin cremas", "extra papas"), y llamada al socket emisor para despachar la orden a cocina.

### 🔹 Integrante 4 — Monitor de Cocina (KDS) y Servidor de Sockets en Tiempo Real
* **Rama:** `feature/kds-cocina-sockets`
* **Responsabilidad:** Hilo del servidor TCP `ServidorSockets` (puerto 9090), interfaz KDS `KitchenDisplayView.fxml` (IU04/IU09), tarjetas dinámicas `ComandaCard.fxml` con alerta sonora y cambio de colores por tiempo transcurrido (verde, amarillo, rojo).

### 🔹 Integrante 5 — Seguridad (RBAC), Asistencia y Reportes Analíticos
* **Rama:** `feature/login-reportes-asistencia`
* **Responsabilidad:** Pantalla de autenticación `LoginView.fxml`, control de marcación rápida de asistencia `MarcacionAsistenciaView.fxml` (IU14), y consola gerencial `ReportesView.fxml` (IU17) con gráficos de ventas y ranking de platos de baja rotación (CU13).

---

## 🌿 3. Estrategia de Ramas (GitFlow Simplificado)

* **`main`**: Código de producción estable y probado, listo para la sustentación final.
* **`develop`**: Rama de integración donde se unen las partes de cada integrante.
* **Ramas individuales de trabajo (parten siempre de `develop`):**
  * `feature/db-core-models` (Integrante 1)
  * `feature/pos-caja-ventas` (Integrante 2)
  * `feature/salon-pedidos-mozo` (Integrante 3)
  * `feature/kds-cocina-sockets` (Integrante 4)
  * `feature/login-reportes-asistencia` (Integrante 5)

---

## 💻 4. Requisitos de Instalación en la PC de Cada Integrante

1. **Java Development Kit (JDK 17 o 21 LTS):** Descargar e instalar [Eclipse Temurin JDK](https://adoptium.net/) configurando la variable de entorno `JAVA_HOME`.
2. **Visual Studio Code:** Instalar junto a:
   * *Extension Pack for Java* (Microsoft).
   * *Maven for Java*.
3. **Gluon Scene Builder:** Instalador oficial para abrir y maquetar los archivos `.fxml` visualmente con doble clic.
4. **MySQL Server 8.0 y MySQL Workbench:** Para levantar la base de datos local `foodcore_db` con usuario `root`.
5. **Git:** Cliente de consola para clonar el repositorio y sincronizar ramas.

---

## 🗄️ 5. Puesta en Marcha de la Base de Datos

1. Abre **MySQL Workbench** o tu cliente MySQL favorito.
2. Abre y ejecuta el archivo:
   ```
   database/schema.sql
   ```
3. Esto creará la base de datos `foodcore_db` con las 23 tablas y datos semilla iniciales (roles, usuarios, clientes, productos, insumos y recetas).
4. Verifica tus credenciales en `src/main/resources/database.properties`:
   ```properties
   db.host=localhost
   db.port=3306
   db.name=foodcore_db
   db.user=root
   db.password=root
   ```

---

## 🚀 6. Cómo Ejecutar el Proyecto

No es necesario tener Maven instalado globalmente; el repositorio incluye el **Maven Wrapper oficial**:

### En Windows (PowerShell / CMD):
```powershell
.\mvnw.cmd javafx:run
```

### En Linux / macOS:
```bash
./mvnw javafx:run
```

O desde Visual Studio Code: presiona `Ctrl + Shift + B` y selecciona **"Ejecutar FoodCore ERP (javafx:run)"**.

---

## 📁 7. Estructura de Paquetes Oficial

```
foodcore-erp-desktop/
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/
├── .vscode/
│   ├── settings.json
│   ├── launch.json
│   └── tasks.json
├── database/
│   └── schema.sql                  <- Script DDL de 23 tablas + Seeds
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── foodcore/
        │           ├── App.java                <- Main JavaFX con AtlantaFX
        │           ├── model/                  <- POJOs de las 23 entidades
        │           ├── dao/                    <- Interfaces DAO y DAOImpl JDBC
        │           ├── service/                <- Lógica de negocio y transacciones ACID
        │           ├── controller/             <- Controladores FXML JavaFX
        │           ├── util/                   <- ConexionBD (HikariCP), ServidorSockets
        │           └── exception/              <- NegocioException
        └── resources/
            ├── database.properties             <- Configuración de MySQL y Pool LAN
            ├── css/
            │   └── style.css                   <- Estilos personalizados
            └── com/
                └── foodcore/
                    └── view/                   <- Archivos FXML para Scene Builder
                        ├── LoginView.fxml
                        ├── MainDashboardView.fxml
                        ├── VentaPOSView.fxml
                        ├── ArqueoCajaView.fxml
                        ├── SalonMesasView.fxml
                        ├── MozoOrderView.fxml
                        ├── KitchenDisplayView.fxml
                        ├── ComandaCard.fxml
                        ├── MarcacionAsistenciaView.fxml
                        └── ReportesView.fxml
```

---

## 🤖 8. Prompts para Asistentes de IA (Copiar y Usar)

### Prompt de Contexto Maestro (Para Todo el Equipo)
```plaintext
Actúa como un Arquitecto de Software Senior especializado en Java, JavaFX y Bases de Datos Relacionales. Estamos desarrollando el sistema ERP de escritorio "FoodCore" para el restaurante "Fast Food S.A.C." bajo un modelo de Costo Cero ($0.00) en Red Local (LAN).

REGLAS ESTRICTAS DE ARQUITECTURA:
1. Stack: Java 17+, JavaFX 21 (FXML con Scene Builder), tema AtlantaFX (PrimerLight o PrimerDark), MySQL Server 8.0 (motor InnoDB) y pool HikariCP.
2. Estructura de paquetes:
   - com.foodcore.model (entidades POJO)
   - com.foodcore.dao (interfaces DAO y DAOImpl con PreparedStatement y ResultSet)
   - com.foodcore.service (lógica de negocio y transacciones conn.setAutoCommit(false))
   - com.foodcore.controller (controladores JavaFX @FXML)
   - com.foodcore.util (ConexionBD Singleton, ServidorSockets)
   - com.foodcore.exception (NegocioException para validaciones)
3. Reglas operativas:
   - Ningún controlador de interfaz escribe sentencias SQL directas.
   - Toda transacción compuesta (Venta + Detalle + Descuento de Insumos vía Receta) se maneja con ACID seguro (commit y rollback).
   - La comunicación salón-cocina se realiza vía Java Sockets en el puerto 9090 sin hacer polling a la base de datos.
```

### Prompt para Integrante 1 (BD, Entidades y Conexión HikariCP)
```plaintext
[Usa el Contexto Maestro de FoodCore]
Eres el Integrante 1 del equipo. Tu responsabilidad es la base de datos y la persistencia fundamental.
TAREAS A GENERAR:
1. Genera el script SQL DDL completo de MySQL 8.0 InnoDB para la base de datos foodcore_db con las 23 tablas normalizadas (rol, usuario, empleado, categoria, producto, cliente, pedido, detalle_pedido, comanda, venta, detalle_venta, comprobante_pago, caja, movimiento_caja, insumo, receta, proveedor, compra, detalle_compra, movimiento_inventario, asistencia, planilla, costo_operativo).
2. Agrega datos semilla (INSERT) con 4 roles, 4 usuarios, 3 categorías, 6 platos de comida rápida, sus insumos y recetas de composición en gramos/unidades.
3. Escribe la clase ConexionBD.java bajo patrón Singleton utilizando com.zaxxer.hikari.HikariDataSource con parámetros optimizados para LAN local.
4. Genera las entidades POJO en com.foodcore.model con constructores, getters y setters para Producto, Insumo, Receta, Pedido, Venta y Caja.
```

### Prompt para Integrante 2 (Punto de Venta POS y Arqueo de Caja)
```plaintext
[Usa el Contexto Maestro de FoodCore]
Eres el Integrante 2 del equipo. Tu responsabilidad es el Módulo de Ventas y Caja.
TAREAS A GENERAR:
1. Diseña el archivo FXML (VentaPOSView.fxml) para una terminal táctil dividida en dos paneles: catálogo con TilePane de productos y comanda activa en TableView con botones grandes de cobro (Efectivo [F5], Tarjeta [F6], Yape/Plin [F7]) y desglose de subtotal, IGV (18%) y total.
2. Implementa VentaController.java para capturar clics, añadir productos a la venta activa y abrir el diálogo de confirmación de cobro.
3. Implementa VentaService.java con el método transaccional registrarVenta(Venta v, List<DetalleVenta> detalles, ComprobantePago comp):
   - Valida que exista una caja en estado 'ABIERTA'.
   - Inicia conn.setAutoCommit(false).
   - Inserta la venta y el detalle.
   - Consulta la receta de cada producto y genera salidas en movimiento_inventario descontando el stock_actual de insumo.
   - Emite el comprobante y ejecuta conn.commit().
4. Diseña ArqueoCajaView.fxml y su controlador para cuadrar el monto del sistema contra el dinero físico ingresado y cerrar el turno.
```

### Prompt para Integrante 3 (Salón, Mesas y Comandas del Mozo)
```plaintext
[Usa el Contexto Maestro de FoodCore]
Eres el Integrante 3 del equipo. Tu responsabilidad es el Módulo de Atención y Salón.
TAREAS A GENERAR:
1. Diseña SalonMesasView.fxml con una cuadrícula visual de mesas (estados: LIBRE en verde, OCUPADA en rojo).
2. Diseña MozoOrderView.fxml para la captura táctil de pedidos: lista de platos con botón (+ / -), campo para indicaciones especiales ("sin mayonesa", "extra papas") y número de mesa.
3. Implementa PedidoController.java para guardar el pedido en estado 'REGISTRADO'.
4. Agrega el método enviarComandaACocina() que guarda la comanda en la tabla comanda y dispara un socket cliente TCP al puerto 9090 enviando un mensaje JSON: {"accion": "NUEVA_COMANDA", "idPedido": 12, "mesa": "04", "items": [...]}.
```

### Prompt para Integrante 4 (Monitor KDS de Cocina y Servidor Sockets)
```plaintext
[Usa el Contexto Maestro de FoodCore]
Eres el Integrante 4 del equipo. Tu responsabilidad es la Estación de Cocina KDS y la comunicación en tiempo real.
TAREAS A GENERAR:
1. Crea la clase ServidorSockets.java en com.foodcore.util que escuche en el puerto TCP 9090 en un hilo independiente (ExecutorService) y mantenga la lista de clientes conectados (pantallas de cocina y tablets).
2. Diseña KitchenDisplayView.fxml en pantalla completa con un FlowPane o HBox contenedor de tarjetas de comandas.
3. Diseña el componente visual ComandaCard.fxml: encabezado con número de pedido, mesa, tiempo transcurrido con temporizador visual, lista de platos con sus notas y dos botones táctiles: "Iniciar Preparación" y "Plato Listo / Despachar".
4. Implementa KitchenDisplayController.java para que reciba las notificaciones del Socket en segundo plano y use Platform.runLater() para renderizar la tarjeta al instante con sonido de notificación.
```

### Prompt para Integrante 5 (Login RBAC, Asistencia y Reportes)
```plaintext
[Usa el Contexto Maestro de FoodCore]
Eres el Integrante 5 del equipo. Tu responsabilidad es la Seguridad, Asistencia y Reportes Analíticos.
TAREAS A GENERAR:
1. Diseña LoginView.fxml con estilo moderno de AtlantaFX, campos de usuario y contraseña con icono FontAwesome.
2. Implementa LoginController.java y AuthService.java verificando credenciales en la tabla usuario y cargando la vista correspondiente según el rol del usuario (Gerente, Administrador, Mozo, Cocina).
3. Diseña MarcacionAsistenciaView.fxml: teclado numérico en pantalla para digitar el DNI del trabajador y registrar ingreso/salida con hora exacta en la tabla asistencia.
4. Diseña ReportesView.fxml con gráficos de JavaFX (BarChart de ventas por día y PieChart de productos de baja rotación CU13) alimentados por consultas de agregación SQL desde ReporteService.java.
```
