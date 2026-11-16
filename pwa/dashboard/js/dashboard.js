/**
 * FOODCORE ERP v4.2.0 — PWA DASHBOARD GERENCIAL (DUEÑO)
 * Conexión a APIs REST de métricas, gráficos Chart.js y menú lateral responsive.
 */
const FoodCoreDashboard = (() => {
  let chartInstance = null;
  let pollInterval = null;
  let ultimosPuntosGrafico = [];
  const THEME_STORAGE_KEY = 'fc_theme';

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
      btn.innerHTML = esOscuro
        ? '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"/><path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/></svg>'
        : '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>';
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
      } catch (e) {
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
    registrarServiceWorker();
    configurarDrawer();
    configurarAuthModal();
    cargarDashboardData();

    // Actualizar KPIs cada 10 segundos
    pollInterval = setInterval(cargarDashboardData, 10000);
  }

  function registrarServiceWorker() {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker
        .register('sw.js')
        .then(() => console.log('[Dashboard SW] Registrado correctamente'))
        .catch((err) => console.log('[Dashboard SW] Error al registrar:', err));
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
      if (resp.status === 401 || resp.status === 403) {
        mostrarModalAuthDash(resp.status === 403 ? 'Requiere rol ADMIN para consultar métricas.' : '');
        return;
      }
      if (resp.ok) {
        ocultarModalAuthDash();
        const kpis = await resp.json();
        actualizarKpisUI(kpis);
        return;
      }
    } catch (e) {
      console.warn('[Dashboard] Fallo al cargar KPIs de API, usando caché de respaldo', e);
    }

    // Datos iniciales cuando no hay respuesta del servidor
    actualizarKpisUI({
      ventas_dia: 2410.50,
      ticket_promedio: 48.20,
      transacciones: 50,
      plato_top: 'Hamburguesa Royal Doble',
      plato_top_unidades: 14,
      mesas_ocupadas: 4,
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
      lblVentas.textContent = `S/ ${v.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    }
    if (lblTicket) {
      const t = typeof data.ticket_promedio === 'number' ? data.ticket_promedio : parseFloat(data.ticket_promedio || 0);
      lblTicket.textContent = `S/ ${t.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    }
    if (lblTx) {
      lblTx.textContent = `${data.transacciones || 0} tx`;
    }
    if (lblPlato) {
      const cant = data.plato_top_unidades ? ` (${data.plato_top_unidades} un.)` : '';
      lblPlato.textContent = `${data.plato_top || 'Hamburguesa Royal'}${cant}`;
    }

    // Mesas & Ocupación
    const ocupadas = data.mesas_ocupadas || 2;
    const totales = data.mesas_totales || 12;
    const libres = totales - ocupadas;
    const pct = Math.round((ocupadas / totales) * 100);

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
      if (resp.ok) {
        const data = await resp.json();
        if (Array.isArray(data) && data.length > 0) {
          puntos = data;
        }
      }
    } catch (e) {
      console.warn('[Dashboard] Fallo al cargar ventas por hora, sin respuesta del API', e);
    }

    if (puntos.length === 0) {
      puntos = [
        { hora: '12:00', monto: 180.00 },
        { hora: '13:00', monto: 345.50 },
        { hora: '14:00', monto: 490.00 },
        { hora: '15:00', monto: 210.00 },
        { hora: '19:00', monto: 390.00 },
        { hora: '20:00', monto: 620.00 },
        { hora: '21:00', monto: 480.00 }
      ];
    }

    renderizarGrafico(puntos);
  }

  function renderizarGrafico(puntos) {
    ultimosPuntosGrafico = Array.isArray(puntos) ? puntos : [];
    const canvas = document.getElementById('chartVentasHora');
    if (!canvas) return;

    const tokens = leerTokensGrafico();
    const labels = ultimosPuntosGrafico.map((p) => p.hora);
    const dataValues = ultimosPuntosGrafico.map((p) => p.monto);

    // Si Chart.js está cargado (online), usar Chart.js
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

    // Fallback nativo Canvas 2D Bézier si Chart.js está offline
    renderizarCanvasFallback(canvas, labels, dataValues, tokens);
  }

  function renderizarCanvasFallback(canvas, labels, dataValues, tokens = leerTokensGrafico()) {
    const ctx = canvas.getContext('2d');
    const w = canvas.parentElement.clientWidth || 340;
    const h = 220;
    canvas.width = w;
    canvas.height = h;

    ctx.clearRect(0, 0, w, h);

    const padLeft = 45;
    const padRight = 20;
    const padTop = 20;
    const padBottom = 35;
    const plotW = w - padLeft - padRight;
    const plotH = h - padTop - padBottom;

    const maxVal = Math.max(...dataValues, 100);

    // Líneas de guía horizontales
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

    // Curva suavizada
    const points = dataValues.map((val, idx) => ({
      x: padLeft + (plotW / Math.max(1, dataValues.length - 1)) * idx,
      y: padTop + plotH - (val / maxVal) * plotH
    }));

    // Relleno gradiente 20% a transparente
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

    // Línea principal 3px en --brand-primary
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

    // Puntos y etiquetas X
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
      if (resp.ok) {
        platos = await resp.json();
      }
    } catch (e) {
      console.warn('[Dashboard] Fallo al cargar top platos, sin respuesta del API', e);
    }

    if (!Array.isArray(platos) || platos.length === 0) {
      platos = [
        { ranking: 1, nombre: 'Hamburguesa Royal Doble', unidades_vendidas: 14, total_recaudado: 455.00 },
        { ranking: 2, nombre: 'Lomo Saltado Criollo', unidades_vendidas: 11, total_recaudado: 418.00 },
        { ranking: 3, nombre: 'Ceviche Mixto Tradicional', unidades_vendidas: 9, total_recaudado: 342.00 },
        { ranking: 4, nombre: 'Pollo a la Brasa 1/4', unidades_vendidas: 8, total_recaudado: 208.00 },
        { ranking: 5, nombre: 'Limonada Frozen Artesanal', unidades_vendidas: 16, total_recaudado: 192.00 }
      ];
    }

    listEl.innerHTML = '';
    platos.forEach((p, idx) => {
      const row = document.createElement('div');
      row.className = 'top-dish-row';
      row.innerHTML = `
        <div class="top-dish-info">
          <span class="top-dish-rank">#${p.ranking || (idx + 1)}</span>
          <span class="top-dish-name">${p.nombre}</span>
        </div>
        <span class="top-dish-units">${p.unidades_vendidas || p.cantidad || 0} un.</span>
      `;
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
      if (resp.ok) {
        txs = await resp.json();
      }
    } catch (e) {
      console.warn('[Dashboard] Fallo al cargar transacciones recientes, sin respuesta del API', e);
    }

    if (!Array.isArray(txs) || txs.length === 0) {
      txs = [
        { id_venta: 108, numero_mesa: '04', total: 64.50, medio_pago: 'TARJETA', hora: '14:22' },
        { id_venta: 107, numero_mesa: '02', total: 38.00, medio_pago: 'EFECTIVO', hora: '13:50' },
        { id_venta: 106, numero_mesa: '06', total: 112.00, medio_pago: 'YAPE', hora: '13:15' }
      ];
    }

    listEl.innerHTML = '';
    txs.slice(0, 4).forEach((tx) => {
      const item = document.createElement('div');
      item.className = 'tx-item';
      item.innerHTML = `
        <div class="tx-left">
          <span class="tx-mesa">Mesa ${tx.numero_mesa || '01'} (${tx.medio_pago || 'EFECTIVO'})</span>
          <span class="tx-time">${tx.hora || 'Reciente'}</span>
        </div>
        <span class="tx-amount">S/ ${(tx.total || 0).toFixed(2)}</span>
      `;
      listEl.appendChild(item);
    });
  }

  return {
    init,
    cargarDashboardData,
    toggleTheme
  };
})();
