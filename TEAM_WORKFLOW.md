# Guía de Trabajo en Equipo - GitFlow y Desarrollo FoodCore ERP

Esta guía explica paso a paso cómo cada integrante debe clonar el proyecto, trabajar en su rama correspondiente y coordinar con el equipo para evitar conflictos de código.

---

## 1. Clonar el Repositorio

Abre una terminal (PowerShell o Git Bash) y ejecuta:

```bash
git clone <URL_DEL_REPOSITORIO_GITHUB>
cd foodcore-erp-desktop
```

---

## 2. Cambiar a tu Rama Asignada

El repositorio ya tiene creadas las 5 ramas de trabajo individuales basadas en `develop`. Localiza tu rol y ejecuta el comando respectivo:

* **Integrante 1 (BD, Entidades y Conexión):**
  ```bash
  git checkout feature/db-core-models
  ```

* **Integrante 2 (Punto de Venta POS y Arqueo):**
  ```bash
  git checkout feature/pos-caja-ventas
  ```

* **Integrante 3 (Salón, Mesas y Mozo):**
  ```bash
  git checkout feature/salon-pedidos-mozo
  ```

* **Integrante 4 (Monitor KDS y Sockets):**
  ```bash
  git checkout feature/kds-cocina-sockets
  ```

* **Integrante 5 (Login, Asistencia y Reportes):**
  ```bash
  git checkout feature/login-reportes-asistencia
  ```

---

## 3. Abrir el Proyecto en Visual Studio Code

```bash
code .
```

VS Code detectará automáticamente la configuración Java, Maven y JavaFX gracias a la carpeta `.vscode/`.

---

## 4. Edición Visual de Pantallas con Scene Builder

Para modificar cualquiera de los archivos `.fxml` (`src/main/resources/com/foodcore/view/`):
1. Abre el explorador de archivos de Windows o macOS.
2. Navega a `src/main/resources/com/foodcore/view/`.
3. Haz doble clic sobre el archivo `.fxml` que te corresponde (por ejemplo `VentaPOSView.fxml`).
4. Se abrirá automáticamente en **Gluon Scene Builder**.
5. Realiza tus modificaciones visuales, guarda (`Ctrl + S`) y vuelve a VS Code.

---

## 5. Probar y Ejecutar la Aplicación

No necesitas instalar Maven por separado. Usa el Maven Wrapper incluido:

```powershell
.\mvnw.cmd javafx:run
```

O presiona `Ctrl + Shift + B` en VS Code y elige `Ejecutar FoodCore ERP (javafx:run)`.

---

## 6. Guardar y Subir Cambios a GitHub

Cuando hayas completado tus tareas o avances diarios:

```bash
# 1. Verificar archivos modificados
git status

# 2. Agregar cambios al área de preparación
git add .

# 3. Crear commit descriptivo
git commit -m "feat(modulo): descripcion clara del avance realizado"

# 4. Subir a tu rama en GitHub
git push origin <nombre-de-tu-rama>
```

---

## 7. Integración en la Rama `develop` (Fin de Sprint)

1. Una vez que tu funcionalidad esté probada localmente, solicita un Pull Request (PR) en GitHub desde tu rama `feature/...` hacia la rama `develop`.
2. El Líder Técnico (Integrante 1) o el equipo revisa el código y aprueba el merge.
3. Cada integrante debe actualizar su rama con los cambios de `develop` ejecutando:
   ```bash
   git checkout develop
   git pull origin develop
   git checkout feature/<tu-rama>
   git merge develop
   ```
