package com.foodcore.util;

import com.foodcore.App;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Utilidad centralizada para abrir {@link FileChooser} nativos de guardado/apertura
 * recordando la última carpeta seleccionada mediante {@link Preferences}.
 */
public class ExportFileChooserHelper {

    public static final String PREF_KEY_ULTIMA_CARPETA_EXPORT = "ultima_carpeta_exportacion";
    public static final String PREF_KEY_ULTIMA_CARPETA_IMPORT = "ultima_carpeta_importacion";

    private static final Logger LOGGER = Logger.getLogger(ExportFileChooserHelper.class.getName());
    private static final AtomicReference<Function<FileChooserRequest, Optional<File>>> TEST_SELECTOR_OVERRIDE =
            new AtomicReference<>();
    private static final ExportFileChooserHelper DEFAULT_INSTANCE =
            new ExportFileChooserHelper(Preferences.userNodeForPackage(ExportFileChooserHelper.class));

    private final Preferences preferences;

    /**
     * Formatos de archivo soportados para exportación e importación tabular.
     */
    public enum FormatoExportacion {
        PDF("Documentos PDF (*.pdf)", ".pdf", "*.pdf"),
        EXCEL("Libros de Excel (*.xlsx)", ".xlsx", "*.xlsx"),
        CSV("Archivos CSV (*.csv)", ".csv", "*.csv"),
        DATOS_TABULARES("Archivos Excel y CSV (*.xlsx, *.csv)", ".xlsx", "*.xlsx", "*.csv");

        private final String descripcion;
        private final String extensionPrincipal;
        private final List<String> patrones;

        FormatoExportacion(String descripcion, String extensionPrincipal, String... patrones) {
            this.descripcion = descripcion;
            this.extensionPrincipal = extensionPrincipal;
            this.patrones = List.of(patrones);
        }

        public String getDescripcion() {
            return descripcion;
        }

        public String getExtensionPrincipal() {
            return extensionPrincipal;
        }

        public List<String> getPatrones() {
            return new ArrayList<>(patrones);
        }

        public FileChooser.ExtensionFilter toExtensionFilter() {
            return new FileChooser.ExtensionFilter(descripcion, patrones);
        }
    }

    /**
     * Solicitud inmutable enviada al selector (útil también para pruebas automatizadas).
     */
    public record FileChooserRequest(
            boolean modoGuardar,
            String titulo,
            String nombreSugerido,
            Path directorioInicial,
            FormatoExportacion formatoPrincipal,
            List<FileChooser.ExtensionFilter> filtros) {

        public FileChooserRequest {
            filtros = filtros != null ? List.copyOf(filtros) : List.of();
        }
    }

    private ExportFileChooserHelper(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences no puede ser nulo");
    }

    public static ExportFileChooserHelper conPreferencias(Preferences preferences) {
        return new ExportFileChooserHelper(preferences);
    }

    public static void setTestSelectorOverride(Function<FileChooserRequest, Optional<File>> override) {
        TEST_SELECTOR_OVERRIDE.set(override);
    }

    public static void clearTestSelectorOverride() {
        TEST_SELECTOR_OVERRIDE.set(null);
    }

    public static Optional<File> elegirDestinoExportacion(
            Node ownerNode, String titulo, String nombreSugerido, FormatoExportacion formato) {
        return DEFAULT_INSTANCE.seleccionarArchivoGuardar(
                resolverVentana(ownerNode), titulo, nombreSugerido, formato);
    }

    public static Optional<File> elegirOrigenImportacion(
            Node ownerNode, String titulo) {
        return DEFAULT_INSTANCE.seleccionarArchivoAbrir(
                resolverVentana(ownerNode),
                titulo,
                PREF_KEY_ULTIMA_CARPETA_IMPORT,
                List.of(
                        FormatoExportacion.DATOS_TABULARES,
                        FormatoExportacion.EXCEL,
                        FormatoExportacion.CSV));
    }

    public static Optional<File> elegirPdfApertura(
            Node ownerNode, String titulo) {
        return DEFAULT_INSTANCE.seleccionarArchivoAbrir(
                resolverVentana(ownerNode),
                titulo,
                PREF_KEY_ULTIMA_CARPETA_IMPORT,
                List.of(FormatoExportacion.PDF));
    }

    public static Window resolverVentana(Node ownerNode) {
        if (ownerNode != null) {
            Scene scene = ownerNode.getScene();
            if (scene != null && scene.getWindow() != null) {
                return scene.getWindow();
            }
        }
        return App.getPrimaryStage();
    }

    public Optional<File> seleccionarArchivoGuardar(
            Window owner, String titulo, String nombreSugerido, FormatoExportacion formato) {
        FormatoExportacion formatoEfectivo = formato != null ? formato : FormatoExportacion.EXCEL;
        String nombreBase = asegurarExtension(nombreSugerido, formatoEfectivo.getExtensionPrincipal());
        List<FileChooser.ExtensionFilter> filtros = construirFiltrosExportacion(formatoEfectivo);
        Path dirInicial = obtenerUltimoDirectorio(PREF_KEY_ULTIMA_CARPETA_EXPORT).orElse(null);

        FileChooserRequest req = new FileChooserRequest(
                true, titulo, nombreBase, dirInicial, formatoEfectivo, filtros);

        Optional<File> seleccion = invocarSelector(owner, req);
        if (seleccion.isEmpty()) {
            return Optional.empty();
        }

        File archivo = asegurarExtensionEnArchivo(seleccion.get(), formatoEfectivo);
        guardarDirectorioPadre(PREF_KEY_ULTIMA_CARPETA_EXPORT, archivo);
        return Optional.of(archivo);
    }

    public Optional<File> seleccionarArchivoAbrir(
            Window owner, String titulo, String prefKey, List<FormatoExportacion> formatosPermitidos) {
        List<FileChooser.ExtensionFilter> filtros = new ArrayList<>();
        FormatoExportacion principal = formatosPermitidos != null && !formatosPermitidos.isEmpty()
                ? formatosPermitidos.get(0)
                : FormatoExportacion.DATOS_TABULARES;
        if (formatosPermitidos != null) {
            for (FormatoExportacion f : formatosPermitidos) {
                filtros.add(f.toExtensionFilter());
            }
        } else {
            filtros.add(principal.toExtensionFilter());
        }

        Path dirInicial = obtenerUltimoDirectorio(prefKey).orElse(null);
        FileChooserRequest req = new FileChooserRequest(false, titulo, null, dirInicial, principal, filtros);

        Optional<File> seleccion = invocarSelector(owner, req);
        seleccion.ifPresent(file -> guardarDirectorioPadre(prefKey, file));
        return seleccion;
    }

    public Optional<Path> obtenerUltimoDirectorio(String prefKey) {
        try {
            String raw = preferences.get(prefKey, null);
            if (raw != null && !raw.isBlank()) {
                Path path = Path.of(raw.trim());
                if (Files.isDirectory(path)) {
                    return Optional.of(path);
                }
            }
        } catch (RuntimeException ex) {
            LOGGER.log(Level.FINE, "No se pudo leer la carpeta recordada en Preferences", ex);
        }
        return Optional.empty();
    }

    private Optional<File> invocarSelector(Window owner, FileChooserRequest req) {
        Function<FileChooserRequest, Optional<File>> override = TEST_SELECTOR_OVERRIDE.get();
        if (override != null) {
            Optional<File> res = override.apply(req);
            return res != null ? res : Optional.empty();
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(req.titulo());
        if (req.directorioInicial() != null && Files.isDirectory(req.directorioInicial())) {
            chooser.setInitialDirectory(req.directorioInicial().toFile());
        }
        if (req.nombreSugerido() != null && !req.nombreSugerido().isBlank()) {
            chooser.setInitialFileName(req.nombreSugerido());
        }
        if (!req.filtros().isEmpty()) {
            chooser.getExtensionFilters().addAll(req.filtros());
            chooser.setSelectedExtensionFilter(req.filtros().get(0));
        }

        File elegido = req.modoGuardar()
                ? chooser.showSaveDialog(owner)
                : chooser.showOpenDialog(owner);
        return Optional.ofNullable(elegido);
    }

    private List<FileChooser.ExtensionFilter> construirFiltrosExportacion(FormatoExportacion principal) {
        List<FileChooser.ExtensionFilter> filtros = new ArrayList<>();
        filtros.add(principal.toExtensionFilter());
        for (FormatoExportacion f : List.of(FormatoExportacion.PDF, FormatoExportacion.EXCEL, FormatoExportacion.CSV)) {
            if (f != principal) {
                filtros.add(f.toExtensionFilter());
            }
        }
        return filtros;
    }

    private void guardarDirectorioPadre(String prefKey, File archivo) {
        if (archivo == null) {
            return;
        }
        File parent = archivo.isDirectory() ? archivo : archivo.getParentFile();
        if (parent != null) {
            try {
                preferences.put(prefKey, parent.getAbsolutePath());
                preferences.flush();
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "No se pudo persistir carpeta en Preferences", ex);
            }
        }
    }

    private String asegurarExtension(String nombreSugerido, String extension) {
        String base = (nombreSugerido == null || nombreSugerido.isBlank()) ? "Reporte_FoodCore" : nombreSugerido.trim();
        if (base.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT))) {
            return base;
        }
        return base + extension;
    }

    private File asegurarExtensionEnArchivo(File archivo, FormatoExportacion formato) {
        String nombreLower = archivo.getName().toLowerCase(Locale.ROOT);
        if (nombreLower.endsWith(".pdf") || nombreLower.endsWith(".xlsx") || nombreLower.endsWith(".csv")) {
            return archivo;
        }
        File parent = archivo.getParentFile();
        String nombreConExt = archivo.getName() + formato.getExtensionPrincipal();
        return parent != null ? new File(parent, nombreConExt) : new File(nombreConExt);
    }
}
