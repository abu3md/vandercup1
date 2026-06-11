$ErrorActionPreference = 'Stop'

# Define local folder names
$JdkFolder = 'jdk-21.0.3+9'
$MvnFolder = 'apache-maven-3.9.6'

# 1. Download & Extract JDK 21 if not present
if (-not (Test-Path $JdkFolder)) {
    Write-Host '📥 Downloading portable OpenJDK 21 (Temurin)...' -ForegroundColor Cyan
    $jdkUrl = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.3%2B9/OpenJDK21U-jdk_x64_windows_hotspot_21.0.3_9.zip'
    Invoke-WebRequest -Uri $jdkUrl -OutFile 'jdk21.zip'
    
    Write-Host '📦 Extracting OpenJDK 21...' -ForegroundColor Cyan
    Expand-Archive -Path 'jdk21.zip' -DestinationPath '.'
    Remove-Item 'jdk21.zip'
}

# 2. Download & Extract Maven 3.9.6 if not present
if (-not (Test-Path $MvnFolder)) {
    Write-Host '📥 Downloading portable Apache Maven 3.9.6...' -ForegroundColor Cyan
    $mvnUrl = 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip'
    Invoke-WebRequest -Uri $mvnUrl -OutFile 'maven.zip'
    
    Write-Host '📦 Extracting Apache Maven...' -ForegroundColor Cyan
    Expand-Archive -Path 'maven.zip' -DestinationPath '.'
    Remove-Item 'maven.zip'
}

# 3. Configure local environment variables for this compilation/execution session
$env:JAVA_HOME = Join-Path $pwd $JdkFolder
$MvnBin = Join-Path $pwd (Join-Path $MvnFolder 'bin')
$env:Path = $MvnBin + ';' + (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path

# 4. Compile the project into target/worldcupbot-1.0-SNAPSHOT.jar
Write-Host '🔨 Compiling the Discord Bot...' -ForegroundColor Green
$MvnCmd = Join-Path $MvnBin 'mvn.cmd'
& $MvnCmd clean package

# 5. Run the Bot under Java 21 without memory limits
Write-Host '🚀 Starting the Discord Bot...' -ForegroundColor Green
$JavaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
& $JavaExe -jar target/worldcupbot-1.0-SNAPSHOT.jar
