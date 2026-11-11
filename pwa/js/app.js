/**
 * Controlador Principal Frontend — FoodCore ERP v4.0 Tablet PWA (SPEC-FOODCORE-v4.0 §3, §4)
 * Gestiona autenticación JWT, selección de mesas, toma de comanda táctil, envío a KDS
 * y sincronización automática cada 10s desde IndexedDB (`outbox_pedidos`).
 */
const FoodCorePWA = (() => {
  const API_BASE = (window.location.origin && window.location.origin !== 'null' && !window.location.origin.startsWith('file://'))
    ? `${window.location.origin}/api`
    : 'https://localhost:8443/api';

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

  /**
   * Extrae las iniciales profesionales del producto para el contenedor fallback SaaS (v4.0.1):
   * - Primera letra de la primera y última palabra del nombre del producto.
   * - Si el nombre es de una sola palabra, usa las dos primeras letras.
   */
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
    if (window.crypto && window.crypto.randomUUID) {
      return window.crypto.randomUUID();
    }
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
      const r = (Math.random() * 16) | 0;
      const v = c === 'x' ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
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

  /**
   * Actualiza el indicador visual circular verde/rojo y el contador de Outbox IndexedDB.
   */
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
   * Ciclo de sincronización cada 10 segundos (SPEC-FOODCORE-v4.0 §4):
   * Reintenta enviar los pedidos de `outbox_pedidos` a `POST /api/pedidos` y los elimina al tener éxito.
   */
  async function sincronizarOutbox() {
    if (modoOfflineSimulado || typeof FoodCoreOfflineDB === 'undefined') {
      await actualizarIndicadorConexion(false);
      return;
    }
    try {
      const pendientes = await FoodCoreOfflineDB.obtenerPedidosPendientes();
      if (pendientes.length === 0) {
        await verificarSaludApi();
        return;
      }

      let huboFallo = false;
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
          } else {
            huboFallo = true;
          }
        } catch (_) {
          huboFallo = true;
        }
      }
      await actualizarIndicadorConexion(!huboFallo);
    } catch (_) {
      await actualizarIndicadorConexion(false);
    }
  }

  // =========================================================================
  // FLUJO 1: LOGIN (index.html)
  // =========================================================================
  async function iniciarLoginView() {
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
    registrarServiceWorker();
    const user = obtenerUsuarioSesion();
    const lblMozo = document.getElementById('lblNombreMozo');
    if (lblMozo) {
      lblMozo.textContent = `${user.nombre}`;
    }

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
      if (cache && cache.length > 0) {
        listaMesas = cache;
      } else {
        listaMesas = Array.from({ length: 12 }, (_, idx) => {
          const num = String(idx + 1).padStart(2, '0');
          const ocupada = idx === 0 || idx === 1 || idx === 3;
          return {
            id: idx + 1,
            numero: num,
            capacidad: (idx % 3 === 0) ? 6 : 4,
            zona: idx < 8 ? 'Salón Principal' : 'Terraza Lounge',
            estado: ocupada ? 'OCUPADA' : 'LIBRE',
            total_actual: ocupada ? 74.50 : 0.0
          };
        });
      }
    }
    renderizarMesas(filtroEstado);
  }

  function renderizarMesas(filtro = 'TODAS') {
    const grid = document.getElementById('mesasGrid');
    if (!grid) return;
    grid.innerHTML = '';

    const filtradas = listaMesas.filter((m) => {
      if (filtro === 'TODAS') return true;
      return (m.estado || 'LIBRE').toUpperCase() === filtro;
    });

    filtradas.forEach((m) => {
      const estado = (m.estado || 'LIBRE').toUpperCase();
      const claseEstado = estado === 'OCUPADA' ? 'ocupada' : 'libre';
      const card = document.createElement('div');
      card.className = `mesa-card ${claseEstado}`;
      card.onclick = () => {
        window.location.href = `catalogo.html?mesa=${encodeURIComponent(m.numero)}`;
      };
      const totalTexto = m.total_actual && m.total_actual > 0
        ? `S/ ${Number(m.total_actual).toFixed(2)}`
        : 'Disponible';

      card.innerHTML = `
        <div class="mesa-top">
          <span class="mesa-number">Mesa ${m.numero}</span>
          <span class="mesa-badge ${claseEstado}">${estado}</span>
        </div>
        <div class="mesa-meta">
          <span>${m.zona || 'Salón Principal'} · ${m.capacidad || 4} pax</span>
          <span class="mesa-total">${totalTexto}</span>
        </div>
        <button type="button" class="btn-touch ${estado === 'OCUPADA' ? 'btn-primary' : 'btn-secondary'}" style="width:100%;">
          ${estado === 'OCUPADA' ? 'Ver / Adicionar Comanda' : '+ Tomar Pedido'}
        </button>
      `;
      grid.appendChild(card);
    });
  }

  // =========================================================================
  // FLUJO 3: CATÁLOGO Y TOMA DE PEDIDO (catalogo.html)
  // =========================================================================
  async function iniciarCatalogoView() {
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

    await cargarCatalogoProductos();

    if (esDemo && carrito.length === 0 && catalogoProductos.length >= 3) {
      agregarAlCarritoPorObjeto(catalogoProductos[0], 2, 'Término 3/4, sin mayonesa');
      agregarAlCarritoPorObjeto(catalogoProductos[2] || catalogoProductos[1], 1, 'Salsa BBQ aparte');
      agregarAlCarritoPorObjeto(catalogoProductos[5] || catalogoProductos[0], 2, 'Helada');
    }

    renderizarCarrito();
    setInterval(sincronizarOutbox, 4000);

    // Auto-refresco del catálogo cada 30 segundos (v4.2.0 Sub-Fase A)
    setInterval(async () => {
      await cargarCatalogoProductos(true);
    }, 30000);
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

    // Recolectar categorías presentes en el catálogo
    const categoriasSet = new Set();
    catalogoProductos.forEach((p) => {
      if (p.categoria && p.categoria.trim()) {
        categoriasSet.add(p.categoria.trim());
      }
    });
    const categoriasUnicas = Array.from(categoriasSet);

    container.innerHTML = '';

    // 1. Tab Todos los Platos
    const btnTodos = document.createElement('button');
    btnTodos.type = 'button';
    btnTodos.className = `cat-tab ${categoriaActiva === 'TODOS' ? 'active' : ''}`;
    btnTodos.textContent = 'Todos los Platos';
    btnTodos.onclick = () => filtrarCategoria('TODOS', btnTodos);
    container.appendChild(btnTodos);

    // 2. Tab Menú del Día
    const btnMenuDia = document.createElement('button');
    btnMenuDia.type = 'button';
    btnMenuDia.className = `cat-tab tab-menu-dia ${categoriaActiva === 'MENU_DEL_DIA' ? 'active' : ''}`;
    btnMenuDia.innerHTML = '⭐ Menú del Día';
    btnMenuDia.onclick = () => filtrarCategoria('MENU_DEL_DIA', btnMenuDia);
    container.appendChild(btnMenuDia);

    // 3. Categorías dinámicas
    categoriasUnicas.forEach((cat) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      const esActiva = categoriaActiva.toLowerCase() === cat.toLowerCase();
      btn.className = `cat-tab ${esActiva ? 'active' : ''}`;
      btn.textContent = cat;
      btn.onclick = () => filtrarCategoria(cat, btn);
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

  function renderizarCatalogo() {
    const grid = document.getElementById('dishesGrid');
    if (!grid) return;
    grid.innerHTML = '';

    const filtrados = catalogoProductos.filter((p) => {
      let coincideCat = false;
      if (categoriaActiva === 'TODOS') {
        coincideCat = true;
      } else if (categoriaActiva === 'MENU_DEL_DIA') {
        coincideCat = (p.es_menu_del_dia === true || p.es_menu_del_dia === 1 || (p.categoria && p.categoria.toLowerCase().includes('menú del día')));
      } else {
        coincideCat = p.categoria && (p.categoria.toLowerCase() === categoriaActiva.toLowerCase() || p.categoria.toLowerCase().includes(categoriaActiva.toLowerCase()));
      }

      const coincideTexto = !textoBusqueda
        || p.nombre.toLowerCase().includes(textoBusqueda)
        || (p.descripcion && p.descripcion.toLowerCase().includes(textoBusqueda))
        || (p.codigo && p.codigo.toLowerCase().includes(textoBusqueda));

      return coincideCat && coincideTexto;
    });

    filtrados.forEach((p) => {
      const card = document.createElement('div');
      const disponible = (p.disponible !== false && p.disponible !== 0);
      const esMenuDia = (p.es_menu_del_dia === true || p.es_menu_del_dia === 1);

      card.className = `dish-card ${disponible ? '' : 'dish-card-agotado'}`.trim();
      const iniciales = obtenerInicialesProducto(p.nombre);
      const imgUrl = (typeof p.imagen_url === 'string') ? p.imagen_url.trim() : '';
      const tieneImagenValida = Boolean(imgUrl && imgUrl !== 'null' && imgUrl !== 'undefined');

      const mediaHtml = tieneImagenValida
        ? `<img class="dish-thumb" src="${imgUrl}" alt="${p.nombre}" style="object-fit: cover;" onerror="const d=document.createElement('div'); d.className='dish-thumb-fallback'; d.setAttribute('aria-label', '${(p.nombre || '').replace(/'/g, "\\'")}'); d.textContent='${iniciales}'; this.replaceWith(d);" />`
        : `<div class="dish-thumb-fallback" aria-label="${p.nombre}">${iniciales}</div>`;

      const badgeMenuDia = esMenuDia ? `<span class="dish-badge-menu-dia">⭐ Menú del Día</span>` : '';
      const badgeAgotado = !disponible ? `<span class="dish-badge-agotado">AGOTADO</span>` : '';

      card.innerHTML = `
        <div class="dish-thumb-wrapper">${mediaHtml}</div>
        <div class="dish-body">
          <div style="display:flex; align-items:center; flex-wrap:wrap;">
            <span class="dish-category-tag">${p.categoria || 'Especialidad'}</span>
            ${badgeMenuDia}
            ${badgeAgotado}
          </div>
          <div class="dish-title">${p.nombre}</div>
          <div class="dish-desc">${p.descripcion || 'Preparación al instante en cocina'}</div>
          <div class="dish-footer">
            <span class="dish-price">S/ ${Number(p.precio).toFixed(2)}</span>
            <button type="button" class="btn-add-dish ${disponible ? '' : 'disabled'}" ${disponible ? '' : 'disabled'} aria-label="${disponible ? 'Agregar ' + p.nombre : p.nombre + ' agotado'}">
              ${disponible ? '+' : '✕'}
            </button>
          </div>
        </div>
      `;

      const imgEl = card.querySelector('img.dish-thumb');
      if (imgEl) {
        const replaceWithFallback = () => {
          const fallbackDiv = document.createElement('div');
          fallbackDiv.className = 'dish-thumb-fallback';
          fallbackDiv.setAttribute('aria-label', p.nombre);
          fallbackDiv.textContent = iniciales;
          if (imgEl.parentNode) {
            imgEl.replaceWith(fallbackDiv);
          }
        };
        imgEl.addEventListener('error', replaceWithFallback);
        if (imgEl.complete && imgEl.naturalWidth === 0) {
          replaceWithFallback();
        }
        imgEl.addEventListener('error', () => {
          const fallbackDiv = document.createElement('div');
          fallbackDiv.className = 'dish-thumb-fallback';
          fallbackDiv.setAttribute('aria-label', p.nombre);
          fallbackDiv.textContent = iniciales;
          imgEl.replaceWith(fallbackDiv);
        });
      }

      if (disponible) {
        card.querySelector('.btn-add-dish').addEventListener('click', () => {
          agregarAlCarritoPorObjeto(p, 1, '');
        });
      }
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

    if (carrito.length === 0) {
      listEl.innerHTML = `<div class="cart-empty">Aún no hay platos en la comanda.<br/>Toque el botón <strong>+</strong> de cualquier plato para agregarlo.</div>`;
      return;
    }

    listEl.innerHTML = '';
    carrito.forEach((item) => {
      const sub = item.cantidad * item.precio_unitario;
      const div = document.createElement('div');
      div.className = 'cart-item';
      div.onclick = () => {
        itemSeleccionadoId = item.id_producto;
      };
      div.innerHTML = `
        <div class="cart-item-top">
          <span class="cart-item-name">${item.nombre}</span>
          <span class="cart-item-subtotal">S/ ${sub.toFixed(2)}</span>
        </div>
        <div class="cart-item-controls">
          <span style="font-size:12.5px;color:#6B7280;font-family:var(--font-mono);">P.U. S/ ${item.precio_unitario.toFixed(2)}</span>
          <div class="qty-stepper">
            <button type="button" class="btn-qty" data-action="dec">-</button>
            <span class="qty-value">${item.cantidad}</span>
            <button type="button" class="btn-qty" data-action="inc">+</button>
          </div>
        </div>
        <input type="text" class="cart-item-note-input" placeholder="Nota de cocina (ej. sin cremas, término 3/4)..." value="${item.notas || ''}" />
      `;
      div.querySelector('[data-action="dec"]').addEventListener('click', (e) => {
        e.stopPropagation();
        cambiarCantidadCarrito(item.id_producto, -1);
      });
      div.querySelector('[data-action="inc"]').addEventListener('click', (e) => {
        e.stopPropagation();
        cambiarCantidadCarrito(item.id_producto, 1);
      });
      div.querySelector('.cart-item-note-input').addEventListener('input', (e) => {
        actualizarNotaItem(item.id_producto, e.target.value);
      });
      listEl.appendChild(div);
    });
  }

  /**
   * Envía el pedido a `POST /api/pedidos` con `uuid_local` idempotente.
   * Si la API no responde (o está en modo Offline), lo guarda en IndexedDB (`outbox_pedidos`) con estado `PENDIENTE`.
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

      if (!res.ok) {
        throw new Error('Servidor API no disponible');
      }

      const data = await res.json();
      carrito = [];
      renderizarCarrito();
      await actualizarIndicadorConexion(true);
      mostrarToast(`✓ Comanda ${data.numero_comanda} enviada a Cocina (Mesa ${numeroMesa})`);
    } catch (_) {
      // Flujo Offline garantizado por IndexedDB (SPEC §4)
      await FoodCoreOfflineDB.guardarPedidoOutbox(pedidoPayload);
      carrito = [];
      renderizarCarrito();
      await actualizarIndicadorConexion(false);
      mostrarToast(`📦 Sin conexión: Pedido guardado en IndexedDB (outbox_pedidos PENDIENTE)`);
    }
  }

  function obtenerInicialesNombre(nombre) {
    if (!nombre) return 'FC';
    const partes = nombre.trim().split(/\s+/);
    if (partes.length === 1) return partes[0].slice(0, 2).toUpperCase();
    return (partes[0][0] + partes[1][0]).toUpperCase();
  }

  // =========================================================================
  // SUB-FASE C.1: MODO MOZO Y SEGURIDAD PIN (v4.2.0)
  // =========================================================================
  let pinActual = '';
  let usuarioSeleccionadoPin = null;
  let pinModalActual = '';
  let usuarioSeleccionadoModal = null;
  let usuariosActivosCache = [];

  async function iniciarPinLoginView() {
    registrarServiceWorker();
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

  async function cargarUsuariosActivosPinLogin() {
    const grid = document.getElementById('mozosGrid');
    if (!grid) return;

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
      grid.innerHTML = '';
      usuariosActivosCache.forEach((u, index) => {
        const card = document.createElement('div');
        const esActivo = index === 0;
        card.className = `mozo-card ${esActivo ? 'active' : ''}`;
        card.dataset.id = u.id || u.id_usuario;
        card.dataset.username = u.username;
        card.dataset.nombre = u.nombre;
        const iniciales = obtenerInicialesNombre(u.nombre);
        const esAdmin = (u.rol || '').toUpperCase().includes('ADMIN');

        card.innerHTML = `
          <div class="mozo-avatar-circle ${esAdmin ? 'admin' : ''}">${iniciales}</div>
          <div class="mozo-info">
            <span class="mozo-name">${u.nombre}</span>
            <span class="mozo-role-badge ${esAdmin ? 'badge-admin' : ''}">${u.rol || 'MOZO'}</span>
          </div>
          <span class="pin-ready-dot" title="${u.tiene_pin ? 'PIN configurado' : 'Sin PIN'}"></span>
        `;
        card.onclick = () => seleccionarUsuarioPin(card);
        grid.appendChild(card);

        if (esActivo) {
          usuarioSeleccionadoPin = {
            id: u.id || u.id_usuario,
            username: u.username,
            nombre: u.nombre
          };
          actualizarLabelTargetMozo();
        }
      });
    }
  }

  function seleccionarUsuarioPin(element) {
    document.querySelectorAll('.mozo-card').forEach(c => c.classList.remove('active'));
    element.classList.add('active');
    usuarioSeleccionadoPin = {
      id: parseInt(element.dataset.id, 10),
      username: element.dataset.username,
      nombre: element.dataset.nombre
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

  function presionarDigitoPin(digito) {
    if (pinActual.length >= 6) return;
    pinActual += digito;
    actualizarDotsPin();
    ocultarErrorPin();

    if (pinActual.length === 4) {
      ejecutarPinLogin();
    }
  }

  function borrarUltimoDigitoPin() {
    if (pinActual.length > 0) {
      pinActual = pinActual.slice(0, -1);
      actualizarDotsPin();
      ocultarErrorPin();
    }
  }

  function limpiarPin() {
    pinActual = '';
    actualizarDotsPin();
    ocultarErrorPin();
  }

  function actualizarDotsPin() {
    for (let i = 0; i < 4; i++) {
      const dot = document.getElementById(`dot-${i}`);
      if (dot) {
        if (i < pinActual.length) {
          dot.classList.add('filled');
        } else {
          dot.classList.remove('filled');
        }
      }
    }
    const input = document.getElementById('txtPinInput');
    if (input) {
      input.value = pinActual;
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
    if (pinActual.length < 4) {
      mostrarErrorPin('Ingrese un PIN de 4 dígitos.');
      return;
    }

    const payload = {
      pin: pinActual,
      id_usuario: usuarioSeleccionadoPin ? usuarioSeleccionadoPin.id : 0,
      username: usuarioSeleccionadoPin ? usuarioSeleccionadoPin.username : ''
    };

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
    }
  }

  // Modal Cambio Rápido de Mozo
  async function abrirModalCambiarMozo() {
    const modal = document.getElementById('modalCambiarMozo');
    if (!modal) return;
    modal.style.display = 'flex';
    pinModalActual = '';
    actualizarDotsPinModal();
    ocultarErrorPinModal();

    await cargarMozosParaModalSwitch();
  }

  function cerrarModalCambiarMozo() {
    const modal = document.getElementById('modalCambiarMozo');
    if (modal) {
      modal.style.display = 'none';
    }
    pinModalActual = '';
    actualizarDotsPinModal();
    ocultarErrorPinModal();
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
    container.innerHTML = '';
    if (!usuariosActivosCache || usuariosActivosCache.length === 0) {
      container.innerHTML = '<div style="padding:10px; color:#6B7280; font-size:13px;">No se pudieron cargar los operadores activos del servidor.</div>';
      return;
    }
    usuariosActivosCache.forEach((u, index) => {
      const chip = document.createElement('div');
      const esActual = u.username === currentUser.username;
      chip.className = `mozo-chip-modal ${index === 0 ? 'active' : ''}`;
      chip.dataset.id = u.id || u.id_usuario;
      chip.dataset.username = u.username;
      chip.dataset.nombre = u.nombre;
      chip.innerHTML = `
        <span class="mozo-avatar-mini">${obtenerInicialesNombre(u.nombre)}</span>
        <span>${u.nombre} ${esActual ? '(Actual)' : ''}</span>
      `;
      chip.onclick = () => {
        container.querySelectorAll('.mozo-chip-modal').forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        usuarioSeleccionadoModal = {
          id: parseInt(chip.dataset.id, 10),
          username: chip.dataset.username,
          nombre: chip.dataset.nombre
        };
        limpiarPinModal();
      };
      container.appendChild(chip);

      if (index === 0) {
        usuarioSeleccionadoModal = {
          id: u.id || u.id_usuario,
          username: u.username,
          nombre: u.nombre
        };
      }
    });
  }

  function presionarDigitoPinModal(digit) {
    if (pinModalActual.length >= 6) return;
    pinModalActual += digit;
    actualizarDotsPinModal();
    ocultarErrorPinModal();

    if (pinModalActual.length === 4) {
      confirmarCambioMozoPin();
    }
  }

  function borrarDigitoPinModal() {
    if (pinModalActual.length > 0) {
      pinModalActual = pinModalActual.slice(0, -1);
      actualizarDotsPinModal();
      ocultarErrorPinModal();
    }
  }

  function limpiarPinModal() {
    pinModalActual = '';
    actualizarDotsPinModal();
    ocultarErrorPinModal();
  }

  function actualizarDotsPinModal() {
    for (let i = 0; i < 4; i++) {
      const dot = document.getElementById(`mdot-${i}`);
      if (dot) {
        if (i < pinModalActual.length) {
          dot.classList.add('filled');
        } else {
          dot.classList.remove('filled');
        }
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
    if (pinModalActual.length < 4) {
      mostrarErrorPinModal('Ingrese un PIN de 4 dígitos.');
      return;
    }

    const payload = {
      pin: pinModalActual,
      id_usuario: usuarioSeleccionadoModal ? usuarioSeleccionadoModal.id : 0,
      username: usuarioSeleccionadoModal ? usuarioSeleccionadoModal.username : ''
    };

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
    }
  }

  async function alternarSimulacionOffline() {
    modoOfflineSimulado = !modoOfflineSimulado;
    const btn = document.getElementById('btnToggleOffline');
    if (btn) {
      btn.textContent = modoOfflineSimulado ? '⚡ Reconectar API' : '🔌 Simular Caída API';
    }
    if (!modoOfflineSimulado) {
      await sincronizarOutbox();
    } else {
      await actualizarIndicadorConexion(false);
      mostrarToast('🔌 Modo Offline activado: Los pedidos se guardarán en IndexedDB');
    }
  }

  return {
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
