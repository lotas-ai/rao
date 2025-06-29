/*
 * r-command-evaluator.ts
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

import { logger } from '../core/logger';
import { jsLiteralEscape } from '../core/string-utils';
import { MainWindow } from './main-window';

export class RCommandEvaluator {
  static window: MainWindow | null;

  static setMainWindow(window: MainWindow | null): void {
    RCommandEvaluator.window = window;
  }

  static evaluate(rCmd: string): void {
    if (RCommandEvaluator.window === null) {
      return;
    }

    rCmd = jsLiteralEscape(rCmd);
    this.window?.executeJavaScript(`window.desktopHooks.evaluateRCmd("${rCmd}")`).catch((error) => {
      logger().logError(error);
    });
  }
}
