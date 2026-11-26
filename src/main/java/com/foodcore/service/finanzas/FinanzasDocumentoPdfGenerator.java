package com.foodcore.service.finanzas;

import com.foodcore.model.Insumo;
import java.io.File;
import java.util.List;

public interface FinanzasDocumentoPdfGenerator {
    void generarOrdenCompraPdf(int idProveedor, List<Insumo> insumosRequeridos, File archivoDestino) throws Exception;
    void generarEstadoCuentaClientePdf(int idCliente, File archivoDestino) throws Exception;
}
