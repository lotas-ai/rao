/*
 * url-utils.test.ts
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
import { 
  getAuthority,
  isAboutUrl,
  isAllowedProtocol,
  isChromeGpuUrl,
  isLocalUrl,
  isSafeHost,
  makeAbsoluteUrl
} from '../../../src/main/url-utils';
import { URL } from 'url';

describe('URL Utils', () => {

  it('allows valid protcol scheme', () => {
    let testUrl = new URL('data:,Hello%2C%20World%21');
    assert.isTrue(isAllowedProtocol(testUrl));

    testUrl = new URL('http://rstudio.com');
    assert.isTrue(isAllowedProtocol(testUrl));
  });

  it('disallows invalid protocol scheme', () => {
    let testUrl = new URL('gopher://rstudio.com:123/rstudio/ide');
    assert.isFalse(isAllowedProtocol(testUrl), testUrl.toString());

    testUrl = new URL('about:blank');
    assert.isFalse(isAllowedProtocol(testUrl), testUrl.toString());
  });

  it('allows local url', () => {
    let testUrl = new URL('http://localhost');
    assert.isTrue(isLocalUrl(testUrl));

    testUrl = new URL('http://127.0.0.1');
    assert.isTrue(isLocalUrl(testUrl));

    testUrl = new URL('http://127.0.0.1:123');
    assert.isTrue(isLocalUrl(testUrl));

    // long form of ::1
    testUrl = new URL('http://[0:0:0:0:0:0:0:1]');
    assert.isTrue(isLocalUrl(testUrl));

    testUrl = new URL('http://[::1]');
    assert.isTrue(isLocalUrl(testUrl));
  });

  it('allows about:blank', () => {
    assert.isTrue(isAboutUrl('about:blank'));
  });

  it('allows chrome://gpu', () => {
    assert.isTrue(isChromeGpuUrl('chrome://gpu/'));
    assert.isTrue(isChromeGpuUrl('chrome://gpu'));
  });

  it('isSafeHost detects safe host', () => {
    const host = 'somewhere.c9.ms';
    assert.isTrue(isSafeHost(host));
  });

  it('isSafeHost detects unsafe host', () => {
    const host = 'bad.place.foo';
    assert.isFalse(isSafeHost(host));
  });

  it('extracts authority from url', () => {
    const url = 'http://localhost:123/tutorial';

    assert.equal(getAuthority(url), 'http://localhost:123');
    assert.equal(getAuthority(`${url}?param_one=test`), 'http://localhost:123');
  });

  it('gets authority for localhost paths', () => {
    assert.equal(getAuthority('rmd_output/0'), 'http://127.0.0.1');
  });
  
  it('gets authority for external URLs', () => {
    assert.equal(getAuthority('http://www.rstudio.com/download'), 'http://www.rstudio.com');
  });

  it('handles invalid url', () => {
    assert.equal(getAuthority('http://bad^url'), '');
  });

  it('makes absolute urls from relative', () => {
    assert.equal(makeAbsoluteUrl('rmdOutput/3').toString(), 'http://127.0.0.1/rmdOutput/3');
  });

  it('makes absolute urls from valid url', () => {
    assert.equal(makeAbsoluteUrl('http://www.rstudio.com/download').toString(), 'http://www.rstudio.com/download');
  });
});
