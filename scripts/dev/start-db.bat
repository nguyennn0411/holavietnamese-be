@echo off
pushd "%~dp0..\.."
echo =======================================================
echo   Dang khoi dong MySQL bang Docker...
echo =======================================================
docker compose --env-file .env -f deployment\compose.yml up -d
echo.
echo Database containers da khoi dong thanh cong!
echo - MySQL Database: hola_vietnamese (Port 3307)
echo - Username: root ^| Password: root
echo - Giao dien Web phpMyAdmin: http://localhost:8081
echo =======================================================
popd
pause
