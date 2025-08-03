/*
 * build-info.ts.in
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

export interface BuildInfo {
  RSTUDIO_VERSION: string;
  RSTUDIO_BUILD_DATE: string;
  RSTUDIO_COPYRIGHT_YEAR: string;
  RSTUDIO_VERSION_PATCH: number;
  RSTUDIO_R_MAJOR_VERSION_REQUIRED: number;
  RSTUDIO_R_MINOR_VERSION_REQUIRED: number;
  RSTUDIO_R_PATCH_VERSION_REQUIRED: number;
  RSTUDIO_PACKAGE_OS: string;
  RSTUDIO_GIT_COMMIT: string;
  RSTUDIO_RELEASE_NAME: string;
}

// -----------------------------------------------------------------------------
// This file gets updated when doing a full build of Electron via make-package.
// Do not commit the updated file (won't break anything, but will make developer
// builds have the same version info as that make-package build).
// -----------------------------------------------------------------------------
export function buildInfo(): BuildInfo {
  return {
    RSTUDIO_VERSION: '0.3.0',
    RSTUDIO_BUILD_DATE: '2025-08-03',
    RSTUDIO_COPYRIGHT_YEAR: '2025',
    RSTUDIO_VERSION_PATCH: 0,
    RSTUDIO_R_MAJOR_VERSION_REQUIRED: 4,
    RSTUDIO_R_MINOR_VERSION_REQUIRED: 0,
    RSTUDIO_R_PATCH_VERSION_REQUIRED: 0,
    RSTUDIO_PACKAGE_OS: 'Windows',
    RSTUDIO_GIT_COMMIT: 'ecd87352c9ca4dca424214a9603859b5117c20d2',
    RSTUDIO_RELEASE_NAME: 'Cucumberleaf Sunflower',
  };
}
