import * as Path from 'path'
import * as Os from 'os'

import { mkdir, writeFile } from 'fs/promises'
import { spawn, getPathSegments, setPathSegments } from '../lib/process/win32'
import { pathExists } from '../ui/lib/path-exists'

// Simple log for Squirrel events (no heavy logging)
const log = {
  error: (msg: string, err?: any) => console.error(`[SQUIRREL-ERROR] ${msg}`, err || ''),
  info: (msg: string) => console.log(`[SQUIRREL-INFO] ${msg}`)
}

// Timing helper function
function logTiming(message: string) {
  const startTime = (global as any).squirrelStartTime || Date.now()
  console.log(`[SQUIRREL-TIMING] ${Date.now() - startTime}ms: ${message}`)
}

const appFolder = Path.resolve(process.execPath, '..')
const rootAppDir = Path.resolve(appFolder, '..')
const updateDotExe = Path.resolve(Path.join(rootAppDir, 'Update.exe'))
const exeName = Path.basename(process.execPath)

/**
 * Handle Squirrel.Windows app lifecycle events.
 *
 * Returns a promise which will resolve when the work is done.
 */
export function handleSquirrelEvent(eventName: string): Promise<void> | null {
  logTiming(`handleSquirrelEvent called with: ${eventName}`)
  
  switch (eventName) {
    case '--squirrel-install':
      logTiming('Starting handleInstalled()')
      return handleInstalled()

    case '--squirrel-updated':
      logTiming('Starting handleUpdated()')
      return handleUpdated()

    case '--squirrel-uninstall':
      logTiming('Starting handleUninstall()')
      return handleUninstall()

    case '--squirrel-obsolete':
      logTiming('Handling --squirrel-obsolete')
      return Promise.resolve()
  }

  logTiming(`Unknown Squirrel event: ${eventName}`)
  return null
}

async function handleInstalled(): Promise<void> {
  logTiming('handleInstalled: Creating shortcuts')
  await createShortcut(['StartMenu', 'Desktop'])
  logTiming('handleInstalled: Installing CLI')
  await installWindowsCLI()
  logTiming('handleInstalled: Completed')
}

async function handleUpdated(): Promise<void> {
  logTiming('handleUpdated: Updating shortcuts')
  await updateShortcut()
  logTiming('handleUpdated: Installing CLI')
  await installWindowsCLI()
  logTiming('handleUpdated: Completed')
}

async function handleUninstall(): Promise<void> {
  logTiming('handleUninstall: Removing shortcuts')
  await removeShortcut()
  logTiming('handleUninstall: Uninstalling CLI')
  const result = uninstallWindowsCLI()
  logTiming('handleUninstall: Completed')
  return result
}

export async function installWindowsCLI(): Promise<void> {
  logTiming('installWindowsCLI: Starting')
  const binPath = getBinPath()
  logTiming('installWindowsCLI: Creating bin directory')
  await mkdir(binPath, { recursive: true })
  logTiming('installWindowsCLI: Writing batch trampoline')
  await writeBatchScriptCLITrampoline(binPath)
  logTiming('installWindowsCLI: Writing shell trampoline')
  await writeShellScriptCLITrampoline(binPath)
  try {
    logTiming('installWindowsCLI: Reading PATH segments')
    const paths = getPathSegments()
    if (paths.indexOf(binPath) < 0) {
      logTiming('installWindowsCLI: Adding to PATH')
      await setPathSegments([...paths, binPath])
    } else {
      logTiming('installWindowsCLI: PATH already contains bin path')
    }
    logTiming('installWindowsCLI: PATH update completed')
  } catch (e) {
    const errorMessage = e instanceof Error ? e.message : String(e)
    logTiming(`installWindowsCLI: ERROR updating PATH: ${errorMessage}`)
    log.error('Failed inserting bin path into PATH environment variable', e)
  }
  logTiming('installWindowsCLI: Completed')
}

export async function uninstallWindowsCLI() {
  try {
    const paths = getPathSegments()
    const binPath = getBinPath()
    const pathsWithoutBinPath = paths.filter(p => p !== binPath)
    return setPathSegments(pathsWithoutBinPath)
  } catch (e) {
    log.error('Failed removing bin path from PATH environment variable', e)
  }
}

/**
 * Get the path for the `bin` directory which exists in our `AppData` but
 * outside path which includes the installed app version.
 */
function getBinPath(): string {
  return Path.resolve(process.execPath, '../../bin')
}

function resolveVersionedPath(binPath: string, relativePath: string): string {
  const appFolder = Path.resolve(process.execPath, '..')
  return Path.relative(binPath, Path.join(appFolder, relativePath))
}

/**
 * Here's the problem: our app's path contains its version number. So each time
 * we update, the path to our app changes. So it's Real Hard to add our path
 * directly to `Path`. We'd have to detect and remove stale entries, etc.
 *
 * So instead, we write a trampoline out to a fixed path, still inside our
 * `AppData` directory but outside the version-specific path. That trampoline
 * just launches the current version's CLI tool. Then, whenever we update, we
 * rewrite the trampoline to point to the new, version-specific path. Bingo
 * bango Bob's your uncle.
 */
function writeBatchScriptCLITrampoline(binPath: string): Promise<void> {
  const versionedPath = resolveVersionedPath(
    binPath,
    'resources/app/static/rao.bat'
  )

  const trampoline = `@echo off\n"%~dp0\\${versionedPath}" %*`
  const trampolinePath = Path.join(binPath, 'rao.bat')

  return writeFile(trampolinePath, trampoline)
}

function writeShellScriptCLITrampoline(binPath: string): Promise<void> {
  // The path we get from `resolveVersionedPath` is a Win32 relative
  // path (something like `..\app-2.5.0\resources\app\static\rao.sh`).
  // We need to make sure it's a POSIX path in order for WSL to be able
  // to resolve it. See https://github.com/desktop/desktop/issues/4998
  const versionedPath = resolveVersionedPath(
    binPath,
    'resources/app/static/rao.sh'
  ).replace(/\\/g, '/')

  const trampoline = `#!/usr/bin/env bash
  DIR="$( cd "$( dirname "\$\{BASH_SOURCE[0]\}" )" && pwd )"
  sh "$DIR/${versionedPath}" "$@"`
  const trampolinePath = Path.join(binPath, 'rao')

  return writeFile(trampolinePath, trampoline, { encoding: 'utf8', mode: 755 })
}

/** Spawn the Squirrel.Windows `Update.exe` with a command. */
async function spawnSquirrelUpdate(
  commands: ReadonlyArray<string>
): Promise<void> {
  logTiming(`spawnSquirrelUpdate: Running Update.exe with: ${commands.join(' ')}`)
  await spawn(updateDotExe, commands)
  logTiming('spawnSquirrelUpdate: Update.exe completed')
}

type ShortcutLocations = ReadonlyArray<'StartMenu' | 'Desktop'>

function createShortcut(locations: ShortcutLocations): Promise<void> {
  logTiming(`createShortcut: Creating shortcuts for ${locations.join(', ')}`)
  return spawnSquirrelUpdate([
    '--createShortcut',
    exeName,
    '-l',
    locations.join(','),
  ]).then(() => {
    logTiming('createShortcut: Completed')
  })
}

function removeShortcut(): Promise<void> {
  logTiming('removeShortcut: Removing shortcuts')
  return spawnSquirrelUpdate(['--removeShortcut', exeName]).then(() => {
    logTiming('removeShortcut: Completed')
  })
}

async function updateShortcut(): Promise<void> {
  const homeDirectory = Os.homedir()
  if (homeDirectory) {
    const desktopShortcutPath = Path.join(
      homeDirectory,
      'Desktop',
      'Rao.lnk'
    )
    const exists = await pathExists(desktopShortcutPath)
    const locations: ShortcutLocations = exists
      ? ['StartMenu', 'Desktop']
      : ['StartMenu']
    return createShortcut(locations)
  } else {
    return createShortcut(['StartMenu', 'Desktop'])
  }
}