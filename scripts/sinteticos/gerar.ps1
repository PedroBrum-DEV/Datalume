# Gera (ou remove) dados sinteticos no Postgres do docker-compose.
#   .\scripts\sinteticos\gerar.ps1 -Quantidade 2000000
#   .\scripts\sinteticos\gerar.ps1 -Limpar
#   .\scripts\sinteticos\gerar.ps1 -Explain
param(
    [int]$Quantidade = 2000000,
    [switch]$Limpar,
    [switch]$Explain,
    [string]$Container = 'datalume-db'
)
$ErrorActionPreference = 'Stop'
$arquivo = if ($Limpar) { 'limpar.sql' } elseif ($Explain) { 'explain.sql' } else { 'gerar.sql' }
$caminho = Join-Path $PSScriptRoot $arquivo
Write-Host "Executando $arquivo no container $Container..."
Get-Content -Path $caminho -Raw | docker exec -i $Container psql -U datalume -d datalume -v ON_ERROR_STOP=1 -v quantidade=$Quantidade
if ($LASTEXITCODE -ne 0) { throw "psql terminou com erro ($LASTEXITCODE)" }
Write-Host "Concluido." -ForegroundColor Green