/*
 * secondary-window.test.ts
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

import { describe } from 'mocha';
import { assert } from 'chai';
import sinon from 'sinon';

import { isWindowsDocker } from '../unit-utils';
import { SecondaryWindow } from '../../../src/main/secondary-window';
import { clearApplicationSingleton, setApplication } from '../../../src/main/app-state';
import { Application } from '../../../src/main/application';

if (!isWindowsDocker()) {
  describe('SecondaryWindow', () => {
    beforeEach(() => {
      setApplication(new Application());
    });

    afterEach(() => {
      clearApplicationSingleton();
      sinon.restore();
    });

    it('construction creates a hidden BrowserWindow', () => {
      const win = new SecondaryWindow(false, 'some name');
      assert.isObject(win);
      assert.isObject(win.window);
      assert.isFalse(win.window.isVisible());
    });
  });
}