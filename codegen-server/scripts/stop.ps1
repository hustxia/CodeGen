#requires -Version 5.1
<#
.SYNOPSIS
  停止 codegen 前后端（按监听端口定位进程，后端 8120 / 前端 9530）。
.DESCRIPTION
  - 先按端口找 PID（最准）；后端找不到再按命令行含 codegen-server.jar 兜底
  - 只杀匹配到的进程，不误伤其它 Java/Node 程序
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\stop.ps1
#>

$ErrorActionPreference = 'Continue'

$backPort  = 8120
$frontPort = 9530

function Stop-ByPort([int]$Port, [string]$Name) {
    $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) {
        Write-Host "[跳过] $Name（$Port）未在运行" -ForegroundColor Yellow
        return $false
    }
    foreach ($procId in ($conns | Select-Object -ExpandProperty OwningProcess -Unique)) {
        $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if ($p) {
            Write-Host "[停止] $Name pid=$procId ($($p.ProcessName))" -ForegroundColor Cyan
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
    }
    return $true
}

# 前端：按端口
Stop-ByPort $frontPort '前端' | Out-Null

# 后端：按端口；端口没监听时按 jar 命令行兜底（处理启动卡死没监听端口的场景）
if (-not (Stop-ByPort $backPort '后端')) {
    $procs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match 'codegen-server\.jar' }
    foreach ($p in $procs) {
        Write-Host "[停止] 后端残留进程 pid=$($p.ProcessId)" -ForegroundColor Cyan
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    }
    if (-not $procs) { Write-Host "[跳过] 未发现后端 java 进程" -ForegroundColor Yellow }
}

Write-Host "[OK] 已停止" -ForegroundColor Green
