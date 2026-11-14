package com.foodcore.model;

/**
 * Configuración de enrutamiento de impresión térmica por categoría de producto hacia una impresora Windows.
 */
public class ConfiguracionImpresora {

    private int idConfiguracion;
    private int idCategoriaProducto;
    private String nombreCategoria;
    private String nombreImpresoraWindows;
    private boolean activa = true;

    public ConfiguracionImpresora() {
    }

    public ConfiguracionImpresora(int idConfiguracion,
                                  int idCategoriaProducto,
                                  String nombreCategoria,
                                  String nombreImpresoraWindows,
                                  boolean activa) {
        this.idConfiguracion = idConfiguracion;
        this.idCategoriaProducto = idCategoriaProducto;
        this.nombreCategoria = nombreCategoria;
        this.nombreImpresoraWindows = nombreImpresoraWindows;
        this.activa = activa;
    }

    public int getIdConfiguracion() {
        return idConfiguracion;
    }

    public void setIdConfiguracion(int idConfiguracion) {
        this.idConfiguracion = idConfiguracion;
    }

    public int getIdCategoriaProducto() {
        return idCategoriaProducto;
    }

    public void setIdCategoriaProducto(int idCategoriaProducto) {
        this.idCategoriaProducto = idCategoriaProducto;
    }

    public String getNombreCategoria() {
        return nombreCategoria;
    }

    public void setNombreCategoria(String nombreCategoria) {
        this.nombreCategoria = nombreCategoria;
    }

    public String getNombreImpresoraWindows() {
        return nombreImpresoraWindows;
    }

    public void setNombreImpresoraWindows(String nombreImpresoraWindows) {
        this.nombreImpresoraWindows = nombreImpresoraWindows;
    }

    public boolean isActiva() {
        return activa;
    }

    public void setActiva(boolean activa) {
        this.activa = activa;
    }
}
