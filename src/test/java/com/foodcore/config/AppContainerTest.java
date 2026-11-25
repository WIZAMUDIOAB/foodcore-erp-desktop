package com.foodcore.config;

import com.foodcore.controller.ArqueoCajaController;
import com.foodcore.controller.ClientesController;
import com.foodcore.controller.CobroModalController;
import com.foodcore.controller.CuentasPorCobrarController;
import com.foodcore.controller.CuentasPorPagarController;
import com.foodcore.controller.EstadoResultadosController;
import com.foodcore.controller.HistorialPreciosController;
import com.foodcore.controller.KitchenDisplayController;
import com.foodcore.controller.LoginController;
import com.foodcore.controller.MainDashboardController;
import com.foodcore.controller.MenuController;
import com.foodcore.controller.ProveedoresController;
import com.foodcore.controller.ReportesAvanzadosController;
import com.foodcore.controller.SalonMesasController;
import com.foodcore.controller.SplashController;
import com.foodcore.controller.VentaController;
import com.foodcore.service.AuditoriaConsultaService;
import com.foodcore.service.AusenciasVacacionesService;
import com.foodcore.service.AuthService;
import com.foodcore.service.CajaService;
import com.foodcore.service.ComboService;
import com.foodcore.service.ExpedienteEmpleadoService;
import com.foodcore.service.FidelizacionService;
import com.foodcore.service.FinanzasService;
import com.foodcore.service.PedidoService;
import com.foodcore.service.PersonalizacionProductoService;
import com.foodcore.service.PlanillaService;
import com.foodcore.service.VentaService;
import com.foodcore.tx.TransactionManager;
import javafx.fxml.FXMLLoader;
import javafx.util.Callback;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppContainerTest {

    @Test
    void resuelveServiciosCoreSinDependenciaDeBaseDeDatosActiva() {
        AppContainer container = new AppContainer();
        assertNotNull(AppContainer.getDefault());

        assertNotNull(container.resolve(TransactionManager.class));
        assertNotNull(container.transactionManager());
        assertNotNull(container.authService());
        assertNotNull(container.ventaService());
        assertNotNull(container.cajaService());
        assertNotNull(container.pedidoService());
        assertNotNull(container.finanzasService());
        assertNotNull(container.fidelizacionService());
        assertNotNull(container.auditoriaConsultaService());
        assertNotNull(container.personalizacionProductoService());
        assertNotNull(container.comboService());
        assertNotNull(container.planillaService());
        assertNotNull(container.expedienteEmpleadoService());
        assertNotNull(container.ausenciasVacacionesService());
        assertNotNull(container.resolve(AuthService.class));
        assertNotNull(container.resolve(VentaService.class));
        assertNotNull(container.resolve(CajaService.class));
        assertNotNull(container.resolve(PedidoService.class));
        assertNotNull(container.resolve(FinanzasService.class));
        assertNotNull(container.resolve(FidelizacionService.class));
        assertNotNull(container.resolve(AuditoriaConsultaService.class));
        assertNotNull(container.resolve(PersonalizacionProductoService.class));
        assertNotNull(container.resolve(ComboService.class));
        assertNotNull(container.resolve(PlanillaService.class));
        assertNotNull(container.resolve(ExpedienteEmpleadoService.class));
        assertNotNull(container.resolve(AusenciasVacacionesService.class));
    }

    @Test
    void controllerFactoryInstanciaControladoresClaveSinErrores() {
        AppContainer container = new AppContainer();
        Callback<Class<?>, Object> factory = container.controllerFactory();
        assertNotNull(factory);

        List<Class<?>> controllers = List.of(
                LoginController.class,
                MainDashboardController.class,
                VentaController.class,
                CobroModalController.class,
                ArqueoCajaController.class,
                ClientesController.class,
                ProveedoresController.class,
                CuentasPorCobrarController.class,
                CuentasPorPagarController.class,
                HistorialPreciosController.class,
                EstadoResultadosController.class,
                MenuController.class,
                KitchenDisplayController.class,
                SalonMesasController.class,
                ReportesAvanzadosController.class,
                SplashController.class
        );
        for (Class<?> controllerType : controllers) {
            Object instance = factory.call(controllerType);
            assertNotNull(instance, "Debe instanciar " + controllerType.getSimpleName());
            assertInstanceOf(controllerType, instance);
        }

        FXMLLoader loader = container.createLoader(getClass().getResource("/com/foodcore/view/LoginView.fxml"));
        assertNotNull(loader.getControllerFactory());
    }

    @Test
    void withOverridesInyectaDependenciasPersonalizadasEnControladores() {
        AtomicBoolean loginLlamado = new AtomicBoolean(false);
        AuthService fakeAuth = (AuthService) Proxy.newProxyInstance(
                AuthService.class.getClassLoader(),
                new Class<?>[] {AuthService.class},
                (proxy, method, args) -> {
                    if ("login".equals(method.getName())) {
                        loginLlamado.set(true);
                        return null;
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(method.getName())) {
                        return "FakeAuthService";
                    }
                    return method.getReturnType() == boolean.class ? false : null;
                });
        AppContainer custom = AppContainer.withOverrides(Map.of(AuthService.class, fakeAuth));
        assertSame(fakeAuth, custom.authService());
        Object ctrl = custom.controllerFactory().call(LoginController.class);
        assertInstanceOf(LoginController.class, ctrl);
        try {
            custom.authService().login("u", "p");
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
        assertTrue(loginLlamado.get());
        assertEquals(fakeAuth, custom.authService());
    }
}
