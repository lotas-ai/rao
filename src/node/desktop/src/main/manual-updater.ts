/*
 * manual-updater.ts
 *
 * Copyright (C) 2024
 *
 * Unless you have received this program directly from Posit Software pursuant
 * to the terms of a commercial license agreement with Posit Software, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

/**
 * Manual update checker for platforms that don't support auto-updates.
 * Currently used for Linux only.
 * 
 * macOS: Uses auto-updater with update-electron-app (ZIP files)
 * Windows: Uses auto-updater with update-electron-app (Squirrel.Windows .nupkg files)
 * Linux: Uses this manual update system (manual download of .deb/.rpm/.AppImage files)
 */

import { app, dialog, shell } from 'electron';
import { logger } from '../core/logger';
import * as https from 'https';
import * as semver from 'semver';

// S3 bucket base URL
const S3_BASE_URL = 'https://lotas-downloads.s3.us-east-2.amazonaws.com';

// Platform-specific update info
interface UpdateInfo {
  version: string;
  notes: string;
  pubDate: string;
  downloadUrl: string;
}

/**
 * Fetch latest version info from the S3 bucket
 */
async function fetchLatestVersionInfo(): Promise<UpdateInfo | null> {
  try {
    // Determine platform-specific metadata URL
    const platform = process.platform;
    let metadataUrl: string;
    
    console.log(`Update check: platform detected as ${platform}`);
    
    if (platform === 'darwin') {
      metadataUrl = `${S3_BASE_URL}/latest-mac.json`;
    } else if (platform === 'win32') {
      metadataUrl = `${S3_BASE_URL}/latest-win.json`;
    } else if (platform === 'linux') {
      metadataUrl = `${S3_BASE_URL}/latest-linux.json`;
    } else {
      console.log('Unsupported platform for updates');
      return null;
    }
    
    console.log(`Update check: fetching metadata from ${metadataUrl}`);
    
    // Fetch the metadata
    const metadata = await fetchJson(metadataUrl);
    
    console.log(`Update check: received metadata: ${JSON.stringify(metadata)}`);
    
    if (!metadata || !metadata.version) {
      console.error('Invalid metadata format');
      return null;
    }
    
    // Determine file extension based on platform
    const fileExt = platform === 'darwin' ? '.dmg' : platform === 'win32' ? '.exe' : '.deb';
    const fileName = `Rao-v${metadata.version}${fileExt}`;
    
    // Use downloadUrl from metadata if available, otherwise use default URL
    const downloadUrl = metadata.downloadUrl || `${S3_BASE_URL}/${fileName}`;
    
    const updateInfo = {
      version: metadata.version,
      notes: metadata.notes || '',
      pubDate: metadata.pubDate || '',
      downloadUrl: downloadUrl
    };
    
    console.log(`Update check: found version ${updateInfo.version}`);
    
    return updateInfo;
  } catch (error) {
    // Log specific error details instead of generic message
    const errorMessage = error instanceof Error ? error.message : String(error);
    const errorStack = error instanceof Error ? error.stack : undefined;
    
    console.error(`Error fetching update info: ${errorMessage}`);
    if (errorStack) {
      console.log(`Fetch update info error stack: ${errorStack}`);
    }
    return null;
  }
}

/**
 * Fetch JSON from a URL
 */
function fetchJson(url: string): Promise<any> {
  return new Promise((resolve, reject) => {
    console.log(`Update check: starting HTTPS request to ${url}`);
    
    const request = https.get(url, (res) => {
      console.log(`Update check: received response with status ${res.statusCode}`);
      
      if (res.statusCode !== 200) {
        const error = new Error(`Failed to fetch ${url}: ${res.statusCode}`);
        console.error(`Update check: HTTP error: ${error.message}`);
        reject(error);
        return;
      }
      
      let data = '';
      res.on('data', (chunk) => {
        data += chunk;
        console.log(`Update check: received ${chunk.length} bytes, total: ${data.length}`);
      });
      res.on('end', () => {
        console.log(`Update check: response complete, parsing JSON data of length ${data.length}`);
        try {
          const parsed = JSON.parse(data);
          console.log(`Update check: successfully parsed JSON: ${JSON.stringify(parsed)}`);
          resolve(parsed);
        } catch (error) {
          console.error(`Update check: JSON parse error: ${error}`);
          reject(error);
        }
      });
    });
    
    request.on('error', (error) => {
      console.error(`Update check: HTTPS request error: ${error.message}`);
      reject(error);
    });
    
    request.setTimeout(10000, () => {
      console.error('Update check: request timeout after 10 seconds');
      request.destroy();
      reject(new Error('Request timeout'));
    });
  });
}

/**
 * Check if an update is available
 */
export async function checkForUpdates(showNoUpdateDialog = true): Promise<boolean> {
  console.log(`Update check: starting check (showNoUpdateDialog: ${showNoUpdateDialog})`);
  
  try {
    const currentVersion = app.getVersion();
    console.log(`Update check: current version is ${currentVersion}`);
    
    const updateInfo = await fetchLatestVersionInfo();
    
    if (!updateInfo) {
      console.error('Update check: failed to get update info');
      if (showNoUpdateDialog) {
        await dialog.showMessageBox({
          type: 'info',
          title: 'No Update Available',
          message: 'Could not check for updates. Please try again later.',
          buttons: ['OK']
        });
      }
      return false;
    }
    
    console.log(`Update check: comparing versions - current: ${currentVersion}, available: ${updateInfo.version}`);
    
    // Compare versions
    const hasUpdate = semver.gt(updateInfo.version, currentVersion);
    
    console.log(`Update check: version comparison result: hasUpdate = ${hasUpdate}`);
    
    if (hasUpdate) {
      console.log(`Update check: update available from ${currentVersion} to ${updateInfo.version}`);
      
      // Format release notes for better display
      const formattedNotes = updateInfo.notes || 'No release notes available.';
      
      // Show update dialog with enhanced formatting
      const result = await dialog.showMessageBox({
        type: 'info',
        title: 'Update Available',
        message: `A new version (${updateInfo.version}) is available!`,
        detail: `${formattedNotes}\n\nWould you like to download it now?`,
        buttons: ['Download', 'Later'],
        defaultId: 0,
        cancelId: 1,
        noLink: true
      });
      
      console.log(`Update check: user response: ${result.response === 0 ? 'Download' : 'Later'}`);
      
      if (result.response === 0) {
        console.log(`Update check: opening download URL: ${updateInfo.downloadUrl}`);
        // Open download URL in browser
        shell.openExternal(updateInfo.downloadUrl);
      }
      
      return true;
    } else {
      console.log('Update check: no update available');
      if (showNoUpdateDialog) {
        await dialog.showMessageBox({
          type: 'info',
          title: 'No Update Available',
          message: 'You are using the latest version.',
          buttons: ['OK']
        });
      }
    }
    
    return false;
  } catch (error) {
    // Log the raw error with more details
    const errorMessage = error instanceof Error ? error.message : String(error);
    const errorStack = error instanceof Error ? error.stack : undefined;
    
    console.error(`Error checking for updates: ${errorMessage}`);
    if (errorStack) {
      console.log(`Update check error stack: ${errorStack}`);
    }
    
    if (showNoUpdateDialog) {
      await dialog.showMessageBox({
        type: 'error',
        title: 'Update Check Failed',
        message: 'Failed to check for updates.',
        detail: `Error: ${errorMessage}`,
        buttons: ['OK']
      });
    }
    
    return false;
  }
}

/**
 * Check for updates with user feedback (can be called from menu item)
 */
export function checkForUpdatesManually(): Promise<boolean> {
  return checkForUpdates(true);
}

/**
 * Silent check for updates on startup (no dialogs if no update available)
 */
export function checkForUpdatesOnStartup(): void {
  console.log('Update check: scheduling startup update check in 2 seconds');
  
  // Small delay to let app finish startup
  setTimeout(() => {
    console.log('Update check: starting silent startup update check');
    void checkForUpdates(false);
  }, 2000);
} 