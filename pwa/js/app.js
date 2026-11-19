/**
 * Controlador Principal Frontend — FoodCore ERP v5.7.1 Tablet PWA
 * Gestiona autenticación JWT, selección de mesas, toma de comanda táctil, envío a KDS
 * y sincronización automática segura desde IndexedDB (`outbox_pedidos`).
 */
const FoodCorePWA = (() => {
  const THEME_STORAGE_KEY = 'fc_theme';
  const SVG_NS = 'http://www.w3.org/2000/svg';
  const PIN_AUTO_SUBMIT_DELAY_MS = 450;

  const API_BASE = (window.location.origin && window.location.origin !== 'null' && !window.location.origin.startsWith('file://'))
    ? `${window.location.origin}/api`
    : 'https://localhost:8443/api';

  function crearSvgElemento(tag, attrs = {}) {
    const el = document.createElementNS(SVG_NS, tag);
    Object.entries(attrs).forEach(([k, v]) => el.setAttribute(k, String(v)));
    return el;
  }

  function crearIconoTemaSvg(esOscuro) {
    const svg = crearSvgElemento('svg', {
      class: 'ui-icon',
      viewBox: '0 0 24 24',
      fill: 'none',
      stroke: 'currentColor',
      'stroke-width': '2',
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round',
      'aria-hidden': 'true'
    });
    if (esOscuro) {
      svg.appendChild(crearSvgElemento('circle', { cx: '12', cy: '12', r: '4' }));
      ['M12 2v2', 'M12 20v2', 'm4.93 4.93 1.41 1.41', 'm17.66 17.66 1.41 1.41',
        'M2 12h2', 'M20 12h2', 'm6.34 17.66-1.41 1.41', 'm19.07 4.93-1.41 1.41'].forEach((d) => {
        svg.appendChild(crearSvgElemento('path', { d }));
      });
    } else {
      svg.appendChild(crearSvgElemento('path', { d: 'M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z' }));
    }
    return svg;
  }

  /**
   * Valida estrictamente el esquema de imagen_url:
   * permite únicamente rutas relativas (/uploads/... o /...) o URLs http:// / https://.
   */
  function esUrlImagenSegura(url) {
    if (typeof url !== 'string') return false;
    const trimmed = url.trim();
    if (!trimmed || trimmed === 'null' || trimmed === 'undefined') return false;
    if (/^\/(?!\/)/.test(trimmed)) {
      return !trimmed.includes('..');
    }
    if (!/^https?:\/\//i.test(trimmed)) {
      return false;
    }
    try {
      const parsed = new URL(trimmed);
      return parsed.protocol === 'http:' || parsed.protocol === 'https:';
    } catch (_) {
      return false;
    }
  }

  function resolverTemaInicial() {
    try {
      const saved = localStorage.getItem(THEME_STORAGE_KEY);
      if (saved === 'dark') return true;
      if (saved === 'light') return false;
    } catch (_) {}
    return Boolean(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
  }

  function aplicarTemaPwa(esOscuro) {
    const root = document.documentElement;
    root.classList.toggle('dark', Boolean(esOscuro));
    root.setAttribute('data-theme', esOscuro ? 'dark' : 'light');
    if (document.body) {
      document.body.classList.toggle('dark', Boolean(esOscuro));
    }
    const metaTheme = document.querySelector('meta[name="theme-color"]');
    if (metaTheme) {
      metaTheme.setAttribute('content', esOscuro ? '#09090B' : '#F9F7F4');
    }
    const btn = document.getElementById('btnThemeToggle');
    if (btn) {
      btn.replaceChildren(crearIconoTemaSvg(esOscuro));
      btn.setAttribute('aria-pressed', esOscuro ? 'true' : 'false');
    }
  }

  function inicializarTemaPwa() {
    const oscuro = resolverTemaInicial();
    aplicarTemaPwa(oscuro);
    const btn = document.getElementById('btnThemeToggle');
    if (btn && !btn.dataset.themeBound) {
      btn.dataset.themeBound = '1';
      btn.addEventListener('click', alternarTemaPwa);
    }
  }

  function alternarTemaPwa() {
    const actualOscuro = document.documentElement.classList.contains('dark');
    const siguiente = !actualOscuro;
    try {
      localStorage.setItem(THEME_STORAGE_KEY, siguiente ? 'dark' : 'light');
    } catch (_) {}
    aplicarTemaPwa(siguiente);
  }

  aplicarTemaPwa(resolverTemaInicial());

  function getAuthHeaders(extra = {}) {
    const token = localStorage.getItem('fc_pwa_token');
    const headers = { ...extra };
    if (token && token.trim() !== '') {
      headers.Authorization = `Bearer ${token.trim()}`;
    }
    return headers;
  }

  let catalogoProductos = [];
  let listaMesas = [];
  let carrito = [];
  let categoriaActiva = 'TODOS';
  let textoBusqueda = '';
  let itemSeleccionadoId = null;
  let modoOfflineSimulado = false;
  let estadoOnline = true;
  let syncEnProgreso = false;

  const CATALOGO_FALLBACK = [
    {
      id: 1,
      codigo: 'HAM-01',
      nombre: 'Hamburguesa Royal Doble',
      descripcion: 'Doble carne angus 180g, huevo, queso cheddar y papas',
      precio: 24.50,
      categoria: 'Hamburguesas',
      imagen_url: '',
      disponible: true,
      es_menu_del_dia: true
    },
    {
      id: 2,
      codigo: 'HAM-02',
      nombre: 'Hamburguesa Clásica',
      descripcion: 'Carne 180g al carbón, queso cheddar fundido y vegetales',
      precio: 18.00,
      categoria: 'Hamburguesas',
      imagen_url: '',
      disponible: true,
      es_menu_del_dia: false
    },
    {
      id: 3,
      codigo: 'ALI-01',
      nombre: 'Alitas BBQ Picantes',
      descripcion: '6 alitas bañadas en salsa artesanal BBQ ligeramente picante',
      precio: 22.00,
      categoria: 'Alitas & Pollo',
      imagen_url: '',
      disponible: true,
      es_menu_del_dia: false
    },
    {
      id: 4,
      codigo: 'CRIS-01',
      nombre: 'Combo Pollo Crispy',
      descripcion: '3 piezas broaster crocantes con papas amarillas nativas',
      precio: 26.00,
      categoria: 'Alitas & Pollo',
      imagen_url: '',
      disponible: true,
      es_menu_del_dia: true
    },
    {
      id: 5,
      codigo: 'BEB-01',
      nombre: 'Limonada Frozen',
      descripcion: 'Jarra de limonada natural frappeada con hierbabuena',
      precio: 14.00,
      categoria: 'Bebidas',
      imagen_url: '',
      disponible: true,
      es_menu_del_dia: true
    },
    {
      id: 6,
      codigo: 'BEB-02',
      nombre: 'Chicha Morada',
      descripcion: 'Receta tradicional de maíz morado con manzana y canela',
      precio: 12.00,
      categoria: 'Bebidas',
      imagen_url: '',
      disponible: true,
      es_menu_del_dia: false
    },
    {
      id: 7,
      codigo: 'COM-01',
      nombre: 'Papas Fritas Supremas',
      descripcion: 'Porción familiar con salsa de queso cheddar y tocino',
      precio: 11.50,
      categoria: 'Guarniciones',
      imagen_url: '',
      disponible: true,
      es_menu_del_dia: false
    }
  ];

  function obtenerInicialesProducto(nombre) {
    const limpio = String(nombre || '').trim();
    if (!limpio) {
      return 'PR';
    }
    const palabras = limpio.split(/\s+/).filter(Boolean);
    if (palabras.length === 1) {
      return palabras[0].substring(0, 2).toUpperCase();
    }
    const primera = palabras[0].charAt(0);
    const ultima = palabras[palabras.length - 1].charAt(0);
    return (primera + ultima).toUpperCase();
  }

  function generarUUIDv4() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
      return window.crypto.randomUUID();
    }
    if (window.crypto && typeof window.crypto.getRandomValues === 'function') {
      const bytes = new Uint8Array(16);
      window.crypto.getRandomValues(bytes);
      bytes[6] = (bytes[6] & 0x0f) | 0x40;
      bytes[8] = (bytes[8] & 0x3f) | 0x80;
      const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
      return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
    }
    throw new Error('Web Crypto API no disponible para generar identificadores seguros.');
  }

  function registrarServiceWorker() {
    if ('serviceWorker' in navigator && window.location.protocol.startsWith('http')) {
      navigator.serviceWorker.register('/sw.js').catch(() => {});
    }
  }

  function obtenerUsuarioSesion() {
    try {
      const raw = localStorage.getItem('fc_pwa_user');
      return raw ? JSON.parse(raw) : { id: null, username: '', nombre: 'Operador activo', rol: '' };
    } catch (_) {
      return { id: null, username: '', nombre: 'Operador activo', rol: '' };
    }
  }

  /**
   * Cierre de sesión real de la PWA: limpia tokens y datos locales (localStorage, sessionStorage e IndexedDB).
   */
  async function cerrarSesionPwa(redirectUrl = 'index.html') {
    carrito = [];
    usuariosActivosCache = [];
    usuarioSeleccionadoPin = null;
    usuarioSeleccionadoModal = null;
    try {
      localStorage.removeItem('fc_pwa_token');
      localStorage.removeItem('fc_pwa_user');
      localStorage.clear();
    } catch (_) {}
    try {
      sessionStorage.clear();
    } catch (_) {}
    try {
      if (typeof FoodCoreOfflineDB !== 'undefined' && typeof FoodCoreOfflineDB.limpiarBaseDatos === 'function') {
        await FoodCoreOfflineDB.limpiarBaseDatos();
      } else if (typeof indexedDB !== 'undefined') {
        await new Promise((resolve) => {
          const req = indexedDB.deleteDatabase('foodcore_pwa_db');
          req.onsuccess = () => resolve(true);
          req.onerror = () => resolve(false);
          req.onblocked = () => resolve(false);
        });
      }
    } catch (_) {}
    if (redirectUrl) {
      window.location.href = redirectUrl;
    }
  }

  function mostrarToast(mensaje) {
    const toast = document.getElementById('toastBanner');
    if (!toast) return;
    toast.textContent = mensaje;
    toast.classList.add('show');
    clearTimeout(toast._timer);
    toast._timer = setTimeout(() => {
      toast.classList.remove('show');
    }, 3400);
  }

  async function actualizarIndicadorConexion(conectado) {
    estadoOnline = conectado && !modoOfflineSimulado;
    const dot = document.getElementById('statusDot');
    const text = document.getElementById('statusText');
    const badge = document.getElementById('outboxBadge');

    if (dot) {
      dot.className = 'status-dot ' + (estadoOnline ? 'online' : 'offline');
    }
    if (text) {
      const proto = window.location.protocol === 'https:' ? 'HTTPS' : 'HTTP';
      const puerto = window.location.port || (proto === 'HTTPS' ? '8443' : '8080');
      text.textContent = estadoOnline ? `API Online (${proto} ${puerto})` : 'Modo Offline · IndexedDB';
    }
    if (badge && typeof FoodCoreOfflineDB !== 'undefined') {
      try {
        const pendientes = await FoodCoreOfflineDB.obtenerPedidosPendientes();
        badge.textContent = `Outbox: ${pendientes.length}`;
        badge.style.display = pendientes.length > 0 ? 'inline-block' : 'none';
      } catch (_) {}
    }
  }

  async function verificarSaludApi() {
    if (modoOfflineSimulado) {
      await actualizarIndicadorConexion(false);
      return false;
    }
    try {
      const res = await fetch(`${API_BASE}/health`, { method: 'GET', cache: 'no-store' });
      const ok = res.ok;
      await actualizarIndicadorConexion(ok);
      return ok;
    } catch (_) {
      await actualizarIndicadorConexion(false);
      return false;
    }
  }

  /**
   * Ciclo de sincronización protegido por mutex `syncEnProgreso`.
   * No reintenta ni trata HTTP 400/401/403 como fallos de conectividad offline.
   */
  async function sincronizarOutbox() {
    if (syncEnProgreso) {
      return;
    }
    if (modoOfflineSimulado || typeof FoodCoreOfflineDB === 'undefined') {
      await actualizarIndicadorConexion(false);
      return;
    }
    syncEnProgreso = true;
    try {
      const pendientes = await FoodCoreOfflineDB.obtenerPedidosPendientes();
      if (pendientes.length === 0) {
        await verificarSaludApi();
        return;
      }

      let huboFalloRed = false;
      for (const pedido of pendientes) {
        try {
          const res = await fetch(`${API_BASE}/pedidos`, {
            method: 'POST',
            headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
            body: JSON.stringify(pedido)
          });
          if (res.ok) {
            await FoodCoreOfflineDB.eliminarPedidoOutbox(pedido.uuid_local);
            mostrarToast(`✓ Pedido sincronizado con Cocina (Mesa ${pedido.numero_mesa})`);
          } else if (res.status === 401) {
            mostrarToast('⚠ Sesión expirada. Inicie sesión para sincronizar pedidos pendientes.');
            return;
          } else if (res.status === 400 || res.status === 403) {
            await FoodCoreOfflineDB.eliminarPedidoOutbox(pedido.uuid_local);
            let msgErr = 'Pedido rechazado por validación del servidor.';
            try {
              const errData = await res.json();
              if (errData && (errData.message || errData.error)) {
                msgErr = errData.message || errData.error;
              }
            } catch (_) {}
            mostrarToast(`⚠ ${msgErr}`);
          } else {
            huboFalloRed = true;
          }
        } catch (_) {
          huboFalloRed = true;
        }
      }
      await actualizarIndicadorConexion(!huboFalloRed);
    } catch (_) {
      await actualizarIndicadorConexion(false);
    } finally {
      syncEnProgreso = false;
    }
  }

  // =========================================================================
  // FLUJO 1: LOGIN (index.html)
  // =========================================================================
  async function iniciarLoginView() {
    inicializarTemaPwa();
    registrarServiceWorker();
    await verificarSaludApi();
    setInterval(sincronizarOutbox, 4000);

    const form = document.getElementById('loginForm');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const username = document.getElementById('txtUsername').value.trim();
      const password = document.getElementById('txtPassword').value.trim();
      const errBanner = document.getElementById('loginErrorMsg');
      if (errBanner) {
        errBanner.style.display = 'none';
      }

      try {
        const res = await fetch(`${API_BASE}/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password })
        });
        const data = await res.json();
        if (!res.ok) {
          const msg = '⚠ ' + (data.message || 'Credenciales inválidas.');
          if (errBanner) {
            errBanner.textContent = msg;
            errBanner.style.display = 'block';
          }
          mostrarToast(msg);
          return;
        }
        localStorage.setItem('fc_pwa_token', data.token);
        localStorage.setItem('fc_pwa_user', JSON.stringify(data.usuario));
        window.location.href = 'mesas.html';
      } catch (_) {
        const msg = '⚠ No se pudo conectar con el servidor de autenticación.';
        if (errBanner) {
          errBanner.textContent = msg;
          errBanner.style.display = 'block';
        }
        mostrarToast(msg);
      }
    });
  }

  // =========================================================================
  // FLUJO 2: SELECCIÓN DE MESAS (mesas.html)
  // =========================================================================
  async function iniciarMesasView() {
    inicializarTemaPwa();
    registrarServiceWorker();
    const user = obtenerUsuarioSesion();
    const lblMozo = document.getElementById('lblNombreMozo');
    if (lblMozo) {
      lblMozo.textContent = `${user.nombre}`;
    }

    const btnLogout = document.getElementById('btnCerrarSesionPwa');
    if (btnLogout && !btnLogout.dataset.bound) {
      btnLogout.dataset.bound = '1';
      btnLogout.addEventListener('click', (e) => {
        e.preventDefault();
        cerrarSesionPwa('index.html');
      });
    }

    document.querySelectorAll('[data-filter-mesas]').forEach((btn) => {
      if (!btn.dataset.bound) {
        btn.dataset.bound = '1';
        btn.addEventListener('click', () => cargarMesas(btn.dataset.filterMesas || 'TODAS'));
      }
    });

    await cargarMesas('TODAS');
    setInterval(sincronizarOutbox, 4000);
  }

  async function cargarMesas(filtroEstado = 'TODAS') {
    try {
      const res = await fetch(`${API_BASE}/mesas`, {
        method: 'GET',
        cache: 'no-store',
        headers: getAuthHeaders()
      });
      if (res.status === 401) {
        await cerrarSesionPwa('index.html');
        return;
      }
      if (res.ok) {
        listaMesas = await res.json();
        await FoodCoreOfflineDB.guardarMesasCache(listaMesas);
        await actualizarIndicadorConexion(true);
      } else {
        throw new Error('Error HTTP');
      }
    } catch (_) {
      await actualizarIndicadorConexion(false);
      const cache = await FoodCoreOfflineDB.obtenerMesasCache();
      listaMesas = Array.isArray(cache) ? cache : [];
    }
    renderizarMesas(filtroEstado);
  }

  function renderizarMesas(filtro = 'TODAS') {
    const grid = document.getElementById('mesasGrid');
    if (!grid) return;
    grid.replaceChildren();

    const filtradas = listaMesas.filter((m) => {
      if (filtro === 'TODAS') return true;
      return (m.estado || 'LIBRE').toUpperCase() === filtro;
    });

    if (filtradas.length === 0) {
      const empty = document.createElement('div');
      empty.className = 'cart-empty';
      empty.textContent = 'No hay mesas disponibles para mostrar en este filtro.';
      grid.appendChild(empty);
      return;
    }

    filtradas.forEach((m) => {
      const estado = (m.estado || 'LIBRE').toUpperCase();
      const claseEstado = estado === 'OCUPADA' ? 'ocupada' : 'libre';
      const numeroMesa = String(m.numero ?? '');

      const card = document.createElement('div');
      card.className = `mesa-card ${claseEstado}`;
      card.addEventListener('click', () => {
        window.location.href = `catalogo.html?mesa=${encodeURIComponent(numeroMesa)}`;
      });

      const top = document.createElement('div');
      top.className = 'mesa-top';

      const numSpan = document.createElement('span');
      numSpan.className = 'mesa-number';
      numSpan.textContent = `Mesa ${numeroMesa}`;

      const badgeSpan = document.createElement('span');
      badgeSpan.className = `mesa-badge ${claseEstado}`;
      badgeSpan.textContent = estado;
      top.append(numSpan, badgeSpan);

      const meta = document.createElement('div');
      meta.className = 'mesa-meta';

      const zonaSpan = document.createElement('span');
      zonaSpan.textContent = `${m.zona || 'Salón Principal'} · ${Number(m.capacidad) || 4} pax`;

      const totalSpan = document.createElement('span');
      totalSpan.className = 'mesa-total';
      totalSpan.textContent = (m.total_actual && Number(m.total_actual) > 0)
        ? `S/ ${Number(m.total_actual).toFixed(2)}`
        : 'Disponible';
      meta.append(zonaSpan, totalSpan);

      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = `btn-touch ${estado === 'OCUPADA' ? 'btn-primary' : 'btn-secondary'}`;
      btn.style.width = '100%';
      btn.textContent = estado === 'OCUPADA' ? 'Ver / Adicionar Comanda' : '+ Tomar Pedido';

      card.append(top, meta, btn);
      grid.appendChild(card);
    });
  }

  // =========================================================================
  // FLUJO 3: CATÁLOGO Y TOMA DE PEDIDO (catalogo.html)
  // =========================================================================
  async function iniciarCatalogoView() {
    inicializarTemaPwa();
    registrarServiceWorker();
    const params = new URLSearchParams(window.location.search);
    const mesa = params.get('mesa') || '04';
    const esDemo = params.get('demo') === '1';

    const pillMesa = document.getElementById('lblMesaActiva');
    if (pillMesa) {
      pillMesa.textContent = `Mesa ${mesa}`;
    }
    const user = obtenerUsuarioSesion();
    const lblMozo = document.getElementById('lblNombreMozo');
    if (lblMozo) {
      lblMozo.textContent = user.nombre;
    }
    const avatarMozo = document.getElementById('avatarMozoActivo');
    if (avatarMozo) {
      avatarMozo.textContent = obtenerInicialesNombre(user.nombre);
    }

    const inputBuscar = document.getElementById('txtBuscarPlato');
    if (inputBuscar) {
      inputBuscar.addEventListener('input', (e) => {
        textoBusqueda = e.target.value.trim().toLowerCase();
        renderizarCatalogo();
      });
    }

    vincularEventosCatalogo();

    await cargarCatalogoProductos();

    if (esDemo && carrito.length === 0 && catalogoProductos.length >= 3) {
      agregarAlCarritoPorObjeto(catalogoProductos[0], 2, 'Término 3/4, sin mayonesa');
      agregarAlCarritoPorObjeto(catalogoProductos[2] || catalogoProductos[1], 1, 'Salsa BBQ aparte');
      agregarAlCarritoPorObjeto(catalogoProductos[5] || catalogoProductos[0], 2, 'Helada');
    }

    renderizarCarrito();
    setInterval(sincronizarOutbox, 4000);

    setInterval(async () => {
      await cargarCatalogoProductos(true);
    }, 30000);
  }

  function vincularEventosCatalogo() {
    const bindClick = (id, handler) => {
      const el = document.getElementById(id);
      if (el && !el.dataset.bound) {
        el.dataset.bound = '1';
        el.addEventListener('click', handler);
      }
    };

    bindClick('btnCambiarMozo', () => abrirModalCambiarMozo());
    bindClick('btnEnviarCocina', () => enviarPedidoACocina());
    bindClick('btnCerrarModalMozo', () => cerrarModalCambiarMozo());
    bindClick('btnCancelarCambioMozo', () => cerrarModalCambiarMozo());
    bindClick('btnConfirmarCambioMozo', () => confirmarCambioMozoPin());
    bindClick('btnModalPinClear', () => limpiarPinModal());
    bindClick('btnModalPinDelete', () => borrarDigitoPinModal());

    document.querySelectorAll('.note-chip[data-note]').forEach((chip) => {
      if (!chip.dataset.bound) {
        chip.dataset.bound = '1';
        chip.addEventListener('click', () => aplicarChipNota(chip.dataset.note));
      }
    });

    document.querySelectorAll('#modalCambiarMozo .pin-btn[data-modal-digit]').forEach((btn) => {
      if (!btn.dataset.bound) {
        btn.dataset.bound = '1';
        btn.addEventListener('click', () => presionarDigitoPinModal(btn.dataset.modalDigit));
      }
    });
  }

  async function cargarCatalogoProductos(silencioso = false) {
    try {
      const token = localStorage.getItem('fc_pwa_token');
      const url = (token && token.trim() !== '')
        ? `${API_BASE}/productos`
        : `${API_BASE}/public/productos`;
      let res = await fetch(url, {
        method: 'GET',
        cache: 'no-store',
        headers: getAuthHeaders()
      });
      if (res.status === 401) {
        res = await fetch(`${API_BASE}/public/productos`, { method: 'GET', cache: 'no-store' });
      }
      if (res.ok) {
        catalogoProductos = await res.json();
        await FoodCoreOfflineDB.guardarCatalogoCache(catalogoProductos);
        await actualizarIndicadorConexion(true);
      } else {
        throw new Error('Error HTTP');
      }
    } catch (_) {
      await actualizarIndicadorConexion(false);
      const cache = await FoodCoreOfflineDB.obtenerCatalogoCache();
      catalogoProductos = (cache && cache.length > 0) ? cache : CATALOGO_FALLBACK;
    }
    renderizarCategoriasTabs();
    renderizarCatalogo();
  }

  function renderizarCategoriasTabs() {
    const container = document.getElementById('categoryTabs');
    if (!container) return;

    const categoriasSet = new Set();
    catalogoProductos.forEach((p) => {
      if (p.categoria && p.categoria.trim()) {
        categoriasSet.add(p.categoria.trim());
      }
    });
    const categoriasUnicas = Array.from(categoriasSet);

    container.replaceChildren();

    const btnTodos = document.createElement('button');
    btnTodos.type = 'button';
    btnTodos.className = `cat-tab ${categoriaActiva === 'TODOS' ? 'active' : ''}`;
    btnTodos.textContent = 'Todos los Platos';
    btnTodos.addEventListener('click', () => filtrarCategoria('TODOS', btnTodos));
    container.appendChild(btnTodos);

    const btnMenuDia = document.createElement('button');
    btnMenuDia.type = 'button';
    btnMenuDia.className = `cat-tab tab-menu-dia ${categoriaActiva === 'MENU_DEL_DIA' ? 'active' : ''}`;
    btnMenuDia.textContent = '⭐ Menú del Día';
    btnMenuDia.addEventListener('click', () => filtrarCategoria('MENU_DEL_DIA', btnMenuDia));
    container.appendChild(btnMenuDia);

    categoriasUnicas.forEach((cat) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      const esActiva = categoriaActiva.toLowerCase() === cat.toLowerCase();
      btn.className = `cat-tab ${esActiva ? 'active' : ''}`;
      btn.textContent = cat;
      btn.addEventListener('click', () => filtrarCategoria(cat, btn));
      container.appendChild(btn);
    });
  }

  function filtrarCategoria(cat, btnElement) {
    categoriaActiva = cat;
    document.querySelectorAll('.cat-tab').forEach((b) => b.classList.remove('active'));
    if (btnElement) {
      btnElement.classList.add('active');
    }
    renderizarCatalogo();
  }

  function crearFallbackThumb(nombre, iniciales) {
    const fallbackDiv = document.createElement('div');
    fallbackDiv.className = 'dish-thumb-fallback';
    fallbackDiv.setAttribute('aria-label', String(nombre || 'Producto'));
    fallbackDiv.textContent = iniciales;
    return fallbackDiv;
  }

  function renderizarCatalogo() {
    const grid = document.getElementById('dishesGrid');
    if (!grid) return;
    grid.replaceChildren();

    const filtrados = catalogoProductos.filter((p) => {
      let coincideCat = false;
      if (categoriaActiva === 'TODOS') {
        coincideCat = true;
      } else if (categoriaActiva === 'MENU_DEL_DIA') {
        coincideCat = (p.es_menu_del_dia === true || p.es_menu_del_dia === 1 || (p.categoria && p.categoria.toLowerCase().includes('menú del día')));
      } else {
        coincideCat = p.categoria && (p.categoria.toLowerCase() === categoriaActiva.toLowerCase() || p.categoria.toLowerCase().includes(categoriaActiva.toLowerCase()));
      }

      const nombre = String(p.nombre || '');
      const coincideTexto = !textoBusqueda
        || nombre.toLowerCase().includes(textoBusqueda)
        || (p.descripcion && String(p.descripcion).toLowerCase().includes(textoBusqueda))
        || (p.codigo && String(p.codigo).toLowerCase().includes(textoBusqueda));

      return coincideCat && coincideTexto;
    });

    filtrados.forEach((p) => {
      const card = document.createElement('div');
      const disponible = (p.disponible !== false && p.disponible !== 0);
      const esMenuDia = (p.es_menu_del_dia === true || p.es_menu_del_dia === 1);
      const nombreProd = String(p.nombre || 'Platillo');

      card.className = `dish-card ${disponible ? '' : 'dish-card-agotado'}`.trim();
      const iniciales = obtenerInicialesProducto(nombreProd);
      const imgUrl = (typeof p.imagen_url === 'string') ? p.imagen_url.trim() : '';

      const thumbWrapper = document.createElement('div');
      thumbWrapper.className = 'dish-thumb-wrapper';

      if (esUrlImagenSegura(imgUrl)) {
        const imgEl = document.createElement('img');
        imgEl.className = 'dish-thumb';
        imgEl.alt = nombreProd;
        imgEl.style.objectFit = 'cover';
        const replaceWithFallback = () => {
          if (imgEl.parentNode) {
            imgEl.replaceWith(crearFallbackThumb(nombreProd, iniciales));
          }
        };
        imgEl.addEventListener('error', replaceWithFallback, { once: true });
        imgEl.src = imgUrl;
        thumbWrapper.appendChild(imgEl);
        if (imgEl.complete && imgEl.naturalWidth === 0) {
          replaceWithFallback();
        }
      } else {
        thumbWrapper.appendChild(crearFallbackThumb(nombreProd, iniciales));
      }

      const body = document.createElement('div');
      body.className = 'dish-body';

      const tagsRow = document.createElement('div');
      tagsRow.style.display = 'flex';
      tagsRow.style.alignItems = 'center';
      tagsRow.style.flexWrap = 'wrap';

      const catTag = document.createElement('span');
      catTag.className = 'dish-category-tag';
      catTag.textContent = p.categoria || 'Especialidad';
      tagsRow.appendChild(catTag);

      if (esMenuDia) {
        const badgeMenu = document.createElement('span');
        badgeMenu.className = 'dish-badge-menu-dia';
        badgeMenu.textContent = '⭐ Menú del Día';
        tagsRow.appendChild(badgeMenu);
      }
      if (!disponible) {
        const badgeAgot = document.createElement('span');
        badgeAgot.className = 'dish-badge-agotado';
        badgeAgot.textContent = 'AGOTADO';
        tagsRow.appendChild(badgeAgot);
      }

      const titleEl = document.createElement('div');
      titleEl.className = 'dish-title';
      titleEl.textContent = nombreProd;

      const descEl = document.createElement('div');
      descEl.className = 'dish-desc';
      descEl.textContent = p.descripcion || 'Preparación al instante en cocina';

      const footerEl = document.createElement('div');
      footerEl.className = 'dish-footer';

      const priceEl = document.createElement('span');
      priceEl.className = 'dish-price';
      priceEl.textContent = `S/ ${Number(p.precio || 0).toFixed(2)}`;

      const addBtn = document.createElement('button');
      addBtn.type = 'button';
      addBtn.className = `btn-add-dish ${disponible ? '' : 'disabled'}`.trim();
      addBtn.disabled = !disponible;
      addBtn.setAttribute('aria-label', disponible ? `Agregar ${nombreProd}` : `${nombreProd} agotado`);
      addBtn.textContent = disponible ? '+' : '✕';
      if (disponible) {
        addBtn.addEventListener('click', () => {
          agregarAlCarritoPorObjeto(p, 1, '');
        });
      }

      footerEl.append(priceEl, addBtn);
      body.append(tagsRow, titleEl, descEl, footerEl);
      card.append(thumbWrapper, body);
      grid.appendChild(card);
    });
  }

  function agregarAlCarritoPorObjeto(prod, cantidad = 1, notaInicial = '') {
    const existente = carrito.find((item) => item.id_producto === prod.id);
    if (existente) {
      existente.cantidad += cantidad;
      if (notaInicial && !existente.notas) {
        existente.notas = notaInicial;
      }
      itemSeleccionadoId = existente.id_producto;
    } else {
      carrito.push({
        id_producto: prod.id,
        nombre: prod.nombre,
        precio_unitario: Number(prod.precio),
        cantidad: cantidad,
        notas: notaInicial
      });
      itemSeleccionadoId = prod.id;
    }
    renderizarCarrito();
  }

  function cambiarCantidadCarrito(idProducto, delta) {
    const idx = carrito.findIndex((i) => i.id_producto === idProducto);
    if (idx === -1) return;
    carrito[idx].cantidad += delta;
    if (carrito[idx].cantidad <= 0) {
      carrito.splice(idx, 1);
    }
    renderizarCarrito();
  }

  function actualizarNotaItem(idProducto, nota) {
    const item = carrito.find((i) => i.id_producto === idProducto);
    if (item) {
      item.notas = nota;
    }
  }

  function aplicarChipNota(textoChip) {
    if (carrito.length === 0) return;
    const target = carrito.find((i) => i.id_producto === itemSeleccionadoId) || carrito[carrito.length - 1];
    if (!target.notas) {
      target.notas = textoChip;
    } else if (!target.notas.includes(textoChip)) {
      target.notas += `, ${textoChip}`;
    }
    renderizarCarrito();
  }

  function renderizarCarrito() {
    const listEl = document.getElementById('cartItemsList');
    const countEl = document.getElementById('cartCountPill');
    const subEl = document.getElementById('lblSubtotal');
    const igvEl = document.getElementById('lblIgv');
    const totEl = document.getElementById('lblTotal');

    if (!listEl) return;

    const totalUnidades = carrito.reduce((acc, i) => acc + i.cantidad, 0);
    const totalMonto = carrito.reduce((acc, i) => acc + i.cantidad * i.precio_unitario, 0);
    const subtotalBase = totalMonto / 1.18;
    const igvMonto = totalMonto - subtotalBase;

    if (countEl) countEl.textContent = `${totalUnidades} ítems`;
    if (subEl) subEl.textContent = `S/ ${subtotalBase.toFixed(2)}`;
    if (igvEl) igvEl.textContent = `S/ ${igvMonto.toFixed(2)}`;
    if (totEl) totEl.textContent = `S/ ${totalMonto.toFixed(2)}`;

    listEl.replaceChildren();
    if (carrito.length === 0) {
      const empty = document.createElement('div');
      empty.className = 'cart-empty';
      empty.append(
        document.createTextNode('Aún no hay platos en la comanda.'),
        document.createElement('br'),
        document.createTextNode('Toque el botón ')
      );
      const strong = document.createElement('strong');
      strong.textContent = '+';
      empty.append(strong, document.createTextNode(' de cualquier plato para agregarlo.'));
      listEl.appendChild(empty);
      return;
    }

    carrito.forEach((item) => {
      const sub = item.cantidad * item.precio_unitario;
      const div = document.createElement('div');
      div.className = 'cart-item';
      div.addEventListener('click', () => {
        itemSeleccionadoId = item.id_producto;
      });

      const top = document.createElement('div');
      top.className = 'cart-item-top';

      const nameSpan = document.createElement('span');
      nameSpan.className = 'cart-item-name';
      nameSpan.textContent = String(item.nombre || '');

      const subSpan = document.createElement('span');
      subSpan.className = 'cart-item-subtotal';
      subSpan.textContent = `S/ ${sub.toFixed(2)}`;
      top.append(nameSpan, subSpan);

      const controls = document.createElement('div');
      controls.className = 'cart-item-controls';

      const puSpan = document.createElement('span');
      puSpan.style.fontSize = '12.5px';
      puSpan.style.color = 'var(--text-secondary)';
      puSpan.style.fontVariantNumeric = 'tabular-nums';
      puSpan.textContent = `P.U. S/ ${Number(item.precio_unitario).toFixed(2)}`;

      const stepper = document.createElement('div');
      stepper.className = 'qty-stepper';

      const btnDec = document.createElement('button');
      btnDec.type = 'button';
      btnDec.className = 'btn-qty';
      btnDec.dataset.action = 'dec';
      btnDec.textContent = '-';
      btnDec.addEventListener('click', (e) => {
        e.stopPropagation();
        cambiarCantidadCarrito(item.id_producto, -1);
      });

      const qtyVal = document.createElement('span');
      qtyVal.className = 'qty-value';
      qtyVal.textContent = String(item.cantidad);

      const btnInc = document.createElement('button');
      btnInc.type = 'button';
      btnInc.className = 'btn-qty';
      btnInc.dataset.action = 'inc';
      btnInc.textContent = '+';
      btnInc.addEventListener('click', (e) => {
        e.stopPropagation();
        cambiarCantidadCarrito(item.id_producto, 1);
      });

      stepper.append(btnDec, qtyVal, btnInc);
      controls.append(puSpan, stepper);

      const noteInput = document.createElement('input');
      noteInput.type = 'text';
      noteInput.className = 'cart-item-note-input';
      noteInput.placeholder = 'Nota de cocina (ej. sin cremas, término 3/4)...';
      noteInput.value = item.notas || '';
      noteInput.addEventListener('input', (e) => {
        actualizarNotaItem(item.id_producto, e.target.value);
      });

      div.append(top, controls, noteInput);
      listEl.appendChild(div);
    });
  }

  /**
   * Envía el pedido a `POST /api/pedidos` con `uuid_local` idempotente.
   * Rechazos HTTP 400/401/403 NO se encolan como fallos offline.
   */
  async function enviarPedidoACocina() {
    if (carrito.length === 0) {
      mostrarToast('⚠ Agregue al menos un plato antes de enviar a cocina.');
      return;
    }

    const params = new URLSearchParams(window.location.search);
    const numeroMesa = params.get('mesa') || '04';
    const user = obtenerUsuarioSesion();

    const pedidoPayload = {
      uuid_local: generarUUIDv4(),
      numero_mesa: numeroMesa,
      id_usuario: user.id || 1,
      id_usuario_creador: user.id || 1,
      cliente_nombre: `Mesa ${numeroMesa}`,
      items: carrito.map((i) => ({
        id_producto: i.id_producto,
        nombre: i.nombre,
        cantidad: i.cantidad,
        precio_unitario: i.precio_unitario,
        notas: i.notas || ''
      }))
    };

    if (modoOfflineSimulado) {
      await FoodCoreOfflineDB.guardarPedidoOutbox(pedidoPayload);
      carrito = [];
      renderizarCarrito();
      await actualizarIndicadorConexion(false);
      mostrarToast(`📦 Guardado en IndexedDB (PENDIENTE) · UUID ${pedidoPayload.uuid_local.slice(0, 8)}`);
      return;
    }

    try {
      const res = await fetch(`${API_BASE}/pedidos`, {
        method: 'POST',
        headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify(pedidoPayload)
      });

      if (res.status === 401) {
        mostrarToast('⚠ Sesión expirada o no autorizada. Inicie sesión nuevamente.');
        await cerrarSesionPwa('index.html');
        return;
      }

      if (res.status === 400 || res.status === 403) {
        let mensajeError = res.status === 403
          ? 'No tiene permisos para enviar pedidos.'
          : 'El pedido contiene datos inválidos o productos agotados.';
        try {
          const errJson = await res.json();
          if (errJson && (errJson.message || errJson.error)) {
            mensajeError = errJson.message || errJson.error;
          }
        } catch (_) {}
        mostrarToast(`⚠ ${mensajeError}`);
        return;
      }

      if (!res.ok) {
        throw new Error('Servidor API temporalmente no disponible');
      }

      const data = await res.json();
      carrito = [];
      renderizarCarrito();
      await actualizarIndicadorConexion(true);
      mostrarToast(`✓ Comanda ${data.numero_comanda} enviada a Cocina (Mesa ${numeroMesa})`);
    } catch (_) {
      await FoodCoreOfflineDB.guardarPedidoOutbox(pedidoPayload);
      carrito = [];
      renderizarCarrito();
      await actualizarIndicadorConexion(false);
      mostrarToast('📦 Sin conexión: Pedido guardado en IndexedDB (outbox_pedidos PENDIENTE)');
    }
  }

  function obtenerInicialesNombre(nombre) {
    if (!nombre) return 'FC';
    const partes = nombre.trim().split(/\s+/);
    if (partes.length === 1) return partes[0].slice(0, 2).toUpperCase();
    return (partes[0][0] + partes[1][0]).toUpperCase();
  }

  // =========================================================================
  // SUB-FASE C.1: MODO MOZO Y SEGURIDAD PIN (4 a 6 dígitos)
  // =========================================================================
  let pinActual = '';
  let usuarioSeleccionadoPin = null;
  let pinModalActual = '';
  let usuarioSeleccionadoModal = null;
  let usuariosActivosCache = [];
  let pinAutoSubmitTimer = null;
  let pinModalAutoSubmitTimer = null;
  let pinSubmitEnProgreso = false;
  let pinModalSubmitEnProgreso = false;

  async function iniciarPinLoginView() {
    inicializarTemaPwa();
    registrarServiceWorker();
    vincularEventosPinLogin();
    await verificarSaludApi();
    await cargarUsuariosActivosPinLogin();

    window.addEventListener('keydown', (e) => {
      if (e.key >= '0' && e.key <= '9') {
        presionarDigitoPin(e.key);
      } else if (e.key === 'Backspace') {
        borrarUltimoDigitoPin();
      } else if (e.key === 'Enter') {
        ejecutarPinLogin();
      } else if (e.key === 'Escape' || e.key === 'c' || e.key === 'C') {
        limpiarPin();
      }
    });
  }

  function vincularEventosPinLogin() {
    document.querySelectorAll('#pinKeypad .pin-btn[data-digit]').forEach((btn) => {
      if (!btn.dataset.bound) {
        btn.dataset.bound = '1';
        btn.addEventListener('click', () => presionarDigitoPin(btn.dataset.digit));
      }
    });
    const btnClear = document.getElementById('btnPinClear');
    if (btnClear && !btnClear.dataset.bound) {
      btnClear.dataset.bound = '1';
      btnClear.addEventListener('click', limpiarPin);
    }
    const btnDel = document.getElementById('btnPinDelete');
    if (btnDel && !btnDel.dataset.bound) {
      btnDel.dataset.bound = '1';
      btnDel.addEventListener('click', borrarUltimoDigitoPin);
    }
    const btnSubmit = document.getElementById('btnPinSubmit');
    if (btnSubmit && !btnSubmit.dataset.bound) {
      btnSubmit.dataset.bound = '1';
      btnSubmit.addEventListener('click', () => ejecutarPinLogin());
    }
  }

  async function cargarUsuariosActivosPinLogin() {
    const grid = document.getElementById('mozosGrid');
    if (!grid) return;

    usuariosActivosCache = [];
    try {
      if (localStorage.getItem('fc_pwa_token')) {
        const res = await fetch(`${API_BASE}/usuarios/activos`, {
          method: 'GET',
          cache: 'no-store',
          headers: getAuthHeaders()
        });
        if (res.ok) {
          usuariosActivosCache = await res.json();
        }
      }
    } catch (_) {
      usuariosActivosCache = [];
    }

    if (usuariosActivosCache && usuariosActivosCache.length > 0) {
      grid.replaceChildren();
      usuariosActivosCache.forEach((u, index) => {
        const card = document.createElement('div');
        const esActivo = index === 0;
        const userId = u.id || u.id_usuario;
        const nombreStr = String(u.nombre || u.username || 'Operador');
        const rolStr = String(u.rol || 'MOZO');
        card.className = `mozo-card ${esActivo ? 'active' : ''}`;
        card.dataset.id = String(userId || '');
        card.dataset.username = String(u.username || '');
        card.dataset.nombre = nombreStr;
        const iniciales = obtenerInicialesNombre(nombreStr);
        const esAdmin = rolStr.toUpperCase().includes('ADMIN');

        const avatar = document.createElement('div');
        avatar.className = `mozo-avatar-circle ${esAdmin ? 'admin' : ''}`.trim();
        avatar.textContent = iniciales;

        const info = document.createElement('div');
        info.className = 'mozo-info';

        const nameEl = document.createElement('span');
        nameEl.className = 'mozo-name';
        nameEl.textContent = nombreStr;

        const roleEl = document.createElement('span');
        roleEl.className = `mozo-role-badge ${esAdmin ? 'badge-admin' : ''}`.trim();
        roleEl.textContent = rolStr;
        info.append(nameEl, roleEl);

        const dot = document.createElement('span');
        dot.className = 'pin-ready-dot';
        dot.title = u.tiene_pin ? 'PIN configurado' : 'Sin PIN';

        card.append(avatar, info, dot);
        card.addEventListener('click', () => seleccionarUsuarioPin(card));
        grid.appendChild(card);

        if (esActivo) {
          usuarioSeleccionadoPin = {
            id: userId,
            username: u.username,
            nombre: nombreStr
          };
          actualizarLabelTargetMozo();
        }
      });
    }
  }

  function seleccionarUsuarioPin(element) {
    document.querySelectorAll('.mozo-card').forEach((c) => c.classList.remove('active'));
    element.classList.add('active');
    usuarioSeleccionadoPin = {
      id: parseInt(element.dataset.id, 10) || 0,
      username: element.dataset.username || '',
      nombre: element.dataset.nombre || 'Operador por PIN'
    };
    actualizarLabelTargetMozo();
    limpiarPin();
  }

  function actualizarLabelTargetMozo() {
    const lbl = document.getElementById('lblTargetMozoNombre');
    if (lbl && usuarioSeleccionadoPin) {
      lbl.textContent = usuarioSeleccionadoPin.nombre;
    }
  }

  function cancelarAutoSubmitPin() {
    if (pinAutoSubmitTimer !== null) {
      clearTimeout(pinAutoSubmitTimer);
      pinAutoSubmitTimer = null;
    }
  }

  function presionarDigitoPin(digito) {
    if (!/^\d$/.test(String(digito)) || pinActual.length >= 6) return;
    cancelarAutoSubmitPin();
    pinActual += String(digito);
    actualizarDotsPin();
    ocultarErrorPin();

    if (pinActual.length === 4) {
      pinAutoSubmitTimer = setTimeout(() => {
        pinAutoSubmitTimer = null;
        if (pinActual.length === 4 && !pinSubmitEnProgreso) {
          ejecutarPinLogin();
        }
      }, PIN_AUTO_SUBMIT_DELAY_MS);
    }
  }

  function borrarUltimoDigitoPin() {
    cancelarAutoSubmitPin();
    if (pinActual.length > 0) {
      pinActual = pinActual.slice(0, -1);
      actualizarDotsPin();
      ocultarErrorPin();
    }
  }

  function limpiarPin() {
    cancelarAutoSubmitPin();
    pinActual = '';
    actualizarDotsPin();
    ocultarErrorPin();
  }

  function actualizarDotsPin() {
    const display = document.getElementById('pinDotsDisplay');
    for (let i = 0; i < 4; i++) {
      const dot = document.getElementById(`dot-${i}`);
      if (dot) {
        dot.classList.toggle('filled', i < pinActual.length);
      }
    }
    if (display) {
      display.querySelectorAll('.pin-dot-extra').forEach((extra) => extra.remove());
      for (let i = 4; i < pinActual.length; i++) {
        const extraDot = document.createElement('span');
        extraDot.className = 'pin-dot pin-dot-extra filled';
        extraDot.id = `dot-${i}`;
        display.appendChild(extraDot);
      }
    }
  }

  function mostrarErrorPin(mensaje) {
    const err = document.getElementById('pinErrorMsg');
    if (err) {
      err.textContent = '⚠ ' + mensaje;
      err.style.display = 'block';
    }
  }

  function ocultarErrorPin() {
    const err = document.getElementById('pinErrorMsg');
    if (err) {
      err.style.display = 'none';
    }
  }

  async function ejecutarPinLogin() {
    cancelarAutoSubmitPin();
    if (pinSubmitEnProgreso) {
      return;
    }
    if (pinActual.length < 4 || pinActual.length > 6) {
      mostrarErrorPin('Ingrese un PIN de 4 a 6 dígitos.');
      return;
    }

    const payload = {
      pin: pinActual,
      id_usuario: usuarioSeleccionadoPin ? usuarioSeleccionadoPin.id : 0,
      username: usuarioSeleccionadoPin ? usuarioSeleccionadoPin.username : ''
    };

    pinSubmitEnProgreso = true;
    try {
      const res = await fetch(`${API_BASE}/auth/pin-login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (!res.ok) {
        mostrarErrorPin(data.message || data.mensaje || 'PIN incorrecto.');
        limpiarPin();
        return;
      }

      localStorage.setItem('fc_pwa_token', data.token);
      localStorage.setItem('fc_pwa_user', JSON.stringify(data.usuario));
      mostrarToast(`✓ Bienvenido, ${data.usuario.nombre}`);
      setTimeout(() => {
        window.location.href = 'mesas.html';
      }, 400);
    } catch (_) {
      mostrarErrorPin('No se pudo conectar con el servidor para validar el PIN.');
      limpiarPin();
    } finally {
      pinSubmitEnProgreso = false;
    }
  }

  // Modal Cambio Rápido de Mozo
  async function abrirModalCambiarMozo() {
    const modal = document.getElementById('modalCambiarMozo');
    if (!modal) return;
    modal.style.display = 'flex';
    limpiarPinModal();

    await cargarMozosParaModalSwitch();
  }

  function cerrarModalCambiarMozo() {
    const modal = document.getElementById('modalCambiarMozo');
    if (modal) {
      modal.style.display = 'none';
    }
    limpiarPinModal();
  }

  async function cargarMozosParaModalSwitch() {
    const container = document.getElementById('modalMozosList');
    if (!container) return;

    if (!usuariosActivosCache || usuariosActivosCache.length === 0) {
      try {
        if (localStorage.getItem('fc_pwa_token')) {
          const res = await fetch(`${API_BASE}/usuarios/activos`, {
            method: 'GET',
            cache: 'no-store',
            headers: getAuthHeaders()
          });
          if (res.ok) {
            usuariosActivosCache = await res.json();
          }
        }
      } catch (_) {}
    }

    const currentUser = obtenerUsuarioSesion();
    container.replaceChildren();
    if (!usuariosActivosCache || usuariosActivosCache.length === 0) {
      const emptyMsg = document.createElement('div');
      emptyMsg.style.padding = '10px';
      emptyMsg.style.color = 'var(--text-secondary)';
      emptyMsg.style.fontSize = '13px';
      emptyMsg.textContent = 'No se pudieron cargar los operadores activos del servidor.';
      container.appendChild(emptyMsg);
      return;
    }

    usuariosActivosCache.forEach((u, index) => {
      const chip = document.createElement('div');
      const esActual = u.username === currentUser.username;
      const userId = u.id || u.id_usuario;
      const nombreStr = String(u.nombre || u.username || 'Operador');

      chip.className = `mozo-chip-modal ${index === 0 ? 'active' : ''}`;
      chip.dataset.id = String(userId || '');
      chip.dataset.username = String(u.username || '');
      chip.dataset.nombre = nombreStr;

      const avatarMini = document.createElement('span');
      avatarMini.className = 'mozo-avatar-mini';
      avatarMini.textContent = obtenerInicialesNombre(nombreStr);

      const labelSpan = document.createElement('span');
      labelSpan.textContent = esActual ? `${nombreStr} (Actual)` : nombreStr;

      chip.append(avatarMini, labelSpan);
      chip.addEventListener('click', () => {
        container.querySelectorAll('.mozo-chip-modal').forEach((c) => c.classList.remove('active'));
        chip.classList.add('active');
        usuarioSeleccionadoModal = {
          id: parseInt(chip.dataset.id, 10) || 0,
          username: chip.dataset.username || '',
          nombre: chip.dataset.nombre || nombreStr
        };
        limpiarPinModal();
      });
      container.appendChild(chip);

      if (index === 0) {
        usuarioSeleccionadoModal = {
          id: userId,
          username: u.username,
          nombre: nombreStr
        };
      }
    });
  }

  function cancelarAutoSubmitPinModal() {
    if (pinModalAutoSubmitTimer !== null) {
      clearTimeout(pinModalAutoSubmitTimer);
      pinModalAutoSubmitTimer = null;
    }
  }

  function presionarDigitoPinModal(digit) {
    if (!/^\d$/.test(String(digit)) || pinModalActual.length >= 6) return;
    cancelarAutoSubmitPinModal();
    pinModalActual += String(digit);
    actualizarDotsPinModal();
    ocultarErrorPinModal();

    if (pinModalActual.length === 4) {
      pinModalAutoSubmitTimer = setTimeout(() => {
        pinModalAutoSubmitTimer = null;
        if (pinModalActual.length === 4 && !pinModalSubmitEnProgreso) {
          confirmarCambioMozoPin();
        }
      }, PIN_AUTO_SUBMIT_DELAY_MS);
    }
  }

  function borrarDigitoPinModal() {
    cancelarAutoSubmitPinModal();
    if (pinModalActual.length > 0) {
      pinModalActual = pinModalActual.slice(0, -1);
      actualizarDotsPinModal();
      ocultarErrorPinModal();
    }
  }

  function limpiarPinModal() {
    cancelarAutoSubmitPinModal();
    pinModalActual = '';
    actualizarDotsPinModal();
    ocultarErrorPinModal();
  }

  function actualizarDotsPinModal() {
    const display = document.getElementById('modalPinDots');
    for (let i = 0; i < 4; i++) {
      const dot = document.getElementById(`mdot-${i}`);
      if (dot) {
        dot.classList.toggle('filled', i < pinModalActual.length);
      }
    }
    if (display) {
      display.querySelectorAll('.pin-dot-extra').forEach((extra) => extra.remove());
      for (let i = 4; i < pinModalActual.length; i++) {
        const extraDot = document.createElement('span');
        extraDot.className = 'pin-dot pin-dot-extra filled';
        extraDot.id = `mdot-${i}`;
        display.appendChild(extraDot);
      }
    }
  }

  function mostrarErrorPinModal(msg) {
    const err = document.getElementById('modalPinErrorMsg');
    if (err) {
      err.textContent = '⚠ ' + msg;
      err.style.display = 'block';
    }
  }

  function ocultarErrorPinModal() {
    const err = document.getElementById('modalPinErrorMsg');
    if (err) {
      err.style.display = 'none';
    }
  }

  async function confirmarCambioMozoPin() {
    cancelarAutoSubmitPinModal();
    if (pinModalSubmitEnProgreso) {
      return;
    }
    if (pinModalActual.length < 4 || pinModalActual.length > 6) {
      mostrarErrorPinModal('Ingrese un PIN de 4 a 6 dígitos.');
      return;
    }

    const payload = {
      pin: pinModalActual,
      id_usuario: usuarioSeleccionadoModal ? usuarioSeleccionadoModal.id : 0,
      username: usuarioSeleccionadoModal ? usuarioSeleccionadoModal.username : ''
    };

    pinModalSubmitEnProgreso = true;
    try {
      const res = await fetch(`${API_BASE}/auth/pin-switch`, {
        method: 'POST',
        headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (!res.ok) {
        mostrarErrorPinModal(data.message || data.mensaje || 'PIN de mozo incorrecto.');
        limpiarPinModal();
        return;
      }

      localStorage.setItem('fc_pwa_token', data.token);
      localStorage.setItem('fc_pwa_user', JSON.stringify(data.usuario));

      const lblMozo = document.getElementById('lblNombreMozo');
      if (lblMozo) {
        lblMozo.textContent = data.usuario.nombre;
      }
      const avatarMozo = document.getElementById('avatarMozoActivo');
      if (avatarMozo) {
        avatarMozo.textContent = obtenerInicialesNombre(data.usuario.nombre);
      }

      cerrarModalCambiarMozo();
      mostrarToast(`✓ Mozo activo transferido a: ${data.usuario.nombre}`);
    } catch (_) {
      mostrarErrorPinModal('No se pudo conectar con el servidor para validar el PIN.');
      limpiarPinModal();
    } finally {
      pinModalSubmitEnProgreso = false;
    }
  }

  async function alternarSimulacionOffline() {
    const params = new URLSearchParams(window.location.search);
    if (params.get('debug') !== '1') {
      return;
    }
    modoOfflineSimulado = !modoOfflineSimulado;
    if (!modoOfflineSimulado) {
      await sincronizarOutbox();
    } else {
      await actualizarIndicadorConexion(false);
      mostrarToast('Modo Offline activado: Los pedidos se guardarán en IndexedDB');
    }
  }

  return {
    inicializarTemaPwa,
    alternarTemaPwa,
    esUrlImagenSegura,
    cerrarSesionPwa,
    iniciarLoginView,
    iniciarPinLoginView,
    seleccionarUsuarioPin,
    presionarDigitoPin,
    borrarUltimoDigitoPin,
    limpiarPin,
    ejecutarPinLogin,
    abrirModalCambiarMozo,
    cerrarModalCambiarMozo,
    presionarDigitoPinModal,
    borrarDigitoPinModal,
    limpiarPinModal,
    confirmarCambioMozoPin,
    obtenerInicialesNombre,
    iniciarMesasView,
    cargarMesas,
    iniciarCatalogoView,
    cargarCatalogoProductos,
    renderizarCategoriasTabs,
    filtrarCategoria,
    obtenerInicialesProducto,
    aplicarChipNota,
    enviarPedidoACocina,
    alternarSimulacionOffline,
    sincronizarOutbox
  };
})();
