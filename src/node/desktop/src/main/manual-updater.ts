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
    
    logger().logInfo(`Update check: platform detected as ${platform}`);
    
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
    
    logger().logInfo(`Update check: fetching metadata from ${metadataUrl}`);
    
    // Fetch the metadata
    const metadata = await fetchJson(metadataUrl);
    
    logger().logDebug(`Update check: received metadata: ${JSON.stringify(metadata)}`);
    
    if (!metadata || !metadata.version) {
      logger().logError('Invalid metadata format');
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
    
    logger().logInfo(`Update check: found version ${updateInfo.version}`);
    
    return updateInfo;
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
    logger().logDebug(`Update check: starting HTTPS request to ${url}`);
    
    const request = https.get(url, (res) => {
      logger().logDebug(`Update check: received response with status ${res.statusCode}`);
      
      if (res.statusCode !== 200) {
        const error = new Error(`Failed to fetch ${url}: ${res.statusCode}`);
        logger().logError(`Update check: HTTP error: ${error.message}`);
        reject(error);
        return;
      }
      
      let data = '';
      res.on('data', (chunk) => {
        data += chunk;
        logger().logDebug(`Update check: received ${chunk.length} bytes, total: ${data.length}`);
      });
      res.on('end', () => {
        logger().logDebug(`Update check: response complete, parsing JSON data of length ${data.length}`);
        try {
          const parsed = JSON.parse(data);
          logger().logDebug(`Update check: successfully parsed JSON: ${JSON.stringify(parsed)}`);
          resolve(parsed);
        } catch (error) {
          logger().logError(`Update check: JSON parse error: ${error}`);
          reject(error);
        }
      });
    });
    
    request.on('error', (error) => {
      logger().logError(`Update check: HTTPS request error: ${error.message}`);
      reject(error);
    });
    
    request.setTimeout(10000, () => {
      logger().logError('Update check: request timeout after 10 seconds');
      request.destroy();
      reject(new Error('Request timeout'));
    });
  });
}

/**
 * Check if an update is available
 */
export async function checkForUpdates(showNoUpdateDialog = true): Promise<boolean> {
  logger().logInfo(`Update check: starting check (showNoUpdateDialog: ${showNoUpdateDialog})`);
  
  try {
    const currentVersion = app.getVersion();
    logger().logInfo(`Update check: current version is ${currentVersion}`);
    
    const updateInfo = await fetchLatestVersionInfo();
    
    if (!updateInfo) {
      logger().logError('Update check: failed to get update info');
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
    
    logger().logInfo(`Update check: comparing versions - current: ${currentVersion}, available: ${updateInfo.version}`);
    
    // Compare versions
    const hasUpdate = semver.gt(updateInfo.version, currentVersion);
    
    logger().logInfo(`Update check: version comparison result: hasUpdate = ${hasUpdate}`);
    
    if (hasUpdate) {
      logger().logInfo(`Update check: update available from ${currentVersion} to ${updateInfo.version}`);
      
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
      
      logger().logDebug(`Update check: user response: ${result.response === 0 ? 'Download' : 'Later'}`);
      
      if (result.response === 0) {
        logger().logInfo(`Update check: opening download URL: ${updateInfo.downloadUrl}`);
        // Open download URL in browser
        shell.openExternal(updateInfo.downloadUrl);
      }
      
      return true;
    } else {
      logger().logInfo('Update check: no update available');
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
  logger().logInfo('Update check: scheduling startup update check in 2 seconds');
  
  // Small delay to let app finish startup
  setTimeout(() => {
    logger().logInfo('Update check: starting silent startup update check');
    void checkForUpdates(false);
  }, 2000);
} 