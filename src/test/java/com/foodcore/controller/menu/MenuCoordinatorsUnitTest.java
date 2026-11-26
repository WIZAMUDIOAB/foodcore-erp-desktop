package com.foodcore.controller.menu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MenuCoordinatorsUnitTest {
    @TempDir
    Path tempDir;

    @Test
    void extensionesSeguras() {
        LocalMenuImagenStorage storage = new LocalMenuImagenStorage();
        assertEquals(".png", storage.resolverExtensionSegura("x.png"));
        assertEquals(".jpg", storage.resolverExtensionSegura("x.jpg"));
        assertEquals(".jpeg", storage.resolverExtensionSegura("x.jpeg"));
        assertEquals(".webp", storage.resolverExtensionSegura("x.webp"));
        assertEquals(".png", storage.resolverExtensionSegura("X.PNG"));
        assertNull(storage.resolverExtensionSegura(null));
        assertNull(storage.resolverExtensionSegura("x.gif"));
    }

    @Test
    void copiaYResuelveImagenDentroDeBase() throws Exception {
        LocalMenuImagenStorage storage = new LocalMenuImagenStorage();
        Path origen = tempDir.resolve("origen.png");
        Files.writeString(origen, "img");
        String url = storage.copiarImagen(tempDir.resolve("base"), origen.toFile());
        Path base = tempDir.resolve("base");
        assertTrue(Files.isRegularFile(base.resolve(url.substring(url.lastIndexOf('/') + 1))));
        assertNotNull(storage.resolverImagenSegura(base, url));
        assertNull(storage.resolverImagenSegura(base, "../origen.png"));
        assertNull(storage.resolverImagenSegura(base, tempDir.resolve("origen.png").toString()));
        assertThrows(java.io.IOException.class, () -> storage.copiarImagen(base, new File("invalida.gif")));
    }

    @Test
    void validacionesYFormateoDeMenu() {
        DefaultMenuCrudPresenter presenter = new DefaultMenuCrudPresenter(null, null);
        assertFalse(presenter.validarFormularioPlatillo("", null, null, "", " ", "1").valido());
        assertFalse(presenter.validarFormularioPlatillo("Nueva", null, null, "", "Plato", "no").valido());
        assertFalse(presenter.validarFormularioPlatillo("Nueva", null, null, "", "Plato", null).valido());
        MenuCrudPresenter.ValidacionPlatillo valida = presenter.validarFormularioPlatillo(
                "Nueva", null, null, "", "Plato", "12.5");
        assertTrue(valida.valido());
        assertEquals(new BigDecimal("12.50"), valida.precio());
        assertEquals("Estándar", presenter.formatearTextoOpciones(0, 0));
        assertNotNull(presenter.filtrarProductosSimples(null));
    }

    @Test
    void personalizacionYComboValidanEntradas() {
        DefaultMenuPersonalizacionCoordinator personalizacion = new DefaultMenuPersonalizacionCoordinator(null);
        assertFalse(personalizacion.validarNuevoModificador(" ", "1").valido());
        assertFalse(personalizacion.validarNuevoModificador("Extra", "-1").valido());
        assertEquals(new BigDecimal("0.00"), personalizacion.validarNuevoModificador("Extra", "").precio());
        DefaultMenuComboCoordinator combos = new DefaultMenuComboCoordinator(null);
        assertFalse(combos.agregarComponenteACombo(new java.util.ArrayList<>(), null, "1").ok());
        assertFalse(combos.validarFormularioCombo("", "", "1", java.util.List.of()).valido());
        assertEquals("0 productos (0 uds.)", combos.formatearResumenCombo(null));
    }
}
