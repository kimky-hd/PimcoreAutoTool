# Script khởi tạo môi trường test cho PimcoreAutoTool
$ErrorActionPreference = "Stop"

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "       KHOI TAO MOI TRUONG TEST CHO PIMCORE AUTO TOOL (MOCK TEST)" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

$chromeCandidates = @(
    "C:\Program Files\Google\Chrome\Application\chrome.exe",
    "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
)

$chrome = $chromeCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $chrome) {
    Write-Host "[LOI] Khong tim thay Google Chrome tren may cua ban!" -ForegroundColor Red
    pause
    exit 1
}

$mockPimcore = (Resolve-Path "mock_environment/mock_pimcore.html").Path.Replace("\", "/")
$mockAppscript = (Resolve-Path "mock_environment/mock_appscript.html").Path.Replace("\", "/")

$portOpen = $false
try {
    $socket = New-Object System.Net.Sockets.TcpClient("127.0.0.1", 9222)
    $socket.Close()
    $portOpen = $true
} catch {
    $portOpen = $false
}

if ($portOpen) {
    Write-Host "[THONG BAO] Chrome Debug dang chay san tai 127.0.0.1:9222." -ForegroundColor Green
    Write-Host "Dang mo 2 tab Mock vao cua so Chrome..." -ForegroundColor Yellow
    try {
        Invoke-RestMethod -Method Put -Uri "http://127.0.0.1:9222/json/new?file:///$mockPimcore" | Out-Null
        Invoke-RestMethod -Method Put -Uri "http://127.0.0.1:9222/json/new?file:///$mockAppscript" | Out-Null
        Write-Host "=> DA MO THANH CONG 2 TAB MOCK TREN CHROME!" -ForegroundColor Green
    } catch {
        Write-Host "[CANH BAO] Khong the gui lenh CDP toi Chrome: $_" -ForegroundColor Yellow
    }
} else {
    Write-Host "Dang khoi chay Google Chrome Debug tren cong 9222..." -ForegroundColor Yellow
    Start-Process -FilePath $chrome -ArgumentList "--remote-debugging-port=9222", "--user-data-dir=C:\ChromeAutomationProfile", "file:///$mockPimcore", "file:///$mockAppscript"
    Write-Host "=> DA KHOI CHAY CHROME KEM 2 TAB MOCK THANH CONG!" -ForegroundColor Green
}

Write-Host ""
Write-Host "HUONG DAN TIEP THEO:" -ForegroundColor Cyan
Write-Host "1. Tren cua so Chrome vua mo, hay mo them 1 tab Google Doc co chu boi dam."
Write-Host "2. Mo Tool: java -jar target\PimcoreAutoTool.jar"
Write-Host "3. Dan link Google Doc vao o nhap, chinh 'Cho Sync' thanh 2-3 giay."
Write-Host "4. Bam '2. Bat dau chay (Start)' de test." -ForegroundColor Green
Write-Host "==============================================================================" -ForegroundColor Cyan

