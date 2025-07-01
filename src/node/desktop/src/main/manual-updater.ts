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
    
    if (platform === 'darwin') {
      metadataUrl = `${S3_BASE_URL}/latest-mac.json`;
    } else if (platform === 'win32') {
      metadataUrl = `${S3_BASE_URL}/latest-win.json`;
    } else if (platform === 'linux') {
      metadataUrl = `${S3_BASE_URL}/latest-linux.json`;
    } else {
      logger().logDebug('Unsupported platform for updates');
      return null;
    }
    
    // Fetch the metadata
    const metadata = await fetchJson(metadataUrl);
    
    if (!metadata || !metadata.version) {
      logger().logError('Invalid metadata format');
      return null;
    }
    
    // Determine file extension based on platform
    const fileExt = platform === 'darwin' ? '.dmg' : platform === 'win32' ? '.exe' : '.deb';
    const fileName = `Rao-v${metadata.version}${fileExt}`;
    
    // Use downloadUrl from metadata if available, otherwise use default URL
    const downloadUrl = metadata.downloadUrl || `${S3_BASE_URL}/${fileName}`;
    
    return {
      version: metadata.version,
      notes: metadata.notes || '',
      pubDate: metadata.pubDate || '',
      downloadUrl: downloadUrl
    };
  } catch (error) {
    logger().logError(`Error fetching update info: ${error}`);
    return null;
  }
}

/**
 * Fetch JSON from a URL
 */
function fetchJson(url: string): Promise<any> {
  return new Promise((resolve, reject) => {
    https.get(url, (res) => {
      if (res.statusCode !== 200) {
        reject(new Error(`Failed to fetch ${url}: ${res.statusCode}`));
        return;
      }
      
      let data = '';
      res.on('data', (chunk) => data += chunk);
      res.on('end', () => {
        try {
          resolve(JSON.parse(data));
        } catch (error) {
          reject(error);
        }
      });
    }).on('error', reject);
  });
}

/**
 * Check if an update is available
 */
export async function checkForUpdates(showNoUpdateDialog = true): Promise<boolean> {
  try {
    const currentVersion = app.getVersion();
    const updateInfo = await fetchLatestVersionInfo();
    
    if (!updateInfo) {
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
    
    // Compare versions
    const hasUpdate = semver.gt(updateInfo.version, currentVersion);
    
    if (hasUpdate) {
      // Format release notes for better display
      const formattedNotes = updateInfo.notes || 'No release notes available.';
      
      // Show update dialog with enhanced formatting
      const result = await dialog.showMessageBox({
        type: 'info',
        title: 'Update Available',
        message: `A new version (${updateInfo.version}) is available!`,
        detail: `${formattedNotes}\n\n─────────────────────────────\n\nWould you like to download it now?`,
        buttons: ['Download', 'Later'],
        defaultId: 0,
        cancelId: 1,
        noLink: true
      });
      
      if (result.response === 0) {
        // Open download URL in browser
        shell.openExternal(updateInfo.downloadUrl);
      }
      
      return true;
    } else if (showNoUpdateDialog) {
      await dialog.showMessageBox({
        type: 'info',
        title: 'No Update Available',
        message: 'You are using the latest version.',
        buttons: ['OK']
      });
    }
    
    return false;
  } catch (error) {
    logger().logError(`Error checking for updates: ${error}`);
    
    if (showNoUpdateDialog) {
      await dialog.showMessageBox({
        type: 'error',
        title: 'Update Check Failed',
        message: 'Failed to check for updates.',
        detail: `Error: ${error}`,
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
  // Small delay to let app finish startup
  setTimeout(() => {
    void checkForUpdates(false);
  }, 2000);
} 