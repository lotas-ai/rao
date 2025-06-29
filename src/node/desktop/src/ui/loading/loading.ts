/*
 * loading.ts
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

import { changeLanguage, initI18n } from '../../main/i18n-manager';
import i18next from 'i18next';
import { checkForNewLanguage } from '../utils';

const loadPageLocalization = () => {
  initI18n();

  const updateLabels = () => {
    const i18nIds = ['initializingR', 'rLogo', 'initializingR', 'theRsessionIsInitializing'].map((id) => 'i18n-' + id);

    try {
      document.title = i18next.t('uiFolder.initializingR');

      i18nIds.forEach((id) => {
        const reducedId = id.replace('i18n-', '');
        const elements = document.querySelectorAll(`[id=${id}]`);

        elements.forEach((element) => {
          switch (reducedId) {
            case 'theRsessionIsInitializing':
              element.innerHTML = i18next.t('uiFolder.' + reducedId, { mdash: '&mdash;' });
              break;
            default:
              element.innerHTML = i18next.t('uiFolder.' + reducedId);
              break;
          }
        });
      });
    } catch (err) {
      console.log('Error occurred when loading i18n: ', err);
    }
  };

  window.addEventListener('load', () => {
    checkForNewLanguage()
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      .then(async (newLanguage: any) =>
        changeLanguage('' + newLanguage).then(() => {
          updateLabels();
        }),
      )
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      .catch((err: any) => {
        console.error('An error happened when trying to fetch a new locale: ', err);
      });
  });
};

loadPageLocalization();
