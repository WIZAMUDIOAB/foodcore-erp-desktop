package com.foodcore;

/**
 * Lanzador independiente para el JAR ejecutable de FoodCore ERP.
 * Evita la restricción de módulo de JavaFX al no extender directamente de Application.
 */
public class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}
