/**
 * Controlador Principal Frontend — FoodCore ERP v4.0 Tablet PWA (SPEC-FOODCORE-v4.0 §3, §4)
 * Gestiona autenticación JWT, selección de mesas, toma de comanda táctil, envío a KDS
 * y sincronización automática cada 10s desde IndexedDB (`outbox_pedidos`).
 */
const FoodCorePWA = (() => {
  const API_BASE = (window.location.port === '8080')
    ? `${window.location.origin}/api`
    : 'http://localhost:8080/api';

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
      imagen_url: ''
    },
    {
      id: 2,
      codigo: 'HAM-02',
      nombre: 'Hamburguesa Clásica',
      descripcion: 'Carne 180g al carbón, queso cheddar fundido y vegetales',
      precio: 18.00,
      categoria: 'Hamburguesas',
      imagen_url: ''
    },
    {
      id: 3,
      codigo: 'ALI-01',
      nombre: 'Alitas BBQ Picantes',
      descripcion: '6 alitas bañadas en salsa artesanal BBQ ligeramente picante',
      precio: 22.00,
      categoria: 'Alitas & Pollo',
      imagen_url: ''
    },
    {
      id: 4,
      codigo: 'CRIS-01',
      nombre: 'Combo Pollo Crispy',
      descripcion: '3 piezas broaster crocantes con papas amarillas nativas',
      precio: 26.00,
      categoria: 'Alitas & Pollo',
      imagen_url: ''
    },
    {
      id: 5,
      codigo: 'BEB-01',
      nombre: 'Limonada Frozen',
      descripcion: 'Jarra de limonada natural frappeada con hierbabuena',
      precio: 14.00,
      categoria: 'Bebidas',
      imagen_url: ''
    },
    {
      id: 6,
      codigo: 'BEB-02',
      nombre: 'Chicha Morada',
      descripcion: 'Receta tradicional de maíz morado con manzana y canela',
      precio: 12.00,
      categoria: 'Bebidas',
      imagen_url: ''
    },
    {
      id: 7,
      codigo: 'COM-01',
      nombre: 'Papas Fritas Supremas',
      descripcion: 'Porción familiar con salsa de queso cheddar y tocino',
      precio: 11.50,
      categoria: 'Guarniciones',
      imagen_url: ''
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
      return raw ? JSON.parse(raw) : { id: 3, username: 'mozo', nombre: 'Mateo Quispe (Mozo)', rol: 'MOZO' };
    } catch (_) {
      return { id: 3, username: 'mozo', nombre: 'Mateo Quispe (Mozo)', rol: 'MOZO' };
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
      text.textContent = estadoOnline ? 'API Online (8080)' : 'Modo Offline · IndexedDB';
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
            headers: { 'Content-Type': 'application/json' },
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
        // Login resiliente en caso de inicio offline
        const fallbackUser = {
          id: 3,
          username: username || 'mozo',
          nombre: username === 'admin' ? 'Administrador General' : 'Mateo Quispe (Mozo)',
          rol: username === 'admin' ? 'ADMIN' : 'MOZO'
        };
        localStorage.setItem('fc_pwa_user', JSON.stringify(fallbackUser));
        window.location.href = 'mesas.html';
      }
    });
  }

  function rellenarCredencialesDemo(user, pass) {
    const u = document.getElementById('txtUsername');
    const p = document.getElementById('txtPassword');
    if (u && p) {
      u.value = user;
      p.value = pass;
    }
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
      const res = await fetch(`${API_BASE}/mesas`, { method: 'GET', cache: 'no-store' });
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
  }

  async function cargarCatalogoProductos() {
    try {
      const res = await fetch(`${API_BASE}/productos`, { method: 'GET', cache: 'no-store' });
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
    renderizarCatalogo();
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
      const coincideCat = categoriaActiva === 'TODOS'
        || (p.categoria && p.categoria.toLowerCase().includes(categoriaActiva.toLowerCase()));
      const coincideTexto = !textoBusqueda
        || p.nombre.toLowerCase().includes(textoBusqueda)
        || (p.descripcion && p.descripcion.toLowerCase().includes(textoBusqueda));
      return coincideCat && coincideTexto;
    });

    filtrados.forEach((p) => {
      const card = document.createElement('div');
      card.className = 'dish-card';
      const iniciales = obtenerInicialesProducto(p.nombre);
      const imgUrl = (typeof p.imagen_url === 'string') ? p.imagen_url.trim() : '';
      const tieneImagenValida = imgUrl !== '';

      const mediaHtml = tieneImagenValida
        ? `<img class="dish-thumb" src="${imgUrl}" alt="${p.nombre}" style="object-fit: cover;" />`
        : `<div class="dish-thumb-fallback" aria-label="${p.nombre}">${iniciales}</div>`;

      card.innerHTML = `
        <div class="dish-thumb-wrapper">${mediaHtml}</div>
        <div class="dish-body">
          <span class="dish-category-tag">${p.categoria || 'Especialidad'}</span>
          <div class="dish-title">${p.nombre}</div>
          <div class="dish-desc">${p.descripcion || 'Preparación al instante en cocina'}</div>
          <div class="dish-footer">
            <span class="dish-price">S/ ${Number(p.precio).toFixed(2)}</span>
            <button type="button" class="btn-add-dish" aria-label="Agregar ${p.nombre}">+</button>
          </div>
        </div>
      `;

      const imgEl = card.querySelector('img.dish-thumb');
      if (imgEl) {
        imgEl.addEventListener('error', () => {
          const fallbackDiv = document.createElement('div');
          fallbackDiv.className = 'dish-thumb-fallback';
          fallbackDiv.setAttribute('aria-label', p.nombre);
          fallbackDiv.textContent = iniciales;
          imgEl.replaceWith(fallbackDiv);
        });
      }

      card.querySelector('.btn-add-dish').addEventListener('click', () => {
        agregarAlCarritoPorObjeto(p, 1, '');
      });
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
        headers: { 'Content-Type': 'application/json' },
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
    rellenarCredencialesDemo,
    iniciarMesasView,
    cargarMesas,
    iniciarCatalogoView,
    cargarCatalogoProductos,
    filtrarCategoria,
    obtenerInicialesProducto,
    aplicarChipNota,
    enviarPedidoACocina,
    alternarSimulacionOffline,
    sincronizarOutbox
  };
})();
