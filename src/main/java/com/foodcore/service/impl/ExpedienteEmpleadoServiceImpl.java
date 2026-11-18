package com.foodcore.service.impl;

import com.foodcore.dao.DocumentoEmpleadoDAO;
import com.foodcore.dao.ExpedienteUsuarioDAO;
import com.foodcore.dao.impl.DocumentoEmpleadoDAOImpl;
import com.foodcore.dao.impl.ExpedienteUsuarioDAOImpl;
import com.foodcore.model.DocumentoEmpleado;
import com.foodcore.model.Usuario;
import com.foodcore.service.ExpedienteEmpleadoService;
import com.foodcore.util.ConexionBD;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementación de {@link ExpedienteEmpleadoService}.
 */
public class ExpedienteEmpleadoServiceImpl implements ExpedienteEmpleadoService {

    private static final long MAX_PDF_BYTES = 15L * 1024L * 1024L;
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final ExpedienteUsuarioDAO usuarioDao;
    private final DocumentoEmpleadoDAO documentoDao;
    private final Path directorioBaseUploads;

    public ExpedienteEmpleadoServiceImpl() {
        this(new ExpedienteUsuarioDAOImpl(),
                new DocumentoEmpleadoDAOImpl(),
                Paths.get("uploads", "expedientes"));
    }

    public ExpedienteEmpleadoServiceImpl(ExpedienteUsuarioDAO usuarioDao,
                                         DocumentoEmpleadoDAO documentoDao,
                                         Path directorioBaseUploads) {
        this.usuarioDao = Objects.requireNonNull(usuarioDao, "usuarioDao requerido");
        this.documentoDao = Objects.requireNonNull(documentoDao, "documentoDao requerido");
        this.directorioBaseUploads = Objects.requireNonNull(directorioBaseUploads, "directorioBaseUploads requerido");
    }

    @Override
    public List<Usuario> listarExpedientes(boolean soloActivos) {
        try (Connection conn = ConexionBD.getConnection()) {
            return usuarioDao.listarTodos(conn, soloActivos);
        } catch (SQLException e) {
            throw new IllegalStateException("Error al listar expedientes de personal: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Usuario> buscarExpediente(int idUsuario) {
        if (idUsuario <= 0) {
            return Optional.empty();
        }
        try (Connection conn = ConexionBD.getConnection()) {
            return usuarioDao.buscarPorId(conn, idUsuario);
        } catch (SQLException e) {
            throw new IllegalStateException("Error al consultar expediente #" + idUsuario + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void guardarDatosPersonales(Usuario usuario) {
        validarUsuarioBase(usuario);
        String tipoDoc = normalizarRequerido(usuario.getTipoDocumento(), "El tipo de documento es obligatorio.");
        String dni = normalizarRequerido(usuario.getDni(), "El número de documento (DNI/CE) es obligatorio.");
        if (dni.length() < 6 || dni.length() > 20) {
            throw new IllegalArgumentException("El número de documento debe tener entre 6 y 20 caracteres.");
        }
        if (usuario.getFechaNacimiento() != null && usuario.getFechaNacimiento().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de nacimiento no puede ser futura.");
        }
        usuario.setTipoDocumento(tipoDoc.toUpperCase(Locale.ROOT));
        usuario.setDni(dni);
        usuario.setDireccion(limpiarOpcional(usuario.getDireccion()));
        usuario.setTelefono(limpiarOpcional(usuario.getTelefono()));
        usuario.setTelefonoEmergencia(limpiarOpcional(usuario.getTelefonoEmergencia()));
        usuario.setContactoEmergencia(limpiarOpcional(usuario.getContactoEmergencia()));

        try (Connection conn = ConexionBD.getConnection()) {
            boolean actualizado = usuarioDao.actualizarDatosPersonales(conn, usuario);
            if (!actualizado) {
                throw new IllegalArgumentException("No se encontró el empleado #" + usuario.getIdUsuario());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error al guardar datos personales: " + e.getMessage(), e);
        }
    }

    @Override
    public void guardarDatosLaborales(Usuario usuario) {
        validarUsuarioBase(usuario);
        if (usuario.getSueldoBase() < 0) {
            throw new IllegalArgumentException("El sueldo base no puede ser negativo.");
        }
        String tipoContrato = normalizarRequerido(usuario.getTipoContrato(), "El tipo de contrato es obligatorio.");
        if (usuario.getFechaIngreso() != null
                && usuario.getFechaVencimientoContrato() != null
                && usuario.getFechaVencimientoContrato().isBefore(usuario.getFechaIngreso())) {
            throw new IllegalArgumentException(
                    "La fecha de vencimiento de contrato no puede ser anterior a la fecha de ingreso.");
        }
        usuario.setTipoContrato(tipoContrato.toUpperCase(Locale.ROOT));
        usuario.setCargo(limpiarOpcional(usuario.getCargo()));
        usuario.setHorarioLaboral(limpiarOpcional(usuario.getHorarioLaboral()));

        try (Connection conn = ConexionBD.getConnection()) {
            boolean actualizado = usuarioDao.actualizarDatosLaborales(conn, usuario);
            if (!actualizado) {
                throw new IllegalArgumentException("No se encontró el empleado #" + usuario.getIdUsuario());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error al guardar datos laborales: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DocumentoEmpleado> listarDocumentos(int idUsuario) {
        if (idUsuario <= 0) {
            return List.of();
        }
        try (Connection conn = ConexionBD.getConnection()) {
            return documentoDao.listarPorUsuario(conn, idUsuario);
        } catch (SQLException e) {
            throw new IllegalStateException("Error al listar documentos del empleado: " + e.getMessage(), e);
        }
    }

    @Override
    public DocumentoEmpleado subirDocumentoPdf(int idUsuario,
                                               String tipoDocumento,
                                               File archivoOrigen,
                                               LocalDate fechaEmision,
                                               LocalDate fechaVencimiento,
                                               String observaciones) {
        if (idUsuario <= 0) {
            throw new IllegalArgumentException("Seleccione un empleado válido.");
        }
        String tipoNormalizado = normalizarRequerido(tipoDocumento, "El tipo de documento es obligatorio.");
        validarArchivoPdf(archivoOrigen);
        if (fechaEmision != null && fechaVencimiento != null && fechaVencimiento.isBefore(fechaEmision)) {
            throw new IllegalArgumentException("La fecha de vencimiento no puede ser anterior a la fecha de emisión.");
        }

        String nombreOriginal = archivoOrigen.getName();
        String nombreSeguro = sanitizarNombreArchivo(nombreOriginal);
        String nombreDestino = LocalDateTime.now().format(FILE_TS) + "_" + nombreSeguro;
        Path carpetaEmpleado = directorioBaseUploads.toAbsolutePath().normalize()
                .resolve(String.valueOf(idUsuario));
        Path destino = carpetaEmpleado.resolve(nombreDestino).normalize();
        if (!destino.startsWith(carpetaEmpleado)) {
            throw new IllegalArgumentException("Nombre de archivo inválido.");
        }

        try {
            Files.createDirectories(carpetaEmpleado);
            Files.copy(archivoOrigen.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo copiar el archivo PDF al repositorio local.", e);
        }

        DocumentoEmpleado doc = new DocumentoEmpleado();
        doc.setIdUsuario(idUsuario);
        doc.setTipoDocumento(tipoNormalizado.toUpperCase(Locale.ROOT));
        doc.setNombreArchivo(nombreOriginal);
        doc.setRutaArchivo(destino.toString());
        doc.setFechaEmision(fechaEmision);
        doc.setFechaVencimiento(fechaVencimiento);
        doc.setObservaciones(limpiarOpcional(observaciones));

        try (Connection conn = ConexionBD.getConnection()) {
            documentoDao.insertar(conn, doc);
            return documentoDao.buscarPorId(conn, doc.getIdDocumento()).orElse(doc);
        } catch (SQLException e) {
            eliminarArchivoSilencioso(destino);
            throw new IllegalStateException("Error al registrar el documento PDF en base de datos: " + e.getMessage(), e);
        }
    }

    @Override
    public void eliminarDocumento(long idDocumento) {
        if (idDocumento <= 0) {
            throw new IllegalArgumentException("Identificador de documento inválido.");
        }
        try (Connection conn = ConexionBD.getConnection()) {
            Optional<DocumentoEmpleado> existente = documentoDao.buscarPorId(conn, idDocumento);
            if (existente.isEmpty()) {
                throw new IllegalArgumentException("No existe el documento #" + idDocumento);
            }
            boolean eliminado = documentoDao.eliminar(conn, idDocumento);
            if (eliminado && existente.get().getRutaArchivo() != null) {
                Path baseAbs = directorioBaseUploads.toAbsolutePath().normalize();
                Path rutaDoc = Paths.get(existente.get().getRutaArchivo()).toAbsolutePath().normalize();
                if (rutaDoc.startsWith(baseAbs)) {
                    eliminarArchivoSilencioso(rutaDoc);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error al eliminar el documento: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DocumentoEmpleado> listarDocumentosProximosAVencer(int dias) {
        try (Connection conn = ConexionBD.getConnection()) {
            return documentoDao.listarProximosAVencer(conn, dias);
        } catch (SQLException e) {
            throw new IllegalStateException("Error al consultar documentos próximos a vencer: " + e.getMessage(), e);
        }
    }

    private void validarUsuarioBase(Usuario usuario) {
        if (usuario == null || usuario.getIdUsuario() <= 0) {
            throw new IllegalArgumentException("Seleccione un empleado válido.");
        }
    }

    private void validarArchivoPdf(File archivo) {
        if (archivo == null || !archivo.exists() || !archivo.isFile()) {
            throw new IllegalArgumentException("Debe seleccionar un archivo PDF existente.");
        }
        if (!archivo.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("Solo se permiten archivos en formato PDF (.pdf).");
        }
        long length = archivo.length();
        if (length <= 0 || length > MAX_PDF_BYTES) {
            throw new IllegalArgumentException("El archivo PDF debe pesar entre 1 byte y 15 MB.");
        }
        byte[] header = new byte[5];
        try (InputStream in = Files.newInputStream(archivo.toPath())) {
            int read = in.readNBytes(header, 0, 5);
            String sig = new String(header, 0, read, StandardCharsets.US_ASCII);
            if (!"%PDF-".equals(sig)) {
                throw new IllegalArgumentException("El archivo seleccionado no tiene cabecera PDF válida (%PDF-).");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo PDF seleccionado.", e);
        }
    }

    private String sanitizarNombreArchivo(String nombre) {
        Path p = Paths.get(nombre).getFileName();
        String base = (p != null) ? p.toString() : nombre;
        String limpio = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (limpio.isBlank()) {
            return "documento.pdf";
        }
        return limpio;
    }

    private String normalizarRequerido(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor.trim();
    }

    private String limpiarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private void eliminarArchivoSilencioso(Path ruta) {
        try {
            Files.deleteIfExists(ruta);
        } catch (IOException ignored) {
            // Limpieza best-effort en disco
        }
    }
}
