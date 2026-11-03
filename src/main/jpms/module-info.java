module com.foodcore {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.desktop;
    requires java.logging;
    requires com.zaxxer.hikari;
    requires com.google.gson;
    requires org.flywaydb.core;

    opens com.foodcore to javafx.fxml;
    opens com.foodcore.controller to javafx.fxml;
    exports com.foodcore;
    exports com.foodcore.controller;
    exports com.foodcore.model;
    exports com.foodcore.service;
    exports com.foodcore.service.impl;
    exports com.foodcore.dao;
    exports com.foodcore.dao.impl;
    exports com.foodcore.security;
}
