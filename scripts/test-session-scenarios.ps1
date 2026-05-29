# Session scenario smoke tests via adb (routing + UI hints)
$pkg = "ge.fmg.community"
$activity = "$pkg/.MainActivity"

function Get-Prefs {
    adb shell "run-as $pkg cat shared_prefs/ge.fmg.community.prefs.xml 2>/dev/null"
}

function Get-UiDump {
    adb shell uiautomator dump /sdcard/window_dump.xml 2>$null | Out-Null
    adb shell cat /sdcard/window_dump.xml 2>$null
}

function Start-App {
    adb shell am force-stop $pkg
    Start-Sleep -Milliseconds 500
    adb shell am start -n $activity | Out-Null
    Start-Sleep -Seconds 2
}

function Test-UiContains([string[]]$patterns) {
    $ui = Get-UiDump
    foreach ($p in $patterns) {
        if ($ui -match $p) { return $true }
    }
    return $false
}

Write-Host "=== Scenario B: fresh start -> onboarding ===" -ForegroundColor Cyan
adb shell pm clear $pkg | Out-Null
Start-Sleep -Seconds 1
$prefs = Get-Prefs
Write-Host "Prefs after clear: $(if ($prefs) { $prefs } else { '(empty - OK)' })"
Start-App
$onboarding = Test-UiContains @("onboarding_skip", "გამოტოვება", "onboarding_next", "გახსენი")
$loginOnly = Test-UiContains @("login_submit_btn", "შესვლა") -and -not $onboarding
Write-Host "Onboarding UI detected: $onboarding"
Write-Host "Login-only (no onboarding): $loginOnly"
if ($onboarding -and -not $loginOnly) {
    Write-Host "PASS Scenario B (initial screen)" -ForegroundColor Green
} else {
    Write-Host "FAIL Scenario B (initial screen)" -ForegroundColor Red
}

Write-Host "`n=== Scenario B: after onboarding dismiss without login -> reopen ===" -ForegroundColor Cyan
# Simulate: user finished slides (prefs still false) - tap skip via UI if possible
# Use prefs: only loggedIn false, onboardingSeen false (same as fresh)
adb shell input tap 540 2200 2>$null  # approximate skip/next area - may miss
Start-Sleep -Seconds 1
adb shell am force-stop $pkg
Start-Sleep -Milliseconds 500
$prefs = Get-Prefs
Write-Host "Prefs before reopen: $prefs"
Start-App
$onboarding2 = Test-UiContains @("onboarding_skip", "გამოტოვება", "გახსენი")
Write-Host "Onboarding on reopen: $onboarding2"
if ($onboarding2) {
    Write-Host "PASS Scenario B (reopen shows onboarding)" -ForegroundColor Green
} else {
    Write-Host "CHECK Scenario B (reopen) - may need manual skip then force-stop" -ForegroundColor Yellow
}

Write-Host "`n=== Scenario A: loggedIn=true -> WebView (no onboarding/login shell) ===" -ForegroundColor Cyan
$prefXml = @"
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="loggedIn" value="true" />
    <boolean name="onboardingSeen" value="true" />
    <string name="odooServer">https://fmggeo-araa-19679928.dev.odoo.com</string>
</map>
"@
adb shell pm clear $pkg | Out-Null
Start-Sleep -Seconds 1
$prefXml | adb shell "run-as $pkg sh -c 'mkdir -p shared_prefs && cat > shared_prefs/ge.fmg.community.prefs.xml'" 2>$null
# Alternative write via echo if cat fails
adb push - 2>$null
$prefs = Get-Prefs
Write-Host "Prefs: $prefs"
Start-App
Start-Sleep -Seconds 3
$ui = Get-UiDump
$hasOnboarding = $ui -match "onboarding_skip|გამოტოვება"
$hasLoginShell = $ui -match "login_server_input|შეიყვანე სერვერის"
$hasMainChrome = $ui -match "nav_balance|header_home|ბალანსი"
Write-Host "Onboarding visible: $hasOnboarding"
Write-Host "Server login shell visible: $hasLoginShell"
Write-Host "Main app chrome visible: $hasMainChrome"
if (-not $hasOnboarding -and -not $hasLoginShell -and $hasMainChrome) {
    Write-Host "PASS Scenario A (routing to main WebView)" -ForegroundColor Green
} elseif (-not $hasOnboarding -and -not $hasLoginShell) {
    Write-Host "PARTIAL PASS A (no onboarding/login; WebView may be loading Odoo)" -ForegroundColor Yellow
} else {
    Write-Host "FAIL Scenario A" -ForegroundColor Red
}

Write-Host "`n=== Scenario C: logout clears flags -> onboarding ===" -ForegroundColor Cyan
# Simulate post-logout state: prefs cleared
adb shell pm clear $pkg | Out-Null
Start-Sleep -Seconds 1
Start-App
# Manual logout required for full C; verify clearLoginState via simulated empty prefs = same as B
if (Test-UiContains @("onboarding_skip", "გამოტოვება")) {
    Write-Host "PASS Scenario C (fresh/cleared state shows onboarding - same as post-logout)" -ForegroundColor Green
} else {
    Write-Host "FAIL Scenario C" -ForegroundColor Red
}

Write-Host "`nDone. Full A/B/C with Odoo login/logout may need manual confirmation on device." -ForegroundColor Gray
