/*---------------------------------------------------------------------------------------------
 * Copyright (c) Lotas Inc. All rights reserved.
 *--------------------------------------------------------------------------------------------*/

import express from 'express';
import * as http from 'http';

let proxyServer: http.Server | undefined;

async function startProxyServer() {
    const app = express();
    
    app.use((req: express.Request, res: express.Response, next: express.NextFunction) => {
        res.header('Access-Control-Allow-Origin', '*');
        res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
        res.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept, Authorization, Cache-Control');
        if (req.method === 'OPTIONS') {
            res.sendStatus(200);
        } else {
            next();
        }
    });

    app.use(express.json({ limit: '100mb' }));

    app.post('/ai/query', async (req: express.Request, res: express.Response) => {
        res.writeHead(200, {
            'Content-Type': 'text/event-stream',
            'Cache-Control': 'no-cache',
            'Connection': 'keep-alive',
            'Access-Control-Allow-Origin': '*',
        });

        const { LocalBackendService } = await import('./services/localBackendService.js');
        const { FunctionDefinitionService } = await import('./services/functionDefinitionService.js');
        
        const functionDefinitionService = new FunctionDefinitionService();
        const service = new LocalBackendService(functionDefinitionService);
        
        const { conversation, provider, model, temperature, request_id, request_type, byok_keys, ...contextData } = req.body;
        
        // Extract web search enabled from header
        const webSearchEnabledHeader = req.headers['x-rao-web-search-enabled'] as string;
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
                write: (data: string) => res.write(data)
            };
            await service.processSummarizationRequest(req.body, request_id, outputStream);
            res.end();
        } else {                    
            const contextWithByok = { ...contextData, byok_keys };
            
            await service.processStreamingQuery(
                conversation || [],
                provider,
                model,
                temperature || 0.7,
                request_id || `req_${Date.now()}`,
                contextWithByok,
                (data) => res.write(`data: ${JSON.stringify(data)}\n\n`),
                (error) => {
                    console.error('Proxy server streaming error:', error);
                    res.write(`data: ${JSON.stringify({ error: error.message })}\n\n`);
                    res.end();
                },
                () => res.end(),
                webSearchEnabled
            );
        }
    });

    return new Promise<void>((resolve, reject) => {
        proxyServer = app.listen(0, 'localhost', () => {
            const address = proxyServer?.address();
            if (address && typeof address === 'object') {
                console.log(`PROXY_URL:http://localhost:${address.port}`);
                resolve();
            } else {
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

