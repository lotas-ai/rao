/*
 * AiServerOperations.java
 *
 * Copyright (C) 2025 by William Nickols
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.studio.client.workbench.views.ai.model;

import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.JavaScriptObject;
import org.rstudio.studio.client.server.ServerRequestCallback;

public interface AiServerOperations
{
   void getAi(String topic, 
                String packageName,
                int type,
                ServerRequestCallback<AiInfo> requestCallback);
   
   String getApplicationURL(String topicURI);

   void showAiTopic(String topic, String pkgName, int type);
   
   void getCustomAi(String aiHandler,
                      String topic,
                      String source,
                      String language,
                      ServerRequestCallback<AiInfo.Custom> requestCallback);
   
   void getCustomParameterAi(String aiHandler,
                               String source,
                               String language,
                               ServerRequestCallback<AiInfo.Custom> requestCallback);
   
   void showCustomAiTopic(String aiHandler, String topic, String source);

   void getVignetteTitle(String topic,
                         String pkgName, 
                         ServerRequestCallback<String> requestCallback);

   void getVignetteDescription(String topic,
                                      String pkgName, 
                                      ServerRequestCallback<String> requestCallback);

   void showVignette(String topic, String pkgName);

   void acceptEditFileCommand(String editedCode, String messageId, String requestId, ServerRequestCallback<JavaScriptObject> requestCallback);
      
   void clearAiConversation(ServerRequestCallback<Void> requestCallback);
   
   void createNewConversation(ServerRequestCallback<CreateAiConversationResult> requestCallback);
   
   void checkTerminalComplete(int messageId, ServerRequestCallback<Boolean> requestCallback);
   
   void clearConsoleDoneFlag(int messageId, ServerRequestCallback<Void> requestCallback);
   
   void clearTerminalDoneFlag(int messageId, ServerRequestCallback<Void> requestCallback);
   
   void addConsoleOutputToAiConversation(int messageId, ServerRequestCallback<Boolean> requestCallback);
   
   void addTerminalOutputToAiConversation(int messageId, ServerRequestCallback<Boolean> requestCallback);
   
   void revertAiMessage(int messageId, ServerRequestCallback<Void> requestCallback);
      
   void switchConversation(int index);
   
   void listConversations(ServerRequestCallback<JsArrayString> requestCallback);
   
   void getConversationLog(int conversationId, ServerRequestCallback<ConversationLogResult> requestCallback);
   
   void deleteFolder(String path, ServerRequestCallback<Void> requestCallback);
   
   void getApiKeyManagement(ServerRequestCallback<ApiKeyManagementResult> requestCallback);
   
   void saveApiKey(String provider, String key, ServerRequestCallback<Void> requestCallback);
   
   void deleteApiKey(String provider, ServerRequestCallback<Void> requestCallback);
   
   void setActiveProvider(String provider, ServerRequestCallback<Void> requestCallback);
   
   void setModel(String provider, String model, ServerRequestCallback<Void> requestCallback);
   
   void getConversationName(int conversationId, ServerRequestCallback<String> requestCallback);
   
   void setConversationName(int conversationId, String name, ServerRequestCallback<Void> requestCallback);
   
   void deleteConversationName(int conversationId, ServerRequestCallback<Void> requestCallback);
   
   void listConversationNames(ServerRequestCallback<ConversationNamesResult> requestCallback);
   
   void shouldPromptForName(ServerRequestCallback<Boolean> requestCallback);
   
   void generateConversationName(int conversationId, ServerRequestCallback<String> requestCallback);
   
   void saveAiAttachment(String filePath, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void listAttachments(ServerRequestCallback<JsArrayString> requestCallback);
   
   void deleteAttachment(String filePath, ServerRequestCallback<Void> requestCallback);
   
   void deleteAllAttachments(ServerRequestCallback<java.lang.Void> requestCallback);

   void cleanupConversationAttachments(int conversationId, ServerRequestCallback<Void> requestCallback);

   void markButtonAsRun(String messageId, String buttonType, ServerRequestCallback<Boolean> requestCallback);

   void getFileNameForMessageId(String messageId, ServerRequestCallback<String> requestCallback);

   void isConversationEmpty(int conversationId, ServerRequestCallback<Boolean> requestCallback);

   void setAiWorkingDirectory(String dir, ServerRequestCallback<Void> requestCallback);

   void browseDirectory(ServerRequestCallback<JavaScriptObject> requestCallback);
   
   void browseForFile(ServerRequestCallback<org.rstudio.core.client.files.FileSystemItem> requestCallback);
   
   void addContextItem(String path, ServerRequestCallback<Boolean> requestCallback);
   
   void addContextLines(String path, int startLine, int endLine, ServerRequestCallback<Boolean> requestCallback);
   
   void getContextItems(ServerRequestCallback<JsArrayString> requestCallback);
   
   void removeContextItem(String path, ServerRequestCallback<Boolean> requestCallback);
   
   void clearContextItems(ServerRequestCallback<Void> requestCallback);

   void getTerminalWebsocketPort(ServerRequestCallback<Double> requestCallback);
   
   void getWebSocketChannelId(int port, ServerRequestCallback<String> requestCallback);
   
   void getTabFilePath(String tabId, ServerRequestCallback<String> requestCallback);
   
   void matchTextInOpenDocuments(String searchText, ServerRequestCallback<TextMatchResult> requestCallback);
   
   void acceptTerminalCommand(String messageId, String script, String requestId, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void cancelTerminalCommand(String messageId, String requestId, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void acceptConsoleCommand(String messageId, String script, String requestId, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void cancelConsoleCommand(String messageId, String requestId, ServerRequestCallback<java.lang.Void> requestCallback);

   void cancelEditFileCommand(String messageId, String requestId, ServerRequestCallback<JavaScriptObject> requestCallback);

   void runScriptInConsole(String script, int messageId, ServerRequestCallback<Void> requestCallback);
   
   void runScriptInTerminal(String script, int messageId, ServerRequestCallback<Void> requestCallback);
   
   void finalizeConsoleCommand(int messageId, String requestId, String consoleOutput, ServerRequestCallback<JavaScriptObject> requestCallback);
   
   void finalizeTerminalCommand(int messageId, String requestId, ServerRequestCallback<JavaScriptObject> requestCallback);
   
   void processAiOperation(JavaScriptObject operationParams, ServerRequestCallback<JavaScriptObject> requestCallback);
   
   void getCurrentConversationIndex(ServerRequestCallback<Double> requestCallback);
   
   void getDiffDataForEditFile(String messageId, ServerRequestCallback<com.google.gwt.core.client.JavaScriptObject> requestCallback);
}
