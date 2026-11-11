/**
 * FOODCORE ERP v4.2.0 — PWA KDS (Kitchen Display System)
 * Lógica de Kanban en tiempo real, cronómetros dinámicos y transiciones de comandas.
 */
const FoodCoreKDS = (() => {
  let itemsCocina = [];
  let filtroEstacion = 'TODOS';
  let pollInterval = null;
  let timerInterval = null;

  function getAuthHeaders(extra = {}) {
    const token = localStorage.getItem('fc_pwa_token');
    const headers = { ...extra };
    if (token && token.trim() !== '') {
      headers.Authorization = `Bearer ${token.trim()}`;
    }
    return headers;
  }

  function mostrarModalAuthKds(mensajeError = '') {
    const overlay = document.getElementById('kdsAuthOverlay');
    const errEl = document.getElementById('kdsLoginError');
    if (overlay) {
      overlay.style.display = 'flex';
    }
    if (errEl) {
      if (mensajeError) {
        errEl.textContent = mensajeError;
        errEl.style.display = 'block';
      } else {
        errEl.style.display = 'none';
      }
    }
  }

  function ocultarModalAuthKds() {
    const overlay = document.getElementById('kdsAuthOverlay');
    if (overlay) {
      overlay.style.display = 'none';
    }
  }

  function configurarAuthModal() {
    const btn = document.getElementById('btnKdsLogin');
    if (!btn) return;
    btn.addEventListener('click', async () => {
      const userEl = document.getElementById('kdsLoginUser');
      const passEl = document.getElementById('kdsLoginPass');
      const username = userEl ? userEl.value.trim() : '';
      const password = passEl ? passEl.value.trim() : '';
      try {
        const resp = await fetch('/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password })
        });
        const data = await resp.json();
        if (!resp.ok || !data.token) {
          mostrarModalAuthKds(data.message || 'Credenciales inválidas.');
          return;
        }
        localStorage.setItem('fc_pwa_token', data.token);
        if (data.usuario) {
          localStorage.setItem('fc_pwa_user', JSON.stringify(data.usuario));
        }
        ocultarModalAuthKds();
        cargarItemsKds();
      } catch (e) {
        mostrarModalAuthKds('No se pudo conectar con el servidor de autenticación.');
      }
    });
  }

  function init() {
    registrarServiceWorker();
    configurarAuthModal();
    iniciarReloj();
    cargarItemsKds();

    // Polling cada 5 segundos
    pollInterval = setInterval(cargarItemsKds, 5000);

    // Actualización de cronómetros cada segundo
    timerInterval = setInterval(actualizarCronometros, 1000);

    // Eventos de filtros de estación
    document.querySelectorAll('.kds-filter-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.kds-filter-btn').forEach((b) => b.classList.remove('active'));
        btn.classList.add('active');
        filtroEstacion = btn.dataset.estacion || 'TODOS';
        renderizarKanban();
      });
    });
  }

  function registrarServiceWorker() {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker
        .register('sw.js')
        .then(() => console.log('[KDS SW] Registrado correctamente'))
        .catch((err) => console.log('[KDS SW] Error al registrar:', err));
    }
  }

  function iniciarReloj() {
    const el = document.getElementById('lblReloj');
    if (!el) return;
    function tic() {
      const d = new Date();
      el.textContent = d.toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
    }
    tic();
    setInterval(tic, 1000);
  }

  async function cargarItemsKds() {
    try {
      const resp = await fetch('/api/kds/items', {
        method: 'GET',
        cache: 'no-store',
        headers: getAuthHeaders()
      });
      if (resp.status === 401 || resp.status === 403) {
        mostrarModalAuthKds(resp.status === 403 ? 'Rol sin permiso para KDS (requiere COCINA o ADMIN).' : '');
        actualizarPillConexion(true);
        return;
      }
      if (resp.ok) {
        ocultarModalAuthKds();
        const data = await resp.json();
        if (Array.isArray(data) && data.length > 0) {
          // Normalizar enviado_en a timestamp
          itemsCocina = data.map((item) => ({
            ...item,
            timestamp: item.enviado_en ? new Date(item.enviado_en).getTime() : Date.now()
          }));
          renderizarKanban();
          actualizarPillConexion(true);
          return;
        }
      }
    } catch (err) {
      console.warn('[KDS] Error al consultar API, usando estado local', err);
      actualizarPillConexion(false);
    }

    // Si la lista está vacía, cargar ítems de demostración iniciales
    if (itemsCocina.length === 0) {
      const now = Date.now();
      itemsCocina = [
        {
          id_detalle_pedido: 501,
          id_pedido: 101,
          numero_mesa: '04',
          nombre: 'Hamburguesa Royal Doble',
          cantidad: 2,
          notas: 'Sin cebolla, papas crocantes',
          estado_cocina: 'PENDIENTE',
          timestamp: now - 3 * 60 * 1000 // 3 min atrás (verde)
        },
        {
          id_detalle_pedido: 502,
          id_pedido: 102,
          numero_mesa: '02',
          nombre: 'Lomo Saltado Clásico Criollo',
          cantidad: 1,
          notas: 'Término medio 3/4',
          estado_cocina: 'EN_PREPARACION',
          timestamp: now - 7 * 60 * 1000 // 7 min atrás (ámbar)
        },
        {
          id_detalle_pedido: 503,
          id_pedido: 103,
          numero_mesa: '06',
          nombre: 'Ceviche Mixto con Chicharrón',
          cantidad: 1,
          notas: 'Ají moderado, camote glaseado',
          estado_cocina: 'PENDIENTE',
          timestamp: now - 12 * 60 * 1000 // 12 min atrás (rojo)
        },
        {
          id_detalle_pedido: 504,
          id_pedido: 104,
          numero_mesa: '01',
          nombre: 'Limonada Frozen Artesanal',
          cantidad: 2,
          notas: 'Con hierbabuena',
          estado_cocina: 'LISTO',
          timestamp: now - 4 * 60 * 1000
        }
      ];
      renderizarKanban();
    }
  }

  function actualizarPillConexion(online) {
    const txt = document.getElementById('lblEstadoConexion');
    const dot = document.querySelector('.kds-dot');
    if (!txt || !dot) return;
    if (online) {
      txt.textContent = 'En Línea';
      dot.style.background = 'var(--kds-success)';
    } else {
      txt.textContent = 'Offline (Caché Local)';
      dot.style.background = 'var(--kds-warning)';
    }
  }

  function renderizarKanban() {
    const colPendiente = document.getElementById('colPendiente');
    const colPreparacion = document.getElementById('colPreparacion');
    const colListo = document.getElementById('colListo');

    if (!colPendiente || !colPreparacion || !colListo) return;

    colPendiente.innerHTML = '';
    colPreparacion.innerHTML = '';
    colListo.innerHTML = '';

    let cPend = 0;
    let cPrep = 0;
    let cListo = 0;

    itemsCocina.forEach((item) => {
      const est = (item.estado_cocina || 'PENDIENTE').toUpperCase();

      // Aplicar filtro estación si es bar/bebidas
      if (filtroEstacion === 'BAR' && !item.nombre.toLowerCase().includes('limonada') && !item.nombre.toLowerCase().includes('bebida') && !item.nombre.toLowerCase().includes('cerveza')) {
        return;
      }
      if (filtroEstacion === 'COCINA' && (item.nombre.toLowerCase().includes('limonada') || item.nombre.toLowerCase().includes('cerveza'))) {
        return;
      }

      if (est === 'PENDIENTE') {
        colPendiente.appendChild(crearCardElemento(item, 'PENDIENTE'));
        cPend++;
      } else if (est === 'EN_PREPARACION') {
        colPreparacion.appendChild(crearCardElemento(item, 'EN_PREPARACION'));
        cPrep++;
      } else if (est === 'LISTO') {
        colListo.appendChild(crearCardElemento(item, 'LISTO'));
        cListo++;
      }
    });

    document.getElementById('countPendiente').textContent = cPend;
    document.getElementById('countPreparacion').textContent = cPrep;
    document.getElementById('countListo').textContent = cListo;

    if (cPend === 0) colPendiente.innerHTML = '<div class="kds-empty-col">No hay órdenes pendientes</div>';
    if (cPrep === 0) colPreparacion.innerHTML = '<div class="kds-empty-col">Nada en preparación</div>';
    if (cListo === 0) colListo.innerHTML = '<div class="kds-empty-col">No hay platos por entregar</div>';
  }

  function crearCardElemento(item, estado) {
    const card = document.createElement('div');
    card.className = 'kds-card';
    card.dataset.id = item.id_detalle_pedido;

    const diffSeg = Math.max(0, Math.floor((Date.now() - (item.timestamp || Date.now())) / 1000));
    const timerClass = obtenerClaseTimer(diffSeg);
    const timerText = formatearTimer(diffSeg);

    let btnHtml = '';
    if (estado === 'PENDIENTE') {
      btnHtml = `<button type="button" class="kds-btn-action btn-iniciar" onclick="FoodCoreKDS.cambiarEstado(${item.id_detalle_pedido}, 'EN_PREPARACION')">▶ Iniciar Preparación</button>`;
    } else if (estado === 'EN_PREPARACION') {
      btnHtml = `<button type="button" class="kds-btn-action btn-listo" onclick="FoodCoreKDS.cambiarEstado(${item.id_detalle_pedido}, 'LISTO')">✓ Marcar Listo</button>`;
    } else if (estado === 'LISTO') {
      btnHtml = `<button type="button" class="kds-btn-action btn-entregar" onclick="FoodCoreKDS.cambiarEstado(${item.id_detalle_pedido}, 'ENTREGADO')">📦 Entregar al Mozo</button>`;
    }

    const notasHtml = item.notas ? `<div class="kds-notes">⚠️ ${item.notas}</div>` : '';

    card.innerHTML = `
      <div class="kds-card-top">
        <span class="kds-mesa-badge">Mesa ${item.numero_mesa || '01'}</span>
        <span class="kds-timer-badge ${timerClass}" data-ts="${item.timestamp || Date.now()}">⏱ ${timerText}</span>
      </div>
      <div class="kds-dish-title">
        <span class="kds-qty">x${item.cantidad}</span>
        <span class="kds-name">${item.nombre}</span>
      </div>
      ${notasHtml}
      <div class="kds-card-meta">
        <span>Comanda #${item.id_pedido || item.id_detalle_pedido}</span>
        <span>Ticket #${item.id_detalle_pedido}</span>
      </div>
      ${btnHtml}
    `;

    return card;
  }

  function obtenerClaseTimer(segundos) {
    if (segundos < 300) return 'timer-green'; // < 5 min
    if (segundos < 600) return 'timer-amber'; // 5 - 10 min
    return 'timer-red'; // > 10 min
  }

  function formatearTimer(segundos) {
    const m = Math.floor(segundos / 60);
    const s = segundos % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  }

  function actualizarCronometros() {
    document.querySelectorAll('.kds-timer-badge').forEach((badge) => {
      const ts = parseInt(badge.dataset.ts, 10);
      if (ts) {
        const seg = Math.max(0, Math.floor((Date.now() - ts) / 1000));
        badge.textContent = `⏱ ${formatearTimer(seg)}`;
        badge.className = `kds-timer-badge ${obtenerClaseTimer(seg)}`;
      }
    });
  }

  async function cambiarEstado(idDetalle, nuevoEstado) {
    // 1. Optimistic UI update
    const item = itemsCocina.find((i) => i.id_detalle_pedido === idDetalle);
    if (item) {
      if (nuevoEstado === 'ENTREGADO') {
        itemsCocina = itemsCocina.filter((i) => i.id_detalle_pedido !== idDetalle);
      } else {
        item.estado_cocina = nuevoEstado;
      }
      renderizarKanban();
    }

    // 2. Notificar al Backend Javalin
    try {
      const resp = await fetch(`/api/kds/items/${idDetalle}/estado`, {
        method: 'PATCH',
        headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ estado: nuevoEstado })
      });
      if (resp.status === 401) {
        mostrarModalAuthKds('Sesión requerida para actualizar comanda.');
      }
    } catch (e) {
      console.warn('[KDS] Error al sincronizar estado con backend', e);
    }
  }

  return {
    init,
    cambiarEstado,
    cargarItemsKds
  };
})();
