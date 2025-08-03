import { spawn as spawnInternal, execSync } from 'child_process'

/** Get the path segments in the user's `Path` using Windows reg.exe. */
export function getPathSegments(): ReadonlyArray<string> {
  const startTime = (global as any).squirrelStartTime || Date.now()
  console.log(`[SQUIRREL-TIMING] ${Date.now() - startTime}ms: getPathSegments: Starting reg query`)
  
  try {
    const stdout = execSync('reg query HKCU\\Environment /v Path', { 
      encoding: 'utf8' 
    })
    console.log(`[SQUIRREL-TIMING] ${Date.now() - startTime}ms: getPathSegments: reg query completed`)
    
    // Parse reg query output to extract PATH value
    const lines = stdout.split('\n')
    for (const line of lines) {
      if (line.trim().startsWith('Path') && line.includes('REG_')) {
        const parts = line.split(/\s{2,}/) // Split on multiple spaces
        if (parts.length >= 3) {
          const pathValue = parts[2].trim()
          const segments = pathValue.split(';').filter((x: string) => x.length > 0)
          console.log(`[SQUIRREL-TIMING] ${Date.now() - startTime}ms: getPathSegments: Found ${segments.length} PATH segments`)
          return segments
        }
      }
    }
    console.log(`[SQUIRREL-TIMING] ${Date.now() - startTime}ms: getPathSegments: No PATH found, returning empty array`)
    return []
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error)
    console.log(`[SQUIRREL-TIMING] ${Date.now() - startTime}ms: getPathSegments: ERROR: ${errorMessage}`)
    throw new Error('Could not find PATH environment variable')
  }
}

/** Set the user's `Path` using Windows reg.exe. */
export async function setPathSegments(
  paths: ReadonlyArray<string>
): Promise<void> {
  const startTime = (global as any).squirrelStartTime || Date.now()
  const pathValue = paths.join(';')
  console.log(`[SQUIRREL-TIMING] ${Date.now() - startTime}ms: setPathSegments: Starting reg add for ${paths.length} paths`)
  
  try {
    await spawn('reg', [
      'add',
      'HKCU\\Environment',
      '/v',
      'Path',
      '/t',
      'REG_EXPAND_SZ',
      '/d',
      pathValue,
      '/f'
    ])
    console.log(`[SQUIRREL-TIMING] ${Date.now() - startTime}ms: setPathSegments: PATH update completed`)
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error)
    console.log(`[SQUIRREL-TIMING] ${Date.now() - startTime}ms: setPathSegments: ERROR: ${errorMessage}`)
    throw new Error(`Could not update PATH environment variable: ${error}`)
  }
}

/** Spawn a command with arguments and capture its output. */
export function spawn(
  command: string,
  args: ReadonlyArray<string>
): Promise<string> {
  try {
    const child = spawnInternal(command, args as string[])
    return new Promise<string>((resolve, reject) => {
      let stdout = ''

      // If Node.js encounters a synchronous runtime error while spawning
      // `stdout` will be undefined and the error will be emitted asynchronously
      if (child.stdout) {
        child.stdout.on('data', (data: Buffer) => {
          stdout += data.toString()
        })
      }

      child.on('close', (code: number | null) => {
        if (code === 0) {
          resolve(stdout)
        } else {
          reject(new Error(`Command "${command} ${args}" failed: "${stdout}"`))
        }
      })

      child.on('error', (err: Error) => {
        reject(err)
      })

      if (child.stdin) {
        // This is necessary if using Powershell 2 on Windows 7 to get the events
        // to raise.
        // See http://stackoverflow.com/questions/9155289/calling-powershell-from-nodejs
        child.stdin.end()
      }
    })
  } catch (error) {
    return Promise.reject(error)
  }
}