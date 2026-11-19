/**
 * Motor Local-First IndexedDB — FoodCore ERP v4.0 Tablet PWA (SPEC-FOODCORE-v4.0 §4)
 * Almacena pedidos en la tabla `outbox_pedidos` con estado `PENDIENTE` cuando la API
 * no responde y conserva copia local de catálogo y mesas para operación ininterrumpida.
 */
const FoodCoreOfflineDB = (() => {
  const DB_NAME = 'foodcore_pwa_db';
  const DB_VERSION = 1;
  const STORE_OUTBOX = 'outbox_pedidos';
  const STORE_PRODUCTOS = 'cache_productos';
  const STORE_MESAS = 'cache_mesas';

  let dbInstance = null;

  function abrirDB() {
    if (dbInstance) {
      return Promise.resolve(dbInstance);
    }
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);

      request.onupgradeneeded = (event) => {
        const db = event.target.result;

        // Tabla principal exigida por SPEC §4: outbox_pedidos
        if (!db.objectStoreNames.contains(STORE_OUTBOX)) {
          const outboxStore = db.createObjectStore(STORE_OUTBOX, { keyPath: 'uuid_local' });
          outboxStore.createIndex('idx_estado', 'estado', { unique: false });
          outboxStore.createIndex('idx_creado_en', 'creado_en', { unique: false });
        }

        // Caché local de catálogo para toma de pedidos 100% offline
        if (!db.objectStoreNames.contains(STORE_PRODUCTOS)) {
          db.createObjectStore(STORE_PRODUCTOS, { keyPath: 'id' });
        }

        // Caché local de mesas
        if (!db.objectStoreNames.contains(STORE_MESAS)) {
          db.createObjectStore(STORE_MESAS, { keyPath: 'numero' });
        }
      };

      request.onsuccess = (event) => {
        dbInstance = event.target.result;
        resolve(dbInstance);
      };

      request.onerror = (event) => {
        reject(event.target.error);
      };
    });
  }

  /**
   * Guarda un pedido en `outbox_pedidos` con estado `PENDIENTE`.
   */
  async function guardarPedidoOutbox(pedido) {
    const db = await abrirDB();
    return new Promise((resolve, reject) => {
      const tx = db.transaction([STORE_OUTBOX], 'readwrite');
      const store = tx.objectStore(STORE_OUTBOX);
      const registro = {
        ...pedido,
        uuid_local: pedido.uuid_local,
        estado: 'PENDIENTE',
        creado_en: pedido.creado_en || new Date().toISOString()
      };
      const req = store.put(registro);
      req.onsuccess = () => resolve(registro);
      req.onerror = (e) => reject(e.target.error);
    });
  }

  /**
   * Lista todos los pedidos en `outbox_pedidos` con estado `PENDIENTE`.
   */
  async function obtenerPedidosPendientes() {
    const db = await abrirDB();
    return new Promise((resolve, reject) => {
      const tx = db.transaction([STORE_OUTBOX], 'readonly');
      const store = tx.objectStore(STORE_OUTBOX);
      const req = store.getAll();
      req.onsuccess = () => {
        const todos = req.result || [];
        resolve(todos.filter((p) => p.estado === 'PENDIENTE'));
      };
      req.onerror = (e) => reject(e.target.error);
    });
  }

  /**
   * Elimina de IndexedDB un pedido que ya fue enviado exitosamente a la API.
   */
  async function eliminarPedidoOutbox(uuidLocal) {
    const db = await abrirDB();
    return new Promise((resolve, reject) => {
      const tx = db.transaction([STORE_OUTBOX], 'readwrite');
      const store = tx.objectStore(STORE_OUTBOX);
      const req = store.delete(uuidLocal);
      req.onsuccess = () => resolve(true);
      req.onerror = (e) => reject(e.target.error);
    });
  }

  async function guardarCatalogoCache(productos) {
    const db = await abrirDB();
    return new Promise((resolve) => {
      const tx = db.transaction([STORE_PRODUCTOS], 'readwrite');
      const store = tx.objectStore(STORE_PRODUCTOS);
      store.clear();
      (Array.isArray(productos) ? productos : []).forEach((p) => store.put(p));
      tx.oncomplete = () => resolve(true);
      tx.onerror = () => resolve(false);
    });
  }

  async function obtenerCatalogoCache() {
    const db = await abrirDB();
    return new Promise((resolve) => {
      const tx = db.transaction([STORE_PRODUCTOS], 'readonly');
      const store = tx.objectStore(STORE_PRODUCTOS);
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => resolve([]);
    });
  }

  async function guardarMesasCache(mesas) {
    const db = await abrirDB();
    return new Promise((resolve) => {
      const tx = db.transaction([STORE_MESAS], 'readwrite');
      const store = tx.objectStore(STORE_MESAS);
      store.clear();
      (Array.isArray(mesas) ? mesas : []).forEach((m) => store.put(m));
      tx.oncomplete = () => resolve(true);
      tx.onerror = () => resolve(false);
    });
  }

  async function obtenerMesasCache() {
    const db = await abrirDB();
    return new Promise((resolve) => {
      const tx = db.transaction([STORE_MESAS], 'readonly');
      const store = tx.objectStore(STORE_MESAS);
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => resolve([]);
    });
  }

  async function limpiarBaseDatos() {
    if (dbInstance) {
      try {
        await new Promise((resolve) => {
          const tx = dbInstance.transaction([STORE_OUTBOX, STORE_PRODUCTOS, STORE_MESAS], 'readwrite');
          tx.objectStore(STORE_OUTBOX).clear();
          tx.objectStore(STORE_PRODUCTOS).clear();
          tx.objectStore(STORE_MESAS).clear();
          tx.oncomplete = () => resolve(true);
          tx.onerror = () => resolve(false);
          tx.onabort = () => resolve(false);
        });
      } catch (_) {}
      try {
        dbInstance.close();
      } catch (_) {}
      dbInstance = null;
    }
    return new Promise((resolve) => {
      try {
        const req = indexedDB.deleteDatabase(DB_NAME);
        req.onsuccess = () => resolve(true);
        req.onerror = () => resolve(false);
        req.onblocked = () => resolve(false);
      } catch (_) {
        resolve(false);
      }
    });
  }

  return {
    abrirDB,
    guardarPedidoOutbox,
    obtenerPedidosPendientes,
    eliminarPedidoOutbox,
    guardarCatalogoCache,
    obtenerCatalogoCache,
    guardarMesasCache,
    obtenerMesasCache,
    limpiarBaseDatos
  };
})();
