import http from 'k6/http';
import { check, group, sleep } from 'k6';

/**
 * FoodCore ERP — Suite k6 de Carga y Estrés (Sub-Fase G - Ítems #56 y #57)
 *
 * Escenarios:
 *   1) load_test   : 20 VUs constantes durante 3 minutos (Login + Creación de Pedidos + Consulta KDS)
 *   2) stress_test : Escalado progresivo hasta 100 VUs para identificar el punto de quiebre
 *
 * Uso:
 *   k6 run scripts/perf/load-test.js
 *   k6 run -e BASE_URL=https://127.0.0.1:8443 scripts/perf/load-test.js
 */
const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const USERNAME = __ENV.FOODCORE_USER || 'admin';
const PASSWORD = __ENV.FOODCORE_PASS || 'Admin#2026!';

export const options = {
  insecureSkipTLSVerify: true,
  scenarios: {
    load_20_vus: {
      executor: 'constant-vus',
      vus: 20,
      duration: '3m',
      exec: 'ejecutarFlujoPosYComanda',
      tags: { perfil: 'carga_20vus' },
    },
    stress_100_vus: {
      executor: 'ramping-vus',
      startTime: '3m10s',
      startVUs: 10,
      stages: [
        { duration: '1m', target: 35 },
        { duration: '1m', target: 70 },
        { duration: '1m30s', target: 100 },
        { duration: '45s', target: 0 },
      ],
      exec: 'ejecutarFlujoPosYComanda',
      tags: { perfil: 'estres_100vus' },
    },
  },
  thresholds: {
    'http_req_failed{perfil:carga_20vus}': ['rate<0.01'],
    'http_req_duration{perfil:carga_20vus}': ['p(95)<500'],
    'checks': ['rate>0.95'],
  },
};

function autenticarSiNecesario() {
  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: USERNAME, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'login' } }
  );

  check(loginRes, {
    'login responde 200 o 429 controlado': (r) => r.status === 200 || r.status === 429,
  });

  if (loginRes.status !== 200) {
    return null;
  }
  const body = loginRes.json();
  return body && body.token ? body.token : null;
}

export function ejecutarFlujoPosYComanda() {
  let token = null;

  group('01_login', () => {
    token = autenticarSiNecesario();
  });

  if (!token) {
    sleep(0.5);
    return;
  }

  const authHeaders = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };

  group('02_crear_pedido_pos', () => {
    const mesa = String(((__VU + __ITER) % 12) + 1).padStart(2, '0');
    const uuidLocal = `k6-${__VU}-${__ITER}-${Date.now()}`;
    const payload = JSON.stringify({
      uuid_local: uuidLocal,
      numero_mesa: mesa,
      items: [
        {
          id_producto: 1,
          nombre: 'Hamburguesa Royal Doble',
          cantidad: 1,
          precio_unitario: 24.50,
          notas: 'Prueba k6 Sub-Fase G',
        },
      ],
    });

    const resPedido = http.post(`${BASE_URL}/api/pedidos`, payload, {
      headers: authHeaders,
      tags: { endpoint: 'crear_pedido' },
    });

    check(resPedido, {
      'pedido creado HTTP 200/201': (r) => r.status === 200 || r.status === 201,
    });
  });

  group('03_consulta_kds', () => {
    const resKds = http.get(`${BASE_URL}/api/kds/items`, {
      headers: authHeaders,
      tags: { endpoint: 'kds_items' },
    });

    check(resKds, {
      'KDS responde HTTP 200': (r) => r.status === 200,
    });
  });

  sleep(0.3);
}
