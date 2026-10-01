@echo off
echo =======================================================
echo   Dang khoi dong MySQL va phpMyAdmin bang Docker...
echo =======================================================
docker compose up -d
echo.
echo Database containers da khoi dong thanh cong!
echo - MySQL Database: holavietnamese (Port 3306)
echo - Username: root ^| Password: root
echo - Giao dien Web phpMyAdmin: http://localhost:8081
echo =======================================================
pause
