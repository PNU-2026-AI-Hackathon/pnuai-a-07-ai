# SafeWork AI — 시연 환경 정리
#
#   .\stop-demo.ps1          백엔드·ML·프론트·터널만 내린다 (DB 는 그대로)
#   .\stop-demo.ps1 -All     Postgres 컨테이너까지 내린다
#
# DB 는 기본적으로 남겨 둔다. 다시 띄울 때 오래 걸리고, 시연용 계정·진단 기록이 들어 있다.
# (컨테이너를 내려도 데이터는 볼륨에 남는다. 지워지는 건 `docker compose down -v` 뿐이다)

param([switch]$All)

# docker 가 stderr 로 경고를 뱉어도 스크립트가 죽지 않게 한다(PowerShell 5.1).
$ErrorActionPreference = "Continue"

function Kill-Port($port, $name) {
    $c = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($c) {
        Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue
        Write-Host ("  내림  {0} ({1})" -f $name, $port)
    } else {
        Write-Host ("  이미 꺼짐  {0} ({1})" -f $name, $port)
    }
}

Write-Host ""
Kill-Port 5173 "프론트"
Kill-Port 8000 "ML 서버"
Kill-Port 8080 "백엔드"

# gradlew bootRun 은 자식 자바 프로세스를 남기는 경우가 있다.
Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like "*bootRun*" -or $_.CommandLine -like "*SafeworkBackendApplication*" } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force
Get-Process ngrok -ErrorAction SilentlyContinue | Stop-Process -Force
Write-Host "  내림  터널"

if ($All) {
    docker stop safework-postgres | Out-Null
    Write-Host "  내림  Postgres (데이터는 볼륨에 남습니다)"
} else {
    Write-Host "  유지  Postgres — 전부 내리려면 -All"
}
Write-Host ""
