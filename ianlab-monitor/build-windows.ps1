param([switch] $FrameworkDependent)
$ErrorActionPreference = 'Stop'
$project = Join-Path $PSScriptRoot 'src/IanLabMonitor/IanLabMonitor.csproj'
$publish = Join-Path $PSScriptRoot 'dist/publish'
if (Test-Path $publish) { Remove-Item $publish -Recurse -Force }
$mode = if ($FrameworkDependent) { 'false' } else { 'true' }
dotnet publish $project -c Release -r win-x64 --self-contained $mode -p:PublishSingleFile=false -o $publish
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
$destination = Join-Path $PSScriptRoot 'dist/IANLAB-Monitor-win-x64.zip'
if (Test-Path $destination) { Remove-Item $destination -Force }
Compress-Archive -Path (Join-Path $publish '*') -DestinationPath $destination -Force
Write-Host "Created $destination"
