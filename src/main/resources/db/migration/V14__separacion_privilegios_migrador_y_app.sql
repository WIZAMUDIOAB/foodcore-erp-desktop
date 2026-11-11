-- ==============================================================================
-- V14: Separación de privilegios entre migrador DDL (foodcore_migrator)
--      y usuario runtime de aplicación DML-only (foodcore_app).
--
-- Modelo DBA recomendado para aprovisionamiento inicial en producción/staging
-- (ejecutar una única vez como cuenta DBA, nunca embeber contraseñas en VCS):
--   CREATE USER IF NOT EXISTS 'foodcore_migrator'@'127.0.0.1'
--     IDENTIFIED BY '<STRONG_MIGRATOR_PASSWORD>' REQUIRE SSL;
--   GRANT CREATE, ALTER, DROP, INDEX, REFERENCES, SELECT, INSERT, UPDATE, DELETE
--     ON foodcore.* TO 'foodcore_migrator'@'127.0.0.1';
--
--   CREATE USER IF NOT EXISTS 'foodcore_app'@'127.0.0.1'
--     IDENTIFIED BY '<STRONG_APP_PASSWORD>' REQUIRE SSL;
--   GRANT SELECT, INSERT, UPDATE, DELETE
--     ON foodcore.* TO 'foodcore_app'@'127.0.0.1';
--   FLUSH PRIVILEGES;
-- ==============================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS sp_aplicar_privilegios_dml_app_v14 $$

CREATE PROCEDURE sp_aplicar_privilegios_dml_app_v14()
BEGIN
    DECLARE schema_actual VARCHAR(64);
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;

    SET schema_actual = REPLACE(DATABASE(), '`', '``');

    IF EXISTS (SELECT 1 FROM mysql.user WHERE User = 'foodcore_app' AND Host = '127.0.0.1') THEN
        SET @sql_grant_127 = CONCAT(
            'GRANT SELECT, INSERT, UPDATE, DELETE ON `', schema_actual, '`.* TO ''foodcore_app''@''127.0.0.1'''
        );
        PREPARE stmt1 FROM @sql_grant_127;
        EXECUTE stmt1;
        DEALLOCATE PREPARE stmt1;
    END IF;

    IF EXISTS (SELECT 1 FROM mysql.user WHERE User = 'foodcore_app' AND Host = 'localhost') THEN
        SET @sql_grant_local = CONCAT(
            'GRANT SELECT, INSERT, UPDATE, DELETE ON `', schema_actual, '`.* TO ''foodcore_app''@''localhost'''
        );
        PREPARE stmt2 FROM @sql_grant_local;
        EXECUTE stmt2;
        DEALLOCATE PREPARE stmt2;
    END IF;
END $$

CALL sp_aplicar_privilegios_dml_app_v14() $$

DROP PROCEDURE IF EXISTS sp_aplicar_privilegios_dml_app_v14 $$

DELIMITER ;
