$releaseDir = "PimcoreAutoTool_v2.0_Release"
if (Test-Path $releaseDir) {
    Remove-Item -Recurse -Force $releaseDir
}
New-Item -ItemType Directory -Path $releaseDir | Out-Null

Copy-Item "target\PimcoreAutoTool.jar" -Destination "$releaseDir\PimcoreAutoTool.jar"
Copy-Item "Chay_Tool_Windows.bat" -Destination "$releaseDir\Chay_Tool_Windows.bat"
Copy-Item "Chay_Tool_Mac.command" -Destination "$releaseDir\Chay_Tool_Mac.command"
Copy-Item "Chay_Tool_Linux.sh" -Destination "$releaseDir\Chay_Tool_Linux.sh"
Copy-Item "selectors.properties" -Destination "$releaseDir\selectors.properties"
Copy-Item "src\main\resources\assets\app_logo.png" -Destination "$releaseDir\logo.png"
Copy-Item "Huong_Dan_Su_Dung.txt" -Destination "$releaseDir\Huong_Dan_Su_Dung.txt"

$zipName = "PimcoreAutoTool_v2.0_Release.zip"
if (Test-Path $zipName) {
    Remove-Item -Force $zipName
}
Compress-Archive -Path "$releaseDir\*" -DestinationPath $zipName -Force

Get-Item $zipName | Select-Object Name, Length, LastWriteTime
Get-ChildItem $releaseDir | Select-Object Name, Length, LastWriteTime

