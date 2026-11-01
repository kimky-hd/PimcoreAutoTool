$ErrorActionPreference = "Stop"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "      BẮT ĐẦU ĐÓNG GÓI PHIÊN BẢN PORTABLE (ZERO-INSTALL JAVA)     " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# 1. Tạo custom JRE Windows nếu chưa có
$jreWinDir = "target/jre_windows"
if (-not (Test-Path "$jreWinDir/bin/javaw.exe")) {
    Write-Host "[1/4] Đang tạo bộ máy ảo Java Portable siêu nhẹ cho Windows qua jlink..." -ForegroundColor Yellow
    if (Test-Path $jreWinDir) { Remove-Item -Recurse -Force $jreWinDir }
    & "C:\Program Files\Java\jdk-17\bin\jlink.exe" `
        --add-modules java.base,java.desktop,java.logging,java.sql,java.naming,java.management,java.instrument,java.net.http,jdk.unsupported `
        --strip-debug `
        --no-man-pages `
        --no-header-files `
        --output $jreWinDir
    Write-Host "   -> Đã tạo xong Portable JRE Windows thành công!" -ForegroundColor Green
} else {
    Write-Host "[1/4] Đã có sẵn Portable JRE Windows tại $jreWinDir." -ForegroundColor Green
}

# 2. Đóng gói bản WINDOWS PORTABLE
Write-Host "[2/4] Đóng gói thư mục PimcoreAutoTool_v2.0_Windows_Portable..." -ForegroundColor Yellow
$winPkgDir = "PimcoreAutoTool_v2.0_Windows_Portable"
if (Test-Path $winPkgDir) { Remove-Item -Recurse -Force $winPkgDir }
New-Item -ItemType Directory -Path $winPkgDir | Out-Null

Copy-Item "target/PimcoreAutoTool.jar" -Destination "PimcoreAutoTool.jar" -Force
Copy-Item -Recurse "$jreWinDir" -Destination "$winPkgDir/jre"
Copy-Item "target/PimcoreAutoTool.jar" -Destination "$winPkgDir/PimcoreAutoTool.jar"
Copy-Item "Chay_Tool_Windows.bat" -Destination "$winPkgDir/Chay_Tool_Windows.bat"
Copy-Item "selectors.properties" -Destination "$winPkgDir/selectors.properties"
Copy-Item "src/main/resources/assets/app_logo.png" -Destination "$winPkgDir/logo.png"
Copy-Item "Huong_Dan_Su_Dung_Windows.txt" -Destination "$winPkgDir/Huong_Dan_Su_Dung_Windows.txt"

$winZip = "PimcoreAutoTool_v2.0_Windows_Portable.zip"
if (Test-Path $winZip) { Remove-Item -Force $winZip }
Write-Host "   -> Đang nén file $winZip bằng chuẩn .NET Zip chuẩn Windows..." -ForegroundColor Yellow
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory((Resolve-Path $winPkgDir).Path, (Join-Path (Get-Location) $winZip), [System.IO.Compression.CompressionLevel]::Optimal, $false)
Write-Host "   -> Hoàn thành gói Windows: $winZip" -ForegroundColor Green

# 3. Đóng gói bản macOS
Write-Host "[3/4] Đóng gói thư mục PimcoreAutoTool_v2.0_macOS..." -ForegroundColor Yellow
$macPkgDir = "PimcoreAutoTool_v2.0_macOS"
if (Test-Path $macPkgDir) { Remove-Item -Recurse -Force $macPkgDir }
New-Item -ItemType Directory -Path $macPkgDir | Out-Null

Copy-Item "target/PimcoreAutoTool.jar" -Destination "$macPkgDir/PimcoreAutoTool.jar"
Copy-Item "Chay_Tool_Mac.command" -Destination "$macPkgDir/Chay_Tool_Mac.command"
Copy-Item "selectors.properties" -Destination "$macPkgDir/selectors.properties"
Copy-Item "src/main/resources/assets/app_logo.png" -Destination "$macPkgDir/logo.png"
Copy-Item "Huong_Dan_Su_Dung_Mac.txt" -Destination "$macPkgDir/Huong_Dan_Su_Dung_Mac.txt"

$macZip = "PimcoreAutoTool_v2.0_macOS.zip"
if (Test-Path $macZip) { Remove-Item -Force $macZip }
Write-Host "   -> Đang nén file $macZip bằng chuẩn .NET Zip..." -ForegroundColor Yellow
[System.IO.Compression.ZipFile]::CreateFromDirectory((Resolve-Path $macPkgDir).Path, (Join-Path (Get-Location) $macZip), [System.IO.Compression.CompressionLevel]::Optimal, $false)
Write-Host "   -> Hoàn thành gói macOS: $macZip" -ForegroundColor Green

# 4. Hiển thị kết quả
Write-Host ""
Write-Host "[4/4] TỔNG KẾT CÁC GÓI PHÁT HÀNH CHO KHÁCH HÀNG:" -ForegroundColor Cyan
Get-Item $winZip, $macZip | Select-Object Name, @{Name="Size (MB)"; Expression={[math]::Round($_.Length / 1MB, 2)}}, LastWriteTime | Format-Table -AutoSize

