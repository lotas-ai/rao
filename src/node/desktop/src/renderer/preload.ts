/*
 * preload.ts
 *
 * Copyright (C) 2022 by Posit Software, PBC
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
import { contextBridge } from 'electron';
import posthog from 'posthog-js';

import { removeDups } from '../core/string-utils';

import { getDesktopInfoBridge } from './desktop-info-bridge';
import { getMenuBridge } from './menu-bridge';
import { getDesktopBridge } from './desktop-bridge';
import { firstStartingWith } from '../core/array-utils';
import { getDesktopLoggerBridge, logString } from './logger-bridge';

/**
 * The preload script is run in the renderer before our GWT code and enables
 * setting up a bridge between the main process and the renderer process via
 * the contextBridge mechanism.
 *
 * Preload code has access to powerful node.js and Electron APIs even though
 * the renderer itself is configured with node disabled and context isolation.
 *
 * Be careful to only expose the exact APIs desired; DO NOT expose general-purpose
 * IPC objects, etc.
 *
 * Actual implementation happens in the main process, reached via ipcRenderer.
 */

contextBridge.exposeInMainWorld('desktopLogger', getDesktopLoggerBridge());

// Initialize PostHog
posthog.init('phc_b9DTWB8h678cfkt3DPgD6jYN57IIu0AzAD0tn20cSyo', {
  api_host: 'https://us.i.posthog.com',
  person_profiles: 'always', // or 'always' to create profiles for anonymous users as well
  capture_pageview: false, // Disable automatic pageview capture, we'll do it conditionally
  opt_out_capturing_by_default: true, // Start with tracking disabled, enable based on security mode
  // Recommended for Electron apps to help distinguish between environments
  loaded: (posthog) => {
    posthog.register({
      app_platform: 'electron'
    });
    
    // Get security mode from R session and configure PostHog accordingly
    initializePostHogFromRSettings(posthog);
  }
});

/**
 * Initialize PostHog based on security mode from R settings
 */
async function initializePostHogFromRSettings(posthog: any): Promise<void> {
  try {
    // Get security mode from R session via desktop bridge
    const desktopBridge = getDesktopBridge();
    
    desktopBridge.getSecurityMode((securityMode: string) => {
      if (securityMode === 'secure') {
        posthog.opt_out_capturing();
      } else {
        posthog.opt_in_capturing();
        posthog.capture('$pageview'); // Capture pageview if tracking enabled
      }
    });
    
  } catch (error) {
    console.error('Error initializing PostHog from R settings:', error);
    // Default to secure mode on any error
    posthog.opt_out_capturing();
  }
}

// Expose PostHog functions to the renderer process
contextBridge.exposeInMainWorld('analytics', {
  identify: (distinctId: string, userProperties?: Record<string, any>) => {
    posthog.identify(distinctId, userProperties);
  },
  capture: (eventName: string, properties?: Record<string, any>) => {
    posthog.capture(eventName, properties);
  }
});

const apiKeys = removeDups(firstStartingWith(process.argv, '--api-keys=').split('|'));
for (const apiKey of apiKeys) {
  switch (apiKey) {
    case 'desktop':
      logString('debug', '[preload] connecting desktop hooks');
      contextBridge.exposeInMainWorld(apiKey, getDesktopBridge());
      break;
    case 'desktopInfo':
      logString('debug', '[preload] connecting desktopInfo hooks');
      contextBridge.exposeInMainWorld(apiKey, getDesktopInfoBridge());
      break;
    case 'desktopMenuCallback':
      logString('debug', '[preload] connecting desktopMenuCallback hooks');
      contextBridge.exposeInMainWorld(apiKey, getMenuBridge());
      break;
    default:
      logString('debug', `[preload] ignoring unsupported apiKey: '${apiKey}'`);
  }
}
