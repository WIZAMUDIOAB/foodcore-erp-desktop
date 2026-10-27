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
