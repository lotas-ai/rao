/*
 * AiServerOperations.java
 *
 * Copyright (C) 2025 by Lotas Inc.
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

   void acceptSearchReplaceCommand(String editedCode, String messageId, String requestId, ServerRequestCallback<JavaScriptObject> requestCallback);
   
   void cancelSearchReplaceCommand(String messageId, String requestId, ServerRequestCallback<JavaScriptObject> requestCallback);
      
   void clearAiConversation(ServerRequestCallback<Void> requestCallback);
   
   void createNewConversation(ServerRequestCallback<CreateAiConversationResult> requestCallback);
   
   void checkTerminalComplete(int messageId, ServerRequestCallback<Boolean> requestCallback);
   
   void clearConsoleDoneFlag(int messageId, ServerRequestCallback<Void> requestCallback);
   
   void clearTerminalDoneFlag(int messageId, ServerRequestCallback<Void> requestCallback);
      
   void revertAiMessage(int messageId, ServerRequestCallback<Void> requestCallback);
      
   void switchConversation(int index);
   
   void listConversations(ServerRequestCallback<JsArrayString> requestCallback);
   
   void getConversationLog(int conversationId, ServerRequestCallback<ConversationLogResult> requestCallback);
   
   void deleteFolder(String path, ServerRequestCallback<Void> requestCallback);
   
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
   
   void suggestTopics(String prefix, ServerRequestCallback<JsArrayString> requestCallback);
   
   void addDocsContext(String topic, String name, ServerRequestCallback<Boolean> requestCallback);
   
   void addChatContext(int conversationId, String name, ServerRequestCallback<Boolean> requestCallback);

   void getTerminalWebsocketPort(ServerRequestCallback<Double> requestCallback);
   
   void getWebSocketChannelId(int port, ServerRequestCallback<String> requestCallback);
   
   void getTabFilePath(String tabId, ServerRequestCallback<String> requestCallback);
   
   void matchTextInOpenDocuments(String searchText, ServerRequestCallback<TextMatchResult> requestCallback);
   
   void acceptTerminalCommand(String messageId, String script, String requestId, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void cancelTerminalCommand(String messageId, String requestId, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void acceptConsoleCommand(String messageId, String script, String requestId, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void cancelConsoleCommand(String messageId, String requestId, ServerRequestCallback<java.lang.Void> requestCallback);

   void runScriptInConsole(String script, int messageId, ServerRequestCallback<Void> requestCallback);
   
   void runScriptInTerminal(String script, int messageId, ServerRequestCallback<Void> requestCallback);
   
   void finalizeConsoleCommand(int messageId, String requestId, String consoleOutput, ServerRequestCallback<JavaScriptObject> requestCallback);
   
   void finalizeTerminalCommand(int messageId, String requestId, ServerRequestCallback<JavaScriptObject> requestCallback);
   
   void processAiOperation(JavaScriptObject operationParams, ServerRequestCallback<JavaScriptObject> requestCallback);
   
   void getCurrentConversationIndex(ServerRequestCallback<Double> requestCallback);
   
   void saveAiImage(String imagePath, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void createTempImageFile(String dataUrl, String fileName, ServerRequestCallback<String> requestCallback);
   
   void listImages(ServerRequestCallback<JsArrayString> requestCallback);
   
   void deleteImage(String imagePath, ServerRequestCallback<Void> requestCallback);
   
   // BYOK (Bring Your Own Key) operations
   void startLocalBackendProxy(ServerRequestCallback<String> requestCallback);
   
   void stopLocalBackendProxy(ServerRequestCallback<Boolean> requestCallback);
   
   void isBYOKEnabled(String provider, ServerRequestCallback<Boolean> requestCallback);
   
   void setBYOKApiKey(String provider, String apiKey, ServerRequestCallback<Boolean> requestCallback);
   
   void setBYOKEnabled(String provider, boolean enabled, ServerRequestCallback<Boolean> requestCallback);
   
   void clearBYOKApiKey(String provider, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void hasBYOKApiKey(String provider, ServerRequestCallback<Boolean> requestCallback);
   
   void setSageMakerEndpoint(String endpoint, ServerRequestCallback<Boolean> requestCallback);
   
   void getSageMakerEndpoint(ServerRequestCallback<String> requestCallback);
   
   // Rules file operations
   void setRulesFilePath(String filePath, ServerRequestCallback<Boolean> requestCallback);
   
   void getRulesFilePath(ServerRequestCallback<String> requestCallback);
   
   void setSageMakerRegion(String region, ServerRequestCallback<Boolean> requestCallback);
   
   void getSageMakerRegion(ServerRequestCallback<String> requestCallback);
   
   void setSageMakerModel(String model, ServerRequestCallback<Boolean> requestCallback);
   
   void getSageMakerModel(ServerRequestCallback<String> requestCallback);
   
   void getLocalModelEndpoint(ServerRequestCallback<String> requestCallback);
   
   void setLocalModelEndpoint(String endpoint, ServerRequestCallback<Boolean> requestCallback);
   
   void getLocalModelName(ServerRequestCallback<String> requestCallback);
   
   void setLocalModelName(String modelName, ServerRequestCallback<Boolean> requestCallback);
   
   void deleteAllImages(ServerRequestCallback<java.lang.Void> requestCallback);
   
   void checkImageContentDuplicate(String imagePath, ServerRequestCallback<Boolean> requestCallback);
   
   void getPersistentDiffData(String filePath, ServerRequestCallback<JavaScriptObject> requestCallback);
   
   void getUserProfile(ServerRequestCallback<AiUserProfile> requestCallback);
   
   void getSubscriptionStatus(ServerRequestCallback<AiSubscriptionStatus> requestCallback);
   
   void getReferralSummary(ServerRequestCallback<AiReferralSummary> requestCallback);
   
   void getApiKeyStatus(ServerRequestCallback<Boolean> requestCallback);
   
   void getAvailableModels(ServerRequestCallback<JsArrayString> requestCallback);
   
   void getSelectedModel(ServerRequestCallback<String> requestCallback);
   
   void getCurrentWorkingDirectory(ServerRequestCallback<String> requestCallback);
   
   void getTemperature(ServerRequestCallback<Double> requestCallback);
   
   void setTemperature(double temperature, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void getSecurityMode(ServerRequestCallback<String> requestCallback);
   
   void setSecurityMode(String mode, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void getWebSearchEnabled(ServerRequestCallback<Boolean> requestCallback);
   
   void setWebSearchEnabled(boolean enabled, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void getUserRules(ServerRequestCallback<JavaScriptObject> requestCallback);
   
   void addUserRule(String rule, ServerRequestCallback<JavaScriptObject> requestCallback);
   
   void editUserRule(int index, String rule, ServerRequestCallback<JavaScriptObject> requestCallback);
   
   void deleteUserRule(int index, ServerRequestCallback<JavaScriptObject> requestCallback);
   
   void signInWithWebsite(String websiteUrl, ServerRequestCallback<String> requestCallback);
   
   void cleanupAuthServer(ServerRequestCallback<java.lang.Void> requestCallback);
   
   // Automation settings
   void getAutoAcceptEdits(ServerRequestCallback<Boolean> requestCallback);
   
   void setAutoAcceptEdits(boolean enabled, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void getAutoAcceptConsole(ServerRequestCallback<Boolean> requestCallback);
   
   void setAutoAcceptConsole(boolean enabled, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void getAutoAcceptTerminal(ServerRequestCallback<Boolean> requestCallback);
   
   void setAutoAcceptTerminal(boolean enabled, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void getAutoRunFiles(ServerRequestCallback<Boolean> requestCallback);
   
   void setAutoRunFiles(boolean enabled, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void getAutoDeleteFiles(ServerRequestCallback<Boolean> requestCallback);
   
   void setAutoDeleteFiles(boolean enabled, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void getAutoAcceptConsoleAllowAnything(ServerRequestCallback<Boolean> requestCallback);
   
   void setAutoAcceptConsoleAllowAnything(boolean enabled, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void getAutoAcceptTerminalAllowAnything(ServerRequestCallback<Boolean> requestCallback);
   
   void setAutoAcceptTerminalAllowAnything(boolean enabled, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void getAutoRunFilesAllowAnything(ServerRequestCallback<Boolean> requestCallback);
   
   void setAutoRunFilesAllowAnything(boolean enabled, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void getAutomationList(String listType, ServerRequestCallback<JavaScriptObject> requestCallback);
   
   void setAutomationList(String listType, JavaScriptObject items, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void setInteractionMode(String mode, ServerRequestCallback<java.lang.Void> requestCallback);
   
   void getInteractionMode(ServerRequestCallback<String> requestCallback);
}
