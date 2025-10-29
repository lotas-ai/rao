/*
 * AiSettingsWidget.java
 *
 * Copyright (C) 2025 by Lotas Inc.
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 */

package org.rstudio.studio.client.workbench.views.ai.widgets;

import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArrayString;
import org.rstudio.core.client.Debug;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import com.google.gwt.user.client.Command;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.Timer;
import org.rstudio.core.client.widget.ToolbarPopupMenu;

import com.google.gwt.user.client.ui.PasswordTextBox;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.workbench.views.ai.model.AiServerOperations;
import org.rstudio.core.client.theme.ThemeHelper;
import org.rstudio.studio.client.workbench.views.ai.model.AiUserProfile;
import org.rstudio.studio.client.workbench.views.ai.model.AiSubscriptionStatus;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.application.events.EventBus;
import com.google.gwt.user.client.Timer;
import org.rstudio.studio.client.common.FileDialogs;
import org.rstudio.core.client.files.FileSystemContext;
import org.rstudio.core.client.files.FileSystemItem;
import org.rstudio.core.client.widget.ProgressOperationWithInput;
import org.rstudio.core.client.widget.ProgressIndicator;
import org.rstudio.studio.client.common.filetypes.FileTypeRegistry;

public class AiSettingsWidget extends Composite
{
   public interface SettingsHandler
   {
      void onSaveApiKey(String apiKey);
      void onDeleteApiKey();
      void onSignInWithWebsite();
      void onModelChange(String model);
      void onTemperatureChange(double temperature);
      void onSecurityModeChange(String mode);
      void onWebSearchEnabledChange(boolean enabled);
      void onAddRule(String rule);
      void onEditRule(int index, String rule);
      void onDeleteRule(int index);
      void onAutoAcceptEditsChange(boolean enabled);
      void onAutoAcceptConsoleChange(boolean enabled);
      void onAutoAcceptTerminalChange(boolean enabled);
      void onAutoRunFilesChange(boolean enabled);
      void onAutoDeleteFilesChange(boolean enabled);
      void onAutoAcceptConsoleAllowAnythingChange(boolean enabled);
      void onAutoAcceptTerminalAllowAnythingChange(boolean enabled);
      void onAutoRunFilesAllowAnythingChange(boolean enabled);
      void onBYOKEnabledChange(String provider, boolean enabled);
      void onBYOKApiKeySet(String provider, String apiKey);
      void onBYOKApiKeyDeleted(String provider);
   }
   
   public interface Styles extends CssResource
   {
      String settingsContainer();
      String settingsHeader();
      String settingsSection();
      String sectionTitle();
      String settingRow();
      String settingLabel();
      String settingInput();
      String settingButton();
      String primaryButton();
      String secondaryButton();
      String dangerButton();
      String statusBadge();
      String statusActive();
      String statusTrial();
      String statusOrgPromo();
      String statusPastDue();
      String statusPaymentActionRequired();
      String statusCancelled();
      String statusExpired();
      String profileInfo();
      String directoryRow();
      String temperatureRow();
      String errorMessage();
      String successMessage();
      String profileTitlePanel();
      String profileName();
      String signOutContainer();
      String sectionHeaderPanel();
      String sectionChevron();
      String sectionContent();
      String sectionContentCollapsed();
      String collapsed();
      String lightGrayButton();
      String ruleTextArea();
      String ruleContainer();
      String ruleText();
      String optionsButton();
      String optionsMenu();
      String menuButton();
      String buttonPanel();
      String inputContainer();
      String addRuleButton();
      String compactButton();
      String settingsDescription();
      String keyStoredText();
      String allowDenyPanel();
      String listItemText();
      String listItemRemove();
      String usageBarContainer();
      String usageBarFill();
      String toggleShadow();
      String listItemContainer();
   }
   
   public interface Resources extends ClientBundle
   {
      @Source("AiSettingsWidget.css")
      Styles styles();
   }
   
   private static final Resources RES = GWT.create(Resources.class);
   private static final Styles styles_ = RES.styles();
   static { RES.styles().ensureInjected(); }
   
   private final SettingsHandler handler_;
   private final AiServerOperations server_;
   private final EventBus eventBus_;
   private final GlobalDisplay globalDisplay_;
   private final FileDialogs fileDialogs_;
   private final FileSystemContext fileSystemContext_;
   private final FileTypeRegistry fileTypeRegistry_;
   
   // UI Components
   private PasswordTextBox apiKeyInput_;
   private Button saveApiKeyButton_;
   private Button deleteApiKeyButton_;
   private Button signInButton_;
   private Button optionsButton_;
   private VerticalPanel apiKeySection_;
   private Label userNameLabel_;
   private HTML subscriptionStatusLabel_;
   private ListBox modelSelect_;
   private com.google.gwt.user.client.ui.HTML temperatureSlider_;
   private TextBox temperatureInput_;
   private Label temperatureLabel_;
   private Label profileErrorLabel_;
   private Label directorySuccessLabel_;
   private Label directoryErrorLabel_;
   private Label directoryPromptLabel_;
   private TextBox workingDirectoryInput_;
   private Button browseDirectoryButton_;
   private Button setDirectoryButton_;
   private HTML securityModeToggle_;
   private HTML webSearchToggle_;
   private Label securityModeText_;
   private Label webSearchText_;
   private Button addRuleButton_;
   private VerticalPanel rulesContainer_;
   private HorizontalPanel rulesFileContainer_;
   private String currentRulesFilePath_ = null;
   private TextArea newRuleInput_;
   private Button saveNewRuleButton_;
   private Button cancelNewRuleButton_;
   private VerticalPanel newRulePanel_;
   private HTML profileSection_;
   private HTML modelSection_;
   private HTML workingDirectorySection_;
   private HTML rulesSection_;
   private HTML securitySection_;
   private HTML automationSection_;
   private HTML byokSection_;
   
   // State
   private boolean hasApiKey_ = false;
   private boolean hasAnyAuth_ = false; // True if user has Rao key OR BYOK keys
   private String currentModel_ = null;
   private String currentDirectory_ = null;
   private double currentTemperature_ = 0.5; // Default temperature
   private AiUserProfile userProfile_ = null;
   private AiSubscriptionStatus subscriptionStatus_ = null;
   private boolean subscriptionDetailsAdded_ = false;
   private boolean shouldShowDirectoryPrompt_ = false;
   private List<String> currentRules_ = new ArrayList<>();
   private Map<Integer, VerticalPanel> ruleMenus_ = new HashMap<>();
   private Map<Integer, TextArea> editInputs_ = new HashMap<>();
   
   // Section expanded/collapsed state
   private boolean profileSectionExpanded_ = true;
   private boolean modelSectionExpanded_ = true;
   private boolean workingDirectorySectionExpanded_ = true;
   private boolean rulesSectionExpanded_ = true;
   private boolean securitySectionExpanded_ = true;
   private boolean automationSectionExpanded_ = true;
   private boolean byokSectionExpanded_ = false;
   
   // Automation toggle widgets
   private HTML autoAcceptEditsToggle_;
   private HTML autoAcceptConsoleToggle_;
   private HTML autoAcceptTerminalToggle_;
   private HTML autoRunFilesToggle_;
   private HTML autoDeleteFilesToggle_;
   
   // Map to store FlowPanel references for automation lists
   private Map<String, FlowPanel> automationListContainers_ = new HashMap<String, FlowPanel>();
   
   // BYOK input storage
   private Map<String, TextBox> byokApiKeyInputs_ = new HashMap<>();
   private Map<String, String> byokDisplayNames_ = new HashMap<>();
   private Map<String, FlowPanel> byokInputContainers_ = new HashMap<>();
   private Map<String, HorizontalPanel> byokStoredContainers_ = new HashMap<>();
   
   // SageMaker-specific fields
   private TextBox sagemakerAccessKeyInput_;
   private TextBox sagemakerSecretKeyInput_;
   private TextBox sagemakerEndpointInput_;
   private TextBox sagemakerRegionInput_;
   private TextBox sagemakerModelInput_;
   private FlowPanel sagemakerInputContainer_;
   private HorizontalPanel sagemakerStoredContainer_;
   
   // Local Model-specific fields
   private TextBox localModelEndpointInput_;
   private TextBox localModelNameInput_;
   private PasswordTextBox localModelApiKeyInput_;
   private FlowPanel localModelInputContainer_;
   private HorizontalPanel localModelStoredContainer_;
   
   public AiSettingsWidget(SettingsHandler handler, 
                          AiServerOperations server, 
                          EventBus eventBus,
                          GlobalDisplay globalDisplay,
                          FileDialogs fileDialogs,
                          FileSystemContext fileSystemContext,
                          FileTypeRegistry fileTypeRegistry)
   {
      handler_ = handler;
      server_ = server;
      eventBus_ = eventBus;
      globalDisplay_ = globalDisplay;
      fileDialogs_ = fileDialogs;
      fileSystemContext_ = fileSystemContext;
      fileTypeRegistry_ = fileTypeRegistry;
      
      initWidget(createWidget());
      addStyleName(styles_.settingsContainer());
      
      // Add theme classes to enable CSS theme selectors
      addThemeClasses();
      
      // Register for theme change events
      eventBus_.addHandler(org.rstudio.studio.client.application.events.ThemeChangedEvent.TYPE, 
         new org.rstudio.studio.client.application.events.ThemeChangedEvent.Handler() {
            @Override
            public void onThemeChanged(org.rstudio.studio.client.application.events.ThemeChangedEvent event) {
               updateThemeClasses();
            }
         });
      
      // Load initial data
      loadUserProfile();
      loadSubscriptionStatus();
      loadCurrentSettings();
   }
   
   /**
    * Add theme classes to enable CSS theme selectors to work properly
    */
   private void addThemeClasses()
   {
      // Get current theme from body class
      String themeName = ThemeHelper.getCurrentTheme();
      
      // Add appropriate theme class
      if (themeName.equals("dark-grey")) {
         addStyleName("rstudio-themes-dark-grey");
      } else if (themeName.equals("alternate")) {
         addStyleName("rstudio-themes-alternate");
      } else {
         addStyleName("rstudio-themes-default");
      }
   }
   
   /**
    * Update theme classes when theme changes at runtime
    */
   private void updateThemeClasses()
   {
      // Remove all existing theme classes
      removeStyleName("rstudio-themes-default");
      removeStyleName("rstudio-themes-dark-grey");
      removeStyleName("rstudio-themes-alternate");
      
      // Add the current theme class
      addThemeClasses();
   }
   
   private Widget createWidget()
   {
      // Create main content panel
      VerticalPanel mainPanel = new VerticalPanel();
      mainPanel.setWidth("100%");
      
      // Header
      Label headerLabel = new Label("Settings");
      headerLabel.addStyleName(styles_.settingsHeader());
      mainPanel.add(headerLabel);
      
      // Profile Section
      profileSection_ = new HTML();
      profileSection_.addStyleName(styles_.settingsSection());
      mainPanel.add(profileSection_);
      
      // BYOK Section
      byokSection_ = new HTML();
      byokSection_.addStyleName(styles_.settingsSection());
      mainPanel.add(byokSection_);
      
      // Rules Section
      rulesSection_ = new HTML();
      rulesSection_.addStyleName(styles_.settingsSection());
      mainPanel.add(rulesSection_);
      

      
      // Security Section
      securitySection_ = new HTML();
      securitySection_.addStyleName(styles_.settingsSection());
      mainPanel.add(securitySection_);
      
      // Automation Section
      automationSection_ = new HTML();
      automationSection_.addStyleName(styles_.settingsSection());
      mainPanel.add(automationSection_);
      
      // Model Section (moved after Automation)
      modelSection_ = new HTML();
      modelSection_.addStyleName(styles_.settingsSection());
      mainPanel.add(modelSection_);
      
      // CRITICAL FIX: Wrap in ScrollPanel to enable scrolling like other working widgets
      ScrollPanel scrollPanel = new ScrollPanel(mainPanel);
      scrollPanel.setSize("100%", "100%");
      scrollPanel.addStyleName("ace_editor"); // Get ACE theme background from .rstheme files
      scrollPanel.addStyleName("ace_scroller"); // Standard RStudio scrollable styling
      scrollPanel.addStyleName("ace_editor_theme"); // Theme context marker
      scrollPanel.addStyleName("ai-modal-background"); // Transparent background to show ACE theme
      
      return scrollPanel;
   }
   
   private void buildProfileSection()
   {
      // Reset the flag since we're rebuilding the section
      subscriptionDetailsAdded_ = false;
      
      VerticalPanel section = new VerticalPanel();
      section.setWidth("100%");
      
      // Create header using CSS flexbox instead of table-based layout
      HTML headerContainer = new HTML();
      headerContainer.setWidth("100%");
      headerContainer.addStyleName(styles_.sectionHeaderPanel());
      
      // Build the header HTML directly with flexbox
      if (userNameLabel_ == null) {
         userNameLabel_ = new Label();
         userNameLabel_.addStyleName(styles_.profileName());
      }
      
      String userName = userNameLabel_.getText();
      if (userName == null || userName.isEmpty()) {
         userName = ""; // Default to empty if no user name yet
      }
      
               String headerHtml = 
            "<div style='display: flex; align-items: center; width: 100%; position: relative; padding-right: 35px;'>" +
               "<div style='flex: 0 0 auto;'>" +
                  "<span class='" + styles_.sectionTitle() + "'>Profile</span>" +
               "</div>" +
               "<div style='flex: 1 1 auto;'></div>" + // Spacer
               "<div style='flex: 0 0 auto; margin-right: 10px;'>" +
                  "<span class='" + styles_.profileName() + "' style='white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 200px;'>" + userName + "</span>" +
               "</div>" +
            "</div>" +
            "<div style='position: absolute; top: 8px; right: 8px; z-index: 10;'>" +
               "<div class='ai-chevron-button' onclick='window.handleProfileChevronClick && window.handleProfileChevronClick();'>" +
                  "<svg width='10' height='12' viewBox='0 0 10 12' class='ai-chevron-svg'>" +
                     "<path d='M2 4L5 2L8 4' stroke-width='1.2' fill='none' stroke-linecap='round' stroke-linejoin='round'/>" +
                     "<path d='M2 8L5 10L8 8' stroke-width='1.2' fill='none' stroke-linecap='round' stroke-linejoin='round'/>" +
                  "</svg>" +
               "</div>" +
            "</div>";
      
      headerContainer.setHTML(headerHtml);
      
      // Add native click handler for the chevron
      addNativeProfileChevronHandler();
      
      section.add(headerContainer);
      
      // Content section (collapsible)
      VerticalPanel contentPanel = new VerticalPanel();
      contentPanel.setWidth("100%");
      contentPanel.addStyleName(styles_.sectionContent());
      if (!profileSectionExpanded_) {
         contentPanel.addStyleName(styles_.sectionContentCollapsed());
      }
      
      if (hasApiKey_) {
         // Profile info section
         VerticalPanel profileInfo = new VerticalPanel();
         profileInfo.setWidth("100%");
         
         // Name will be shown in the title panel, not here
         
         // Subscription status - wrap in proper container for consistent spacing
         HorizontalPanel subscriptionPanel = new HorizontalPanel();
         subscriptionPanel.setWidth("100%");
         subscriptionPanel.addStyleName(styles_.settingRow());
         
         subscriptionStatusLabel_ = new HTML();
         subscriptionStatusLabel_.addStyleName(styles_.settingLabel());
         subscriptionPanel.add(subscriptionStatusLabel_);
         
         profileInfo.add(subscriptionPanel);
         
         // Add detailed subscription information if available
         if (subscriptionStatus_ != null) {
            buildSubscriptionDetails(profileInfo);
            subscriptionDetailsAdded_ = true;
         }
         
         // Delete API key button - align to the right
         HorizontalPanel signOutContainer = new HorizontalPanel();
         signOutContainer.setWidth("100%");
         signOutContainer.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
         signOutContainer.addStyleName(styles_.signOutContainer());
         
         deleteApiKeyButton_ = new Button("Sign out");
         deleteApiKeyButton_.addStyleName(styles_.settingButton());
         deleteApiKeyButton_.addStyleName(styles_.dangerButton());
         deleteApiKeyButton_.addStyleName(styles_.compactButton());
         
         // Add native DOM click event listener (same pattern as console/terminal widgets)
         addNativeClickHandler(deleteApiKeyButton_.getElement(), "Sign out");
         signOutContainer.add(deleteApiKeyButton_);
         profileInfo.add(signOutContainer);
         
         contentPanel.add(profileInfo);
      } else {
         // No Rao API key - show sign in options (even if BYOK keys exist)
         VerticalPanel signInContainer = new VerticalPanel();
         signInContainer.setWidth("100%");
         signInContainer.addStyleName(styles_.settingRow());
         
         FlowPanel buttonPanel = new FlowPanel();
         buttonPanel.getElement().getStyle().setProperty("display", "flex");
         buttonPanel.getElement().getStyle().setProperty("gap", "8px");
         
         // Sign In button
         signInButton_ = new Button("Sign up/Sign in");
         signInButton_.addStyleName(styles_.settingButton());
         signInButton_.addStyleName(styles_.primaryButton());
         signInButton_.setWidth("150px");
         addNativeClickHandler(signInButton_.getElement(), "Sign in");
         
         // Options button
         optionsButton_ = new Button("Use Lotas API Key");
         optionsButton_.addStyleName(styles_.settingButton());
         optionsButton_.addStyleName(styles_.primaryButton());
         optionsButton_.setWidth("150px");
         addNativeClickHandler(optionsButton_.getElement(), "Options");
         
         buttonPanel.add(signInButton_);
         buttonPanel.add(optionsButton_);
         signInContainer.add(buttonPanel);
         contentPanel.add(signInContainer);
         
         // API Key input section (initially hidden)
         apiKeySection_ = new VerticalPanel();
         apiKeySection_.setWidth("100%");
         apiKeySection_.addStyleName(styles_.settingRow());
         apiKeySection_.setVisible(false);
         
         Label keyLabel = new Label("API Key");
         keyLabel.addStyleName(styles_.settingLabel());
         apiKeySection_.add(keyLabel);
         
         apiKeyInput_ = new PasswordTextBox();
         apiKeyInput_.addStyleName(styles_.settingInput());
         apiKeyInput_.getElement().setAttribute("placeholder", "Enter your Rao API key from www.lotas.ai/account");
         apiKeySection_.add(apiKeyInput_);
         
         saveApiKeyButton_ = new Button("Save API Key");
         saveApiKeyButton_.addStyleName(styles_.settingButton());
         saveApiKeyButton_.addStyleName(styles_.primaryButton());
         addNativeClickHandler(saveApiKeyButton_.getElement(), "Save API Key");
         apiKeySection_.add(saveApiKeyButton_);
         
         contentPanel.add(apiKeySection_);
      }
      
      // Error message label
      profileErrorLabel_ = new Label();
      profileErrorLabel_.addStyleName(styles_.errorMessage());
      profileErrorLabel_.setVisible(false);
      contentPanel.add(profileErrorLabel_);
      
      // Add content panel to section
      section.add(contentPanel);
      
      profileSection_.getElement().setInnerHTML("");
      profileSection_.getElement().appendChild(section.getElement());
      
      // Apply collapsed class if section is collapsed
      if (!profileSectionExpanded_) {
         profileSection_.addStyleName(styles_.collapsed());
         // Immediately apply the visual collapse using JavaScript
         applyImmediateCollapse(profileSection_.getElement());
      } else {
         profileSection_.removeStyleName(styles_.collapsed());
      }
   }
   
   private void buildModelSection()
   {
      VerticalPanel section = new VerticalPanel();
      section.setWidth("100%");
      
      // Section header
      HorizontalPanel headerPanel = createSectionHeader("Model", "model", modelSectionExpanded_);
      section.add(headerPanel);
      
      // Add chevron button positioned absolutely on the right
      HTML chevronButton = createChevronButton("model", modelSectionExpanded_);
      section.add(chevronButton);
      
      // Content section (collapsible)
      VerticalPanel contentPanel = new VerticalPanel();
      contentPanel.setWidth("100%");
      contentPanel.addStyleName(styles_.sectionContent());
      if (!modelSectionExpanded_) {
         contentPanel.addStyleName(styles_.sectionContentCollapsed());
      }
      
      // Always create model selection UI - it will handle BYOK keys as well
      HorizontalPanel modelPanel = new HorizontalPanel();
      modelPanel.setWidth("100%");
      modelPanel.addStyleName(styles_.settingRow());
      
      VerticalPanel modelContainer = new VerticalPanel();
      modelContainer.setWidth("100%");
      
      HTML modelLabel = new HTML("<b>Choose model</b>");
      modelLabel.addStyleName(styles_.settingLabel());
      modelContainer.add(modelLabel);
      
      modelSelect_ = new ListBox();
      modelSelect_.addStyleName(styles_.settingInput());
      modelSelect_.setWidth("100%");
      
      // Add native DOM change event listener (same pattern as buttons)
      addNativeChangeHandler(modelSelect_.getElement());
      
      modelContainer.add(modelSelect_);
      
      modelPanel.add(modelContainer);
      contentPanel.add(modelPanel);
      
      // Temperature slider section
      HorizontalPanel temperaturePanel = new HorizontalPanel();
      temperaturePanel.setWidth("100%");
      temperaturePanel.addStyleName(styles_.settingRow());
      
      VerticalPanel temperatureContainer = new VerticalPanel();
      temperatureContainer.setWidth("100%");
      
      HTML temperatureLabel = new HTML("<b>Temperature</b>");
      temperatureLabel.addStyleName(styles_.settingLabel());
      temperatureContainer.add(temperatureLabel);
      
      // Temperature description
      Label temperatureDescription = new Label("Temperature determines the model's variability from 0 (deterministic) to 1 (highly variable).");
      temperatureDescription.addStyleName(styles_.settingsDescription());
      temperatureDescription.getElement().getStyle().setProperty("marginBottom", "8px");
      temperatureContainer.add(temperatureDescription);
      
      // Container for slider and input using flexbox layout
      FlowPanel sliderInputPanel = new FlowPanel();
      sliderInputPanel.setWidth("100%");
      sliderInputPanel.addStyleName(styles_.temperatureRow());
      sliderInputPanel.getElement().getStyle().setProperty("display", "flex");
      sliderInputPanel.getElement().getStyle().setProperty("gap", "8px");
      sliderInputPanel.getElement().getStyle().setProperty("alignItems", "center");
      
      // HTML5 range slider - takes 85% of width
      temperatureSlider_ = new HTML();
      temperatureSlider_.getElement().setInnerHTML(
         "<input type='range' min='0' max='1' step='0.1' value='" + currentTemperature_ + "' style='width: 100%;' />"
      );
      temperatureSlider_.addStyleName(styles_.settingInput());
      temperatureSlider_.getElement().getStyle().setProperty("flex", "0 0 85%");
      
      // Add native event handlers for slider
      addNativeSliderChangeHandler(temperatureSlider_.getElement().getFirstChildElement());
      
      sliderInputPanel.add(temperatureSlider_);
      
      // Numeric input box - takes remaining 15% of width
      temperatureInput_ = new TextBox();
      temperatureInput_.setValue(String.valueOf(currentTemperature_));
      temperatureInput_.addStyleName(styles_.settingInput());
      temperatureInput_.getElement().setAttribute("placeholder", "0.5");
      temperatureInput_.getElement().getStyle().setProperty("flex", "1");
      
      // Add native event handlers for input
      addNativeInputChangeHandler(temperatureInput_.getElement());
      
      sliderInputPanel.add(temperatureInput_);
      
      temperatureContainer.add(sliderInputPanel);
      temperaturePanel.add(temperatureContainer);
      contentPanel.add(temperaturePanel);
      
      // Load available models (handles both Rao API keys and BYOK keys)
      loadAvailableModels();
      
      // Add content panel to section
      section.add(contentPanel);
      
      modelSection_.getElement().setInnerHTML("");
      modelSection_.getElement().appendChild(section.getElement());
      
      // Apply collapsed class if section is collapsed
      if (!modelSectionExpanded_) {
         modelSection_.addStyleName(styles_.collapsed());
         // Immediately apply the visual collapse using JavaScript
         applyImmediateCollapse(modelSection_.getElement());
      } else {
         modelSection_.removeStyleName(styles_.collapsed());
      }
   }
   
   private void buildRulesSection()
   {
      VerticalPanel section = new VerticalPanel();
      section.setWidth("100%");
      
      // Section header
      HorizontalPanel headerPanel = createSectionHeader("Rules", "rules", rulesSectionExpanded_);
      section.add(headerPanel);
      
      // Add chevron button positioned absolutely on the right
      HTML chevronButton = createChevronButton("rules", rulesSectionExpanded_);
      section.add(chevronButton);
      
      // Content section (collapsible)
      VerticalPanel contentPanel = new VerticalPanel();
      contentPanel.setWidth("100%");
      contentPanel.addStyleName(styles_.sectionContent());
      if (!rulesSectionExpanded_) {
         contentPanel.addStyleName(styles_.sectionContentCollapsed());
      }
      
      // Container for description and add button
      HorizontalPanel descriptionPanel = new HorizontalPanel();
      descriptionPanel.setWidth("100%");
      descriptionPanel.setVerticalAlignment(HorizontalPanel.ALIGN_TOP);
      descriptionPanel.setHorizontalAlignment(HorizontalPanel.ALIGN_LEFT);
      
      Label description = new Label("These rules are provided to the AI on each query to guide its response.");
      description.addStyleName(styles_.settingLabel());
      description.setWidth("100%");
      descriptionPanel.add(description);
      descriptionPanel.setCellWidth(description, "100%");
      descriptionPanel.setCellHorizontalAlignment(description, HasHorizontalAlignment.ALIGN_LEFT);
      
      // Add rule button positioned on the right with fixed size
      addRuleButton_ = new Button("+ Add Rule");
      addRuleButton_.addStyleName(styles_.settingButton());
      addRuleButton_.addStyleName(styles_.primaryButton());
      addRuleButton_.addStyleName(styles_.addRuleButton());
      
      // Add native DOM click event listener
      addNativeClickHandler(addRuleButton_.getElement(), "+ Add Rule");
      descriptionPanel.add(addRuleButton_);
      descriptionPanel.setCellHorizontalAlignment(addRuleButton_, HasHorizontalAlignment.ALIGN_RIGHT);
      descriptionPanel.setCellVerticalAlignment(addRuleButton_, HasVerticalAlignment.ALIGN_TOP);
      
      contentPanel.add(descriptionPanel);
      
      // Rules container (will expand with rules)
      rulesContainer_ = new VerticalPanel();
      rulesContainer_.setWidth("100%");
      rulesContainer_.addStyleName(styles_.settingRow());
      contentPanel.add(rulesContainer_);
      
      // New rule input panel (initially hidden)
      newRulePanel_ = new VerticalPanel();
      newRulePanel_.setWidth("100%");
      newRulePanel_.setVisible(false);
      newRulePanel_.addStyleName(styles_.settingRow());
      newRulePanel_.getElement().getStyle().setProperty("marginTop", "8px");
      
      // Container for input and buttons with relative positioning
      VerticalPanel inputContainer = new VerticalPanel();
      inputContainer.setWidth("100%");
      inputContainer.addStyleName(styles_.inputContainer());
      
      newRuleInput_ = new TextArea();
      newRuleInput_.getElement().setAttribute("placeholder", "Enter a rule for the AI to follow");
      newRuleInput_.addStyleName(styles_.ruleTextArea());
      newRuleInput_.setVisibleLines(3); // Start with 3 lines
      inputContainer.add(newRuleInput_);
      
      // Button panel on the right
      FlowPanel buttonPanel = new FlowPanel();
      buttonPanel.getElement().getStyle().setProperty("display", "flex");
      buttonPanel.getElement().getStyle().setProperty("gap", "8px");
      buttonPanel.getElement().getStyle().setProperty("justifyContent", "flex-end");
      buttonPanel.getElement().getStyle().setProperty("marginTop", "8px");
      
      saveNewRuleButton_ = new Button("Save");
      saveNewRuleButton_.addStyleName(styles_.lightGrayButton());
      addNativeClickHandler(saveNewRuleButton_.getElement(), "Save");
      buttonPanel.add(saveNewRuleButton_);
      
      cancelNewRuleButton_ = new Button("Cancel");
      cancelNewRuleButton_.addStyleName(styles_.lightGrayButton());
      addNativeClickHandler(cancelNewRuleButton_.getElement(), "Cancel");
      buttonPanel.add(cancelNewRuleButton_);
      
      inputContainer.add(buttonPanel);
      newRulePanel_.add(inputContainer);
      contentPanel.add(newRulePanel_);
      
      // Build the rules list
      buildRulesList();
      
      // Rules file section - styled like the description panel above
      HorizontalPanel rulesFilePanel = new HorizontalPanel();
      rulesFilePanel.setWidth("100%");
      rulesFilePanel.setVerticalAlignment(HorizontalPanel.ALIGN_TOP);
      rulesFilePanel.setHorizontalAlignment(HorizontalPanel.ALIGN_LEFT);
      rulesFilePanel.getElement().getStyle().setProperty("marginTop", "12px");
      
      Label rulesFileDescription = new Label("Attach a text document with expanded rules.");
      rulesFileDescription.addStyleName(styles_.settingLabel());
      rulesFileDescription.setWidth("100%");
      rulesFilePanel.add(rulesFileDescription);
      rulesFilePanel.setCellWidth(rulesFileDescription, "100%");
      rulesFilePanel.setCellHorizontalAlignment(rulesFileDescription, HasHorizontalAlignment.ALIGN_LEFT);
      
      // Attach File button - always visible
      Button attachFileButton_ = new Button("Attach File");
      attachFileButton_.addStyleName(styles_.settingButton());
      attachFileButton_.addStyleName(styles_.primaryButton());
      attachFileButton_.addStyleName(styles_.addRuleButton());
      addNativeClickHandler(attachFileButton_.getElement(), "AttachRulesFile");
      rulesFilePanel.add(attachFileButton_);
      rulesFilePanel.setCellHorizontalAlignment(attachFileButton_, HasHorizontalAlignment.ALIGN_RIGHT);
      rulesFilePanel.setCellVerticalAlignment(attachFileButton_, HasVerticalAlignment.ALIGN_TOP);
      
      contentPanel.add(rulesFilePanel);
      
      // Container for attached file display (initially hidden) - like BYOK stored key
      rulesFileContainer_ = new HorizontalPanel();
      rulesFileContainer_.setVisible(false);
      rulesFileContainer_.setVerticalAlignment(HorizontalPanel.ALIGN_MIDDLE);
      rulesFileContainer_.getElement().getStyle().setProperty("marginTop", "10px");
      rulesFileContainer_.getElement().getStyle().setProperty("marginBottom", "4px");
      contentPanel.add(rulesFileContainer_);
      
      // Build the rules file display
      buildRulesFileDisplay();
      
      // Add content panel to section
      section.add(contentPanel);
      
      rulesSection_.getElement().setInnerHTML("");
      rulesSection_.getElement().appendChild(section.getElement());
      
      // Apply collapsed class if section is collapsed
      if (!rulesSectionExpanded_) {
         rulesSection_.addStyleName(styles_.collapsed());
         // Immediately apply the visual collapse using JavaScript
         applyImmediateCollapse(rulesSection_.getElement());
      } else {
         rulesSection_.removeStyleName(styles_.collapsed());
      }
   }
   
   private void buildRulesList()
   {
      // Clear existing rules and menu map
      rulesContainer_.clear();
      ruleMenus_.clear();
      editInputs_.clear();
      
      // Add each rule as a panel with edit/delete options
      for (int i = 0; i < currentRules_.size(); i++) {
         final int ruleIndex = i;
         final String rule = currentRules_.get(i);
         
         // Create rule container with white background to match input
         VerticalPanel ruleContainer = new VerticalPanel();
         ruleContainer.setWidth("100%");
         ruleContainer.addStyleName(styles_.ruleContainer());
         
         // Rule content panel
         HorizontalPanel ruleContent = new HorizontalPanel();
         ruleContent.setWidth("100%");
         ruleContent.setVerticalAlignment(HorizontalPanel.ALIGN_TOP);
         // Add right padding to leave space for the options button
         ruleContent.getElement().getStyle().setProperty("paddingRight", "30px");
         
         // Rule text (takes most of the space)
         Label ruleText = new Label(rule);
         ruleText.addStyleName(styles_.ruleText());
         ruleContent.add(ruleText);
         
         // Options menu button (Text-based for reliable clicking - following codebase patterns)
         Button optionsButton = new Button("•••"); // Simple text instead of SVG
         optionsButton.addStyleName(styles_.optionsButton());
         

         
         // Create options menu
         final VerticalPanel optionsMenu = createRuleOptionsMenu(ruleIndex, rule);
         
         // Style the menu as a minimal popup that can overlap other sections
         optionsMenu.setVisible(false);
         optionsMenu.addStyleName(styles_.optionsMenu());
         
         // Store menu for later access by click handler
         ruleMenus_.put(ruleIndex, optionsMenu);
         
         // Use the EXACT same pattern as working buttons (Add Rule, Sign out, etc.)
         addNativeClickHandler(optionsButton.getElement(), "Options-" + ruleIndex);
         
         // Add options button directly to rule container (positioned absolutely)
         ruleContainer.add(ruleContent);
         ruleContainer.add(optionsButton);
         ruleContainer.add(optionsMenu);
         

         
         rulesContainer_.add(ruleContainer); // Add to bottom for correct indexing
      }
   }
   
   private void buildRulesFileDisplay()
   {
      rulesFileContainer_.clear();
      
      if (currentRulesFilePath_ != null && !currentRulesFilePath_.isEmpty()) {
         // Show the container with file path
         rulesFileContainer_.setVisible(true);
         
         // File path label with clickable underline - wrapping text like BYOK
         HTML filePathLabel = new HTML(currentRulesFilePath_);
         filePathLabel.addStyleName(styles_.keyStoredText());
         filePathLabel.getElement().getStyle().setProperty("textDecoration", "underline");
         filePathLabel.getElement().getStyle().setProperty("cursor", "pointer");
         filePathLabel.getElement().getStyle().setProperty("marginRight", "8px");
         filePathLabel.getElement().getStyle().setProperty("wordBreak", "break-word");
         filePathLabel.getElement().getStyle().setProperty("flex", "1");
         addNativeClickHandler(filePathLabel.getElement(), "OpenRulesFile");
         rulesFileContainer_.add(filePathLabel);
         
         // Delete icon - use Label with SVG like BYOK
         Label deleteIcon = new Label();
         deleteIcon.getElement().setInnerHTML("<svg width='16' height='16' viewBox='0 0 16 16' xmlns='http://www.w3.org/2000/svg' fill='currentColor'><path fill-rule='evenodd' clip-rule='evenodd' d='M10 3h3v1h-1v9l-1 1H4l-1-1V4H2V3h3V2a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v1zM9 2H6v1h3V2zM4 13h7V4H4v9zm2-8H5v7h1V5zm1 0h1v7H7V5zm2 0h1v7H9V5z'/></svg>");
         deleteIcon.getElement().getStyle().setProperty("cursor", "pointer");
         deleteIcon.getElement().getStyle().setProperty("display", "inline-flex");
         deleteIcon.getElement().getStyle().setProperty("alignItems", "center");
         deleteIcon.getElement().getStyle().setProperty("userSelect", "none");
         deleteIcon.getElement().getStyle().setProperty("marginLeft", "8px");
         
         addNativeClickHandler(deleteIcon.getElement(), "RemoveRulesFile");
         rulesFileContainer_.add(deleteIcon);
      } else {
         // Hide the container when no file is attached
         rulesFileContainer_.setVisible(false);
      }
   }
   
      private void toggleOptionsMenu(VerticalPanel targetMenu) {
      
      // Hide all other menus first
      for (int i = 0; i < rulesContainer_.getWidgetCount(); i++) {
         VerticalPanel container = (VerticalPanel) rulesContainer_.getWidget(i);
         for (int j = 0; j < container.getWidgetCount(); j++) {
            Widget widget = container.getWidget(j);
            if (widget instanceof VerticalPanel && widget != targetMenu && ruleMenus_.containsValue(widget)) {
               widget.setVisible(false);
            }
         }
      }
      
      // Toggle target menu visibility
      boolean newVisible = !targetMenu.isVisible();
      if (newVisible) {
         // Position the menu correctly using fixed positioning
         positionFixedMenu(targetMenu);
         // Add click outside handler to hide menu
         addClickOutsideHandler(targetMenu);
      }
      targetMenu.setVisible(newVisible);
   }
   
   private void positionFixedMenu(VerticalPanel menu) {
      // Find the button that triggered this menu
      Button triggerButton = findTriggerButton(menu);
      if (triggerButton != null) {
         positionMenuRelativeToButton(menu.getElement(), triggerButton.getElement());
      }
   }
   
   private Button findTriggerButton(VerticalPanel menu) {
      // Find which rule index this menu belongs to
      for (Map.Entry<Integer, VerticalPanel> entry : ruleMenus_.entrySet()) {
         if (entry.getValue() == menu) {
            int ruleIndex = entry.getKey();
            // Find the button in the corresponding rule container
            if (ruleIndex < rulesContainer_.getWidgetCount()) {
               VerticalPanel ruleContainer = (VerticalPanel) rulesContainer_.getWidget(ruleIndex);
               for (int j = 0; j < ruleContainer.getWidgetCount(); j++) {
                  Widget widget = ruleContainer.getWidget(j);
                  if (widget instanceof Button) {
                     return (Button) widget;
                  }
               }
            }
            break;
         }
      }
      return null;
   }
   
   private native void positionMenuRelativeToButton(com.google.gwt.dom.client.Element menuElement, com.google.gwt.dom.client.Element buttonElement) /*-{
      var buttonRect = buttonElement.getBoundingClientRect();
      var windowHeight = $wnd.innerHeight;
      var menuHeight = 80; // Approximate menu height
      
      // Position to the right of the button, below it
      var left = buttonRect.right - 60; // Align right edge with some offset
      var top = buttonRect.bottom + 2; // Just below the button
      
      // Adjust if it would go off the bottom of the screen
      if (top + menuHeight > windowHeight) {
         top = buttonRect.top - menuHeight - 2; // Position above the button instead
      }
      
      // Adjust if it would go off the left of the screen  
      if (left < 5) {
         left = 5;
      }
      
      menuElement.style.left = left + 'px';
      menuElement.style.top = top + 'px';
   }-*/;
      
   private void addClickOutsideHandler(VerticalPanel menu) {
      addClickOutsideHandlerNative(menu.getElement());
   }
   
   private native void addClickOutsideHandlerNative(com.google.gwt.dom.client.Element menuElement) /*-{
      var self = this;
      
      // Remove any existing outside click handler
      if ($wnd.currentMenuOutsideHandler) {
         $doc.removeEventListener('click', $wnd.currentMenuOutsideHandler);
      }
      
      // Add new outside click handler
      $wnd.currentMenuOutsideHandler = function(event) {
         // Check if click is outside the menu
         if (!menuElement.contains(event.target)) {
            // Hide the menu
            menuElement.style.display = 'none';
            // Remove the handler
            $doc.removeEventListener('click', $wnd.currentMenuOutsideHandler);
            $wnd.currentMenuOutsideHandler = null;
         }
      };
      
      // Add handler after a short delay to avoid immediate hiding
      setTimeout(function() {
         $doc.addEventListener('click', $wnd.currentMenuOutsideHandler);
      }, 100);
   }-*/;
   

   

   
   private VerticalPanel createRuleOptionsMenu(final int ruleIndex, final String rule)
   {
      VerticalPanel menu = new VerticalPanel();
      menu.setWidth("100%");
      
      // Create Edit button using the working pattern
      Button editButton = new Button("Edit");
      editButton.addStyleName(styles_.menuButton());
      
      // Use the SAME working pattern as triple dots
      addNativeClickHandler(editButton.getElement(), "Edit-" + ruleIndex);
      
      // Create Delete button using the working pattern  
      Button deleteButton = new Button("Delete");
      deleteButton.addStyleName(styles_.menuButton());
      
      // Use the SAME working pattern as triple dots
      addNativeClickHandler(deleteButton.getElement(), "Delete-" + ruleIndex);
      

      
      menu.add(editButton);
      menu.add(deleteButton);
      
      return menu;
   }
   

   
   private void startEditingRule(int ruleIndex, String currentRule)
   {
      // Hide the rule in the list and show edit interface
      // For simplicity, we'll replace the rule display with an edit interface
      VerticalPanel ruleContainer = (VerticalPanel) rulesContainer_.getWidget(ruleIndex);
      ruleContainer.clear();
      
      // Container for input and buttons with relative positioning
      VerticalPanel editContainer = new VerticalPanel();
      editContainer.setWidth("100%");
      editContainer.addStyleName(styles_.inputContainer());
      
      // Add edit interface - use TextArea to match new input styling
      TextArea editInput = new TextArea();
      editInput.setValue(currentRule);
      editInput.addStyleName(styles_.ruleTextArea());
      editInput.setVisibleLines(3); // Start with 3 lines
      editContainer.add(editInput);
      
      // Button panel on the right
      FlowPanel buttonPanel = new FlowPanel();
      buttonPanel.getElement().getStyle().setProperty("display", "flex");
      buttonPanel.getElement().getStyle().setProperty("gap", "8px");
      buttonPanel.getElement().getStyle().setProperty("justifyContent", "flex-end");
      buttonPanel.getElement().getStyle().setProperty("marginTop", "8px");
      
      Button saveButton = new Button("Save");
      saveButton.addStyleName(styles_.lightGrayButton());
      
      // Store the edit input globally for access in handler
      storeEditInput(ruleIndex, editInput);
      
      // Use the SAME working pattern as all other buttons
      addNativeClickHandler(saveButton.getElement(), "EditSave-" + ruleIndex);
      buttonPanel.add(saveButton);
      
      Button cancelButton = new Button("Cancel");
      cancelButton.addStyleName(styles_.lightGrayButton());
      
      // Use the SAME working pattern as all other buttons
      addNativeClickHandler(cancelButton.getElement(), "EditCancel-" + ruleIndex);
      buttonPanel.add(cancelButton);
      
      editContainer.add(buttonPanel);
      ruleContainer.add(editContainer);
   }
   
   private void storeEditInput(int ruleIndex, TextArea editInput) {
      editInputs_.put(ruleIndex, editInput);
   }
   
   private void storeBYOKInputs(String provider, TextBox apiKeyInput, String displayName) {
      byokApiKeyInputs_.put(provider, apiKeyInput);
      byokDisplayNames_.put(provider, displayName);
   }
   
   private void storeBYOKContainers(String provider, FlowPanel inputContainer, HorizontalPanel storedContainer) {
      byokInputContainers_.put(provider, inputContainer);
      byokStoredContainers_.put(provider, storedContainer);
   }
   
   private void buildSecuritySection()
   {
      VerticalPanel section = new VerticalPanel();
      section.setWidth("100%");
      
      // Section header
      HorizontalPanel headerPanel = createSectionHeader("Security", "security", securitySectionExpanded_);
      section.add(headerPanel);
      
      // Add chevron button positioned absolutely on the right
      HTML chevronButton = createChevronButton("security", securitySectionExpanded_);
      section.add(chevronButton);
      
      // Content section (collapsible)
      VerticalPanel contentPanel = new VerticalPanel();
      contentPanel.setWidth("100%");
      contentPanel.addStyleName(styles_.sectionContent());
      if (!securitySectionExpanded_) {
         contentPanel.addStyleName(styles_.sectionContentCollapsed());
      }
      
      // Security mode setting
      HorizontalPanel securityModePanel = new HorizontalPanel();
      securityModePanel.setWidth("100%");
      securityModePanel.addStyleName(styles_.settingRow());
      
      VerticalPanel securityModeContainer = new VerticalPanel();
      securityModeContainer.setWidth("100%");
      
      HTML securityModeLabel = new HTML("<b>Secure mode</b>");
      securityModeLabel.addStyleName(styles_.settingLabel());
      securityModeContainer.add(securityModeLabel);
      
      // Create horizontal panel with text on left and toggle on right
      HorizontalPanel securityTogglePanel = new HorizontalPanel();
      securityTogglePanel.setWidth("100%");
      securityTogglePanel.setVerticalAlignment(HorizontalPanel.ALIGN_TOP);
      
      securityModeText_ = new Label("On secure mode, no analytics are collected and zero data is retained by the model providers. Secure mode only uses search-replace for editing files. This must be used for any sensitive data like PHI. On \"Improve Rao for everyone,\" user analytics are collected to improve the experience. Still, zero data is retained by the model providers. Your current mode is: Secure");
      securityModeText_.addStyleName(styles_.settingsDescription());
      securityModeText_.getElement().getStyle().setProperty("marginRight", "15px");
      securityModeText_.setWidth("100%");
      securityTogglePanel.add(securityModeText_);
      securityTogglePanel.setCellWidth(securityModeText_, "100%");
      
      securityModeToggle_ = new HTML();
      securityModeToggle_.getElement().setInnerHTML(
         "<div style='position: relative; width: 32px; height: 16px; border-radius: 8px; cursor: pointer; transition: background 0.3s; display: none;' data-setting='security_mode' class='ai-toggle-enabled'>" +
         "<div class='" + styles_.toggleShadow() + "' style='position: absolute; top: 1px; right: 1px; width: 14px; height: 14px; background: white; border-radius: 50%; transition: left 0.3s, right 0.3s;'></div>" +
         "</div>"
      );
      addNativeSecurityModeChangeHandler(securityModeToggle_.getElement());
      securityTogglePanel.add(securityModeToggle_);
      
      securityModeContainer.add(securityTogglePanel);
      securityModePanel.add(securityModeContainer);
      contentPanel.add(securityModePanel);
      
      // Web search setting
      HorizontalPanel webSearchPanel = new HorizontalPanel();
      webSearchPanel.setWidth("100%");
      webSearchPanel.addStyleName(styles_.settingRow());
      
      VerticalPanel webSearchContainer = new VerticalPanel();
      webSearchContainer.setWidth("100%");
      
      HTML webSearchLabel = new HTML("<b>Web search</b>");
      webSearchLabel.addStyleName(styles_.settingLabel());
      webSearchContainer.add(webSearchLabel);
      
      // Create horizontal panel with text on left and toggle on right
      HorizontalPanel webSearchTogglePanel = new HorizontalPanel();
      webSearchTogglePanel.setWidth("100%");
      webSearchTogglePanel.setVerticalAlignment(HorizontalPanel.ALIGN_TOP);
      
      webSearchText_ = new Label("When web search is on, the model may choose to search the web. Such searches could involve information from the conversation history and should be disabled for sensitive data like PHI. Web search is currently: off");
      webSearchText_.addStyleName(styles_.settingsDescription());
      webSearchText_.getElement().getStyle().setProperty("marginRight", "15px");
      webSearchText_.setWidth("100%");
      webSearchTogglePanel.add(webSearchText_);
      webSearchTogglePanel.setCellWidth(webSearchText_, "100%");
      
      webSearchToggle_ = new HTML();
      webSearchToggle_.getElement().setInnerHTML(
         "<div style='position: relative; width: 32px; height: 16px; border-radius: 8px; cursor: pointer; transition: background 0.3s; display: none;' data-setting='web_search' class='ai-toggle-disabled'>" +
         "<div class='" + styles_.toggleShadow() + "' style='position: absolute; top: 1px; left: 1px; width: 14px; height: 14px; background: white; border-radius: 50%; transition: left 0.3s;'></div>" +
         "</div>"
      );
      addNativeWebSearchChangeHandler(webSearchToggle_.getElement());
      webSearchTogglePanel.add(webSearchToggle_);
      
      webSearchContainer.add(webSearchTogglePanel);
      webSearchPanel.add(webSearchContainer);
      contentPanel.add(webSearchPanel);
      
      // Add content panel to section
      section.add(contentPanel);
      
      securitySection_.getElement().setInnerHTML("");
      securitySection_.getElement().appendChild(section.getElement());
      
      // Apply collapsed class if section is collapsed
      if (!securitySectionExpanded_) {
         securitySection_.addStyleName(styles_.collapsed());
         // Immediately apply the visual collapse using JavaScript
         applyImmediateCollapse(securitySection_.getElement());
      } else {
         securitySection_.removeStyleName(styles_.collapsed());
      }
   }
   
   private void buildAutomationSection()
   {
      automationListContainers_.clear(); // Clear stale container references BEFORE creating new ones
      
      VerticalPanel section = new VerticalPanel();
      section.setWidth("100%");
      
      // Section header
      HorizontalPanel headerPanel = createSectionHeader("Automation", "automation", automationSectionExpanded_);
      section.add(headerPanel);
      
      // Add chevron button positioned absolutely on the right
      HTML chevronButton = createChevronButton("automation", automationSectionExpanded_);
      section.add(chevronButton);
      
      // Content section (collapsible)
      VerticalPanel contentPanel = new VerticalPanel();
      contentPanel.setWidth("100%");
      contentPanel.addStyleName(styles_.sectionContent());
      if (!automationSectionExpanded_) {
         contentPanel.addStyleName(styles_.sectionContentCollapsed());
      }
      
      // Auto-accept edits setting
      contentPanel.add(createAutomationToggle(
         "Auto-accept edits",
         "When enabled, edits proposed by the model will be automatically accepted without user confirmation.",
         "autoAcceptEdits"
      ));
      
      // Auto-accept console commands setting
      contentPanel.add(createAutomationToggle(
         "Auto-accept console commands",
         "When enabled, if the model proposes console commands, those on an allow list will be automatically executed without user confirmation. Commands to not run can be specified in a deny list.",
         "autoAcceptConsole"
      ));
      // Add allow/deny panel at the same level to avoid table layout constraints
      VerticalPanel consoleAllowDenyPanel = createAllowDenyListPanel("auto_accept_console");
      consoleAllowDenyPanel.getElement().setAttribute("data-automation-panel", "autoAcceptConsole");
      consoleAllowDenyPanel.getElement().getStyle().setProperty("display", "none"); // Start hidden
      contentPanel.add(consoleAllowDenyPanel);
      
      // Auto-accept terminal commands setting
      contentPanel.add(createAutomationToggle(
         "Auto-accept terminal commands",
         "When enabled, if the model proposes terminal commands, those on an allow list will be automatically executed without user confirmation. Commands to not run can be specified in a deny list.",
         "autoAcceptTerminal"
      ));
      // Add allow/deny panel at the same level to avoid table layout constraints
      VerticalPanel terminalAllowDenyPanel = createAllowDenyListPanel("auto_accept_terminal");
      terminalAllowDenyPanel.getElement().setAttribute("data-automation-panel", "autoAcceptTerminal");
      terminalAllowDenyPanel.getElement().getStyle().setProperty("display", "none"); // Start hidden
      contentPanel.add(terminalAllowDenyPanel);
      
      // Auto-run files setting
      contentPanel.add(createAutomationToggle(
         "Auto-run code from files",
         "When enabled, if the model proposes to run code from allowed files, that code will be automatically executed without user confirmation. Files to not run can be specified in a deny list.",
         "autoRunFiles"
      ));
      // Add allow/deny panel at the same level to avoid table layout constraints
      VerticalPanel runFilesAllowDenyPanel = createAllowDenyListPanel("auto_run_files");
      runFilesAllowDenyPanel.getElement().setAttribute("data-automation-panel", "autoRunFiles");
      runFilesAllowDenyPanel.getElement().getStyle().setProperty("display", "none"); // Start hidden
      contentPanel.add(runFilesAllowDenyPanel);
      
      // Auto-delete files setting
      contentPanel.add(createAutomationToggle(
         "Auto-delete files",
         "When enabled, if the model proposes to delete files, those deletions will be automatically executed without user confirmation.",
         "autoDeleteFiles"
      ));
      
      // Add content panel to section
      section.add(contentPanel);
      
      automationSection_.getElement().setInnerHTML("");
      automationSection_.getElement().appendChild(section.getElement());
      
      // Apply collapsed class if section is collapsed
      if (!automationSectionExpanded_) {
         automationSection_.addStyleName(styles_.collapsed());
         // Immediately apply the visual collapse using JavaScript
         applyImmediateCollapse(automationSection_.getElement());
      } else {
         automationSection_.removeStyleName(styles_.collapsed());
      }
   }
   
   private void buildBYOKSection()
   {
      VerticalPanel section = new VerticalPanel();
      section.setWidth("100%");
      
      // Section header
      HorizontalPanel headerPanel = createSectionHeader("Bring Your Own Key", "byok", byokSectionExpanded_);
      section.add(headerPanel);
      
      // Add chevron button
      HTML chevronButton = createChevronButton("byok", byokSectionExpanded_);
      section.add(chevronButton);
      
      // Content section (collapsible)
      VerticalPanel contentPanel = new VerticalPanel();
      contentPanel.setWidth("100%");
      contentPanel.addStyleName(styles_.sectionContent());
      if (!byokSectionExpanded_) {
         contentPanel.addStyleName(styles_.sectionContentCollapsed());
      }
      
      // Description
      HTML description = new HTML("Use your own API keys for AI providers. Your keys are stored securely and requests are routed through a local proxy.");
      description.addStyleName(styles_.settingsDescription());
      description.getElement().getStyle().setProperty("marginBottom", "15px");
      contentPanel.add(description);
      
      // Anthropic BYOK
      contentPanel.add(createBYOKProviderPanel("anthropic", "Anthropic"));
      
      // OpenAI BYOK
      contentPanel.add(createBYOKProviderPanel("openai", "OpenAI"));
      
      // SageMaker BYOK (special panel with multiple inputs)
      contentPanel.add(createSageMakerProviderPanel());
      
      // Local Model BYOK (special panel with multiple inputs)
      contentPanel.add(createLocalModelProviderPanel());
      
      // Add content panel to section
      section.add(contentPanel);
      
      byokSection_.getElement().setInnerHTML("");
      byokSection_.getElement().appendChild(section.getElement());
      
      // Apply collapsed class if section is collapsed
      if (!byokSectionExpanded_) {
         byokSection_.addStyleName(styles_.collapsed());
         applyImmediateCollapse(byokSection_.getElement());
      } else {
         byokSection_.removeStyleName(styles_.collapsed());
      }
   }
   
   private VerticalPanel createBYOKProviderPanel(final String provider, String displayName)
   {
      VerticalPanel panel = new VerticalPanel();
      panel.setWidth("100%");
      panel.addStyleName(styles_.ruleContainer());
      panel.getElement().getStyle().setProperty("marginBottom", "15px");
      
      // Title row with toggle
      HorizontalPanel titleRow = new HorizontalPanel();
      titleRow.setWidth("100%");
      titleRow.setVerticalAlignment(HorizontalPanel.ALIGN_MIDDLE);
      
      HTML label = new HTML("Use my own " + displayName + " API key");
      label.addStyleName(styles_.settingLabel());
      titleRow.add(label);
      
      // Toggle (hidden initially until we check status)
      final HTML toggle = new HTML();
      toggle.getElement().setInnerHTML(
         "<div style='position: relative; width: 32px; height: 16px; border-radius: 8px; cursor: pointer; transition: background 0.3s; display: none;' data-byok-provider='" + provider + "' class='ai-toggle-disabled'>" +
         "<div class='" + styles_.toggleShadow() + "' style='position: absolute; top: 1px; left: 1px; width: 14px; height: 14px; background: white; border-radius: 50%; transition: left 0.3s, right 0.3s;'></div>" +
         "</div>"
      );
      titleRow.add(toggle);
      titleRow.setCellHorizontalAlignment(toggle, HorizontalPanel.ALIGN_RIGHT);
      
      panel.add(titleRow);
      
      // Stored key display container (initially hidden) - use HorizontalPanel for reliable side-by-side layout
      final HorizontalPanel storedKeyContainer = new HorizontalPanel();
      storedKeyContainer.setVisible(false);
      storedKeyContainer.setVerticalAlignment(HorizontalPanel.ALIGN_MIDDLE);
      storedKeyContainer.getElement().getStyle().setProperty("marginTop", "10px");
      storedKeyContainer.getElement().getStyle().setProperty("marginBottom", "4px");
      storedKeyContainer.getElement().setAttribute("data-byok-stored", provider);
      
      HTML storedKeyText = new HTML("Key securely stored");
      storedKeyText.addStyleName(styles_.keyStoredText());
      storedKeyContainer.add(storedKeyText);
      
      // Delete icon - use Label like the × button for list items
      Label deleteIcon = new Label();
      deleteIcon.getElement().setInnerHTML("<svg width='16' height='16' viewBox='0 0 16 16' xmlns='http://www.w3.org/2000/svg' fill='currentColor'><path fill-rule='evenodd' clip-rule='evenodd' d='M10 3h3v1h-1v9l-1 1H4l-1-1V4H2V3h3V2a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v1zM9 2H6v1h3V2zM4 13h7V4H4v9zm2-8H5v7h1V5zm1 0h1v7H7V5zm2 0h1v7H9V5z'/></svg>");
      deleteIcon.getElement().getStyle().setProperty("cursor", "pointer");
      deleteIcon.getElement().getStyle().setProperty("display", "inline-flex");
      deleteIcon.getElement().getStyle().setProperty("alignItems", "center");
      deleteIcon.getElement().getStyle().setProperty("userSelect", "none");
      deleteIcon.getElement().getStyle().setProperty("marginLeft", "8px");
      
      // Use the same native click handler pattern as other settings buttons
      addNativeClickHandler(deleteIcon.getElement(), "DeleteBYOK-" + provider);
      storedKeyContainer.add(deleteIcon);
      
      panel.add(storedKeyContainer);
      
      // API key input container (initially hidden) - use FlowPanel to avoid table layout
      final FlowPanel inputContainer = new FlowPanel();
      inputContainer.setWidth("100%");
      inputContainer.setVisible(false);
      inputContainer.getElement().getStyle().setProperty("marginTop", "10px");
      inputContainer.getElement().setAttribute("data-byok-input", provider);
      
      // Input field - styled exactly like allow/deny list inputs
      final TextBox apiKeyInput = new TextBox();
      apiKeyInput.addStyleName(styles_.settingInput());
      apiKeyInput.setWidth("100%");
      apiKeyInput.getElement().getStyle().setProperty("fontSize", "13px");
      apiKeyInput.getElement().getStyle().setProperty("display", "block");
      apiKeyInput.getElement().setAttribute("placeholder", "Type and press Enter to add");
      apiKeyInput.getElement().setAttribute("type", "password");
      inputContainer.add(apiKeyInput);
      
      // Store references for the handler
      storeBYOKInputs(provider, apiKeyInput, displayName);
      
      // Add Enter key handler to save API key
      addNativeBYOKKeyHandler(apiKeyInput.getElement(), provider);
      
      panel.add(inputContainer);
      
      // Store container references for later use
      storeBYOKContainers(provider, inputContainer, storedKeyContainer);
      
      // Check if BYOK is enabled for this provider and update UI
      server_.isBYOKEnabled(provider, new ServerRequestCallback<Boolean>()
      {
         @Override
         public void onResponseReceived(Boolean enabled)
         {
            // Show toggle
            toggle.getElement().getFirstChildElement().getStyle().setProperty("display", "block");
            
            // Update toggle state
            if (enabled) {
               toggle.getElement().getFirstChildElement().addClassName("ai-toggle-enabled");
               toggle.getElement().getFirstChildElement().removeClassName("ai-toggle-disabled");
               com.google.gwt.dom.client.Element knob = toggle.getElement().getFirstChildElement().getFirstChildElement().cast();
               knob.getStyle().setProperty("left", "auto");
               knob.getStyle().setProperty("right", "1px");
               
               // Check if a key is already stored
               server_.hasBYOKApiKey(provider, new ServerRequestCallback<Boolean>()
               {
                  @Override
                  public void onResponseReceived(Boolean hasKey)
                  {
                     if (hasKey) {
                        storedKeyContainer.setVisible(true);
                        inputContainer.setVisible(false);
                     } else {
                        storedKeyContainer.setVisible(false);
                        inputContainer.setVisible(true);
                     }
                  }
                  
                  @Override
                  public void onError(ServerError error)
                  {
                     Debug.log("Error checking if BYOK key exists for " + provider + ": " + error.getMessage());
                     inputContainer.setVisible(true);
                  }
               });
            } else {
               toggle.getElement().getFirstChildElement().addClassName("ai-toggle-disabled");
               toggle.getElement().getFirstChildElement().removeClassName("ai-toggle-enabled");
               storedKeyContainer.setVisible(false);
               inputContainer.setVisible(false);
            }
            
            // Add toggle click handler
            addNativeBYOKToggleHandler(toggle.getElement(), provider, inputContainer.getElement(), storedKeyContainer.getElement());
         }
         
         @Override
         public void onError(ServerError error)
         {
            Debug.log("Error checking BYOK status for " + provider + ": " + error.getMessage());
         }
      });
      
      return panel;
   }
   
   private VerticalPanel createSageMakerProviderPanel()
   {
      final String provider = "sagemaker";
      final String displayName = "AWS SageMaker";
      
      VerticalPanel panel = new VerticalPanel();
      panel.setWidth("100%");
      panel.addStyleName(styles_.ruleContainer());
      panel.getElement().getStyle().setProperty("marginBottom", "15px");
      
      // Title row with toggle
      HorizontalPanel titleRow = new HorizontalPanel();
      titleRow.setWidth("100%");
      titleRow.setVerticalAlignment(HorizontalPanel.ALIGN_MIDDLE);
      
      HTML label = new HTML("Use my own " + displayName + " endpoint");
      label.addStyleName(styles_.settingLabel());
      titleRow.add(label);
      
      // Toggle (hidden initially until we check status)
      final HTML toggle = new HTML();
      toggle.getElement().setInnerHTML(
         "<div style='position: relative; width: 32px; height: 16px; border-radius: 8px; cursor: pointer; transition: background 0.3s; display: none;' data-byok-provider='" + provider + "' class='ai-toggle-disabled'>" +
         "<div class='" + styles_.toggleShadow() + "' style='position: absolute; top: 1px; left: 1px; width: 14px; height: 14px; background: white; border-radius: 50%; transition: left 0.3s, right 0.3s;'></div>" +
         "</div>"
      );
      titleRow.add(toggle);
      titleRow.setCellHorizontalAlignment(toggle, HorizontalPanel.ALIGN_RIGHT);
      
      panel.add(titleRow);
      
      // Stored key display container (initially hidden)
      sagemakerStoredContainer_ = new HorizontalPanel();
      sagemakerStoredContainer_.setVisible(false);
      sagemakerStoredContainer_.setVerticalAlignment(HorizontalPanel.ALIGN_MIDDLE);
      sagemakerStoredContainer_.getElement().getStyle().setProperty("marginTop", "10px");
      sagemakerStoredContainer_.getElement().getStyle().setProperty("marginBottom", "4px");
      sagemakerStoredContainer_.getElement().setAttribute("data-byok-stored", provider);
      
      HTML storedKeyText = new HTML("Credentials and endpoint configured");
      storedKeyText.addStyleName(styles_.keyStoredText());
      sagemakerStoredContainer_.add(storedKeyText);
      
      // Delete icon
      Label deleteIcon = new Label();
      deleteIcon.getElement().setInnerHTML("<svg width='16' height='16' viewBox='0 0 16 16' xmlns='http://www.w3.org/2000/svg' fill='currentColor'><path fill-rule='evenodd' clip-rule='evenodd' d='M10 3h3v1h-1v9l-1 1H4l-1-1V4H2V3h3V2a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v1zM9 2H6v1h3V2zM4 13h7V4H4v9zm2-8H5v7h1V5zm1 0h1v7H7V5zm2 0h1v7H9V5z'/></svg>");
      deleteIcon.getElement().getStyle().setProperty("cursor", "pointer");
      deleteIcon.getElement().getStyle().setProperty("display", "inline-flex");
      deleteIcon.getElement().getStyle().setProperty("alignItems", "center");
      deleteIcon.getElement().getStyle().setProperty("marginLeft", "10px");
      deleteIcon.getElement().getStyle().setProperty("padding", "4px");
      deleteIcon.getElement().getStyle().setProperty("borderRadius", "3px");
      deleteIcon.getElement().getStyle().setProperty("transition", "background 0.2s");
      
      addNativeClickHandler(deleteIcon.getElement(), "DeleteBYOK-" + provider);
      sagemakerStoredContainer_.add(deleteIcon);
      
      panel.add(sagemakerStoredContainer_);
      
      // Input container (initially hidden)
      sagemakerInputContainer_ = new FlowPanel();
      sagemakerInputContainer_.setWidth("100%");
      sagemakerInputContainer_.setVisible(false);
      sagemakerInputContainer_.getElement().getStyle().setProperty("marginTop", "10px");
      sagemakerInputContainer_.getElement().setAttribute("data-byok-input", provider);
      
      // AWS Access Key ID input
      sagemakerAccessKeyInput_ = new TextBox();
      sagemakerAccessKeyInput_.addStyleName(styles_.settingInput());
      sagemakerAccessKeyInput_.setWidth("100%");
      sagemakerAccessKeyInput_.getElement().getStyle().setProperty("fontSize", "13px");
      sagemakerAccessKeyInput_.getElement().getStyle().setProperty("display", "block");
      sagemakerAccessKeyInput_.getElement().setAttribute("placeholder", "AWS Access Key ID");
      sagemakerAccessKeyInput_.getElement().setAttribute("type", "password");
      sagemakerInputContainer_.add(sagemakerAccessKeyInput_);
      
      // AWS Secret Access Key input
      sagemakerSecretKeyInput_ = new TextBox();
      sagemakerSecretKeyInput_.addStyleName(styles_.settingInput());
      sagemakerSecretKeyInput_.setWidth("100%");
      sagemakerSecretKeyInput_.getElement().getStyle().setProperty("fontSize", "13px");
      sagemakerSecretKeyInput_.getElement().getStyle().setProperty("display", "block");
      sagemakerSecretKeyInput_.getElement().setAttribute("placeholder", "AWS Secret Access Key");
      sagemakerSecretKeyInput_.getElement().setAttribute("type", "password");
      sagemakerInputContainer_.add(sagemakerSecretKeyInput_);
      
      // SageMaker Endpoint Name input
      sagemakerEndpointInput_ = new TextBox();
      sagemakerEndpointInput_.addStyleName(styles_.settingInput());
      sagemakerEndpointInput_.setWidth("100%");
      sagemakerEndpointInput_.getElement().getStyle().setProperty("fontSize", "13px");
      sagemakerEndpointInput_.getElement().getStyle().setProperty("display", "block");
      sagemakerEndpointInput_.getElement().setAttribute("placeholder", "SageMaker Endpoint Name");
      sagemakerInputContainer_.add(sagemakerEndpointInput_);
      
      // AWS Region input
      sagemakerRegionInput_ = new TextBox();
      sagemakerRegionInput_.addStyleName(styles_.settingInput());
      sagemakerRegionInput_.setWidth("100%");
      sagemakerRegionInput_.getElement().getStyle().setProperty("fontSize", "13px");
      sagemakerRegionInput_.getElement().getStyle().setProperty("display", "block");
      sagemakerRegionInput_.getElement().setAttribute("placeholder", "AWS Region (e.g., us-east-1)");
      sagemakerRegionInput_.setValue("us-east-1");
      sagemakerInputContainer_.add(sagemakerRegionInput_);
      
      // Model Name input
      sagemakerModelInput_ = new TextBox();
      sagemakerModelInput_.addStyleName(styles_.settingInput());
      sagemakerModelInput_.setWidth("100%");
      sagemakerModelInput_.getElement().getStyle().setProperty("fontSize", "13px");
      sagemakerModelInput_.getElement().getStyle().setProperty("display", "block");
      sagemakerModelInput_.getElement().setAttribute("placeholder", "Model Name (e.g., Qwen/Qwen3-Coder-30B-A3B-Instruct)");
      sagemakerModelInput_.setValue("Qwen/Qwen3-Coder-30B-A3B-Instruct");
      sagemakerInputContainer_.add(sagemakerModelInput_);
      
      // Save button styled like other settings buttons
      Button saveSageMakerButton = new Button("Save Configuration");
      saveSageMakerButton.addStyleName(styles_.settingButton());
      saveSageMakerButton.addStyleName(styles_.primaryButton());
      saveSageMakerButton.getElement().getStyle().setProperty("marginTop", "8px");
      addNativeClickHandler(saveSageMakerButton.getElement(), "SaveSageMaker");
      sagemakerInputContainer_.add(saveSageMakerButton);
      
      panel.add(sagemakerInputContainer_);
      
      // Check if BYOK is enabled for SageMaker and update UI
      server_.isBYOKEnabled(provider, new ServerRequestCallback<Boolean>()
      {
         @Override
         public void onResponseReceived(Boolean enabled)
         {
            // Show toggle
            toggle.getElement().getFirstChildElement().getStyle().setProperty("display", "block");
            
            // Update toggle state
            if (enabled) {
               toggle.getElement().getFirstChildElement().addClassName("ai-toggle-enabled");
               toggle.getElement().getFirstChildElement().removeClassName("ai-toggle-disabled");
               com.google.gwt.dom.client.Element knob = toggle.getElement().getFirstChildElement().getFirstChildElement().cast();
               knob.getStyle().setProperty("left", "auto");
               knob.getStyle().setProperty("right", "1px");
               
               // Check if credentials are stored
               server_.hasBYOKApiKey(provider, new ServerRequestCallback<Boolean>()
               {
                  @Override
                  public void onResponseReceived(Boolean hasKey)
                  {
                     if (hasKey) {
                        sagemakerStoredContainer_.setVisible(true);
                        sagemakerInputContainer_.setVisible(false);
                        
                        // Load existing endpoint and region values
                        server_.getSageMakerEndpoint(new ServerRequestCallback<String>()
                        {
                           @Override
                           public void onResponseReceived(String endpoint)
                           {
                              sagemakerEndpointInput_.setValue(endpoint);
                           }
                           
                           @Override
                           public void onError(ServerError error)
                           {
                              Debug.log("Error loading SageMaker endpoint: " + error.getMessage());
                           }
                        });
                        
                        server_.getSageMakerRegion(new ServerRequestCallback<String>()
                        {
                           @Override
                           public void onResponseReceived(String region)
                           {
                              sagemakerRegionInput_.setValue(region);
                           }
                           
                           @Override
                           public void onError(ServerError error)
                           {
                              Debug.log("Error loading SageMaker region: " + error.getMessage());
                           }
                        });
                        
                        server_.getSageMakerModel(new ServerRequestCallback<String>()
                        {
                           @Override
                           public void onResponseReceived(String model)
                           {
                              sagemakerModelInput_.setValue(model);
                           }
                           
                           @Override
                           public void onError(ServerError error)
                           {
                              Debug.log("Error loading SageMaker model: " + error.getMessage());
                           }
                        });
                     } else {
                        sagemakerStoredContainer_.setVisible(false);
                        sagemakerInputContainer_.setVisible(true);
                     }
                  }
                  
                  @Override
                  public void onError(ServerError error)
                  {
                     Debug.log("Error checking if BYOK key exists for " + provider + ": " + error.getMessage());
                     sagemakerInputContainer_.setVisible(true);
                  }
               });
            } else {
               toggle.getElement().getFirstChildElement().addClassName("ai-toggle-disabled");
               toggle.getElement().getFirstChildElement().removeClassName("ai-toggle-enabled");
               sagemakerStoredContainer_.setVisible(false);
               sagemakerInputContainer_.setVisible(false);
            }
            
            // Add toggle click handler
            addNativeSageMakerToggleHandler(toggle.getElement(), sagemakerInputContainer_.getElement(), sagemakerStoredContainer_.getElement());
         }
         
         @Override
         public void onError(ServerError error)
         {
            Debug.log("Error checking BYOK status for " + provider + ": " + error.getMessage());
         }
      });
      
      return panel;
   }
   
   private VerticalPanel createLocalModelProviderPanel()
   {
      final String provider = "localmodel";
      final String displayName = "Local Model";
      
      VerticalPanel panel = new VerticalPanel();
      panel.setWidth("100%");
      panel.addStyleName(styles_.ruleContainer());
      panel.getElement().getStyle().setProperty("marginBottom", "15px");
      
      // Title row with toggle
      HorizontalPanel titleRow = new HorizontalPanel();
      titleRow.setWidth("100%");
      titleRow.setVerticalAlignment(HorizontalPanel.ALIGN_MIDDLE);
      
      HTML label = new HTML("Use my own " + displayName + " endpoint");
      label.addStyleName(styles_.settingLabel());
      titleRow.add(label);
      
      // Toggle (hidden initially until we check status)
      final HTML toggle = new HTML();
      toggle.getElement().setInnerHTML(
         "<div style='position: relative; width: 32px; height: 16px; border-radius: 8px; cursor: pointer; transition: background 0.3s; display: none;' data-byok-provider='" + provider + "' class='ai-toggle-disabled'>" +
         "<div class='" + styles_.toggleShadow() + "' style='position: absolute; top: 1px; left: 1px; width: 14px; height: 14px; background: white; border-radius: 50%; transition: left 0.3s, right 0.3s;'></div>" +
         "</div>"
      );
      titleRow.add(toggle);
      titleRow.setCellHorizontalAlignment(toggle, HorizontalPanel.ALIGN_RIGHT);
      
      panel.add(titleRow);
      
      // Stored configuration display container (initially hidden)
      final HorizontalPanel storedContainer = new HorizontalPanel();
      storedContainer.setVisible(false);
      storedContainer.setVerticalAlignment(HorizontalPanel.ALIGN_MIDDLE);
      storedContainer.getElement().getStyle().setProperty("marginTop", "10px");
      storedContainer.getElement().getStyle().setProperty("marginBottom", "4px");
      
      HTML storedText = new HTML("Endpoint configured");
      storedText.addStyleName(styles_.keyStoredText());
      storedContainer.add(storedText);
      
      // Delete icon
      Label deleteIcon = new Label();
      deleteIcon.getElement().setInnerHTML("<svg width='16' height='16' viewBox='0 0 16 16' xmlns='http://www.w3.org/2000/svg' fill='currentColor'><path fill-rule='evenodd' clip-rule='evenodd' d='M10 3h3v1h-1v9l-1 1H4l-1-1V4H2V3h3V2a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v1zM9 2H6v1h3V2zM4 13h7V4H4v9zm2-8H5v7h1V5zm1 0h1v7H7V5zm2 0h1v7H9V5z'/></svg>");
      deleteIcon.getElement().getStyle().setProperty("cursor", "pointer");
      deleteIcon.getElement().getStyle().setProperty("display", "inline-flex");
      deleteIcon.getElement().getStyle().setProperty("alignItems", "center");
      deleteIcon.getElement().getStyle().setProperty("userSelect", "none");
      deleteIcon.getElement().getStyle().setProperty("marginLeft", "8px");
      
      addNativeClickHandler(deleteIcon.getElement(), "DeleteLocalModelConfig");
      storedContainer.add(deleteIcon);
      
      panel.add(storedContainer);
      localModelStoredContainer_ = storedContainer;
      
      // Input container (initially hidden)
      final FlowPanel inputContainer = new FlowPanel();
      inputContainer.setWidth("100%");
      inputContainer.setVisible(false);
      inputContainer.getElement().getStyle().setProperty("marginTop", "10px");
      
      // Endpoint URL input
      HTML endpointLabel = new HTML("Endpoint URL:");
      endpointLabel.addStyleName(styles_.settingLabel());
      endpointLabel.getElement().getStyle().setProperty("display", "block");
      endpointLabel.getElement().getStyle().setProperty("marginBottom", "5px");
      endpointLabel.getElement().getStyle().setProperty("fontSize", "13px");
      inputContainer.add(endpointLabel);
      
      localModelEndpointInput_ = new TextBox();
      localModelEndpointInput_.addStyleName(styles_.settingInput());
      localModelEndpointInput_.setWidth("100%");
      localModelEndpointInput_.getElement().getStyle().setProperty("fontSize", "13px");
      localModelEndpointInput_.getElement().getStyle().setProperty("display", "block");
      localModelEndpointInput_.getElement().getStyle().setProperty("marginBottom", "10px");
      localModelEndpointInput_.getElement().setAttribute("placeholder", "http://localhost:11434");
      inputContainer.add(localModelEndpointInput_);
      
      // Model Name input
      HTML modelNameLabel = new HTML("Model Name:");
      modelNameLabel.addStyleName(styles_.settingLabel());
      modelNameLabel.getElement().getStyle().setProperty("display", "block");
      modelNameLabel.getElement().getStyle().setProperty("marginBottom", "5px");
      modelNameLabel.getElement().getStyle().setProperty("fontSize", "13px");
      inputContainer.add(modelNameLabel);
      
      localModelNameInput_ = new TextBox();
      localModelNameInput_.addStyleName(styles_.settingInput());
      localModelNameInput_.setWidth("100%");
      localModelNameInput_.getElement().getStyle().setProperty("fontSize", "13px");
      localModelNameInput_.getElement().getStyle().setProperty("display", "block");
      localModelNameInput_.getElement().getStyle().setProperty("marginBottom", "10px");
      localModelNameInput_.getElement().setAttribute("placeholder", "llama3.2:1b");
      inputContainer.add(localModelNameInput_);
      
      // API Key input (optional)
      HTML apiKeyLabel = new HTML("API Key (optional):");
      apiKeyLabel.addStyleName(styles_.settingLabel());
      apiKeyLabel.getElement().getStyle().setProperty("display", "block");
      apiKeyLabel.getElement().getStyle().setProperty("marginBottom", "5px");
      apiKeyLabel.getElement().getStyle().setProperty("fontSize", "13px");
      inputContainer.add(apiKeyLabel);
      
      localModelApiKeyInput_ = new PasswordTextBox();
      localModelApiKeyInput_.addStyleName(styles_.settingInput());
      localModelApiKeyInput_.setWidth("100%");
      localModelApiKeyInput_.getElement().getStyle().setProperty("fontSize", "13px");
      localModelApiKeyInput_.getElement().getStyle().setProperty("display", "block");
      localModelApiKeyInput_.getElement().getStyle().setProperty("marginBottom", "10px");
      localModelApiKeyInput_.getElement().setAttribute("placeholder", "Optional API key");
      inputContainer.add(localModelApiKeyInput_);
      
      // Save button styled like other settings buttons
      Button saveButton = new Button("Save Configuration");
      saveButton.addStyleName(styles_.settingButton());
      saveButton.addStyleName(styles_.primaryButton());
      saveButton.getElement().getStyle().setProperty("marginTop", "8px");
      addNativeClickHandler(saveButton.getElement(), "SaveLocalModelConfig");
      inputContainer.add(saveButton);
      
      panel.add(inputContainer);
      localModelInputContainer_ = inputContainer;
      
      // Check if BYOK is enabled and update UI
      server_.isBYOKEnabled(provider, new ServerRequestCallback<Boolean>()
      {
         @Override
         public void onResponseReceived(Boolean enabled)
         {
            toggle.getElement().getFirstChildElement().getStyle().setProperty("display", "block");
            
            if (enabled) {
               toggle.getElement().getFirstChildElement().addClassName("ai-toggle-enabled");
               toggle.getElement().getFirstChildElement().removeClassName("ai-toggle-disabled");
               com.google.gwt.dom.client.Element knob = toggle.getElement().getFirstChildElement().getFirstChildElement().cast();
               knob.getStyle().setProperty("left", "auto");
               knob.getStyle().setProperty("right", "1px");
               
               loadLocalModelConfiguration(storedContainer, inputContainer);
            } else {
               storedContainer.setVisible(false);
               inputContainer.setVisible(false);
            }
            
            addNativeBYOKToggleHandler(toggle.getElement(), provider, inputContainer.getElement(), storedContainer.getElement());
         }
         
         @Override
         public void onError(ServerError error)
         {
            Debug.log("Error checking Local Model BYOK status: " + error.getMessage());
         }
      });
      
      return panel;
   }
   
   private void loadLocalModelConfiguration(final HorizontalPanel storedContainer, final FlowPanel inputContainer)
   {
      server_.getLocalModelEndpoint(new ServerRequestCallback<String>()
      {
         @Override
         public void onResponseReceived(String endpoint)
         {
            if (endpoint != null && !endpoint.isEmpty()) {
               storedContainer.setVisible(true);
               inputContainer.setVisible(false);
               localModelEndpointInput_.setText(endpoint);
               
               server_.getLocalModelName(new ServerRequestCallback<String>()
               {
                  @Override
                  public void onResponseReceived(String modelName)
                  {
                     if (modelName != null && !modelName.isEmpty()) {
                        localModelNameInput_.setText(modelName);
                     }
                  }
                  
                  @Override
                  public void onError(ServerError error)
                  {
                     Debug.log("Error loading local model name: " + error.getMessage());
                  }
               });
            } else {
               storedContainer.setVisible(false);
               inputContainer.setVisible(true);
            }
         }
         
         @Override
         public void onError(ServerError error)
         {
            Debug.log("Error loading local model endpoint: " + error.getMessage());
            inputContainer.setVisible(true);
         }
      });
   }
   
   private native void addNativeSageMakerToggleHandler(com.google.gwt.dom.client.Element element, com.google.gwt.dom.client.Element inputContainer, com.google.gwt.dom.client.Element storedKeyContainer) /*-{
      var thiz = this;
      var toggleDiv = element.querySelector('[data-byok-provider="sagemaker"]');
      if (toggleDiv) {
         toggleDiv.onclick = function() {
            var isEnabled = toggleDiv.classList.contains('ai-toggle-enabled');
            var newEnabled = !isEnabled;
            
            // Update toggle visual
            if (newEnabled) {
               toggleDiv.classList.add('ai-toggle-enabled');
               toggleDiv.classList.remove('ai-toggle-disabled');
               var knob = toggleDiv.querySelector('div');
               knob.style.left = 'auto';
               knob.style.right = '1px';
               
               // Check if credentials are stored to show appropriate container
               thiz.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::checkAndShowSageMakerContainer(Lcom/google/gwt/dom/client/Element;Lcom/google/gwt/dom/client/Element;)(inputContainer, storedKeyContainer);
            } else {
               toggleDiv.classList.remove('ai-toggle-enabled');
               toggleDiv.classList.add('ai-toggle-disabled');
               var knob = toggleDiv.querySelector('div');
               knob.style.left = '1px';
               knob.style.right = 'auto';
               inputContainer.style.display = 'none';
               storedKeyContainer.style.display = 'none';
            }
            
            // Call handler
            thiz.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleBYOKEnabledChange(Ljava/lang/String;Z)("sagemaker", newEnabled);
         };
      }
   }-*/;
   
   private void checkAndShowSageMakerContainer(final com.google.gwt.dom.client.Element inputContainer, final com.google.gwt.dom.client.Element storedKeyContainer)
   {
      server_.hasBYOKApiKey("sagemaker", new ServerRequestCallback<Boolean>()
      {
         @Override
         public void onResponseReceived(Boolean hasKey)
         {
            if (hasKey) {
               storedKeyContainer.getStyle().setProperty("display", "block");
               inputContainer.getStyle().setProperty("display", "none");
            } else {
               storedKeyContainer.getStyle().setProperty("display", "none");
               inputContainer.getStyle().setProperty("display", "block");
            }
         }
         
         @Override
         public void onError(ServerError error)
         {
            Debug.log("Error checking if BYOK key exists: " + error.getMessage());
            inputContainer.getStyle().setProperty("display", "block");
            storedKeyContainer.getStyle().setProperty("display", "none");
         }
      });
   }
   
   private native void addNativeBYOKToggleHandler(com.google.gwt.dom.client.Element element, String provider, com.google.gwt.dom.client.Element inputContainer, com.google.gwt.dom.client.Element storedKeyContainer) /*-{
      var thiz = this;
      var toggleDiv = element.querySelector('[data-byok-provider="' + provider + '"]');
      if (toggleDiv) {
         toggleDiv.onclick = function() {
            var isEnabled = toggleDiv.classList.contains('ai-toggle-enabled');
            var newEnabled = !isEnabled;
            
            // Update toggle visual
            if (newEnabled) {
               toggleDiv.classList.add('ai-toggle-enabled');
               toggleDiv.classList.remove('ai-toggle-disabled');
               var knob = toggleDiv.querySelector('div');
               knob.style.left = 'auto';
               knob.style.right = '1px';
               
               // For local model, check endpoint configuration; for others, check API key
               if (provider === 'localmodel') {
                  thiz.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::checkAndShowLocalModelContainer(Lcom/google/gwt/dom/client/Element;Lcom/google/gwt/dom/client/Element;)(inputContainer, storedKeyContainer);
               } else {
                  thiz.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::checkAndShowBYOKContainer(Ljava/lang/String;Lcom/google/gwt/dom/client/Element;Lcom/google/gwt/dom/client/Element;)(provider, inputContainer, storedKeyContainer);
               }
            } else {
               toggleDiv.classList.remove('ai-toggle-enabled');
               toggleDiv.classList.add('ai-toggle-disabled');
               var knob = toggleDiv.querySelector('div');
               knob.style.left = '1px';
               knob.style.right = 'auto';
               inputContainer.style.display = 'none';
               storedKeyContainer.style.display = 'none';
            }
            
            // Call handler
            thiz.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleBYOKEnabledChange(Ljava/lang/String;Z)(provider, newEnabled);
         };
      }
   }-*/;
   
   private native void updateToggleState(String provider, boolean enabled) /*-{
      var toggleDiv = $doc.querySelector('[data-byok-provider="' + provider + '"]');
      if (toggleDiv) {
         if (enabled) {
            toggleDiv.classList.add('ai-toggle-enabled');
            toggleDiv.classList.remove('ai-toggle-disabled');
            var knob = toggleDiv.querySelector('div');
            if (knob) {
               knob.style.left = 'auto';
               knob.style.right = '1px';
            }
         } else {
            toggleDiv.classList.remove('ai-toggle-enabled');
            toggleDiv.classList.add('ai-toggle-disabled');
            var knob = toggleDiv.querySelector('div');
            if (knob) {
               knob.style.left = '1px';
               knob.style.right = 'auto';
            }
         }
      }
   }-*/;
   
   private void checkAndShowBYOKContainer(String provider, final com.google.gwt.dom.client.Element inputContainer, final com.google.gwt.dom.client.Element storedKeyContainer)
   {
      server_.hasBYOKApiKey(provider, new ServerRequestCallback<Boolean>()
      {
         @Override
         public void onResponseReceived(Boolean hasKey)
         {
            if (hasKey) {
               storedKeyContainer.getStyle().setProperty("display", "block");
               inputContainer.getStyle().setProperty("display", "none");
            } else {
               storedKeyContainer.getStyle().setProperty("display", "none");
               inputContainer.getStyle().setProperty("display", "block");
            }
         }
         
         @Override
         public void onError(ServerError error)
         {
            Debug.log("Error checking if BYOK key exists: " + error.getMessage());
            inputContainer.getStyle().setProperty("display", "block");
            storedKeyContainer.getStyle().setProperty("display", "none");
         }
      });
   }
   
   private void checkAndShowLocalModelContainer(final com.google.gwt.dom.client.Element inputContainer, final com.google.gwt.dom.client.Element storedKeyContainer)
   {
      // For local model, check if endpoint is configured
      server_.getLocalModelEndpoint(new ServerRequestCallback<String>()
      {
         @Override
         public void onResponseReceived(String endpoint)
         {
            if (endpoint != null && !endpoint.isEmpty()) {
               storedKeyContainer.getStyle().setProperty("display", "block");
               inputContainer.getStyle().setProperty("display", "none");
            } else {
               storedKeyContainer.getStyle().setProperty("display", "none");
               inputContainer.getStyle().setProperty("display", "block");
            }
         }
         
         @Override
         public void onError(ServerError error)
         {
            Debug.log("Error checking local model endpoint: " + error.getMessage());
            inputContainer.getStyle().setProperty("display", "block");
            storedKeyContainer.getStyle().setProperty("display", "none");
         }
      });
   }
   
   private void handleBYOKEnabledChange(String provider, boolean enabled)
   {
      // Save the enabled state to the server
      server_.setBYOKEnabled(provider, enabled, new ServerRequestCallback<Boolean>()
      {
         @Override
         public void onResponseReceived(Boolean success)
         {
            if (success) {
               // Now call the handler to start/stop proxy
               handler_.onBYOKEnabledChange(provider, enabled);
               
               // Reload models to show/hide models based on enabled state
               AiSettingsWidget.this.loadAvailableModels();
            } else {
               globalDisplay_.showErrorMessage("Error", "Failed to save BYOK enabled state");
            }
         }
         
         @Override
         public void onError(ServerError error)
         {
            globalDisplay_.showErrorMessage(
               "Save Failed",
               "Failed to save BYOK enabled state: " + error.getUserMessage()
            );
         }
      });
   }
   
   private VerticalPanel createAutomationToggle(String title, String description, String settingName)
   {
      // Create main container with ruleContainer styling to match rules section
      VerticalPanel mainContainer = new VerticalPanel();
      mainContainer.setWidth("100%");
      mainContainer.addStyleName(styles_.ruleContainer());
      
      // Title and toggle row
      HorizontalPanel titleRow = new HorizontalPanel();
      titleRow.setWidth("100%");
      titleRow.setVerticalAlignment(HorizontalPanel.ALIGN_MIDDLE);
      
      // Title
      HTML titleLabel = new HTML("<b>" + title + "</b>");
      titleLabel.addStyleName(styles_.settingLabel());
      titleRow.add(titleLabel);
      
      // Toggle switch - completely hidden until R values are loaded
      HTML toggle = new HTML();
      toggle.getElement().setInnerHTML(
         "<div style='position: relative; width: 32px; height: 16px; border-radius: 8px; cursor: pointer; transition: background 0.3s; display: none;' data-setting='" + settingName + "' class='ai-toggle-disabled'>" +
         "<div class='" + styles_.toggleShadow() + "' style='position: absolute; top: 1px; left: 1px; width: 14px; height: 14px; background: white; border-radius: 50%; transition: left 0.3s, right 0.3s;'></div>" +
         "</div>"
      );
      
      // Store toggle reference and add event handler
      if ("autoAcceptEdits".equals(settingName)) {
         autoAcceptEditsToggle_ = toggle;
         addNativeAutomationChangeHandler(toggle.getElement(), "autoAcceptEdits");
      } else if ("autoAcceptConsole".equals(settingName)) {
         autoAcceptConsoleToggle_ = toggle;
         addNativeAutomationChangeHandler(toggle.getElement(), "autoAcceptConsole");
      } else if ("autoAcceptTerminal".equals(settingName)) {
         autoAcceptTerminalToggle_ = toggle;
         addNativeAutomationChangeHandler(toggle.getElement(), "autoAcceptTerminal");
      } else if ("autoRunFiles".equals(settingName)) {
         autoRunFilesToggle_ = toggle;
         addNativeAutomationChangeHandler(toggle.getElement(), "autoRunFiles");
      } else if ("autoDeleteFiles".equals(settingName)) {
         autoDeleteFilesToggle_ = toggle;
         addNativeAutomationChangeHandler(toggle.getElement(), "autoDeleteFiles");
      }
      
      titleRow.add(toggle);
      titleRow.setCellHorizontalAlignment(toggle, HorizontalPanel.ALIGN_RIGHT);
      
      mainContainer.add(titleRow);
      
      // Description text
      HTML descriptionHTML = new HTML(description);
      descriptionHTML.addStyleName(styles_.ruleText());
      descriptionHTML.getElement().getStyle().setProperty("marginTop", "4px");
      mainContainer.add(descriptionHTML);
      
      // Allow/deny panels are now added separately at the same level as the toggle containers
      // to avoid table layout constraints
      
      return mainContainer;
   }
   
   private VerticalPanel createAllowDenyListPanel(String settingName)
   {
      VerticalPanel panel = new VerticalPanel();
      panel.setWidth("100%");
      panel.addStyleName(styles_.allowDenyPanel());
      
      // "Allow anything" toggle
      HorizontalPanel allowAnythingRow = new HorizontalPanel();
      allowAnythingRow.setWidth("100%");
      allowAnythingRow.setVerticalAlignment(HorizontalPanel.ALIGN_MIDDLE);
      allowAnythingRow.getElement().getStyle().setProperty("marginBottom", "8px");
      
      // Create descriptive label - hidden until R settings are loaded
      HTML allowAnythingLabel = new HTML("");
      allowAnythingLabel.addStyleName(styles_.settingLabel());
      allowAnythingLabel.getElement().getStyle().setProperty("fontSize", "13px");
      allowAnythingLabel.getElement().getStyle().setProperty("display", "none");
      allowAnythingLabel.getElement().setAttribute("data-allow-anything-label", settingName);
      allowAnythingRow.add(allowAnythingLabel);
      
      HTML allowAnythingToggle = new HTML();
      allowAnythingToggle.getElement().setInnerHTML(
         "<div style='position: relative; width: 28px; height: 14px; border-radius: 7px; cursor: pointer; transition: background 0.3s; display: none;' data-setting='" + settingName + "_allow_anything' class='ai-toggle-disabled'>" +
         "<div class='" + styles_.toggleShadow() + "' style='position: absolute; top: 1px; left: 1px; width: 12px; height: 12px; background: white; border-radius: 50%; transition: left 0.3s, right 0.3s;'></div>" +
         "</div>"
      );
      
      // Add click handler for "Allow anything" toggle
      addNativeAllowAnythingToggleHandler(allowAnythingToggle.getElement(), settingName + "_allow_anything");
      
      allowAnythingRow.add(allowAnythingToggle);
      allowAnythingRow.setCellHorizontalAlignment(allowAnythingToggle, HorizontalPanel.ALIGN_RIGHT);
      
      panel.add(allowAnythingRow);
      
      // Allow/Deny lists container - hidden until R settings are loaded
      VerticalPanel listsContainer = new VerticalPanel();
      listsContainer.setWidth("100%");
      listsContainer.getElement().setAttribute("data-lists-container", settingName);
      listsContainer.getElement().getStyle().setProperty("display", "none");
      
      // Allow list section
      FlowPanel allowListSection = createListSection("Allow list", settingName + "_allow_list");
      listsContainer.add(allowListSection);
      
      // Deny list section  
      FlowPanel denyListSection = createListSection("Deny list", settingName + "_deny_list");
      listsContainer.add(denyListSection);
      
      panel.add(listsContainer);
      
      return panel;
   }
   
   private FlowPanel createListSection(String title, String listType)
   {
      FlowPanel section = new FlowPanel();
      section.setWidth("100%");
      section.getElement().getStyle().setProperty("marginBottom", "12px");
      section.getElement().getStyle().setProperty("display", "block");
      
      // Section title
      Label titleLabel = new Label(title);
      titleLabel.addStyleName(styles_.settingLabel());
      titleLabel.getElement().getStyle().setProperty("fontSize", "12px");
      titleLabel.getElement().getStyle().setProperty("fontWeight", "bold");
      titleLabel.getElement().getStyle().setProperty("marginBottom", "4px");
      titleLabel.getElement().getStyle().setProperty("display", "block");
      section.add(titleLabel);
      
      // Input field
      TextBox inputField = new TextBox();
      inputField.addStyleName(styles_.settingInput());
      inputField.setWidth("100%");
      inputField.getElement().getStyle().setProperty("fontSize", "13px");
      inputField.getElement().getStyle().setProperty("display", "block");
      inputField.getElement().setAttribute("placeholder", "Type and press Enter to add");
      inputField.getElement().setAttribute("data-list-type", listType);
      section.add(inputField);
      
      // Items container
      FlowPanel itemsContainer = new FlowPanel();
      itemsContainer.getElement().getStyle().setProperty("marginTop", "6px");
      itemsContainer.getElement().getStyle().setProperty("display", "block");
      itemsContainer.getElement().setAttribute("data-items-container", listType);
      section.add(itemsContainer);
      
      // Store reference to the FlowPanel for later access
      automationListContainers_.put(listType, itemsContainer);
      
      // Add enter key handler
      addListInputHandler(inputField, itemsContainer, listType);
      
      return section;
   }
   
   private void addListInputHandler(TextBox inputField, FlowPanel itemsContainer, String listType) {
      // Use native event handler pattern like the rest of the system
      addNativeKeyHandler(inputField.getElement(), itemsContainer, listType);
   }
   
   private void addListItem(FlowPanel container, String text, String listType) {
      // Check for duplicates before adding
      if (container != null) {
         com.google.gwt.dom.client.Element automationElement = automationSection_.getElement();
         com.google.gwt.dom.client.Element itemsContainer = findElementByAttribute(automationElement, "data-items-container", listType);
         if (itemsContainer != null) {
            String[] existingItems = collectListItems(itemsContainer);
            for (String existingItem : existingItems) {
               if (existingItem != null && existingItem.equals(text)) {
                  return; // Don't add duplicate
               }
            }
         }
      }
      
      // Add visually and save to R
      addListItemVisualOnly(container, text, listType);
      
      // Save to settings
      saveListToSettings(listType);
   }
   
   private void addListItemVisualOnly(FlowPanel container, String text, String listType) {
      
      // Create item container matching chevron button style
      FlowPanel itemContainer = new FlowPanel();
      itemContainer.addStyleName(styles_.listItemContainer());
      
      // Text label
      Label textLabel = new Label(text);
      textLabel.addStyleName(styles_.listItemText());
      itemContainer.add(textLabel);
      
      // Remove button
      Label removeButton = new Label("×");
      removeButton.addStyleName(styles_.listItemRemove());
      removeButton.getElement().getStyle().setProperty("textAlign", "center");
      removeButton.getElement().getStyle().setProperty("position", "relative");
      removeButton.getElement().getStyle().setProperty("top", "-2px");
      removeButton.getElement().getStyle().setProperty("userSelect", "none");
      
      // Use the same native click handler pattern as other settings buttons
      addNativeClickHandler(removeButton.getElement(), "RemoveListItem-" + listType + "-" + text);
      
      itemContainer.add(removeButton);
      container.add(itemContainer);
      
      // Sort items alphabetically
      sortListItems(container);
   }
   
   private void sortListItems(FlowPanel container) {
      // Collect all items with their text content
      java.util.List<Widget> items = new java.util.ArrayList<Widget>();
      java.util.List<String> texts = new java.util.ArrayList<String>();
      
      for (int i = 0; i < container.getWidgetCount(); i++) {
         Widget widget = container.getWidget(i);
         if (widget instanceof FlowPanel) {
            FlowPanel itemPanel = (FlowPanel) widget;
            // Get the text from the first child (Label)
            if (itemPanel.getWidgetCount() > 0 && itemPanel.getWidget(0) instanceof Label) {
               Label textLabel = (Label) itemPanel.getWidget(0);
               String text = textLabel.getText();
               items.add(widget);
               texts.add(text);
            }
         }
      }
      
      // Sort by text content (case-insensitive)
      java.util.List<Integer> indices = new java.util.ArrayList<Integer>();
      for (int i = 0; i < texts.size(); i++) {
         indices.add(i);
      }
      
      indices.sort(new java.util.Comparator<Integer>() {
         @Override
         public int compare(Integer a, Integer b) {
            return texts.get(a).toLowerCase().compareTo(texts.get(b).toLowerCase());
         }
      });
      
      // Clear and re-add in sorted order
      container.clear();
      for (Integer index : indices) {
         container.add(items.get(index));
      }
   }
   
   private void saveListToSettings(String listType) {
      // Find the items container for this list type
      com.google.gwt.dom.client.Element automationElement = automationSection_.getElement();
      com.google.gwt.dom.client.Element itemsContainer = findElementByAttribute(automationElement, "data-items-container", listType);
      
      if (itemsContainer != null) {
         // Collect all list items using native JavaScript
         String[] items = collectListItems(itemsContainer);
         
         // Convert to JavaScript array and save via server
         com.google.gwt.core.client.JavaScriptObject jsArray = createJavaScriptArray(items);
         server_.setAutomationList(listType, jsArray, new ServerRequestCallback<java.lang.Void>() {
            @Override
            public void onResponseReceived(java.lang.Void result) {
               // Success - no action needed
            }
            
            @Override
            public void onError(ServerError error) {
               // TODO: Handle error appropriately
            }
         });
      }
   }
   
   private void toggleAllowDenyPanel(String settingName, boolean show) {
      // Find the allow/deny panel for this setting using native DOM query
      com.google.gwt.dom.client.Element automationElement = automationSection_.getElement();
      com.google.gwt.dom.client.Element panel = findElementByAttribute(automationElement, "data-automation-panel", settingName);
      if (panel != null) {
         if (show) {
            panel.getStyle().setProperty("display", "block");
         } else {
            panel.getStyle().setProperty("display", "none");
         }
      }
   }
   
   private native com.google.gwt.dom.client.Element findElementByAttribute(com.google.gwt.dom.client.Element parent, String attributeName, String attributeValue) /*-{
      var elements = parent.querySelectorAll("*[" + attributeName + "='" + attributeValue + "']");
      return elements.length > 0 ? elements[0] : null;
   }-*/;
   
   private native com.google.gwt.core.client.JavaScriptObject createJavaScriptArray(String[] items) /*-{
      var array = [];
      for (var i = 0; i < items.length; i++) {
         array.push(items[i]);
      }
      return array;
   }-*/;
   
   private native void removeListItemByText(com.google.gwt.dom.client.Element itemsContainer, String text) /*-{
      var itemElements = itemsContainer.querySelectorAll("div[style*='display: inline-flex']");
      
      // Remove ALL matching items, not just the first one
      for (var i = itemElements.length - 1; i >= 0; i--) { // Iterate backwards to avoid index issues
         var itemElement = itemElements[i];
         var labelElement = itemElement.firstElementChild;
         if (labelElement) {
            var itemText = labelElement.innerText || labelElement.textContent || '';
            if (itemText === text) {
               // Remove this item from the DOM
               itemElement.parentNode.removeChild(itemElement);
            }
         }
      }
   }-*/;
   
   private native String[] collectListItems(com.google.gwt.dom.client.Element itemsContainer) /*-{
      var itemElements = itemsContainer.querySelectorAll("div[style*='display: inline-flex']");
      var items = [];
      
      for (var i = 0; i < itemElements.length; i++) {
         var itemElement = itemElements[i];
         var labelElement = itemElement.firstElementChild;
         if (labelElement) {
            var text = labelElement.innerText || labelElement.textContent || '';
            items[i] = text;
         } else {
            items[i] = '';
         }
      }
      return items;
   }-*/;
   
   private void loadUserProfile() {
      server_.getUserProfile(new ServerRequestCallback<AiUserProfile>() {
         @Override
         public void onResponseReceived(AiUserProfile profile) {
            if (profile != null) {
               userProfile_ = profile;
               updateUserProfileDisplay(profile);
               
               // Also load subscription status for display
               loadSubscriptionStatus();
            } else {
               showError("Failed to load user profile");
            }
         }
         
         @Override
         public void onError(ServerError error) {
            showError("Failed to load user profile: " + error.getMessage());
         }
      });
   }
   
   private void loadSubscriptionStatus()
   {
      server_.getSubscriptionStatus(new ServerRequestCallback<AiSubscriptionStatus>() {
         @Override
         public void onResponseReceived(AiSubscriptionStatus status) {
            subscriptionStatus_ = status;
            updateProfileSection();
         }
         
         @Override
         public void onError(ServerError error) {
            // Non-critical error, just log it
            Debug.logError(error);
         }
      });
   }
   
   private void loadCurrentSettings()
   {
      // Load API key status
      server_.getApiKeyStatus(new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean hasKey) {
            hasApiKey_ = hasKey;
            checkForAnyAuthentication();
         }
         
         @Override
         public void onError(ServerError error) {
            hasApiKey_ = false;
            checkForAnyAuthentication();
         }
      });
      
      // Load current temperature
      server_.getTemperature(new ServerRequestCallback<Double>() {
         @Override
         public void onResponseReceived(Double temperature) {
            currentTemperature_ = temperature != null ? temperature : 0.5;
            updateTemperatureDisplay();
         }
         
         @Override
         public void onError(ServerError error) {
            currentTemperature_ = 0.5; // Default value
            updateTemperatureDisplay();
         }
      });
      
      updateSecurityModeDisplay();
      updateWebSearchDisplay();
      updateAutomationTogglesDisplay();
      
      // Load user rules
      refreshRules();
      
      // Load rules file path
      server_.getRulesFilePath(new ServerRequestCallback<String>() {
         @Override
         public void onResponseReceived(String filePath) {
            if (filePath != null && !filePath.isEmpty()) {
               currentRulesFilePath_ = filePath;
               buildRulesFileDisplay();
            }
         }
         
         @Override
         public void onError(ServerError error) {
            currentRulesFilePath_ = null;
            buildRulesFileDisplay();
         }
      });
   }
   
   private void checkForAnyAuthentication()
   {
      // Always set hasAnyAuth_ to true so all functionality is available
      hasAnyAuth_ = true;
      updateAllSections();
      
      // Still check for BYOK keys to determine auth status for profile display
      if (hasApiKey_) {
         // User has Rao API key
      } else {
         // Check for BYOK keys (for informational purposes only)
         server_.hasBYOKApiKey("anthropic", new ServerRequestCallback<Boolean>() {
            @Override
            public void onResponseReceived(final Boolean hasAnthropicKey) {
               if (!hasAnthropicKey) {
                  server_.hasBYOKApiKey("openai", new ServerRequestCallback<Boolean>() {
                     @Override
                     public void onResponseReceived(Boolean hasOpenAIKey) {
                     }
                     
                     @Override
                     public void onError(ServerError error) {
                        Debug.log("Error checking OpenAI BYOK: " + error.getMessage());
                     }
                  });
               }
            }
            
            @Override
            public void onError(ServerError error) {
               Debug.log("Error checking Anthropic BYOK: " + error.getMessage());
            }
         });
      }
   }
   
   private void loadAvailableModels() {
      server_.getAvailableModels(new ServerRequestCallback<JsArrayString>() {
         @Override
         public void onResponseReceived(JsArrayString models) {
            if (models != null && models.length() > 0) {
               String[] modelArray = new String[models.length()];
               for (int i = 0; i < models.length(); i++) {
                  modelArray[i] = models.get(i);
               }
               
               // Always show all models regardless of authentication status
               updateModelDropdown(modelArray);
            } else {
               // No models returned from server
               if (modelSelect_ != null) {
                  modelSelect_.clear();
                  modelSelect_.addItem("Please sign in or bring your own key.", "");
               }
            }
         }
         
         @Override
         public void onError(ServerError error) {
            Debug.log("Error loading models: " + (error != null ? error.getMessage() : "null error"));
            if (modelSelect_ != null) {
               modelSelect_.clear();
               modelSelect_.addItem("Please sign in or bring your own key.", "");
            }
         }
      });
   }
   
   private void selectCurrentModel()
   {
      if (currentModel_ != null && modelSelect_ != null) {
         for (int i = 0; i < modelSelect_.getItemCount(); i++) {
            String itemValue = modelSelect_.getValue(i);
            if (itemValue.equals(currentModel_)) {
               modelSelect_.setSelectedIndex(i);
               break;
            }
         }
      }
   }
   
   private void updateAllSections()
   {
      buildProfileSection();
      buildBYOKSection();
      buildRulesSection();
      buildSecuritySection();
      buildAutomationSection();
      buildModelSection();
   }
   
   private void updateProfileSection()
   {
      if (userNameLabel_ != null && userProfile_ != null) {
         // Use the name from the profile (already formatted as full name)
         String name = userProfile_.getName();
         userNameLabel_.setText(name != null ? name : "");
      }
      
      if (subscriptionStatusLabel_ != null && subscriptionStatus_ != null) {
         String status = subscriptionStatus_.getSubscriptionStatus();
         subscriptionStatusLabel_.setHTML("<b>Subscription:</b> " + formatSubscriptionStatus(status));
         updateSubscriptionStatusStyle(status);
         
         // Add subscription details if they haven't been added yet
         addSubscriptionDetailsIfMissing();
      }
   }
   
   private void addSubscriptionDetailsIfMissing() {
      if (hasApiKey_ && subscriptionStatus_ != null && !subscriptionDetailsAdded_) {
         // Find the profile info container (parent of the parent of subscriptionStatusLabel_)
         // subscriptionStatusLabel_ -> HorizontalPanel -> VerticalPanel (profileInfo)
         Widget parent = subscriptionStatusLabel_.getParent(); // HorizontalPanel
         
         if (parent != null) {
            Widget grandParent = parent.getParent(); // VerticalPanel (profileInfo)
            
            if (grandParent instanceof VerticalPanel) {
               VerticalPanel profileInfo = (VerticalPanel) grandParent;
               
               // Find the position after the subscription status panel (parent)
               int insertIndex = profileInfo.getWidgetIndex(parent) + 1;
               
               // Create a temporary container for subscription details
               VerticalPanel tempContainer = new VerticalPanel();
               buildSubscriptionDetails(tempContainer);
               
               // Insert each widget from the temp container into the profile info
               while (tempContainer.getWidgetCount() > 0) {
                  Widget widget = tempContainer.getWidget(0);
                  tempContainer.remove(widget);
                  profileInfo.insert(widget, insertIndex++);
               }
               
               // Mark as added to prevent duplicates
               subscriptionDetailsAdded_ = true;
            }
         }
      }
   }
   
   
   private String formatSubscriptionStatus(String status)
   {
      if (status == null) return "Unknown";
      
      switch (status.toLowerCase()) {
         case "trial":
            return "Free Tier";
         case "org_promo":
            return "Organization Promotion";
         case "active":
            return "Active";
         case "past_due":
            return "Past Due";
         case "payment_action_required":
            return "Payment Required";
         case "cancelled":
            return "Cancelled";
         case "expired":
            return "Expired";
         default:
            return status;
      }
   }
   
   private void updateSubscriptionStatusStyle(String status)
   {
      if (subscriptionStatusLabel_ == null) return;
      
      // Remove all status classes
      subscriptionStatusLabel_.removeStyleName(styles_.statusActive());
      subscriptionStatusLabel_.removeStyleName(styles_.statusTrial());
      subscriptionStatusLabel_.removeStyleName(styles_.statusOrgPromo());
      subscriptionStatusLabel_.removeStyleName(styles_.statusPastDue());
      subscriptionStatusLabel_.removeStyleName(styles_.statusPaymentActionRequired());
      subscriptionStatusLabel_.removeStyleName(styles_.statusCancelled());
      subscriptionStatusLabel_.removeStyleName(styles_.statusExpired());
      
      // Add appropriate status class
      if (status != null) {
         switch (status.toLowerCase()) {
            case "trial":
               subscriptionStatusLabel_.addStyleName(styles_.statusTrial());
               break;
            case "org_promo":
               subscriptionStatusLabel_.addStyleName(styles_.statusOrgPromo());
               break;
            case "active":
               subscriptionStatusLabel_.addStyleName(styles_.statusActive());
               break;
            case "past_due":
               subscriptionStatusLabel_.addStyleName(styles_.statusPastDue());
               break;
            case "payment_action_required":
               subscriptionStatusLabel_.addStyleName(styles_.statusPaymentActionRequired());
               break;
            case "cancelled":
               subscriptionStatusLabel_.addStyleName(styles_.statusCancelled());
               break;
            case "expired":
               subscriptionStatusLabel_.addStyleName(styles_.statusExpired());
               break;
         }
      }
   }
   
   private void buildSubscriptionDetails(VerticalPanel profileInfo)
   {
      if (subscriptionStatus_ == null) return;
      
      // Usage information from subscription status (fresh data)
      int monthlyLimit = subscriptionStatus_.getQueriesLimit();
      int monthlyRemaining = subscriptionStatus_.getQueriesRemaining();
      int monthlyUsed = monthlyLimit - monthlyRemaining;
            
      // Safety checks to prevent negative values or division by zero
      if (monthlyLimit <= 0) {
         return;
      }
      
      if (monthlyUsed < 0) {
         monthlyUsed = 0;
      }
      
      // Usage panel
      VerticalPanel usagePanel = new VerticalPanel();
      usagePanel.setWidth("100%");
      usagePanel.addStyleName(styles_.settingRow());
      
      // Usage label
      HTML usageLabel = new HTML("<b>Monthly usage:</b> " + monthlyUsed + " / " + monthlyLimit + " queries");
      usageLabel.addStyleName(styles_.settingLabel());
      usagePanel.add(usageLabel);
      
      // Usage bar
      HorizontalPanel usageBarContainer = new HorizontalPanel();
      usageBarContainer.addStyleName(styles_.usageBarContainer());
      usageBarContainer.getElement().getStyle().setProperty("overflow", "hidden");
      
      // Usage bar fill
      HTML usageBarFill = new HTML();
      double usagePercent = Math.min(100.0, Math.max(0.0, (double) monthlyUsed / monthlyLimit * 100.0));
      usageBarFill.setWidth(usagePercent + "%");
      usageBarFill.addStyleName(styles_.usageBarFill());
      
      usageBarContainer.add(usageBarFill);
      usagePanel.add(usageBarContainer);
      profileInfo.add(usagePanel);
      
      // Current period information
      String periodStart = subscriptionStatus_.getCurrentPeriodStart();
      String periodEnd = subscriptionStatus_.getCurrentPeriodEnd();
      if (periodStart != null && periodEnd != null) {
         HTML periodLabel = new HTML("<b>Current period:</b> " + formatDate(periodStart) + " - " + formatDate(periodEnd));
         periodLabel.addStyleName(styles_.settingLabel());
         profileInfo.add(periodLabel);
      }
      
      // Usage-based billing status
      if (subscriptionStatus_.getUsageBasedBillingEnabled()) {
         int overageCount = subscriptionStatus_.getOverageCount();
         if (overageCount > 0) {
            double pendingCents = subscriptionStatus_.getPendingOverageCents();
            double dollarAmount = pendingCents / 100.0;
            // Format to 2 decimal places manually since String.format isn't available in GWT
            String formattedAmount = String.valueOf(Math.round(dollarAmount * 100.0) / 100.0);
            if (formattedAmount.indexOf('.') == -1) {
               formattedAmount += ".00";
            } else if (formattedAmount.length() - formattedAmount.indexOf('.') == 2) {
               formattedAmount += "0";
            }
            Label overageLabel = new Label("Overage: " + overageCount + " queries ($" + formattedAmount + " pending)");
            overageLabel.addStyleName(styles_.settingLabel());
            profileInfo.add(overageLabel);
         }
      }
   }
   
   private String formatDate(String isoDate)
   {
      if (isoDate == null) return "";
      try {
         // Parse ISO date format (YYYY-MM-DD) to readable format
         String[] parts = isoDate.substring(0, 10).split("-");
         if (parts.length == 3) {
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            
            String[] monthNames = {"January", "February", "March", "April", "May", "June",
                                   "July", "August", "September", "October", "November", "December"};
            
            if (month >= 1 && month <= 12) {
               return monthNames[month - 1] + " " + day + ", " + year;
            }
         }
         return isoDate.substring(0, 10);
      } catch (Exception e) {
         return isoDate;
      }
   }
   
   private void showProfileError(String message)
   {
      if (profileErrorLabel_ != null) {
         profileErrorLabel_.setText(message);
         profileErrorLabel_.setVisible(true);
         
         // Auto-hide after 5 seconds
         Timer timer = new Timer() {
            @Override
            public void run() {
               profileErrorLabel_.setVisible(false);
            }
         };
         timer.schedule(5000);
      }
   }
   
   
   
   public void onApiKeySaved()
   {
      hasApiKey_ = true;
      hasAnyAuth_ = true;
      updateAllSections();
      loadUserProfile();
      loadSubscriptionStatus();
      loadAvailableModels();
   }
   
   public void onAuthenticationCompleted()
   {
      hasApiKey_ = true;
      hasAnyAuth_ = true;
      updateAllSections();
      loadUserProfile();
      loadSubscriptionStatus();
      loadAvailableModels();
   }
   
   public void onApiKeyDeleted()
   {
      hasApiKey_ = false;
      userProfile_ = null;
      subscriptionStatus_ = null;
      checkForAnyAuthentication();
      loadAvailableModels();
   }
   
   public void onModelChanged(String model)
   {
      currentModel_ = model;
      selectCurrentModel();
   }
   
   public void onTemperatureChanged(double temperature)
   {
      currentTemperature_ = temperature;
      updateTemperatureDisplay();
   }
   
   /**
    * Refresh all settings when the settings page is shown
    * This ensures we always query fresh values and never rely on cached data
    */
   public void refreshAllSettings() {
      // Refresh subscription status
      refreshSubscriptionStatus();
      
      // Always query fresh security mode, web search, and automation values from R functions
      updateSecurityModeDisplay();
      updateWebSearchDisplay();
      updateAutomationTogglesDisplay();
      
      // Refresh other settings that might have changed
      loadCurrentSettings();
   }
   
   /**
    * Refreshes the subscription status from the server
    * Called when navigating to the Settings page to ensure up-to-date information
    */
   public void refreshSubscriptionStatus()
   {
      // Also refresh API key status since subscription details depend on it
      server_.getApiKeyStatus(new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean hasKey) {
            hasApiKey_ = hasKey;
            
            // Load subscription status and rebuild profile section
            server_.getSubscriptionStatus(new ServerRequestCallback<AiSubscriptionStatus>() {
               @Override
               public void onResponseReceived(AiSubscriptionStatus status) {
                  if (status != null) {
                     subscriptionStatus_ = status;                     
                     buildProfileSection();
                     updateProfileSection();
                  } else {
                     subscriptionStatus_ = null;
                  }
               }
               
               @Override
               public void onError(ServerError error) {
                  Debug.log("Error loading subscription status for refresh: " + error.getMessage());
                  Debug.logError(error);
               }
            });
         }
         
         @Override
         public void onError(ServerError error) {
            Debug.log("Error refreshing API key status: " + error.getMessage());
            // Still try to load subscription status
            loadSubscriptionStatus();
         }
      });
   }
   
   private void updateTemperatureDisplay()
   {
      if (temperatureSlider_ != null) {
         setSliderValue(currentTemperature_);
      }
      if (temperatureInput_ != null) {
         temperatureInput_.setValue(String.valueOf(currentTemperature_));
      }
   }

   private void updateUserProfileDisplay(AiUserProfile profile) {
      userProfile_ = profile;
      updateProfileSection();
   }

   private void showError(String message) {
      showProfileError(message);
   }

   private void updateModelDropdown(String[] models) {
      modelSelect_.clear();
      for (String model : models) {
         String displayName = model;
         modelSelect_.addItem(displayName, model);
      }
      
      // Set current selection
      server_.getSelectedModel(new ServerRequestCallback<String>() {
         @Override
         public void onResponseReceived(String currentModel) {
            currentModel_ = currentModel;
            
            // Check if current model is still available
            boolean modelStillAvailable = false;
            for (int i = 0; i < modelSelect_.getItemCount(); i++) {
               if (modelSelect_.getValue(i).equals(currentModel)) {
                  modelStillAvailable = true;
                  break;
               }
            }
            
            // If current model is not available, auto-select first available model
            if (!modelStillAvailable && modelSelect_.getItemCount() > 0) {
               String firstModel = modelSelect_.getValue(0);
               currentModel_ = firstModel;
               // Determine provider from model name
               String provider = getProviderFromModel(firstModel);
               // Save the new selection
               server_.setModel(provider, firstModel, new ServerRequestCallback<Void>() {
                  @Override
                  public void onResponseReceived(Void result) {
                     handler_.onModelChange(firstModel);
                  }
                  
                  @Override
                  public void onError(ServerError error) {
                     Debug.logError(error);
                  }
               });
            }
            
            selectCurrentModel();
         }
         
         @Override
         public void onError(ServerError error) {
            Debug.log("Error loading current model: " + error.getMessage());
            Debug.logError(error);
         }
      });
   }
   
   // Add native DOM event handler using JSNI (same pattern as console/terminal widgets)
   private native void addNativeChangeHandler(com.google.gwt.dom.client.Element element) /*-{
      var self = this;
      
      element.addEventListener('change', function(event) {
         self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleModelChange()();
         // Don't prevent default for change events - this allows normal form behavior
      }, false);
   }-*/;
   
   // Add native DOM event handler for temperature slider
   private native void addNativeSliderChangeHandler(com.google.gwt.dom.client.Element element) /*-{
      var self = this;
      
      element.addEventListener('input', function(event) {
         self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleSliderChange()();
         // Don't prevent default for input events - this allows normal form behavior
      }, false);
   }-*/;
   
   // Add native DOM event handler for temperature input
   private native void addNativeInputChangeHandler(com.google.gwt.dom.client.Element element) /*-{
      var self = this;
      
      // Handle blur event (when field loses focus)
      element.addEventListener('blur', function(event) {
         self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleInputChange()();
         // Don't prevent default for blur events - this allows normal form behavior
      }, false);
      
      // Handle Enter key
      element.addEventListener('keydown', function(event) {
         if (event.key === 'Enter' || event.keyCode === 13) {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleInputChange()();
            event.preventDefault(); // Only prevent default for Enter key
            event.stopPropagation();
         }
      }, false);
      }-*/;
   
      // Add native DOM event handler for security mode toggle  
   private native void addNativeSecurityModeChangeHandler(com.google.gwt.dom.client.Element element) /*-{
      var self = this;
      
      element.addEventListener('click', function(event) {
         var toggleDiv = element.querySelector('div[data-setting]');
         if (toggleDiv) {
            var currentValue = toggleDiv.getAttribute('data-value');
            var newValue = currentValue === 'secure' ? 'improve' : 'secure';
            toggleDiv.setAttribute('data-value', newValue);
            
            // Update visual state
            var slider = toggleDiv.querySelector('div');
            var isSecure = newValue === 'secure';
            if (isSecure) {
               toggleDiv.classList.add('ai-toggle-enabled');
               toggleDiv.classList.remove('ai-toggle-disabled');
            } else {
               toggleDiv.classList.remove('ai-toggle-enabled');
               toggleDiv.classList.add('ai-toggle-disabled');
            }
            
            // Use right positioning for secure (green), left for improve (grey)
            if (isSecure) {
               slider.style.left = '';
               slider.style.right = '1px';
            } else {
               slider.style.right = '';
               slider.style.left = '1px';
            }
            
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleSecurityModeChange()();
            
            // Only prevent default when actually handling the toggle
            event.preventDefault();
            event.stopPropagation();
         }
         // If not a toggle click, let it bubble normally (allows text selection)
      }, false);
   }-*/;
   
   // Add native DOM event handler for web search toggle
   private native void addNativeWebSearchChangeHandler(com.google.gwt.dom.client.Element element) /*-{
      var self = this;
      
      element.addEventListener('click', function(event) {
         var toggleDiv = element.querySelector('div[data-setting]');
         if (toggleDiv) {
            var currentValue = toggleDiv.getAttribute('data-value');
            var newValue = currentValue === 'false' ? 'true' : 'false';
            toggleDiv.setAttribute('data-value', newValue);
            
            // Update visual state
            var slider = toggleDiv.querySelector('div');
            var isEnabled = newValue === 'true';
            if (isEnabled) {
               toggleDiv.classList.add('ai-toggle-enabled');
               toggleDiv.classList.remove('ai-toggle-disabled');
            } else {
               toggleDiv.classList.remove('ai-toggle-enabled');
               toggleDiv.classList.add('ai-toggle-disabled');
            }
            slider.style.left = isEnabled ? '17px' : '1px';
            
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleWebSearchChange()();
            
            // Only prevent default when actually handling the toggle
            event.preventDefault();
            event.stopPropagation();
         }
         // If not a toggle click, let it bubble normally (allows text selection)
      }, false);
   }-*/;
   
   // Add native DOM event handler for automation toggles
   private native void addNativeAutomationChangeHandler(com.google.gwt.dom.client.Element element, String settingName) /*-{
      var self = this;
      
      element.addEventListener('click', function(event) {
         var toggleDiv = element.querySelector('div[data-setting]');
         if (toggleDiv) {
            // Just call the appropriate handler - let Java handle all state management
            if (settingName === 'autoAcceptEdits') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleAutoAcceptEditsChange()();
            } else if (settingName === 'autoAcceptConsole') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleAutoAcceptConsoleChange()();
            } else if (settingName === 'autoAcceptTerminal') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleAutoAcceptTerminalChange()();
            } else if (settingName === 'autoRunFiles') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleAutoRunFilesChange()();
            } else if (settingName === 'autoDeleteFiles') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleAutoDeleteFilesChange()();
            }
            
            event.preventDefault();
            event.stopPropagation();
         }
      }, false);
   }-*/;
   
   // Add native DOM event handler for "Allow anything" toggles
   private native void addNativeAllowAnythingToggleHandler(com.google.gwt.dom.client.Element element, String settingName) /*-{
      var self = this;
      
      element.addEventListener('click', function(event) {
         var toggleDiv = element.querySelector('div[data-setting]');
         if (toggleDiv) {
            // Just call the appropriate handler - let Java handle all state management
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleAllowAnythingToggleChange(Ljava/lang/String;)(settingName);
            
            event.preventDefault();
            event.stopPropagation();
         }
      }, false);
   }-*/;
   
   // Add native DOM event handler for text input Enter key
   private native void addNativeKeyHandler(com.google.gwt.dom.client.Element inputElement, Object itemsContainer, String listType) /*-{
      var self = this;
      
      inputElement.addEventListener('keydown', function(event) {
         if (event.key === 'Enter' || event.keyCode === 13) {
            var value = inputElement.value.trim();
            if (value !== '') {
               // Call Java method to add the item
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleAddListItem(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)(itemsContainer, value, listType);
               
               // Clear the input
               inputElement.value = '';
            }
            event.preventDefault();
            event.stopPropagation();
         }
      }, false);
   }-*/;
   
   // Add native DOM event handler for BYOK API key input Enter key
   private native void addNativeBYOKKeyHandler(com.google.gwt.dom.client.Element inputElement, String provider) /*-{
      var self = this;
      
      inputElement.addEventListener('keydown', function(event) {
         if (event.key === 'Enter' || event.keyCode === 13) {
            // Call Java method to save the API key
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleBYOKSaveKey(Ljava/lang/String;)(provider);
            event.preventDefault();
            event.stopPropagation();
         }
      }, false);
   }-*/;
   
   // Rule management handlers
   private void handleAddRule() {
      // Show the new rule input panel
      if (newRulePanel_ != null) {
         newRulePanel_.setVisible(true);
         newRuleInput_.setValue("");
         newRuleInput_.setFocus(true);
      }
   }
   
   private void handleSaveNewRule() {
      if (newRuleInput_ != null) {
         String rule = newRuleInput_.getValue().trim();
         if (!rule.isEmpty() && handler_ != null) {
            handler_.onAddRule(rule);
         }
      }
   }
   
   private void handleCancelNewRule() {
      // Hide the new rule panel and show the add rule button
      if (newRulePanel_ != null) {
         newRulePanel_.setVisible(false);
      }
      if (addRuleButton_ != null) {
         addRuleButton_.setVisible(true);
      }
   }
   
   private void handleOptionsMenu(int ruleIndex) {
      VerticalPanel menu = ruleMenus_.get(ruleIndex);
      if (menu != null) {
         toggleOptionsMenu(menu);
      }
   }
   
   private void handleEditRule(int ruleIndex) {
      if (ruleIndex < currentRules_.size()) {
         String rule = currentRules_.get(ruleIndex);
         startEditingRule(ruleIndex, rule);
         
         // Hide the menu
         VerticalPanel menu = ruleMenus_.get(ruleIndex);
         if (menu != null) {
            menu.setVisible(false);
         }
      }
   }
   
   private void handleDeleteRule(int ruleIndex) {
      if (handler_ != null) {
         handler_.onDeleteRule(ruleIndex + 1); // Convert to 1-based index for R
      }
      
      // Hide the menu
      VerticalPanel menu = ruleMenus_.get(ruleIndex);
      if (menu != null) {
         menu.setVisible(false);
      }
   }
   
   private void handleRemoveListItem(String listType, String text) {
      FlowPanel container = automationListContainers_.get(listType);
      
      // Find the items container for this list type
      com.google.gwt.dom.client.Element automationElement = automationSection_.getElement();
      com.google.gwt.dom.client.Element itemsContainer = findElementByAttribute(automationElement, "data-items-container", listType);
      
      if (itemsContainer != null) {
         // Find and remove the specific item by text content
         removeListItemByText(itemsContainer, text);
         
         // CRITICAL FIX: Rebuild FlowPanel from DOM state after removal
         if (container != null) {
            // Get current DOM state (after removal)
            String[] remainingItems = collectListItems(itemsContainer);
            
            // Deduplicate remaining items
            java.util.Set<String> uniqueItems = new java.util.LinkedHashSet<String>();
            for (String item : remainingItems) {
               if (item != null && !item.trim().isEmpty()) {
                  uniqueItems.add(item);
               }
            }
            
            // Clear FlowPanel and rebuild from deduplicated items
            container.clear();
            for (String item : uniqueItems) {
               addListItemVisualOnly(container, item, listType);
            }
         }
         
         // Save updated list to settings
         saveListToSettings(listType);
      }
   }
   
   private void handleEditSave(int ruleIndex) {
      try {
         // Find the TextArea directly in the DOM instead of relying on the map
         String editedText = findEditTextAreaValue(ruleIndex);
         if (editedText != null && handler_ != null) {
            handler_.onEditRule(ruleIndex + 1, editedText); // Convert to 1-based index for R
         }
      } catch (Exception e) {
         Debug.logException(e);
      }
   }
   
   private String findEditTextAreaValue(int ruleIndex) {
      // Find the TextArea in the rule container directly
      if (ruleIndex < rulesContainer_.getWidgetCount()) {
         VerticalPanel ruleContainer = (VerticalPanel) rulesContainer_.getWidget(ruleIndex);
         String result = findTextAreaInContainer(ruleContainer);
         return result;
      }
      return null;
   }
   
   private String findTextAreaInContainer(VerticalPanel container) {
      for (int i = 0; i < container.getWidgetCount(); i++) {
         Widget widget = container.getWidget(i);
         
         if (widget instanceof VerticalPanel) {
            VerticalPanel subContainer = (VerticalPanel) widget;
            for (int j = 0; j < subContainer.getWidgetCount(); j++) {
               Widget subWidget = subContainer.getWidget(j);
               if (subWidget instanceof TextArea) {
                  TextArea textArea = (TextArea) subWidget;
                  String value = textArea.getValue();
                  return value;
               }
            }
         } else if (widget instanceof TextArea) {
            TextArea textArea = (TextArea) widget;
            String value = textArea.getValue();
            return value;
         }
      }
      return null;
   }
   
   private void handleEditCancel(int ruleIndex) {
      // Rebuild the rules list to cancel editing
      buildRulesList();
   }
   
   private void handleBYOKSaveKey(String provider) {
      TextBox apiKeyInput = byokApiKeyInputs_.get(provider);
      String displayName = byokDisplayNames_.get(provider);
      
      if (apiKeyInput == null) {
         globalDisplay_.showErrorMessage("Error", "Could not find API key input for " + provider);
         return;
      }
      
      String key = apiKeyInput.getValue();
      
      if (key != null && !key.isEmpty()) {
         server_.setBYOKApiKey(provider, key, new ServerRequestCallback<Boolean>()
         {
            @Override
            public void onResponseReceived(Boolean success)
            {
               if (success) {
                  apiKeyInput.setValue("");
                  globalDisplay_.showMessage(
                     GlobalDisplay.MSG_INFO,
                     "API Key Saved",
                     "Your " + displayName + " API key has been securely stored."
                  );
                  
                  // Switch UI to show stored key display
                  switchBYOKContainers(provider, false);
                  
               handler_.onBYOKApiKeySet(provider, key);
               
               // Reload models to show newly available models
               AiSettingsWidget.this.loadAvailableModels();
               
               // Update authentication status
               checkForAnyAuthentication();
               }
            }
            
            @Override
            public void onError(ServerError error)
            {
               globalDisplay_.showErrorMessage(
                  "Save Failed",
                  error.getUserMessage()
               );
            }
         });
      } else {
         globalDisplay_.showErrorMessage("Error", "Please enter a valid API key.");
      }
   }
   
   private void handleBYOKDeleteKey(String provider) {
      String displayName = byokDisplayNames_.get(provider);
      
      server_.clearBYOKApiKey(provider, new ServerRequestCallback<java.lang.Void>()
      {
         @Override
         public void onResponseReceived(java.lang.Void result)
         {
            // For SageMaker, also clear endpoint and region
            if (provider.equals("sagemaker")) {
               server_.setSageMakerEndpoint("", new ServerRequestCallback<Boolean>() {
                  @Override
                  public void onResponseReceived(Boolean success) {
                     Debug.log("SageMaker endpoint cleared");
                  }
                  
                  @Override
                  public void onError(ServerError error) {
                     Debug.log("Error clearing SageMaker endpoint: " + error.getMessage());
                  }
               });
               
               server_.setSageMakerRegion("us-east-1", new ServerRequestCallback<Boolean>() {
                  @Override
                  public void onResponseReceived(Boolean success) {
                     Debug.log("SageMaker region reset to default");
                  }
                  
                  @Override
                  public void onError(ServerError error) {
                     Debug.log("Error resetting SageMaker region: " + error.getMessage());
                  }
               });
               
               server_.setSageMakerModel("Qwen/Qwen3-Coder-30B-A3B-Instruct", new ServerRequestCallback<Boolean>() {
                  @Override
                  public void onResponseReceived(Boolean success) {
                     Debug.log("SageMaker model reset to default");
                  }
                  
                  @Override
                  public void onError(ServerError error) {
                     Debug.log("Error resetting SageMaker model: " + error.getMessage());
                  }
               });
               
               // Switch UI to show input container for SageMaker
               sagemakerInputContainer_.setVisible(true);
               sagemakerStoredContainer_.setVisible(false);
               
               // Clear input fields
               sagemakerAccessKeyInput_.setValue("");
               sagemakerSecretKeyInput_.setValue("");
               sagemakerEndpointInput_.setValue("");
               sagemakerRegionInput_.setValue("us-east-1");
            } else {
               // Switch UI to show input container for other providers
               switchBYOKContainers(provider, true);
            }
            
            handler_.onBYOKApiKeyDeleted(provider);
            
            // Reload models to remove models that are no longer available
            AiSettingsWidget.this.loadAvailableModels();
            
            // Update authentication status
            checkForAnyAuthentication();
         }
         
         @Override
         public void onError(ServerError error)
         {
            Debug.log("Delete key failed for provider: " + provider + " error: " + error.getMessage());
            globalDisplay_.showErrorMessage(
               "Delete Failed",
               error.getUserMessage()
            );
         }
      });
   }
   
   private void switchBYOKContainers(String provider, boolean showInput) {
      FlowPanel inputContainer = byokInputContainers_.get(provider);
      HorizontalPanel storedContainer = byokStoredContainers_.get(provider);
      
      if (inputContainer != null && storedContainer != null) {
         if (showInput) {
            inputContainer.setVisible(true);
            storedContainer.setVisible(false);
         } else {
            inputContainer.setVisible(false);
            storedContainer.setVisible(true);
         }
      }
   }
   
   private void handleSaveSageMakerConfig() {
      String accessKey = sagemakerAccessKeyInput_.getValue();
      String secretKey = sagemakerSecretKeyInput_.getValue();
      String endpoint = sagemakerEndpointInput_.getValue();
      String region = sagemakerRegionInput_.getValue();
      String model = sagemakerModelInput_.getValue();
      
      // Validate inputs
      if (accessKey == null || accessKey.trim().isEmpty()) {
         globalDisplay_.showErrorMessage("Error", "Please enter AWS Access Key ID.");
         return;
      }
      
      if (secretKey == null || secretKey.trim().isEmpty()) {
         globalDisplay_.showErrorMessage("Error", "Please enter AWS Secret Access Key.");
         return;
      }
      
      if (endpoint == null || endpoint.trim().isEmpty()) {
         globalDisplay_.showErrorMessage("Error", "Please enter SageMaker Endpoint Name.");
         return;
      }
      
      if (region == null || region.trim().isEmpty()) {
         globalDisplay_.showErrorMessage("Error", "Please enter AWS Region.");
         return;
      }
      
      if (model == null || model.trim().isEmpty()) {
         globalDisplay_.showErrorMessage("Error", "Please enter Model Name.");
         return;
      }
      
      // Create AWS credentials JSON
      String awsCredentialsJson = "{\"accessKeyId\":\"" + accessKey + "\",\"secretAccessKey\":\"" + secretKey + "\"}";
      
      // Save AWS credentials first
      server_.setBYOKApiKey("sagemaker", awsCredentialsJson, new ServerRequestCallback<Boolean>()
      {
         @Override
         public void onResponseReceived(Boolean success)
         {
            if (success) {
               // Then save endpoint
               server_.setSageMakerEndpoint(endpoint, new ServerRequestCallback<Boolean>()
               {
                  @Override
                  public void onResponseReceived(Boolean endpointSuccess)
                  {
                     if (endpointSuccess) {
                        // Save region
                        server_.setSageMakerRegion(region, new ServerRequestCallback<Boolean>()
                        {
                           @Override
                           public void onResponseReceived(Boolean regionSuccess)
                           {
                              if (regionSuccess) {
                                 // Finally save model
                                 server_.setSageMakerModel(model, new ServerRequestCallback<Boolean>()
                                 {
                                    @Override
                                    public void onResponseReceived(Boolean modelSuccess)
                                    {
                                       if (modelSuccess) {
                                          // Clear input fields
                                          sagemakerAccessKeyInput_.setValue("");
                                          sagemakerSecretKeyInput_.setValue("");
                                          sagemakerEndpointInput_.setValue("");
                                          sagemakerRegionInput_.setValue("us-east-1");
                                          sagemakerModelInput_.setValue("Qwen/Qwen3-Coder-30B-A3B-Instruct");
                                          
                                          globalDisplay_.showMessage(
                                             GlobalDisplay.MSG_INFO,
                                             "Configuration Saved",
                                             "Your SageMaker configuration has been securely stored."
                                          );
                                          
                                          // Switch UI to show stored container
                                          sagemakerInputContainer_.setVisible(false);
                                          sagemakerStoredContainer_.setVisible(true);
                                          
                                          handler_.onBYOKApiKeySet("sagemaker", awsCredentialsJson);
                                          
                                          // Reload models to show newly available models
                                          AiSettingsWidget.this.loadAvailableModels();
                                          
                                          // Update authentication status
                                          checkForAnyAuthentication();
                                       } else {
                                          globalDisplay_.showErrorMessage("Error", "Failed to save SageMaker model.");
                                       }
                                    }
                                    
                                    @Override
                                    public void onError(ServerError error)
                                    {
                                       Debug.log("Error saving SageMaker model: " + error.getMessage());
                                       globalDisplay_.showErrorMessage("Error", "Failed to save SageMaker model: " + error.getMessage());
                                    }
                                 });
                              } else {
                                 globalDisplay_.showErrorMessage("Error", "Failed to save SageMaker region.");
                              }
                           }
                           
                           @Override
                           public void onError(ServerError error)
                           {
                              globalDisplay_.showErrorMessage(
                                 "Save Failed",
                                 "Failed to save SageMaker region: " + error.getUserMessage()
                              );
                           }
                        });
                     } else {
                        globalDisplay_.showErrorMessage("Error", "Failed to save SageMaker endpoint.");
                     }
                  }
                  
                  @Override
                  public void onError(ServerError error)
                  {
                     globalDisplay_.showErrorMessage(
                        "Save Failed",
                        "Failed to save SageMaker endpoint: " + error.getUserMessage()
                     );
                  }
               });
            } else {
               globalDisplay_.showErrorMessage("Error", "Failed to save AWS credentials.");
            }
         }
         
         @Override
         public void onError(ServerError error)
         {
            globalDisplay_.showErrorMessage(
               "Save Failed",
               "Failed to save AWS credentials: " + error.getUserMessage()
            );
         }
      });
   }
   
   private void handleSaveLocalModelConfig() {
      String endpoint = localModelEndpointInput_.getValue();
      String modelName = localModelNameInput_.getValue();
      String apiKey = localModelApiKeyInput_.getValue();
      
      // Validate inputs
      if (endpoint == null || endpoint.trim().isEmpty()) {
         globalDisplay_.showErrorMessage("Error", "Please enter endpoint URL.");
         return;
      }
      
      if (modelName == null || modelName.trim().isEmpty()) {
         globalDisplay_.showErrorMessage("Error", "Please enter model name.");
         return;
      }
      
      // First, enable BYOK for local model
      server_.setBYOKEnabled("localmodel", true, new ServerRequestCallback<Boolean>()
      {
         @Override
         public void onResponseReceived(Boolean enableSuccess)
         {
            if (enableSuccess) {
               // Then save endpoint
               saveLocalModelEndpointAndName(endpoint, modelName, apiKey);
            } else {
               globalDisplay_.showErrorMessage("Error", "Failed to enable local model BYOK.");
            }
         }
         
         @Override
         public void onError(ServerError error)
         {
            globalDisplay_.showErrorMessage("Save Failed", "Failed to enable local model BYOK: " + error.getUserMessage());
         }
      });
   }
   
   private void saveLocalModelEndpointAndName(String endpoint, String modelName, String apiKey) {
      server_.setLocalModelEndpoint(endpoint, new ServerRequestCallback<Boolean>()
      {
         @Override
         public void onResponseReceived(Boolean success)
         {
            if (success) {
               // Then save model name
               server_.setLocalModelName(modelName, new ServerRequestCallback<Boolean>()
               {
                  @Override
                  public void onResponseReceived(Boolean modelSuccess)
                  {
                     if (modelSuccess) {
                        // Save API key if provided
                        if (apiKey != null && !apiKey.trim().isEmpty()) {
                           server_.setBYOKApiKey("localmodel", apiKey, new ServerRequestCallback<Boolean>()
                           {
                              @Override
                              public void onResponseReceived(Boolean keySuccess)
                              {
                                 if (keySuccess) {
                                    completeLocalModelSave();
                                 } else {
                                    globalDisplay_.showErrorMessage("Error", "Failed to save API key.");
                                 }
                              }
                              
                              @Override
                              public void onError(ServerError error)
                              {
                                 globalDisplay_.showErrorMessage("Save Failed", "Failed to save API key: " + error.getUserMessage());
                              }
                           });
                        } else {
                           completeLocalModelSave();
                        }
                     } else {
                        globalDisplay_.showErrorMessage("Error", "Failed to save model name.");
                     }
                  }
                  
                  @Override
                  public void onError(ServerError error)
                  {
                     globalDisplay_.showErrorMessage("Save Failed", "Failed to save model name: " + error.getUserMessage());
                  }
               });
            } else {
               globalDisplay_.showErrorMessage("Error", "Failed to save endpoint.");
            }
         }
         
         @Override
         public void onError(ServerError error)
         {
            globalDisplay_.showErrorMessage("Save Failed", "Failed to save endpoint: " + error.getUserMessage());
         }
      });
   }
   
   private void completeLocalModelSave() {
      // Clear inputs
      localModelEndpointInput_.setValue("");
      localModelNameInput_.setValue("");
      localModelApiKeyInput_.setValue("");
      
      // Update UI to show stored state
      localModelInputContainer_.setVisible(false);
      localModelStoredContainer_.setVisible(true);
      
      // Update toggle to show enabled state
      updateToggleState("localmodel", true);
      
      // Notify handler to start proxy
      handler_.onBYOKEnabledChange("localmodel", true);
      
      globalDisplay_.showMessage(
         GlobalDisplay.MSG_INFO,
         "Success",
         "Local model configuration saved successfully."
      );
      
      // Reload available models since local model is now configured
      loadAvailableModels();
   }
   
   private void handleDeleteLocalModelConfig() {
      // First, disable BYOK for local model
      server_.setBYOKEnabled("localmodel", false, new ServerRequestCallback<Boolean>()
      {
         @Override
         public void onResponseReceived(Boolean disableSuccess)
         {
            if (disableSuccess) {
               // Then clear endpoint and configuration
               clearLocalModelConfiguration();
            } else {
               globalDisplay_.showErrorMessage("Error", "Failed to disable local model BYOK.");
            }
         }
         
         @Override
         public void onError(ServerError error)
         {
            globalDisplay_.showErrorMessage("Delete Failed", "Failed to disable local model BYOK: " + error.getUserMessage());
         }
      });
   }
   
   private void clearLocalModelConfiguration() {
      // Clear endpoint
      server_.setLocalModelEndpoint("", new ServerRequestCallback<Boolean>()
      {
         @Override
         public void onResponseReceived(Boolean success)
         {
            if (success) {
               // Clear model name
               server_.setLocalModelName("", new ServerRequestCallback<Boolean>()
               {
                  @Override
                  public void onResponseReceived(Boolean modelSuccess)
                  {
                     if (modelSuccess) {
                        // Clear API key if any
                        server_.clearBYOKApiKey("localmodel", new ServerRequestCallback<java.lang.Void>()
                        {
                           @Override
                           public void onResponseReceived(java.lang.Void result)
                           {
                              completeLocalModelDelete();
                           }
                           
                           @Override
                           public void onError(ServerError error)
                           {
                              // Still complete delete even if key clear fails
                              completeLocalModelDelete();
                           }
                        });
                     } else {
                        globalDisplay_.showErrorMessage("Error", "Failed to delete model name.");
                     }
                  }
                  
                  @Override
                  public void onError(ServerError error)
                  {
                     globalDisplay_.showErrorMessage("Delete Failed", "Failed to delete model name: " + error.getUserMessage());
                  }
               });
            } else {
               globalDisplay_.showErrorMessage("Error", "Failed to delete endpoint.");
            }
         }
         
         @Override
         public void onError(ServerError error)
         {
            globalDisplay_.showErrorMessage("Delete Failed", "Failed to delete endpoint: " + error.getUserMessage());
         }
      });
   }
   
   private void completeLocalModelDelete() {
      // Update UI to show input state
      localModelStoredContainer_.setVisible(false);
      localModelInputContainer_.setVisible(true);
      
      // Clear input fields
      localModelEndpointInput_.setValue("");
      localModelNameInput_.setValue("");
      localModelApiKeyInput_.setValue("");
      
      // Update toggle to show disabled state
      updateToggleState("localmodel", false);
      
      // Notify handler to stop proxy
      handler_.onBYOKEnabledChange("localmodel", false);
      
      globalDisplay_.showMessage(
         GlobalDisplay.MSG_INFO,
         "Success",
         "Local model configuration deleted successfully."
      );
      
      // Reload available models since local model is no longer configured
      loadAvailableModels();
   }
   
   private void handleAttachRulesFile() {
      FileSystemItem initialPath = FileSystemItem.home();
      if (currentRulesFilePath_ != null && !currentRulesFilePath_.isEmpty()) {
         FileSystemItem currentFile = FileSystemItem.createFile(currentRulesFilePath_);
         initialPath = currentFile.getParentPath();
      }
      
      fileDialogs_.openFile(
         "Select Rules File",
         fileSystemContext_,
         initialPath,
         new ProgressOperationWithInput<FileSystemItem>()
         {
            @Override
            public void execute(FileSystemItem file, ProgressIndicator indicator)
            {
               if (file != null) {
                  String filePath = file.getPath();
                  server_.setRulesFilePath(filePath, new ServerRequestCallback<Boolean>()
                  {
                     @Override
                     public void onResponseReceived(Boolean success)
                     {
                        if (success) {
                           currentRulesFilePath_ = filePath;
                           buildRulesFileDisplay();
                           indicator.onCompleted();
                        } else {
                           indicator.onError("Failed to save rules file path");
                        }
                     }
                     
                     @Override
                     public void onError(ServerError error)
                     {
                        indicator.onError(error.getUserMessage());
                     }
                  });
               } else {
                  indicator.onCompleted();
               }
            }
         }
      );
   }
   
   private void handleRemoveRulesFile() {
      server_.setRulesFilePath(null, new ServerRequestCallback<Boolean>()
      {
         @Override
         public void onResponseReceived(Boolean success)
         {
            if (success) {
               currentRulesFilePath_ = null;
               buildRulesFileDisplay();
            } else {
               globalDisplay_.showErrorMessage("Error", "Failed to remove rules file");
            }
         }
         
         @Override
         public void onError(ServerError error)
         {
            globalDisplay_.showErrorMessage("Error", "Failed to remove rules file: " + error.getUserMessage());
         }
      });
   }
   
   private void handleOpenRulesFile() {
      if (currentRulesFilePath_ != null && !currentRulesFilePath_.isEmpty()) {
         FileSystemItem file = FileSystemItem.createFile(currentRulesFilePath_);
         fileTypeRegistry_.openFile(file);
      }
   }
   
   // Native method to get toggle value
   private native String getToggleValue(com.google.gwt.dom.client.Element element) /*-{
      var toggleDiv = element.querySelector('div[data-setting]');
      return toggleDiv ? toggleDiv.getAttribute('data-value') : null;
   }-*/;
   
   // Native method to update toggle display
   private native void updateToggleDisplay(com.google.gwt.dom.client.Element element, String value, String offValue) /*-{
      var toggleDiv = element.querySelector('div[data-setting]');
      if (toggleDiv) {
         toggleDiv.style.display = 'block';
         toggleDiv.setAttribute('data-value', value);
         var slider = toggleDiv.querySelector('div');
         
         if (offValue === 'secure') {
            // Security mode toggle - secure is green/right, improve is grey/left
            var isSecure = value === 'secure';
            if (isSecure) {
               toggleDiv.classList.add('ai-toggle-enabled');
               toggleDiv.classList.remove('ai-toggle-disabled');
            } else {
               toggleDiv.classList.remove('ai-toggle-enabled');
               toggleDiv.classList.add('ai-toggle-disabled');
            }
            
            if (isSecure) {
               slider.style.left = '';
               slider.style.right = '1px';
            } else {
               slider.style.right = '';
               slider.style.left = '1px';
            }
         } else {
            // Automation toggle  
            var isEnabled = value === 'true';
            if (isEnabled) {
               toggleDiv.classList.add('ai-toggle-enabled');
               toggleDiv.classList.remove('ai-toggle-disabled');
            } else {
               toggleDiv.classList.remove('ai-toggle-enabled');
               toggleDiv.classList.add('ai-toggle-disabled');
            }
            slider.style.left = isEnabled ? '17px' : '1px';
         }
      }
   }-*/;
   
   // Native method to get slider value
   private native double getSliderValue() /*-{
      if (this.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::temperatureSlider_) {
         var slider = this.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::temperatureSlider_.@com.google.gwt.user.client.ui.HTML::getElement()().firstChild;
         if (slider) {
            return parseFloat(slider.value);
         }
      }
      return 0.5; // Default value
   }-*/;
   
   // Native method to set slider value
   private native void setSliderValue(double value) /*-{
      if (this.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::temperatureSlider_) {
         var slider = this.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::temperatureSlider_.@com.google.gwt.user.client.ui.HTML::getElement()().firstChild;
         if (slider) {
            slider.value = value;
         }
      }
   }-*/;
   
   private native void addNativeClickHandler(com.google.gwt.dom.client.Element element, String buttonText) /*-{
      var self = this;
      
      element.addEventListener('click', function(event) {
         // Only handle clicks that are specifically on button elements to preserve text selection
         if (event.target === element || element.contains(event.target)) {
            if (buttonText === 'Save API Key') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleSaveApiKey()();
            } else if (buttonText === 'Sign in') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleSignIn()();
            } else if (buttonText === 'Options') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleOptionsClick()();
            } else if (buttonText === 'Sign out') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleDeleteApiKey()();
            } else if (buttonText === '+ Add Rule') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleAddRule()();
            } else if (buttonText === 'Save') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleSaveNewRule()();
            } else if (buttonText === 'Cancel') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleCancelNewRule()();
            } else if (buttonText.startsWith('Options-')) {
               var ruleIndex = parseInt(buttonText.split('-')[1]);
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleOptionsMenu(I)(ruleIndex);
            } else if (buttonText.startsWith('Edit-')) {
               var ruleIndex = parseInt(buttonText.split('-')[1]);
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleEditRule(I)(ruleIndex);
            } else if (buttonText.startsWith('Delete-')) {
               var ruleIndex = parseInt(buttonText.split('-')[1]);
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleDeleteRule(I)(ruleIndex);
            } else if (buttonText.startsWith('EditSave-')) {
               var ruleIndex = parseInt(buttonText.split('-')[1]);
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleEditSave(I)(ruleIndex);
            } else if (buttonText.startsWith('EditCancel-')) {
               var ruleIndex = parseInt(buttonText.split('-')[1]);
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleEditCancel(I)(ruleIndex);
            } else if (buttonText.startsWith('RemoveListItem-')) {
               // Parse: "RemoveListItem-autoAcceptConsole_allow-text"
               var parts = buttonText.split('-');
               var listType = parts[1];
               var text = parts.slice(2).join('-'); // Rejoin in case text contains dashes
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleRemoveListItem(Ljava/lang/String;Ljava/lang/String;)(listType, text);
            } else if (buttonText.startsWith('DeleteBYOK-')) {
               var provider = buttonText.substring(11); // Remove "DeleteBYOK-" prefix
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleBYOKDeleteKey(Ljava/lang/String;)(provider);
            } else if (buttonText === 'SaveSageMaker') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleSaveSageMakerConfig()();
            } else if (buttonText === 'SaveLocalModelConfig') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleSaveLocalModelConfig()();
            } else if (buttonText === 'DeleteLocalModelConfig') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleDeleteLocalModelConfig()();
            } else if (buttonText === 'AttachRulesFile') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleAttachRulesFile()();
            } else if (buttonText === 'RemoveRulesFile') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleRemoveRulesFile()();
            } else if (buttonText === 'OpenRulesFile') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleOpenRulesFile()();
            }
            
            // Only prevent default for actual button clicks
            event.preventDefault();
            event.stopPropagation();
         }
         // If the click is not on the button element, let it bubble normally (allows text selection)
      }, false); // Use bubbling phase instead of capture to allow text selection to work first
   }-*/;
   
   // Button click handlers
   private void handleSaveApiKey() {
      String apiKey = apiKeyInput_.getValue().trim();
      if (!apiKey.isEmpty()) {
         handler_.onSaveApiKey(apiKey);
      } else {
         globalDisplay_.showErrorMessage("Error", "Please enter a valid API key.");
      }
   }
   
   private void handleDeleteApiKey() {
      handler_.onDeleteApiKey();
   }
   
   private void handleSignIn() {
      handler_.onSignInWithWebsite();
   }
   
   private void handleOptionsClick() {
      boolean isVisible = apiKeySection_.isVisible();
      apiKeySection_.setVisible(!isVisible);
   }
   
   private void handleModelChange() {
      if (modelSelect_ != null) {
         int selectedIndex = modelSelect_.getSelectedIndex();
         if (selectedIndex >= 0) {
            String selectedModel = modelSelect_.getValue(selectedIndex);
            handler_.onModelChange(selectedModel);
         }
      }
   }
   
   private void handleSliderChange() {
      if (temperatureSlider_ != null) {
         double sliderValue = getSliderValue();
         
         // Validate range
         if (sliderValue < 0.0) sliderValue = 0.0;
         if (sliderValue > 1.0) sliderValue = 1.0;
         
         currentTemperature_ = sliderValue;
         
         // Update the input box to match the slider
         if (temperatureInput_ != null) {
            temperatureInput_.setValue(String.valueOf(sliderValue));
         }
         
         handler_.onTemperatureChange(sliderValue);
      }
   }
   
   private void handleInputChange() {
      if (temperatureInput_ != null) {
         String inputValue = temperatureInput_.getValue();
         
         try {
            double temperature = Double.parseDouble(inputValue);
            
            // Validate range
            if (temperature < 0.0) {
               temperature = 0.0;
               temperatureInput_.setValue("0.0");
            } else if (temperature > 1.0) {
               temperature = 1.0;
               temperatureInput_.setValue("1.0");
            }
            
            currentTemperature_ = temperature;
            
            // Update the slider to match the input
            if (temperatureSlider_ != null) {
               setSliderValue(temperature);
            }
            
            handler_.onTemperatureChange(temperature);
         } catch (NumberFormatException e) {
            Debug.log("Invalid temperature value: " + inputValue + ", reverting to current: " + currentTemperature_);
            temperatureInput_.setValue(String.valueOf(currentTemperature_));
         }
      }
   }
   
   private void handleSecurityModeChange() {
      // Never read from toggle elements - always query the server to get current state
      server_.getSecurityMode(new ServerRequestCallback<String>() {
         @Override
         public void onResponseReceived(String mode) {
            String currentMode = mode != null ? mode : "secure";
            
            // Toggle to the opposite of current mode
            String newMode = "secure".equals(currentMode) ? "improve" : "secure";
            
            // Update the display immediately with new mode
            if (securityModeToggle_ != null) {
               updateToggleDisplay(securityModeToggle_.getElement(), newMode, "secure");
            }
            if (securityModeText_ != null) {
               boolean isSecure = "secure".equals(newMode);
               String modeText = isSecure ? "Secure" : "Improve Rao for everyone";
               securityModeText_.setText("On secure mode, no analytics are collected and zero data is retained by the model providers. This must be used for any sensitive data like PHI. On \"Improve Rao for everyone,\" user analytics are collected to improve the experience. Still, zero data is retained by the model providers. Your current mode is: " + modeText);
            }
            
            // Initialize PostHog with new security mode
            updatePostHogForSecurityMode(newMode);
            
            // Send change to handler
            handler_.onSecurityModeChange(newMode);
         }
         
         @Override
         public void onError(ServerError error) {
            Debug.log("Error loading security mode for toggle: " + error.getMessage());
            // On error, just refresh display without changing
            updateSecurityModeDisplay();
         }
      });
   }
   
   private void handleWebSearchChange() {
      // Never read from toggle elements - always query the server to get current state
      server_.getWebSearchEnabled(new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean enabled) {
            boolean currentEnabled = enabled != null ? enabled : false;
            
            // Toggle to the opposite of current state
            boolean newEnabled = !currentEnabled;
            
            // Update the display immediately with new state
            if (webSearchToggle_ != null) {
               updateToggleDisplay(webSearchToggle_.getElement(), newEnabled ? "true" : "false", "false");
            }
            if (webSearchText_ != null) {
               String statusText = newEnabled ? "on" : "off";
               webSearchText_.setText("When web search is on, the model may choose to search the web. Such searches could involve information from the conversation history and should be disabled for sensitive data like PHI. Web search is currently: " + statusText);
            }
            
            // Send change to handler
            handler_.onWebSearchEnabledChange(newEnabled);
         }
         
         @Override
         public void onError(ServerError error) {
            Debug.log("Error loading web search enabled for toggle: " + error.getMessage());
            // On error, just refresh display without changing
            updateWebSearchDisplay();
         }
      });
   }
   
   private void handleAutoAcceptEditsChange() {
      // Follow security mode pattern - query server for current state, then toggle
      server_.getAutoAcceptEdits(new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean currentValue) {
            boolean current = currentValue != null ? currentValue : false;
            boolean newValue = !current;
            
            // Update visual state to match the new server value
            if (autoAcceptEditsToggle_ != null) {
               updateToggleDisplay(autoAcceptEditsToggle_.getElement(), newValue ? "true" : "false", "true");
            }
            
            handler_.onAutoAcceptEditsChange(newValue);
         }
         
         @Override
         public void onError(ServerError error) {
            // Error getting auto accept edits
         }
      });
   }
   
   private void handleAutoAcceptConsoleChange() {
      // Follow security mode pattern - query server for current state, then toggle
      server_.getAutoAcceptConsole(new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean currentValue) {
            boolean current = currentValue != null ? currentValue : false;
            boolean newValue = !current;
            
            // Update visual state to match the new server value
            if (autoAcceptConsoleToggle_ != null) {
               updateToggleDisplay(autoAcceptConsoleToggle_.getElement(), newValue ? "true" : "false", "true");
            }
            
            // Update panel visibility to match the new server value
            toggleAllowDenyPanel("autoAcceptConsole", newValue);
            
            // Load allow/deny lists data when enabling the feature
            if (newValue) {
               loadAutomationLists("auto_accept_console");
            }
            
            handler_.onAutoAcceptConsoleChange(newValue);
         }
         
         @Override
         public void onError(ServerError error) {
            // Error getting auto accept console
         }
      });
   }
   
   private void handleAutoAcceptTerminalChange() {
      // Follow security mode pattern - query server for current state, then toggle
      server_.getAutoAcceptTerminal(new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean currentValue) {
            boolean current = currentValue != null ? currentValue : false;
            boolean newValue = !current;
            
            // Update visual state to match the new server value
            if (autoAcceptTerminalToggle_ != null) {
               updateToggleDisplay(autoAcceptTerminalToggle_.getElement(), newValue ? "true" : "false", "true");
            }
            
            // Update panel visibility to match the new server value
            toggleAllowDenyPanel("autoAcceptTerminal", newValue);
            
            // Load allow/deny lists data when enabling the feature
            if (newValue) {
               loadAutomationLists("auto_accept_terminal");
            }
            
            handler_.onAutoAcceptTerminalChange(newValue);
         }
         
         @Override
         public void onError(ServerError error) {
            // Error getting auto accept terminal
         }
      });
   }
   
   private void handleAutoRunFilesChange() {
      // Follow security mode pattern - query server for current state, then toggle
      server_.getAutoRunFiles(new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean currentValue) {
            boolean current = currentValue != null ? currentValue : false;
            boolean newValue = !current;
            
            // Update visual state to match the new server value
            if (autoRunFilesToggle_ != null) {
               updateToggleDisplay(autoRunFilesToggle_.getElement(), newValue ? "true" : "false", "true");
            }
            
            // Update panel visibility to match the new server value
            toggleAllowDenyPanel("autoRunFiles", newValue);
            
            // Load allow/deny lists data when enabling the feature
            if (newValue) {
               loadAutomationLists("auto_run_files");
            }
            
            handler_.onAutoRunFilesChange(newValue);
         }
         
         @Override
         public void onError(ServerError error) {
            Debug.log("Error getting auto run files: " + error.getMessage());
         }
      });
   }
   
   private void handleAutoDeleteFilesChange() {
      // Follow security mode pattern - query server for current state, then toggle
      server_.getAutoDeleteFiles(new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean currentValue) {
            boolean current = currentValue != null ? currentValue : false;
            boolean newValue = !current;
            
            // Update visual state to match the new server value
            if (autoDeleteFilesToggle_ != null) {
               updateToggleDisplay(autoDeleteFilesToggle_.getElement(), newValue ? "true" : "false", "true");
            }
            
            handler_.onAutoDeleteFilesChange(newValue);
         }
         
         @Override
         public void onError(ServerError error) {
            Debug.log("Error getting auto delete files: " + error.getMessage());
         }
      });
   }
   
   private void handleAllowAnythingToggleChange(String settingName) {
      // Query server for current state, then toggle
      if ("auto_accept_console_allow_anything".equals(settingName)) {
         server_.getAutoAcceptConsoleAllowAnything(new ServerRequestCallback<Boolean>() {
            @Override
            public void onResponseReceived(Boolean currentValue) {
               boolean current = currentValue != null ? currentValue : false;
               boolean newValue = !current;
               
               // Update visual state
               updateAllowAnythingToggleDisplay(settingName, newValue);
               
               // Update list visibility
               String baseSettingName = settingName.replace("_allow_anything", "");
               updateListsVisibility(baseSettingName, !newValue);
               
               // Save to server
               handler_.onAutoAcceptConsoleAllowAnythingChange(newValue);
            }
            
            @Override
            public void onError(ServerError error) {
               Debug.log("Error getting " + settingName + ": " + error.getMessage());
            }
         });
      } else if ("auto_accept_terminal_allow_anything".equals(settingName)) {
         server_.getAutoAcceptTerminalAllowAnything(new ServerRequestCallback<Boolean>() {
            @Override
            public void onResponseReceived(Boolean currentValue) {
               boolean current = currentValue != null ? currentValue : false;
               boolean newValue = !current;
               
               // Update visual state
               updateAllowAnythingToggleDisplay(settingName, newValue);
               
               // Update list visibility
               String baseSettingName = settingName.replace("_allow_anything", "");
               updateListsVisibility(baseSettingName, !newValue);
               
               // Save to server
               handler_.onAutoAcceptTerminalAllowAnythingChange(newValue);
            }
            
            @Override
            public void onError(ServerError error) {
               Debug.log("Error getting " + settingName + ": " + error.getMessage());
            }
         });
      } else if ("auto_run_files_allow_anything".equals(settingName)) {
         server_.getAutoRunFilesAllowAnything(new ServerRequestCallback<Boolean>() {
            @Override
            public void onResponseReceived(Boolean currentValue) {
               boolean current = currentValue != null ? currentValue : false;
               boolean newValue = !current;
               
               // Update visual state
               updateAllowAnythingToggleDisplay(settingName, newValue);
               
               // Update list visibility
               String baseSettingName = settingName.replace("_allow_anything", "");
               updateListsVisibility(baseSettingName, !newValue);
               
               // Save to server
               handler_.onAutoRunFilesAllowAnythingChange(newValue);
            }
            
            @Override
            public void onError(ServerError error) {
               Debug.log("Error getting " + settingName + ": " + error.getMessage());
            }
         });
      }
   }
   
   private void handleAllowAnythingToggle(String settingName, boolean enabled) {
      // Update the message text immediately
      updateAllowAnythingLabelText(settingName, enabled);
      
      // Extract base setting name for list visibility
      String baseSettingName = settingName.replace("_allow_anything", "");
      
      // Show/hide specific lists based on mode
      // When "allow anything" is OFF (allow list mode), show only allow list
      // When "allow anything" is ON (deny list mode), show only deny list
      updateListsVisibility(baseSettingName, !enabled);
      
      // Handle different settings based on setting name
      if ("auto_accept_console_allow_anything".equals(settingName)) {
         handler_.onAutoAcceptConsoleAllowAnythingChange(enabled);
      } else if ("auto_accept_terminal_allow_anything".equals(settingName)) {
         handler_.onAutoAcceptTerminalAllowAnythingChange(enabled);
      } else if ("auto_run_files_allow_anything".equals(settingName)) {
         handler_.onAutoRunFilesAllowAnythingChange(enabled);
      }
   }
   
   private void handleAddListItem(Object itemsContainer, String text, String listType) {
      // Cast the container back to FlowPanel
      if (itemsContainer instanceof FlowPanel) {
         FlowPanel container = (FlowPanel) itemsContainer;
         addListItem(container, text, listType);
      }
   }


   
   private void updateSecurityModeDisplay() {
      // Always query fresh values from server instead of using cached values
      server_.getSecurityMode(new ServerRequestCallback<String>() {
         @Override
         public void onResponseReceived(String mode) {
            String currentMode = mode != null ? mode : "secure";
            
            // Treat lockdown as secure for display purposes in AI settings
            String displayMode = "lockdown".equals(currentMode) ? "secure" : currentMode;
            
            if (securityModeToggle_ != null) {
               updateToggleDisplay(securityModeToggle_.getElement(), displayMode, "secure");
            }
            if (securityModeText_ != null) {
               boolean isSecure = "secure".equals(displayMode);
               String modeText = isSecure ? "Secure" : "Improve Rao for everyone";
               securityModeText_.setText("On secure mode, no analytics are collected and zero data is retained by the model providers. This must be used for any sensitive data like PHI. On \"Improve Rao for everyone,\" user analytics are collected to improve the experience. Still, zero data is retained by the model providers. Your current mode is: " + modeText);
            }
            
            // Initialize PostHog with current security mode
            updatePostHogForSecurityMode(currentMode);
         }
         
         @Override
         public void onError(ServerError error) {
            // Use secure as default on error
            if (securityModeToggle_ != null) {
               updateToggleDisplay(securityModeToggle_.getElement(), "secure", "secure");
            }
            if (securityModeText_ != null) {
               securityModeText_.setText("On secure mode, no analytics are collected and zero data is retained by the model providers. This must be used for any sensitive data like PHI. On \"Improve Rao for everyone,\" user analytics are collected to improve the experience. Still, zero data is retained by the model providers. Your current mode is: Secure");
            }
            
            // Initialize PostHog with secure mode as default
            updatePostHogForSecurityMode("secure");
         }
      });
   }
   
   private void updateAutomationTogglesDisplay() {
      // Load auto-accept edits setting
      server_.getAutoAcceptEdits(new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean enabled) {
            boolean isEnabled = enabled != null ? enabled : false;
            if (autoAcceptEditsToggle_ != null) {
               updateToggleDisplay(autoAcceptEditsToggle_.getElement(), isEnabled ? "true" : "false", "true");
            }
         }
         
         @Override
         public void onError(ServerError error) {
            // Error loading auto accept edits for display
            // No fallback - let R handle defaults
         }
      });
      
      // Load auto-accept console setting
      server_.getAutoAcceptConsole(new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean enabled) {
            boolean isEnabled = enabled != null ? enabled : false;
            if (autoAcceptConsoleToggle_ != null) {
               updateToggleDisplay(autoAcceptConsoleToggle_.getElement(), isEnabled ? "true" : "false", "true");
            }
            // Update panel visibility to match server state
            toggleAllowDenyPanel("autoAcceptConsole", isEnabled);
            
            // Load allow/deny lists for console
            if (isEnabled) {
               loadAutomationLists("auto_accept_console");
            }
         }
         
         @Override
         public void onError(ServerError error) {
            // Error loading auto accept console for display
            // No fallback - let R handle defaults
         }
      });
      
      // Load auto-accept terminal setting
      server_.getAutoAcceptTerminal(new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean enabled) {
            boolean isEnabled = enabled != null ? enabled : false;
            if (autoAcceptTerminalToggle_ != null) {
               updateToggleDisplay(autoAcceptTerminalToggle_.getElement(), isEnabled ? "true" : "false", "true");
            }
            // Update panel visibility to match server state
            toggleAllowDenyPanel("autoAcceptTerminal", isEnabled);
            
            // Load allow/deny lists for terminal
            if (isEnabled) {
               loadAutomationLists("auto_accept_terminal");
            }
         }
         
         @Override
         public void onError(ServerError error) {
            // Error loading auto accept terminal for display
            // No fallback - let R handle defaults
         }
      });
      
      // Load auto-run files setting
      server_.getAutoRunFiles(new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean enabled) {
            boolean isEnabled = enabled != null ? enabled : false;
            if (autoRunFilesToggle_ != null) {
               updateToggleDisplay(autoRunFilesToggle_.getElement(), isEnabled ? "true" : "false", "true");
            }
            // Update panel visibility to match server state
            toggleAllowDenyPanel("autoRunFiles", isEnabled);
            
            // Load allow/deny lists for run files
            if (isEnabled) {
               loadAutomationLists("auto_run_files");
            }
         }
         
         @Override
         public void onError(ServerError error) {
            Debug.log("Error loading auto run files for display: " + error.getMessage());
            // No fallback - let R handle defaults
         }
      });
      
      // Load auto-delete files setting
      server_.getAutoDeleteFiles(new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean enabled) {
            boolean isEnabled = enabled != null ? enabled : false;
            if (autoDeleteFilesToggle_ != null) {
               updateToggleDisplay(autoDeleteFilesToggle_.getElement(), isEnabled ? "true" : "false", "true");
            }
         }
         
         @Override
         public void onError(ServerError error) {
            Debug.log("Error loading auto delete files for display: " + error.getMessage());
            // No fallback - let R handle defaults
         }
      });
   }
   
   private void updateWebSearchDisplay() {
      // Always query fresh values from server instead of using cached values
      server_.getWebSearchEnabled(new ServerRequestCallback<Boolean>() {
         @Override
         public void onResponseReceived(Boolean enabled) {
            boolean currentEnabled = enabled != null ? enabled : false;
            if (webSearchToggle_ != null) {
               updateToggleDisplay(webSearchToggle_.getElement(), currentEnabled ? "true" : "false", "false");
            }
            if (webSearchText_ != null) {
               String statusText = currentEnabled ? "on" : "off";
               webSearchText_.setText("When web search is on, the model may choose to search the web. Such searches could involve information from the conversation history and should be disabled for sensitive data like PHI. Web search is currently: " + statusText);
            }
         }
         
         @Override
         public void onError(ServerError error) {
            Debug.log("Error loading web search enabled for display: " + error.getMessage());
            // Use false as default on error
            if (webSearchToggle_ != null) {
               updateToggleDisplay(webSearchToggle_.getElement(), "false", "false");
            }
            if (webSearchText_ != null) {
               webSearchText_.setText("When web search is on, the model may choose to search the web. Such searches could involve information from the conversation history and should be disabled for sensitive data like PHI. Web search is currently: off");
            }
         }
      });
   }
   
   public void onSecurityModeChanged(String mode) {
      updateSecurityModeDisplay();
   }
   
   public void onWebSearchEnabledChanged(boolean enabled) {
      updateWebSearchDisplay();
   }
   
   // Rule management callback methods
   public void onRuleAdded() {
      // Hide the new rule panel and show the add rule button
      if (newRulePanel_ != null) {
         newRulePanel_.setVisible(false);
      }
      if (addRuleButton_ != null) {
         addRuleButton_.setVisible(true);
      }
      // Refresh rules from server and rebuild the rules list
      refreshRules();
   }
   
   public void onRuleEdited() {
      // Refresh rules from server and rebuild the rules list
      refreshRules();
   }
   
   public void onRuleDeleted() {
      // Refresh rules from server and rebuild the rules list
      refreshRules();
   }
   
   private void refreshRules() {
      server_.getUserRules(new ServerRequestCallback<JavaScriptObject>() {
         @Override
         public void onResponseReceived(JavaScriptObject response) {
            // Extract rules from response and update current rules
            JsArrayString rulesArray = extractRulesArray(response);
            currentRules_.clear();
            for (int i = 0; i < rulesArray.length(); i++) {
               currentRules_.add(rulesArray.get(i));
            }
            // Rebuild the rules list
            buildRulesList();
         }
         
         @Override
         public void onError(ServerError error) {
            Debug.log("Error loading user rules: " + error.getMessage());
         }
      });
   }
   
   private native JsArrayString extractRulesArray(JavaScriptObject response) /*-{
      // Handle different possible response formats from R
      if (response) {
         // If response is already an array
         if (response.length !== undefined) {
            return response;
         }
         // If response is an object with a rules property
         if (response.rules && response.rules.length !== undefined) {
            return response.rules;
         }
         // If response is a list/object, convert to array
         var rules = [];
         var keys = Object.keys(response);
         for (var i = 0; i < keys.length; i++) {
            var key = keys[i];
            if (key !== 'success' && key !== 'error') {
               rules.push(response[key]);
            }
         }
         return rules;
      }
      // Return empty array if no rules found
      return [];
   }-*/;
   
   /**
    * Update PostHog tracking based on security mode
    * @param mode The security mode ("secure" or "make_rao_better")
    */
   private void updatePostHogForSecurityMode(String mode) {
      updatePostHogForSecurityModeImpl(mode);
   }
   
   /**
    * Native method to update PostHog tracking based on security mode
    */
   private native void updatePostHogForSecurityModeImpl(String mode) /*-{
      if ($wnd.PostHogHelper && $wnd.PostHogHelper.updateTrackingForSecurityMode) {
         $wnd.PostHogHelper.updateTrackingForSecurityMode(mode);
      } else {
         console.warn("PostHog helper not available for security mode update");
      }
   }-*/;
   
   /**
    * Creates a section header with just the title
    */
   private HorizontalPanel createSectionHeader(String title, String sectionName, boolean isExpanded) {
      HorizontalPanel headerPanel = new HorizontalPanel();
      headerPanel.setWidth("100%");
      headerPanel.addStyleName(styles_.sectionHeaderPanel());
      headerPanel.setVerticalAlignment(HorizontalPanel.ALIGN_MIDDLE);
      
      // Section title only
      Label titleLabel = new Label(title);
      titleLabel.addStyleName(styles_.sectionTitle());
      headerPanel.add(titleLabel);
      
      return headerPanel;
   }
   
   /**
    * Creates a chevron button positioned absolutely on the right side of the section
    */
   private HTML createChevronButton(String sectionName, boolean isExpanded) {
      HTML chevronButton = new HTML();
      chevronButton.addStyleName(styles_.sectionChevron());
      
      // Create double chevron SVG icon with CSS classes for theme support
      String chevronSvg = 
         "<div class='ai-chevron-button'>" +
         "<svg width='10' height='12' viewBox='0 0 10 12' class='ai-chevron-svg'>" +
         "<path d='M2 4L5 2L8 4' stroke-width='1.2' fill='none' stroke-linecap='round' stroke-linejoin='round'/>" +
         "<path d='M2 8L5 10L8 8' stroke-width='1.2' fill='none' stroke-linecap='round' stroke-linejoin='round'/>" +
         "</svg>" +
         "</div>";
      
      chevronButton.setHTML(chevronSvg);
      
      // Position absolutely on the right side of the section
      chevronButton.getElement().getStyle().setProperty("position", "absolute");
      chevronButton.getElement().getStyle().setProperty("top", "8px");
      chevronButton.getElement().getStyle().setProperty("right", "8px");
      chevronButton.getElement().getStyle().setProperty("zIndex", "10");
      
      // Add click handler that actually works
      addNativeSectionToggleHandler(chevronButton.getElement(), sectionName);
      
      return chevronButton;
   }
   
   /**
    * Toggles a section's expanded/collapsed state
    */
   private void toggleSection(String sectionName) {
      boolean wasExpanded = getSectionExpandedState(sectionName);
      boolean newExpanded = !wasExpanded;
      setSectionExpandedState(sectionName, newExpanded);
      
      // Get the section element and toggle its content
      HTML sectionElement = getSectionElement(sectionName);
      if (sectionElement != null) {
         toggleSectionContent(sectionElement.getElement(), newExpanded);
      }
   }
   
   /**
    * Gets the section HTML element for a given section name
    */
   private HTML getSectionElement(String sectionName) {
      switch (sectionName) {
         case "profile":
            return profileSection_;
         case "model":
            return modelSection_;
         case "rules":
            return rulesSection_;
         case "security":
            return securitySection_;
         case "automation":
            return automationSection_;
         case "byok":
            return byokSection_;
         default:
            return null;
      }
   }
   
   /**
    * Native method to properly collapse/expand section content with animation
    */
   private native void toggleSectionContent(com.google.gwt.dom.client.Element sectionElement, boolean expanded) /*-{
      try {
         // Get the actual obfuscated class names
         var sectionContentClass = this.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::getSectionContentClassName()();
         var sectionContentCollapsedClass = this.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::getSectionContentCollapsedClassName()();
         var collapsedClass = this.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::getCollapsedClassName()();
         
         // Find the content panel within the section using the actual class name
         var contentPanel = sectionElement.querySelector('.' + sectionContentClass);
         if (!contentPanel) {
            return;
         }
         
         if (expanded) {
            // Expanding: restore content panel and section container
            contentPanel.style.height = 'auto';
            contentPanel.style.maxHeight = 'none';
            contentPanel.style.opacity = '1';
            contentPanel.style.overflow = 'visible';
            contentPanel.style.marginTop = '8px';
            if (contentPanel.classList) {
               contentPanel.classList.remove(sectionContentCollapsedClass);
            }
            
            // CRITICAL: Restore section container height
            sectionElement.style.height = 'auto';
            sectionElement.style.minHeight = 'auto';
            sectionElement.style.maxHeight = 'none';
            sectionElement.style.overflow = 'visible';
            
            // Remove collapsed class from section container to restore full padding
            if (sectionElement.classList) {
               sectionElement.classList.remove(collapsedClass);
            }
         } else {
            
            // Add the collapsed class which should have !important rules
            if (contentPanel.classList) {
               contentPanel.classList.add(sectionContentCollapsedClass);
            }
            
            // Add collapsed class to section container to reduce padding
            if (sectionElement.classList) {
               sectionElement.classList.add(collapsedClass);
            }
            
            // Also force the section container to collapse by setting its height
            var headerHeight = 36;
            sectionElement.style.height = headerHeight + 'px';
            sectionElement.style.minHeight = headerHeight + 'px';
            sectionElement.style.maxHeight = headerHeight + 'px';
            sectionElement.style.overflow = 'hidden';
         }
      } catch (e) {
         console.error('Error toggling section content:', e);
      }
   }-*/;
   
   /**
    * Get the actual obfuscated CSS class name for sectionContent
    */
   private String getSectionContentClassName() {
      return styles_.sectionContent();
   }
   
   /**
    * Get the actual obfuscated CSS class name for sectionContentCollapsed
    */
   private String getSectionContentCollapsedClassName() {
      return styles_.sectionContentCollapsed();
   }
   
   /**
    * Get the actual obfuscated CSS class name for collapsed
    */
   private String getCollapsedClassName() {
      return styles_.collapsed();
   }
   
   private String getProviderFromModel(String model) {
      // Check for SageMaker models first (they have a specific prefix)
      if (model.startsWith("sagemaker:")) {
         return "sagemaker";
      } else if (model.toLowerCase().contains("sagemaker")) {
         return "sagemaker";
      } else if (model.startsWith("claude-")) {
         return "anthropic";
      } else if (model.startsWith("gpt-") || model.startsWith("o1-")) {
         return "openai";
      }
      return "anthropic"; // default
   }
   
   /**
    * Gets the expanded state for a section
    */
   private boolean getSectionExpandedState(String sectionName) {
      switch (sectionName) {
         case "profile":
            return profileSectionExpanded_;
         case "model":
            return modelSectionExpanded_;
         case "rules":
            return rulesSectionExpanded_;
         case "security":
            return securitySectionExpanded_;
         case "automation":
            return automationSectionExpanded_;
         case "byok":
            return byokSectionExpanded_;
         default:
            return true;
      }
   }
   
   /**
    * Sets the expanded state for a section
    */
   private void setSectionExpandedState(String sectionName, boolean expanded) {
      switch (sectionName) {
         case "profile":
            profileSectionExpanded_ = expanded;
            break;
         case "model":
            modelSectionExpanded_ = expanded;
            break;
         case "rules":
            rulesSectionExpanded_ = expanded;
            break;
         case "security":
            securitySectionExpanded_ = expanded;
            break;
         case "automation":
            automationSectionExpanded_ = expanded;
            break;
         case "byok":
            byokSectionExpanded_ = expanded;
            break;
      }
   }
   
   /**
    * Native DOM event handler for section header clicks
    */
   private native void addNativeSectionToggleHandler(com.google.gwt.dom.client.Element element, String sectionName) /*-{
      var self = this;
      
      element.addEventListener('click', function(event) {
         try {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::toggleSection(Ljava/lang/String;)(sectionName);
         } catch (e) {
            console.error('Error toggling section:', e);
         }
         event.preventDefault();
         event.stopPropagation();
      }, false); // Use bubbling phase to allow text selection
   }-*/;
   
   /**
    * Native handler for the profile section chevron specifically
    */
   private native void addNativeProfileChevronHandler() /*-{
      var self = this;
      
      $wnd.handleProfileChevronClick = function() {
         try {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::toggleSection(Ljava/lang/String;)('profile');
         } catch (e) {
            console.error('Error toggling profile section:', e);
         }
      };
   }-*/;
   
   /**
    * Immediately applies visual collapse to a section without animation
    * Used on page load to ensure collapsed sections appear collapsed
    */
   private native void applyImmediateCollapse(com.google.gwt.dom.client.Element sectionElement) /*-{
      try {
         // Get the actual obfuscated class names
         var sectionContentClass = this.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::getSectionContentClassName()();
         var sectionContentCollapsedClass = this.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::getSectionContentCollapsedClassName()();
         var collapsedClass = this.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::getCollapsedClassName()();
         
         // Find the content panel within the section
         var contentPanel = sectionElement.querySelector('.' + sectionContentClass);
         if (contentPanel) {
            // Add collapsed classes (to match the toggle behavior)
            if (contentPanel.classList) {
               contentPanel.classList.add(sectionContentCollapsedClass);
            }
            if (sectionElement.classList) {
               sectionElement.classList.add(collapsedClass);
            }
            // Use same height as toggle method for consistency
            var headerHeight = 36; // Match the toggle method height
            sectionElement.style.height = headerHeight + 'px';
            sectionElement.style.minHeight = headerHeight + 'px';
            sectionElement.style.maxHeight = headerHeight + 'px';
            sectionElement.style.overflow = 'hidden';
         }
      } catch (e) {
         console.error('Error applying immediate collapse:', e);
      }
   }-*/;

   private void loadAutomationLists(String settingPrefix) {
      // Load both allow and deny lists for the given setting prefix
      // (e.g., "auto_accept_console" loads both allow_list and deny_list)
      
      String allowListType = settingPrefix + "_allow_list";
      String denyListType = settingPrefix + "_deny_list";
      String allowAnythingType = settingPrefix + "_allow_anything";
      
      // Load "Allow anything" toggle state first
      loadAllowAnythingToggle(settingPrefix, allowAnythingType);
      
      // Load allow list
      server_.getAutomationList(allowListType, new ServerRequestCallback<JavaScriptObject>() {
         @Override
         public void onResponseReceived(JavaScriptObject result) {
            if (result != null) {
               String[] items = convertJavaScriptArrayToStringArray(result);
               loadListItems(allowListType, items);
            }
         }
         
         @Override
         public void onError(ServerError error) {
            // TODO: Handle error appropriately
         }
      });
      
      // Load deny list
      server_.getAutomationList(denyListType, new ServerRequestCallback<JavaScriptObject>() {
         @Override
         public void onResponseReceived(JavaScriptObject result) {
            if (result != null) {
               String[] items = convertJavaScriptArrayToStringArray(result);
               loadListItems(denyListType, items);
            }
         }
         
         @Override
         public void onError(ServerError error) {
            // TODO: Handle error appropriately
         }
      });
   }
   
   private void loadListItems(String listType, String[] items) {
      FlowPanel container = automationListContainers_.get(listType);
      
      if (container != null) {
         // Clear existing items
         container.clear();
         
         // Add each item visually only (don't save back to R)
         for (String item : items) {
            if (item != null && !item.trim().isEmpty()) {
               addListItemVisualOnly(container, item, listType);
            }
         }
      }
   }
   
   private native String[] convertJavaScriptArrayToStringArray(JavaScriptObject jsArray) /*-{
      if (!jsArray || !Array.isArray(jsArray)) {
         return [];
      }
      
      var result = [];
      for (var i = 0; i < jsArray.length; i++) {
         if (jsArray[i] != null) {
            result[i] = String(jsArray[i]);
         }
      }
      return result;
   }-*/;
   
   private void loadAllowAnythingToggle(String settingPrefix, String allowAnythingType) {
      // Load the "Allow anything" toggle state for this setting
      if ("auto_accept_console".equals(settingPrefix)) {
         server_.getAutoAcceptConsoleAllowAnything(new ServerRequestCallback<Boolean>() {
            @Override
            public void onResponseReceived(Boolean enabled) {
               boolean isEnabled = enabled != null ? enabled : false;
               updateAllowAnythingToggleDisplay(allowAnythingType, isEnabled);
               // Show the lists container now that we have R data
               showListsContainer(settingPrefix);
               // Update lists visibility based on "Allow anything" state
               updateListsVisibility(settingPrefix, !isEnabled);
            }
            
            @Override
            public void onError(ServerError error) {
               Debug.log("Error loading " + allowAnythingType + ": " + error.getMessage());
               // No fallback - let R handle defaults
            }
         });
      } else if ("auto_accept_terminal".equals(settingPrefix)) {
         server_.getAutoAcceptTerminalAllowAnything(new ServerRequestCallback<Boolean>() {
            @Override
            public void onResponseReceived(Boolean enabled) {
               boolean isEnabled = enabled != null ? enabled : false;
               updateAllowAnythingToggleDisplay(allowAnythingType, isEnabled);
               showListsContainer(settingPrefix);
               updateListsVisibility(settingPrefix, !isEnabled);
            }
            
            @Override
            public void onError(ServerError error) {
               Debug.log("Error loading " + allowAnythingType + ": " + error.getMessage());
               // No fallback - let R handle defaults
            }
         });
      } else if ("auto_run_files".equals(settingPrefix)) {
         server_.getAutoRunFilesAllowAnything(new ServerRequestCallback<Boolean>() {
            @Override
            public void onResponseReceived(Boolean enabled) {
               boolean isEnabled = enabled != null ? enabled : false;
               updateAllowAnythingToggleDisplay(allowAnythingType, isEnabled);
               // Show the lists container now that we have R data
               showListsContainer(settingPrefix);
               updateListsVisibility(settingPrefix, !isEnabled);
            }
            
            @Override
            public void onError(ServerError error) {
               Debug.log("Error loading " + allowAnythingType + ": " + error.getMessage());
               // No fallback - let R handle defaults
            }
         });
      }
   }
   
   private void showListsContainer(String settingPrefix) {
      com.google.gwt.dom.client.Element automationElement = automationSection_.getElement();
      com.google.gwt.dom.client.Element listsContainer = findElementByAttribute(automationElement, "data-lists-container", settingPrefix);
      
      if (listsContainer != null) {
         listsContainer.getStyle().setProperty("display", "block");
      }
   }
   
   private void updateAllowAnythingToggleDisplay(String settingName, boolean enabled) {
      com.google.gwt.dom.client.Element automationElement = automationSection_.getElement();
      com.google.gwt.dom.client.Element toggleElement = findElementByAttribute(automationElement, "data-setting", settingName);
      
      if (toggleElement != null) {
         // Show the toggle now that we have R data
         toggleElement.getStyle().setProperty("display", "block");
         updateAllowAnythingToggleDisplayStyle(toggleElement, enabled);
      }
      
      // Also update the descriptive label text and show it
      updateAllowAnythingLabelText(settingName, enabled);
   }
   
   // Native method to update allow-anything toggle display (smaller toggles with different dimensions)
   private native void updateAllowAnythingToggleDisplayStyle(com.google.gwt.dom.client.Element element, boolean enabled) /*-{
      element.setAttribute('data-value', enabled ? 'true' : 'false');
      var slider = element.querySelector('div');
      
      // Allow-anything toggles: 28x14px with 12px slider
      if (enabled) {
         element.classList.add('ai-toggle-enabled');
         element.classList.remove('ai-toggle-disabled');
      } else {
         element.classList.remove('ai-toggle-enabled');
         element.classList.add('ai-toggle-disabled');
      }
      
      // For allow-anything toggles, use simple left positioning (14px when enabled, 1px when disabled)
      if (slider) {
         slider.style.left = enabled ? '14px' : '1px';
      }
   }-*/;
   
   private void updateAllowAnythingLabelText(String settingName, boolean allowAnythingEnabled) {
      com.google.gwt.dom.client.Element automationElement = automationSection_.getElement();
      // Extract the base setting name (remove "_allow_anything" suffix if present)
      String baseSettingName = settingName.replace("_allow_anything", "");
      com.google.gwt.dom.client.Element labelElement = findElementByAttribute(automationElement, "data-allow-anything-label", baseSettingName);
      
      if (labelElement != null) {
         // Show the label now that we have R data
         labelElement.getStyle().setProperty("display", "block");
         String newMessage = getAllowAnythingMessage(baseSettingName, allowAnythingEnabled);
         labelElement.setInnerHTML(newMessage);
      }
   }
   
   private void updateListsVisibility(String settingPrefix, boolean allowMode) {
      // allowMode = true: show only allow list (hide deny list)
      // allowMode = false: show only deny list (hide allow list)
      
      com.google.gwt.dom.client.Element automationElement = automationSection_.getElement();
      
      // Find allow list section
      String allowListType = settingPrefix + "_allow_list";
      FlowPanel allowContainer = automationListContainers_.get(allowListType);
      if (allowContainer != null) {
         com.google.gwt.dom.client.Element allowSection = allowContainer.getParent().getElement();
         allowSection.getStyle().setProperty("display", allowMode ? "block" : "none");
      }
      
      // Find deny list section  
      String denyListType = settingPrefix + "_deny_list";
      FlowPanel denyContainer = automationListContainers_.get(denyListType);
      if (denyContainer != null) {
         com.google.gwt.dom.client.Element denySection = denyContainer.getParent().getElement();
         denySection.getStyle().setProperty("display", allowMode ? "none" : "block");
      }
   }
   
   private String getAllowAnythingMessage(String settingName, boolean allowAnythingEnabled) {
      String action = "";
      String allowMode = "";
      String denyMode = "";
      
      if ("auto_accept_console".equals(settingName)) {
         action = "auto-run console commands";
         allowMode = "auto run only the allow list";
         denyMode = "auto run everything except the deny list";
      } else if ("auto_accept_terminal".equals(settingName)) {
         action = "auto-run terminal commands";
         allowMode = "auto run only the allow list";
         denyMode = "auto run everything except the deny list";
      } else if ("auto_run_files".equals(settingName)) {
         action = "auto-run code from files";
         allowMode = "auto run only the allow list";
         denyMode = "auto run everything except the deny list";
      } else {
         // Fallback
         action = "auto-run commands";
         allowMode = "auto run only the allow list";
         denyMode = "auto run everything except the deny list";
      }
      
      String currentMode = allowAnythingEnabled ? denyMode : allowMode;
      
      return "Only " + action + " on the allow list or " + action.replace("auto-run", "auto run") + 
             " except those on the deny list. You are currently on: <b>" + currentMode + "</b>.";
   }

}