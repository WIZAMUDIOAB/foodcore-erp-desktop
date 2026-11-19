/**
 * FOODCORE ERP v5.7.1 — PWA KDS (Kitchen Display System)
 * Tablero Kanban en tiempo real construido íntegramente con DOM seguro (createElement/textContent/addEventListener),
 * cronómetros dinámicos y sin datos demo falsos ante respuestas vacías o 401/403.
 */
const FoodCoreKDS = (() => {
  let itemsCocina = [];
  let filtroEstacion = 'TODOS';
  let pollInterval = null;
  let timerInterval = null;
  let clockInterval = null;
  const THEME_STORAGE_KEY = 'fc_theme';
  const SVG_NS = 'http://www.w3.org/2000/svg';

  function crearSvgElemento(tag, attrs = {}) {
    const el = document.createElementNS(SVG_NS, tag);
    Object.entries(attrs).forEach(([k, v]) => el.setAttribute(k, String(v)));
    return el;
  }

  function crearIconoTemaSvg(esOscuro) {
    const svg = crearSvgElemento('svg', {
      width: '18',
      height: '18',
      viewBox: '0 0 24 24',
      fill: 'none',
      stroke: 'currentColor',
      'stroke-width': '2',
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round'
    });
    if (esOscuro) {
      svg.appendChild(crearSvgElemento('circle', { cx: '12', cy: '12', r: '5' }));
      svg.appendChild(crearSvgElemento('path', {
        d: 'M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42'
      }));
    } else {
      svg.appendChild(crearSvgElemento('path', { d: 'M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z' }));
    }
    return svg;
  }

  function crearIconoAccionKds(estado) {
    const svg = crearSvgElemento('svg', {
      width: '16',
      height: '16',
      viewBox: '0 0 24 24',
      fill: 'none',
      stroke: 'currentColor',
      'stroke-width': estado === 'EN_PREPARACION' ? '2.4' : (estado === 'PENDIENTE' ? '2.2' : '2'),
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round'
    });
    if (estado === 'PENDIENTE') {
      svg.appendChild(crearSvgElemento('polygon', { points: '5 3 19 12 5 21 5 3' }));
    } else if (estado === 'EN_PREPARACION') {
      svg.appendChild(crearSvgElemento('polyline', { points: '20 6 9 17 4 12' }));
    } else {
      svg.appendChild(crearSvgElemento('path', {
        d: 'M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z'
      }));
    }
    return svg;
  }

  function resolverTemaInicial() {
    try {
      const saved = localStorage.getItem(THEME_STORAGE_KEY);
      if (saved === 'light' || saved === 'dark') {
        return saved;
      }
    } catch (_e) {}
    return 'dark';
  }

  function aplicarTemaKds(tema) {
    const esOscuro = tema !== 'light';
    document.documentElement.classList.toggle('dark', esOscuro);
    document.documentElement.classList.toggle('light', !esOscuro);
    document.documentElement.setAttribute('data-theme', esOscuro ? 'dark' : 'light');
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) {
      meta.setAttribute('content', esOscuro ? '#09090B' : '#F9F7F4');
    }
    const btn = document.getElementById('btnThemeToggle');
    if (btn) {
      btn.setAttribute('aria-pressed', esOscuro ? 'true' : 'false');
      btn.replaceChildren(crearIconoTemaSvg(esOscuro));
    }
  }

  function toggleTheme() {
    const next = document.documentElement.classList.contains('dark') ? 'light' : 'dark';
    try {
      localStorage.setItem(THEME_STORAGE_KEY, next);
    } catch (_e) {}
    aplicarTemaKds(next);
    return next;
  }

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
      } catch (_e) {
        mostrarModalAuthKds('No se pudo conectar con el servidor de autenticación.');
      }
    });
  }

  function init() {
    aplicarTemaKds(resolverTemaInicial());
    const btnTheme = document.getElementById('btnThemeToggle');
    if (btnTheme) {
      btnTheme.addEventListener('click', toggleTheme);
    }
    registrarServiceWorker();
    configurarAuthModal();
    iniciarReloj();
    cargarItemsKds();

    pollInterval = setInterval(cargarItemsKds, 5000);
    timerInterval = setInterval(actualizarCronometros, 1000);

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
        .catch(() => {});
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
    if (clockInterval) {
      clearInterval(clockInterval);
    }
    clockInterval = setInterval(tic, 1000);
  }

  async function cargarItemsKds() {
    try {
      const resp = await fetch('/api/kds/items', {
        method: 'GET',
        cache: 'no-store',
        headers: getAuthHeaders()
      });
      if (resp.status === 401 || resp.status === 403) {
        if (resp.status === 401) {
          try {
            localStorage.removeItem('fc_pwa_token');
            localStorage.removeItem('fc_pwa_user');
          } catch (_e) {}
        }
        itemsCocina = [];
        renderizarKanban();
        mostrarModalAuthKds(resp.status === 403 ? 'Rol sin permiso para KDS (requiere COCINA o ADMIN).' : '');
        actualizarPillConexion(true);
        return;
      }
      if (resp.ok) {
        ocultarModalAuthKds();
        const data = await resp.json();
        itemsCocina = Array.isArray(data)
          ? data.map((item) => ({
            ...item,
            timestamp: item.enviado_en ? new Date(item.enviado_en).getTime() : Date.now()
          }))
          : [];
        renderizarKanban();
        actualizarPillConexion(true);
        return;
      }
    } catch (_err) {
      actualizarPillConexion(false);
    }
    renderizarKanban();
  }

  function actualizarPillConexion(online) {
    const txt = document.getElementById('lblEstadoConexion');
    const dot = document.querySelector('.kds-dot');
    const pill = document.getElementById('kdsStatusPill');
    if (!txt || !dot) return;
    if (pill) {
      pill.classList.toggle('offline', !online);
    }
    if (online) {
      txt.textContent = 'En Línea';
      dot.style.background = 'var(--status-success)';
    } else {
      txt.textContent = 'Offline (Caché Local)';
      dot.style.background = 'var(--status-warning)';
    }
  }

  function crearMensajeColumnaVacia(texto) {
    const div = document.createElement('div');
    div.className = 'kds-empty-col';
    div.textContent = texto;
    return div;
  }

  function renderizarKanban() {
    const colPendiente = document.getElementById('colPendiente');
    const colPreparacion = document.getElementById('colPreparacion');
    const colListo = document.getElementById('colListo');

    if (!colPendiente || !colPreparacion || !colListo) return;

    colPendiente.replaceChildren();
    colPreparacion.replaceChildren();
    colListo.replaceChildren();

    let cPend = 0;
    let cPrep = 0;
    let cListo = 0;

    itemsCocina.forEach((item) => {
      const est = (item.estado_cocina || 'PENDIENTE').toUpperCase();
      const nombreLower = String(item.nombre || '').toLowerCase();

      if (filtroEstacion === 'BAR' && !nombreLower.includes('limonada') && !nombreLower.includes('bebida') && !nombreLower.includes('cerveza')) {
        return;
      }
      if (filtroEstacion === 'COCINA' && (nombreLower.includes('limonada') || nombreLower.includes('cerveza'))) {
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

    const elPend = document.getElementById('countPendiente');
    const elPrep = document.getElementById('countPreparacion');
    const elListo = document.getElementById('countListo');
    if (elPend) elPend.textContent = String(cPend);
    if (elPrep) elPrep.textContent = String(cPrep);
    if (elListo) elListo.textContent = String(cListo);

    if (cPend === 0) colPendiente.appendChild(crearMensajeColumnaVacia('No hay órdenes pendientes'));
    if (cPrep === 0) colPreparacion.appendChild(crearMensajeColumnaVacia('Nada en preparación'));
    if (cListo === 0) colListo.appendChild(crearMensajeColumnaVacia('No hay platos por entregar'));
  }

  function crearCardElemento(item, estado) {
    const card = document.createElement('div');
    const ts = Number.isFinite(Number(item.timestamp)) ? Number(item.timestamp) : Date.now();
    const diffSeg = Math.max(0, Math.floor((Date.now() - ts) / 1000));
    const timerClass = obtenerClaseTimer(diffSeg);
    const cardStateClass = timerClass.replace('timer-', 'state-');
    card.className = `kds-card ${cardStateClass}`;
    card.dataset.id = String(item.id_detalle_pedido ?? '');

    const cardTop = document.createElement('div');
    cardTop.className = 'kds-card-top';

    const mesaBadge = document.createElement('span');
    mesaBadge.className = 'kds-mesa-badge';
    mesaBadge.textContent = `Mesa ${item.numero_mesa || '01'}`;

    const timerBadge = document.createElement('span');
    timerBadge.className = `kds-timer-badge ${timerClass}`;
    timerBadge.dataset.ts = String(ts);
    timerBadge.textContent = `⏱ ${formatearTimer(diffSeg)}`;
    cardTop.append(mesaBadge, timerBadge);

    const dishTitle = document.createElement('div');
    dishTitle.className = 'kds-dish-title';

    const qtySpan = document.createElement('span');
    qtySpan.className = 'kds-qty';
    qtySpan.textContent = `x${Number(item.cantidad) || 1}`;

    const nameSpan = document.createElement('span');
    nameSpan.className = 'kds-name';
    nameSpan.textContent = String(item.nombre || '');
    dishTitle.append(qtySpan, nameSpan);

    card.append(cardTop, dishTitle);

    if (item.notas && String(item.notas).trim()) {
      const notesDiv = document.createElement('div');
      notesDiv.className = 'kds-notes';
      notesDiv.textContent = `Nota: ${String(item.notas)}`;
      card.appendChild(notesDiv);
    }

    const metaDiv = document.createElement('div');
    metaDiv.className = 'kds-card-meta';

    const comandaSpan = document.createElement('span');
    comandaSpan.textContent = `Comanda #${item.id_pedido || item.id_detalle_pedido || ''}`;

    const ticketSpan = document.createElement('span');
    ticketSpan.textContent = `Ticket #${item.id_detalle_pedido || ''}`;
    metaDiv.append(comandaSpan, ticketSpan);
    card.appendChild(metaDiv);

    let nextEstado = null;
    let btnClass = '';
    let btnTexto = '';
    if (estado === 'PENDIENTE') {
      nextEstado = 'EN_PREPARACION';
      btnClass = 'btn-iniciar';
      btnTexto = ' Iniciar Preparación';
    } else if (estado === 'EN_PREPARACION') {
      nextEstado = 'LISTO';
      btnClass = 'btn-listo';
      btnTexto = ' Marcar Listo';
    } else if (estado === 'LISTO') {
      nextEstado = 'ENTREGADO';
      btnClass = 'btn-entregar';
      btnTexto = ' Entregar al Mozo';
    }

    if (nextEstado) {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = `kds-btn-action ${btnClass}`;
      btn.append(crearIconoAccionKds(estado), document.createTextNode(btnTexto));
      btn.addEventListener('click', () => cambiarEstado(item.id_detalle_pedido, nextEstado));
      card.appendChild(btn);
    }

    return card;
  }

  function obtenerClaseTimer(segundos) {
    if (segundos < 300) return 'timer-green';
    if (segundos < 600) return 'timer-amber';
    return 'timer-red';
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
        const timerClass = obtenerClaseTimer(seg);
        badge.textContent = `⏱ ${formatearTimer(seg)}`;
        badge.className = `kds-timer-badge ${timerClass}`;
        const card = badge.closest('.kds-card');
        if (card) {
          card.classList.remove('state-green', 'state-amber', 'state-red');
          card.classList.add(timerClass.replace('timer-', 'state-'));
        }
      }
    });
  }

  async function cambiarEstado(idDetalle, nuevoEstado) {
    const item = itemsCocina.find((i) => i.id_detalle_pedido === idDetalle);
    if (item) {
      if (nuevoEstado === 'ENTREGADO') {
        itemsCocina = itemsCocina.filter((i) => i.id_detalle_pedido !== idDetalle);
      } else {
        item.estado_cocina = nuevoEstado;
      }
      renderizarKanban();
    }

    try {
      const resp = await fetch(`/api/kds/items/${encodeURIComponent(String(idDetalle))}/estado`, {
        method: 'PATCH',
        headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ estado: nuevoEstado })
      });
      if (resp.status === 401 || resp.status === 403) {
        mostrarModalAuthKds('Sesión requerida para actualizar comanda.');
      }
    } catch (_e) {
      actualizarPillConexion(false);
    }
  }

  return {
    init,
    cambiarEstado,
    cargarItemsKds,
    toggleTheme
  };
})();
