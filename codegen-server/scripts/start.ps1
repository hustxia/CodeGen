#requires -Version 5.1
<#
.SYNOPSIS
  一键启动 codegen 前后端（后端 8120 / 前端 9530）。
.DESCRIPTION
  - 后端：target/codegen-server.jar，JDK 固定 D:/tool/jdk-21.0.12.1+1
    （~/.jdks/temurin-21 已损坏，见坑库「环境与工具链」）
  - 显式传 --server.port=8120，并清掉 SERVER__PORT / SERVER_PORT
    （Spring 宽松绑定会把环境变量当端口，见坑库 spring-boot-3.md #5）
  - jar 不存在时自动 Maven 打包（跳过测试）
  - 前端：codegen-web，npx vue-cli-service serve（端口 9530）
  - 日志：后端 logback 自己写 logs/codegen-server.log；
    控制台兜底 logs/*.out.log / *.err.log（logs/ 已被 .gitignore 忽略）
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\start.ps1
#>

$ErrorActionPreference = 'Stop'

$root      = Split-Path -Parent $PSScriptRoot          # codegen-server 根目录
$webDir    = Join-Path (Split-Path -Parent $root) 'codegen-web'
$jdkJava   = 'D:\tool\jdk-21.0.12.1+1\bin\java.exe'
$mvnCmd    = 'C:\dev\maven\bin\mvn.cmd'
$mvnSet    = 'C:\dev\maven-settings.xml'
$jar       = Join-Path $root 'target\codegen-server.jar'
$logDir    = Join-Path $root 'logs'
$backPort  = 8120
$frontPort = 9530

function Test-Port([int]$Port) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $iar = $c.BeginConnect('127.0.0.1', $Port, $null, $null)
        if ($iar.AsyncWaitHandle.WaitOne(500) -and $c.Connected) {
            $c.EndConnect($iar); $c.Close(); return $true
        }
        $c.Close(); return $false
    } catch { return $false }
}

function Wait-Port([int]$Port, [int]$TimeoutSec, [string]$Name) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-Port $Port) { return $true }
        Start-Sleep -Seconds 2
    }
    Write-Host "[超时] $Name 在 $TimeoutSec 秒内未就绪，日志见 $logDir" -ForegroundColor Red
    return $false
}

# 关键：清掉会劫持 server.port 的环境变量（对本脚本及其子进程生效）
Remove-Item Env:SERVER__PORT -ErrorAction SilentlyContinue
Remove-Item Env:SERVER_PORT  -ErrorAction SilentlyContinue

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

# ---------- 后端 ----------
if (Test-Port $backPort) {
    Write-Host "[跳过] 后端 $backPort 已在运行" -ForegroundColor Yellow
} else {
    if (-not (Test-Path $jdkJava)) { throw "JDK 不存在：$jdkJava" }
    if (-not (Test-Path $jar)) {
        Write-Host "[构建] jar 不存在，开始打包（跳过测试）..." -ForegroundColor Cyan
        & $mvnCmd -s $mvnSet -q package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "Maven 打包失败" }
    }
    # 不用 cmd 包层：Start-Process 直接拉起 java，控制台输出走 Redirect 文件，
    # 应用日志由 logback 写 logs/codegen-server.log（嵌套引号在 PS5 下会被吃掉，坑过）
    Start-Process -FilePath $jdkJava -WindowStyle Hidden `
        -ArgumentList '-jar', "`"$jar`"", '--server.port=8120' `
        -WorkingDirectory $root `
        -RedirectStandardOutput (Join-Path $logDir 'codegen-server.out.log') `
        -RedirectStandardError  (Join-Path $logDir 'codegen-server.err.log')
    Write-Host "[启动] 后端进程已派生，等待 $backPort 就绪..." -ForegroundColor Cyan
    if (Wait-Port $backPort 60 '后端') {
        Write-Host "[OK] 后端已就绪  http://127.0.0.1:$backPort" -ForegroundColor Green
    }
}

# ---------- 前端 ----------
if (-not (Test-Path $webDir)) {
    Write-Host "[跳过] 前端目录不存在：$webDir" -ForegroundColor Yellow
} elseif (Test-Port $frontPort) {
    Write-Host "[跳过] 前端 $frontPort 已在运行" -ForegroundColor Yellow
} else {
    Start-Process -FilePath 'cmd.exe' -WindowStyle Hidden `
        -ArgumentList '/c', 'npx vue-cli-service serve' `
        -WorkingDirectory $webDir `
        -RedirectStandardOutput (Join-Path $logDir 'codegen-web.out.log') `
        -RedirectStandardError  (Join-Path $logDir 'codegen-web.err.log')
    Write-Host "[启动] 前端已派生，等待 $frontPort 就绪（首次编译较慢）..." -ForegroundColor Cyan
    if (Wait-Port $frontPort 120 '前端') {
        Write-Host "[OK] 前端已就绪  http://127.0.0.1:$frontPort  （admin / admin123）" -ForegroundColor Green
    }
}

Write-Host "日志目录：$logDir" -ForegroundColor DarkGray
