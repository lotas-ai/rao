/*
 * error.ts
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
    const i18nIds = [
      'errorStartingR',
      'rLogo',
      'errorStartingR',
      'sessionPath',
      'rSessionFailedToStart',
      'rstudioVersion',
      'output',
      'nextSteps',
      'rCanFailToStartupForManyReasons',
      'investigateAnyErrorsAbove',
      'makeSureThatRStartsUpCorrectly',
      'fullyUninstallAllVersionsOfRFromYourMachine',
      'removeStartupCustomizationsSuchAs',
      'ifPostingThisReportOnlineUseTheCopyProblemReport',
      'furtherTroubleshootingHelpCanBeFound',
      'troubleshootingRstudioStartup',
      'copyProblemReport',
    ].map((id) => 'i18n-' + id);

    try {
      document.title = i18next.t('uiFolder.errorStartingR');

      i18nIds.forEach((id) => {
        const reducedId = id.replace('i18n-', '');
        const elements = document.querySelectorAll(`[id=${id}]`);

        elements.forEach((element) => {
          switch (reducedId) {
            case 'removeStartupCustomizationsSuchAs':
              element.innerHTML = i18next.t('uiFolder.' + reducedId, {
                rProfileFileExtension: '<samp>.Rprofile</samp>',
              });
              break;
            case 'rSessionExitedWithCode':
              element.innerHTML = i18next.t('uiFolder.' + reducedId, {
                exitCode: '<strong><span id="exit_code"></span></strong>',
              });
              break;
            case 'copyProblemReport':
              (document.getElementById(id) as HTMLInputElement).value = i18next.t('uiFolder.' + reducedId);
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

const replaceReportVar = async (report: string, varName: string): Promise<string> => {
  return new Promise((resolve, reject) => {
    try {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (window as any).desktop.getStartupErrorInfo('!' + varName, (result: any) => {
        resolve(report.replace(new RegExp('#' + varName + '#', 'g'), result));
      });
    } catch (err) {
      console.error(err);
      reject(err);
    }
  });
};

// Function to copy problem report to clipboard
const copyProblemReport = async () => {
  // Find the <textarea> containing the problem report
  const reportElement = document.getElementById('problem_report') as HTMLTextAreaElement;
  let report = reportElement.value;
  try {
    // Fill in the user agent
    report = report.replace('user_agent', navigator.userAgent);

    // Replace all variables
    report = await replaceReportVar(report, 'version');
    report = await replaceReportVar(report, 'launch_failed');
    report = await replaceReportVar(report, 'exit_code');
    report = await replaceReportVar(report, 'process_error');
    report = await replaceReportVar(report, 'process_output');
    report = await replaceReportVar(report, 'log_file');
    report = await replaceReportVar(report, 'log_content');

    reportElement.value = report;

    // Select all the text in the report
    reportElement.focus();
    reportElement.select();

    // Attempt to copy the text to the clipboard
    if (document.execCommand('copy')) {
      alert(i18next.t('uiFolder.problemReportCopiedToClipboard'));
    } else {
      alert(i18next.t('uiFolder.couldNotCopyProblemReportToClipboard'));
    }
  } catch (error) {
    console.log('Error on copy problem report: ', error);
  }
};

const addProblemReportEventListener = () => {
  window.addEventListener('load', () => {
    const copyButton = document.getElementById('i18n-copyProblemReport') as HTMLButtonElement;
    copyButton.addEventListener('click', () => {
      void copyProblemReport();
    });
  });
};

loadPageLocalization();
addProblemReportEventListener();
