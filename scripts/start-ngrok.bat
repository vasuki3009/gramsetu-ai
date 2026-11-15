@echo off
REM GramSetu - expose the local backend to Twilio with an ngrok tunnel.
REM Usage: scripts\start-ngrok.bat            (optionally: set PORT=9000 first)
setlocal
if "%PORT%"=="" set PORT=8081

echo ============================================
echo  GramSetu - starting ngrok tunnel on :%PORT%
echo ============================================
echo.
echo Make sure the backend is running first:
echo   mvn spring-boot:run   OR   docker compose up --build
echo.
echo After ngrok starts, copy the https "Forwarding" URL (https://xxxx.ngrok-free.app), then:
echo   1. Set it as APP_PUBLIC_BASE_URL in your .env and restart the backend.
echo      The Twilio signature check validates against exactly this URL.
echo   2. Paste  https://YOUR-NGROK-URL/api/whatsapp/webhook  into the Twilio WhatsApp
echo      Sandbox settings (WHEN A MESSAGE COMES IN, method POST):
echo      https://console.twilio.com/us1/develop/sms/try-it-out/whatsapp-learn
echo.
echo ============================================
echo  ngrok installation (if not installed)
echo ============================================
echo   Option A - Chocolatey:  choco install ngrok
echo   Option B - Manual:
echo     1. Download from https://ngrok.com/download (Windows ZIP)
echo     2. Extract ngrok.exe to C:\ngrok\ (or anywhere on PATH)
echo     3. Sign up at https://ngrok.com and copy your auth token
echo     4. Run: ngrok config add-authtoken YOUR_TOKEN
echo ============================================
echo.
ngrok http %PORT%
endlocal
