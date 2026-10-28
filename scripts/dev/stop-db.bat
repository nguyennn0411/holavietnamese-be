@echo off
pushd "%~dp0..\.."
echo =======================================================
echo   Dang tat cac container database Docker...
echo =======================================================
docker compose --env-file .env.mysql -f deployment\compose.yml down
echo.
echo Database containers da duoc tat an toan (du lieu van duoc luu).
echo =======================================================
popd
pause
