@echo off
REM Load environment variables from .env file and run the application

if exist .env (
    echo Loading environment variables from .env file...
    for /f "tokens=*" %%a in ('type .env ^| findstr /v "^#" ^| findstr /v "^//"') do set %%a
) else (
    echo Warning: .env file not found
)

echo Starting Appointment Scheduling Agent...
echo.
gradlew.bat run -q --console=plain

