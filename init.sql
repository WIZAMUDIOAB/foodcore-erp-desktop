-- FoodCore ERP — Inicialización Idempotente de Base de Datos (Desarrollo / Staging)
-- Ejecutar como usuario root de MySQL

CREATE DATABASE IF NOT EXISTS foodcore
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- Usuario de migraciones (DDL: CREATE, ALTER, DROP)
CREATE USER IF NOT EXISTS 'foodcore_migrator'@'localhost'
  IDENTIFIED BY 'FoodCoreMigrator123!';
ALTER USER 'foodcore_migrator'@'localhost'
  IDENTIFIED BY 'FoodCoreMigrator123!';
CREATE USER IF NOT EXISTS 'foodcore_migrator'@'127.0.0.1'
  IDENTIFIED BY 'FoodCoreMigrator123!';
ALTER USER 'foodcore_migrator'@'127.0.0.1'
  IDENTIFIED BY 'FoodCoreMigrator123!';
CREATE USER IF NOT EXISTS 'foodcore_migrator'@'%'
  IDENTIFIED BY 'FoodCoreMigrator123!';
ALTER USER 'foodcore_migrator'@'%'
  IDENTIFIED BY 'FoodCoreMigrator123!';

GRANT ALL PRIVILEGES ON foodcore.* TO 'foodcore_migrator'@'localhost' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON foodcore.* TO 'foodcore_migrator'@'127.0.0.1' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON foodcore.* TO 'foodcore_migrator'@'%' WITH GRANT OPTION;

-- Usuario de aplicación (DML: SELECT, INSERT, UPDATE, DELETE)
CREATE USER IF NOT EXISTS 'foodcore_app'@'localhost'
  IDENTIFIED BY 'FoodCoreApp123!';
ALTER USER 'foodcore_app'@'localhost'
  IDENTIFIED BY 'FoodCoreApp123!';
CREATE USER IF NOT EXISTS 'foodcore_app'@'127.0.0.1'
  IDENTIFIED BY 'FoodCoreApp123!';
ALTER USER 'foodcore_app'@'127.0.0.1'
  IDENTIFIED BY 'FoodCoreApp123!';
CREATE USER IF NOT EXISTS 'foodcore_app'@'%'
  IDENTIFIED BY 'FoodCoreApp123!';
ALTER USER 'foodcore_app'@'%'
  IDENTIFIED BY 'FoodCoreApp123!';

GRANT SELECT, INSERT, UPDATE, DELETE ON foodcore.* TO 'foodcore_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON foodcore.* TO 'foodcore_app'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE ON foodcore.* TO 'foodcore_app'@'%';

FLUSH PRIVILEGES;
