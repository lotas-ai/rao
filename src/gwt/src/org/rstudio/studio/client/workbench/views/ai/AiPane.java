/*
 * AiPane.java
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

package org.rstudio.studio.client.workbench.views.ai;

import org.rstudio.core.client.BrowseCap;
import org.rstudio.core.client.ElementIds;
import org.rstudio.core.client.Point;
import org.rstudio.core.client.Rectangle;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.command.KeyboardShortcut;
import org.rstudio.core.client.command.ShortcutManager;
import org.rstudio.core.client.dom.DomUtils;
import org.rstudio.core.client.dom.ElementEx;
import org.rstudio.core.client.dom.EventProperty;
import org.rstudio.core.client.dom.IFrameElementEx;
import org.rstudio.core.client.dom.WindowEx;
import org.rstudio.core.client.events.NativeKeyDownEvent;
import org.rstudio.core.client.files.FileSystemItem;
import org.rstudio.core.client.hyperlink.AiHyperlinkPopupHeader;
import org.rstudio.core.client.hyperlink.AiPageShower;
import org.rstudio.core.client.hyperlink.AiPreview;
import org.rstudio.core.client.hyperlink.HyperlinkPopupPanel;
import org.rstudio.core.client.hyperlink.HyperlinkPopupPositioner;
import org.rstudio.core.client.regex.Match;
import org.rstudio.core.client.regex.Pattern;
import org.rstudio.core.client.theme.res.ThemeStyles;
import org.rstudio.core.client.widget.CanFocus;
import org.rstudio.core.client.widget.FindTextBox;
import org.rstudio.core.client.widget.FocusHelper;
import org.rstudio.core.client.widget.MessageDialog;
import org.rstudio.core.client.widget.RStudioThemedFrame;
import org.rstudio.core.client.widget.SearchDisplay;
import org.rstudio.core.client.widget.SearchWidget;
import org.rstudio.core.client.widget.SimpleMenuLabel;
import org.rstudio.core.client.widget.SmallButton;
import org.rstudio.core.client.widget.Toolbar;
import org.rstudio.core.client.widget.ToolbarButton;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.application.Desktop;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.common.AutoGlassPanel;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.common.GlobalDisplay.NewWindowOptions;
import org.rstudio.studio.client.common.SimpleRequestCallback;
import org.rstudio.studio.client.common.debugging.model.UnhandledError;
import org.rstudio.studio.client.server.Server;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.server.Void;
import org.rstudio.studio.client.server.VoidServerRequestCallback;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.prefs.model.UserPrefs;
import org.rstudio.studio.client.workbench.ui.WorkbenchPane;
import org.rstudio.studio.client.workbench.views.ai.Ai.LinkMenu;
import org.rstudio.studio.client.workbench.views.ai.model.Link;
import org.rstudio.studio.client.workbench.views.ai.events.AiNavigateEvent;
import org.rstudio.studio.client.workbench.views.ai.events.HasAiNavigateHandlers;
import org.rstudio.studio.client.workbench.views.ai.events.StoreActiveRequestIdEvent;
import org.rstudio.studio.client.workbench.views.ai.model.AiServerOperations;
import org.rstudio.studio.client.workbench.views.ai.model.CreateAiConversationResult;

import org.rstudio.studio.client.workbench.views.ai.search.AiSearch;
import org.rstudio.studio.client.workbench.views.ai.AiPaneLifecycle;
import org.rstudio.studio.client.workbench.views.ai.AiPaneEventHandlers;

import org.rstudio.studio.client.workbench.views.ai.AiPaneScroll;
import org.rstudio.studio.client.workbench.views.ai.AiPaneResponses;
import org.rstudio.studio.client.workbench.views.ai.AiPaneConversations;
import org.rstudio.studio.client.workbench.model.SessionInfo;
import org.rstudio.core.client.Debug;
import org.rstudio.core.client.command.AppCommand;
import org.rstudio.core.client.command.Handler;
import org.rstudio.core.client.widget.SecondaryToolbar;
import org.rstudio.core.client.widget.ToolbarPopupMenu;
import org.rstudio.studio.client.common.icons.StandardIcons;
import org.rstudio.studio.client.common.satellite.SatelliteManager;
import org.rstudio.studio.client.common.satellite.SatelliteUtils;
import org.rstudio.studio.client.common.synctex.model.SourceLocation;
import org.rstudio.studio.client.workbench.views.console.events.SendToConsoleEvent;
import org.rstudio.studio.client.workbench.views.source.model.SourcePosition;
import org.rstudio.core.client.command.CommandBinder;
import org.rstudio.core.client.widget.Operation;
import org.rstudio.studio.client.workbench.views.terminal.TerminalSessionSocket;
import org.rstudio.studio.client.workbench.views.ai.widgets.AiStreamingPanel;
import org.rstudio.studio.client.workbench.views.ai.widgets.AiConsoleWidget;
import org.rstudio.studio.client.workbench.views.ai.AiTerminalWidget;
import org.rstudio.studio.client.workbench.views.ai.events.AiStreamDataEvent;
import org.rstudio.studio.client.workbench.views.ai.AiToolbars;
import org.rstudio.core.client.js.JsObject;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.dom.client.LoadEvent;
import com.google.gwt.event.dom.client.LoadHandler;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.http.client.URL;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.ui.RootPanel;
import com.sksamuel.gwt.websockets.CloseEvent;
import com.sksamuel.gwt.websockets.Websocket;
import com.sksamuel.gwt.websockets.WebsocketListenerExt;

import java.util.ArrayList;
import java.util.Collections;

public class AiPane extends WorkbenchPane
                      implements Ai.Display
{
   @Inject
   public AiPane(Provider<AiSearch> searchProvider,
                   GlobalDisplay globalDisplay,
                   Commands commands,
                   EventBus events,
                   UserPrefs prefs,
                   AiServerOperations server,
                   AiContext aiContext)
   {
      super(constants_.aiText(), events);

      // Set the current instance for JSNI callbacks
      currentInstance_ = this;
      
      // Export JavaScript callbacks for use by the API key management page
      exportJSCallbackMethods();
      
      searchProvider_ = searchProvider;
      globalDisplay_ = globalDisplay;
      commands_ = commands;
      server_ = server;
      aiContext_ = aiContext;

      prefs_ = prefs;
      
      // Fetch WebSocket port proactively on initialization
      fetchWebSocketPort();

      // Initialize the conversations manager
      conversationsManager_ = new AiPaneConversations(this, server, globalDisplay);

      // Initialize the attachments menu
      attachmentsManager_ = new AiPaneAttachments(this, server, globalDisplay);

      // Initialize the images manager  
      imagesManager_ = new AiPaneImages(this, server, globalDisplay);
      
      // Use the conversations manager to initialize the menu
      history_ = conversationsManager_.initConversationMenu(commands);
      attachmentsMenu_ = attachmentsManager_.initAttachmentsMenu(commands);
      imagesMenu_ = imagesManager_.initImagesMenu(commands);

      // Handles resizing of the window to hide the conversation menu and attachments menu when the window is resized
      Window.addResizeHandler(new ResizeHandler()
      {
         public void onResize(ResizeEvent event)
         {
            history_.getMenu().hide();
            attachmentsMenu_.getMenu().hide();
            imagesMenu_.getMenu().hide();
         }
      });

      // Creates the main frame for the AI pane
      frame_ = new RStudioThemedFrame(
         constants_.aiPaneTitle(),
         null,
         RES.editorStyles().getText() +
         // Remove global body font-family override; only apply to a container class
         "\n .rstudio-ai-pane-body { font-size: 14px !important; font-family: sans-serif !important; }" +
         "\n div.message.assistant { margin: 6px 0; }" +
         "\n div.text.assistant { margin-bottom: 6px; padding-bottom: 4px; }",
         null,
         false,
         true);
      
      // Sets the size of the frame to 100% of the window
      frame_.setSize("100%", "100%");
      frame_.setStylePrimaryName("rstudio-AiFrame");

      // Add a load handler to restore scroll position if needed
      frame_.addLoadHandler(new LoadHandler() {
         @Override
         public void onLoad(LoadEvent event) {
            // Save the current search container height to restore after load events
            final int storedHeight = getCurrentSearchContainerHeight();
            
            WindowEx window = getIFrameEx().getContentWindow();
            if (window != null) {
               // Check if this is the API key management page and hide search container if needed
               String url = window.getLocationHref();
               if (url != null && url.contains("api_key_management")) {
                  hideSearchContainer();
                  
                  // Set the title to "API Key Management" when on this page
                  updateTitle("API Key Management");
               } else {
                  restoreSearchContainer();
                  
                  // Restore the specific height we had before to prevent jumping
                  if (storedHeight > 0 && searchContainer != null && mainPanel != null) {
                     Scheduler.get().scheduleDeferred(() -> {
                        mainPanel.setWidgetSize(searchContainer, storedHeight);
                        mainPanel.forceLayout();
                        searchContainer.getElement().getStyle().setProperty("minHeight", storedHeight + "px");
                     });
                  }
               }               
            }
         }
      });

      // Adds the ace_editor_theme class to the frame
      frame_.addStyleName("ace_editor_theme");

      // Assigns the AI_FRAME element ID to the frame
      ElementIds.assignElementId(frame_.getElement(), ElementIds.AI_FRAME);
      
      // Create the background frame exactly like the main frame
      backgroundFrame_ = new RStudioThemedFrame(
         constants_.aiPaneTitle(),
         null,
         RES.editorStyles().getText() + "\n body { font-size: 14px !important; font-family: sans-serif !important; }" +
         "\n div.message.assistant { margin: 6px 0; }" +
         "\n div.text.assistant { margin-bottom: 6px; padding-bottom: 4px; }",
         null,
         false,
         true);
      
      // Sets the size of the background frame to 100% of the window
      backgroundFrame_.setSize("100%", "100%");
      backgroundFrame_.setStylePrimaryName("rstudio-AiFrame");
      backgroundFrame_.addStyleName("ace_editor_theme");
      
      // Initially hide the background frame
      backgroundFrame_.getElement().getStyle().setDisplay(Display.NONE);
      
      // Add the same load handler to the background frame
      backgroundFrame_.addLoadHandler(new LoadHandler() {
         @Override
         public void onLoad(LoadEvent event) {
            // Save the current search container height to restore after load events
            final int storedHeight = getCurrentSearchContainerHeight();
            
            WindowEx window = getBackgroundIFrameEx().getContentWindow();
            if (window != null) {
               // Check if this is the API key management page and hide search container if needed
               String url = window.getLocationHref();
               if (url != null && url.contains("api_key_management")) {
                  hideSearchContainer();
                  
                  // Set the title to "API Key Management" when on this page
                  updateTitle("API Key Management");
               } else {
                  restoreSearchContainer();
                  
                  // Restore the specific height we had before to prevent jumping
                  if (storedHeight > 0 && searchContainer != null && mainPanel != null) {
                     Scheduler.get().scheduleDeferred(() -> {
                        mainPanel.setWidgetSize(searchContainer, storedHeight);
                        mainPanel.forceLayout();
                        searchContainer.getElement().getStyle().setProperty("minHeight", storedHeight + "px");
                     });
                  }
               }
            }
         }
      });
      


      // Initialize event handlers
      eventHandlers_ = new AiPaneEventHandlers(this, commands, events);
      eventHandlers_.setGlobalDisplay(globalDisplay);
      eventHandlers_.setServer(server);
      

      
      // Initialize scroll handler
      scrollHandler_ = new AiPaneScroll(this);
      
      // Initialize AI orchestrator for flat architecture first
      aiOrchestrator_ = new AiOrchestrator(server, this, events);
      
      // Initialize responses handler
      responses_ = new AiPaneResponses(this, server, globalDisplay, aiOrchestrator_);
      
      // Initialize toolbars handler
      toolbars_ = new AiToolbars(this, searchProvider, commands, history_, frame_, backgroundFrame_, scrollHandler_, aiContext, events);

      // Register for StoreActiveRequestIdEvent
      events.addHandler(StoreActiveRequestIdEvent.TYPE, 
         new StoreActiveRequestIdEvent.Handler() {
            @Override
            public void onStoreActiveRequestId(StoreActiveRequestIdEvent event) {
               String requestId = event.getId();
               if (requestId != null && !requestId.isEmpty()) {
                  storeActiveRequestId(requestId);
               } else {
                  Debug.log("DEBUG: Request ID is null or empty, not storing");
               }
            }
         });
      


      // NOTE: we do some pretty strange gymnastics to save the scroll
      // position for the iframe. when the Ai Pane is deactivated
      // (e.g. another tab in the tabset is selected), a synthetic scroll
      // event is sent to the iframe's window, forcing it to scroll back
      // to the top of the window. in order to suppress this behavior, we
      // track whether the scroll event occurred when the tab was deactivated;
      // if it was, then we restore the last-recorded scroll position instead.

      // Creates a timer to save the scroll position of the content window when the user switches tabs
      scrollTimer_ = new Timer()
      {
         @Override
         public void run()
         {
            WindowEx contentWindow = getContentWindow();
            if (contentWindow != null)
            {
               if (lifecycle_.isSelected())
               {
                  scrollPos_ = contentWindow.getScrollPosition();
               }
               else if (scrollPos_ != null)
               {
                  contentWindow.setScrollPosition(scrollPos_);
               }
            }
         }
      };

      prefs_.helpFontSizePoints().bind((Double value) -> refresh());
      
      ensureWidget();
      //
      lifecycle_ = new AiPaneLifecycle(this);
   }

   @Override
   public void onBeforeUnselected()
   {
      super.onBeforeUnselected();
      lifecycle_.onBeforeUnselected();
   }

   @Override
   public void onSelected()
   {
      super.onSelected();
      lifecycle_.onSelected();
      
      // Load context items to synchronize UI with R session state
      if (aiContext_ != null) {
         FlowPanel selectedFilesPanel = toolbars_ != null ? 
            toolbars_.getSelectedFilesPanel() : null;
         aiContext_.loadContextItems(selectedFilesPanel);
      }
      
      // Check if we're on the API key management page and hide search bar if needed
      WindowEx window = getContentWindow();
      if (window != null) {
         String url = window.getLocationHref();
         if (url != null && url.contains("api_key_management")) {
            hideSearchContainer();
         }
      }
   }

   @Override
   public void setFocus()
   {
      lifecycle_.setFocus();
   }

   @Override
   public void onResize()
   {
      lifecycle_.onResize();
      super.onResize();
   }

   @Override
   protected void onLoad()
   {
      super.onLoad();
      
      // Make sure JavaScript callbacks are registered
      exportJSCallbackMethods();
            
      // Register keyboard shortcut for frame toggling (Alt+T)
      this.addDomHandler(new KeyDownHandler() {
         @Override
         public void onKeyDown(KeyDownEvent event) {
            if (event.isAltKeyDown() && event.getNativeKeyCode() == 'T') {
               toggleFrames();
               event.preventDefault();
               event.stopPropagation();
            }
         }
      }, KeyDownEvent.getType());
      
      // Load context items to synchronize UI with R session state
      if (aiContext_ != null) {
         FlowPanel selectedFilesPanel = toolbars_ != null ? 
            toolbars_.getSelectedFilesPanel() : null;
         aiContext_.loadContextItems(selectedFilesPanel);
      }
      
      lifecycle_.onLoad();
   }
   
   // Returns the window of the frame
   public WindowEx getFrameWindow() {
      return frame_.getWindow();
   }

   private String getTerm()
   {
      return eventHandlers_.getTerm();
   }
   
   public void findNext()
   {
      eventHandlers_.findNext();
   }

   public void findPrev()
   {
      eventHandlers_.findPrev();
   }

   public void performFind(String term,
                            boolean forwards,
                            boolean incremental)
   {
      WindowEx contentWindow = getContentWindow();
      if (contentWindow == null)
         return;

      // if this is an incremental search then reset the selection first
      if (incremental)
         contentWindow.removeSelection();

      contentWindow.find(term, false, !forwards, true, false);
   }

   // Firefox changes focus during our typeahead search (it must take
   // focus when you set the selection into the iframe) which breaks
   // typeahead entirely. rather than code around this we simply
   // disable it for Firefox
   public boolean isIncrementalFindSupported()
   {
      return eventHandlers_.isIncrementalFindSupported();
   }

   @Override
   public String getUrl()
   {
      return eventHandlers_.getUrl();
   }

   @Override
   public String getDocTitle()
   {
      return eventHandlers_.getDocTitle();
   }

   /**
    * Updates the title in the title label with the given conversation name
    * @param title The new title to display
    */
   public void updateTitle(String title)
   {
      if (title != null && !title.isEmpty()) {
         // Always update the overlay title that's visible to users
         if (overlayTitle_ != null) {
            // We need to preserve the arrow element when updating the text
            Element titleElement = overlayTitle_.getElement();
            Element arrowElement = null;
            
            // Look for the existing arrow span
            NodeList<Element> spans = titleElement.getElementsByTagName("span");
            for (int i = 0; i < spans.getLength(); i++) {
               Element span = spans.getItem(i);
               if (span.getClassName().contains("ai-dropdown-arrow")) {
                  arrowElement = span;
                  break;
               }
            }
            
            // Clear the inner HTML entirely
            titleElement.setInnerText("");
            
            // Add the text as a text node first
            titleElement.appendChild(Document.get().createTextNode(title));
            
            // If we found an arrow element, add it back
            if (arrowElement != null) {
               titleElement.appendChild(arrowElement);
            } else {
               // If no arrow was found, create a new one
               arrowElement = Document.get().createSpanElement();
               arrowElement.setClassName("ai-dropdown-arrow");
               arrowElement.getStyle().setDisplay(Style.Display.INLINE_BLOCK);
               arrowElement.getStyle().setMarginLeft(3, Unit.PX);
               arrowElement.getStyle().setPosition(Style.Position.RELATIVE);
               arrowElement.getStyle().setTop(0, Unit.PX);
               arrowElement.setInnerHTML("&#9662;"); // Unicode down triangle
               titleElement.appendChild(arrowElement);
            }
            
            // Ensure the title maintains proper styling
            titleElement.getStyle().setDisplay(Style.Display.INLINE_BLOCK);
            titleElement.getStyle().setProperty("maxWidth", "300px");
            titleElement.getStyle().setProperty("whiteSpace", "nowrap");
            titleElement.getStyle().setProperty("textOverflow", "ellipsis");
            titleElement.getStyle().setProperty("overflow", "hidden");
            titleElement.getStyle().setPaddingRight(0, Unit.PX);
            titleElement.getStyle().setMarginRight(0, Unit.PX);
            titleElement.getStyle().setCursor(Style.Cursor.POINTER);
            titleElement.getStyle().setFontSize(13, Unit.PX);
            
            // Store the title for session preservation
            conversationsManager_.storeConversationTitle(title);
         }
         
         // Also update the original title reference which might be used elsewhere
         if (title_ != null) {
            title_.setText(title);
         }
      }
   }
   

   // OLD SCROLL SAVING METHOD REMOVED - scroll positions now saved by AiStreamingPanel

   public void updateTitle2(String title2)
   {
      if (title2_ != null)
      {
         title2_.setText(title2);
      }
   }

   // Makes cursor focus on the search input
   @Override
   public void focusSearchAi()
   {
      eventHandlers_.focusSearchAi();
   }

   @Override
   public void showAi(String url)
   {
      eventHandlers_.showAi(url);
   }

   // Refreshes the AI pane
   @Override
   public void refresh()
   {
      // Load context items to synchronize UI with R session state
      if (aiContext_ != null) {
         FlowPanel selectedFilesPanel = toolbars_ != null ? 
            toolbars_.getSelectedFilesPanel() : null;
         aiContext_.loadContextItems(selectedFilesPanel);
      }
      
      // If this is being called from startup initialization, show the API key management page
      if (!navigated_) {
         refreshApiKeyManagement();
      } else {
         // If called from the refresh button ("New conversation"), create a new conversation
         conversationsManager_.createNewConversation();
      }
   }

   /**
    * Creates a new conversation by calling the server and updating the UI
    */
   private void createNewConversation() {
      // Delegate to the conversations manager
      conversationsManager_.createNewConversation();
   }

   /**
    * Creates a brand new conversation without checking for existing empty ones
    */
   private void createBrandNewConversation() {
      // This is now handled by conversationsManager_
      conversationsManager_.createBrandNewConversation();
   }

   public WindowEx getContentWindow()
   {
      return getIFrameEx() != null ? getIFrameEx().getContentWindow() : null;
   }


   @Override
   public void print()
   {
      getContentWindow().focus();
      getContentWindow().print();
   }

   @Override
   public void popout()
   {
      String href = getContentWindow().getLocationHref();
      NewWindowOptions options = new NewWindowOptions();
      options.setName("aipanepopout_" + popoutCount_++);
      globalDisplay_.openWebMinimalWindow(href, false, 0, 0, options);
   }

   @Override
   public void focus()
   {
      WindowEx contentWindow = getContentWindow();
      if (contentWindow != null)
         contentWindow.focus();
   }

   @Override
   public HandlerRegistration addAiNavigateHandler(AiNavigateEvent.Handler handler)
   {
      return addHandler(handler, AiNavigateEvent.TYPE);
   }


   @Override
   public LinkMenu getHistory()
   {
      return history_;
   }

   @Override
   public LinkMenu getAttachments()
   {
      return attachmentsMenu_;
   }

   @Override
   public boolean navigated()
   {
      return navigated_;
   }

   public IFrameElementEx getIFrameEx()
   {
      return frame_.getElement().cast();
   }

   private void findInTopic(String term, CanFocus findInputSource)
   {
      // get content window
      WindowEx contentWindow = getContentWindow();
      if (contentWindow == null)
         return;

      if (!contentWindow.find(term, false, false, true, false))
      {
         globalDisplay_.showMessage(MessageDialog.INFO,
               constants_.findInTopicLabel(),
               constants_.noOccurrencesFoundMessage(),
               findInputSource);
      }
   }

   private final native void replaceFrameUrl(JavaScriptObject frame, String url) /*-{
      frame.contentWindow.setTimeout(function() {
         this.location.replace(url);
      }, 0);
   }-*/;

   // Make these methods public for AiPaneEventHandlers with different names to avoid recursion
   
   /**
    * Configures an input element to support multiline text with auto-expanding height
    * by replacing it with a textarea that has similar attributes
    */
   public void configureInputForMultiline(Element input) {
      scrollHandler_.configureInputForMultiline(input);
   }

   /**
    * Updates the size of the South panel in the DockLayoutPanel
    */
   public void updateSouthPanelSize(int newHeight) {
      scrollHandler_.updateSouthPanelSize(newHeight);
   }

   // Accessors for AiPaneScroll
   public Timer getScrollTimer() {
      return scrollTimer_;
   }
   
   public DockLayoutPanel getMainPanel() {
      return mainPanel;
   }
   
   public SimplePanel getSearchContainer() {
      return searchContainer;
   }

   // Static reference to current instance for JSNI callbacks
   private static AiPane currentInstance_;
   
   // Static initializer to set up global JavaScript functions
   static {
      setupGlobalJavaScriptFunctions();
   }
   
   private static native void setupGlobalJavaScriptFunctions() /*-{
      // Set up the global aiSetModel function that will be available to all frames
      $wnd.aiSetModel = $entry(function(provider, model) {
         @org.rstudio.studio.client.workbench.views.ai.AiPane::setModelStatic(Ljava/lang/String;Ljava/lang/String;)(provider, model);
      });
   }-*/;
   
   public static AiPane getCurrentInstance() {
      return currentInstance_;
   }
   
   public static void setModelStatic(String provider, String model) {
      if (currentInstance_ != null) {
         currentInstance_.handleSetModel(provider, model);
      }
   }

   public interface Styles extends CssResource
   {
      String findTopicTextbox();
      String topicNavigationButton();
      String topicTitle();
      String bottomSearchWidget();
   }

   public interface EditorStyles extends CssResource
   {
      // No specific methods needed as the editor styles are accessed directly as text
   }

   public interface Resources extends ClientBundle
   {
      @Source("AiPane.css")
      Styles styles();

      @Source("AiPane.css")
      EditorStyles editorStyles();
   }

   private static final Resources RES = GWT.create(Resources.class);
   static { RES.styles().ensureInjected(); }

   private UserPrefs prefs_;


   private final AiToolbarLinkMenu history_;
   private final AiAttachmentsMenu attachmentsMenu_;
   private final AiImagesMenu imagesMenu_;
   /* Package-private to allow access from AiPaneLifecycle */
   Label title_;
   Label overlayTitle_; // Stable title that doesn't change during navigation
   Label title2_;
   private RStudioThemedFrame frame_;
   private RStudioThemedFrame backgroundFrame_; // Background iframe for loading content
   /* Package-private to allow access from AiPaneLifecycle */
   FindTextBox findTextBox_;
   private final Provider<AiSearch> searchProvider_;
   private GlobalDisplay globalDisplay_;
   private final Commands commands_;
   private boolean navigated_;
   private String targetUrl_;
   private Point scrollPos_;
   private Timer scrollTimer_;
   private static int popoutCount_ = 0;
   private SearchDisplay searchWidget_;
   private static final AiConstants constants_ = GWT.create(AiConstants.class);
   private AiServerOperations server_;
   private DockLayoutPanel mainPanel;
   private SimplePanel searchContainer;
   /* Package-private to allow access from AiPaneEventHandlers */
   AiPaneEventHandlers eventHandlers_;

   // Methods for AiPaneLifecycle to access
   Point getScrollPos() {
      return scrollPos_;
   }
   
   SearchDisplay getSearchWidget() {
      return searchWidget_;
   }
   
   Label getTitleLabel() {
      return title_;
   }

   Label getTitleLabel2() {
      return title2_;
   }
   
   AiConstants getConstants() {
      return constants_;
   }

   /* Package-private to allow access from AiPaneEventHandlers */
   AiPaneLifecycle lifecycle_;

   public boolean isSelected() {
      return lifecycle_.isSelected();
   }

   // Methods that need to be public to be accessed by AiPaneEventHandlers
   public RStudioThemedFrame getFrame() {
      return frame_;
   }

   /**
    * Returns the background frame
    */
   public RStudioThemedFrame getBackgroundFrame() {
      return backgroundFrame_;
   }

   /**
    * Returns the IFrameElementEx for the background frame
    */
   public IFrameElementEx getBackgroundIFrameEx() {
      return backgroundFrame_.getIFrame().cast();
   }



   public void bringToFront() {
      ensureWidget();
   }

   // Expose navigated_ field
   public boolean getNavigated() {
      return navigated_;
   }
   
   public void setNavigated(boolean navigated) {
      navigated_ = navigated;
   }
   
   /* Handles responses and related functionality */
   private AiPaneResponses responses_;

   // Methods that were moved to AiPaneEventHandlers - delegate to event handlers
   public void setLocation(final String url, final Point scrollPos)
   {
      eventHandlers_.setLocation(url, scrollPos);
   }

   private native static void callCallback(JavaScriptObject callback, String filename) /*-{
      callback(filename);
   }-*/;

   private AiPaneScroll scrollHandler_;

   // Conversations manager
   private AiPaneConversations conversationsManager_;
   
   /**
    * Gets the conversations manager for accessing conversation-related functionality
    * @return The AiPaneConversations instance
    */
   public AiPaneConversations getConversationsManager() {
      return conversationsManager_;
   }
   
   /**
    * Refreshes the API key management view by requesting the page content from server
    * and setting the location to display it.
    */
   public void refreshApiKeyManagement() {
      // Immediately hide the search container - don't wait for page load
      hideSearchContainer();
      
      // Set the title to "API Key Management"
      updateTitle("API Key Management");
      
      server_.getApiKeyManagement(new ServerRequestCallback<org.rstudio.studio.client.workbench.views.ai.model.ApiKeyManagementResult>() {
         @Override
         public void onResponseReceived(org.rstudio.studio.client.workbench.views.ai.model.ApiKeyManagementResult result) {
            if (result.getSuccess()) {
               // Use direct frame loading instead of complex setLocation with background loading
               // This bypasses the issues that were causing safety timeouts
               String path = result.getPath();
               getFrame().setUrl(path);
            } else {
               // Fallback to static page if dynamic generation fails
               getFrame().setUrl("ai/doc/html/api_key_management.html");
            }
         }
         
         @Override
         public void onError(ServerError error) {
            // Fallback to static page if there's an error
            getFrame().setUrl("ai/doc/html/api_key_management.html");
         }
      });
   }

   /**
    * Gets the responses handler for accessing response-related functionality
    * @return The AiPaneResponses instance
    */
   public AiPaneResponses getResponses()
   {
      return responses_;
   }

   /**
    * Creates a JavaScript callback that can be called from JSNI
    */
   private native void exportJSCallbackMethods() /*-{
      var thiz = this;
      
      $wnd.aiAcceptEditFileCommand = $entry(function(editedCode, messageId) {
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::handleAcceptEditFileCommand(Ljava/lang/String;Ljava/lang/String;)(editedCode, messageId);
      });
      
      $wnd.aiGetFileNameForMessageId = $entry(function(messageId, callback) {
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::handleGetFileNameForMessageId(Ljava/lang/String;Lcom/google/gwt/core/client/JavaScriptObject;)(messageId, callback);
      });
      
      $wnd.aiRevertMessage = $entry(function(messageId) {
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::handleRevertMessage(Ljava/lang/String;)(messageId);
      });
      
      $wnd.aiSaveApiKey = $entry(function(provider, key) {
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::handleSaveApiKey(Ljava/lang/String;Ljava/lang/String;)(provider, key);
      });
      
      $wnd.aiDeleteApiKey = $entry(function(provider) {
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::handleDeleteApiKey(Ljava/lang/String;)(provider);
      });
      
      $wnd.aiSetActiveProvider = $entry(function(provider) {
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::handleSetActiveProvider(Ljava/lang/String;)(provider);
      });
      
      $wnd.aiSetModel = $entry(function(provider, model) {
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::handleSetModel(Ljava/lang/String;Ljava/lang/String;)(provider, model);
      });
      
      $wnd.aiSetWorkingDirectory = $entry(function(dir) {
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::handleSetAiWorkingDirectory(Ljava/lang/String;)(dir);
      });

      $wnd.aiMarkButtonAsRun = function(messageId, buttonType) {
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::handleMarkButtonAsRun(*)(messageId, buttonType);
      }
      
      $wnd.aiRevertConfirmation = $entry(function(messageId) {
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::handleAiRevertConfirmation(Ljava/lang/String;)(messageId);
      });
      
      $wnd.aiCreateUserRevertButton = $entry(function(messageId) {
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::handleCreateUserRevertButton(Ljava/lang/String;)(messageId);
      });
      
      // Export the aiBrowseDirectory method
      $wnd.aiBrowseDirectory = function() {
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::handleBrowseDirectory()();
      };
      
      // Export the aiBrowseForFile method
      $wnd.aiBrowseForFile = $entry(function(selectedFilesPanelId) {
         // If a panel ID is provided, we'll use that specific panel
         // Otherwise we use the default panel from the UI
         if (selectedFilesPanelId) {
            var panel = $doc.getElementById(selectedFilesPanelId);
            if (panel) {
               // Implementation with panel element would go here
               // For now we'll just use the default implementation
               thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::aiContext_
                  .@org.rstudio.studio.client.workbench.views.ai.AiContext::handleBrowseForFile()();
            } else {
               thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::aiContext_
                  .@org.rstudio.studio.client.workbench.views.ai.AiContext::handleBrowseForFile()();
            }
         } else {
            thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::aiContext_
               .@org.rstudio.studio.client.workbench.views.ai.AiContext::handleBrowseForFile()();
         }
      });
      
      $wnd.aiAcceptConsoleCommand = $entry(function(messageId, editedCommand) {
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::handleAcceptConsoleCommand(Ljava/lang/String;Ljava/lang/String;)(messageId, editedCommand || "");
      });
      
      $wnd.aiCancelConsoleCommand = $entry(function(messageId) {
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::handleCancelConsoleCommand(Ljava/lang/String;)(messageId);
      });
      
      $wnd.aiAcceptTerminalCommand = $entry(function(messageId, editedCommand) {
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::handleAcceptTerminalCommand(Ljava/lang/String;Ljava/lang/String;)(messageId, editedCommand || "");
      });
      
      $wnd.aiCancelTerminalCommand = $entry(function(messageId) {
         thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::handleCancelTerminalCommand(Ljava/lang/String;)(messageId);
      });
      

      
      // Export sequence-based event handling for the new buffering system
      $wnd.aiAddOperationEvent = $entry(function(sequence, operationType, messageId, command, explanation, requestId, filename, content) {
         var streamingPanel = thiz.@org.rstudio.studio.client.workbench.views.ai.AiPane::getStreamingPanel()();
         if (streamingPanel) {
            streamingPanel.@org.rstudio.studio.client.workbench.views.ai.widgets.AiStreamingPanel::addOperationEvent(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)(sequence || 0, operationType || "", messageId || "", command || "", explanation || "", requestId || "", filename || "", content || "");
         }
      });
   }-*/;

   /**
    * Get the ID of the currently displayed conversation
    * @return The conversation ID
    */
   public void getCurrentConversationId(ServerRequestCallback<Double> callback) {
      // Use the conversations manager to get the current conversation ID
      if (conversationsManager_ != null) {
         conversationsManager_.getCurrentConversationIndex(callback);
      } else {
         callback.onResponseReceived(0.0);
      }
   }

   // Add a new private member variable for attachments manager
   private AiPaneAttachments attachmentsManager_;

   // Add a new private member variable for images manager
   private AiPaneImages imagesManager_;

   // Add a private member for the attachments menu button
   private Element attachmentsMenuButton_;



   // Add the AiToolbars member variable after the other member declarations
   private AiToolbars toolbars_;
   


   // Add a method to update the attachments list
   public void refreshAttachmentsList() 
   {
      if (attachmentsManager_ != null && attachmentsMenu_ != null && searchToolbar_ != null) {
         // Load the attachments first
         server_.listAttachments(new ServerRequestCallback<JsArrayString>() {
            @Override
            public void onResponseReceived(JsArrayString response) {
               // Update the menu with the loaded attachments
               attachmentsManager_.loadAttachments(attachmentsMenu_);
               
               // Add a handler to revert the arrow direction when the menu closes
               if (attachmentsMenu_.getMenu() != null) {
                  attachmentsMenu_.getMenu().addCloseHandler(event -> {
                     // Find and update the arrow to point upward when menu closes
                     if (title2_ != null) {
                        Element titleEl = title2_.getElement();
                        for (int i = 0; i < titleEl.getChildCount(); i++) {
                           com.google.gwt.dom.client.Node node = titleEl.getChild(i);
                           if (Element.is(node)) {
                              Element elem = Element.as(node);
                              if (elem.getClassName().contains("ai-attachment-dropdown-arrow")) {
                                 elem.setInnerHTML("&#9652;"); // Reset to up triangle
                                 break;
                              }
                           }
                        }
                     }
                  });
               }
               
               // Update menu visibility based on attachment count
               if (response == null || response.length() == 0) {
                  // If we have an attachment menu widget, hide it
                  if (attachmentMenuContainer_ != null) {
                     attachmentMenuContainer_.clear();
                  }
               } else {
                  // There are attachments, make sure the menu is added
                  if (attachmentMenuContainer_ != null) {
                     // Create a new menu label
                     title2_ = new Label();
                     title2_.addStyleName(RES.styles().topicTitle());
                     
                     // Set the text to the most recently added attachment
                     FileSystemItem file = FileSystemItem.createFile(response.get(response.length() - 1));
                     title2_.setText(file.getName());
                     
                     // Style the label to show it's a menu
                     Element titleElement = title2_.getElement();
                     titleElement.getStyle().setMarginLeft(0, Unit.PX);
                     titleElement.getStyle().setCursor(Style.Cursor.POINTER);
                     
                     // Add a triangle pointing upward using an inline-block element
                     Element arrowSpan = Document.get().createSpanElement();
                     arrowSpan.setClassName("ai-attachment-dropdown-arrow");
                     arrowSpan.getStyle().setDisplay(Style.Display.INLINE_BLOCK);
                     arrowSpan.getStyle().setMarginLeft(3, Unit.PX);
                     arrowSpan.getStyle().setPosition(Style.Position.RELATIVE);
                     arrowSpan.getStyle().setTop(0, Unit.PX);
                     arrowSpan.setInnerHTML("&#9652;"); // Unicode up triangle
                     titleElement.appendChild(arrowSpan);
                     
                     // Create a wrapper for the label
                     SimpleMenuLabel menuLabel = new SimpleMenuLabel(title2_);
                     
                     // Create a clickable widget that shows the menu
                     attachmentMenuWidget_ = menuLabel;
                     attachmentMenuWidget_.addDomHandler(new ClickHandler() {
                        @Override
                        public void onClick(ClickEvent event) {
                           // Position the menu with smart placement (up or down based on available space)
                           attachmentsMenu_.getMenu().setPopupPositionAndShow(new PopupPanel.PositionCallback() {
                              @Override
                              public void setPosition(int offsetWidth, int offsetHeight) {
                                 Element titleEl = title2_.getElement();
                                 int left = titleEl.getAbsoluteLeft();
                                 
                                 // Get window dimensions and element position
                                 int windowHeight = Window.getClientHeight();
                                 int topPosition = titleEl.getAbsoluteTop();
                                 int elementHeight = titleEl.getOffsetHeight();
                                 int bottomPosition = topPosition + elementHeight;
                                 
                                 // Calculate space above and below
                                 int spaceAbove = topPosition;
                                 int spaceBelow = windowHeight - bottomPosition;
                                 
                                 // Determine if we should show menu upward or downward
                                 if (spaceBelow < offsetHeight && spaceAbove > offsetHeight) {
                                    // Not enough space below but enough space above - show upward
                                    attachmentsMenu_.getMenu().setPopupPosition(left, topPosition - offsetHeight);
                                    
                                    // Update the arrow to point downward when menu is above
                                    for (int i = 0; i < titleEl.getChildCount(); i++) {
                                       com.google.gwt.dom.client.Node node = titleEl.getChild(i);
                                       if (Element.is(node)) {
                                          Element elem = Element.as(node);
                                          if (elem.getClassName().contains("ai-attachment-dropdown-arrow")) {
                                             elem.setInnerHTML("&#9662;"); // Down triangle
                                             break;
                                          }
                                       }
                                    }
                                 } else {
                                    // Default: show downward
                                    attachmentsMenu_.getMenu().setPopupPosition(left, bottomPosition);
                                    
                                    // Ensure arrow is pointing upward when menu is below
                                    for (int i = 0; i < titleEl.getChildCount(); i++) {
                                       com.google.gwt.dom.client.Node node = titleEl.getChild(i);
                                       if (Element.is(node)) {
                                          Element elem = Element.as(node);
                                          if (elem.getClassName().contains("ai-attachment-dropdown-arrow")) {
                                             elem.setInnerHTML("&#9652;"); // Up triangle
                                             break;
                                          }
                                       }
                                    }
                                 }
                              }
                           });
                        }
                     }, ClickEvent.getType());
                     
                     // Clear and add to container
                     attachmentMenuContainer_.clear();
                     attachmentMenuContainer_.add(attachmentMenuWidget_);
                  }
               }
            }
            
            @Override
            public void onError(ServerError error) {
               // On error, clear the menu container
               if (attachmentMenuContainer_ != null) {
                  attachmentMenuContainer_.clear();
               }
               
               globalDisplay_.showErrorMessage("Error", "Failed to load attachments: " + error.getMessage());
            }
         });
      }
   }

   // Add a method to update the images list
   public void refreshImagesList() 
   {
      if (imagesManager_ != null && imagesMenu_ != null && toolbars_ != null) {
         // Get the image menu container from toolbars
         SimplePanel imageMenuContainer = toolbars_.getImageMenuContainer();
         if (imageMenuContainer == null) return;
         
         // Load the images first
         server_.listImages(new ServerRequestCallback<JsArrayString>() {
            @Override
            public void onResponseReceived(JsArrayString response) {
               // Update the menu with the loaded images
               imagesManager_.loadImages(imagesMenu_);
               
               // Add a handler to revert the arrow direction when the menu closes
               if (imagesMenu_.getMenu() != null) {
                  imagesMenu_.getMenu().addCloseHandler(event -> {
                     // Find and update the arrow to point upward when menu closes
                     if (imageMenuLabel_ != null) {
                        Element titleEl = imageMenuLabel_.getElement();
                        for (int i = 0; i < titleEl.getChildCount(); i++) {
                           com.google.gwt.dom.client.Node node = titleEl.getChild(i);
                           if (Element.is(node)) {
                              Element elem = Element.as(node);
                              if (elem.getClassName().contains("ai-image-dropdown-arrow")) {
                                 elem.setInnerHTML("&#9652;"); // Reset to up triangle
                                 break;
                              }
                           }
                        }
                     }
                  });
               }
               
               // Update menu visibility based on image count
               if (response == null || response.length() == 0) {
                  // If we have an image menu widget, hide it
                  if (imageMenuContainer != null) {
                     imageMenuContainer.clear();
                  }
               } else {
                  // There are images, make sure the menu is added
                  if (imageMenuContainer != null) {
                     // Create a new menu label
                     imageMenuLabel_ = new Label();
                     imageMenuLabel_.addStyleName(RES.styles().topicTitle());
                     
                     // Set the text to show count of attached images
                     int imageCount = response.length();
                     String labelText;
                     if (imageCount == 1) {
                        labelText = "1 image attached";
                     } else {
                        labelText = imageCount + " images attached";
                     }
                     imageMenuLabel_.setText(labelText);
                     
                     // Style the label to show it's a menu
                     Element titleElement = imageMenuLabel_.getElement();
                     titleElement.getStyle().setMarginLeft(0, Unit.PX);
                     titleElement.getStyle().setCursor(Style.Cursor.POINTER);
                     
                     // Add a triangle pointing upward using an inline-block element
                     Element arrowSpan = Document.get().createSpanElement();
                     arrowSpan.setClassName("ai-image-dropdown-arrow");
                     arrowSpan.getStyle().setDisplay(Style.Display.INLINE_BLOCK);
                     arrowSpan.getStyle().setMarginLeft(3, Unit.PX);
                     arrowSpan.getStyle().setPosition(Style.Position.RELATIVE);
                     arrowSpan.getStyle().setTop(0, Unit.PX);
                     arrowSpan.setInnerHTML("&#9652;"); // Unicode up triangle
                     titleElement.appendChild(arrowSpan);
                     
                     // Create a wrapper for the label
                     SimpleMenuLabel menuLabel = new SimpleMenuLabel(imageMenuLabel_);
                     
                     // Create a clickable widget that shows the menu
                     imageMenuWidget_ = menuLabel;
                     imageMenuWidget_.addDomHandler(new ClickHandler() {
                        @Override
                        public void onClick(ClickEvent event) {
                           // Position the menu with smart placement (up or down based on available space)
                           imagesMenu_.getMenu().setPopupPositionAndShow(new PopupPanel.PositionCallback() {
                              @Override
                              public void setPosition(int offsetWidth, int offsetHeight) {
                                 Element titleEl = imageMenuLabel_.getElement();
                                 int left = titleEl.getAbsoluteLeft();
                                 
                                 // Get window dimensions and element position
                                 int windowHeight = Window.getClientHeight();
                                 int topPosition = titleEl.getAbsoluteTop();
                                 int elementHeight = titleEl.getOffsetHeight();
                                 int bottomPosition = topPosition + elementHeight;
                                 
                                 // Calculate space above and below
                                 int spaceAbove = topPosition;
                                 int spaceBelow = windowHeight - bottomPosition;
                                 
                                 // Determine if we should show menu upward or downward
                                 if (spaceBelow < offsetHeight && spaceAbove > offsetHeight) {
                                    // Not enough space below but enough space above - show upward
                                    imagesMenu_.getMenu().setPopupPosition(left, topPosition - offsetHeight);
                                    
                                    // Update the arrow to point downward when menu is above
                                    for (int i = 0; i < titleEl.getChildCount(); i++) {
                                       com.google.gwt.dom.client.Node node = titleEl.getChild(i);
                                       if (Element.is(node)) {
                                          Element elem = Element.as(node);
                                          if (elem.getClassName().contains("ai-image-dropdown-arrow")) {
                                             elem.setInnerHTML("&#9662;"); // Down triangle
                                             break;
                                          }
                                       }
                                    }
                                 } else {
                                    // Default: show downward
                                    imagesMenu_.getMenu().setPopupPosition(left, bottomPosition);
                                    
                                    // Ensure arrow is pointing upward when menu is below
                                    for (int i = 0; i < titleEl.getChildCount(); i++) {
                                       com.google.gwt.dom.client.Node node = titleEl.getChild(i);
                                       if (Element.is(node)) {
                                          Element elem = Element.as(node);
                                          if (elem.getClassName().contains("ai-image-dropdown-arrow")) {
                                             elem.setInnerHTML("&#9652;"); // Up triangle
                                             break;
                                          }
                                       }
                                    }
                                 }
                              }
                           });
                        }
                     }, ClickEvent.getType());
                     
                     // Clear and add to container
                     imageMenuContainer.clear();
                     imageMenuContainer.add(imageMenuWidget_);
                  }
               }
            }
            
            @Override
            public void onError(ServerError error) {
               // On error, clear the menu container
               SimplePanel imageMenuContainer = toolbars_.getImageMenuContainer();
               if (imageMenuContainer != null) {
                  imageMenuContainer.clear();
               }
               // Don't show error message for images - fail silently
            }
         });
      }
   }

   private Toolbar searchToolbar_;
   private SimplePanel attachmentMenuContainer_;
   private Widget attachmentMenuWidget_;
   private Label imageMenuLabel_;
   private Widget imageMenuWidget_;
   private Widget cancelButton_;
   private boolean cancelButtonShown_ = false;
   private SmallButton toggleFrameButton_;

   @Override
   public void refreshIframe()
   {      
      // Load context items to synchronize UI with R session state
      if (aiContext_ != null) {
         FlowPanel selectedFilesPanel = toolbars_ != null ? 
            toolbars_.getSelectedFilesPanel() : null;
         aiContext_.loadContextItems(selectedFilesPanel);
      }
   }

   /**
    * Gets the AiSearch instance from the searchProvider
    */
   public AiSearch getSearch() {
      return searchProvider_.get();
   }

   // Add this method to get event handlers
   public AiPaneEventHandlers getEventHandlers()
   {
      return eventHandlers_;
   }

   /**
    * Shows a cancel button in the search toolbar
    */
   public void showCancelButton() {
      // Use the toolbars' method to transform the button to cancel mode
      if (toolbars_ != null) {
         toolbars_.setButtonToCancelMode();
         cancelButtonShown_ = true;
      }
   }
   
   /**
    * Hides the cancel button from the search toolbar
    */
   public void hideCancelButton() {
      // Use the toolbars' method to transform the button back to send mode
      if (toolbars_ != null) {
         toolbars_.forceButtonToSendMode();
         cancelButtonShown_ = false;
      }
   }
   
   /**
    * Hides the search container completely for pages like API key management
    */
   public void hideSearchContainer() {
      if (searchContainer != null) {
         Element style = searchContainer.getElement();
         
         // Apply inline styles directly to get !important effect
         style.setAttribute("style", 
            "height: 0px !important; " +
            "min-height: 0px !important; " +
            "max-height: 0px !important; " +
            "opacity: 0 !important; " +
            "visibility: hidden !important; " +
            "overflow: hidden !important; " +
            "position: absolute !important; " +
            "z-index: -1000 !important; " +
            "pointer-events: none !important; " +
            "display: none !important; " +
            "border-top: none !important; " +
            "border-bottom: none !important; " +
            "background-color: transparent !important;");
         
         // Also update the DockLayoutPanel size for the search container
         if (mainPanel != null) {
            mainPanel.setWidgetSize(searchContainer, 0);
            mainPanel.forceLayout();
         }
      }
   }
   
   /**
    * Restores the search container to normal visibility
    */
   public void restoreSearchContainer() {
      // First check if we're on the API key management page in either frame
      if (isLoadingApiKeyManagement()) {
         // Keep the search container hidden if API key management is showing
         hideSearchContainer();
         return;
      }
      
      if (searchContainer != null) {
         // Calculate an appropriate height - either current (if valid) or minimum 100px
         int heightToUse = Math.max(100, getCurrentSearchContainerHeight());
         
         Element style = searchContainer.getElement();
         
         // Reset all styles with a fresh inline style attribute
         style.setAttribute("style", 
            "visibility: visible; " +
            "opacity: 1; " +
            "height: auto; " +
            "min-height: " + heightToUse + "px; " +
            "position: relative; " +
            "z-index: 100; " +
            "background-color: #ffffff; " +
            "border-top: none; " +
            "border-bottom: none; " +
            "transition: opacity 0.1s ease;");
         
         // Restore appropriate size in the DockLayoutPanel
         if (mainPanel != null) {
            mainPanel.setWidgetSize(searchContainer, heightToUse);
            mainPanel.forceLayout();
         }
      }
   }

   private native boolean getBooleanProperty(JavaScriptObject obj, String property) /*-{
      if (obj && obj.hasOwnProperty(property))
         return obj[property];
      return false;
   }-*/;
   
   private native String getStringProperty(JavaScriptObject obj, String property) /*-{
      if (obj && obj.hasOwnProperty(property))
         return obj[property];
      return null;
   }-*/;
   
   private final native boolean hasProperty(JavaScriptObject obj, String property) /*-{
      return obj && obj.hasOwnProperty(property);
   }-*/;

   /**
    * Shows the background frame and hides the main frame
    */
   public void showBackgroundFrame() {
      // Before switching frames, check if main frame has preserved height that needs transferring
      WindowEx contentWindow = getContentWindow();
      if (contentWindow != null) {
         transferPreservedHeightBetweenFrames(contentWindow, getBackgroundIFrameEx().getContentWindow());
      }
      
      frame_.getElement().getStyle().setDisplay(Display.NONE);
      backgroundFrame_.getElement().getStyle().setDisplay(Display.BLOCK);
      
      // Check if background frame is showing API key management page
      WindowEx bgWindow = getBackgroundIFrameEx().getContentWindow();
      if (bgWindow != null) {
         String url = bgWindow.getLocationHref();
         if (url != null && 
             (url.contains("api_key_management.html") || 
              url.contains("ai/doc/html/api_key_management"))) {
            // Hide search container when switching to API key page
            hideSearchContainer();
         } else {
            // Ensure search container maintains consistent height during frame switch
            // Use current height rather than a fixed value
            if (searchContainer != null && mainPanel != null) {
               int currentHeight = getCurrentSearchContainerHeight();
               mainPanel.setWidgetSize(searchContainer, currentHeight);
               mainPanel.forceLayout();
               searchContainer.getElement().getStyle().setProperty("minHeight", currentHeight + "px");
            }
         }
      }
   }

   /**
    * Shows the main frame and hides the background frame
    */
   public void showMainFrame() {
      // Before switching frames, check if background frame has preserved height that needs transferring
      WindowEx bgContentWindow = getBackgroundIFrameEx().getContentWindow();
      if (bgContentWindow != null) {
         transferPreservedHeightBetweenFrames(bgContentWindow, getContentWindow());
      }
      
      backgroundFrame_.getElement().getStyle().setDisplay(Display.NONE);
      frame_.getElement().getStyle().setDisplay(Display.BLOCK);
      
      // Check if main frame is showing API key management page
      WindowEx window = getContentWindow();
      if (window != null) {
         String url = window.getLocationHref();
         if (url != null && 
             (url.contains("api_key_management.html") || 
              url.contains("ai/doc/html/api_key_management"))) {
            // Hide search container when switching to API key page
            hideSearchContainer();
         } else {
            // Ensure search container maintains consistent height during frame switch
            // Use current height rather than a fixed value
            if (searchContainer != null && mainPanel != null) {
               int currentHeight = getCurrentSearchContainerHeight();
               mainPanel.setWidgetSize(searchContainer, currentHeight);
               mainPanel.forceLayout();
               searchContainer.getElement().getStyle().setProperty("minHeight", currentHeight + "px");
            }
         }
      }
   }
   
   /**
    * Transfers preserved height attributes between frames to ensure consistent sizing
    */
   private native void transferPreservedHeightBetweenFrames(WindowEx sourceWindow, WindowEx targetWindow) /*-{
      try {
         if (!sourceWindow || !sourceWindow.document || !targetWindow || !targetWindow.document) {
            return;
         }
         
         // Get conversation containers in both windows
         var sourceContainer = sourceWindow.document.querySelector('.conversation-container');
         var targetContainer = targetWindow.document.querySelector('.conversation-container');
         
         if (!sourceContainer || !targetContainer) {
            return;
         }
         
         // Check if source has preserved height attributes
         var hasPreservedHeight = sourceContainer.getAttribute('data-height-preserved') === 'true';
         var preservedHeight = parseInt(sourceContainer.getAttribute('data-preserved-height'), 10);
         
         if (hasPreservedHeight && !isNaN(preservedHeight) && preservedHeight > 0) {
            
            // Transfer the attributes to the target container
            targetContainer.setAttribute('data-height-preserved', 'true');
            targetContainer.setAttribute('data-preserved-height', preservedHeight);
            targetContainer.style.minHeight = preservedHeight + "px";
            
            // Also transfer the custom property for JS access
            targetContainer._preservedHeight = preservedHeight;
         } else {
            // Check if source has min-height style that should be transferred
            var sourceMinHeight = sourceContainer.style.minHeight;
            if (sourceMinHeight && sourceMinHeight !== "0px" && sourceMinHeight !== "auto" && sourceMinHeight !== "") {
               targetContainer.style.minHeight = sourceMinHeight;
            }
         }
      } catch (e) {
         console.error("Error transferring preserved height between frames:", e);
      }
   }-*/;

   /**
    * Toggles which frame is visible
    */
   public void toggleFrames() {
      // Get the current display styles
      String frameDisplay = frame_.getElement().getStyle().getDisplay();
      String bgFrameDisplay = backgroundFrame_.getElement().getStyle().getDisplay();
      
      // Check which frame is currently visible
      if (frameDisplay == null || frameDisplay.isEmpty() || !frameDisplay.equals("none")) {
         showBackgroundFrame();
      } else {
         // Background frame is visible or both are hidden, switch to mai
         showMainFrame();
      }
   }

   /**
    * Gets the current height of the search container
    */
   private int getCurrentSearchContainerHeight() {
      if (searchContainer != null && mainPanel != null && searchContainer.isVisible()) {
         try {
            // Get the current assigned height from the DockLayoutPanel and convert to int properly
            double size = mainPanel.getWidgetSize(searchContainer);
            return (int) Math.round(size);
         } catch (Exception e) {
            // Default to 100px if there's any error
            return 100;
         }
      }
      return 100; // Default height
   }
   
   /**
    * Empty placeholder method to replace the native JS method
    * This will be compiled out by GWT but prevents compiler errors
    */
   private void positionDropdownArrow(Element arrowElement, Element labelElement) {
      // This method intentionally left empty
   }

   private AiContext aiContext_;
   private AiOrchestrator aiOrchestrator_;

   // Delegate methods that forward to eventHandlers_
   
   public void handleRevertMessage(String messageId) {
      // Retrieve the current active request ID
      String activeRequestId = getActiveRequestId();
      
      // Send cancellation via WebSocket if we have an active request
      if (activeRequestId != null && !activeRequestId.isEmpty()) {
         sendCancellationViaWebSocket(activeRequestId);
      }
      
      // Continue with existing revert message logic
      eventHandlers_.handleRevertMessage(messageId);
   }
   
   public void handleAiRevertConfirmation(String messageId) {
      // Retrieve the current active request ID
      String activeRequestId = getActiveRequestId();
      
      // Send cancellation via WebSocket if we have an active request
      if (activeRequestId != null && !activeRequestId.isEmpty()) {
         sendCancellationViaWebSocket(activeRequestId);
      }
      
      // Continue with existing revert confirmation logic
      eventHandlers_.handleAiRevertConfirmation(messageId);
   }
   
   public void handleGetFileNameForMessageId(String messageId, JavaScriptObject callback) {
      eventHandlers_.handleGetFileNameForMessageId(messageId, callback);
   }
   
   public void handleSaveApiKey(String provider, String key) {
      eventHandlers_.handleSaveApiKey(provider, key);
   }
   
   public void handleDeleteApiKey(String provider) {
      eventHandlers_.handleDeleteApiKey(provider);
   }
   
   public void handleSetActiveProvider(String provider) {
      eventHandlers_.handleSetActiveProvider(provider);
   }
   
   public void handleSetModel(String provider, String model) {
      eventHandlers_.handleSetModel(provider, model);
   }
   
   public void handleSetAiWorkingDirectory(String dir) {
      eventHandlers_.handleSetAiWorkingDirectory(dir);
   }
   
   public void handleMarkButtonAsRun(String messageId, String buttonType) {
      eventHandlers_.handleMarkButtonAsRun(messageId, buttonType);
   }
   
   public void handleBrowseDirectory() {
      eventHandlers_.handleBrowseDirectory();
   }
   
   public void handleAcceptConsoleCommand(String messageId, String editedCommand) {
      // IMMEDIATELY mark button as run to make it disappear - no advancement, just gone forever
      server_.markButtonAsRun(messageId, "accept", new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean result) {
            // Hide the buttons in the widget
            hideButtonsInWidget(messageId, "console");
            
            // Get the request ID from the console widget (like edit file widgets do)
            String requestId = null;
            AiStreamingPanel streamingPanel = getStreamingPanel();
            if (streamingPanel != null) {
               AiConsoleWidget consoleWidget = streamingPanel.getConsoleWidget(messageId);
               if (consoleWidget != null) {
                  requestId = consoleWidget.getRequestId();
               }
            }
            
            // requestId must always be present - if it's missing, this indicates a bug
            if (requestId == null || requestId.isEmpty()) {
               String errorMessage = "CRITICAL ERROR: Console command acceptance failed - missing request_id for messageId: " + messageId;
               Debug.log(errorMessage);
               globalDisplay_.showErrorMessage("Console Command Error", errorMessage);
               return;
            }
            
            // Initialize console tracking BEFORE starting the command
            responses_.initializeConsoleTracking();
            
            // Now proceed with the actual command execution
            server_.acceptConsoleCommand(messageId, editedCommand, requestId, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void result) {
                  // Start checking for console completion using the existing polling mechanism
                  responses_.startConsoleCheck(Integer.parseInt(messageId));
               }
               
               @Override
               public void onError(ServerError error) {
                  globalDisplay_.showErrorMessage("Error", "Failed to execute console command: " + error.getMessage());
                  // Note: Error state will be handled by polling mechanism in AiPaneResponses
               }
            });
         }
         
         @Override
         public void onError(ServerError error) {
            globalDisplay_.showErrorMessage("Error", "Failed to mark button as run: " + error.getMessage());
         }
      });
   }
   
   public void handleCancelConsoleCommand(String messageId) {
      // IMMEDIATELY mark button as run to make it disappear - no advancement, just gone forever
      server_.markButtonAsRun(messageId, "cancel", new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean result) {
            // Hide the buttons in the widget
            hideButtonsInWidget(messageId, "console");
            
            // Get the request ID from the console widget (like edit file widgets do)
            String requestId = null;
            AiStreamingPanel streamingPanel = getStreamingPanel();
            if (streamingPanel != null) {
               AiConsoleWidget consoleWidget = streamingPanel.getConsoleWidget(messageId);
               if (consoleWidget != null) {
                  requestId = consoleWidget.getRequestId();
               }
            }
            
            // requestId must always be present - if it's missing, this indicates a bug
            if (requestId == null || requestId.isEmpty()) {
               String errorMessage = "CRITICAL ERROR: Console command cancellation failed - missing request_id for messageId: " + messageId;
               Debug.log(errorMessage);
               globalDisplay_.showErrorMessage("Console Command Error", errorMessage);
               return;
            }
            
            // Now proceed with the actual cancellation
            server_.cancelConsoleCommand(messageId, requestId, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void result) {
                  // Start checking for console completion using the existing polling mechanism
                  responses_.startConsoleCheck(Integer.parseInt(messageId));
               }
               
               @Override
               public void onError(ServerError error) {
                  globalDisplay_.showErrorMessage("Error", "Failed to cancel console command: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onError(ServerError error) {
            globalDisplay_.showErrorMessage("Error", "Failed to mark button as run: " + error.getMessage());
         }
      });
   }
   
   public void handleAcceptTerminalCommand(String messageId, String editedCommand) {
      // IMMEDIATELY mark button as run to make it disappear - no advancement, just gone forever
      server_.markButtonAsRun(messageId, "accept", new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean result) {
            // Hide the buttons in the widget
            hideButtonsInWidget(messageId, "terminal");
            
            // Get the request ID from the terminal widget (like edit file widgets do)
            String requestId = null;
            AiStreamingPanel streamingPanel = getStreamingPanel();
            if (streamingPanel != null) {
               AiTerminalWidget terminalWidget = streamingPanel.getTerminalWidget(messageId);
               if (terminalWidget != null) {
                  requestId = terminalWidget.getRequestId();
               }
            }
            
            // requestId must always be present - if it's missing, this indicates a bug
            if (requestId == null || requestId.isEmpty()) {
               String errorMessage = "CRITICAL ERROR: Terminal command acceptance failed - missing request_id for messageId: " + messageId;
               Debug.log(errorMessage);
               globalDisplay_.showErrorMessage("Terminal Command Error", errorMessage);
               return;
            }
            
            // Now proceed with the actual command execution
            server_.acceptTerminalCommand(messageId, editedCommand, requestId, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void result) {
                  // Start checking for terminal completion using the existing polling mechanism
                  responses_.startTerminalCheck(Integer.parseInt(messageId));
               }
               
               @Override
               public void onError(ServerError error) {
                  globalDisplay_.showErrorMessage("Error", "Failed to execute terminal command: " + error.getMessage());
                  // Note: Error state will be handled by polling mechanism in AiPaneResponses
               }
            });
         }
         
         @Override
         public void onError(ServerError error) {
            globalDisplay_.showErrorMessage("Error", "Failed to mark button as run: " + error.getMessage());
         }
      });
   }
   
   public void handleCancelTerminalCommand(String messageId) {
      // IMMEDIATELY mark button as run to make it disappear - no advancement, just gone forever
      server_.markButtonAsRun(messageId, "cancel", new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean result) {
            // Hide the buttons in the widget
            hideButtonsInWidget(messageId, "terminal");
            
            // Get the request ID from the terminal widget (like edit file widgets do)
            String requestId = null;
            AiStreamingPanel streamingPanel = getStreamingPanel();
            if (streamingPanel != null) {
               AiTerminalWidget terminalWidget = streamingPanel.getTerminalWidget(messageId);
               if (terminalWidget != null) {
                  requestId = terminalWidget.getRequestId();
               }
            }
            
            // requestId must always be present - if it's missing, this indicates a bug
            if (requestId == null || requestId.isEmpty()) {
               String errorMessage = "CRITICAL ERROR: Terminal command cancellation failed - missing request_id for messageId: " + messageId;
               Debug.log(errorMessage);
               globalDisplay_.showErrorMessage("Terminal Command Error", errorMessage);
               return;
            }
            
            // Now proceed with the actual cancellation
            server_.cancelTerminalCommand(messageId, requestId, new ServerRequestCallback<java.lang.Void>() {
               @Override
               public void onResponseReceived(java.lang.Void result) {
                  // Start checking for terminal completion using the existing polling mechanism
                  responses_.startTerminalCheck(Integer.parseInt(messageId));
               }
               
               @Override
               public void onError(ServerError error) {
                  globalDisplay_.showErrorMessage("Error", "Failed to cancel terminal command: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onError(ServerError error) {
            globalDisplay_.showErrorMessage("Error", "Failed to mark button as run: " + error.getMessage());
         }
      });
   }

   /**
    * Returns the AiContext object
    */
   public AiContext getAiContext()
   {
      return aiContext_;
   }

   /**
    * Adds custom webkit scrollbar styles to an element for better horizontal scrollbar appearance
    * @param element The element to style
    */
   public native void addWebkitScrollbarStyles(Element element) /*-{
      try {
         // Add a custom style block specific to this element's ID
         if (!element.id) {
            element.id = "ai-files-panel-" + Math.floor(Math.random() * 10000);
         }
         
         var styleId = "style-" + element.id;
         var existingStyle = $doc.getElementById(styleId);
         
         // Remove existing style if it exists
         if (existingStyle) {
            existingStyle.parentNode.removeChild(existingStyle);
         }
         
         // Create new style element
         var style = $doc.createElement("style");
         style.id = styleId;
         style.innerHTML = "#" + element.id + "::-webkit-scrollbar { height: 4px; }" +
                           "#" + element.id + "::-webkit-scrollbar-thumb { background: #888888; border-radius: 2px; }" +
                           "#" + element.id + "::-webkit-scrollbar-track { background: #f0f0f0; }";
         
         // Add the style to the document head
         $doc.head.appendChild(style);
      } catch (e) {
         // Ignore any errors, this is just for enhanced styling
         console.error("Error applying webkit scrollbar styles:", e);
      }
   }-*/;

   // Override createMainWidget() to use the new AiToolbars implementation
   @Override
   protected Widget createMainWidget()
   {
      Widget widget = toolbars_.createMainWidget();
      
      // Set references to the widgets we need to access
      mainPanel = toolbars_.getMainPanel();
      searchContainer = toolbars_.getSearchContainer();
      searchWidget_ = toolbars_.getSearchWidget();
      searchToolbar_ = toolbars_.getSearchToolbar();
      attachmentMenuContainer_ = toolbars_.getAttachmentMenuContainer();
      title2_ = toolbars_.getTitle2();
      title_ = toolbars_.getTitle();
      overlayTitle_ = toolbars_.getOverlayTitle();
      findTextBox_ = toolbars_.getFindTextBox();
      
      return widget;
   }
   
   // Override createMainToolbar() to use the new AiToolbars implementation
   @Override
   protected Toolbar createMainToolbar()
   {
      return toolbars_.createMainToolbar();
   }

   /**
    * Checks if API key management is loading in either frame
    */
   public boolean isLoadingApiKeyManagement() {
      boolean isLoading = false;
      
      // Check main frame URL
      if (frame_ != null) {
         IFrameElementEx frameEx = getIFrameEx();
         if (frameEx != null) {
            WindowEx window = frameEx.getContentWindow();
            if (window != null) {
               String url = window.getLocationHref();
               if (url != null && 
                   (url.contains("api_key_management.html") || 
                    url.contains("ai/doc/html/api_key_management"))) {
                  isLoading = true;
               }
            }
         }
      }
      
      // Check background frame URL
      if (backgroundFrame_ != null) {
         IFrameElementEx bgFrameEx = getBackgroundIFrameEx();
         if (bgFrameEx != null) {
            WindowEx window = bgFrameEx.getContentWindow();
            if (window != null) {
               String url = window.getLocationHref();
               if (url != null && 
                   (url.contains("api_key_management.html") || 
                    url.contains("ai/doc/html/api_key_management"))) {
                  isLoading = true;
               }
            }
         }
      }
      
      return isLoading;
   }

   // Add a method to get the active request ID
   public native String getActiveRequestId() /*-{
      // Access only from the global variable
      var requestId = $wnd.activeAiRequestId || null;
      return requestId;
   }-*/;

   // Add a method to store the active request ID
   public native void storeActiveRequestId(String requestId) /*-{
      if (requestId && requestId !== "") {
         // Store only in the global variable
         $wnd.activeAiRequestId = requestId;
      } else {
         console.log("DEBUG storeActiveRequestId: requestId is null or empty, not storing");
      }
   }-*/;

   // Store the WebSocket port value (obtained proactively before sending AI requests)
   private static int websocketPort_ = 0;
   
   // Store the transformed WebSocket channel ID
   private static String websocketChannelId_ = "";
   
   // WebSocket connection timeout (3 seconds, matching TerminalSessionSocket default)
   private static final int WEBSOCKET_CONNECT_TIMEOUT = 3;
   
   // WebSocket ping interval in seconds (10 seconds, matching TerminalSessionSocket default)
   private static final int WEBSOCKET_PING_INTERVAL = 10;
   
   // Current active WebSocket connection
   private Websocket socket_;
   
   // Timer for WebSocket keep-alive, exactly as in TerminalSessionSocket
   private Timer keepAliveTimer_ = new Timer()
   {
      @Override
      public void run()
      {
         // This matches the implementation in TerminalSessionSocket
         // For cancellation, we don't actually need to send keep-alives,
         // but we include this to fully match the TerminalSessionSocket structure
         if (socket_ != null)
         {
            socket_.send(AiSocketPacket.keepAlivePacket());
         }
         else
         {
            keepAliveTimer_.cancel();
         }
      }
   };
   
   // Timer for WebSocket connection timeout, exactly as in TerminalSessionSocket
   private Timer       connectWebSocketTimer_ = new Timer()
      {
         @Override
         public void run()
         {
            // Timeout connecting to WebSocket
         }
      };
   
   /**
    * Sets the WebSocket port to use for cancellation
    * This should be called proactively before initiating AI requests
    * @param port The port to use for WebSocket connections
    */
   public static void setWebsocketPort(int port) {
      websocketPort_ = port;      
      // Get current instance to fetch the channel ID
      AiPane instance = getCurrentInstance();
      if (instance != null) {
         instance.fetchWebSocketChannelId();
      }
   }
   
   /**
    * Gets the currently stored WebSocket port
    * @return The stored port value (0 if not set)
    */
   public static int getWebsocketPort() {
      return websocketPort_;
   }
   
   /**
    * Helper class for creating WebSocket packets, similar to TerminalSocketPacket
    */
   private static class AiSocketPacket
   {
      public static String cancelPacket(String requestId)
      {
         return "{\"type\":\"ai_cancel\",\"id\":\"" + requestId + "\"}";
      }
      
      public static String keepAlivePacket()
      {
         return "{\"type\":\"keep-alive\"}";
      }
   }

   /**
    * Send a cancellation message via WebSocket
    * Implementation uses only code found in TerminalSessionSocket.java
    */
   public void sendCancellationViaWebSocket(final String requestId)
   {
      // CRITICAL FIX: Mark that cancellation has been requested
      markCancellationRequested();
      
      // Validate request ID - similar to how TerminalSessionSocket checks zombie state
      if (requestId == null || requestId.isEmpty())
      {
         return;
      }
      
      // Clean up any existing socket
      if (socket_ != null)
      {
         socket_.close();
         socket_ = null;
         keepAliveTimer_.cancel();
         connectWebSocketTimer_.cancel();
      }
      
      // The URL construction should match TerminalSessionSocket's pattern
      // The major difference is we use "ai_cancel" instead of "terminal/[handle]/"
      String url;
      if (Desktop.isDesktop())
      {
         // For desktop, use direct connection to localhost
         url = "ws://127.0.0.1:" + websocketPort_ + "/ai_cancel";
      }
      else
      {
         // For web client, use the proxy path with channel ID
         url = GWT.getHostPageBaseURL();
         if (url.startsWith("https:"))
         {
            url = "wss:" + StringUtil.substring(url, 6) + "p/" + websocketChannelId_ + "/ai_cancel";
         }
         else if (url.startsWith("http:"))
         {
            url = "ws:" + StringUtil.substring(url, 5) + "p/" + websocketChannelId_ + "/ai_cancel";
         }
         else
         {
            Debug.log("Unable to discover websocket protocol");
            return;
         }
      }
      
               // Dispatch the abort event for this request before attempting WebSocket connection
         dispatchAbortEvent(requestId);
            
      // Create socket - exactly as in TerminalSessionSocket.java
      socket_ = new Websocket(url);
      
      // Add listener, same as TerminalSessionSocket.java
      socket_.addListener(new WebsocketListenerExt()
      {
         @Override
         public void onClose(CloseEvent event)
         {
            connectWebSocketTimer_.cancel();
            keepAliveTimer_.cancel();
            socket_ = null;
         }
         
         @Override
         public void onMessage(String msg)
         {
            // Handle any responses
         }
         
         @Override
         public void onOpen()
         {
            connectWebSocketTimer_.cancel();            
            // Schedule keep-alive timer if needed
            if (WEBSOCKET_PING_INTERVAL > 0)
            {
               keepAliveTimer_.scheduleRepeating(WEBSOCKET_PING_INTERVAL * 1000);
            }
            
            // Send cancellation message
            String message = AiSocketPacket.cancelPacket(requestId);
            socket_.send(message);
            
            // Close the socket after sending
            new Timer()
            {
               @Override
               public void run()
               {
                  if (socket_ != null)
                  {
                     socket_.close();
                     socket_ = null;
                  }
                  keepAliveTimer_.cancel();
               }
            }.schedule(500);
         }
         
         @Override
         public void onError()
         {
            connectWebSocketTimer_.cancel();
            socket_ = null;
            keepAliveTimer_.cancel();
         }
      });
      
      // Schedule the timeout timer, exactly as in TerminalSessionSocket
      if (WEBSOCKET_CONNECT_TIMEOUT > 0)
      {
         connectWebSocketTimer_.schedule(WEBSOCKET_CONNECT_TIMEOUT * 1000);
      }
      
      // Open the socket - exactly as in TerminalSessionSocket.java
      socket_.open();
   }
   
   /**
    * Dispatches a custom abort event that handlers can listen for
    * @param requestId The ID of the request being aborted
    */
   private native void dispatchAbortEvent(String requestId) /*-{
      try {
         // Create a custom event with the request ID in the detail
         var abortEvent = new CustomEvent("aiRequestAborted", {
            detail: {
               requestId: requestId,
               timestamp: new Date().getTime()
            },
            bubbles: true,
            cancelable: true
         });
         
         // Dispatch the event in the main window
         $wnd.dispatchEvent(abortEvent);
         
         // Try to dispatch in both frames
         var mainWindow = this.@org.rstudio.studio.client.workbench.views.ai.AiPane::getContentWindow()();
         if (mainWindow) {
            mainWindow.dispatchEvent(abortEvent);
         }
         
         var bgFrameEx = this.@org.rstudio.studio.client.workbench.views.ai.AiPane::getBackgroundIFrameEx()();
         if (bgFrameEx) {
            var bgWindow = bgFrameEx.contentWindow;
            if (bgWindow) {
               bgWindow.dispatchEvent(abortEvent);
            }
         }
      } catch (e) {
         console.error("Error dispatching abort event:", e);
      }
   }-*/;

   /**
    * Fetches the WebSocket port from the server and stores it for later use
    */
   private void fetchWebSocketPort() {
      server_.getTerminalWebsocketPort(new ServerRequestCallback<Double>() {
         @Override
         public void onResponseReceived(Double port) {
            if (port != null && port > 0) {
               websocketPort_ = port.intValue();
               
               // After getting the port, also get the channel ID
               fetchWebSocketChannelId();
            }
         }
         
         @Override
         public void onError(ServerError error) {
            // Error fetching WebSocket port
         }
      });
   }
   
   /**
    * Fetches the transformed WebSocket channel ID from the server
    * This converts the raw port into the format expected by the WebSocket server
    */
   private void fetchWebSocketChannelId() {
      // This should be an RPC call to get the channel ID for the stored port
      server_.getWebSocketChannelId(websocketPort_, new ServerRequestCallback<String>() {
         @Override
         public void onResponseReceived(String channelId) {
            if (channelId != null && !channelId.isEmpty()) {
               websocketChannelId_ = channelId;
            }
         }
         
         @Override
         public void onError(ServerError error) {
            // Error fetching WebSocket channel ID
         }
      });
   }

   // Add a method to set the cancellation in progress flag
   public native void setCancellationInProgress(boolean inProgress) /*-{
      try {
         $wnd.aiCancellationInProgress = inProgress;
      } catch (e) {
         console.error("Error setting cancellation flag:", e);
      }
   }-*/;

   /**
    * Gets the toolbars handler for accessing toolbar-related functionality
    * @return The AiToolbars instance
    */
   public AiToolbars getToolbars()
   {
      return toolbars_;
   }
   
   public AiStreamingPanel getStreamingPanel() {
      return toolbars_.getStreamingPanel();
   }

   /**
    * Gets the AI server operations instance
    * @return The AiServerOperations instance
    */
   public AiServerOperations getAiServerOperations() {
      return server_;
   }
   
   public AiOrchestrator getAiOrchestrator() {
      return aiOrchestrator_;
   }
   
   public void handleAcceptEditFileCommand(String messageId, String editedContent) {
      // IMMEDIATELY mark button as run to make it disappear - no advancement, just gone forever
      server_.markButtonAsRun(messageId, "accept", new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean result) {
            // Hide the buttons in the widget
            hideButtonsInWidget(messageId, "edit_file");
            
            // Now proceed with the actual edit file acceptance
            // Get the request ID from the edit file widget (like console/terminal widgets do)
            String requestId = null;
            AiStreamingPanel streamingPanel = getStreamingPanel();
            if (streamingPanel != null) {
               org.rstudio.studio.client.workbench.views.ai.widgets.AiEditFileWidget editFileWidget = 
                  streamingPanel.getEditFileWidget(messageId);
               if (editFileWidget != null) {
                  requestId = editFileWidget.getRequestId();
               }
            }
            
            // requestId must always be present - if it's missing, this indicates a bug
            if (requestId == null || requestId.isEmpty()) {
               String errorMessage = "CRITICAL ERROR: Edit file command acceptance failed - missing request_id for messageId: " + messageId;
               Debug.log(errorMessage);
               globalDisplay_.showErrorMessage("Edit File Command Error", errorMessage);
               return;
            }
            
            // Make requestId final for use in the callback
            final String finalRequestId = requestId;
            server_.acceptEditFileCommand(editedContent, messageId, finalRequestId, new ServerRequestCallback<JavaScriptObject>() {
               @Override
               public void onResponseReceived(JavaScriptObject result) {
                  // Check if the result contains a status that needs processing
                  if (result != null) {
                     JSONObject resultObj = new JSONObject(result);
                     if (resultObj.containsKey("status")) {
                        String status = responses_.getString(resultObj, "status", "");
                        if ("continue_silent".equals(status)) {
                           // R wants us to continue the conversation
                           Integer relatedToId = null;
                           Integer conversationIndex = null;
                           
                           if (resultObj.containsKey("data")) {
                              JSONObject dataObj = resultObj.get("data").isObject();
                              if (dataObj != null) {
                                 if (dataObj.containsKey("related_to_id")) {
                                    relatedToId = responses_.getInteger(dataObj, "related_to_id", null);
                                 }
                                 if (dataObj.containsKey("conversation_index")) {
                                    conversationIndex = responses_.getInteger(dataObj, "conversation_index", null);
                                 }
                              }
                           }
                           
                           // Validate required parameters
                           if (conversationIndex == null) {
                              globalDisplay_.showErrorMessage("Error", "Edit file command response missing conversation_index");
                              return;
                           }
                           
                           if (relatedToId == null) {
                              globalDisplay_.showErrorMessage("Error", "Edit file command response missing related_to_id");
                              return;
                           }
                           
                           // Use orchestrator to continue the conversation
                           if (aiOrchestrator_ != null) {
                              aiOrchestrator_.continueConversation(relatedToId, conversationIndex, finalRequestId);
                           } else {
                              Debug.log("DEBUG handleAcceptEditFileCommand: aiOrchestrator_ is null!");
                           }
                           return;
                        }
                        else if ("done".equals(status)) {
                           // Processing is complete - no further action needed
                           return;
                        } else {
                           Debug.log("DEBUG handleAcceptEditFileCommand: status is not continue_silent or done, it is: " + status);
                        }
                     } else {
                        Debug.log("DEBUG handleAcceptEditFileCommand: result does not contain status key");
                     }
                  } else {
                     Debug.log("DEBUG handleAcceptEditFileCommand: result is null");
                  }
                  // No recognized status or null result - no further action needed
               }
               
               @Override
               public void onError(ServerError error) {
                  Debug.log("DEBUG: accept_edit_file_command server call failed: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onError(ServerError error) {
            globalDisplay_.showErrorMessage("Error", "Failed to mark button as run: " + error.getMessage());
         }
      });
   }
   
   public void handleCancelEditFileCommand(String messageId) {
      // IMMEDIATELY mark button as run to make it disappear - no advancement, just gone forever
      server_.markButtonAsRun(messageId, "cancel", new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean result) {
            // Hide the buttons in the widget
            hideButtonsInWidget(messageId, "edit_file");
            
            // Now proceed with the actual edit file cancellation
            // Get the request ID from the edit file widget (like console/terminal widgets do)
            String requestId = null;
            AiStreamingPanel streamingPanel = getStreamingPanel();
            if (streamingPanel != null) {
               org.rstudio.studio.client.workbench.views.ai.widgets.AiEditFileWidget editFileWidget = 
                  streamingPanel.getEditFileWidget(messageId);
               if (editFileWidget != null) {
                  requestId = editFileWidget.getRequestId();
               }
            }
            
            // requestId must always be present - if it's missing, this indicates a bug
            if (requestId == null || requestId.isEmpty()) {
               String errorMessage = "CRITICAL ERROR: Edit file command cancellation failed - missing request_id for messageId: " + messageId;
               Debug.log(errorMessage);
               globalDisplay_.showErrorMessage("Edit File Command Error", errorMessage);
               return;
            }
            
            // Make requestId final for use in the callback
            final String finalRequestId = requestId;
            server_.cancelEditFileCommand(messageId, finalRequestId, new ServerRequestCallback<JavaScriptObject>() {
               @Override
               public void onResponseReceived(JavaScriptObject result) {
                  // Check if the result contains a status that needs processing
                  if (result != null) {
                     JSONObject resultObj = new JSONObject(result);
                     if (resultObj.containsKey("status")) {
                        String status = responses_.getString(resultObj, "status", "");
                        if ("continue_silent".equals(status)) {
                           // R wants us to continue the conversation
                           Integer relatedToId = null;
                           Integer conversationIndex = null;
                           
                           if (resultObj.containsKey("data")) {
                              JSONObject dataObj = resultObj.get("data").isObject();
                              if (dataObj != null) {
                                 if (dataObj.containsKey("related_to_id")) {
                                    relatedToId = responses_.getInteger(dataObj, "related_to_id", null);
                                 }
                                 if (dataObj.containsKey("conversation_index")) {
                                    conversationIndex = responses_.getInteger(dataObj, "conversation_index", null);
                                 }
                              }
                           }
                           
                           // Validate required parameters
                           if (conversationIndex == null) {
                              globalDisplay_.showErrorMessage("Error", "Edit file command response missing conversation_index");
                              return;
                           }
                           
                           if (relatedToId == null) {
                              globalDisplay_.showErrorMessage("Error", "Edit file command response missing related_to_id");
                              return;
                           }
                           
                           // Use orchestrator to continue the conversation
                           if (aiOrchestrator_ != null) {
                              aiOrchestrator_.continueConversation(relatedToId, conversationIndex, finalRequestId);
                           } else {
                              Debug.log("DEBUG handleCancelEditFileCommand: aiOrchestrator_ is null!");
                           }
                           return;
                        }
                        else if ("done".equals(status)) {
                           // Processing is complete - no further action needed
                           return;
                        } else {
                           Debug.log("DEBUG handleCancelEditFileCommand: status is not continue_silent or done, it is: " + status);
                        }
                     } else {
                        Debug.log("DEBUG handleCancelEditFileCommand: result does not contain status key");
                     }
                  } else {
                     Debug.log("DEBUG handleCancelEditFileCommand: result is null");
                  }
                  // No recognized status or null result - no further action needed
               }
               
               @Override
               public void onError(ServerError error) {
                  globalDisplay_.showErrorMessage("Error", "Failed to cancel edit file command: " + error.getMessage());
               }
            });
         }
         
         @Override
         public void onError(ServerError error) {
            globalDisplay_.showErrorMessage("Error", "Failed to mark button as run: " + error.getMessage());
         }
      });
   }
   
   /**
    * Hides buttons in the specified widget type by message ID
    */
   private void hideButtonsInWidget(String messageId, String widgetType) {
      AiStreamingPanel streamingPanel = getStreamingPanel();
      if (streamingPanel == null) {
         return;
      }
      
      if ("console".equals(widgetType)) {
         AiConsoleWidget consoleWidget = streamingPanel.getConsoleWidget(messageId);
         if (consoleWidget != null) {
            consoleWidget.hideButtons();
         }
      } else if ("terminal".equals(widgetType)) {
         AiTerminalWidget terminalWidget = streamingPanel.getTerminalWidget(messageId);
         if (terminalWidget != null) {
            terminalWidget.hideButtons();
         }
      } else if ("edit_file".equals(widgetType)) {
         org.rstudio.studio.client.workbench.views.ai.widgets.AiEditFileWidget editFileWidget = 
            streamingPanel.getEditFileWidget(messageId);
         if (editFileWidget != null) {
            editFileWidget.hideButtons();
         }
      }
   }
   
   /**
    * Handle AI streaming test events from R
    */
   private void handleAiStreamTestEvent(JsObject data) {
      try {
         String messageId = data.getString("messageId");
         String delta = data.getString("delta");
         boolean isComplete = data.getBoolean("isComplete");
         
         // Get the streaming panel
         AiStreamingPanel streamingPanel = getStreamingPanel();
         
         if (streamingPanel != null) {
            // Create a AiStreamDataEvent.Data object from JsObject
            AiStreamDataEvent.Data eventData = createStreamDataFromJsObject(messageId, delta, isComplete);
            
            // Fire streaming event using the Data constructor
            AiStreamDataEvent streamEvent = new AiStreamDataEvent(eventData);
            RStudioGinjector.INSTANCE.getEventBus().fireEvent(streamEvent);
         }
         
      } catch (Exception e) {
         Debug.log("STREAM_ERROR: " + e.getMessage());
      }
   }
   
   /**
    * Creates an AiStreamDataEvent.Data object from the given parameters
    */
   private native AiStreamDataEvent.Data createStreamDataFromJsObject(String messageId, String delta, boolean isComplete) /*-{
      // Ensure strings are properly handled by converting to native strings
      var safeMessageId = messageId ? String(messageId) : "";
      var safeDelta = delta ? String(delta) : "";
      
      return {
         messageId: safeMessageId,
         message_id: safeMessageId,
         delta: safeDelta,
         isComplete: isComplete,
         is_complete: isComplete
      };
   }-*/;



   public static void submitQueryStatic(String query) {
      AiPane instance = getCurrentInstance();
      if (instance != null && query != null && !query.trim().isEmpty()) {
         AiSearch search = instance.getSearch();
         if (search != null) {
            search.submitQuery(query.trim());
         }
      }
   }

       public native void handleCreateUserRevertButton(String messageId) /*-{
       
       var self = this; // Keep reference to the Java object like console buttons do
       
       // Find the streaming conversation container
       var conversationElement = $doc.getElementById('streaming-conversation');
       if (!conversationElement) {
          console.log("DEBUG: streaming-conversation element not found");
          return;
       }
       
       // Remove any existing revert buttons first to prevent duplicates
       var existingButtons = conversationElement.querySelectorAll('.revert-button');
       for (var i = 0; i < existingButtons.length; i++) {
          existingButtons[i].remove();
       }
       
       // Find user message containers
       var userContainers = conversationElement.querySelectorAll('.user-container, .message.user');
       
       // Add revert button to ALL user messages
       if (userContainers.length > 0) {
          
          for (var i = 0; i < userContainers.length; i++) {
             var userContainer = userContainers[i];
             
             // Find the actual user message div within the container
             var userMessageDiv = userContainer.querySelector('.message.user') || userContainer;
             
             // Extract the messageId from the user message div's id attribute
             var userMessageId = userMessageDiv.id;
             if (!userMessageId) {
                continue;
             }
             
             
             // Check if this user message already has a revert button to avoid duplicates
             var existingButton = userMessageDiv.querySelector('.revert-button');
             if (existingButton) {
                continue;
             }
             
             // Create button container with positioning like console buttons - append to user message itself
             var buttonContainer = $doc.createElement('div');
             buttonContainer.style.position = 'relative';
             buttonContainer.style.height = '0px';
             buttonContainer.style.width = '100%';
             buttonContainer.style.zIndex = '10';
             
             // Create the revert button with console-style positioning and styling
             var revertButton = $doc.createElement('button');
             revertButton.className = 'revert-button';
             revertButton.textContent = 'Revert';
             revertButton.title = 'Revert to this point in the conversation and delete all messages after';
             
             // Position to appear at bottom right of the user message
             revertButton.style.position = 'absolute';
             revertButton.style.bottom = '-18px'; // Position below the message (like console buttons at bottom)
             revertButton.style.right = '4px';
             revertButton.style.backgroundColor = '#cccccc'; // Light gray like disabled console buttons
             revertButton.style.color = 'black';
             revertButton.style.border = '1px solid black';
             revertButton.style.padding = '2px 6px'; // Shorter height like console buttons
             revertButton.style.borderRadius = '3px';
             revertButton.style.fontSize = '11px';
             revertButton.style.cursor = 'pointer';
             revertButton.style.pointerEvents = 'auto';
             revertButton.style.zIndex = '999';
             
             // Add hover effects
             revertButton.addEventListener('mouseenter', function() {
                this.style.backgroundColor = '#b8b8b8'; // Slightly darker on hover
             });
             revertButton.addEventListener('mouseleave', function() {
                this.style.backgroundColor = '#cccccc';
             });
             
             // Create a closure to capture the correct messageId for each button
             (function(capturedMessageId) {
                revertButton.addEventListener('click', function(event) {
                   event.preventDefault();
                   event.stopPropagation();
                   
                   // Call the Java method directly like console buttons do
                   self.@org.rstudio.studio.client.workbench.views.ai.AiPane::handleAiRevertConfirmation(Ljava/lang/String;)(capturedMessageId);
                });
             })(userMessageId);
             
             // Add button to container and append to the user message div itself
             buttonContainer.appendChild(revertButton);
             userMessageDiv.appendChild(buttonContainer);
          }
       } else {
          console.log("DEBUG: No user containers found");
       }
    }-*/;
   
   
   public void handleClearConversation() {
      AiStreamingPanel streamingPanel = getStreamingPanel();
      if (streamingPanel != null) {
         streamingPanel.clearAllContent();
      } else {
         Debug.log("DEBUG: StreamingPanel is null, cannot clear conversation");
      }
   }
   




   public AiPaneAttachments getAttachmentsManager()
   {
      return attachmentsManager_;
   }

   public AiPaneImages getImagesManager()
   {
      return imagesManager_;
   }

   /**
    * Checks if a cancellation has been requested for the current operation.
    * This is used by the orchestrator to avoid making continue API calls after cancellation.
    */
   public boolean isCancellationRequested() {
      // Check if there's an active request ID and if cancellation was requested for it
      String activeRequestId = getActiveRequestId();
      if (activeRequestId != null && !activeRequestId.isEmpty()) {
         // Use the server to check if this request was cancelled
         // For now, we'll use a simple client-side flag approach
         return cancellationRequested_;
      }
      return false;
   }
   
   // Track cancellation state
   private boolean cancellationRequested_ = false;
   
   /**
    * Marks that cancellation has been requested for the current operation.
    */
   private void markCancellationRequested() {
      cancellationRequested_ = true;
   }
   
   /**
    * Clears the cancellation state when starting a new operation.
    */
   public void clearCancellationState() {
      cancellationRequested_ = false;
   }

   /**
    * Send cancellation via WebSocket if we have an active request
    */
   private void sendCancellationToActiveRequest() {
      String activeRequestId = getActiveRequestId();
      if (activeRequestId != null && !activeRequestId.isEmpty()) {
         // Send cancellation via WebSocket if we have an active request
         sendCancellationViaWebSocket(activeRequestId);
         
         // CRITICAL FIX: Mark that cancellation has been requested
         markCancellationRequested();
      }
   }

}
