"use strict";
/*---------------------------------------------------------------------------------------------
 * Copyright (c) Lotas Inc. All rights reserved.
 * Licensed under the MIT License.
 *--------------------------------------------------------------------------------------------*/
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.httpRequest = httpRequest;
const https = __importStar(require("https"));
const http = __importStar(require("http"));
class HttpResponseImpl {
    constructor(status, statusText, stream) {
        this.status = status;
        this.statusText = statusText;
        this.stream = stream;
        this.ok = this.status >= 200 && this.status < 300;
    }
    async body() {
        return this.stream;
    }
}
// Try electron.net.fetch first, fallback to Node.js https
let electronFetch;
try {
    electronFetch = require('electron').net.fetch;
}
catch {
    electronFetch = null;
}
async function httpRequest(url, options = {}) {
    // Try electron.net.fetch first if available (bypasses CORS)
    if (electronFetch) {
        try {
            const response = await electronFetch(url, {
                method: options.method || 'GET',
                headers: options.headers,
                body: options.body,
                signal: options.signal
            });
            return {
                ok: response.ok,
                status: response.status,
                statusText: response.statusText,
                body: async () => {
                    // Convert Web ReadableStream to Node.js stream for consistent interface
                    const webStream = response.body;
                    if (!webStream) {
                        throw new Error('Response body is null');
                    }
                    // Use Node.js built-in method to convert Web ReadableStream to Node.js Readable
                    const { Readable } = require('stream');
                    return Readable.fromWeb(webStream);
                }
            };
        }
        catch (error) {
            // Fall back to Node.js if electron fetch fails
        }
    }
    // Node.js https fallback
    return new Promise((resolve, reject) => {
        const urlObj = new URL(url);
        const module = urlObj.protocol === 'https:' ? https : http;
        const req = module.request(url, {
            method: options.method || 'GET',
            headers: options.headers
        }, (res) => {
            resolve(new HttpResponseImpl(res.statusCode || 0, res.statusMessage || '', res));
        });
        req.setTimeout(60 * 1000);
        req.on('error', reject);
        if (options.signal) {
            options.signal.addEventListener('abort', () => {
                req.destroy();
                reject(new Error('Request aborted'));
            });
        }
        if (options.body) {
            req.write(options.body);
        }
        req.end();
    });
}
//# sourceMappingURL=httpClient.js.map