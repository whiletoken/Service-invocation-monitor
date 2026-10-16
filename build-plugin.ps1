<#
.SYNOPSIS
    Service Invocation Monitor - IntelliJ IDEA Plugin 一键打包脚本
.DESCRIPTION
    自动解析并配置 JDK 编译环境，执行自动化构建，并将最终的插件安装包 (.zip)
    打包输出至根目录 dist/ 文件夹下。
.PARAMETER Clean
    在打包前清理之前的构建产物 (gradle clean)
.PARAMETER SkipTests
    跳过测试任务 (默认跳过)
.EXAMPLE
    .\build-plugin.ps1
    .\build-plugin.ps1 -Clean
#>

[CmdletBinding()]
param (
    [switch]$Clean,
    [switch]$SkipTests = $true
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location -Path $ScriptDir

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Service Invocation Monitor - IDEA Plugin Packaging Tool" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 获取指定目录 JDK 的主版本号
function Get-JdkMajorVersion([string]$dir) {
    if (-not (Test-Path "$dir\bin\javac.exe")) { return 0 }
    
    # 优先解析 release 文件，极其稳定且无需调用子进程
    $relFile = Join-Path $dir "release"
    if (Test-Path $relFile) {
        $content = Get-Content $relFile -ErrorAction SilentlyContinue
        foreach ($line in $content) {
            if ($line -match 'JAVA_VERSION="1\.([0-9]+)') {
                return [int]$matches[1]
            } elseif ($line -match 'JAVA_VERSION="([0-9]+)') {
                return [int]$matches[1]
            }
        }
    }

    # 回退方式：通过 ProcessStartInfo 安全调用 java.exe -version，避免 PS 5.1 NativeCommandError
    try {
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = Join-Path $dir "bin\java.exe"
        $psi.Arguments = "-version"
        $psi.RedirectStandardError = $true
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true
        $p = [System.Diagnostics.Process]::Start($psi)
        $stderr = $p.StandardError.ReadToEnd()
        $p.WaitForExit()
        if ($stderr -match 'version "1\.([0-9]+)') {
            return [int]$matches[1]
        } elseif ($stderr -match 'version "([0-9]+)') {
            return [int]$matches[1]
        }
    } catch {}

    return 0
}

# 1. 检测与查找适合运行 Gradle (需要 JDK 17+) 的 Java 环境
function Find-GradleJdk {
    # 检查当前 JAVA_HOME
    if ($env:JAVA_HOME) {
        $ver = Get-JdkMajorVersion $env:JAVA_HOME
        if ($ver -ge 17) {
            return $env:JAVA_HOME
        }
    }

    # 常见现代 JDK 安装目录检索
    $searchPaths = @(
        "$env:USERPROFILE\.jdks\ms-17*",
        "$env:USERPROFILE\.jdks\*17*",
        "$env:USERPROFILE\.jdks\*21*",
        "$env:USERPROFILE\.jdks\*25*",
        "C:\Program Files\Java\jdk-17*",
        "C:\Program Files\Java\jdk-21*",
        "C:\Program Files\Microsoft\jdk-17*",
        "C:\Program Files\Microsoft\jdk-21*",
        "C:\Program Files\Eclipse Adoptium\jdk-17*",
        "C:\Program Files\Eclipse Adoptium\jdk-21*"
    )

    foreach ($pattern in $searchPaths) {
        $dirs = Get-ChildItem -Path $pattern -Directory -ErrorAction SilentlyContinue
        foreach ($item in $dirs) {
            $ver = Get-JdkMajorVersion $item.FullName
            if ($ver -ge 17) {
                return $item.FullName
            }
        }
    }

    return $null
}

$gradleJdk = Find-GradleJdk
if ($gradleJdk) {
    Write-Host "[INFO] Using JDK for Gradle: $gradleJdk" -ForegroundColor Green
    $env:JAVA_HOME = $gradleJdk
} else {
    Write-Host "[WARN] No JDK 17+ found in standard paths. Attempting default environment..." -ForegroundColor Yellow
}

# 2. 检索 IntelliJ SDK 平台配套的 JBR (支持 Java 25 编译器工具链)
function Find-JbrPath {
    $gradleCacheTransforms = "$env:USERPROFILE\.gradle\caches\*\transforms\*\transformed\idea-*\jbr"
    $found = Get-ChildItem -Path $gradleCacheTransforms -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) {
        return $found.FullName
    }
    return $null
}

$jbrPath = Find-JbrPath
$extraGradleArgs = @()
if ($jbrPath) {
    Write-Host "[INFO] Detected IntelliJ Platform JBR toolchain: $jbrPath" -ForegroundColor Green
    $extraGradleArgs += "-Dorg.gradle.java.installations.paths=$jbrPath"
}

# 3. 构造 Gradle 命令
$gradleCmd = if ($IsWindows -or $env:OS -match "Windows") { ".\gradlew.bat" } else { "./gradlew" }
if (-not (Test-Path $gradleCmd)) {
    Write-Host "[ERROR] Gradle wrapper ($gradleCmd) not found in $ScriptDir" -ForegroundColor Red
    exit 1
}

# 4. 执行构建
$tasks = @()
if ($Clean) {
    $tasks += "clean"
}
$tasks += ":monitor-plugin:packagePlugin"

if ($SkipTests) {
    $extraGradleArgs += "-x"
    $extraGradleArgs += "test"
}

Write-Host "[EXEC] Running: $gradleCmd $($tasks -join ' ') $($extraGradleArgs -join ' ')" -ForegroundColor Cyan
Write-Host "------------------------------------------------------------" -ForegroundColor DarkGray

& $gradleCmd ($tasks + $extraGradleArgs)
$gradleExitCode = $LASTEXITCODE

if ($gradleExitCode -ne 0) {
    Write-Host "------------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host "[FAILED] Build failed with exit code $gradleExitCode" -ForegroundColor Red
    exit $gradleExitCode
}

# 5. 校验输出产物
$distDir = Join-Path $ScriptDir "dist"
$zipFiles = Get-ChildItem -Path $distDir -Filter "*.zip" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending

if ($zipFiles.Count -gt 0) {
    $latestZip = $zipFiles[0]
    $sizeMb = [math]::Round($latestZip.Length / 1MB, 2)
    $hash = (Get-FileHash -Path $latestZip.FullName -Algorithm SHA256).Hash

    Write-Host "============================================================" -ForegroundColor Green
    Write-Host "  打包成功! (BUILD & PACKAGING SUCCESSFUL)" -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host " 产物文件: $($latestZip.FullName)" -ForegroundColor Yellow
    Write-Host " 文件大小: $sizeMb MB ($($latestZip.Length) bytes)" -ForegroundColor Yellow
    Write-Host " SHA-256 : $hash" -ForegroundColor Yellow
    Write-Host "------------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host " 安装使用说明:" -ForegroundColor Cyan
    Write-Host "   1. 打开 IntelliJ IDEA"
    Write-Host "   2. 进入 Settings (Preferences) -> Plugins"
    Write-Host "   3. 点击齿轮图标 ⚙️ -> 选择 'Install Plugin from Disk...'"
    Write-Host "   4. 选择产物文件: $($latestZip.FullName)"
    Write-Host "   5. 重启 IDEA 即可生效!"
    Write-Host "============================================================" -ForegroundColor Green
} else {
    Write-Host "[WARN] Packaging task completed, but no .zip artifact found in dist/" -ForegroundColor Yellow
}
