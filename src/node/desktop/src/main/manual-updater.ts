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
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

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
    // Log specific error details instead of generic message
    const errorMessage = error instanceof Error ? error.message : String(error);
    const errorStack = error instanceof Error ? error.stack : undefined;
    
    logger().logError(`Error fetching update info: ${errorMessage}`);
    if (errorStack) {
      logger().logDebug(`Fetch update info error stack: ${errorStack}`);
    }
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
      
      logger().logInfo('DEBUG: About to show MANUAL update dialog with "Download" and "Later" buttons');
      logger().logInfo(`DEBUG: Dialog message will be: A new version (${updateInfo.version}) is available!`);
      
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
      
      logger().logInfo(`DEBUG: Manual update dialog result: ${result.response === 0 ? 'Download' : 'Later'}`);
      
      logger().logDebug(`Update check: user response: ${result.response === 0 ? 'Download' : 'Later'}`);
      
      if (result.response === 0) {
        logger().logInfo(`Update check: starting download from: ${updateInfo.downloadUrl}`);
        
        // Show download progress dialog
        const downloadResult = await dialog.showMessageBox({
          type: 'info',
          title: 'Downloading Update',
          message: `Downloading Rao ${updateInfo.version}...`,
          detail: 'Please wait while the update is downloaded.',
          buttons: ['OK'],
          defaultId: 0
        });
        
        // Download the file
        const downloadedPath = await downloadUpdateFile(updateInfo.downloadUrl, updateInfo.version);
        
        if (downloadedPath) {
          // Show completion dialog with option to open folder
          const completeResult = await dialog.showMessageBox({
            type: 'info',
            title: 'Download Complete',
            message: `Rao ${updateInfo.version} has been downloaded successfully!`,
            detail: `The installer has been saved to:\n${downloadedPath}\n\nWould you like to open the Downloads folder?`,
            buttons: ['Open Folder', 'Close'],
            defaultId: 0,
            cancelId: 1
          });
          
          if (completeResult.response === 0) {
            // Open Downloads folder
            shell.showItemInFolder(downloadedPath);
          }
        } else {
          // Download failed, fallback to opening browser
          await dialog.showMessageBox({
            type: 'error',
            title: 'Download Failed',
            message: 'Failed to download the update file.',
            detail: 'Opening download page in your browser instead.',
            buttons: ['OK']
          });
          shell.openExternal(updateInfo.downloadUrl);
        }
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
    // Log the raw error with more details
    const errorMessage = error instanceof Error ? error.message : String(error);
    const errorStack = error instanceof Error ? error.stack : undefined;
    
    logger().logError(`Error checking for updates: ${errorMessage}`);
    if (errorStack) {
      logger().logDebug(`Update check error stack: ${errorStack}`);
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
  logger().logInfo('=== DEBUG: checkForUpdatesManually() called - MANUAL UPDATE CHECK ===');
  logger().logInfo('DEBUG: This should show "Download" button, not "Restart" button');
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

/**
 * Download update file to user's Downloads folder
 */
async function downloadUpdateFile(downloadUrl: string, version: string): Promise<string | null> {
  try {
    const platform = process.platform;
    const fileExt = platform === 'darwin' ? '.dmg' : platform === 'win32' ? '.exe' : '.deb';
    const fileName = `Rao-v${version}${fileExt}`;
    const downloadsPath = path.join(os.homedir(), 'Downloads', fileName);
    
    logger().logInfo(`Downloading update file: ${downloadUrl} to ${downloadsPath}`);
    
    return new Promise((resolve, reject) => {
      const file = fs.createWriteStream(downloadsPath);
      
      https.get(downloadUrl, (response) => {
        if (response.statusCode !== 200) {
          reject(new Error(`Download failed: ${response.statusCode}`));
          return;
        }
        
        response.pipe(file);
        
        file.on('finish', () => {
          file.close();
          logger().logInfo(`Download completed: ${downloadsPath}`);
          resolve(downloadsPath);
        });
        
        file.on('error', (err) => {
          fs.unlink(downloadsPath, () => {}); // Delete the file on error
          reject(err);
        });
      }).on('error', (err) => {
        reject(err);
      });
    });
  } catch (error) {
    logger().logError(`Error downloading update file: ${error}`);
    return null;
  }
} 