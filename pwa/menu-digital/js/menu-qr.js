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

  let activeCategory = 'Todos';
  let catalogProducts = [...FALLBACK_CATALOG];

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
      btn.textContent = cat;
      btn.addEventListener('click', () => switchCategory(cat));
      tabsContainer.appendChild(btn);
    });
  }

  function createDishCard(producto, visibleIndex) {
    const card = document.createElement('article');
    const category = normalizeCategory(producto.categoria);
    const basePrice = Number(producto.precio || 0);
    card.className = 'card morph-in';
    card.dataset.category = category;
    card.dataset.id = String(producto.id || visibleIndex);
    card.dataset.basePrice = basePrice.toFixed(2);

    const badge = document.createElement('span');
    badge.className = 'card-cat-badge';
    badge.textContent = category;

    const title = document.createElement('h2');
    title.className = 'card-title';
    title.textContent = producto.nombre || 'Especialidad';

    const desc = document.createElement('p');
    desc.className = 'card-desc';
    desc.textContent = producto.descripcion || 'Plato elaborado con ingredientes frescos.';

    const topWrap = document.createElement('div');
    topWrap.appendChild(badge);
    topWrap.appendChild(title);
    topWrap.appendChild(desc);

    const sizesWrap = document.createElement('div');
    sizesWrap.className = 'size-pills';
    const sizes = [
      { label: 'Regular', delta: 0 },
      { label: 'Grande (+S/ 5)', delta: 5 },
      { label: 'Doble (+S/ 9)', delta: 9 }
    ];
    sizes.forEach((sz, idx) => {
      const pill = document.createElement('button');
      pill.type = 'button';
      pill.className = 'size-pill' + (idx === 0 ? ' active' : '');
      pill.textContent = sz.label;
      pill.dataset.price = (basePrice + sz.delta).toFixed(2);
      pill.addEventListener('click', () => {
        sizesWrap.querySelectorAll('.size-pill').forEach((p) => p.classList.remove('active'));
        pill.classList.add('active');
        const priceEl = card.querySelector('.price');
        if (priceEl) {
          priceEl.textContent = 'S/ ' + Number(pill.dataset.price).toFixed(2);
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
    cta.textContent = 'Especialidad';

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

  window.FoodCoreMenuQr = {
    switchCategory,
    loadCatalog
  };

  document.addEventListener('DOMContentLoaded', loadCatalog);
})();
