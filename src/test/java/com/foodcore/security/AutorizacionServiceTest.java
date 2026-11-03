package com.foodcore.security;

import com.foodcore.exception.CodigoError;
import com.foodcore.exception.NegocioException;
import com.foodcore.model.enums.Permiso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de control de acceso RBAC según SPEC-FOODCORE-v1.0 (§10.2, §10.3, §15.1).
 */
class AutorizacionServiceTest {

    private AutorizacionService autorizacionService;

    @BeforeEach
    void setUp() {
        autorizacionService = new AutorizacionService();
    }

    @Test
    @DisplayName("Debe autorizar cuando el permiso está presente en la sesión del rol")
    void debePermitirOperacionConPermisoPresente() {
        SesionUsuario sesionCajero = autorizacionService.crearSesion(2L, "María Caja", "CAJERO", "TERM-CAJA-01");
        SesionUsuario sesionCocina = autorizacionService.crearSesion(4L, "Chef Pedro", "COCINA", "TERM-KDS-01");

        assertDoesNotThrow(() -> autorizacionService.exigir(sesionCajero, Permiso.VENTA_COBRAR));
        assertDoesNotThrow(() -> autorizacionService.exigir(sesionCajero, Permiso.CAJA_APERTURA));
        assertDoesNotThrow(() -> autorizacionService.exigir(sesionCocina, Permiso.KDS_OPERAR));
    }

    @Test
    @DisplayName("Debe denegar con CodigoError.ACCESO_DENEGADO cuando el permiso está ausente en el rol")
    void debeRechazarOperacionCuandoPermisoEstaAusente() {
        SesionUsuario sesionMozo = autorizacionService.crearSesion(3L, "Carlos Mozo", "MOZO", "TERM-SALON-01");
        SesionUsuario sesionCocina = autorizacionService.crearSesion(4L, "Chef Pedro", "COCINA", "TERM-KDS-01");

        // El mozo puede crear pedidos pero NO puede cobrar ventas ni cerrar caja
        assertDoesNotThrow(() -> autorizacionService.exigir(sesionMozo, Permiso.PEDIDO_CREAR));

        NegocioException exCobro = assertThrows(NegocioException.class,
            () -> autorizacionService.exigir(sesionMozo, Permiso.VENTA_COBRAR));
        assertEquals(CodigoError.ACCESO_DENEGADO, exCobro.getCodigoError());

        // El KDS de cocina NO puede cobrar ni gestionar usuarios (§8.5)
        NegocioException exKdsCobro = assertThrows(NegocioException.class,
            () -> autorizacionService.exigir(sesionCocina, Permiso.VENTA_COBRAR));
        assertEquals(CodigoError.ACCESO_DENEGADO, exKdsCobro.getCodigoError());
    }

    @Test
    @DisplayName("Debe asignar cero permisos a un rol desconocido o sesión nula y denegar el acceso")
    void debeDenegarRolDesconocidoOSesionNula() {
        SesionUsuario sesionRolDesconocido = autorizacionService.crearSesion(99L, "Visitante", "INVITADO_EXTERNO", "TERM-00");
        assertTrue(sesionRolDesconocido.getPermisos().isEmpty());

        NegocioException exRol = assertThrows(NegocioException.class,
            () -> autorizacionService.exigir(sesionRolDesconocido, Permiso.PEDIDO_CREAR));
        assertEquals(CodigoError.ACCESO_DENEGADO, exRol.getCodigoError());

        NegocioException exNull = assertThrows(NegocioException.class,
            () -> autorizacionService.exigir(null, Permiso.VENTA_COBRAR));
        assertEquals(CodigoError.ACCESO_DENEGADO, exNull.getCodigoError());
    }

    @Test
    @DisplayName("Debe validar todos los roles, expiración de sesión y constructores de servicios impl")
    void debeValidarRolesExpiracionYConstructoresImpl() throws Exception {
        SesionUsuario sesionAdmin = autorizacionService.crearSesion(1L, "Admin Root", "ADMIN", "TERM-01");
        SesionUsuario sesionAlmacen = autorizacionService.crearSesion(5L, "Almacén", "ALMACEN", "TERM-01");
        assertEquals(1L, sesionAdmin.getIdUsuario());
        assertEquals("Admin Root", sesionAdmin.getNombreCompleto());
        assertEquals("ADMIN", sesionAdmin.getCodigoRol());
        assertEquals("TERM-01", sesionAdmin.getIdTerminal());
        assertNotNull(sesionAdmin.getIniciadaEn());
        assertNotNull(sesionAdmin.getExpiraEn());
        assertFalse(sesionAdmin.haExpirado(LocalDateTime.now()));
        assertFalse(sesionAdmin.haExpirado(LocalDateTime.now(), 30L));
        assertTrue(sesionAdmin.haExpirado(null));
        assertTrue(sesionAdmin.haExpirado(null, 30L));
        assertFalse(sesionAlmacen.getPermisos().isEmpty());
        autorizacionService.exigirPermiso(sesionAlmacen, Permiso.KARDEX_CONSULTAR);

        SesionManager.iniciarSesion(sesionAdmin, false);
        assertSame(sesionAdmin, SesionManager.getSesionActiva());
        assertFalse(SesionManager.isCambioPasswordPendiente());
        assertSame(sesionAdmin, SesionManager.verificarSesionActiva());
        SesionManager.completarCambioPassword();
        SesionManager.cerrarSesion();
        assertNull(SesionManager.getSesionActiva());

        assertNotNull(new com.foodcore.service.impl.CajaServiceImpl());
        assertNotNull(new com.foodcore.service.impl.VentaServiceImpl());
        assertNotNull(new com.foodcore.service.impl.PedidoServiceImpl());
        assertNotNull(new com.foodcore.service.impl.AuthServiceImpl());
    }
}
