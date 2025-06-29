/*
 * unit-utils.ts
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

import path from 'path';
import os from 'os';

import { createStubInstance, StubbableType, SinonStubbedInstance, SinonStubbedMember, SinonSandbox } from 'sinon';
import { getenv } from '../../src/core/environment';
import { FilePath } from '../../src/core/file-path';
import { randomString } from '../../src/main/utils';

/**
 * save and clear specific env vars
 */
export function saveAndClear(vars: Record<string, string>): void {
  for (const name in vars) {
    vars[name] = process.env[name] ?? '';
    delete process.env[name];
  }
}

/**
 * put back original env vars
 */
export function restore(vars: Record<string, string>): void {
  for (const name in vars) {
    if (vars[name]) {
      process.env[name] = vars[name];
      vars[name] = '';
    } else {
      delete process.env[name];
    }
  }
}

// From: https://github.com/sinonjs/sinon/issues/1963
// Sinon can't stub members marked with TypeScript "private" or "protected"
export type StubbedClass<T> = SinonStubbedInstance<T> & T;

export function createSinonStubInstance<T>(
  constructor: StubbableType<T>,
  overrides?: { [K in keyof T]?: SinonStubbedMember<T[K]> },
): StubbedClass<T> {
  const stub = createStubInstance<T>(constructor, overrides);
  return stub as unknown as StubbedClass<T>;
}

export function createSinonStubInstanceForSandbox<T>(
  sandbox: SinonSandbox,
  constructor: StubbableType<T>,
  overrides?: { [K in keyof T]?: SinonStubbedMember<T[K]> },
): StubbedClass<T> {
  const stub = sandbox.createStubInstance<T>(constructor, overrides);
  return stub as unknown as StubbedClass<T>;
}

/**
 * Creates a random directory name located inside the temp directory
 *
 * @param label A label, if any, to include inside the random name. Useful to
 * identify the origin of any leftover directories from a unit test that weren't
 * cleaned up properly
 *
 * @returns The FilePath to the randomly generated directory
 */
export function tempDirectory(label = ''): FilePath {
  const tempName = label
    ? path.join(os.tmpdir(), label + '-' + randomString())
    : path.join(os.tmpdir(), randomString());
  return new FilePath(tempName);
}

/**
 * @returns true if tests are running in Docker on Windows
 */
export function isWindowsDocker(): boolean {
  return !!getenv('RSTUDIO_DOCKER_WINDOWS');
}
