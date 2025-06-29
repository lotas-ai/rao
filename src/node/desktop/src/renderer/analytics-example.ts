/*
 * analytics-example.ts
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

// Example of identifying a user
export function identifyUser(userId: string, userEmail?: string): void {
  window.analytics.identify(userId, {
    email: userEmail,
    appVersion: process.env.APP_VERSION
  });
}

// Example of capturing an event
export function trackEvent(eventName: string, properties?: Record<string, any>): void {
  window.analytics.capture(eventName, properties);
}

// Usage examples:
// 
// When a user logs in:
// identifyUser('user-123', 'user@example.com');
//
// When tracking actions:
// trackEvent('project_opened', { project_name: 'My R Project', project_type: 'r-project' });
// trackEvent('package_installed', { package_name: 'dplyr', package_version: '1.0.10' });
// trackEvent('document_created', { document_type: 'r-markdown' }); 