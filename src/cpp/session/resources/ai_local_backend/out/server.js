"use strict";
/*---------------------------------------------------------------------------------------------
 * Copyright (c) Lotas Inc. All rights reserved.
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
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const express_1 = __importDefault(require("express"));
let proxyServer;
async function startProxyServer() {
    const app = (0, express_1.default)();
    app.use((req, res, next) => {
        res.header('Access-Control-Allow-Origin', '*');
        res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
        res.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept, Authorization, Cache-Control');
        if (req.method === 'OPTIONS') {
            res.sendStatus(200);
        }
        else {
            next();
        }
    });
    app.use(express_1.default.json({ limit: '100mb' }));
    app.post('/ai/query', async (req, res) => {
        res.writeHead(200, {
            'Content-Type': 'text/event-stream',
            'Cache-Control': 'no-cache',
            'Connection': 'keep-alive',
            'Access-Control-Allow-Origin': '*',
        });
        const { LocalBackendService } = await Promise.resolve().then(() => __importStar(require('./services/localBackendService.js')));
        const { FunctionDefinitionService } = await Promise.resolve().then(() => __importStar(require('./services/functionDefinitionService.js')));
        const functionDefinitionService = new FunctionDefinitionService();
        const service = new LocalBackendService(functionDefinitionService);
        const { conversation, provider, model, temperature, request_id, request_type, byok_keys, ...contextData } = req.body;
        // Extract web search enabled from header
        const webSearchEnabledHeader = req.headers['x-rao-web-search-enabled'];
        const webSearchEnabled = webSearchEnabledHeader?.toLowerCase() === 'true';
        if (request_type === 'generate_conversation_name') {
            const result = await service.generateConversationName(req.body);
            const sseData = JSON.stringify({
                conversation_name: result?.conversationName || "Untitled Conversation",
                isComplete: true,
                complete: true,
                error: result?.error
            });
            res.write(`data: ${sseData}\n\n`);
            res.end();
        }
        else if (request_type === 'summarize_conversation') {
            const outputStream = {
                write: (data) => res.write(data)
            };
            await service.processSummarizationRequest(req.body, request_id, outputStream);
            res.end();
        }
        else {
            const contextWithByok = { ...contextData, byok_keys };
            await service.processStreamingQuery(conversation || [], provider, model, temperature || 0.7, request_id || `req_${Date.now()}`, contextWithByok, (data) => res.write(`data: ${JSON.stringify(data)}\n\n`), (error) => {
                console.error('Proxy server streaming error:', error);
                res.write(`data: ${JSON.stringify({ error: error.message })}\n\n`);
                res.end();
            }, () => res.end(), webSearchEnabled);
        }
    });
    return new Promise((resolve, reject) => {
        proxyServer = app.listen(0, 'localhost', () => {
            const address = proxyServer?.address();
            if (address && typeof address === 'object') {
                console.log(`PROXY_URL:http://localhost:${address.port}`);
                resolve();
            }
            else {
                reject(new Error('Failed to get proxy server address'));
            }
        });
        proxyServer.on('error', (error) => {
            console.error('Proxy server error:', error);
            reject(error);
        });
    });
}
startProxyServer().catch((error) => {
    console.error('Failed to start proxy server:', error);
    process.exit(1);
});
process.stdin.resume();
//# sourceMappingURL=server.js.map