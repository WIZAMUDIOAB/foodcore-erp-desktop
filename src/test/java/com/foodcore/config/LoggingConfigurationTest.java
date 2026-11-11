package com.foodcore.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingConfigurationTest {

    @Test
    void logbackConfiguresRollingFileWarnConsoleAndSecurityAuditLog() throws Exception {
        LoggingBootstrap.installBridge();
        assertInstanceOf(LoggerContext.class, LoggerFactory.getILoggerFactory());
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        assertEquals(Level.INFO, root.getLevel());

        Appender<ILoggingEvent> consoleAppender = root.getAppender("CONSOLE");
        assertInstanceOf(ConsoleAppender.class, consoleAppender);
        assertFalse(consoleAppender.getCopyOfAttachedFiltersList().isEmpty());
        ThresholdFilter thresholdFilter = assertInstanceOf(
                ThresholdFilter.class, consoleAppender.getCopyOfAttachedFiltersList().get(0));
        assertTrue(thresholdFilter.isStarted());

        Appender<ILoggingEvent> fileAppender = root.getAppender("FILE");
        RollingFileAppender<?> rollingFile = assertInstanceOf(RollingFileAppender.class, fileAppender);
        TimeBasedRollingPolicy<?> rollingPolicy = assertInstanceOf(
                TimeBasedRollingPolicy.class, rollingFile.getRollingPolicy());
        assertEquals(30, rollingPolicy.getMaxHistory());

        Logger securityLogger = context.getLogger("com.foodcore.security");
        assertEquals(Level.DEBUG, securityLogger.getLevel());
        assertFalse(securityLogger.isAdditive());
        assertNotNull(securityLogger.getAppender("SECURITY_FILE"));

        Logger apiSecurityLogger = context.getLogger("com.foodcore.api.security");
        assertEquals(Level.DEBUG, apiSecurityLogger.getLevel());
        assertFalse(apiSecurityLogger.isAdditive());
        assertNotNull(apiSecurityLogger.getAppender("SECURITY_FILE"));

        String appMarker = "LOGBACK_APP_MARKER_" + UUID.randomUUID();
        String secMarker = "LOGBACK_SEC_MARKER_" + UUID.randomUUID();
        String julSecMarker = "LOGBACK_JUL_SEC_MARKER_" + UUID.randomUUID();

        LoggerFactory.getLogger("com.foodcore.probe").info("Probe app message {}", appMarker);
        LoggerFactory.getLogger("com.foodcore.security.Probe").debug("Probe security message {}", secMarker);
        java.util.logging.Logger.getLogger("com.foodcore.api.security.Probe")
                .fine("Probe JUL security message " + julSecMarker);

        Path appLog = Path.of("logs", "foodcore.log");
        Path secLog = Path.of("logs", "security-audit.log");
        assertTrue(Files.exists(appLog), "Debe crearse logs/foodcore.log");
        assertTrue(Files.exists(secLog), "Debe crearse logs/security-audit.log");

        String appContent = Files.readString(appLog, StandardCharsets.UTF_8);
        String secContent = Files.readString(secLog, StandardCharsets.UTF_8);
        assertTrue(appContent.contains(appMarker));
        assertFalse(appContent.contains(secMarker), "Los logs de seguridad deben ir al archivo separado");
        assertTrue(secContent.contains(secMarker));
        assertTrue(secContent.contains(julSecMarker));
    }
}
