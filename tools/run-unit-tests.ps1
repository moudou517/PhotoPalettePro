<#
	跑单元测试。

	为什么需要这个脚本：Gradle 的测试 worker 在本机的沙箱环境下起不来
	（命名管道被限制，报 GradleWorkerMain ClassNotFoundException / 管道正在被关闭），
	所以这里绕开 Gradle，直接用 java.exe 调 JUnitCore。

	依赖三样东西，都由 Gradle 先生成：
	  1. build/test-cp.txt              单元测试的依赖 classpath
	  2. build/aar-classpath.txt        各 AAR 解出来的 classes.jar
	  3. apk-for-local-test.ap_         Robolectric 需要的资源包

	用法：
	  pwsh tools/run-unit-tests.ps1                     # 跑全部
	  pwsh tools/run-unit-tests.ps1 UiLayoutScreenshotTest
	  pwsh tools/run-unit-tests.ps1 -Prepare            # 只重新生成上面三样
#>
param(
	[string[]]$Tests = @(),
	[switch]$Prepare
)

$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "D:\Java21"
$java = "D:\Java21\bin\java.exe"

$root = Split-Path -Parent $PSScriptRoot          # 仓库根目录
$app = Join-Path $root "app"
$intermediates = Join-Path $app "build\intermediates"

if (-not (Test-Path $java)) { throw "找不到 java：$java" }

# ---- 1. 让 Gradle 把测试所需的产物准备好 ----
if ($Prepare -or -not (Test-Path (Join-Path $root "build\test-cp.txt"))) {
	& (Join-Path $root "gradlew.bat") -p $root `
			:app:compileDebugJavaWithJavac `
			:app:compileDebugUnitTestJavaWithJavac `
			:app:bundleDebugClassesToRuntimeJar `
			:app:generateDebugUnitTestConfig `
			:app:packageDebugUnitTestForUnitTest `
			--console=plain
	if ($LASTEXITCODE -ne 0) { throw "Gradle 准备阶段失败" }
}

# ---- 2. 拼 classpath ----
$cp = Get-Content (Join-Path $root "build\test-cp.txt") -Raw
$aarJars = (Get-Content (Join-Path $root "build\aar-classpath.txt")) -join ';'

$parts = @(
	(Join-Path $intermediates "javac\debugUnitTest\compileDebugUnitTestJavaWithJavac\classes")
	(Join-Path $intermediates "unit_test_config_directory\debugUnitTest\generateDebugUnitTestConfig\out")
	"E:\Data\AndroidSDK\platforms\android-35\android.jar"
	$aarJars
	$cp
)
$classpath = $parts -join ';'

# ---- 3. 跑 ----
if ($Tests.Count -eq 0) {
	# 自动扫描，不维护硬编码列表：
	# 之前那份列表漏掉了新加的测试类，结果「跑全量」跑的是旧集合，
	# 新测试静默地一次都没执行过。这种事不该靠人记得改。
	$testRoot = Join-Path $app "src\test\java"
	$Tests = Get-ChildItem -Path $testRoot -Recurse -Filter "*Test.java" |
		Sort-Object FullName |
		ForEach-Object {
			$_.FullName.Substring($testRoot.Length + 1) -replace '\\', '.' -replace '\.java$', ''
		}
	if ($Tests.Count -eq 0) { throw "在 $testRoot 下没找到任何 *Test.java" }
	Write-Host "发现 $($Tests.Count) 个测试类："
	$Tests | ForEach-Object { Write-Host "  $_" }
} else {
	$Tests = $Tests | ForEach-Object {
		if ($_ -like "*.*") { $_ } else { "com.example.photopalettepro.$_" }
	}
}

Push-Location $app
try {
	& $java -cp $classpath org.junit.runner.JUnitCore @Tests
} finally {
	Pop-Location
}
