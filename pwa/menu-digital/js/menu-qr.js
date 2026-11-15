(function () {
  'use strict';

  const FALLBACK_CATALOG = [
    {
      id: 1,
      nombre: 'Hamburguesa Royal Doble',
      descripcion: 'Doble carne angus 180g, huevo, queso cheddar y papas artesanales.',
      precio: 24.50,
      categoria: 'Hamburguesas'
    },
    {
      id: 2,
      nombre: 'Cheese Burger Clasica',
      descripcion: 'Carne 180g a la parrilla, queso cheddar fundido, lechuga y tomate.',
      precio: 18.00,
      categoria: 'Hamburguesas'
    },
    {
      id: 3,
      nombre: 'Alitas BBQ Picantes x6',
      descripcion: '6 alitas crocantes banadas en salsa artesanal BBQ ligeramente picante.',
      precio: 22.00,
      categoria: 'Alitas & Pollo'
    },
    {
      id: 4,
      nombre: 'Combo Pollo Crispy 3 Pzs',
      descripcion: '3 piezas broaster crocantes acompañadas de papas amarillas.',
      precio: 26.00,
      categoria: 'Alitas & Pollo'
    },
    {
      id: 5,
      nombre: 'Limonada Frozen 1L',
      descripcion: 'Limonada natural frappeada con hojas frescas de hierbabuena.',
      precio: 14.00,
      categoria: 'Bebidas'
    },
    {
      id: 6,
      nombre: 'Chicha Morada 1L',
      descripcion: 'Receta tradicional con maiz morado, manzana y canela.',
      precio: 12.00,
      categoria: 'Bebidas'
    },
    {
      id: 7,
      nombre: 'Papas Fritas Supremas',
      descripcion: 'Porcion gigante de papas con queso fundido y tocino crocante.',
      precio: 11.50,
      categoria: 'Guarniciones'
    }
  ];

  const LANG_STORAGE_KEY = 'fc_menu_qr_lang';
  const DEFAULT_I18N = {
    es: {
      pageTitle: 'FoodCore Gastronomía · Menú Digital',
      subtitle: 'Carta Gastronómica Oficial · Actualizada en Tiempo Real',
      badgeLive: '● Disponible para Pedidos',
      heroKicker: 'EXPERIENCIA CULINARIA EN VIVO',
      heroTitle: 'Sabores Artesanales a la Parrilla',
      cardAction: 'Especialidad',
      summaryLabel: 'Total estimado',
      resetLabel: 'Limpiar',
      itemSingular: 'ítem',
      itemPlural: 'ítems',
      footerText: 'FoodCore ERP v5.4.0 Enterprise · Todos los derechos reservados',
      categories: {
        Todos: 'Todos',
        Hamburguesas: 'Hamburguesas',
        'Alitas & Pollo': 'Alitas & Pollo',
        Bebidas: 'Bebidas',
        Guarniciones: 'Guarniciones',
        Especialidades: 'Especialidades'
      },
      sizes: {
        Regular: 'Regular',
        Grande: 'Grande (+S/ 5)',
        Doble: 'Doble (+S/ 9)'
      }
    },
    en: {
      pageTitle: 'FoodCore Gastronomy · Digital Menu',
      subtitle: 'Official Gastronomy Menu · Real-Time Updates',
      badgeLive: '● Available for Orders',
      heroKicker: 'LIVE CULINARY EXPERIENCE',
      heroTitle: 'Artisan Flame-Grilled Flavors',
      cardAction: 'Specialty',
      summaryLabel: 'Estimated total',
      resetLabel: 'Clear',
      itemSingular: 'item',
      itemPlural: 'items',
      footerText: 'FoodCore ERP v5.4.0 Enterprise · All rights reserved',
      categories: {
        Todos: 'All',
        Hamburguesas: 'Burgers',
        'Alitas & Pollo': 'Wings & Chicken',
        Bebidas: 'Drinks',
        Guarniciones: 'Sides',
        Especialidades: 'Specialties'
      },
      sizes: {
        Regular: 'Regular',
        Grande: 'Large (+S/ 5)',
        Doble: 'Double (+S/ 9)'
      }
    }
  };

  let activeCategory = 'Todos';
  let catalogProducts = [...FALLBACK_CATALOG];
  let previewCount = 0;
  let previewTotal = 0;
  let activeLang = 'es';
  let i18nDict = DEFAULT_I18N.es;

  function translateCategory(cat) {
    const map = (i18nDict && i18nDict.categories) || {};
    return map[cat] || cat;
  }

  function updateSummaryCountText() {
    const countEl = document.getElementById('orderTotalCount');
    if (!countEl) return;
    const unit = previewCount === 1
      ? (i18nDict.itemSingular || 'ítem')
      : (i18nDict.itemPlural || 'ítems');
    countEl.textContent = previewCount + ' ' + unit;
  }

  function applyTranslations() {
    document.documentElement.lang = activeLang;
    if (i18nDict.pageTitle) document.title = i18nDict.pageTitle;
    const setText = (id, val) => {
      const el = document.getElementById(id);
      if (el && val) el.textContent = val;
    };
    setText('headerSubtitle', i18nDict.subtitle);
    setText('badgeLive', i18nDict.badgeLive);
    setText('heroKicker', i18nDict.heroKicker);
    setText('heroTitle', i18nDict.heroTitle);
    setText('lblSummaryCaption', i18nDict.summaryLabel);
    setText('btnResetPreview', i18nDict.resetLabel);
    setText('menuFooter', i18nDict.footerText);
    document.querySelectorAll('.lang-btn').forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.lang === activeLang);
    });
    document.querySelectorAll('.category-tab').forEach((btn) => {
      btn.textContent = translateCategory(btn.dataset.category);
    });
    document.querySelectorAll('.card-cat-badge').forEach((badge) => {
      if (badge.dataset.category) badge.textContent = translateCategory(badge.dataset.category);
    });
    document.querySelectorAll('.btn-pedir').forEach((btn) => {
      btn.textContent = i18nDict.cardAction || 'Especialidad';
    });
    document.querySelectorAll('.size-pill').forEach((pill) => {
      const key = pill.dataset.sizeKey;
      if (key && i18nDict.sizes && i18nDict.sizes[key]) {
        pill.textContent = i18nDict.sizes[key];
      }
    });
    updateSummaryCountText();
  }

  async function setLanguage(lang) {
    const normalized = lang === 'en' ? 'en' : 'es';
    activeLang = normalized;
    try {
      window.localStorage.setItem(LANG_STORAGE_KEY, normalized);
    } catch (_e) {
      // Ignorar si localStorage no esta disponible
    }

    let loaded = null;
    for (const candidate of [normalized + '.json', '/menu-digital/' + normalized + '.json']) {
      try {
        const res = await fetch(candidate, { headers: { Accept: 'application/json' } });
        if (res.ok) {
          loaded = await res.json();
          break;
        }
      } catch (_err) {
        // Fallback al siguiente origen o diccionario embebido
      }
    }
    i18nDict = loaded && loaded.categories ? loaded : DEFAULT_I18N[normalized];
    applyTranslations();
  }

  function animatePrice(element, startValue, endValue, duration = 400) {
    if (!element) return;
    const from = Number.isFinite(Number(startValue)) ? Number(startValue) : 0;
    const to = Number.isFinite(Number(endValue)) ? Number(endValue) : 0;
    const totalDuration = Number(duration) > 0 ? Number(duration) : 400;
    const startTime = performance.now();

    function step(now) {
      const elapsed = now - startTime;
      const progress = Math.min(elapsed / totalDuration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      const current = from + (to - from) * eased;
      element.dataset.value = current.toFixed(2);
      element.textContent = 'S/ ' + current.toFixed(2);
      if (progress < 1) {
        requestAnimationFrame(step);
      } else {
        element.dataset.value = to.toFixed(2);
        element.textContent = 'S/ ' + to.toFixed(2);
      }
    }

    requestAnimationFrame(step);
  }

  function normalizeCategory(cat) {
    return (cat || 'Especialidades').trim();
  }

  function renderCategoryTabs(products) {
    const tabsContainer = document.getElementById('categoryTabs');
    if (!tabsContainer) return;

    const categories = ['Todos'];
    products.forEach((p) => {
      const c = normalizeCategory(p.categoria);
      if (!categories.includes(c)) {
        categories.push(c);
      }
    });

    tabsContainer.innerHTML = '';
    categories.forEach((cat) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'category-tab' + (cat === activeCategory ? ' active' : '');
      btn.dataset.category = cat;
      btn.textContent = translateCategory(cat);
      btn.addEventListener('click', () => switchCategory(cat));
      tabsContainer.appendChild(btn);
    });
  }

  function buildFallbackDishSvg(title, category) {
    const isDrink = (category || '').toLowerCase().includes('bebida');
    const c1 = isDrink ? '%230F172A' : '%231E293B';
    const c2 = isDrink ? '%230EA5E9' : '%23FF5A1F';
    const accent = isDrink ? '%2338BDF8' : '%23FDBA74';
    return "data:image/svg+xml;utf8,"
      + "%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 400 220' preserveAspectRatio='none'%3E"
      + "%3Cdefs%3E%3ClinearGradient id='g' x1='0' y1='0' x2='1' y2='1'%3E"
      + "%3Cstop offset='0%25' stop-color='" + c1 + "'/%3E"
      + "%3Cstop offset='100%25' stop-color='" + c2 + "'/%3E%3C/linearGradient%3E%3C/defs%3E"
      + "%3Crect width='400' height='220' fill='url(%23g)'/%3E"
      + "%3Ccircle cx='320' cy='110' r='64' fill='%23ffffff' fill-opacity='0.10'/%3E"
      + "%3Ccircle cx='200' cy='110' r='44' fill='" + accent + "' fill-opacity='0.20'/%3E"
      + "%3Ccircle cx='200' cy='110' r='28' fill='none' stroke='%23ffffff' stroke-opacity='0.35' stroke-width='2'/%3E"
      + "%3Ccircle cx='200' cy='110' r='10' fill='%23ffffff' fill-opacity='0.45'/%3E%3C/svg%3E";
  }

  function createDishCard(producto, visibleIndex) {
    const card = document.createElement('article');
    const category = normalizeCategory(producto.categoria);
    const basePrice = Number(producto.precio || 0);
    card.className = 'card morph-in';
    card.dataset.category = category;
    card.dataset.id = String(producto.id || visibleIndex);
    card.dataset.basePrice = basePrice.toFixed(2);
    if (visibleIndex < 6) {
      card.style.animationDelay = (visibleIndex * 50) + 'ms';
    }

    const imgWrap = document.createElement('div');
    imgWrap.className = 'card-img-wrap';
    const img = document.createElement('img');
    img.className = 'parallax-img';
    img.alt = producto.nombre || 'Especialidad';
    const fallbackSrc = buildFallbackDishSvg(producto.nombre, category);
    img.src = (producto.imagen_url && producto.imagen_url.trim()) ? producto.imagen_url : fallbackSrc;
    img.addEventListener('error', () => {
      if (img.src !== fallbackSrc) img.src = fallbackSrc;
    });
    imgWrap.appendChild(img);

    const badge = document.createElement('span');
    badge.className = 'card-cat-badge';
    badge.dataset.category = category;
    badge.textContent = translateCategory(category);

    const title = document.createElement('h2');
    title.className = 'card-title';
    title.textContent = producto.nombre || 'Especialidad';

    const desc = document.createElement('p');
    desc.className = 'card-desc';
    desc.textContent = producto.descripcion || 'Plato elaborado con ingredientes frescos.';

    const topWrap = document.createElement('div');
    topWrap.appendChild(imgWrap);
    topWrap.appendChild(badge);
    topWrap.appendChild(title);
    topWrap.appendChild(desc);

    const sizesWrap = document.createElement('div');
    sizesWrap.className = 'size-pills';
    const sizes = [
      { key: 'Regular', delta: 0 },
      { key: 'Grande', delta: 5 },
      { key: 'Doble', delta: 9 }
    ];
    sizes.forEach((sz, idx) => {
      const pill = document.createElement('button');
      pill.type = 'button';
      pill.className = 'size-pill' + (idx === 0 ? ' active' : '');
      pill.dataset.sizeKey = sz.key;
      pill.textContent = (i18nDict.sizes && i18nDict.sizes[sz.key]) || sz.key;
      pill.dataset.price = (basePrice + sz.delta).toFixed(2);
      pill.addEventListener('click', () => {
        sizesWrap.querySelectorAll('.size-pill').forEach((p) => p.classList.remove('active'));
        pill.classList.add('active');
        const priceEl = card.querySelector('.price');
        if (priceEl) {
          const prevVal = Number(priceEl.dataset.value || basePrice);
          const nextVal = Number(pill.dataset.price);
          animatePrice(priceEl, prevVal, nextVal, 400);
        }
      });
      sizesWrap.appendChild(pill);
    });

    const footer = document.createElement('div');
    footer.className = 'card-footer';

    const price = document.createElement('span');
    price.className = 'price';
    price.dataset.value = basePrice.toFixed(2);
    price.textContent = 'S/ ' + basePrice.toFixed(2);

    const cta = document.createElement('button');
    cta.type = 'button';
    cta.className = 'btn-pedir';
    cta.textContent = i18nDict.cardAction || 'Especialidad';
    cta.addEventListener('click', () => {
      const activePill = sizesWrap.querySelector('.size-pill.active');
      const unitPrice = Number(activePill ? activePill.dataset.price : basePrice);
      const prevTotal = previewTotal;
      previewTotal += unitPrice;
      previewCount += 1;
      const totalEl = document.getElementById('orderPreviewTotal');
      if (totalEl) {
        animatePrice(totalEl, prevTotal, previewTotal, 400);
      }
      updateSummaryCountText();
    });

    footer.appendChild(price);
    footer.appendChild(cta);

    card.appendChild(topWrap);
    card.appendChild(sizesWrap);
    card.appendChild(footer);
    return card;
  }

  function renderGrid(categoryFilter) {
    const grid = document.getElementById('menuGrid');
    if (!grid) return;

    const filtered = catalogProducts.filter((p) => {
      if (!categoryFilter || categoryFilter === 'Todos') return true;
      return normalizeCategory(p.categoria) === categoryFilter;
    });

    grid.innerHTML = '';
    filtered.forEach((producto, idx) => {
      const card = createDishCard(producto, idx);
      grid.appendChild(card);
    });
  }

  function switchCategory(newCategory) {
    activeCategory = newCategory;
    document.querySelectorAll('.category-tab').forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.category === newCategory);
    });

    const existingCards = Array.from(document.querySelectorAll('#menuGrid .card'));
    if (existingCards.length === 0) {
      renderGrid(newCategory);
      return;
    }

    existingCards.forEach((card) => {
      card.classList.remove('morph-in');
      card.classList.add('morph-out');
    });

    window.setTimeout(() => {
      renderGrid(newCategory);
    }, 180);
  }

  async function loadCatalog() {
    try {
      const response = await fetch('/api/public/productos/disponibles', {
        headers: { Accept: 'application/json' }
      });
      if (response.ok) {
        const data = await response.json();
        if (Array.isArray(data) && data.length > 0) {
          catalogProducts = data;
        }
      }
    } catch (_err) {
      // Fallback al catalogo embebido si se abre sin backend activo
    }

    renderCategoryTabs(catalogProducts);
    renderGrid(activeCategory);
  }

  function updateParallax() {
    const scrollY = window.scrollY || window.pageYOffset || 0;
    const heroY = (scrollY * 0.5).toFixed(2);
    const cardY = Math.max(-36, Math.min(36, scrollY * 0.5)).toFixed(2);
    document.querySelectorAll('.hero-parallax-layer').forEach((el) => {
      el.style.transform = 'translateY(' + heroY + 'px)';
    });
    document.querySelectorAll('.card-img-wrap .parallax-img').forEach((img) => {
      img.style.transform = 'translateY(' + cardY + 'px)';
    });
  }

  window.addEventListener('scroll', updateParallax, { passive: true });

  window.FoodCoreMenuQr = {
    switchCategory,
    loadCatalog,
    updateParallax,
    animatePrice,
    setLanguage
  };

  document.addEventListener('DOMContentLoaded', async () => {
    let savedLang = 'es';
    try {
      const stored = window.localStorage.getItem(LANG_STORAGE_KEY);
      if (stored === 'en' || stored === 'es') {
        savedLang = stored;
      }
    } catch (_e) {
      // Ignorar si localStorage no esta disponible
    }
    if (savedLang === 'en') {
      activeLang = 'en';
      i18nDict = DEFAULT_I18N.en;
    }

    document.querySelectorAll('.lang-btn').forEach((btn) => {
      btn.addEventListener('click', () => setLanguage(btn.dataset.lang));
    });

    const resetBtn = document.getElementById('btnResetPreview');
    if (resetBtn) {
      resetBtn.addEventListener('click', () => {
        const prev = previewTotal;
        previewTotal = 0;
        previewCount = 0;
        const totalEl = document.getElementById('orderPreviewTotal');
        if (totalEl) animatePrice(totalEl, prev, 0, 400);
        updateSummaryCountText();
      });
    }

    await loadCatalog();
    await setLanguage(savedLang);
    updateParallax();
  });
})();
