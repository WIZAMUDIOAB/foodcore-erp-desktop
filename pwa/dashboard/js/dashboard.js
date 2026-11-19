/**
 * FOODCORE ERP v5.7.1 — PWA DASHBOARD GERENCIAL (DUEÑO)
 * Conexión segura a APIs REST de métricas, DOM seguro con createElement/textContent, cierre de sesión real
 * y gráficos Chart.js sin datos demo fabricados.
 */
const FoodCoreDashboard = (() => {
  let chartInstance = null;
  let pollInterval = null;
  let ultimosPuntosGrafico = [];
  let redirigiendoLogin = false;
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

  function resolverTemaInicial() {
    try {
      const saved = localStorage.getItem(THEME_STORAGE_KEY);
      if (saved === 'dark' || saved === 'light') {
        return saved;
      }
    } catch (_e) {}
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  function aplicarTemaDashboard(tema) {
    const esOscuro = tema === 'dark';
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
    aplicarTemaDashboard(next);
    if (ultimosPuntosGrafico.length > 0) {
      renderizarGrafico(ultimosPuntosGrafico);
    }
    return next;
  }

  function leerTokensGrafico() {
    const cs = getComputedStyle(document.documentElement);
    return {
      brand: cs.getPropertyValue('--brand-primary').trim() || 'currentColor',
      fillStart: cs.getPropertyValue('--brand-chart-fill-start').trim(),
      fillEnd: cs.getPropertyValue('--brand-chart-fill-end').trim(),
      grid: cs.getPropertyValue('--border-subtle').trim(),
      tick: cs.getPropertyValue('--text-secondary').trim(),
      muted: cs.getPropertyValue('--text-muted').trim()
    };
  }

  function getAuthHeaders(extra = {}) {
    const token = localStorage.getItem('fc_pwa_token');
    const headers = { ...extra };
    if (token && token.trim() !== '') {
      headers.Authorization = `Bearer ${token.trim()}`;
    }
    return headers;
  }

  /**
   * Cierre de sesión real de la PWA desde Dashboard: limpia localStorage, sessionStorage e IndexedDB.
   */
  async function cerrarSesionPwa(redirectUrl = '../index.html') {
    if (pollInterval) {
      clearInterval(pollInterval);
      pollInterval = null;
    }
    try {
      localStorage.removeItem('fc_pwa_token');
      localStorage.removeItem('fc_pwa_user');
      localStorage.clear();
    } catch (_e) {}
    try {
      sessionStorage.clear();
    } catch (_e) {}
    try {
      if (typeof indexedDB !== 'undefined') {
        await new Promise((resolve) => {
          const req = indexedDB.deleteDatabase('foodcore_pwa_db');
          req.onsuccess = () => resolve(true);
          req.onerror = () => resolve(false);
          req.onblocked = () => resolve(false);
        });
      }
    } catch (_e) {}
    if (redirectUrl) {
      window.location.href = redirectUrl;
    }
  }

  function manejarNoAutorizado() {
    if (redirigiendoLogin) {
      return;
    }
    redirigiendoLogin = true;
    cerrarSesionPwa('../index.html');
  }

  function mostrarModalAuthDash(mensajeError = '') {
    const overlay = document.getElementById('dashAuthOverlay');
    const errEl = document.getElementById('dashLoginError');
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

  function ocultarModalAuthDash() {
    const overlay = document.getElementById('dashAuthOverlay');
    if (overlay) {
      overlay.style.display = 'none';
    }
  }

  function configurarAuthModal() {
    const btn = document.getElementById('btnDashLogin');
    if (!btn) return;
    btn.addEventListener('click', async () => {
      const userEl = document.getElementById('dashLoginUser');
      const passEl = document.getElementById('dashLoginPass');
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
          mostrarModalAuthDash(data.message || 'Credenciales inválidas.');
          return;
        }
        localStorage.setItem('fc_pwa_token', data.token);
        if (data.usuario) {
          localStorage.setItem('fc_pwa_user', JSON.stringify(data.usuario));
        }
        ocultarModalAuthDash();
        cargarDashboardData();
      } catch (_e) {
        mostrarModalAuthDash('No se pudo conectar con el servidor de autenticación.');
      }
    });
  }

  function init() {
    aplicarTemaDashboard(resolverTemaInicial());
    const btnTheme = document.getElementById('btnThemeToggle');
    if (btnTheme) {
      btnTheme.addEventListener('click', toggleTheme);
    }
    const btnLogout = document.getElementById('btnDashLogout');
    if (btnLogout) {
      btnLogout.addEventListener('click', (e) => {
        e.preventDefault();
        cerrarSesionPwa('../index.html');
      });
    }
    registrarServiceWorker();
    configurarDrawer();
    configurarAuthModal();
    cargarDashboardData();

    pollInterval = setInterval(cargarDashboardData, 10000);
  }

  function registrarServiceWorker() {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker
        .register('sw.js')
        .catch(() => {});
    }
  }

  function configurarDrawer() {
    const btnOpen = document.getElementById('btnOpenDrawer');
    const btnClose = document.getElementById('btnCloseDrawer');
    const drawer = document.getElementById('drawerMenu');
    const overlay = document.getElementById('drawerOverlay');

    if (!btnOpen || !drawer || !overlay) return;

    btnOpen.addEventListener('click', () => {
      drawer.classList.add('open');
      overlay.classList.add('active');
    });

    const cerrar = () => {
      drawer.classList.remove('open');
      overlay.classList.remove('active');
    };

    if (btnClose) btnClose.addEventListener('click', cerrar);
    overlay.addEventListener('click', cerrar);
  }

  async function cargarDashboardData() {
    if (redirigiendoLogin) {
      return;
    }
    await Promise.allSettled([
      cargarKpis(),
      cargarVentasPorHora(),
      cargarTopPlatos(),
      cargarVentasRecientes()
    ]);
  }

  async function cargarKpis() {
    try {
      const resp = await fetch('/api/dashboard/kpis', {
        method: 'GET',
        cache: 'no-store',
        headers: getAuthHeaders()
      });
      if (resp.status === 401) {
        manejarNoAutorizado();
        return;
      }
      if (resp.status === 403) {
        mostrarModalAuthDash('Requiere rol ADMIN para consultar métricas.');
        return;
      }
      if (resp.ok) {
        ocultarModalAuthDash();
        const kpis = await resp.json();
        actualizarKpisUI(kpis || {});
        return;
      }
    } catch (_e) {
      // Sin respuesta del servidor: mostrar estado vacío honesto sin KPIs falsos
    }

    actualizarKpisUI({
      ventas_dia: 0,
      ticket_promedio: 0,
      transacciones: 0,
      plato_top: '',
      plato_top_unidades: 0,
      mesas_ocupadas: 0,
      mesas_totales: 12
    });
  }

  function actualizarKpisUI(data) {
    const lblVentas = document.getElementById('lblVentasDia');
    const lblTicket = document.getElementById('lblTicketPromedio');
    const lblTx = document.getElementById('lblTransacciones');
    const lblPlato = document.getElementById('lblPlatoTop');

    if (lblVentas) {
      const v = typeof data.ventas_dia === 'number' ? data.ventas_dia : parseFloat(data.ventas_dia || 0);
      lblVentas.textContent = `S/ ${(Number.isFinite(v) ? v : 0).toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    }
    if (lblTicket) {
      const t = typeof data.ticket_promedio === 'number' ? data.ticket_promedio : parseFloat(data.ticket_promedio || 0);
      lblTicket.textContent = `S/ ${(Number.isFinite(t) ? t : 0).toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    }
    if (lblTx) {
      lblTx.textContent = `${Number(data.transacciones || 0)} tx`;
    }
    if (lblPlato) {
      const nombreTop = typeof data.plato_top === 'string' ? data.plato_top.trim() : '';
      if (nombreTop) {
        const cant = data.plato_top_unidades ? ` (${data.plato_top_unidades} un.)` : '';
        lblPlato.textContent = `${nombreTop}${cant}`;
      } else {
        lblPlato.textContent = 'Sin ventas hoy';
      }
    }

    const ocupadas = Math.max(0, Number(data.mesas_ocupadas ?? 0));
    const totales = Math.max(1, Number(data.mesas_totales ?? 12));
    const libres = Math.max(0, totales - ocupadas);
    const pct = Math.min(100, Math.round((ocupadas / totales) * 100));

    const lblOcupadas = document.getElementById('lblMesasOcupadas');
    const lblLibres = document.getElementById('lblMesasLibres');
    const progressFill = document.getElementById('progressMesasFill');

    if (lblOcupadas) lblOcupadas.textContent = `${ocupadas} Ocupadas (${pct}%)`;
    if (lblLibres) lblLibres.textContent = `${libres} Libres`;
    if (progressFill) progressFill.style.width = `${pct}%`;
  }

  async function cargarVentasPorHora() {
    let puntos = [];
    try {
      const resp = await fetch('/api/dashboard/ventas-por-hora', {
        method: 'GET',
        cache: 'no-store',
        headers: getAuthHeaders()
      });
      if (resp.status === 401) {
        manejarNoAutorizado();
        return;
      }
      if (resp.status === 403) {
        return;
      }
      if (resp.ok) {
        const data = await resp.json();
        if (Array.isArray(data)) {
          puntos = data;
        }
      }
    } catch (_e) {
      puntos = [];
    }

    renderizarGrafico(puntos);
  }

  function renderizarGrafico(puntos) {
    ultimosPuntosGrafico = Array.isArray(puntos) ? puntos : [];
    const canvas = document.getElementById('chartVentasHora');
    if (!canvas) return;

    const tokens = leerTokensGrafico();
    const labels = ultimosPuntosGrafico.map((p) => String(p.hora || ''));
    const dataValues = ultimosPuntosGrafico.map((p) => Number(p.monto || 0));

    if (typeof Chart !== 'undefined') {
      const ctx = canvas.getContext('2d');
      if (chartInstance) {
        chartInstance.destroy();
      }

      const gradient = ctx.createLinearGradient(0, 0, 0, 220);
      gradient.addColorStop(0, tokens.fillStart);
      gradient.addColorStop(1, tokens.fillEnd);

      chartInstance = new Chart(ctx, {
        type: 'line',
        data: {
          labels: labels,
          datasets: [{
            label: 'Ventas (S/)',
            data: dataValues,
            borderColor: tokens.brand,
            backgroundColor: gradient,
            borderWidth: 3,
            fill: true,
            tension: 0.4,
            pointBackgroundColor: tokens.brand,
            pointRadius: 4,
            pointHoverRadius: 6
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { display: false }
          },
          scales: {
            x: {
              grid: { display: false },
              ticks: { color: tokens.muted || tokens.tick, font: { family: 'Inter', size: 11 } }
            },
            y: {
              beginAtZero: true,
              grid: { color: tokens.grid },
              ticks: {
                color: tokens.muted || tokens.tick,
                font: { family: 'Inter', size: 11 },
                callback: (v) => `S/${v}`
              }
            }
          }
        }
      });
      return;
    }

    renderizarCanvasFallback(canvas, labels, dataValues, tokens);
  }

  function renderizarCanvasFallback(canvas, labels, dataValues, tokens = leerTokensGrafico()) {
    const ctx = canvas.getContext('2d');
    const w = canvas.parentElement.clientWidth || 340;
    const h = 220;
    canvas.width = w;
    canvas.height = h;

    ctx.clearRect(0, 0, w, h);
    if (!Array.isArray(dataValues) || dataValues.length === 0) {
      ctx.fillStyle = tokens.muted || tokens.tick;
      ctx.font = '12px Inter, sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('Sin ventas registradas por hora', w / 2, h / 2);
      return;
    }

    const padLeft = 45;
    const padRight = 20;
    const padTop = 20;
    const padBottom = 35;
    const plotW = w - padLeft - padRight;
    const plotH = h - padTop - padBottom;

    const maxVal = Math.max(...dataValues, 100);

    ctx.strokeStyle = tokens.grid;
    ctx.lineWidth = 1;
    for (let i = 0; i <= 4; i++) {
      const y = padTop + (plotH / 4) * i;
      ctx.beginPath();
      ctx.moveTo(padLeft, y);
      ctx.lineTo(w - padRight, y);
      ctx.stroke();

      const labelVal = Math.round(maxVal - (maxVal / 4) * i);
      ctx.fillStyle = tokens.muted || tokens.tick;
      ctx.font = '11px Inter, sans-serif';
      ctx.textAlign = 'right';
      ctx.fillText(`S/${labelVal}`, padLeft - 6, y + 4);
    }

    const points = dataValues.map((val, idx) => ({
      x: padLeft + (plotW / Math.max(1, dataValues.length - 1)) * idx,
      y: padTop + plotH - (val / maxVal) * plotH
    }));

    const grad = ctx.createLinearGradient(0, padTop, 0, padTop + plotH);
    grad.addColorStop(0, tokens.fillStart);
    grad.addColorStop(1, tokens.fillEnd);

    ctx.beginPath();
    ctx.moveTo(points[0].x, padTop + plotH);
    ctx.lineTo(points[0].x, points[0].y);
    for (let i = 0; i < points.length - 1; i++) {
      const xc = (points[i].x + points[i + 1].x) / 2;
      const yc = (points[i].y + points[i + 1].y) / 2;
      ctx.quadraticCurveTo(points[i].x, points[i].y, xc, yc);
    }
    ctx.lineTo(points[points.length - 1].x, points[points.length - 1].y);
    ctx.lineTo(points[points.length - 1].x, padTop + plotH);
    ctx.closePath();
    ctx.fillStyle = grad;
    ctx.fill();

    ctx.beginPath();
    ctx.moveTo(points[0].x, points[0].y);
    for (let i = 0; i < points.length - 1; i++) {
      const xc = (points[i].x + points[i + 1].x) / 2;
      const yc = (points[i].y + points[i + 1].y) / 2;
      ctx.quadraticCurveTo(points[i].x, points[i].y, xc, yc);
    }
    ctx.lineTo(points[points.length - 1].x, points[points.length - 1].y);
    ctx.strokeStyle = tokens.brand;
    ctx.lineWidth = 3;
    ctx.stroke();

    points.forEach((pt, idx) => {
      ctx.beginPath();
      ctx.arc(pt.x, pt.y, 4, 0, Math.PI * 2);
      ctx.fillStyle = tokens.brand;
      ctx.fill();

      ctx.fillStyle = tokens.tick;
      ctx.font = '11px Inter, sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(labels[idx] || '', pt.x, h - 12);
    });
  }

  function formatearFechaHoraTx(fechaHoraRaw) {
    if (!fechaHoraRaw) return '';
    const raw = String(fechaHoraRaw).trim();
    const matchHora = raw.match(/(\d{2}:\d{2})/);
    return matchHora ? matchHora[1] : raw;
  }

  async function cargarTopPlatos() {
    const listEl = document.getElementById('topPlatosList');
    if (!listEl) return;

    let platos = [];
    try {
      const resp = await fetch('/api/dashboard/top-platos', {
        method: 'GET',
        cache: 'no-store',
        headers: getAuthHeaders()
      });
      if (resp.status === 401) {
        manejarNoAutorizado();
        return;
      }
      if (resp.status === 403) {
        return;
      }
      if (resp.ok) {
        const data = await resp.json();
        if (Array.isArray(data)) {
          platos = data;
        }
      }
    } catch (_e) {
      platos = [];
    }

    listEl.replaceChildren();
    if (platos.length === 0) {
      const empty = document.createElement('div');
      empty.className = 'top-dish-row';
      const emptyText = document.createElement('span');
      emptyText.className = 'top-dish-name';
      emptyText.textContent = 'Sin platillos vendidos hoy';
      empty.appendChild(emptyText);
      listEl.appendChild(empty);
      return;
    }

    platos.forEach((p, idx) => {
      const row = document.createElement('div');
      row.className = 'top-dish-row';

      const info = document.createElement('div');
      info.className = 'top-dish-info';

      const rank = document.createElement('span');
      rank.className = 'top-dish-rank';
      rank.textContent = `#${p.ranking || (idx + 1)}`;

      const name = document.createElement('span');
      name.className = 'top-dish-name';
      name.textContent = String(p.nombre || '');
      info.append(rank, name);

      const units = document.createElement('span');
      units.className = 'top-dish-units';
      const cantidad = p.unidades ?? p.unidades_vendidas ?? p.cantidad ?? 0;
      units.textContent = `${Number(cantidad) || 0} un.`;

      row.append(info, units);
      listEl.appendChild(row);
    });
  }

  async function cargarVentasRecientes() {
    const listEl = document.getElementById('recentTxList');
    if (!listEl) return;

    let txs = [];
    try {
      const resp = await fetch('/api/dashboard/ventas-recientes', {
        method: 'GET',
        cache: 'no-store',
        headers: getAuthHeaders()
      });
      if (resp.status === 401) {
        manejarNoAutorizado();
        return;
      }
      if (resp.status === 403) {
        return;
      }
      if (resp.ok) {
        const data = await resp.json();
        if (Array.isArray(data)) {
          txs = data;
        }
      }
    } catch (_e) {
      txs = [];
    }

    listEl.replaceChildren();
    if (txs.length === 0) {
      const empty = document.createElement('div');
      empty.className = 'tx-item';
      const label = document.createElement('span');
      label.className = 'tx-time';
      label.textContent = 'Sin transacciones recientes';
      empty.appendChild(label);
      listEl.appendChild(empty);
      return;
    }

    txs.slice(0, 4).forEach((tx) => {
      const item = document.createElement('div');
      item.className = 'tx-item';

      const left = document.createElement('div');
      left.className = 'tx-left';

      const primary = document.createElement('span');
      primary.className = 'tx-mesa';
      const medio = tx.medio_pago || 'EFECTIVO';
      primary.textContent = tx.numero_comprobante
        ? `${tx.numero_comprobante} (${medio})`
        : `Mesa ${tx.numero_mesa || '01'} (${medio})`;

      const time = document.createElement('span');
      time.className = 'tx-time';
      time.textContent = tx.hora || formatearFechaHoraTx(tx.fecha_hora) || 'Reciente';
      left.append(primary, time);

      const amount = document.createElement('span');
      amount.className = 'tx-amount';
      amount.textContent = `S/ ${Number(tx.total || 0).toFixed(2)}`;

      item.append(left, amount);
      listEl.appendChild(item);
    });
  }

  return {
    init,
    cargarDashboardData,
    cerrarSesionPwa,
    toggleTheme
  };
})();
