@echo off
setlocal
REM Arranque del servidor/escritorio FoodCore ERP leyendo secretos desde variables de entorno.
if "%FOODCORE_KEYSTORE_PASS%"=="" (
    echo [ERROR] Debe definir la variable de entorno FOODCORE_KEYSTORE_PASS ^(minimo 8 caracteres^).
    exit /b 1
)
if "%FOODCORE_JWT_SECRET%"=="" (
    echo [ADVERTENCIA] FOODCORE_JWT_SECRET no definida; configure un secreto >= 32 bytes en produccion.
)
java -jar "%~dp0..\target\foodcore-erp.jar" %*
