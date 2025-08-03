# PowerShell script to patch Squirrel.exe timeout from 15 seconds to 20 minutes
# This fixes the OperationCanceledException during rao app installation
# FAILS HARD if binaries are not found or cannot be patched
param(
    [string]$ElectronSourceDir = "."
)

$ErrorActionPreference = "Stop"

Write-Host "=== Patching Squirrel Timeout ==="

$squirrelPath = Join-Path $ElectronSourceDir "node_modules\electron-winstaller\vendor\Squirrel.exe"
$squirrelMonoPath = Join-Path $ElectronSourceDir "node_modules\electron-winstaller\vendor\Squirrel-Mono.exe"

function Patch-SquirrelBinary {
    param([string]$BinaryPath)
    
    # FAIL if binary not found - no fallbacks
    if (!(Test-Path $BinaryPath)) {
        Write-Host "ERROR: Required binary not found: $BinaryPath"
        Write-Host "This indicates npm dependencies were not installed correctly"
        exit 1
    }
    
    Write-Host "Found binary: $(Split-Path $BinaryPath -Leaf)"
    
    # Read the binary file
    $bytes = [System.IO.File]::ReadAllBytes($BinaryPath)
    Write-Host "Loaded $($bytes.Length) bytes"

    # Search for 15000 (0x3A98) in little-endian format: 0x98, 0x3A, 0x00, 0x00
    $targetBytes = @(0x98, 0x3A, 0x00, 0x00)  # 15000 in little-endian
    $replacementBytes = @(0x80, 0xD0, 0x12, 0x00)  # 1,200,000 (20 minutes) in little-endian

    $patchCount = 0
    for ($i = 0; $i -lt ($bytes.Length - 3); $i++) {
        if ($bytes[$i] -eq $targetBytes[0] -and 
            $bytes[$i+1] -eq $targetBytes[1] -and 
            $bytes[$i+2] -eq $targetBytes[2] -and 
            $bytes[$i+3] -eq $targetBytes[3]) {
            
            Write-Host "  Found 15000ms timeout at offset: $i"
            
            # Replace with 20 minutes (1,200,000 ms)
            $bytes[$i] = $replacementBytes[0]
            $bytes[$i+1] = $replacementBytes[1] 
            $bytes[$i+2] = $replacementBytes[2]
            $bytes[$i+3] = $replacementBytes[3]
            
            $patchCount++
        }
    }

    # Check if no timeouts found - this means already patched or wrong binary
    if ($patchCount -eq 0) {
        # Check if it has 20-minute timeouts (already patched)
        $replacementBytes = @(0x80, 0xD0, 0x12, 0x00)  # 1,200,000 (20 minutes) in little-endian
        $existingPatchCount = 0
        for ($i = 0; $i -lt ($bytes.Length - 3); $i++) {
            if ($bytes[$i] -eq $replacementBytes[0] -and 
                $bytes[$i+1] -eq $replacementBytes[1] -and 
                $bytes[$i+2] -eq $replacementBytes[2] -and 
                $bytes[$i+3] -eq $replacementBytes[3]) {
                $existingPatchCount++
            }
        }
        
        if ($existingPatchCount -gt 0) {
            Write-Host "  ALREADY PATCHED: Found $existingPatchCount instances of 20-minute timeout"
            return
        } else {
            Write-Host "ERROR: No 15000ms timeouts found and no 20-minute timeouts in $(Split-Path $BinaryPath -Leaf)"
            Write-Host "This binary may be corrupted or wrong version"
            exit 1
        }
    }

    # Write the patched file
    [System.IO.File]::WriteAllBytes($BinaryPath, $bytes)
    Write-Host "  SUCCESS: Patched $patchCount instances (15s -> 20min)"
}

# Patch both Squirrel executables - BOTH MUST SUCCEED
Patch-SquirrelBinary -BinaryPath $squirrelPath
Patch-SquirrelBinary -BinaryPath $squirrelMonoPath

# Also patch any Squirrel binaries in build output directories and installed locations
$outputSquirrelPaths = @(
    (Join-Path $ElectronSourceDir "out\Rao-win32-x64\Squirrel.exe"),
    (Join-Path $ElectronSourceDir "out\make\squirrel.windows\x64\Squirrel.exe"),
    (Join-Path $env:LOCALAPPDATA "rao\Update.exe"),
    (Join-Path $env:LOCALAPPDATA "rao\app-*\Update.exe")
)

foreach ($outputPath in $outputSquirrelPaths) {
    if (Test-Path $outputPath) {
        Write-Host "Found output Squirrel binary: $(Split-Path $outputPath -Leaf)"
        try {
            Patch-SquirrelBinary -BinaryPath $outputPath
        } catch {
            Write-Host "Warning: Could not patch output binary $outputPath - it may be in use"
        }
    }
}

Write-Host "=== Squirrel Timeout Patch Complete ==="
Write-Host "The 15-second timeout has been extended to 20 minutes"
Write-Host "This prevents OperationCanceledException during installation"

exit 0