package com.foodcore.service;

import com.foodcore.model.DocumentoEmpleado;
import com.foodcore.model.Usuario;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Servicio administrativo para gestión de expedientes laborales y documentos PDF del personal.
 * No realiza cálculos legales (AFP/ONP/Renta 5ta).
 */
public interface ExpedienteEmpleadoService {

    List<Usuario> listarExpedientes(boolean soloActivos);

    Optional<Usuario> buscarExpediente(int idUsuario);

    void guardarDatosPersonales(Usuario usuario);

    void guardarDatosLaborales(Usuario usuario);

    List<DocumentoEmpleado> listarDocumentos(int idUsuario);

    DocumentoEmpleado subirDocumentoPdf(int idUsuario,
                                        String tipoDocumento,
                                        File archivoOrigen,
                                        LocalDate fechaEmision,
                                        LocalDate fechaVencimiento,
                                        String observaciones);

    void eliminarDocumento(long idDocumento);

    List<DocumentoEmpleado> listarDocumentosProximosAVencer(int dias);
}
