$env:JAVA_HOME = (Get-ChildItem "C:\Program Files\Microsoft\jdk-17*" -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = "C:\Android\sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
