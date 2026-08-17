# SafeWork AI — 시연용 전체 실행
#
#   PowerShell 에서:  .\start-demo.ps1
#   공개 주소까지:    .\start-demo.ps1 -Tunnel
#
# 순서가 중요하다. Postgres 가 먼저 떠야 백엔드가 붙고, 백엔드가 떠야 프론트 프록시가 통한다.
# 각 단계마다 실제로 응답할 때까지 기다린 뒤 다음으로 넘어간다.

param(
    [switch]$Tunnel   # 팀원·심사위원이 접속할 공개 주소까지 만든다
)

# 네이티브 명령(docker, npm, curl)이 stderr 로 한 줄만 뱉어도 PowerShell 5.1 은
# 그것을 오류 레코드로 감싼다. "Stop" 이면 docker 의 경고 한 줄에 스크립트가 죽는다.
$ErrorActionPreference = "Continue"

# ── 환경에 맞게 고칠 곳 ────────────────────────────────────────────
$Repo        = "C:\dev\pnuai-a-07-ai\backend\.claude\worktrees\safework-ai-hackathon-4c9b65"
$MlPython    = "C:\swml\Scripts\python.exe"      # ML 서버 가상환경 (경로가 길면 설치가 깨져서 C:\ 아래에 뒀다)
$Cloudflared = "C:\Program Files (x86)\cloudflared\cloudflared.exe"
$DockerApp   = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
# ──────────────────────────────────────────────────────────────────

$Log = Join-Path $env:TEMP "safework"
New-Item -ItemType Directory -Force $Log | Out-Null

function Step($n, $msg) { Write-Host ("[{0}] {1}" -f $n, $msg) -ForegroundColor Cyan }
function Ok($msg)       { Write-Host ("     OK  " + $msg) -ForegroundColor Green }
function Warn($msg)     { Write-Host ("     !!  " + $msg) -ForegroundColor Yellow }

function Wait-Port($port, $name, $timeoutSec = 300) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $timeoutSec) {
        if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) { return $true }
        Start-Sleep -Seconds 2
    }
    Warn "$name ($port) 가 ${timeoutSec}초 안에 안 떴습니다. 로그: $Log"
    return $false
}

function Test-Port($port) {
    [bool](Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
}

Write-Host ""
Write-Host "SafeWork AI 시연 환경을 띄웁니다" -ForegroundColor White
Write-Host "----------------------------------------"

# 1. Docker + Postgres
Step 1 "Docker · PostgreSQL"
if (-not (Get-Process "Docker Desktop" -ErrorAction SilentlyContinue)) {
    Start-Process $DockerApp
    Write-Host "     Docker Desktop 시작 중 (최대 3분)..."
}
$sw = [Diagnostics.Stopwatch]::StartNew()
while ($sw.Elapsed.TotalSeconds -lt 180) {
    docker info | Out-Null
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep -Seconds 5
}
if ($LASTEXITCODE -ne 0) { Warn "Docker 가 안 떴습니다. 직접 실행 후 다시 시도하세요."; exit 1 }

docker start safework-postgres | Out-Null
$sw = [Diagnostics.Stopwatch]::StartNew()
while ($sw.Elapsed.TotalSeconds -lt 120) {
    docker exec safework-postgres pg_isready -h 127.0.0.1 -U postgres -d ai_safework | Out-Null
    if ($LASTEXITCODE -eq 0) { break }
    Start-Sleep -Seconds 2
}
Ok "Postgres 준비됨"

# 2. 백엔드 — AI 답변에 키가 필요하다. 없으면 조문만 나온다.
Step 2 "백엔드 (8080)"
if (Test-Port 8080) { Ok "이미 떠 있음" }
else {
    $env:GEMINI_API_KEY = [Environment]::GetEnvironmentVariable("GEMINI_API_KEY", "User")
    if (-not $env:GEMINI_API_KEY) { Warn "GEMINI_API_KEY 없음 — AI 답변 없이 조문만 나옵니다" }
    Remove-Item Env:\SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue

    Start-Process -FilePath (Join-Path $Repo "backend\gradlew.bat") -ArgumentList "bootRun" `
        -WorkingDirectory (Join-Path $Repo "backend") -WindowStyle Hidden `
        -RedirectStandardOutput "$Log\backend.log" -RedirectStandardError "$Log\backend.err"
    if (Wait-Port 8080 "백엔드" 300) { Ok "백엔드 준비됨" }
}

# 3. ML 서버 — 없어도 서비스는 돌지만 유사 재해사례와 AI 예측이 빠진다.
Step 3 "ML 서버 (8000)"
if (Test-Port 8000) { Ok "이미 떠 있음" }
else {
    Start-Process -FilePath $MlPython -ArgumentList "-m","uvicorn","app.main:app","--port","8000" `
        -WorkingDirectory (Join-Path $Repo "mlserver") -WindowStyle Hidden `
        -RedirectStandardOutput "$Log\ml.log" -RedirectStandardError "$Log\ml.err"
    Write-Host "     LightGBM 모델 24개 로드 중 (1~2분)..."
    if (Wait-Port 8000 "ML 서버" 300) { Ok "ML 서버 준비됨" }
}

# 4. 프론트
Step 4 "프론트 (5173)"
if (Test-Port 5173) { Ok "이미 떠 있음" }
else {
    $env:Path = [Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [Environment]::GetEnvironmentVariable("Path","User")
    Start-Process -FilePath "npm.cmd" -ArgumentList "run","dev" `
        -WorkingDirectory (Join-Path $Repo "frontend") -WindowStyle Hidden `
        -RedirectStandardOutput "$Log\frontend.log" -RedirectStandardError "$Log\frontend.err"
    if (Wait-Port 5173 "프론트" 180) { Ok "프론트 준비됨" }
}

# 5. 공개 주소 (선택)
$publicUrl = $null
if ($Tunnel) {
    Step 5 "공개 주소 (cloudflare 터널)"
    Remove-Item "$Log\tunnel.log" -ErrorAction SilentlyContinue
    Start-Process -FilePath $Cloudflared -ArgumentList "tunnel","--url","http://localhost:5173","--no-autoupdate" `
        -WindowStyle Hidden -RedirectStandardOutput "$Log\tunnel.out" -RedirectStandardError "$Log\tunnel.log"
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt 90 -and -not $publicUrl) {
        Start-Sleep -Seconds 2
        if (Test-Path "$Log\tunnel.log") {
            $m = Select-String -Path "$Log\tunnel.log" -Pattern "https://[a-z0-9-]+\.trycloudflare\.com" -ErrorAction SilentlyContinue
            if ($m) { $publicUrl = $m.Matches[0].Value }
        }
    }
    if ($publicUrl) { Ok $publicUrl } else { Warn "터널 주소를 못 받았습니다. 로그: $Log\tunnel.log" }
}

# 6. 예열 — 첫 요청이 느리다. 무대에서 기다리지 않게 미리 한 번 돌린다.
Step 6 "예열 (첫 요청이 느립니다)"
try {
    $em = "warmup$(Get-Random)@local"
    $reg = @{ email=$em; password="Test1234!"; name="예열" } | ConvertTo-Json -Compress
    $f = "$Log\warm.json"; [IO.File]::WriteAllText($f, $reg, [Text.UTF8Encoding]::new($false))
    $tok = (curl.exe -s -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d "@$f" | ConvertFrom-Json).accessToken
    curl.exe -s -o NUL "http://localhost:8080/api/references" -H "Authorization: Bearer $tok"
    # ML 은 첫 호출에 임베딩·모델을 올리느라 특히 느리다
    $b = @{ industry="제조업"; sub_industry="금속가공"; size_class="5인 미만"; region="부산" } | ConvertTo-Json -Compress
    $f2 = "$Log\warm2.json"; [IO.File]::WriteAllText($f2, $b, [Text.UTF8Encoding]::new($false))
    curl.exe -s -o NUL -X POST http://localhost:8000/predict/risk -H "Content-Type: application/json" -d "@$f2"
    Ok "예열 완료"
} catch { Warn "예열 중 오류 (동작에는 지장 없음)" }

# 결과
Write-Host ""
Write-Host "----------------------------------------"
foreach ($x in @(@(5432,"Postgres"), @(8080,"백엔드"), @(8000,"ML"), @(5173,"프론트"))) {
    $mark = if (Test-Port $x[0]) { "OK  " } else { "실패" }
    Write-Host ("  {0}  {1,-9} {2}" -f $mark, $x[1], $x[0])
}
Write-Host ""
Write-Host "  화면      http://localhost:5173" -ForegroundColor White
if ($publicUrl) { Write-Host ("  공개 주소  " + $publicUrl) -ForegroundColor White }
Write-Host "  Swagger   http://localhost:8080/swagger-ui/index.html"
Write-Host ""
Write-Host "  로그       $Log"
Write-Host "  내릴 때    .\stop-demo.ps1"
Write-Host ""
