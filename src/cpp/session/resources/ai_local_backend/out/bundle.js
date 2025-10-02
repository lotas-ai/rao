"use strict";
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __esm = (fn, res) => function __init() {
  return fn && (res = (0, fn[__getOwnPropNames(fn)[0]])(fn = 0)), res;
};
var __commonJS = (cb, mod) => function __require() {
  return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
};
var __export = (target, all) => {
  for (var name in all)
    __defProp(target, name, { get: all[name], enumerable: true });
};
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

// out/services/streamingService.js
var require_streamingService = __commonJS({
  "out/services/streamingService.js"(exports2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", { value: true });
    exports2.StreamingService = void 0;
    var StreamingService = class {
      constructor() {
      }
      /**
       * Send error event - matches SseErrorEvent format
       * Format: {"request_id":"req_123","error":"Error message","isComplete":true}
       */
      sendErrorEvent(onData, request_id, errorMessage) {
        const event = {
          type: "error",
          request_id,
          error: { message: errorMessage, type: "error", details: {} },
          isComplete: true
        };
        onData(event);
      }
      /**
       * Send end_turn event - matches SseEndTurnEvent format
       * Format: {"request_id":"req_123","end_turn":true,"isComplete":true}
       */
      sendEndTurnEvent(onData, request_id) {
        const event = {
          type: "end_turn",
          request_id,
          end_turn: true,
          isComplete: true
        };
        onData(event);
      }
      /**
       * Send complete event - matches SseTextEvent format and custom field format
       * For response field: {"request_id":"req_123","response":"Complete text","isComplete":true}
       * For other fields: {"request_id":"req_123","field_name":"value","isComplete":true}
       */
      sendCompleteEvent(onData, request_id, field, value) {
        const event = {
          request_id,
          isComplete: true
        };
        if (field === "action") {
          try {
            const parsedAction = JSON.parse(value);
            Object.assign(event, parsedAction);
          } catch (e) {
            event[field] = value;
          }
        } else {
          event[field] = value;
        }
        onData(event);
      }
      /**
       * Send delta event - matches SseDeltaEvent format
       * Format: {"request_id":"req_123","delta":"partial text","field":"response","isComplete":false}
       */
      sendDeltaEvent(onData, request_id, field, delta) {
        const event = {
          request_id,
          delta,
          field,
          isComplete: false
        };
        onData(event);
      }
    };
    exports2.StreamingService = StreamingService;
  }
});

// out/services/httpClient.js
var require_httpClient = __commonJS({
  "out/services/httpClient.js"(exports2) {
    "use strict";
    var __createBinding2 = exports2 && exports2.__createBinding || (Object.create ? (function(o, m, k, k2) {
      if (k2 === void 0) k2 = k;
      var desc = Object.getOwnPropertyDescriptor(m, k);
      if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
        desc = { enumerable: true, get: function() {
          return m[k];
        } };
      }
      Object.defineProperty(o, k2, desc);
    }) : (function(o, m, k, k2) {
      if (k2 === void 0) k2 = k;
      o[k2] = m[k];
    }));
    var __setModuleDefault2 = exports2 && exports2.__setModuleDefault || (Object.create ? (function(o, v) {
      Object.defineProperty(o, "default", { enumerable: true, value: v });
    }) : function(o, v) {
      o["default"] = v;
    });
    var __importStar2 = exports2 && exports2.__importStar || /* @__PURE__ */ (function() {
      var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function(o2) {
          var ar = [];
          for (var k in o2) if (Object.prototype.hasOwnProperty.call(o2, k)) ar[ar.length] = k;
          return ar;
        };
        return ownKeys(o);
      };
      return function(mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) {
          for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding2(result, mod, k[i]);
        }
        __setModuleDefault2(result, mod);
        return result;
      };
    })();
    Object.defineProperty(exports2, "__esModule", { value: true });
    exports2.httpRequest = httpRequest;
    var https = __importStar2(require("https"));
    var http = __importStar2(require("http"));
    var HttpResponseImpl = class {
      constructor(status, statusText, stream) {
        this.status = status;
        this.statusText = statusText;
        this.stream = stream;
        this.ok = this.status >= 200 && this.status < 300;
      }
      async body() {
        return this.stream;
      }
    };
    var electronFetch;
    try {
      electronFetch = require("electron").net.fetch;
    } catch {
      electronFetch = null;
    }
    async function httpRequest(url, options = {}) {
      if (electronFetch) {
        try {
          const response = await electronFetch(url, {
            method: options.method || "GET",
            headers: options.headers,
            body: options.body,
            signal: options.signal
          });
          return {
            ok: response.ok,
            status: response.status,
            statusText: response.statusText,
            body: async () => {
              const webStream = response.body;
              if (!webStream) {
                throw new Error("Response body is null");
              }
              const { Readable } = require("stream");
              return Readable.fromWeb(webStream);
            }
          };
        } catch (error) {
        }
      }
      return new Promise((resolve, reject) => {
        const urlObj = new URL(url);
        const module3 = urlObj.protocol === "https:" ? https : http;
        const req = module3.request(url, {
          method: options.method || "GET",
          headers: options.headers
        }, (res) => {
          resolve(new HttpResponseImpl(res.statusCode || 0, res.statusMessage || "", res));
        });
        req.setTimeout(60 * 1e3);
        req.on("error", reject);
        if (options.signal) {
          options.signal.addEventListener("abort", () => {
            req.destroy();
            reject(new Error("Request aborted"));
          });
        }
        if (options.body) {
          req.write(options.body);
        }
        req.end();
      });
    }
  }
});

// out/services/streamingProxyHelper.js
var require_streamingProxyHelper = __commonJS({
  "out/services/streamingProxyHelper.js"(exports2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", { value: true });
    exports2.StreamingProxyHelper = void 0;
    var StreamingProxyHelper = class {
      /**
       * Safely write to output stream with error handling
       */
      safeWriteToOutputStream(outputStream, data, _requestId, streamState) {
        if (!outputStream) {
          return false;
        }
        try {
          outputStream.write(data);
          return true;
        } catch (e) {
          if (e.code === "EPIPE" || e.code === "ECONNRESET") {
            return false;
          }
          if (streamState && !streamState.writeErrorLogged) {
            console.error("Unexpected error writing to output stream:", e.message);
            streamState.writeErrorLogged = true;
          }
          return false;
        }
      }
      /**
       * Create a standard error event
       */
      createErrorEvent(requestId, errorMessage) {
        const event = {
          request_id: requestId,
          error: errorMessage,
          isComplete: true
        };
        return `data: ${JSON.stringify(event)}

`;
      }
      /**
       * Create a text completion event
       */
      createTextCompleteEvent(requestId, content) {
        const event = {
          request_id: requestId,
          response: content,
          isComplete: true
        };
        return `data: ${JSON.stringify(event)}

`;
      }
      /**
       * Create a text delta event
       */
      createTextDeltaEvent(requestId, delta) {
        const event = {
          request_id: requestId,
          delta,
          field: "response",
          isComplete: false
        };
        return `data: ${JSON.stringify(event)}

`;
      }
      /**
       * Create an end_turn event
       */
      createEndTurnEvent(requestId) {
        const event = {
          request_id: requestId,
          end_turn: true,
          isComplete: true
        };
        return `data: ${JSON.stringify(event)}

`;
      }
      /**
       * Create a timeout event
       */
      createTimeoutEvent(requestId, serviceName, timeoutSeconds) {
        const errorMessage = `Stream timeout - no response from ${serviceName} for ${timeoutSeconds} seconds`;
        return this.createErrorEvent(requestId, errorMessage);
      }
      /**
       * Create web search call event
       */
      createWebSearchCallEvent(requestId, webSearchCallJson) {
        const event = {
          request_id: requestId,
          web_search_call: JSON.parse(webSearchCallJson),
          field: "web_search_call",
          isComplete: false
        };
        return `data: ${JSON.stringify(event)}

`;
      }
      /**
       * Create web search results event
       */
      createWebSearchResultsEvent(requestId, webSearchResultsJson) {
        const event = {
          request_id: requestId,
          web_search_results: JSON.parse(webSearchResultsJson),
          field: "web_search_results",
          isComplete: false
        };
        return `data: ${JSON.stringify(event)}

`;
      }
      /**
       * Create annotations event
       */
      createAnnotationsEvent(requestId, annotationsJson) {
        const event = {
          request_id: requestId,
          annotations: JSON.parse(annotationsJson),
          field: "annotations",
          isComplete: false
        };
        return `data: ${JSON.stringify(event)}

`;
      }
      /**
       * Create response ID event
       */
      createResponseIdEvent(requestId, responseId) {
        const event = {
          request_id: requestId,
          response_id: responseId,
          isComplete: false
        };
        return `data: ${JSON.stringify(event)}

`;
      }
      /**
       * Send streaming function delta event
       */
      sendStreamingFunctionDelta(requestId, outputStream, functionName, callId, delta, streamState) {
        const event = {
          request_id: requestId,
          field: functionName,
          call_id: callId,
          delta,
          isComplete: false
        };
        return this.safeWriteToOutputStream(outputStream, `data: ${JSON.stringify(event)}

`, requestId, streamState);
      }
      /**
       * Handle function call completion
       * This consolidates the repetitive completion logic found in both OpenAI and Anthropic services
       */
      handleFunctionCallCompletion(requestId, outputStream, functionName, callId, functionArguments, originalRequest, streamState, functionCallCompletionSent) {
        if (functionName === "end_turn") {
          return this.safeWriteToOutputStream(outputStream, this.createEndTurnEvent(requestId), requestId, streamState);
        }
        if (functionName === "web_search") {
          return true;
        }
        if (functionName === "search_replace") {
          if (!functionCallCompletionSent) {
            return this.sendStreamingFunctionCompletionInternal(requestId, outputStream, functionName, callId, functionArguments, originalRequest, streamState);
          }
        } else if (functionName === "run_console_cmd" || functionName === "run_terminal_cmd") {
          if (!functionCallCompletionSent) {
            return this.sendStreamingFunctionCompletionInternal(requestId, outputStream, functionName, callId, functionArguments, originalRequest, streamState);
          }
        } else {
          return this.sendFunctionCallEventInternal(requestId, outputStream, functionName, callId, functionArguments, originalRequest, streamState);
        }
        return true;
      }
      /**
       * Send streaming function completion event (internal method)
       */
      sendStreamingFunctionCompletionInternal(requestId, outputStream, fieldName, callId, _functionArguments, _originalRequest, streamState) {
        try {
          const event = {
            request_id: requestId,
            field: fieldName,
            call_id: callId,
            response: null,
            // Minimal completion event
            isComplete: true
          };
          const completeEvent = `data: ${JSON.stringify(event)}

`;
          return this.safeWriteToOutputStream(outputStream, completeEvent, requestId, streamState);
        } catch (e) {
          console.error("Failed to serialize streaming function completion event:", e);
          throw new Error("Critical error: Failed to serialize streaming function completion event");
        }
      }
      /**
       * Send regular function call event (internal method)
       */
      sendFunctionCallEventInternal(requestId, outputStream, functionName, callId, functionArguments, _originalRequest, streamState) {
        try {
          const functionCall = {
            name: functionName,
            call_id: callId,
            arguments: functionArguments.length === 0 ? "{}" : functionArguments
          };
          const event = {
            request_id: requestId,
            isComplete: true,
            action: "function_call",
            function_call: functionCall
          };
          const functionCallEvent = `data: ${JSON.stringify(event)}

`;
          return this.safeWriteToOutputStream(outputStream, functionCallEvent, requestId, streamState);
        } catch (e) {
          console.error("Error creating function call event:", e);
          throw new Error("Critical error: Failed to serialize function call event");
        }
      }
    };
    exports2.StreamingProxyHelper = StreamingProxyHelper;
  }
});

// out/services/openAiProxyService.js
var require_openAiProxyService = __commonJS({
  "out/services/openAiProxyService.js"(exports2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", { value: true });
    exports2.OpenAiProxyService = void 0;
    var httpClient_js_1 = require_httpClient();
    var streamingProxyHelper_js_1 = require_streamingProxyHelper();
    var StreamResult;
    (function(StreamResult2) {
      StreamResult2["SUCCESS"] = "SUCCESS";
    })(StreamResult || (StreamResult = {}));
    var OpenAiProxyService = class {
      constructor() {
        this.streamingHelper = new streamingProxyHelper_js_1.StreamingProxyHelper();
        this.OPENAI_API_URL = "https://api.openai.com/v1/responses";
      }
      /**
       * Process streaming requests with direct OutputStream callback
       * Used by the /ai/query endpoint for unified streaming
       */
      async processStreamingResponsesWithCallback(requestBody, user, originalHeaders, request_id, outputStream, originalRequest) {
        await this.processStreamingResponsesWithCallbackInternal(requestBody, user, originalHeaders, request_id, outputStream, originalRequest);
      }
      /**
       * Internal method with original request parameter for retry logic
       */
      async processStreamingResponsesWithCallbackInternal(requestBody, _user, originalHeaders, request_id, outputStream, originalRequest) {
        const requestBodyJson = JSON.parse(requestBody);
        const apiKey = requestBodyJson.byok_keys?.openai;
        if (!apiKey) {
          throw new Error("OpenAI API key not found in request. Please ensure BYOK is properly configured.");
        }
        const model = requestBodyJson.model;
        const disableInactivityTimeout = false;
        const inactivityTimeoutMs = 3e4;
        const responsesRequest = { ...requestBodyJson };
        delete responsesRequest.byok_keys;
        responsesRequest.stream = true;
        if (requestBodyJson.temperature) {
          responsesRequest.temperature = requestBodyJson.temperature;
        }
        if (model && model.startsWith("gpt-5")) {
          responsesRequest.text = {
            verbosity: "low"
          };
        }
        const headers = {
          "Authorization": `Bearer ${apiKey}`,
          "Content-Type": "application/json"
        };
        if (originalHeaders && originalHeaders["OpenAI-Beta"]) {
          headers["OpenAI-Beta"] = originalHeaders["OpenAI-Beta"];
        }
        const streamState = {
          textContent: "",
          hasTextContent: false,
          hasFunctionCall: false,
          textStreamingComplete: false,
          firstDeltaAtMs: -1,
          lastDeltaAtMs: -1,
          deltaCount: 0,
          parallelFunctionCalls: /* @__PURE__ */ new Map(),
          hasParallelFunctionCalls: false,
          userStreamingStarted: false,
          originalRequest,
          modifiedRequest: null,
          cancelled: false,
          cancelledMessageLogged: false,
          writeErrorLogged: false,
          functionCallCompletionSent: false
        };
        let lastStreamEventTime = Date.now();
        let firstEventLogged = false;
        let sseBuffer = "";
        const response = await (0, httpClient_js_1.httpRequest)(this.OPENAI_API_URL, {
          method: "POST",
          headers,
          body: JSON.stringify(responsesRequest)
        });
        if (!response.ok) {
          throw new Error(`OpenAI API error: ${response.status} ${response.statusText}`);
        }
        const stream = await response.body();
        if (!stream) {
          throw new Error("No response stream available");
        }
        await new Promise((resolve, reject) => {
          const timeoutInterval = setInterval(() => {
            const timeSinceLastEvent = Date.now() - lastStreamEventTime;
            if (!disableInactivityTimeout && timeSinceLastEvent > inactivityTimeoutMs) {
              const timeoutSeconds = Math.floor(inactivityTimeoutMs / 1e3);
              this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTimeoutEvent(request_id, "OpenAI", timeoutSeconds));
              clearInterval(timeoutInterval);
              resolve();
            }
          }, 1e3);
          stream.on("data", (chunk) => {
            try {
              if (streamState.cancelled) {
                clearInterval(timeoutInterval);
                resolve();
                return;
              }
              const nowMs = Date.now();
              lastStreamEventTime = nowMs;
              if (!firstEventLogged) {
                firstEventLogged = true;
              }
              const chunkStr = chunk.toString("utf8");
              sseBuffer += chunkStr;
              while (sseBuffer.includes("\n\n")) {
                const eventEnd = sseBuffer.indexOf("\n\n");
                const eventBlock = sseBuffer.substring(0, eventEnd);
                sseBuffer = sseBuffer.substring(eventEnd + 2);
                if (eventBlock.trim()) {
                  const lines = eventBlock.split("\n");
                  let eventData = null;
                  for (const line of lines) {
                    if (line.startsWith("data: ")) {
                      eventData = line.substring(6).trim();
                    }
                  }
                  if (eventData) {
                    const jsonData = JSON.parse(eventData);
                    this.processStreamingChunk(jsonData, request_id, outputStream, streamState, originalRequest);
                  }
                }
              }
            } catch (e) {
              if (streamState.cancelled) {
                clearInterval(timeoutInterval);
                resolve();
                return;
              }
            }
          });
          stream.on("error", (error) => {
            clearInterval(timeoutInterval);
            let isCancellation = false;
            const errorMessage = error.message;
            if (errorMessage && (errorMessage.includes("Connection reset") || errorMessage.includes("Connection closed") || errorMessage.includes("cancelled"))) {
              isCancellation = true;
            }
            if (isCancellation) {
              streamState.cancelled = true;
              resolve();
              return;
            }
            try {
              let finalErrorMessage = "Stream error: " + errorMessage;
              this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createErrorEvent(request_id, finalErrorMessage));
            } catch (e) {
              console.error("Could not send error to client:", e.message);
            }
            reject(error);
          });
          stream.on("end", () => {
            clearInterval(timeoutInterval);
            if (streamState.cancelled) {
              resolve();
              return;
            }
            try {
              this.handleStreamCompletion(request_id, outputStream, streamState);
            } catch (e) {
              console.error("Error in OpenAI stream completion:", e.message);
            }
            resolve();
          });
        });
        return StreamResult.SUCCESS;
      }
      /**
       * Send a completed function call event for parallel function calling
       */
      sendCompletedFunctionCall(request_id, outputStream, streamState, functionCall) {
        if (!this.handleFunctionCallCompletion(request_id, outputStream, functionCall.functionName, functionCall.callId, functionCall.functionArguments, streamState.originalRequest, streamState, functionCall.functionCallCompletionSent)) {
          return;
        }
        if (functionCall.functionName === "search_replace" || functionCall.functionName === "run_console_cmd" || functionCall.functionName === "run_terminal_cmd") {
          functionCall.functionCallCompletionSent = true;
        }
        streamState.hasFunctionCall = true;
      }
      /**
       * Handle stream completion - send any remaining content
       */
      handleStreamCompletion(request_id, outputStream, streamState) {
        if (streamState.cancelled) {
          return;
        }
        if (streamState.hasTextContent && !streamState.textStreamingComplete && streamState.textContent.length > 0) {
          this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTextCompleteEvent(request_id, streamState.textContent));
        }
      }
      /**
       * Process individual streaming chunks and send appropriate SSE events
       */
      processStreamingChunk(chunkNode, request_id, outputStream, streamState, _originalRequest) {
        if (streamState.cancelled) {
          if (!streamState.cancelledMessageLogged) {
            streamState.cancelledMessageLogged = true;
          }
          return;
        }
        if (chunkNode.type) {
          const eventType = chunkNode.type;
          if (eventType === "response.output_text.delta") {
            const now = Date.now();
            if (streamState.firstDeltaAtMs < 0)
              streamState.firstDeltaAtMs = now;
            streamState.lastDeltaAtMs = now;
            streamState.deltaCount++;
            if (chunkNode.delta) {
              const delta = chunkNode.delta;
              if (chunkNode.annotations) {
                if (!this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createAnnotationsEvent(request_id, chunkNode.annotations))) {
                  return;
                }
              }
              streamState.textContent += delta;
              streamState.hasTextContent = true;
              streamState.userStreamingStarted = true;
              if (!this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTextDeltaEvent(request_id, delta))) {
                return;
              }
            }
          } else if (eventType === "response.function_call_arguments.delta") {
            const now = Date.now();
            if (streamState.firstDeltaAtMs < 0)
              streamState.firstDeltaAtMs = now;
            streamState.lastDeltaAtMs = now;
            streamState.deltaCount++;
            if (chunkNode.delta) {
              const delta = chunkNode.delta;
              let targetCallId = null;
              if (chunkNode.call_id) {
                targetCallId = chunkNode.call_id;
              } else {
                for (const [callId] of streamState.parallelFunctionCalls) {
                  targetCallId = callId;
                  break;
                }
              }
              if (targetCallId != null && streamState.parallelFunctionCalls.has(targetCallId)) {
                const functionCall = streamState.parallelFunctionCalls.get(targetCallId);
                functionCall.functionArguments += delta;
                if (functionCall.functionName === "search_replace") {
                  if (!this.streamingHelper.sendStreamingFunctionDelta(request_id, outputStream, "search_replace", targetCallId, delta, streamState)) {
                    streamState.cancelled = true;
                    return;
                  }
                } else if (functionCall.functionName === "run_console_cmd" || functionCall.functionName === "run_terminal_cmd") {
                  if (!this.streamingHelper.sendStreamingFunctionDelta(request_id, outputStream, functionCall.functionName, targetCallId, delta, streamState)) {
                    streamState.cancelled = true;
                    return;
                  }
                }
              }
            }
          } else if (eventType === "response.output_item.added") {
            if (chunkNode.item) {
              const item = chunkNode.item;
              if (item.type) {
                const itemType = item.type;
                if (itemType === "web_search_call") {
                  if (!this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createWebSearchCallEvent(request_id, item))) {
                    return;
                  }
                } else if (itemType === "function_call") {
                  streamState.hasFunctionCall = true;
                  const itemFunctionName = item.name || "unknown";
                  const itemCallId = item.call_id || "unknown";
                  if (!streamState.parallelFunctionCalls.has(itemCallId)) {
                    streamState.parallelFunctionCalls.set(itemCallId, {
                      functionName: itemFunctionName,
                      callId: itemCallId,
                      functionArguments: "",
                      argumentsComplete: false,
                      functionCallCompletionSent: false
                    });
                    streamState.hasParallelFunctionCalls = true;
                  }
                }
              }
            }
          } else if (eventType === "response.function_call_arguments.done") {
            if (chunkNode.arguments !== void 0) {
              const completeArguments = chunkNode.arguments;
              let targetCallId = null;
              let functionName = null;
              if (chunkNode.call_id) {
                targetCallId = chunkNode.call_id;
              } else {
                for (const [callId] of streamState.parallelFunctionCalls) {
                  targetCallId = callId;
                  break;
                }
                targetCallId = targetCallId || "unknown";
              }
              if (chunkNode.name) {
                functionName = chunkNode.name;
              } else {
                if (targetCallId && streamState.parallelFunctionCalls.has(targetCallId)) {
                  functionName = streamState.parallelFunctionCalls.get(targetCallId).functionName;
                } else {
                  functionName = "unknown";
                }
              }
              if (!this.completeTextStreamingIfNeeded(request_id, outputStream, streamState.textContent, streamState.hasTextContent, streamState.textStreamingComplete, streamState.userStreamingStarted, streamState)) {
                return;
              }
              streamState.textStreamingComplete = true;
              if (!this.handleFunctionCallCompletion(request_id, outputStream, functionName || "unknown", targetCallId || "unknown", completeArguments, streamState.originalRequest, streamState, streamState.functionCallCompletionSent)) {
                return;
              }
              if (functionName === "search_replace" || functionName === "run_console_cmd" || functionName === "run_terminal_cmd") {
                streamState.functionCallCompletionSent = true;
              }
              streamState.hasFunctionCall = true;
              if (streamState.hasParallelFunctionCalls && targetCallId && streamState.parallelFunctionCalls.has(targetCallId)) {
                const functionCall = streamState.parallelFunctionCalls.get(targetCallId);
                functionCall.functionArguments = completeArguments;
                functionCall.argumentsComplete = true;
              }
            }
          } else if (eventType === "response.completed") {
            if (streamState.hasTextContent && !streamState.hasFunctionCall && !streamState.textStreamingComplete) {
              if (this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTextCompleteEvent(request_id, streamState.textContent))) {
                streamState.textStreamingComplete = true;
              }
            }
          } else if (eventType === "response.output_item.done") {
            if (chunkNode.item) {
              const item = chunkNode.item;
              if (item.type === "function_call") {
                if (streamState.hasTextContent && !streamState.textStreamingComplete && streamState.userStreamingStarted) {
                  if (!this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTextCompleteEvent(request_id, streamState.textContent))) {
                    return;
                  }
                  streamState.textStreamingComplete = true;
                }
                streamState.hasFunctionCall = true;
              }
            }
          } else if (eventType === "response.function_call_output") {
            if (chunkNode.function_call) {
              const functionCallNode = chunkNode.function_call;
              if (!this.completeTextStreamingIfNeeded(request_id, outputStream, streamState.textContent, streamState.hasTextContent, streamState.textStreamingComplete, streamState.userStreamingStarted, streamState)) {
                return;
              }
              streamState.textStreamingComplete = true;
              const callId = functionCallNode.call_id || "unknown";
              if (streamState.hasParallelFunctionCalls && streamState.parallelFunctionCalls.has(callId)) {
                const functionCall = streamState.parallelFunctionCalls.get(callId);
                if (functionCall.argumentsComplete) {
                  this.sendCompletedFunctionCall(request_id, outputStream, streamState, functionCall);
                }
              }
              streamState.hasFunctionCall = true;
            } else {
              console.warn("function_call_output event but no function_call field");
            }
          } else if (eventType === "response.content_part.done") {
          } else if (eventType === "response.content_part.added") {
          } else if (eventType === "response.in_progress") {
          } else if (eventType === "response.created") {
            if (chunkNode.response?.id) {
              const responseId = chunkNode.response.id;
              this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createResponseIdEvent(request_id, responseId));
            }
          } else if (eventType === "response.output_text.done") {
            if (streamState.hasTextContent && !streamState.textStreamingComplete && streamState.userStreamingStarted) {
              if (!streamState.hasFunctionCall) {
                if (this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTextCompleteEvent(request_id, streamState.textContent))) {
                  streamState.textStreamingComplete = true;
                }
              }
            }
          } else if (eventType === "response.web_search_call.in_progress") {
          } else if (eventType === "response.web_search_call.searching") {
          } else if (eventType === "response.web_search_call.completed") {
          } else if (eventType === "response.output_text.annotation.added") {
          } else {
            console.warn("Unhandled OpenAI event type:", eventType);
          }
        } else {
          console.warn("OpenAI chunk without 'type' field:", JSON.stringify(chunkNode));
        }
      }
      /**
       * Handle function call completion with all the version-specific logic
       * This consolidates the repetitive completion logic found in both OpenAI and Anthropic services
       */
      handleFunctionCallCompletion(request_id, outputStream, functionName, callId, functionArguments, originalRequest, streamState, functionCallCompletionSent) {
        return this.streamingHelper.handleFunctionCallCompletion(request_id, outputStream, functionName, callId, functionArguments, originalRequest, streamState, functionCallCompletionSent);
      }
      /**
       * Complete text streaming if needed
       */
      completeTextStreamingIfNeeded(request_id, outputStream, textContent, hasTextContent, textStreamingComplete, userStreamingStarted, _streamState) {
        if (hasTextContent && !textStreamingComplete && userStreamingStarted) {
          if (!this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTextCompleteEvent(request_id, textContent))) {
            return false;
          }
        }
        return true;
      }
    };
    exports2.OpenAiProxyService = OpenAiProxyService;
  }
});

// out/services/anthropicProxyService.js
var require_anthropicProxyService = __commonJS({
  "out/services/anthropicProxyService.js"(exports2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", { value: true });
    exports2.AnthropicProxyService = void 0;
    var httpClient_js_1 = require_httpClient();
    var streamingProxyHelper_js_1 = require_streamingProxyHelper();
    var StreamResult;
    (function(StreamResult2) {
      StreamResult2["SUCCESS"] = "SUCCESS";
    })(StreamResult || (StreamResult = {}));
    var AnthropicProxyService = class {
      constructor() {
        this.cancellationService = /* @__PURE__ */ new Set();
        this.streamingHelper = new streamingProxyHelper_js_1.StreamingProxyHelper();
      }
      /**
       * Process streaming requests to Anthropic API with proper SSE parsing
       */
      async processStreamingResponsesWithCallback(requestBody, _user, _originalHeaders, request_id, outputStream, originalRequest) {
        await this.processStreamingResponsesWithCallbackInternal(requestBody, _user, _originalHeaders, request_id, outputStream, originalRequest);
      }
      /**
       * Internal method with original request parameter for retry logic
       */
      async processStreamingResponsesWithCallbackInternal(requestBody, _user, _originalHeaders, request_id, outputStream, originalRequest) {
        try {
          const requestJson = JSON.parse(requestBody);
          const anthropicRequest = {};
          const model = requestJson.model;
          anthropicRequest.model = model;
          anthropicRequest.messages = requestJson.messages;
          if (requestJson.system) {
            if (Array.isArray(requestJson.system)) {
              anthropicRequest.system = requestJson.system;
            } else {
              anthropicRequest.system = requestJson.system;
            }
          }
          if (requestJson.tools) {
            anthropicRequest.tools = requestJson.tools;
          }
          if (requestJson.tool_choice) {
            anthropicRequest.tool_choice = requestJson.tool_choice;
          }
          if (requestJson.max_tokens) {
            anthropicRequest.max_tokens = requestJson.max_tokens;
          } else {
            anthropicRequest.max_tokens = 8192;
          }
          if (requestJson.temperature) {
            anthropicRequest.temperature = requestJson.temperature;
          }
          if (requestJson.top_p) {
            anthropicRequest.top_p = requestJson.top_p;
          }
          if (requestJson.top_k) {
            anthropicRequest.top_k = requestJson.top_k;
          }
          if (anthropicRequest.messages) {
            const messages = anthropicRequest.messages;
            const cleanedMessages = [];
            for (const message of messages) {
              if (message.content) {
                const content = message.content;
                if (content === null || typeof content === "string" && content === "" || Array.isArray(content) && content.length === 0) {
                  continue;
                }
              } else {
                continue;
              }
              cleanedMessages.push(message);
            }
            anthropicRequest.messages = cleanedMessages;
          }
          anthropicRequest.stream = true;
          let anthropicApiKey = "";
          if (originalRequest && originalRequest.byok_keys && originalRequest.byok_keys.anthropic) {
            anthropicApiKey = originalRequest.byok_keys.anthropic;
          } else {
            this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createErrorEvent(request_id, "Anthropic API key not found. Please configure your API key in settings."));
            return StreamResult.SUCCESS;
          }
          const headers = {
            "x-api-key": anthropicApiKey,
            "Content-Type": "application/json",
            "anthropic-version": "2023-06-01",
            "anthropic-beta": "fine-grained-tool-streaming-2025-05-14"
          };
          const streamState = {
            accumulatedText: "",
            toolInputBuffers: /* @__PURE__ */ new Map(),
            toolBlocks: /* @__PURE__ */ new Map(),
            hasTextContent: false,
            hasToolUse: false,
            textStreamingComplete: false,
            usageData: null,
            sseBuffer: "",
            isAfterEditFile: false,
            userStreamingStarted: false,
            modifiedRequest: null,
            cancelled: false,
            cancelledMessageLogged: false,
            writeErrorLogged: false,
            contentBlockTypes: /* @__PURE__ */ new Map(),
            parallelFunctionCalls: /* @__PURE__ */ new Map(),
            hasParallelFunctionCalls: false
          };
          let lastStreamEventTime = Date.now();
          const response = await (0, httpClient_js_1.httpRequest)("https://api.anthropic.com/v1/messages", {
            method: "POST",
            headers,
            body: JSON.stringify(anthropicRequest)
          });
          const stream = await response.body();
          stream.on("data", (chunk) => {
            try {
              if (this.cancellationService.has(request_id)) {
                streamState.cancelled = true;
                return;
              }
              lastStreamEventTime = Date.now();
              if (streamState.cancelled) {
                return;
              }
              this.processAnthropicSSEChunk(chunk.toString(), request_id, outputStream, streamState, originalRequest);
            } catch (error) {
              if (this.cancellationService.has(request_id)) {
                streamState.cancelled = true;
                return;
              }
              console.error("Error processing Anthropic chunk:", error.message);
              if (streamState.cancelled) {
                return;
              }
            }
          });
          stream.on("error", (error) => {
            let isCancellation = false;
            const errorMessage = error.message;
            if (error.code === "ECONNRESET" || errorMessage && (errorMessage.includes("Connection reset") || errorMessage.includes("Connection closed") || errorMessage.includes("cancelled"))) {
              isCancellation = true;
            }
            if (isCancellation) {
              streamState.cancelled = true;
              return;
            }
            console.error("Stream error from Anthropic:", errorMessage, error);
            try {
              let finalErrorMessage = "Stream error: " + errorMessage;
              if (error.status) {
                const statusCode = error.status;
                if (statusCode === 400) {
                  finalErrorMessage = "Invalid request to Anthropic API (400 Bad Request)";
                } else if (statusCode === 401) {
                  finalErrorMessage = "Authentication failed with Anthropic API (401 Unauthorized)";
                } else if (statusCode === 429) {
                  finalErrorMessage = "Rate limit exceeded for Anthropic API (429 Too Many Requests)";
                } else if (statusCode === 500) {
                  finalErrorMessage = "Anthropic API server error (500 Internal Server Error)";
                } else if (statusCode === 529) {
                  finalErrorMessage = "Anthropic's API is temporarily overloaded. Please wait a moment and try again or use a different model provider from the Settings (gear icon).";
                } else {
                  finalErrorMessage = `HTTP error ${statusCode} from Anthropic API`;
                }
              }
              this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createErrorEvent(request_id, finalErrorMessage));
            } catch (e) {
              console.error("Could not send error to client:", e.message);
            }
          });
          stream.on("end", () => {
            if (streamState.cancelled) {
              return;
            }
            try {
              this.handleStreamCompletion(request_id, outputStream, streamState);
            } catch (error) {
              console.error("Error in Anthropic stream completion:", error.message, error);
            }
          });
          return new Promise((resolve) => {
            const checkTimeout = () => {
              const timeSinceLastEvent = Date.now() - lastStreamEventTime;
              if (timeSinceLastEvent > 3e4) {
                this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTimeoutEvent(request_id, "Anthropic", 30));
                resolve(StreamResult.SUCCESS);
                return;
              }
              if (!streamState.cancelled) {
                setTimeout(checkTimeout, 1e3);
              }
            };
            stream.on("end", () => {
              resolve(StreamResult.SUCCESS);
            });
            checkTimeout();
          });
        } catch (error) {
          if (!(error.message === null)) {
            console.error("Error in Anthropic streaming:", error.message, error);
          }
          const errorMessage = error.message || "Request interrupted";
          this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createErrorEvent(request_id, "Failed to process request: " + errorMessage));
        }
        return StreamResult.SUCCESS;
      }
      /**
       * Send a completed function call event for parallel function calling
       */
      sendCompletedFunctionCall(request_id, outputStream, streamState, functionCall, originalRequest) {
        if (functionCall.functionName !== "web_search") {
          this.streamingHelper.handleFunctionCallCompletion(request_id, outputStream, functionCall.functionName, functionCall.callId, functionCall.functionArguments, originalRequest, streamState, false);
        }
        streamState.hasToolUse = true;
      }
      /**
       * Process Anthropic SSE chunks and convert to unified format
       */
      processAnthropicSSEChunk(chunk, request_id, outputStream, state, originalRequest) {
        if (state.cancelled) {
          return;
        }
        state.sseBuffer += chunk;
        const bufferedData = state.sseBuffer;
        const events = bufferedData.split("\n\n");
        const chunkEndsWithCompleteEvent = chunk.endsWith("\n\n");
        const eventsToProcess = chunkEndsWithCompleteEvent ? events.length : events.length - 1;
        for (let i = 0; i < eventsToProcess; i++) {
          const eventBlock = events[i];
          if (!eventBlock.trim())
            continue;
          this.processCompleteSSEEvent(eventBlock, request_id, outputStream, state, originalRequest);
        }
        if (!chunkEndsWithCompleteEvent && events.length > 0) {
          state.sseBuffer = events[events.length - 1];
        } else {
          state.sseBuffer = "";
        }
      }
      /**
       * Process a complete SSE event
       */
      processCompleteSSEEvent(eventBlock, request_id, outputStream, state, originalRequest) {
        if (state.cancelled) {
          return;
        }
        const lines = eventBlock.split("\n");
        let eventType = null;
        let eventData = null;
        for (const line of lines) {
          if (line.startsWith("event: ")) {
            eventType = line.substring(7).trim();
          } else if (line.startsWith("data: ")) {
            eventData = line.substring(6).trim();
          }
        }
        if (!eventType || !eventData) {
          return;
        }
        if (eventType === "ping") {
          return;
        }
        if (eventType === "error") {
          try {
            const errorData = JSON.parse(eventData);
            let errorMessage = "Anthropic API error";
            if (errorData.error && errorData.error.message) {
              errorMessage = errorData.error.message;
              if (errorMessage === "Overloaded" || errorMessage === "The system encountered an overload and is unable to process the request at this time. Please try again later.") {
                errorMessage = "Anthropic's API is temporarily overloaded. Please wait a moment and try again or use a different model provider from the Settings (gear icon).";
              }
            }
            this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createErrorEvent(request_id, errorMessage));
          } catch (error) {
            this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createErrorEvent(request_id, "Anthropic API error"));
          }
          return;
        }
        let data;
        try {
          data = JSON.parse(eventData);
        } catch (error) {
          return;
        }
        switch (eventType) {
          case "message_start":
            state.accumulatedText = "";
            state.toolInputBuffers.clear();
            state.toolBlocks.clear();
            state.hasTextContent = false;
            state.hasToolUse = false;
            state.textStreamingComplete = false;
            state.contentBlockTypes.clear();
            state.parallelFunctionCalls.clear();
            state.hasParallelFunctionCalls = false;
            if (data.message && data.message.usage) {
              state.usageData = data.message.usage;
            }
            break;
          case "content_block_start":
            this.handleContentBlockStart(data, state, originalRequest);
            break;
          case "content_block_delta":
            this.handleContentBlockDelta(data, request_id, outputStream, state, originalRequest);
            break;
          case "content_block_stop":
            this.handleContentBlockStop(data, request_id, outputStream, state, originalRequest);
            break;
          case "message_delta":
            if (data.usage) {
              const messageDeltaUsage = data.usage;
              if (state.usageData && messageDeltaUsage.output_tokens) {
                state.usageData.output_tokens = messageDeltaUsage.output_tokens;
                if (messageDeltaUsage.server_tool_use) {
                  state.usageData.server_tool_use = messageDeltaUsage.server_tool_use;
                }
              } else {
                state.usageData = messageDeltaUsage;
              }
            }
            if (data.delta && data.delta.stop_reason) {
              const stopReason = data.delta.stop_reason;
              if (stopReason === "end_turn") {
                if (state.hasTextContent && state.accumulatedText.length > 0) {
                  if (!this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTextCompleteEvent(request_id, state.accumulatedText))) {
                    return;
                  }
                  state.textStreamingComplete = true;
                }
                if (!this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createEndTurnEvent(request_id))) {
                  return;
                }
              }
            }
            break;
          case "message_stop":
            this.handleMessageStop(request_id, outputStream, state);
            break;
        }
      }
      handleContentBlockStart(data, state, _originalRequest) {
        if (!data.content_block || data.index === void 0)
          return;
        const contentBlock = data.content_block;
        const index = data.index;
        const blockType = contentBlock.type;
        state.contentBlockTypes.set(index, blockType);
        if (blockType === "text") {
          state.hasTextContent = true;
        } else if (blockType === "tool_use") {
          state.hasToolUse = true;
          state.toolBlocks.set(index, contentBlock);
          state.toolInputBuffers.set(index, "");
          if (contentBlock.name && contentBlock.id) {
            const functionName = contentBlock.name;
            const callId = contentBlock.id;
            if (!state.parallelFunctionCalls.has(callId)) {
              state.parallelFunctionCalls.set(callId, {
                functionName,
                callId,
                functionArguments: "",
                argumentsComplete: false,
                functionCallCompletionSent: false,
                contentBlockIndex: index
              });
              state.hasParallelFunctionCalls = true;
            }
          }
        } else if (blockType === "server_tool_use") {
          if (contentBlock.name && contentBlock.name === "web_search") {
            state.toolBlocks.set(index, contentBlock);
            state.toolInputBuffers.set(index, "");
          }
        } else if (blockType === "web_search_tool_result") {
          if (contentBlock.tool_use_id) {
            state.toolBlocks.set(index, contentBlock);
          }
        }
      }
      handleContentBlockDelta(data, request_id, outputStream, state, _originalRequest) {
        if (state.cancelled) {
          return;
        }
        if (!data.delta || data.index === void 0)
          return;
        const delta = data.delta;
        const index = data.index;
        const deltaType = delta.type;
        if (deltaType === "text_delta" && delta.text) {
          const text = delta.text;
          state.accumulatedText += text;
          state.userStreamingStarted = true;
          if (!this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTextDeltaEvent(request_id, text))) {
            state.cancelled = true;
            return;
          }
        } else if (deltaType === "input_json_delta" && delta.partial_json) {
          const partialJson = delta.partial_json;
          if (state.toolInputBuffers.has(index)) {
            const currentBuffer = state.toolInputBuffers.get(index) || "";
            state.toolInputBuffers.set(index, currentBuffer + partialJson);
            const toolBlock = state.toolBlocks.get(index);
            if (toolBlock && toolBlock.id) {
              const callId = toolBlock.id;
              if (state.parallelFunctionCalls.has(callId)) {
                const functionCall = state.parallelFunctionCalls.get(callId);
                functionCall.functionArguments += partialJson;
                if (["search_replace", "run_console_cmd", "run_terminal_cmd"].includes(functionCall.functionName)) {
                  this.streamingHelper.sendStreamingFunctionDelta(request_id, outputStream, functionCall.functionName, callId, partialJson, state);
                }
              }
            }
          }
        }
      }
      handleContentBlockStop(data, request_id, outputStream, state, originalRequest) {
        if (state.cancelled) {
          return;
        }
        if (data.index === void 0)
          return;
        const index = data.index;
        const blockType = state.contentBlockTypes.get(index);
        if (blockType === "server_tool_use") {
          if (state.toolBlocks.has(index) && state.toolInputBuffers.has(index)) {
            const toolBlock = state.toolBlocks.get(index);
            const completeInputJson = state.toolInputBuffers.get(index) || "";
            if (toolBlock && toolBlock.name === "web_search") {
              const searchId = toolBlock.id || "unknown";
              try {
                const searchQuery = JSON.parse(completeInputJson);
                const query = searchQuery.query || "unknown";
                const webSearchCall = {
                  id: searchId,
                  type: "web_search_call",
                  status: "in_progress",
                  query
                };
                if (!this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createWebSearchCallEvent(request_id, JSON.stringify(webSearchCall)))) {
                  return;
                }
              } catch (error) {
                console.error("Error parsing Anthropic web search query:", error);
              }
            }
          }
        } else if (blockType === "web_search_tool_result") {
          if (state.toolBlocks.has(index)) {
            const toolBlock = state.toolBlocks.get(index);
            if (toolBlock && toolBlock.content && Array.isArray(toolBlock.content)) {
              if (!this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createWebSearchResultsEvent(request_id, JSON.stringify(toolBlock)))) {
                return;
              }
            }
          }
        } else if (blockType === "tool_use") {
          if (state.toolBlocks.has(index) && state.toolInputBuffers.has(index)) {
            const toolBlock = state.toolBlocks.get(index);
            const completeInputJson = state.toolInputBuffers.get(index) || "";
            if (state.hasTextContent && !state.textStreamingComplete && state.userStreamingStarted) {
              if (!this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTextCompleteEvent(request_id, state.accumulatedText))) {
                return;
              }
              state.textStreamingComplete = true;
            }
            if (toolBlock) {
              const callId = toolBlock.id;
              if (state.hasParallelFunctionCalls && state.parallelFunctionCalls.has(callId)) {
                const functionCall = state.parallelFunctionCalls.get(callId);
                functionCall.functionArguments = completeInputJson;
                functionCall.argumentsComplete = true;
                this.sendCompletedFunctionCall(request_id, outputStream, state, functionCall, originalRequest);
              }
            }
          }
        }
      }
      handleMessageStop(request_id, outputStream, state) {
        if (state.cancelled) {
          return;
        }
        if (state.hasTextContent && !state.hasToolUse && !state.textStreamingComplete && state.userStreamingStarted) {
          const writeSuccessful = this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTextCompleteEvent(request_id, state.accumulatedText));
          if (!writeSuccessful) {
            state.cancelled = true;
            return;
          }
          state.textStreamingComplete = true;
        }
      }
      /**
       * Handle stream completion and send final completion event
       */
      handleStreamCompletion(request_id, outputStream, state) {
        if (state.cancelled) {
          return;
        }
        if (!state.userStreamingStarted) {
          return;
        }
        if (state.hasTextContent && !state.textStreamingComplete && state.accumulatedText.length > 0) {
          this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTextCompleteEvent(request_id, state.accumulatedText));
        }
      }
    };
    exports2.AnthropicProxyService = AnthropicProxyService;
  }
});

// out/services/sagemakerProxyService.js
var require_sagemakerProxyService = __commonJS({
  "out/services/sagemakerProxyService.js"(exports2) {
    "use strict";
    var __createBinding2 = exports2 && exports2.__createBinding || (Object.create ? (function(o, m, k, k2) {
      if (k2 === void 0) k2 = k;
      var desc = Object.getOwnPropertyDescriptor(m, k);
      if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
        desc = { enumerable: true, get: function() {
          return m[k];
        } };
      }
      Object.defineProperty(o, k2, desc);
    }) : (function(o, m, k, k2) {
      if (k2 === void 0) k2 = k;
      o[k2] = m[k];
    }));
    var __setModuleDefault2 = exports2 && exports2.__setModuleDefault || (Object.create ? (function(o, v) {
      Object.defineProperty(o, "default", { enumerable: true, value: v });
    }) : function(o, v) {
      o["default"] = v;
    });
    var __importStar2 = exports2 && exports2.__importStar || /* @__PURE__ */ (function() {
      var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function(o2) {
          var ar = [];
          for (var k in o2) if (Object.prototype.hasOwnProperty.call(o2, k)) ar[ar.length] = k;
          return ar;
        };
        return ownKeys(o);
      };
      return function(mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) {
          for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding2(result, mod, k[i]);
        }
        __setModuleDefault2(result, mod);
        return result;
      };
    })();
    Object.defineProperty(exports2, "__esModule", { value: true });
    exports2.SagemakerProxyService = void 0;
    var streamingProxyHelper_js_1 = require_streamingProxyHelper();
    var StreamResult;
    (function(StreamResult2) {
      StreamResult2["SUCCESS"] = "SUCCESS";
    })(StreamResult || (StreamResult = {}));
    var SagemakerProxyService = class {
      constructor() {
        this.streamingHelper = new streamingProxyHelper_js_1.StreamingProxyHelper();
      }
      /**
       * Process streaming requests with direct OutputStream callback
       * Used by the /ai/query endpoint for unified streaming
       */
      async processStreamingResponsesWithCallback(requestBody, user, originalHeaders, request_id, outputStream, originalRequest) {
        await this.processStreamingResponsesWithCallbackInternal(requestBody, user, originalHeaders, request_id, outputStream, originalRequest);
      }
      /**
       * Internal method with original request parameter for retry logic
       */
      async processStreamingResponsesWithCallbackInternal(requestBody, _user, _originalHeaders, request_id, outputStream, originalRequest) {
        const requestBodyJson = JSON.parse(requestBody);
        const sagemakerConfig = requestBodyJson.byok_keys?.sagemaker;
        const endpointName = sagemakerConfig?.endpointName;
        const region = sagemakerConfig?.region || "us-east-1";
        if (!endpointName) {
          throw new Error("SageMaker endpoint name not configured in BYOK settings.");
        }
        const awsCredentials = requestBodyJson.byok_keys?.aws;
        if (!awsCredentials || !awsCredentials.accessKeyId || !awsCredentials.secretAccessKey) {
          throw new Error("AWS credentials not found in request. Please ensure AWS credentials are properly configured.");
        }
        const disableInactivityTimeout = false;
        const inactivityTimeoutMs = 3e4;
        const sagemakerRequest = this.convertToSagemakerFormat(requestBodyJson);
        const AWS = await Promise.resolve().then(() => __importStar2(require("@aws-sdk/client-sagemaker-runtime")));
        const client = new AWS.SageMakerRuntimeClient({
          region,
          credentials: {
            accessKeyId: awsCredentials.accessKeyId,
            secretAccessKey: awsCredentials.secretAccessKey
          }
        });
        const streamState = {
          textContent: "",
          hasTextContent: false,
          hasFunctionCall: false,
          textStreamingComplete: false,
          firstDeltaAtMs: -1,
          lastDeltaAtMs: -1,
          deltaCount: 0,
          parallelFunctionCalls: /* @__PURE__ */ new Map(),
          hasParallelFunctionCalls: false,
          userStreamingStarted: false,
          originalRequest,
          modifiedRequest: null,
          cancelled: false,
          cancelledMessageLogged: false,
          writeErrorLogged: false,
          functionCallCompletionSent: false
        };
        let lastStreamEventTime = Date.now();
        let sseBuffer = "";
        try {
          const command = new AWS.InvokeEndpointWithResponseStreamCommand({
            EndpointName: endpointName,
            Body: JSON.stringify(sagemakerRequest),
            ContentType: "application/json"
          });
          const response = await client.send(command);
          if (!response.Body) {
            throw new Error("No response stream available from SageMaker");
          }
          await new Promise((resolve, reject) => {
            const timeoutInterval = setInterval(() => {
              const timeSinceLastEvent = Date.now() - lastStreamEventTime;
              if (!disableInactivityTimeout && timeSinceLastEvent > inactivityTimeoutMs) {
                const timeoutSeconds = Math.floor(inactivityTimeoutMs / 1e3);
                this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTimeoutEvent(request_id, "SageMaker", timeoutSeconds));
                clearInterval(timeoutInterval);
                resolve();
              }
            }, 1e3);
            (async () => {
              try {
                for await (const event of response.Body) {
                  if (streamState.cancelled) {
                    clearInterval(timeoutInterval);
                    resolve();
                    return;
                  }
                  const nowMs = Date.now();
                  lastStreamEventTime = nowMs;
                  if (event.PayloadPart?.Bytes) {
                    const chunk = new TextDecoder().decode(event.PayloadPart.Bytes);
                    sseBuffer += chunk;
                    while (sseBuffer.includes("\n\n")) {
                      const eventEnd = sseBuffer.indexOf("\n\n");
                      const eventBlock = sseBuffer.substring(0, eventEnd);
                      sseBuffer = sseBuffer.substring(eventEnd + 2);
                      if (eventBlock.trim()) {
                        const lines = eventBlock.split("\n");
                        let eventData = null;
                        for (const line of lines) {
                          if (line.startsWith("data: ")) {
                            eventData = line.substring(6).trim();
                          }
                        }
                        if (eventData && eventData !== "[DONE]") {
                          try {
                            const jsonData = JSON.parse(eventData);
                            this.processStreamingChunk(jsonData, request_id, outputStream, streamState, originalRequest);
                          } catch (e) {
                          }
                        }
                      }
                    }
                  }
                }
                clearInterval(timeoutInterval);
                try {
                  this.handleStreamCompletion(request_id, outputStream, streamState);
                } catch (e) {
                  console.error("Error in SageMaker stream completion:", e.message);
                }
                resolve();
              } catch (error) {
                clearInterval(timeoutInterval);
                let isCancellation = false;
                const errorMessage = error.message;
                if (errorMessage && (errorMessage.includes("Connection reset") || errorMessage.includes("Connection closed") || errorMessage.includes("cancelled"))) {
                  isCancellation = true;
                }
                if (isCancellation) {
                  streamState.cancelled = true;
                  resolve();
                  return;
                }
                try {
                  let finalErrorMessage = "SageMaker stream error: " + errorMessage;
                  if (errorMessage.includes("ValidationException")) {
                    finalErrorMessage = "Invalid request to SageMaker endpoint";
                  } else if (errorMessage.includes("ModelError")) {
                    finalErrorMessage = "SageMaker model error - check model configuration";
                  } else if (errorMessage.includes("ServiceUnavailable")) {
                    finalErrorMessage = "SageMaker endpoint temporarily unavailable";
                  } else if (errorMessage.includes("ThrottlingException")) {
                    finalErrorMessage = "SageMaker request throttled - too many requests";
                  }
                  this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createErrorEvent(request_id, finalErrorMessage));
                } catch (e) {
                  console.error("Could not send error to client:", e.message);
                }
                reject(error);
              }
            })();
          });
        } catch (error) {
          let errorMessage = error.message || "Unknown SageMaker error";
          if (error.name === "ValidationException") {
            errorMessage = "SageMaker endpoint validation failed - check configuration";
          } else if (error.name === "ResourceNotFound") {
            errorMessage = "SageMaker endpoint not found - check endpoint name and region";
          } else if (error.name === "AccessDeniedException") {
            errorMessage = "Access denied to SageMaker endpoint - check AWS credentials and permissions";
          } else if (error.name === "ThrottlingException") {
            errorMessage = "SageMaker requests are being throttled - reduce request frequency";
          } else if (error.name === "ServiceUnavailableException") {
            errorMessage = "SageMaker service temporarily unavailable";
          }
          this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createErrorEvent(request_id, errorMessage));
        }
        return StreamResult.SUCCESS;
      }
      convertToSagemakerFormat(openaiRequest) {
        const result = {
          model: openaiRequest.model,
          messages: openaiRequest.messages,
          max_tokens: openaiRequest.max_tokens,
          temperature: openaiRequest.temperature,
          stream: true
        };
        if (openaiRequest.tools && openaiRequest.tools.length > 0) {
          result.tools = openaiRequest.tools;
          result.tool_choice = openaiRequest.tool_choice || "auto";
        }
        return result;
      }
      /**
       * Handle stream completion - send any remaining content
       */
      handleStreamCompletion(request_id, outputStream, streamState) {
        if (streamState.cancelled) {
          return;
        }
        if (streamState.hasTextContent && !streamState.textStreamingComplete && streamState.textContent.length > 0) {
          this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTextCompleteEvent(request_id, streamState.textContent));
        }
      }
      /**
       * Process individual streaming chunks and send appropriate SSE events
       * Based on OpenAI chunk processing but adapted for SageMaker OpenAI-compatible format
       */
      processStreamingChunk(chunkNode, request_id, outputStream, streamState, _originalRequest) {
        if (streamState.cancelled) {
          if (!streamState.cancelledMessageLogged) {
            streamState.cancelledMessageLogged = true;
          }
          return;
        }
        if (chunkNode.choices && chunkNode.choices.length > 0) {
          const choice = chunkNode.choices[0];
          if (choice.delta) {
            if (choice.delta.content) {
              const now = Date.now();
              if (streamState.firstDeltaAtMs < 0)
                streamState.firstDeltaAtMs = now;
              streamState.lastDeltaAtMs = now;
              streamState.deltaCount++;
              const delta = choice.delta.content;
              streamState.textContent += delta;
              streamState.hasTextContent = true;
              streamState.userStreamingStarted = true;
              const textDeltaEvent = this.streamingHelper.createTextDeltaEvent(request_id, delta);
              if (!this.streamingHelper.safeWriteToOutputStream(outputStream, textDeltaEvent)) {
                streamState.cancelled = true;
                return;
              }
            }
            if (choice.delta.tool_calls) {
              const now = Date.now();
              if (streamState.firstDeltaAtMs < 0)
                streamState.firstDeltaAtMs = now;
              streamState.lastDeltaAtMs = now;
              streamState.deltaCount++;
              for (const toolCall of choice.delta.tool_calls) {
                const index = toolCall.index || 0;
                const callKey = `call_${index}`;
                if (!streamState.parallelFunctionCalls.has(callKey)) {
                  streamState.parallelFunctionCalls.set(callKey, {
                    functionName: "",
                    callId: toolCall.id || callKey,
                    // Store actual ID when available
                    functionArguments: "",
                    argumentsComplete: false,
                    functionCallCompletionSent: false
                  });
                  streamState.hasParallelFunctionCalls = true;
                }
                const functionCall = streamState.parallelFunctionCalls.get(callKey);
                if (toolCall.function) {
                  if (toolCall.function.name) {
                    functionCall.functionName += toolCall.function.name;
                  }
                  if (toolCall.function.arguments) {
                    const argDelta = toolCall.function.arguments;
                    functionCall.functionArguments += argDelta;
                    if (functionCall.functionName === "search_replace" || functionCall.functionName === "run_console_cmd" || functionCall.functionName === "run_terminal_cmd") {
                      if (!this.streamingHelper.sendStreamingFunctionDelta(request_id, outputStream, functionCall.functionName, functionCall.callId, argDelta, streamState)) {
                        streamState.cancelled = true;
                        return;
                      }
                    }
                  }
                }
              }
            }
          }
          if (choice.finish_reason) {
            if (choice.finish_reason === "tool_calls" && streamState.hasParallelFunctionCalls) {
              if (!this.completeTextStreamingIfNeeded(request_id, outputStream, streamState.textContent, streamState.hasTextContent, streamState.textStreamingComplete, streamState.userStreamingStarted, streamState)) {
                return;
              }
              streamState.textStreamingComplete = true;
              for (const [, functionCall] of streamState.parallelFunctionCalls) {
                if (!functionCall.functionCallCompletionSent && functionCall.functionName) {
                  if (!this.handleFunctionCallCompletion(request_id, outputStream, functionCall.functionName, functionCall.callId, functionCall.functionArguments, streamState.originalRequest, streamState, functionCall.functionCallCompletionSent)) {
                    return;
                  }
                  if (functionCall.functionName === "search_replace" || functionCall.functionName === "run_console_cmd" || functionCall.functionName === "run_terminal_cmd") {
                    functionCall.functionCallCompletionSent = true;
                  }
                }
              }
              streamState.hasFunctionCall = true;
            } else if (choice.finish_reason === "stop") {
              if (streamState.hasTextContent && !streamState.hasFunctionCall && !streamState.textStreamingComplete) {
                if (this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTextCompleteEvent(request_id, streamState.textContent))) {
                  streamState.textStreamingComplete = true;
                }
              }
            }
          }
        } else {
          console.warn("SageMaker chunk without 'choices' field:", JSON.stringify(chunkNode));
        }
      }
      /**
       * Handle function call completion with all the version-specific logic
       * This consolidates the repetitive completion logic found in both OpenAI and Anthropic services
       */
      handleFunctionCallCompletion(request_id, outputStream, functionName, callId, functionArguments, originalRequest, streamState, functionCallCompletionSent) {
        return this.streamingHelper.handleFunctionCallCompletion(request_id, outputStream, functionName, callId, functionArguments, originalRequest, streamState, functionCallCompletionSent);
      }
      /**
       * Complete text streaming if needed
       */
      completeTextStreamingIfNeeded(request_id, outputStream, textContent, hasTextContent, textStreamingComplete, userStreamingStarted, _streamState) {
        if (hasTextContent && !textStreamingComplete && userStreamingStarted) {
          if (!this.streamingHelper.safeWriteToOutputStream(outputStream, this.streamingHelper.createTextCompleteEvent(request_id, textContent))) {
            return false;
          }
        }
        return true;
      }
    };
    exports2.SagemakerProxyService = SagemakerProxyService;
  }
});

// out/services/localBackendService.js
var require_localBackendService = __commonJS({
  "out/services/localBackendService.js"(exports2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", { value: true });
    exports2.LocalBackendService = void 0;
    var streamingService_js_1 = require_streamingService();
    var openAiProxyService_js_1 = require_openAiProxyService();
    var anthropicProxyService_js_1 = require_anthropicProxyService();
    var sagemakerProxyService_js_1 = require_sagemakerProxyService();
    var LocalBackendService = class _LocalBackendService {
      constructor(functionDefinitionService) {
        this.functionDefinitionService = functionDefinitionService;
        this.streamingService = new streamingService_js_1.StreamingService();
        this.openAiProxyService = new openAiProxyService_js_1.OpenAiProxyService();
        this.anthropicProxyService = new anthropicProxyService_js_1.AnthropicProxyService();
        this.sagemakerProxyService = new sagemakerProxyService_js_1.SagemakerProxyService();
      }
      /**
       * Load developer instructions for VSCode
       */
      async loadDeveloperInstructions(model) {
        try {
          let instructions = await this.functionDefinitionService.loadDeveloperInstructions(model);
          return instructions;
        } catch (e) {
          throw new Error(`Failed to load developer instructions from app-configs/vscode/developer-instructions.txt: ${e}`);
        }
      }
      /**
       * Helper method to select appropriate cheap model for cost optimization
       */
      selectCheapModel(provider) {
        switch (provider) {
          case "openai":
            return _LocalBackendService.OPENAI_CHEAP_MODEL;
          case "anthropic":
            return _LocalBackendService.ANTHROPIC_CHEAP_MODEL;
          case "sagemaker":
            return _LocalBackendService.SAGEMAKER_MODEL;
          default:
            throw new Error(`Unsupported provider: ${provider}. Supported providers: openai, anthropic, sagemaker`);
        }
      }
      /**
       * Some OpenAI models do not accept the 'temperature' parameter
       */
      isOpenAiTemperatureSupported(model) {
        if (!model)
          return false;
        switch (model) {
          case "gpt-4o":
          case "gpt-4o-mini":
          case "gpt-4.1":
          case "gpt-4.1-mini":
            return true;
          default:
            return false;
        }
      }
      /**
       * Check if this is a reasoning model
       */
      isReasoningModel(model) {
        if (!model)
          return false;
        return model.startsWith("o1") || model === "o3" || model.startsWith("o3-") || model.startsWith("o4-");
      }
      /**
       * Escape JSON special characters
       */
      escapeJson(value) {
        if (!value)
          return "";
        return value.replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\n/g, "\\n").replace(/\r/g, "\\r").replace(/\t/g, "\\t");
      }
      /**
       * Helper method for common request validation
       */
      validateBasicRequest(messages) {
        if (!messages || messages.length === 0) {
          return "The conversation received by the backend was empty. A conversation is required. Try opening a new conversation.";
        }
        return null;
      }
      /**
       * Send a Server-Sent Event for streaming
       */
      sendSseEvent(outputStream, request_id, field, value, delta, isComplete = false) {
        const eventData = {
          request_id,
          isComplete
        };
        if (delta !== void 0) {
          eventData.delta = this.escapeJson(delta);
          eventData.field = field;
        } else {
          eventData[field] = this.escapeJson(value);
        }
        outputStream.write(`data: ${JSON.stringify(eventData)}

`);
      }
      /**
       * Check if we need to add a reminder message for two consecutive assistant messages
       */
      needsEndTurnReminder(messages) {
        if (messages.length < 2) {
          return false;
        }
        const lastMessage = messages[messages.length - 1];
        const secondToLastMessage = messages[messages.length - 2];
        return this.isAssistantMessageWithoutFunctionCall(lastMessage) && this.isAssistantMessageWithoutFunctionCall(secondToLastMessage);
      }
      /**
       * Check if a message is an assistant message without function calls
       */
      isAssistantMessageWithoutFunctionCall(message) {
        return message.role === "assistant" && !message.function_call && message.content !== void 0;
      }
      /**
       * Determines if this is the first user message in a conversation
       */
      isFirstMessageInConversation(messages) {
        if (!messages || messages.length === 0) {
          return true;
        }
        let userMessageCount = 0;
        let hasAssistantResponse = false;
        for (const msg of messages) {
          if (msg.role) {
            if (msg.role === "user" && msg.content !== void 0) {
              const isProcedural = msg.procedural === true;
              if (!isProcedural) {
                userMessageCount++;
              }
            }
            if (msg.role === "assistant") {
              hasAssistantResponse = true;
            }
          }
        }
        return userMessageCount <= 1 && !hasAssistantResponse;
      }
      /**
       * Main tools method - get API tools for conversation
       */
      getApiTools(conversation, symbolsNote, isConversationNameRequest, provider, webSearchEnabled, interactionMode = "ask") {
        const tools = [];
        if (isConversationNameRequest) {
          return tools;
        }
        if (webSearchEnabled) {
          let webSearchTool;
          if (provider === "anthropic") {
            webSearchTool = {
              type: "web_search_20250305",
              name: "web_search",
              max_uses: 5
            };
          } else if (provider === "openai") {
            webSearchTool = {
              type: "web_search"
            };
          }
          if (webSearchTool) {
            tools.push(webSearchTool);
          }
        }
        const readOnlyFunctions = [
          "grep",
          "list_dir",
          // Always included
          "search_for_file",
          // Always included (fuzzy file search)
          "read_file",
          // Always included
          "retrieve_documentation"
          // Always included (documentation retrieval)
        ];
        const writeFunctions = [
          "search_replace",
          // Added for find-replace functionality
          "run_terminal_cmd",
          // Always included
          "run_console_cmd",
          // Always included
          "delete_file",
          // Always included  
          "run_file"
          // Always included
        ];
        const standardFunctions = [...readOnlyFunctions];
        if (interactionMode && interactionMode.toLowerCase() === "agent") {
          standardFunctions.push(...writeFunctions);
        }
        if (provider !== "anthropic") {
          standardFunctions.push("end_turn");
        }
        const standardTools = this.functionDefinitionService.getFunctionsByNames(standardFunctions);
        tools.push(...standardTools);
        const conditionalFunctions = [];
        if (this.hasImageFileInConversationOrSymbols(conversation, symbolsNote)) {
          conditionalFunctions.push("view_image");
        }
        if (conditionalFunctions.length > 0) {
          const conditionalTools = this.functionDefinitionService.getFunctionsByNames(conditionalFunctions);
          tools.push(...conditionalTools);
        }
        return tools;
      }
      /**
       * Check if there's an image file mentioned in conversation history or symbols note
       */
      hasImageFileInConversationOrSymbols(conversation, symbolsNote) {
        for (const message of conversation) {
          if (this.hasImageFileInMessage(message)) {
            return true;
          }
        }
        if (symbolsNote) {
          try {
            const parsedSymbolsNote = JSON.parse(symbolsNote);
            return this.hasImageFileInSymbolsNote(parsedSymbolsNote);
          } catch (e) {
          }
        }
        return false;
      }
      /**
       * Check if a message contains references to image files
       */
      hasImageFileInMessage(message) {
        if (message.content === void 0) {
          return false;
        }
        let contentText = "";
        if (typeof message.content === "string") {
          contentText = message.content;
        } else {
          const apiMsg = message;
          if (Array.isArray(apiMsg.content)) {
            const textParts = [];
            for (const item of apiMsg.content) {
              if (typeof item === "object" && item !== null && "text" in item && item.text) {
                textParts.push(item.text);
              }
            }
            contentText = textParts.join(" ");
          }
        }
        return /\.(png|jpg|jpeg|gif|bmp|svg|webp|tiff|tif)\b/i.test(contentText);
      }
      /**
       * Check if symbols note contains image files
       */
      hasImageFileInSymbolsNote(symbolsNote) {
        if (symbolsNote.open_files) {
          for (const file of symbolsNote.open_files) {
            if (file.name && /\.(png|jpg|jpeg|gif|bmp|svg|webp|tiff|tif)\b/i.test(file.name)) {
              return true;
            }
          }
        }
        if (symbolsNote.direct_context) {
          for (const item of symbolsNote.direct_context) {
            if (item.path && /\.(png|jpg|jpeg|gif|bmp|svg|webp|tiff|tif)\b/i.test(item.path)) {
              return true;
            }
          }
        }
        if (symbolsNote.direct_context) {
          for (const item of symbolsNote.direct_context) {
            if (item.path && /\.(png|jpg|jpeg|gif|bmp|svg|webp|tiff|tif)\b/i.test(item.path)) {
              return true;
            }
          }
        }
        return false;
      }
      /**
       * Add image context messages to the conversation
       */
      addImageContextMessages(conversation, imageContext) {
        if (!imageContext || imageContext.length === 0) {
          return;
        }
        let originalQueryMessageIndex = -1;
        for (let i = conversation.length - 1; i >= 0; i--) {
          const message = conversation[i];
          if (message.role === "user" && !message.function_call && message.original_query === true) {
            originalQueryMessageIndex = i;
            break;
          }
        }
        if (originalQueryMessageIndex === -1) {
          console.warn("DEBUG IMAGE FLOW: No original_query user message found to insert images before");
          return;
        }
        for (let i = 0; i < imageContext.length; i++) {
          const imageData = imageContext[i];
          const imageMessage = {
            id: Date.now() + i,
            // Ensure unique IDs
            role: "user",
            timestamp: (/* @__PURE__ */ new Date()).toISOString(),
            content: [
              {
                type: "input_image",
                image_url: `data:${imageData.mime_type};base64,${imageData.base64_data}`
              }
            ]
          };
          conversation.splice(originalQueryMessageIndex + i, 0, imageMessage);
        }
      }
      /**
       * Add user rules as a separate user message before the original query message
       */
      addUserRulesMessage(conversation, userRules) {
        if (!userRules || userRules.length === 0) {
          return;
        }
        let originalQueryMessageIndex = -1;
        for (let i = conversation.length - 1; i >= 0; i--) {
          const message = conversation[i];
          if (message.role === "user" && !message.function_call && message.original_query === true) {
            originalQueryMessageIndex = i;
            break;
          }
        }
        if (originalQueryMessageIndex === -1) {
          console.warn("No original_query user message found to insert user rules before");
          return;
        }
        const rulesContent = [
          "# USER_RULES",
          "User rules are provided instructions for the AI to follow to help work with the codebase.",
          "They may or may not be relevant to the task at hand.",
          "",
          ...userRules
        ].join("\n");
        const rulesMessage = {
          id: Date.now(),
          role: "user",
          timestamp: (/* @__PURE__ */ new Date()).toISOString(),
          content: rulesContent
        };
        conversation.splice(originalQueryMessageIndex, 0, rulesMessage);
      }
      /**
       * Add user environment info to the beginning of the original query message
       */
      addUserInfoToOriginalQuery(conversation, userWorkspacePath, userShell, projectLayout) {
        for (let i = conversation.length - 1; i >= 0; i--) {
          const message = conversation[i];
          if (message.role === "user" && !message.function_call && message.original_query === true) {
            let originalUserQuery = "";
            if (typeof message.content === "string") {
              originalUserQuery = message.content;
            } else {
              const apiMsg = message;
              if (Array.isArray(apiMsg.content) && apiMsg.content.length > 0) {
                const firstItem = apiMsg.content[0];
                if (typeof firstItem === "object" && firstItem !== null && "type" in firstItem && firstItem.type === "text") {
                  originalUserQuery = firstItem.text || "";
                }
              }
            }
            const userInfo = [
              "<user_info>",
              `The absolute path of the user's workspace is ${userWorkspacePath || "/home/byte/code/ai-dashboard"}. The user's shell is ${userShell || "/usr/bin/fish"}.`,
              "</user_info>",
              ""
            ].join("\n");
            let projectLayoutSection = "";
            if (projectLayout && projectLayout.trim()) {
              projectLayoutSection = [
                "<project_layout>",
                "Below is a snapshot of the current workspace's file structure when the user made the most recent query. It skips over .gitignore patterns.",
                "",
                projectLayout,
                "</project_layout>",
                ""
              ].join("\n");
            }
            const modifiedContent = userInfo + projectLayoutSection + originalUserQuery;
            if (typeof message.content === "string") {
              message.content = modifiedContent;
            } else {
              const apiMsg = message;
              if (Array.isArray(apiMsg.content) && apiMsg.content.length > 0) {
                const firstItem = apiMsg.content[0];
                if (typeof firstItem === "object" && firstItem !== null && "type" in firstItem && firstItem.type === "text") {
                  firstItem.text = modifiedContent;
                }
              }
            }
            break;
          }
        }
      }
      /**
       * Modify the last user message with symbols note
       */
      modifyLastUserMessageWithSymbolsNote(conversation, symbolsNoteJson) {
        let foundOriginalQuery = false;
        for (let i = conversation.length - 1; i >= 0; i--) {
          const message = conversation[i];
          if (message.role === "user" && !message.function_call && message.original_query === true) {
            let originalUserQuery = "";
            if (typeof message.content === "string") {
              originalUserQuery = message.content;
            } else {
              const apiMsg = message;
              if (Array.isArray(apiMsg.content) && apiMsg.content.length > 0) {
                const firstItem = apiMsg.content[0];
                if (typeof firstItem === "object" && firstItem !== null && "type" in firstItem && firstItem.type === "text") {
                  originalUserQuery = firstItem.text || "";
                }
              }
            }
            const formattedNote = this.formatSymbolsNote(JSON.parse(symbolsNoteJson), originalUserQuery);
            if (typeof message.content === "string") {
              message.content = formattedNote;
            } else {
              const apiMsg = message;
              if (Array.isArray(apiMsg.content) && apiMsg.content.length > 0) {
                const firstItem = apiMsg.content[0];
                if (typeof firstItem === "object" && firstItem !== null && "type" in firstItem && firstItem.type === "text") {
                  firstItem.text = formattedNote;
                }
              }
            }
            foundOriginalQuery = true;
            break;
          }
        }
        if (!foundOriginalQuery) {
          console.warn("No original_query user message found to modify with symbols note");
        }
      }
      /**
       * Format symbols note with user query
       */
      formatSymbolsNote(symbolsNote, userQuery) {
        const formatted = [];
        formatted.push("\n\n<context>\n");
        if (symbolsNote.open_files && symbolsNote.open_files.length > 0) {
          formatted.push("\n# Files");
          formatted.push("The following files are open in the editor, last edited this many minutes ago:");
          for (const file of symbolsNote.open_files) {
            let displayName = file.path;
            if (!displayName) {
              displayName = file.name;
              if (!displayName) {
                continue;
              }
            }
            const prefix = file.is_active ? "Currently viewing: " : "";
            formatted.push(`${prefix}${displayName} (${Math.round(file.minutes_since_last_update)})`);
          }
          formatted.push("");
        }
        if (symbolsNote.directContext && symbolsNote.directContext.length > 0) {
          formatted.push("\n# User-provided context");
          formatted.push("The user manually provided the following as context:\n");
          for (const item of symbolsNote.directContext) {
            if (item.type === "directory") {
              formatted.push(item.path);
              if (item.contents) {
                formatted.push("Contents:");
                for (const content of item.contents) {
                  formatted.push(`  - ${content}`);
                }
              }
              formatted.push("");
            } else if (item.type === "file") {
              if (item.content) {
                if (item.startLine !== void 0 && item.endLine !== void 0) {
                  formatted.push(`${item.path} (lines ${item.startLine}-${item.endLine}):`);
                } else {
                  formatted.push(`${item.path}:`);
                }
                const fileName = item.name.toLowerCase();
                const isMarkdownFile = fileName.endsWith(".rmd") || fileName.endsWith(".md") || fileName.endsWith(".qmd") || fileName.endsWith(".markdown");
                const codeBlockMarker = isMarkdownFile ? "````" : "```";
                formatted.push(codeBlockMarker);
                const lines = item.content.toString().split("\n");
                for (const line of lines) {
                  formatted.push(line);
                }
                formatted.push(codeBlockMarker);
              } else {
                formatted.push(`${item.path}`);
              }
              formatted.push("");
            } else if (item.type === "chat") {
              if (item.id && item.summary) {
                formatted.push(`Previous conversation ${item.id}:`);
                formatted.push("```");
                formatted.push(item.summary);
                formatted.push("```\n");
              }
            } else if (item.type === "docs") {
              if (item.topic && item.markdown) {
                formatted.push(`R Documentation for ${item.topic}:`);
                formatted.push(`${item.markdown}
`);
              }
            }
          }
        }
        formatted.push("</context>\n\n");
        formatted.push("<user_query>");
        formatted.push(`${userQuery}
`);
        formatted.push("</user_query>");
        return formatted.join("\n");
      }
      /**
       * Build OpenAI request parameters
       */
      async buildOpenAIRequestParams(conversation, model, request, symbolsNote, webSearchEnabled) {
        const isConversationNameRequest = request.request_type === "generate_conversation_name";
        const isNamingRequest = isConversationNameRequest;
        const apiConversationLog = [];
        if (!isNamingRequest) {
          const developerMessage = {
            role: "developer",
            content: await this.loadDeveloperInstructions(model)
          };
          apiConversationLog.push(developerMessage);
        }
        if (request.previous_summary) {
          const summaryMessage = {
            role: "system",
            content: `<previous_conversation_summary>
(Query ${request.previous_summary.query_number} - ${request.previous_summary.timestamp}):

${request.previous_summary.summary_text}
</previous_conversation_summary>
`
          };
          apiConversationLog.push(summaryMessage);
        }
        for (let msgIndex = 0; msgIndex < conversation.length; msgIndex++) {
          const msg = conversation[msgIndex];
          const processedMsg = {};
          if (msg.type === "function_call_output") {
            processedMsg.type = "function_call_output";
            const callId = msg.call_id;
            processedMsg.call_id = callId;
            let output = msg.output || "";
            processedMsg.output = output;
            apiConversationLog.push(processedMsg);
            continue;
          }
          if (msg.function_call) {
            const functionCall = msg.function_call;
            const functionCallMessage = {
              type: "function_call",
              name: functionCall.name
            };
            if (functionCall.call_id) {
              functionCallMessage.call_id = functionCall.call_id;
            }
            if (functionCall.arguments) {
              functionCallMessage.arguments = functionCall.arguments;
            }
            apiConversationLog.push(functionCallMessage);
            continue;
          }
          if (msg.content !== void 0 && msg.role) {
            processedMsg.role = msg.role;
            if (typeof msg.content === "string") {
              let textContent = msg.content;
              if (msg.cancelled) {
                textContent += "... (User cancelled)";
              }
              processedMsg.content = textContent;
            } else {
              const apiMsg = msg;
              if (Array.isArray(apiMsg.content)) {
                const formattedContent = [];
                for (const item of apiMsg.content) {
                  if (typeof item === "object" && item !== null && "type" in item) {
                    const contentItem = {};
                    const itemType = item.type;
                    if (itemType === "input_text") {
                      const textContent = item.text;
                      contentItem.type = "input_text";
                      contentItem.text = textContent;
                      formattedContent.push(contentItem);
                    } else if (itemType === "input_image") {
                      if ("image_url" in item && typeof item.image_url === "string") {
                        contentItem.type = "input_image";
                        contentItem.image_url = item.image_url;
                        formattedContent.push(contentItem);
                      }
                    } else if (itemType === "text") {
                      const textContent = item.text;
                      contentItem.type = "input_text";
                      contentItem.text = textContent;
                      formattedContent.push(contentItem);
                    } else if (itemType === "image_url") {
                      if ("image_url" in item) {
                        contentItem.type = "image_url";
                        contentItem.image_url = item.image_url;
                        formattedContent.push(contentItem);
                      }
                    }
                  }
                }
                processedMsg.content = formattedContent;
              } else {
                processedMsg.content = msg.content;
              }
            }
            apiConversationLog.push(processedMsg);
          }
        }
        const apiParams = {
          input: apiConversationLog,
          model
        };
        if (request.temperature !== void 0 && this.isOpenAiTemperatureSupported(model)) {
          apiParams.temperature = request.temperature;
        }
        if (request.previous_response_id && this.isReasoningModel(model) && !this.isFirstMessageInConversation(conversation)) {
          apiParams.previous_response_id = request.previous_response_id;
        }
        const tools = this.getApiTools(
          conversation,
          symbolsNote,
          isConversationNameRequest,
          "openai",
          // Provider for OpenAI calls
          webSearchEnabled,
          request.interaction_mode || "ask"
        );
        if (tools.length > 0) {
          apiParams.tools = tools;
          const enableParallelCalls = true;
          apiParams.parallel_tool_calls = enableParallelCalls;
        }
        if (model === "gpt-5-mini") {
          apiParams.reasoning = { effort: "medium" };
          apiParams.text = { verbosity: "medium" };
        }
        return apiParams;
      }
      /**
       * Build Anthropic request parameters
       */
      async buildAnthropicRequestParams(conversation, model, request, symbolsNote, webSearchEnabled) {
        const isConversationNameRequest = request.request_type === "generate_conversation_name";
        const isNamingRequest = isConversationNameRequest;
        const isSummarizationRequest = request.request_type === "summarize_conversation";
        const apiParams = {
          model,
          max_tokens: 8192,
          stream: true
          // Enable streaming
        };
        if (request.temperature !== void 0 && this.isOpenAiTemperatureSupported(model)) {
          apiParams.temperature = request.temperature;
        }
        if (!isNamingRequest) {
          let systemPrompt = await this.loadDeveloperInstructions(model);
          if (request.previous_summary) {
            systemPrompt += `

<previous_conversation_summary>
(Query ${request.previous_summary.query_number} - ${request.previous_summary.timestamp}):

${request.previous_summary.summary_text}
</previous_conversation_summary>
`;
          }
          if (model && model.startsWith("claude-") && !isSummarizationRequest) {
            const systemArray = [{
              type: "text",
              text: systemPrompt,
              cache_control: { type: "ephemeral" }
            }];
            apiParams.system = systemArray;
          } else {
            apiParams.system = systemPrompt;
          }
        }
        const messages = [];
        let lastOriginalQueryIndex = -1;
        if (!isSummarizationRequest) {
          for (let i = conversation.length - 1; i >= 0; i--) {
            const msg = conversation[i];
            if (msg.role === "user" && msg.original_query === true) {
              lastOriginalQueryIndex = i;
              break;
            }
          }
        }
        for (let msgIndex = 0; msgIndex < conversation.length; msgIndex++) {
          const msg = conversation[msgIndex];
          if (msg.type === "function_call_output") {
            const callId = msg.call_id;
            let output = msg.output || "";
            const message = {
              role: "user",
              content: [{
                type: "tool_result",
                tool_use_id: callId,
                content: output
              }]
            };
            messages.push(message);
            continue;
          }
          if (!msg.role) {
            continue;
          }
          if (msg.role === "assistant" && msg.function_call) {
            const functionCall = msg.function_call;
            const toolName = functionCall.name;
            const toolCallId = functionCall.call_id;
            const toolUse = {
              type: "tool_use",
              id: toolCallId,
              name: toolName
            };
            if (functionCall.arguments) {
              try {
                const args = JSON.parse(functionCall.arguments);
                toolUse.input = args;
              } catch (e) {
                toolUse.input = {};
              }
            } else {
              toolUse.input = {};
            }
            const message = {
              role: "assistant",
              content: [toolUse]
            };
            messages.push(message);
            continue;
          }
          if (msg.role && msg.content !== void 0) {
            const message = {
              role: msg.role
            };
            const isLastOriginalQuery = msgIndex === lastOriginalQueryIndex && msg.role === "user";
            const isLastUserMessage = msgIndex === conversation.length - 1 && msg.role === "user";
            const shouldCache = !isSummarizationRequest && (isLastOriginalQuery || isLastUserMessage && !isLastOriginalQuery);
            if (typeof msg.content === "string") {
              let textContent = msg.content;
              if (msg.cancelled) {
                textContent += "... (User cancelled)";
              }
              if (shouldCache) {
                message.content = [{
                  type: "text",
                  text: textContent,
                  cache_control: { type: "ephemeral" }
                }];
              } else {
                message.content = textContent;
              }
            } else {
              const apiMsg = msg;
              if (Array.isArray(apiMsg.content)) {
                const contentList = [];
                for (const item of apiMsg.content) {
                  if (typeof item === "object" && item !== null && "type" in item) {
                    const itemType = item.type;
                    if (itemType === "input_text" || itemType === "text") {
                      let textContent = item.text;
                      if (msg.cancelled) {
                        textContent += "... (User cancelled)";
                      }
                      contentList.push({
                        type: "text",
                        text: textContent
                      });
                    } else if (itemType === "input_image") {
                      const imageItem = item;
                      if (imageItem.image_url) {
                        const imageUrl = imageItem.image_url;
                        if (imageUrl && imageUrl.startsWith("data:")) {
                          try {
                            const commaIndex = imageUrl.indexOf(",");
                            if (commaIndex !== -1) {
                              const metadata = imageUrl.substring(5, commaIndex);
                              const base64Data = imageUrl.substring(commaIndex + 1);
                              const mediaType = metadata.split(";")[0];
                              if (["image/jpeg", "image/png", "image/gif", "image/webp"].includes(mediaType)) {
                                contentList.push({
                                  type: "image",
                                  source: {
                                    type: "base64",
                                    media_type: mediaType,
                                    data: base64Data
                                  }
                                });
                              } else {
                                throw new Error(`Unsupported image media type for Anthropic: ${mediaType}. Supported types: image/jpeg, image/png, image/gif, image/webp`);
                              }
                            }
                          } catch (e) {
                            throw new Error(`Failed to parse image for Anthropic: ${e}`);
                          }
                        } else {
                          throw new Error(`Invalid image format for Anthropic: ${imageUrl}`);
                        }
                      }
                    }
                  }
                }
                if (shouldCache && contentList.length > 0) {
                  for (let i = contentList.length - 1; i >= 0; i--) {
                    const block = contentList[i];
                    if (block.type === "text") {
                      block.cache_control = { type: "ephemeral" };
                      break;
                    }
                  }
                }
                message.content = contentList;
              } else {
                message.content = String(msg.content);
              }
            }
            messages.push(message);
          }
        }
        apiParams.messages = messages;
        if (!isNamingRequest) {
          const tools = this.getApiTools(conversation, symbolsNote, isConversationNameRequest, "anthropic", webSearchEnabled, request.interaction_mode || "ask");
          const toolsList = [];
          for (const tool of tools) {
            if (tool.type) {
              const toolType = tool.type;
              if (["web_search_20250305", "web_search", "file_search"].includes(toolType)) {
                toolsList.push(tool);
                continue;
              }
            }
            const anthropicTool = {};
            if (tool.name) {
              anthropicTool.name = tool.name;
            }
            if (tool.description) {
              anthropicTool.description = tool.description;
            }
            if (tool.parameters) {
              const inputSchema = {};
              if (tool.parameters.type) {
                inputSchema.type = tool.parameters.type;
              }
              if (tool.parameters.properties) {
                inputSchema.properties = tool.parameters.properties;
              }
              if (tool.parameters.required) {
                inputSchema.required = tool.parameters.required;
              }
              anthropicTool.input_schema = inputSchema;
            }
            toolsList.push(anthropicTool);
          }
          if (toolsList.length > 0) {
            apiParams.tools = toolsList;
          }
        }
        return apiParams;
      }
      /**
       * Main streaming method - entry point for all API calls
       */
      async makeAiApiCallStreaming(request, request_id, outputStream, webSearchEnabled) {
        const validationError = this.validateBasicRequest(request.conversation || []);
        if (validationError) {
          this.sendSseEvent(outputStream, request_id, "error", validationError, void 0, true);
          return;
        }
        const conversation = request.conversation || [];
        const provider = request.provider;
        const model = request.model;
        if (this.needsEndTurnReminder(conversation)) {
          const event = {
            request_id,
            end_turn: true,
            isComplete: true
          };
          outputStream.write(`data: ${JSON.stringify(event)}

`);
          return;
        }
        const actualProvider = provider;
        const symbolsNote = request.symbols_note;
        if (symbolsNote?.attached_images?.length > 0) {
          this.addImageContextMessages(conversation, symbolsNote.attached_images);
        }
        if (request.user_rules?.length > 0) {
          this.addUserRulesMessage(conversation, request.user_rules);
        }
        let symbolsNoteString = null;
        if (symbolsNote) {
          symbolsNoteString = JSON.stringify(symbolsNote);
          this.modifyLastUserMessageWithSymbolsNote(conversation, symbolsNoteString);
        }
        this.addUserInfoToOriginalQuery(conversation, request.user_workspace_path, request.user_shell, request.project_layout);
        const updatedConversation = conversation;
        if (provider === "openai") {
          await this.callOpenAIStreaming(updatedConversation, model, request, symbolsNoteString, request_id, outputStream, webSearchEnabled);
        } else if (provider === "anthropic") {
          await this.callAnthropicStreaming(updatedConversation, model, request, symbolsNoteString, request_id, outputStream, webSearchEnabled);
        } else if (provider === "sagemaker") {
          await this.callSagemakerStreaming(updatedConversation, model, request, symbolsNoteString, request_id, outputStream);
        } else {
          this.sendSseEvent(outputStream, request_id, "error", `Unsupported provider: ${provider} (actualProvider: ${actualProvider}). Supported providers: openai, anthropic, sagemaker`, void 0, true);
        }
      }
      /**
       * Streaming version of callOpenAI
       */
      async callOpenAIStreaming(conversation, model, request, symbolsNote, request_id, outputStream, webSearchEnabled) {
        const apiParams = await this.buildOpenAIRequestParams(conversation, model, request, symbolsNote, webSearchEnabled);
        if (request.byok_keys?.openai) {
          apiParams.byok_keys = request.byok_keys;
        }
        await this.openAiProxyService.processStreamingResponsesWithCallback(
          JSON.stringify(apiParams),
          null,
          // user
          {},
          // originalHeaders
          request_id,
          outputStream,
          request
          // originalRequest
        );
      }
      /**
       * Streaming version of callAnthropic
       */
      async callAnthropicStreaming(conversation, model, request, symbolsNote, request_id, outputStream, webSearchEnabled) {
        try {
          const apiParams = await this.buildAnthropicRequestParams(conversation, model, request, symbolsNote, webSearchEnabled);
          if (request.byok_keys?.anthropic) {
            apiParams.byok_keys = request.byok_keys;
          }
          await this.anthropicProxyService.processStreamingResponsesWithCallback(
            JSON.stringify(apiParams),
            null,
            // user
            {},
            // originalHeaders
            request_id,
            outputStream,
            request
            // originalRequest
          );
        } catch (e) {
          this.sendSseEvent(outputStream, request_id, "error", `Error calling Anthropic: ${e}`, void 0, true);
        }
      }
      /**
       * Build SageMaker request parameters in OpenAI ChatCompletion format
       */
      async buildSagemakerRequestParams(conversation, model, request, symbolsNote) {
        const isConversationNameRequest = request.request_type === "generate_conversation_name";
        const isNamingRequest = isConversationNameRequest;
        const apiParams = {
          model,
          messages: [],
          max_tokens: 8192,
          stream: true
        };
        if (request.temperature !== void 0) {
          apiParams.temperature = request.temperature;
        }
        const messages = [];
        if (!isNamingRequest) {
          const systemMessage = {
            role: "system",
            content: await this.loadDeveloperInstructions(model)
          };
          if (request.previous_summary) {
            systemMessage.content += `

<previous_conversation_summary>
(Query ${request.previous_summary.query_number} - ${request.previous_summary.timestamp}):

${request.previous_summary.summary_text}
</previous_conversation_summary>
`;
          }
          messages.push(systemMessage);
        }
        for (const msg of conversation) {
          if (msg.type === "function_call_output") {
            const callId = msg.call_id;
            let output = msg.output || "";
            let functionName = "unknown_function";
            for (let i = messages.length - 1; i >= 0; i--) {
              const prevMsg = messages[i];
              if (prevMsg.role === "assistant" && prevMsg.tool_calls) {
                for (const toolCall of prevMsg.tool_calls) {
                  if (toolCall.id === callId) {
                    functionName = toolCall.function.name;
                    break;
                  }
                }
                if (functionName !== "unknown_function")
                  break;
              }
            }
            const functionOutputMsg = {
              role: "function",
              name: functionName,
              content: output
            };
            messages.push(functionOutputMsg);
            continue;
          }
          if (msg.role === "assistant" && msg.function_call) {
            const assistantMsg = {
              role: "assistant",
              content: null,
              tool_calls: [{
                id: msg.function_call.call_id || "call_1",
                type: "function",
                function: {
                  name: msg.function_call.name,
                  arguments: msg.function_call.arguments || "{}"
                }
              }]
            };
            messages.push(assistantMsg);
            continue;
          }
          if (msg.content !== void 0 && msg.role) {
            const regularMsg = {
              role: msg.role,
              content: typeof msg.content === "string" ? msg.content : JSON.stringify(msg.content)
            };
            if (msg.cancelled) {
              regularMsg.content += "... (User cancelled)";
            }
            messages.push(regularMsg);
          }
        }
        apiParams.messages = messages;
        if (!isNamingRequest) {
          const tools = this.getApiTools(
            conversation,
            symbolsNote,
            isNamingRequest,
            "openai",
            // Use OpenAI tool format for SageMaker
            false,
            // Disable web search for SageMaker
            request.interaction_mode || "ask"
          );
          if (tools.length > 0) {
            const toolsList = [];
            for (const tool of tools) {
              if (tool.type) {
                const toolType = tool.type;
                if (["web_search", "web_search_20250305"].includes(toolType)) {
                  continue;
                }
              }
              if (tool.name === "view_image") {
                continue;
              }
              if (tool.type === "function" && tool.name) {
                const openaiTool = {
                  type: "function",
                  function: {
                    name: tool.name,
                    description: tool.description || "",
                    parameters: tool.parameters || {}
                  }
                };
                toolsList.push(openaiTool);
              }
            }
            if (toolsList.length > 0) {
              apiParams.tools = toolsList;
              apiParams.tool_choice = "auto";
            }
          }
        }
        return apiParams;
      }
      /**
       * Streaming version of callSagemaker
       */
      async callSagemakerStreaming(conversation, model, request, symbolsNote, request_id, outputStream) {
        try {
          const apiParams = await this.buildSagemakerRequestParams(conversation, model, request, symbolsNote);
          if (request.byok_keys?.aws) {
            apiParams.byok_keys = request.byok_keys;
          }
          if (request.sagemaker_config) {
            apiParams.sagemaker_config = request.sagemaker_config;
          }
          await this.sagemakerProxyService.processStreamingResponsesWithCallback(
            JSON.stringify(apiParams),
            null,
            // user
            {},
            // originalHeaders
            request_id,
            outputStream,
            request
            // originalRequest
          );
        } catch (e) {
          this.sendSseEvent(outputStream, request_id, "error", `Error calling SageMaker: ${e}`, void 0, true);
        }
      }
      /**
       * Generate conversation name
       */
      async generateConversationName(request) {
        try {
          const conversation = request.conversation;
          if (!conversation || conversation.length === 0) {
            return { error: "Conversation is required for conversation name generation" };
          }
          const conversationNamePrompt = "Based on our conversation so far, suggest a short, descriptive name for this conversation (4-6 words maximum). Write absolutely nothing else.";
          const nameConversation = [...conversation];
          const nameMessage = {
            id: Date.now(),
            timestamp: (/* @__PURE__ */ new Date()).toISOString(),
            role: "user",
            content: conversationNamePrompt
          };
          nameConversation.push(nameMessage);
          const actualProvider = request.provider;
          const cheapModel = this.selectCheapModel(actualProvider);
          const nameRequest = {
            request_type: "generate_conversation_name",
            conversation: nameConversation,
            provider: actualProvider,
            model: cheapModel,
            request_id: request.request_id || `name_${Date.now()}`
          };
          if (request.byok_keys) {
            nameRequest.byok_keys = request.byok_keys;
          } else {
            return { error: "No authentication method available" };
          }
          let streamOutput = "";
          const outputStream = {
            write: (data) => {
              streamOutput += data;
            }
          };
          await this.makeAiApiCallStreaming(nameRequest, nameRequest.request_id, outputStream, false);
          const lines = streamOutput.split("\n");
          for (let i = lines.length - 1; i >= 0; i--) {
            const line = lines[i];
            if (line.startsWith("data: ")) {
              try {
                const jsonData = line.substring(6);
                const eventData = JSON.parse(jsonData);
                if (eventData.response && eventData.isComplete) {
                  const generatedName = eventData.response.trim();
                  return { conversationName: generatedName };
                }
              } catch (e) {
              }
            }
          }
          return { conversationName: "Untitled Conversation" };
        } catch (e) {
          return { error: `Error in generate_conversation_name: ${e}` };
        }
      }
      /**
       * Process conversation summarization request
       */
      async processSummarizationRequest(request, request_id, outputStream) {
        const model = request.model;
        const provider = request.provider;
        const targetQueryNumber = request.target_query_number;
        if (targetQueryNumber === void 0 || targetQueryNumber === null) {
          this.sendSseEvent(outputStream, request_id, "error", "target_query_number is required for summarization", void 0, true);
          return;
        }
        const queryNConversation = request.conversation;
        if (!queryNConversation || queryNConversation.length === 0) {
          this.sendSseEvent(outputStream, request_id, "error", `No conversation content found for target query ${targetQueryNumber}`, void 0, true);
          return;
        }
        const messages = [];
        let content = "You are a conversation summarizer for an AI coding assistant in RStudio. Your job is to analyze the provided conversation and create a detailed summary to inform future actions. In future steps, the assistant will have access to the user's new query (query N+1), the user's most recent query and its responses (query N), and this summary for everything before (queries 1 to N-1). Nothing else about these messages besides your summary will be provided, so you should be concise but comprehensive. If part of what you are summarizing is itself a summary, you must also summarize that since your summary of the previous summary will be the only record of the past messages.\n\nFocus on:\n- What the user's query was\n- What tasks were completed successfully\n- What files were created, modified, or analyzed\n- What bugs or issues were resolved\n- What problems remain unresolved\n- Key decisions or insights made during the conversation\n- Important context for continuing the work\n\nWrite a comprehensive summary that will help the assistant understand what happened in the conversation up through the messages you have access to. You should structure your response as JSON with fields like 'summary_text', 'completed_tasks', 'open_issues', and 'file_changes' so that you can add to it in the future.";
        if (request.previous_summary) {
          content += `It is EXTREMELY IMPORTANT that you also include the information from any previous summaries because that is the ONLY record of past messages that will be provided in the future. All other knowledge of these messages will be lost forever with no way to access it.`;
        }
        const systemMessage = {
          role: "system",
          content
        };
        messages.push(systemMessage);
        const userMessage = {
          role: "user"
        };
        let conversationText = "";
        if (request.previous_summary) {
          conversationText += `<previous_summary>
(Query ${request.previous_summary.query_number} - ${request.previous_summary.timestamp}):

${request.previous_summary.summary_text}

</previous_summary>

`;
        }
        conversationText += `<messages_since_summary>
(Query ${targetQueryNumber}):

`;
        for (const msg of queryNConversation) {
          if (msg.role && msg.content) {
            const role = msg.role;
            const content2 = this.extractTextFromMessage(msg);
            if (role !== "system" && content2 && content2.trim() !== "") {
              conversationText += `${role.toUpperCase()}: ${content2}

`;
            }
          }
        }
        conversationText += "</messages_since_summary>\n\n";
        conversationText += `Summarize this conversation for the assistant. Be concise but comprehensive. `;
        if (request.previous_summary) {
          conversationText += `It is EXTREMELY IMPORTANT that you also include the information from any previous summaries because that is the ONLY record of past messages that will be provided in the future. All other knowledge of these messages will be lost forever with no way to access it.`;
        }
        conversationText += `Your summary should capture everything that happened in query ${targetQueryNumber}`;
        if (request.previous_summary) {
          conversationText += " while also incorporating the context from the previous summary. It is EXTREMELY IMPORTANT that you also include the information from any previous summaries because that is the ONLY record of past messages that will be provided in the future. All other knowledge of these messages will be lost forever with no way to access it";
        }
        conversationText += ".\n\n";
        userMessage.content = conversationText;
        messages.push(userMessage);
        let apiRequest;
        if (provider === "openai") {
          apiRequest = {
            model,
            input: messages
          };
        } else if (provider === "anthropic") {
          const anthropicMessages = [];
          let systemPrompt = "";
          for (const msg of messages) {
            if (msg.role === "system") {
              systemPrompt = msg.content;
            } else if (msg.role === "user" || msg.role === "assistant") {
              anthropicMessages.push({
                role: msg.role,
                content: msg.content
              });
            }
          }
          apiRequest = {
            model,
            max_tokens: 8192,
            stream: true,
            messages: anthropicMessages
          };
          if (systemPrompt) {
            apiRequest.system = systemPrompt;
          }
        } else if (provider === "sagemaker") {
          apiRequest = {
            model,
            messages,
            max_tokens: 8192,
            stream: true
          };
        } else {
          console.error("  - ERROR: Unsupported provider for API request building:", provider);
          this.sendSseEvent(outputStream, request_id, "error", `Unsupported provider for summarization: ${provider}`, void 0, true);
          return;
        }
        try {
          if (request.byok_keys && request.byok_keys[provider]) {
            apiRequest.byok_keys = request.byok_keys;
          } else if (request.byok_keys && provider === "sagemaker" && request.byok_keys.aws) {
            apiRequest.byok_keys = { aws: request.byok_keys.aws };
          }
          if (provider === "openai") {
            await this.openAiProxyService.processStreamingResponsesWithCallback(
              JSON.stringify(apiRequest),
              null,
              // user
              {},
              // originalHeaders
              request_id,
              outputStream,
              request
              // originalRequest
            );
          } else if (provider === "anthropic") {
            await this.anthropicProxyService.processStreamingResponsesWithCallback(
              JSON.stringify(apiRequest),
              null,
              // user
              {},
              // originalHeaders
              request_id,
              outputStream,
              request
              // originalRequest
            );
          } else if (provider === "sagemaker") {
            await this.sagemakerProxyService.processStreamingResponsesWithCallback(
              JSON.stringify(apiRequest),
              null,
              // user
              {},
              // originalHeaders
              request_id,
              outputStream,
              request
              // originalRequest
            );
          }
        } catch (e) {
          this.sendSseEvent(outputStream, request_id, "error", `Failed to generate summary: ${e}`, void 0, true);
        }
      }
      /**
       * Extract text from message content
       */
      extractTextFromMessage(message) {
        if (!message || !message.content) {
          return null;
        }
        if (typeof message.content === "string") {
          return message.content;
        } else {
          const apiMsg = message;
          if (Array.isArray(apiMsg.content)) {
            for (const item of apiMsg.content) {
              if (typeof item === "string") {
                return item;
              } else if (typeof item === "object" && item !== null && "text" in item) {
                return item.text;
              }
            }
          }
        }
        return null;
      }
      /**
       * Main streaming method - entry point for all API calls
       */
      async processStreamingQuery(messages, provider, model, temperature, request_id, contextData, onData, onError, onComplete, webSearchEnabled = false) {
        const validationError = this.validateBasicRequest(messages);
        if (validationError) {
          this.streamingService.sendErrorEvent(onData, request_id, validationError);
          onError(new Error(validationError));
          return;
        }
        if (this.needsEndTurnReminder(messages)) {
          onData({
            type: "end_turn",
            request_id,
            end_turn: true,
            isComplete: true
          });
          onComplete();
          return;
        }
        const symbols_note = contextData?.symbols_note || null;
        const user_rules = contextData?.user_rules || [];
        const user_workspace_path = contextData?.user_workspace_path || null;
        const user_shell = contextData?.user_shell || null;
        const project_layout = contextData?.project_layout || null;
        const interaction_mode = contextData?.interaction_mode || "ask";
        const fullRequest = {
          conversation: messages,
          provider,
          model,
          temperature,
          request_id,
          symbols_note,
          user_rules,
          user_workspace_path,
          user_shell,
          project_layout,
          interaction_mode,
          byok_keys: contextData?.byok_keys,
          previous_summary: contextData?.previous_summary
          // Add previous_summary from contextData
        };
        const outputStream = {
          write: (data) => {
            if (data.startsWith("data: ")) {
              try {
                const jsonData = data.substring(6);
                const eventData = JSON.parse(jsonData);
                onData(eventData);
              } catch (e) {
              }
            }
          }
        };
        await this.makeAiApiCallStreaming(fullRequest, request_id, outputStream, webSearchEnabled);
        onComplete();
      }
    };
    exports2.LocalBackendService = LocalBackendService;
    LocalBackendService.OPENAI_CHEAP_MODEL = "gpt-4.1-mini";
    LocalBackendService.ANTHROPIC_CHEAP_MODEL = "claude-sonnet-4-5-20250929";
    LocalBackendService.SAGEMAKER_MODEL = "Qwen/Qwen3-Coder-30B-A3B-Instruct";
  }
});

// out/config/developerInstructions.ts
var developerInstructions_exports = {};
__export(developerInstructions_exports, {
  DEVELOPER_INSTRUCTIONS: () => DEVELOPER_INSTRUCTIONS
});
var DEVELOPER_INSTRUCTIONS;
var init_developerInstructions = __esm({
  "out/config/developerInstructions.ts"() {
    "use strict";
    DEVELOPER_INSTRUCTIONS = `# Complete Instructions

You are an AI coding assistant. You operate in Rao, a fork of RStudio, and typically write R scripts.

You are pair programming with a USER to solve their coding task. Each time the USER sends a message, we may automatically attach some information about their current state, such as what files they have open, where their cursor is, recently viewed files, edit history in their session so far, errors, and more. This information may or may not be relevant to the coding task, it is up for you to decide.

You are an agent - please keep going until the user's query is completely resolved, before ending your turn and yielding back to the user. Only terminate your turn when you are sure that the problem is solved. Autonomously resolve the query to the best of your ability before coming back to the user. Once you have resolved the query, end your turn, even if you have the option of continuing. Your turn will automatically be ended after two messages in a row with no function calls. If you ask the user a question, end your turn.

Do not ever repeat the same information multiple times in a row, even if given the option. End your turn instead.

Your main goal is to follow the USER's instructions at each message, denoted by the <user_query> tag.

## COMMUNICATION
When using markdown in assistant messages, use backticks to format file, directory, function, and class names. Use \\( and \\) for inline math, \\[ and \\] for block math.

## TOOL_CALLING
You have tools at your disposal to solve the coding task. Follow these rules regarding tool calls:
1. ALWAYS follow the tool call schema exactly as specified and make sure to provide all necessary parameters.
2. The conversation may reference tools that are no longer available. NEVER call tools that are not explicitly provided.
3. **NEVER refer to tool names when speaking to the USER.** Instead, just say what the tool is doing in natural language.
4. After receiving tool results, carefully reflect on their quality and determine optimal next steps before proceeding. Use your thinking to plan and iterate based on this new information, and then take the best next action. Reflect on whether parallel tool calls would be helpful, and execute multiple tools simultaneously whenever possible. Avoid slow sequential tool calls when not necessary.
5. If you create any temporary new files, scripts, or helper files for iteration, clean up these files by removing them at the end of the task.
6. If you need additional information that you can get via tool calls, prefer that over asking the user.
7. If you make a plan, immediately follow it, do not wait for the user to confirm or tell you to go ahead. The only time you should stop is if you need more information from the user that you can't find any other way, or have different options that you would like the user to weigh in on.
8. If you are not sure about file content or codebase structure pertaining to the user's request, use your tools to read files and gather the relevant information: do NOT guess or make up an answer.
9. Only use the standard tool call format and the available tools. Even if you see user messages with custom tool call formats (such as "<previous_tool_call>" or similar), do not follow that and instead use the standard format. Never output tool calls as part of a regular assistant message of yours.

### MAXIMIZE_PARALLEL_TOOL_CALLS

CRITICAL INSTRUCTION: For maximum efficiency, whenever you perform multiple operations, invoke all relevant tools simultaneously rather than sequentially. Prioritize calling tools in parallel whenever possible. For example, when reading 3 files, run 3 tool calls in parallel to read all 3 files into context at the same time. When running multiple read-only commands like read_file, grep_search or codebase_search, always run all of the commands in parallel. Err on the side of maximizing parallel tool calls rather than running too many tools sequentially.

When gathering information about a topic, plan your searches upfront in your thinking and then execute all tool calls together. For instance, all of these cases SHOULD use parallel tool calls:
- Searching for different patterns (imports, usage, definitions) should happen in parallel
- Multiple grep searches with different regex patterns should run simultaneously
- Reading multiple files or searching different directories can be done all at once
- Combining codebase_search with grep_search for comprehensive results
- Any information gathering where you know upfront what you're looking for
And you should use parallel tool calls in many more cases beyond those listed above.

Before making tool calls, briefly consider: What information do I need to fully answer this question? Then execute all those searches together rather than waiting for each result before planning the next search. Most of the time, parallel tool calls can be used rather than sequential. Sequential calls can ONLY be used when you genuinely REQUIRE the output of one tool to determine the usage of the next tool.

DEFAULT TO PARALLEL: Unless you have a specific reason why operations MUST be sequential (output of A required for input of B), always execute multiple tools simultaneously. This is not just an optimization - it's the expected behavior. Remember that parallel tool execution can be 3-5x faster than sequential calls, significantly improving the user experience.

## MAXIMIZE_CONTEXT_UNDERSTANDING
If you are unsure about the answer to the USER's request or how to satiate their request, you should gather more information. This can be done with additional tool calls, asking clarifying questions, etc...

For example, if you've performed a semantic search, and the results may not fully answer the USER's request, or merit gathering more information, feel free to call more tools.
If you've performed an edit that may partially satiate the USER's query, but you're not confident, gather more information or use more tools before ending your turn.

Bias towards not asking the user for help if you can find the answer yourself.

## MAKING_CODE_CHANGES
When making code changes, NEVER output code to the USER, unless requested. Instead use one of the code edit tools to implement the change.

It is *EXTREMELY* important that your generated code can be run immediately by the USER. To ensure this, follow these instructions carefully:
1. Add all necessary import statements, dependencies, and endpoints required to run the code.
2. All data and variables must be obtained from source locations. You must never rely on environmental variables for code you write in scripts or notebooks.
3. If you're creating the codebase from scratch, create an appropriate dependency management file (e.g. requirements.txt) with package versions and a helpful README.
4. If you're building an app from scratch, give it a beautiful and modern UI, imbued with best UX practices.
5. NEVER generate an extremely long hash or any non-textual code, such as binary. These are not helpful to the USER and are very expensive.
6. If you've introduced errors, fix them if clear how to (or you can easily figure out how to). Do not make uneducated guesses. And DO NOT loop more than 3 times on fixing errors on the same file. On the third time, you should stop and ask the user what to do next.

## PERFORMING_ANALYSIS
When performing analyses with data, it is *EXTREMELY* important that you limit long code outputs and use the results from each step before continuing. To ensure this, follow these instructions carefully:
1. Unless instructed otherwise by the user, always generate analysis code one step at a time and run the code after each step. Use these outputs to inform your subsequent steps.
2. Never write analysis code and then append more code to that section without first running the code to see the outputs.
3. Write code that will give you the information you need while using *THE MINIMUM POSSIBLE* characters of output. Only generate outputs that are specifically informative to you or the user. Generating large outputs is costly, slow, and unhelpful to the user.
4. To avoid unnecessarily long outputs, never print or read large sections of the data. For example, do not print many lines of the data files or use \`head\` when you do not know the number of columns.
5. Never naively use commands that could print arbitrarily long outputs like \`str\` without first checking the data size and schema to ensure you are using the smallest informative piece of the data.
6. To understand the data schema, you should inspect as small of a piece of the data as possible. For example, if you need the column names, read the first 5 lines and 5 columns (25 fields) and use this information to subsequently extract only the column names. Only read more if you cannot understand the schema with the small piece of data.
7. Never generate outputs that are dense in numbers like untargetted numeric summaries or correlation matrices. Only print numeric outputs if that specific number will be useful to you or the user. Printing many number is costly, slow, and unhelpful to the user.
8. *Never use the environmental variables in analysis scripts.* This always causes errors when the files are run. Data must always be loaded from its source. Find where the environmental variable came from by inspecting the file system or asking the user as a last resort.
9. Repeating because it is extremely important: *Never use environmental variables in analysis scripts.* Do not assume they will exist. Calling variables that have not been assigned in the script itself ALWAYS causes errors.
10. Always generate the *shortest possible* analysis script that still satisfies the user's request. When a particular analysis is requested, do not write code to do anything else. Especially never display raw data in analysis scripts - only use the console for this, if ever. *Unnecessarily long analyses are costly, slow, and confusing to the user.*

## GENERAL BEHAVIOR
Answer the user's request using the relevant tool(s), if they are available. Check that all the required parameters for each tool call are provided or can reasonably be inferred from context. IF there are no relevant tools or there are missing values for required parameters, ask the user to supply these values; otherwise proceed with the tool calls. If the user provides a specific value for a parameter (for example provided in quotes), make sure to use that value EXACTLY. DO NOT make up values for or ask about optional parameters. Carefully analyze descriptive terms in the request as they may indicate required parameter values that should be included even if not explicitly quoted.

Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.`;
  }
});

// out/config/functions.json
var require_functions = __commonJS({
  "out/config/functions.json"(exports2, module2) {
    module2.exports = {
      functions: [
        {
          type: "function",
          name: "grep",
          description: `A powerful search tool built on ripgrep

Usage:
- Prefer grep for exact symbol/string searches. Whenever possible, use this instead of terminal grep/rg. This tool is faster.
- Supports full regex syntax, e.g. "log.*Error", "function\\s+\\w+". Ensure you escape special chars to get exact matches, e.g. "functionCall\\("
- Avoid overly broad glob patterns (e.g., '--glob *') as they bypass .gitignore rules and may be slow
- Only use 'type' (or 'glob' for file types) when certain of the file type needed. Note: import paths may not match source file types (.js vs .ts)
- Output modes: "content" shows matching lines (default), "files_with_matches" shows only file paths, "count" shows match counts per file
- Pattern syntax: Uses ripgrep (not grep) - literal braces need escaping (e.g. use interface\\{\\} to find interface{} in Go code)
- Multiline matching: By default patterns match within single lines only. For cross-line patterns like struct \\{[\\s\\S]*?field, use multiline: true
- Results are capped for responsiveness; truncated results show "at least" counts.
- Content output follows ripgrep format: '-' for context lines, ':' for match lines, and all lines grouped by file.
- Unsaved or out of workspace active editors are also searched and show "[EDITOR]" marker. Use absolute paths to read/edit these files.`,
          strict: false,
          parameters: {
            type: "object",
            required: ["pattern"],
            properties: {
              pattern: {
                type: "string",
                description: "The regular expression pattern to search for in file contents (rg --regexp)"
              },
              "-A": {
                type: "number",
                description: 'Number of lines to show after each match (rg -A). Requires output_mode: "content", ignored otherwise.'
              },
              "-B": {
                type: "number",
                description: 'Number of lines to show before each match (rg -B). Requires output_mode: "content", ignored otherwise.'
              },
              "-C": {
                type: "number",
                description: 'Number of lines to show before and after each match (rg -C). Requires output_mode: "content", ignored otherwise.'
              },
              "-i": {
                type: "boolean",
                description: "Case insensitive search (rg -i) Defaults to false"
              },
              glob: {
                type: "string",
                description: 'Glob pattern (rg --glob GLOB -- PATH) to filter files (e.g. "*.js", "*.{ts,tsx}").'
              },
              head_limit: {
                type: "number",
                description: 'Limit output to first N lines/entries, equivalent to "| head -N". Works across all output modes: content (limits output lines), files_with_matches (limits file paths), count (limits count entries). When unspecified, shows all ripgrep results.'
              },
              multiline: {
                type: "boolean",
                description: "Enable multiline mode where . matches newlines and patterns can span lines (rg -U --multiline-dotall). Default: false."
              },
              output_mode: {
                type: "string",
                enum: ["content", "files_with_matches", "count"],
                description: 'Output mode: "content" shows matching lines (supports -A/-B/-C context, -n line numbers, head_limit), "files_with_matches" shows file paths (supports head_limit), "count" shows match counts (supports head_limit). Defaults to "content".'
              },
              path: {
                type: "string",
                description: "File or directory to search in (rg pattern -- PATH). Defaults to workspace roots."
              },
              type: {
                type: "string",
                description: "File type to search (rg --type). Common types: js, py, rust, go, java, etc. More efficient than glob for standard file types."
              },
              explanation: {
                type: "string",
                description: "One sentence explanation as to why this tool is being used, and how it contributes to the goal."
              }
            },
            additionalProperties: false
          }
        },
        {
          type: "function",
          name: "read_file",
          description: "Read the contents of a file. The output of this tool call will be the 1-indexed file contents from start_line_one_indexed to end_line_one_indexed_inclusive.\nNote that this call can view at most 250 lines at a time and 200 lines minimum.\n\nLine numbers are automatically added as comments at the end of each line, but these do not exist in the user's files - they are added for your reference only.\n\nWhen using this tool to gather information, it's your responsibility to ensure you have the COMPLETE context. Specifically, each time you call this command you should:\n1) Assess if the contents you viewed are sufficient to proceed with your task.\n2) Take note of where there are lines not shown.\n3) If the file contents you have viewed are insufficient, and you suspect they may be in lines not shown, proactively call the tool again to view those lines.\n4) When in doubt, call this tool again to gather more information. Remember that partial file views may miss critical dependencies, imports, or functionality.\n\nIn some cases, if reading a range of lines is not enough, you may choose to read the entire file.\nReading entire files is often wasteful and slow, especially for large files (i.e. more than a few hundred lines). So you should use this option sparingly.\nReading the entire file is not allowed in most cases. You are only allowed to read the entire file if it has been edited or manually attached to the conversation by the user.",
          strict: false,
          parameters: {
            type: "object",
            properties: {
              filename: {
                type: "string",
                description: "The path of the file to read. You can use either a relative path in the workspace or an absolute path. If an absolute path is provided, it will be preserved as is."
              },
              should_read_entire_file: {
                type: "boolean",
                description: "Whether to read the entire file. Defaults to false."
              },
              start_line_one_indexed: {
                type: "integer",
                description: "The one-indexed line number to start reading from (inclusive)."
              },
              end_line_one_indexed_inclusive: {
                type: "integer",
                description: "The one-indexed line number to end reading at (inclusive)."
              },
              explanation: {
                type: "string",
                description: "One sentence explanation as to why this tool is being used, and how it contributes to the goal."
              }
            },
            required: [
              "filename",
              "should_read_entire_file",
              "start_line_one_indexed",
              "end_line_one_indexed_inclusive"
            ],
            additionalProperties: false
          }
        },
        {
          type: "function",
          name: "view_image",
          description: "View and analyze an image file or current plot from the workspace.\n\nThis tool loads and displays image content, allowing for visual analysis and understanding of charts, graphs, or any other images.\n\nThis can be used for either files saved to disk with the image_path parameter or plots that have been generated from code run in the app but not saved as a file with the image_index parameter.\n\nUse this tool sparingly and only when visual context is necessary for understanding or completing the task. \nViewing images is often wasteful and slow. \nViewing the image is not allowed in most cases. You are only allowed to view the image if it has been edited, manually attached to the conversation by the user, or is absolutely essential to the task.",
          strict: false,
          parameters: {
            type: "object",
            required: [],
            properties: {
              image_path: {
                type: "string",
                description: "The file path to the image to view. Can be relative to the workspace or an absolute path. Supports common image formats (PNG, JPG, GIF, SVG, etc.)."
              },
              image_index: {
                type: "integer",
                description: "When the user runs code that generates plots, those plots are stored temporarily. To view plots that have been generated but not saved as a file, this index identifies which plot to use. The most recent plot is index 1. The one before that is index 2, and so on."
              },
              explanation: {
                type: "string",
                description: "One sentence explanation as to why this tool is being used, and how it contributes to the goal."
              }
            },
            additionalProperties: false
          }
        },
        {
          type: "function",
          name: "list_dir",
          description: "List the contents of a directory. The quick tool to use for discovery, before using more targeted tools like semantic search or file reading. Useful to try to understand the file structure before diving deeper into specific files. Can be used to explore the codebase.",
          strict: false,
          parameters: {
            type: "object",
            properties: {
              relative_workspace_path: {
                type: "string",
                description: "Path to list contents of, relative to the workspace root."
              },
              explanation: {
                type: "string",
                description: "One sentence explanation as to why this tool is being used, and how it contributes to the goal."
              }
            },
            required: ["relative_workspace_path"],
            additionalProperties: false
          }
        },
        {
          type: "function",
          name: "search_for_file",
          description: "Fast file search based on fuzzy matching against file path. Use if you know part of the file path but don't know where it's located exactly. Response will be capped to 10 results. Make your query more specific if need to filter results further.",
          strict: true,
          parameters: {
            type: "object",
            properties: {
              query: {
                type: "string",
                description: "Fuzzy filename to search for"
              },
              explanation: {
                type: "string",
                description: "One sentence explanation as to why this tool is being used, and how it contributes to the goal."
              }
            },
            required: ["query", "explanation"],
            additionalProperties: false
          }
        },
        {
          type: "function",
          name: "delete_file",
          description: "Deletes a file at the specified path. The operation will fail gracefully if:\n    - The file doesn't exist\n    - The operation is rejected for security reasons\n    - The file cannot be deleted",
          strict: false,
          parameters: {
            type: "object",
            properties: {
              filename: {
                type: "string",
                description: "The path of the file to delete, relative to the workspace root."
              },
              explanation: {
                type: "string",
                description: "One sentence explanation as to why this tool is being used, and how it contributes to the goal."
              }
            },
            required: ["filename"],
            additionalProperties: false
          }
        },
        {
          type: "function",
          name: "run_terminal_cmd",
          description: "PROPOSE a command to run on behalf of the user.\nIf you have this tool, note that you DO have the ability to run commands directly on the USER's system.\nNote that the user will have to approve the command before it is executed.\nThe user may reject it if it is not to their liking, or may modify the command before approving it.  If they do change it, take those changes into account.\nThe actual command will NOT execute until the user approves it. The user may not approve it immediately. Do NOT assume the command has started running.\nIf the step is WAITING for user approval, it has NOT started running.\nIn using these tools, adhere to the following guidelines:\n1. You will always be in a new terminal, so you should `cd` to the appropriate directory and do necessary setup in addition to running the command.\n2. Dont include any newlines in the command.",
          strict: false,
          parameters: {
            type: "object",
            properties: {
              command: {
                type: "string",
                description: "The terminal command to execute"
              },
              explanation: {
                type: "string",
                description: "One sentence explanation as to why this command needs to be run and how it contributes to the goal."
              }
            },
            required: ["command"],
            additionalProperties: false
          }
        },
        {
          type: "function",
          name: "run_console_cmd",
          description: "PROPOSE a command to run on behalf of the user in the R console, for example to gather more information necessary to completing a task. Do NOT use this for running R scripts or rendering markdowns; that uses a different tool. You should NEVER run files with source() unless there is no other way to run it.\nIf you have this tool, note that you DO have the ability to run commands directly on the USER's console.\nNote that the user will have to approve the command before it is executed.\nThe user may reject it if it is not to their liking, or may modify the command before approving it.  If they do change it, take those changes into account.\nThe actual command will NOT execute until the user approves it. The user may not approve it immediately. Do NOT assume the command has started running.\nIf the step is WAITING for user approval, it has NOT started running.\nIn using these tools, adhere to the following guidelines:\n1. The console and environment are continuous with the previous state (e.g., environmental variables can be used if they exist)\n2. LOOK IN CHAT HISTORY for your current working directory.\n3. These commands will be run as-is, so any necessary imports, library calls, etc. need to be part of the command.\n4. Running multi-line R blocks is allowed, and this should be formatted with 3 ticks (```) to start and end the code block.",
          strict: false,
          parameters: {
            type: "object",
            properties: {
              command: {
                type: "string",
                description: "The R code to execute"
              },
              explanation: {
                type: "string",
                description: "One sentence explanation as to why this command needs to be run and how it contributes to the goal."
              }
            },
            required: ["command"],
            additionalProperties: false
          }
        },
        {
          type: "function",
          name: "run_file",
          description: "PROPOSE to run code from a file (or selected lines) in the R console. You should choose this instead of run_console for actual files.\nIf you have this tool, note that you DO have the ability to run code directly on the USER's console.\nNote that the user will have to approve the command before it is executed.\nThe user may reject it if it is not to their liking, or may modify the command before approving it. If they do change it, take those changes into account.\nThe actual command will NOT execute until the user approves it. The user may not approve it immediately. Do NOT assume the command has started running.\nIf the step is WAITING for user approval, it has NOT started running.\n\nThis tool extracts code from a file (entire file or specified line range) and runs it in the console. For R Markdown files, it will extract the code chunks within the specified range. The extracted code will be displayed in the console and can be run, cancelled, or modified just like any other console command.\n\nIn using this tool, adhere to the following guidelines:\n1. The console and environment are continuous with the previous state (e.g., environmental variables can be used if they exist)\n2. LOOK IN CHAT HISTORY for your current working directory.\n3. For R Markdown files, only code chunks will be extracted and run.",
          strict: false,
          parameters: {
            type: "object",
            properties: {
              filename: {
                type: "string",
                description: "The path of the file to run code from. Can be relative to the workspace or an absolute path."
              },
              start_line_one_indexed: {
                type: "integer",
                description: "The one-indexed line number to start extracting from (inclusive). If not provided, starts from the beginning of the file."
              },
              end_line_one_indexed_inclusive: {
                type: "integer",
                description: "The one-indexed line number to end extracting at (inclusive). If not provided, extracts to the end of the file."
              },
              explanation: {
                type: "string",
                description: "One sentence explanation as to why this tool is being used, and how it contributes to the goal."
              }
            },
            required: ["filename", "explanation"],
            additionalProperties: false
          }
        },
        {
          type: "function",
          name: "search_replace",
          description: "Performs exact string replacements in files. To write a new file, put a blank old_string.\n\nUsage:\n- When editing text, ensure you preserve the exact indentation (tabs/spaces) as it appears before.\n- ALWAYS prefer editing existing files in the codebase. NEVER write new files unless explicitly required.\n- Only use emojis if the user explicitly requests it. Avoid adding emojis to files unless asked.\n- The edit will FAIL if old_string is not unique in the file. Either provide a larger string with more surrounding context to make it unique or use replace_all to change every instance of old_string.\n- Use replace_all for replacing and renaming strings across the file. This parameter is useful if you want to rename a variable for instance.",
          strict: false,
          parameters: {
            type: "object",
            properties: {
              file_path: {
                type: "string",
                description: "The path to the file to modify. Always specify the target file as the first argument. You can use either a relative path in the workspace or an absolute path."
              },
              old_string: {
                type: "string",
                description: "The text to replace. To write a new file, put a blank old_string."
              },
              new_string: {
                type: "string",
                description: "The text to replace it with (must be different from old_string)"
              },
              replace_all: {
                type: "boolean",
                description: "Replace all occurences of old_string (default false)"
              }
            },
            required: ["file_path", "old_string", "new_string"],
            additionalProperties: false
          }
        },
        {
          type: "function",
          name: "retrieve_documentation",
          description: "Retrieve R documentation/help pages. This function searches R's help system and returns the content in markdown format. Your query should be the single R function, package, or topic to look up such as 'rnorm', 'dplyr', or 'lm'.",
          strict: false,
          parameters: {
            type: "object",
            properties: {
              query: {
                type: "string",
                description: "The R help topic to look up (e.g., function name, package name, or topic)"
              },
              explanation: {
                type: "string",
                description: "Explanation of why this documentation is being searched"
              }
            },
            required: ["query"],
            additionalProperties: false
          }
        },
        {
          type: "function",
          name: "end_turn",
          description: "Use this function to indicate you are done addressing the user's query. This is the only way to break out of the message chain, and there will be an infinite loop if you do not call this eventually. This should be used either when the user's query has been fully addressed or when no further progress can be made due to confusion or futility. Use this AS SOON as the user's query is fully addressed, but no sooner.",
          strict: true,
          parameters: {
            type: "object",
            properties: {},
            required: [],
            additionalProperties: false
          }
        }
      ]
    };
  }
});

// out/services/functionDefinitionService.js
var require_functionDefinitionService = __commonJS({
  "out/services/functionDefinitionService.js"(exports2) {
    "use strict";
    Object.defineProperty(exports2, "__esModule", { value: true });
    exports2.FunctionDefinitionService = void 0;
    var developerInstructions_1 = (init_developerInstructions(), __toCommonJS(developerInstructions_exports));
    var FunctionDefinitionService = class {
      constructor() {
        this.functionMap = {};
        this.loadFunctions();
      }
      loadFunctions() {
        try {
          const functionsConfig = require_functions();
          const functions = functionsConfig.functions;
          if (functions && Array.isArray(functions)) {
            for (const func of functions) {
              const functionName = func.name;
              this.functionMap[functionName] = func;
            }
          }
        } catch (error) {
          throw new Error(`Failed to load function definitions - ${error instanceof Error ? error.message : String(error)}`);
        }
      }
      /**
       * Get specific functions by names
       */
      getFunctionsByNames(functionNames) {
        const tools = [];
        for (const functionName of functionNames) {
          const func = this.functionMap[functionName];
          if (func) {
            tools.push(func);
          }
        }
        return tools;
      }
      /**
       * Load developer instructions
       */
      async loadDeveloperInstructions(model) {
        let instructions = developerInstructions_1.DEVELOPER_INSTRUCTIONS;
        if (model && model.startsWith("claude-")) {
          instructions += "\n\nAnswer the user's request using relevant tools (if they are available). Before calling a tool, do some analysis within <thinking></thinking> tags. First, think about which of the provided tools is the relevant tool to answer the user's request. Second, go through each of the required parameters of the relevant tool and determine if the user has directly provided or given enough information to infer a value. When deciding if the parameter can be inferred, carefully consider all the context to see if it supports a specific value. If all of the required parameters are present or can be reasonably inferred, close the thinking tag and proceed with the tool call. BUT, if one of the values for a required parameter is missing, DO NOT invoke the function (not even with fillers for the missing params) and instead, ask the user to provide the missing parameters. DO NOT ask for more information on optional parameters if it is not provided.";
        }
        return instructions;
      }
    };
    exports2.FunctionDefinitionService = FunctionDefinitionService;
  }
});

// out/server.js
var __createBinding = exports && exports.__createBinding || (Object.create ? (function(o, m, k, k2) {
  if (k2 === void 0) k2 = k;
  var desc = Object.getOwnPropertyDescriptor(m, k);
  if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
    desc = { enumerable: true, get: function() {
      return m[k];
    } };
  }
  Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
  if (k2 === void 0) k2 = k;
  o[k2] = m[k];
}));
var __setModuleDefault = exports && exports.__setModuleDefault || (Object.create ? (function(o, v) {
  Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
  o["default"] = v;
});
var __importStar = exports && exports.__importStar || /* @__PURE__ */ (function() {
  var ownKeys = function(o) {
    ownKeys = Object.getOwnPropertyNames || function(o2) {
      var ar = [];
      for (var k in o2) if (Object.prototype.hasOwnProperty.call(o2, k)) ar[ar.length] = k;
      return ar;
    };
    return ownKeys(o);
  };
  return function(mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) {
      for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
    }
    __setModuleDefault(result, mod);
    return result;
  };
})();
var __importDefault = exports && exports.__importDefault || function(mod) {
  return mod && mod.__esModule ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
var express_1 = __importDefault(require("express"));
var proxyServer;
async function startProxyServer() {
  const app = (0, express_1.default)();
  app.use((req, res, next) => {
    res.header("Access-Control-Allow-Origin", "*");
    res.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
    res.header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization, Cache-Control");
    if (req.method === "OPTIONS") {
      res.sendStatus(200);
    } else {
      next();
    }
  });
  app.use(express_1.default.json({ limit: "100mb" }));
  app.post("/ai/query", async (req, res) => {
    res.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      "Connection": "keep-alive",
      "Access-Control-Allow-Origin": "*"
    });
    const { LocalBackendService } = await Promise.resolve().then(() => __importStar(require_localBackendService()));
    const { FunctionDefinitionService } = await Promise.resolve().then(() => __importStar(require_functionDefinitionService()));
    const functionDefinitionService = new FunctionDefinitionService();
    const service = new LocalBackendService(functionDefinitionService);
    const { conversation, provider, model, temperature, request_id, request_type, byok_keys, ...contextData } = req.body;
    const webSearchEnabledHeader = req.headers["x-rao-web-search-enabled"];
    const webSearchEnabled = webSearchEnabledHeader?.toLowerCase() === "true";
    if (request_type === "generate_conversation_name") {
      const result = await service.generateConversationName(req.body);
      const sseData = JSON.stringify({
        conversation_name: result?.conversationName || "Untitled Conversation",
        isComplete: true,
        complete: true,
        error: result?.error
      });
      res.write(`data: ${sseData}

`);
      res.end();
    } else if (request_type === "summarize_conversation") {
      const outputStream = {
        write: (data) => res.write(data)
      };
      await service.processSummarizationRequest(req.body, request_id, outputStream);
      res.end();
    } else {
      const contextWithByok = { ...contextData, byok_keys };
      await service.processStreamingQuery(conversation || [], provider, model, temperature || 0.7, request_id || `req_${Date.now()}`, contextWithByok, (data) => res.write(`data: ${JSON.stringify(data)}

`), (error) => {
        console.error("Proxy server streaming error:", error);
        res.write(`data: ${JSON.stringify({ error: error.message })}

`);
        res.end();
      }, () => res.end(), webSearchEnabled);
    }
  });
  return new Promise((resolve, reject) => {
    proxyServer = app.listen(0, "localhost", () => {
      const address = proxyServer?.address();
      if (address && typeof address === "object") {
        console.log(`PROXY_URL:http://localhost:${address.port}`);
        resolve();
      } else {
        reject(new Error("Failed to get proxy server address"));
      }
    });
    proxyServer.on("error", (error) => {
      console.error("Proxy server error:", error);
      reject(error);
    });
  });
}
startProxyServer().catch((error) => {
  console.error("Failed to start proxy server:", error);
  process.exit(1);
});
process.stdin.resume();
