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
# ngrok.exe 위치.
#
# 이 PC 에는 ngrok 이 둘 있다. winget 으로 깐 3.3.1 이 PATH 에 잡혀 있고,
# 고정 도메인에 필요한 --url 플래그는 3.20 부터 생겼다. 경로를 한 곳만 박아
# 두거나 PATH 를 그냥 쓰면 어느 쪽이 걸릴지 운에 맡기게 된다.
# 실제로 옛 버전이 걸려 "unknown flag: --url" 로 실패하고 cloudflared 로
# 넘어갔다. 그래서 후보를 모두 모아 버전을 물어보고 가장 새 것을 쓴다.
# 첫 줄이 기준 경로다. 환경변수를 쓰지 않는 절대 경로로 둔 이유가 있다.
# $env:LOCALAPPDATA 로 적었더니 쉘에 따라 못 찾는 일이 반복됐고, 그때마다
# PATH 의 옛 버전이 걸려 실패했다.
$NgrokCandidates = @(
    "C:\dev\ngrok\ngrok.exe",
    "$env:LOCALAPPDATA\ngrok-bin\ngrok.exe",
    "$env:USERPROFILE\ngrok.exe",
    (Get-Command ngrok.exe -ErrorAction SilentlyContinue).Source
) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique

$Ngrok = $null
$NgrokVersion = $null
foreach ($candidate in $NgrokCandidates) {
    $raw = & $candidate version 2>&1 | Out-String
    if ($raw -match '(\d+)\.(\d+)\.(\d+)') {
        $v = [version]"$($Matches[1]).$($Matches[2]).$($Matches[3])"
        if (-not $NgrokVersion -or $v -gt $NgrokVersion) {
            $NgrokVersion = $v
            $Ngrok = $candidate
        }
    }
}
# ngrok 계정이 요구하는 최소 에이전트 버전이 3.20 이다. 그보다 낮으면 접속
# 자체가 거부되므로(ERR_NGROK_121) 아예 쓰지 않는다. 예전에는 PATH 에 있던
# 3.3.1 이 걸려서 매번 실패하고 cloudflared 로 넘어갔다.
$NgrokTooOld = $null
if ($Ngrok -and $NgrokVersion -lt [version]"3.20.0") {
    $NgrokTooOld = "$NgrokVersion ($Ngrok)"
    $Ngrok = $null
}
# ngrok 무료 등급이 주는 고정 도메인. 계정에 예약해 둔 이름을 그대로 적는다.
# 비워 두면 ngrok 이 임시 주소를 주므로 고정 주소의 이점이 사라진다.
$NgrokDomain = "haste-denture-tree.ngrok-free.dev"
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

    # JWT 서명 키. 저장소에 기본값을 두지 않으므로(공개 저장소라 그게 키 유출이다)
    # 이 PC 에만 있는 .env 에서 읽고, 없으면 여기서 만들어 넣는다.
    $envFile = Join-Path $Repo ".env"
    if (Test-Path $envFile) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^\s*([A-Z_]+)\s*=\s*(.*)$' -and $Matches[2]) {
                Set-Item -Path "Env:\$($Matches[1])" -Value $Matches[2].Trim()
            }
        }
    }
    if (-not $env:JWT_SECRET) {
        $env:JWT_SECRET = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
        Add-Content -Path $envFile -Value "JWT_SECRET=$($env:JWT_SECRET)" -Encoding utf8
        Ok "JWT 서명 키를 새로 만들어 .env 에 넣었습니다 (git 에 올라가지 않음)"
    }

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
#
# ngrok 을 먼저 쓴다. 무료 등급이 주는 고정 도메인은 껐다 켜도 그대로라,
# 팀원에게 주소를 한 번만 알려주면 된다. cloudflared 임시 터널은 켤 때마다
# 새 주소를 받아서 매번 다시 공지해야 했다.
# ngrok 이 안 되면 cloudflared 로 넘어간다 — 주소는 바뀌지만 없는 것보다 낫다.
$publicUrl = $null
if ($Tunnel) {
    Step 5 "공개 주소"

    # 이전에 뜬 터널이 남아 있으면 정리한다. ngrok 무료는 동시 세션이 하나뿐이라
    # 남아 있으면 새로 못 뜨고, cloudflared 가 둘 뜨면 주소가 헷갈린다.
    #
    # Stop-Process 는 종료를 요청만 하고 곧바로 돌아온다. 죽는 중인 프로세스가
    # 로그 파일 핸들을 쥔 채로 다음 줄이 실행되면, 그 파일로 리다이렉트하는
    # Start-Process 가 조용히 실패한다(실제로 이것 때문에 ngrok 이 안 떴다).
    # 그래서 실제로 끝날 때까지 기다린다.
    $dying = Get-Process ngrok, cloudflared -ErrorAction SilentlyContinue
    if ($dying) {
        $dying | Stop-Process -Force -ErrorAction SilentlyContinue
        $dying | Wait-Process -Timeout 10 -ErrorAction SilentlyContinue
    }

    if (-not $Ngrok) {
        # 여기서 아무 말 없이 넘어가면, 왜 고정 주소가 안 나오는지 알 수가 없다.
        if ($NgrokTooOld) {
            Warn "ngrok 이 너무 오래됐습니다: $NgrokTooOld (3.20 이상 필요)"
            Write-Host "        새 ngrok 을 C:\dev\ngrok\ngrok.exe 에 두면 됩니다" -ForegroundColor DarkGray
        } else {
            Warn "ngrok.exe 를 찾지 못했습니다. 찾아본 곳:"
            $NgrokCandidates | ForEach-Object { Write-Host "        $_" -ForegroundColor DarkGray }
        }
    }

    if ($Ngrok) {
        # 어느 ngrok 을 쓰는지 남긴다. 두 개가 깔려 있어 헷갈렸던 적이 있다.
        Write-Host "     ngrok $NgrokVersion  ($Ngrok)" -ForegroundColor DarkGray
        # 그래도 파일이 잡혀 있을 수 있으니, 못 지우면 새 이름을 쓴다.
        Remove-Item "$Log\ngrok.log" -ErrorAction SilentlyContinue
        if (Test-Path "$Log\ngrok.log") {
            $stamp = Get-Date -Format "HHmmss"
            $NgrokLog = "$Log\ngrok-$stamp.log"
            $NgrokErr = "$Log\ngrok-$stamp.err"
        } else {
            $NgrokLog = "$Log\ngrok.log"
            $NgrokErr = "$Log\ngrok.err"
        }
        # 변수 이름을 $args 로 쓰면 안 된다. PowerShell 예약 변수(스크립트에 넘어온
        # 잔여 인자)라서 값을 넣어도 Start-Process 에 제대로 전달되지 않는다.
        # 실제로 그 탓에 ngrok 이 시작도 못 하고 조용히 cloudflared 로 넘어갔었다.
        $ngrokArgs = @("http", "--log=stdout", "--log-format=logfmt")
        # 여기까지 왔으면 3.20 이상이 확정이라 --url 을 그대로 쓴다.
        if ($NgrokDomain) { $ngrokArgs += "--url=https://$NgrokDomain" }
        $ngrokArgs += "5173"
        # Start-Process 가 실패해도 스크립트는 계속 돈다($ErrorActionPreference=Continue).
        # 실패를 놓치지 않도록 여기서 잡아 둔다.
        $started = $true
        try {
            Start-Process -FilePath $Ngrok -ArgumentList $ngrokArgs -WindowStyle Hidden `
                -RedirectStandardOutput $NgrokLog -RedirectStandardError $NgrokErr -ErrorAction Stop
        } catch {
            $started = $false
            Warn "ngrok 을 시작하지 못했습니다: $($_.Exception.Message)"
        }

        $sw = [Diagnostics.Stopwatch]::StartNew()
        while ($started -and $sw.Elapsed.TotalSeconds -lt 45 -and -not $publicUrl) {
            Start-Sleep -Seconds 2
            # 로컬 관리 API 가 현재 터널 주소를 알려준다. 로그를 긁는 것보다 정확하다.
            try {
                $t = (Invoke-RestMethod "http://127.0.0.1:4040/api/tunnels" -TimeoutSec 2).tunnels |
                     Where-Object { $_.proto -eq "https" } | Select-Object -First 1
                if ($t) { $publicUrl = $t.public_url }
            } catch { }
        }
        if ($publicUrl) { Ok "$publicUrl  (고정 주소 — 다시 켜도 그대로)" }
        else {
            Warn "ngrok 실패"
            # 왜 실패했는지 그 자리에서 보여준다. 로그를 따로 열어 보게 하면
            # 대개 안 열어 보고 주소가 바뀐 채로 시연에 들어간다.
            Get-Content $NgrokLog, $NgrokErr -Tail 5 -ErrorAction SilentlyContinue |
                Where-Object { $_ -match "err|ERR_|lvl=eror|lvl=warn" } |
                ForEach-Object { Write-Host "        $_" -ForegroundColor DarkYellow }
        }
    }

    if (-not $publicUrl -and (Test-Path $Cloudflared)) {
        Warn "cloudflared 로 대신합니다 (주소가 매번 바뀝니다)"
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
