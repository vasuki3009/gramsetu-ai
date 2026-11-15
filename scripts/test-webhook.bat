@echo off
REM GramSetu - send simulated Twilio webhook calls to a LOCAL backend.
REM
REM These requests are NOT signed by Twilio, so the backend must run with signature
REM validation switched off, for local testing only:
REM     set TWILIO_VALIDATE_SIGNATURE=false      (then start the backend)
REM Never disable it on a deployed server.
REM
REM Usage: scripts\test-webhook.bat [base-url]     (default http://localhost:8081)
setlocal
chcp 65001 >nul

set BASE_URL=%~1
if "%BASE_URL%"=="" set BASE_URL=http://localhost:8081
set WEBHOOK_URL=%BASE_URL%/api/whatsapp/webhook
set FAKE_FROM=whatsapp:+10000000000

echo ============================================
echo  GramSetu - WhatsApp webhook tests
echo ============================================
echo Targeting: %WEBHOOK_URL%
echo (a 403 response means signature validation is ON: see the note at the top of this file)
echo.

REM -- Test 1: text message
echo [1/4] Text message (Tamil farming question)...
curl -s -o nul -w "HTTP %%{http_code}\n" -X POST "%WEBHOOK_URL%" ^
  -d "From=%FAKE_FROM%" ^
  -d "Body=நெல்லுக்கு நல்ல உரம் என்ன?" ^
  -d "NumMedia=0"
echo Expected: HTTP 200 (empty body); the reply is sent through the Twilio API.
echo.

REM -- Test 2: audio message (simulated)
echo [2/4] Audio (voice note) message...
echo Note: MediaUrl0 is a fake URL, so the download fails but the routing is exercised.
echo       Look for "Handling audio" in the backend log.
curl -s -o nul -w "HTTP %%{http_code}\n" -X POST "%WEBHOOK_URL%" ^
  -d "From=%FAKE_FROM%" ^
  -d "Body=" ^
  -d "NumMedia=1" ^
  -d "MediaUrl0=https://api.twilio.com/fake-audio.ogg" ^
  -d "MediaContentType0=audio/ogg"
echo.

REM -- Test 3: image message (simulated)
echo [3/4] Image (crop photo) message...
echo Note: MediaUrl0 is a fake URL, so the download fails but the routing is exercised.
echo       Look for "Handling image for crop disease detection" in the backend log.
curl -s -o nul -w "HTTP %%{http_code}\n" -X POST "%WEBHOOK_URL%" ^
  -d "From=%FAKE_FROM%" ^
  -d "Body=" ^
  -d "NumMedia=1" ^
  -d "MediaUrl0=https://api.twilio.com/fake-image.jpg" ^
  -d "MediaContentType0=image/jpeg"
echo.

REM -- Test 4: empty message
echo [4/4] Empty message (should send the welcome text)...
curl -s -o nul -w "HTTP %%{http_code}\n" -X POST "%WEBHOOK_URL%" ^
  -d "From=%FAKE_FROM%" ^
  -d "Body=" ^
  -d "NumMedia=0"
echo.

echo ============================================
echo  Done. Check the backend console output for details.
echo ============================================
endlocal
