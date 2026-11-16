param(
    [int]$WaitSeconds = 0
)
$ErrorActionPreference = 'Stop'

$root = Split-Path $PSScriptRoot -Parent

# -WaitSeconds > 0：轮询等待 IDEA 退出（插件 jar 被内存映射占用，运行期间无法替换）
if ($WaitSeconds -gt 0) {
    $deadline = (Get-Date).AddSeconds($WaitSeconds)
    "waiting up to $WaitSeconds s for idea64.exe to exit..."
    while (Get-Process -Name 'idea64' -ErrorAction SilentlyContinue) {
        if ((Get-Date) -gt $deadline) { throw 'timed out waiting for IDEA to exit' }
        Start-Sleep -Seconds 5
    }
    'IDEA exited, installing now.'
}

if (Get-Process -Name 'idea64' -ErrorAction SilentlyContinue) {
    'ERROR: IntelliJ IDEA is running (idea64.exe). Close it, or re-run with -WaitSeconds 7200.'
    exit 3
}

$distDir = Join-Path $root 'build\distributions'
$zip = Get-ChildItem $distDir -Filter 'GitLabPlus-*.zip' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $zip) { throw "plugin zip not found in $distDir (run: gradlew buildPlugin)" }

# 目标 IDEA 配置目录：优先取已存在的 IntelliJIdea* 目录，回退到 2026.2
$jetbrains = Join-Path $env:APPDATA 'JetBrains'
$ideDir = Get-ChildItem $jetbrains -Directory -Filter 'IntelliJIdea*' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $ideDir) { throw "no IntelliJIdea* directory under $jetbrains" }

$pluginsDir = Join-Path $ideDir.FullName 'plugins'
New-Item -ItemType Directory -Path $pluginsDir -Force | Out-Null

# 解压出 GitLabPlus/（zip 内已是 GitLabPlus/lib/<jar> 结构），先清理旧目录
$target = Join-Path $pluginsDir 'GitLabPlus'
if (Test-Path $target) { Remove-Item $target -Recurse -Force }
Expand-Archive -Path $zip.FullName -DestinationPath $pluginsDir -Force

$jar = Get-ChildItem (Join-Path $target 'lib') -Filter '*.jar' -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $jar) { throw "installed layout looks wrong: no jar under $target\lib" }

"installed: $($jar.FullName)  size=$($jar.Length)  from=$($zip.Name)"
