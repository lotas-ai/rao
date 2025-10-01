/*
 * AiSettingsWidget.java
 *
 * Copyright (C) 2025 by William Nickols
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
import org.rstudio.studio.client.workbench.views.ai.model.AiUserProfile;
import org.rstudio.studio.client.workbench.views.ai.model.AiSubscriptionStatus;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.application.events.EventBus;
import com.google.gwt.user.client.Timer;

public class AiSettingsWidget extends Composite
{
   public interface SettingsHandler
   {
      void onSaveApiKey(String apiKey);
      void onDeleteApiKey();
      void onSignInWithWebsite();
      void onModelChange(String model);
      void onWorkingDirectoryChange(String directory);
      void onBrowseDirectory();
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
   
   // State
   private boolean hasApiKey_ = false;
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
   
   // Automation toggle widgets
   private HTML autoAcceptEditsToggle_;
   private HTML autoAcceptConsoleToggle_;
   private HTML autoAcceptTerminalToggle_;
   private HTML autoRunFilesToggle_;
   private HTML autoDeleteFilesToggle_;
   
   // Map to store FlowPanel references for automation lists
   private Map<String, FlowPanel> automationListContainers_ = new HashMap<String, FlowPanel>();
   
   public AiSettingsWidget(SettingsHandler handler, 
                          AiServerOperations server, 
                          EventBus eventBus,
                          GlobalDisplay globalDisplay)
   {
      handler_ = handler;
      server_ = server;
      eventBus_ = eventBus;
      globalDisplay_ = globalDisplay;
      
      initWidget(createWidget());
      addStyleName(styles_.settingsContainer());
      
      // Load initial data
      loadUserProfile();
      loadSubscriptionStatus();
      loadCurrentSettings();
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
      
      // Working Directory Section
      workingDirectorySection_ = new HTML();
      workingDirectorySection_.addStyleName(styles_.settingsSection());
      mainPanel.add(workingDirectorySection_);
      
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
      scrollPanel.addStyleName("ace_editor"); // Use standard RStudio scrollable styling
      scrollPanel.addStyleName("ace_scroller");
      
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
               "<div style='width: 20px; height: 20px; background: transparent; border: 1px solid #ccc; border-radius: 3px; display: flex; align-items: center; justify-content: center; cursor: pointer; transition: border-color 0.2s ease;' " +
               "onmouseover='this.style.borderColor=\"#999\"' onmouseout='this.style.borderColor=\"#ccc\"' onclick='window.handleProfileChevronClick && window.handleProfileChevronClick();'>" +
                  "<svg width='10' height='12' viewBox='0 0 10 12' style='flex-shrink: 0;'>" +
                     "<path d='M2 4L5 2L8 4' stroke='#666' stroke-width='1.2' fill='none' stroke-linecap='round' stroke-linejoin='round'/>" +
                     "<path d='M2 8L5 10L8 8' stroke='#666' stroke-width='1.2' fill='none' stroke-linecap='round' stroke-linejoin='round'/>" +
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
      
      if (!hasApiKey_) {
         // Sign in button section - use a container with proper alignment
         VerticalPanel signInContainer = new VerticalPanel();
         signInContainer.setWidth("100%");
         signInContainer.addStyleName(styles_.settingRow());
         
         HorizontalPanel buttonPanel = new HorizontalPanel();
         buttonPanel.getElement().getStyle().setProperty("display", "flex");
         buttonPanel.getElement().getStyle().setProperty("alignItems", "center");
         
         // Sign In button
         signInButton_ = new Button("Sign up/Sign in");
         signInButton_.addStyleName(styles_.settingButton());
         signInButton_.addStyleName(styles_.primaryButton());
         signInButton_.getElement().getStyle().setProperty("marginRight", "8px");
         addNativeClickHandler(signInButton_.getElement(), "Sign in");
         
         // Options button (...)
         optionsButton_ = new Button("Use API key");
         optionsButton_.addStyleName(styles_.settingButton());
         optionsButton_.addStyleName(styles_.primaryButton());
         optionsButton_.getElement().getStyle().setProperty("flexShrink", "0");
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
         
      } else {
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
      }
      
      // Error message label
      profileErrorLabel_ = new Label();
      profileErrorLabel_.addStyleName(styles_.errorMessage());
      profileErrorLabel_.setVisible(false);
      contentPanel.add(profileErrorLabel_);
      
      // Prompt message label - show after API key is saved
      directoryPromptLabel_ = new Label("Please set a working directory below to use Rao. Once done, start a new conversation with the + button in the top left.");
      directoryPromptLabel_.addStyleName(styles_.successMessage());
      directoryPromptLabel_.setVisible(shouldShowDirectoryPrompt_);
      contentPanel.add(directoryPromptLabel_);
      
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
      
      if (hasApiKey_) {
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
         temperatureDescription.addStyleName(styles_.settingLabel());
         temperatureDescription.getElement().getStyle().setProperty("fontWeight", "normal");
         temperatureDescription.getElement().getStyle().setProperty("fontSize", "13px");
         temperatureDescription.getElement().getStyle().setProperty("color", "#666666");
         temperatureDescription.getElement().getStyle().setProperty("marginBottom", "8px");
         temperatureContainer.add(temperatureDescription);
         
         // Container for slider and input using same pattern as working directory
         FlowPanel sliderInputPanel = new FlowPanel();
         sliderInputPanel.setWidth("100%");
         sliderInputPanel.addStyleName(styles_.temperatureRow());
         
         // HTML5 range slider
         temperatureSlider_ = new HTML();
         temperatureSlider_.getElement().setInnerHTML(
            "<input type='range' min='0' max='1' step='0.1' value='" + currentTemperature_ + "' style='width: 100%;' />"
         );
         temperatureSlider_.addStyleName(styles_.settingInput());
         
         // Add native event handlers for slider
         addNativeSliderChangeHandler(temperatureSlider_.getElement().getFirstChildElement());
         
         sliderInputPanel.add(temperatureSlider_);
         
         // Numeric input box
         temperatureInput_ = new TextBox();
         temperatureInput_.setValue(String.valueOf(currentTemperature_));
         temperatureInput_.setWidth("60px");
         temperatureInput_.addStyleName(styles_.settingInput());
         temperatureInput_.getElement().setAttribute("placeholder", "0.5");
         
         // Add native event handlers for input
         addNativeInputChangeHandler(temperatureInput_.getElement());
         
         sliderInputPanel.add(temperatureInput_);
         
         temperatureContainer.add(sliderInputPanel);
         temperaturePanel.add(temperatureContainer);
         contentPanel.add(temperaturePanel);
         
         // Load available models
         loadAvailableModels();
      } else {
         Label noKeyLabel = new Label("Please add your API key first to select a model.");
         noKeyLabel.addStyleName(styles_.settingLabel());
         contentPanel.add(noKeyLabel);
      }
      
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
   
   private void buildWorkingDirectorySection()
   {
      VerticalPanel section = new VerticalPanel();
      section.setWidth("100%");
      
      // Section header
      HorizontalPanel headerPanel = createSectionHeader("Working Directory", "workingDirectory", workingDirectorySectionExpanded_);
      section.add(headerPanel);
      
      // Add chevron button positioned absolutely on the right
      HTML chevronButton = createChevronButton("workingDirectory", workingDirectorySectionExpanded_);
      section.add(chevronButton);
      
      // Content section (collapsible)
      VerticalPanel contentPanel = new VerticalPanel();
      contentPanel.setWidth("100%");
      contentPanel.addStyleName(styles_.sectionContent());
      if (!workingDirectorySectionExpanded_) {
         contentPanel.addStyleName(styles_.sectionContentCollapsed());
      }
      
      Label description = new Label("Setting a narrow working directory helps Rao understand your project context better.");
      description.addStyleName(styles_.settingLabel());
      contentPanel.add(description);
      
      // Directory input row
      VerticalPanel directoryContainer = new VerticalPanel();
      directoryContainer.setWidth("100%");
      directoryContainer.addStyleName(styles_.settingRow());
      
      HTML directoryLabel = new HTML("<b>Current directory</b>");
      directoryLabel.addStyleName(styles_.settingLabel());
      directoryContainer.add(directoryLabel);
      
      FlowPanel inputPanel = new FlowPanel();
      inputPanel.setWidth("100%");
      inputPanel.addStyleName(styles_.directoryRow());
      
      workingDirectoryInput_ = new TextBox();
      workingDirectoryInput_.addStyleName(styles_.settingInput());
      workingDirectoryInput_.getElement().setAttribute("placeholder", "Enter working directory path");
      inputPanel.add(workingDirectoryInput_);
      
      browseDirectoryButton_ = new Button("Browse...");
      browseDirectoryButton_.addStyleName(styles_.settingButton());
      browseDirectoryButton_.addStyleName(styles_.secondaryButton());
      
      // Add native DOM click event listener (same pattern as console/terminal widgets)
      addNativeClickHandler(browseDirectoryButton_.getElement(), "Browse...");
      inputPanel.add(browseDirectoryButton_);
      
      directoryContainer.add(inputPanel);
      
      // Set Directory button below
      setDirectoryButton_ = new Button("Set Directory");
      setDirectoryButton_.addStyleName(styles_.settingButton());
      setDirectoryButton_.addStyleName(styles_.primaryButton());
      setDirectoryButton_.addStyleName(styles_.compactButton());
      setDirectoryButton_.getElement().getStyle().setProperty("marginTop", "8px");
      
      // Add native DOM click event listener (same pattern as console/terminal widgets)
      addNativeClickHandler(setDirectoryButton_.getElement(), "Set Directory");
      directoryContainer.add(setDirectoryButton_);
      contentPanel.add(directoryContainer);
      
      // Success/Error messages
      directorySuccessLabel_ = new Label();
      directorySuccessLabel_.addStyleName(styles_.successMessage());
      directorySuccessLabel_.setVisible(false);
      contentPanel.add(directorySuccessLabel_);
      
      directoryErrorLabel_ = new Label();
      directoryErrorLabel_.addStyleName(styles_.errorMessage());
      directoryErrorLabel_.setVisible(false);
      contentPanel.add(directoryErrorLabel_);
      
      // Add content panel to section
      section.add(contentPanel);
      
      workingDirectorySection_.getElement().setInnerHTML("");
      workingDirectorySection_.getElement().appendChild(section.getElement());
      
      // Apply collapsed class if section is collapsed
      if (!workingDirectorySectionExpanded_) {
         workingDirectorySection_.addStyleName(styles_.collapsed());
         // Immediately apply the visual collapse using JavaScript
         applyImmediateCollapse(workingDirectorySection_.getElement());
      } else {
         workingDirectorySection_.removeStyleName(styles_.collapsed());
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
      
      // Button panel positioned absolutely at bottom right
      HorizontalPanel buttonPanel = new HorizontalPanel();
      buttonPanel.addStyleName(styles_.buttonPanel());
      
      saveNewRuleButton_ = new Button("Save");
      saveNewRuleButton_.addStyleName(styles_.lightGrayButton());
      addNativeClickHandler(saveNewRuleButton_.getElement(), "Save");
      buttonPanel.add(saveNewRuleButton_);
      
      cancelNewRuleButton_ = new Button("Cancel");
      cancelNewRuleButton_.addStyleName(styles_.lightGrayButton());
      cancelNewRuleButton_.getElement().getStyle().setProperty("marginLeft", "4px");
      addNativeClickHandler(cancelNewRuleButton_.getElement(), "Cancel");
      buttonPanel.add(cancelNewRuleButton_);
      
      inputContainer.add(buttonPanel);
      newRulePanel_.add(inputContainer);
      contentPanel.add(newRulePanel_);
      
      // Build the rules list
      buildRulesList();
      
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
      
      // Button panel positioned absolutely at bottom right
      HorizontalPanel buttonPanel = new HorizontalPanel();
      buttonPanel.addStyleName(styles_.buttonPanel());
      
      Button saveButton = new Button("Save");
      saveButton.addStyleName(styles_.lightGrayButton());
      
      // Store the edit input globally for access in handler
      storeEditInput(ruleIndex, editInput);
      
      // Use the SAME working pattern as all other buttons
      addNativeClickHandler(saveButton.getElement(), "EditSave-" + ruleIndex);
      buttonPanel.add(saveButton);
      
      Button cancelButton = new Button("Cancel");
      cancelButton.addStyleName(styles_.lightGrayButton());
      cancelButton.getElement().getStyle().setProperty("marginLeft", "4px");
      
      // Use the SAME working pattern as all other buttons
      addNativeClickHandler(cancelButton.getElement(), "EditCancel-" + ruleIndex);
      buttonPanel.add(cancelButton);
      
      editContainer.add(buttonPanel);
      ruleContainer.add(editContainer);
   }
   
   private void storeEditInput(int ruleIndex, TextArea editInput) {
      editInputs_.put(ruleIndex, editInput);
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
      securityModeText_.addStyleName(styles_.settingLabel());
      securityModeText_.getElement().getStyle().setProperty("fontWeight", "normal");
      securityModeText_.getElement().getStyle().setProperty("fontSize", "13px");
      securityModeText_.getElement().getStyle().setProperty("color", "#666666");
      securityModeText_.getElement().getStyle().setProperty("marginRight", "15px");
      securityModeText_.setWidth("100%");
      securityTogglePanel.add(securityModeText_);
      securityTogglePanel.setCellWidth(securityModeText_, "100%");
      
      securityModeToggle_ = new HTML();
      securityModeToggle_.getElement().setInnerHTML(
         "<div style='position: relative; width: 32px; height: 16px; background: #4CAF50; border-radius: 8px; cursor: pointer; transition: background 0.3s; display: none;' data-setting='security_mode'>" +
         "<div style='position: absolute; top: 1px; right: 1px; width: 14px; height: 14px; background: white; border-radius: 50%; transition: left 0.3s, right 0.3s; box-shadow: 0 1px 2px rgba(0,0,0,0.2);'></div>" +
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
      webSearchText_.addStyleName(styles_.settingLabel());
      webSearchText_.getElement().getStyle().setProperty("fontWeight", "normal");
      webSearchText_.getElement().getStyle().setProperty("fontSize", "13px");
      webSearchText_.getElement().getStyle().setProperty("color", "#666666");
      webSearchText_.getElement().getStyle().setProperty("marginRight", "15px");
      webSearchText_.setWidth("100%");
      webSearchTogglePanel.add(webSearchText_);
      webSearchTogglePanel.setCellWidth(webSearchText_, "100%");
      
      webSearchToggle_ = new HTML();
      webSearchToggle_.getElement().setInnerHTML(
         "<div style='position: relative; width: 32px; height: 16px; background: #ccc; border-radius: 8px; cursor: pointer; transition: background 0.3s; display: none;' data-setting='web_search'>" +
         "<div style='position: absolute; top: 1px; left: 1px; width: 14px; height: 14px; background: white; border-radius: 50%; transition: left 0.3s; box-shadow: 0 1px 2px rgba(0,0,0,0.2);'></div>" +
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
         "<div style='position: relative; width: 32px; height: 16px; background: #ccc; border-radius: 8px; cursor: pointer; transition: background 0.3s; display: none;' data-setting='" + settingName + "'>" +
         "<div style='position: absolute; top: 1px; left: 1px; width: 14px; height: 14px; background: white; border-radius: 50%; transition: left 0.3s, right 0.3s; box-shadow: 0 1px 2px rgba(0,0,0,0.2);'></div>" +
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
      panel.getElement().getStyle().setProperty("marginTop", "8px");
      panel.getElement().getStyle().setProperty("marginBottom", "8px");
      panel.getElement().getStyle().setProperty("paddingLeft", "12px");
      panel.getElement().getStyle().setProperty("borderLeft", "2px solid #e0e0e0");
      
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
         "<div style='position: relative; width: 28px; height: 14px; background: #ccc; border-radius: 7px; cursor: pointer; transition: background 0.3s; display: none;' data-setting='" + settingName + "_allow_anything'>" +
         "<div style='position: absolute; top: 1px; left: 1px; width: 12px; height: 12px; background: white; border-radius: 50%; transition: left 0.3s, right 0.3s; box-shadow: 0 1px 2px rgba(0,0,0,0.2);'></div>" +
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
      
      // Create item container similar to context items
      FlowPanel itemContainer = new FlowPanel();
      itemContainer.getElement().getStyle().setProperty("display", "inline-flex");
      itemContainer.getElement().getStyle().setProperty("alignItems", "center");
      itemContainer.getElement().getStyle().setProperty("backgroundColor", "white");
      itemContainer.getElement().getStyle().setProperty("border", "1px solid #cccccc");
      itemContainer.getElement().getStyle().setProperty("borderRadius", "3px");
      itemContainer.getElement().getStyle().setProperty("padding", "2px 6px");
      itemContainer.getElement().getStyle().setProperty("margin", "0 4px 4px 0");
      itemContainer.getElement().getStyle().setProperty("fontSize", "12px");
      
      // Text label
      Label textLabel = new Label(text);
      textLabel.getElement().getStyle().setProperty("marginRight", "4px");
      itemContainer.add(textLabel);
      
      // Remove button
      Label removeButton = new Label("×");
      removeButton.getElement().getStyle().setProperty("cursor", "pointer");
      removeButton.getElement().getStyle().setProperty("color", "#999999");
      removeButton.getElement().getStyle().setProperty("fontWeight", "bold");
      removeButton.getElement().getStyle().setProperty("fontSize", "14px");
      removeButton.getElement().getStyle().setProperty("width", "12px");
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
            updateAllSections();
         }
         
         @Override
         public void onError(ServerError error) {
            hasApiKey_ = false;
            updateAllSections();
         }
      });
      
      // Load current working directory
      server_.getCurrentWorkingDirectory(new ServerRequestCallback<String>() {
         @Override
         public void onResponseReceived(String directory) {
            currentDirectory_ = directory;
            updateWorkingDirectorySection();
         }
         
         @Override
         public void onError(ServerError error) {
            currentDirectory_ = System.getProperty("user.home", "");
            updateWorkingDirectorySection();
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
               updateModelDropdown(modelArray);
            } else {
               showError("No models available");
            }
         }
         
         @Override
         public void onError(ServerError error) {
            Debug.log("Error loading models: " + error.getMessage());
            showError("Failed to load models: " + error.getMessage());
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
      } else {
         Debug.log("currentModel_ is null: " + (currentModel_ == null) + ", modelSelect_ is null: " + (modelSelect_ == null));
      }
   }
   
   private void updateAllSections()
   {
      buildProfileSection();
      buildWorkingDirectorySection();
      buildRulesSection();
      buildSecuritySection();
      buildAutomationSection();
      buildModelSection();
      
      // Ensure directory input is populated after UI is built
      updateWorkingDirectorySection();
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
   
   private void updateWorkingDirectorySection()
   {
      if (workingDirectoryInput_ != null && currentDirectory_ != null) {
         workingDirectoryInput_.setValue(currentDirectory_);
      }
   }
   
   private String formatSubscriptionStatus(String status)
   {
      if (status == null) return "Unknown";
      
      switch (status.toLowerCase()) {
         case "trial":
            return "Free Tier";
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
      usageBarContainer.setWidth("100%");
      usageBarContainer.getElement().getStyle().setProperty("backgroundColor", "#f0f0f0");
      usageBarContainer.getElement().getStyle().setProperty("borderRadius", "4px");
      usageBarContainer.getElement().getStyle().setProperty("height", "8px");
      usageBarContainer.getElement().getStyle().setProperty("marginTop", "4px");
      usageBarContainer.getElement().getStyle().setProperty("overflow", "hidden");
      
      // Usage bar fill
      HTML usageBarFill = new HTML();
      double usagePercent = Math.min(100.0, Math.max(0.0, (double) monthlyUsed / monthlyLimit * 100.0));
      usageBarFill.setWidth(usagePercent + "%");
      usageBarFill.getElement().getStyle().setProperty("height", "100%");
      usageBarFill.getElement().getStyle().setProperty("backgroundColor", "#28a745");
      usageBarFill.getElement().getStyle().setProperty("transition", "width 0.3s ease");
      
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
   
   public void showDirectorySuccess(String message)
   {
      if (directorySuccessLabel_ != null) {
         directorySuccessLabel_.setText(message);
         directorySuccessLabel_.setVisible(true);
         directoryErrorLabel_.setVisible(false);
         
         // Auto-hide after 10 seconds
         Timer timer = new Timer() {
            @Override
            public void run() {
               directorySuccessLabel_.setVisible(false);
            }
         };
         timer.schedule(10000);
      }
   }
   
   public void showDirectoryError(String message)
   {
      if (directoryErrorLabel_ != null) {
         directoryErrorLabel_.setText(message);
         directoryErrorLabel_.setVisible(true);
         directorySuccessLabel_.setVisible(false);
         
         // Auto-hide after 5 seconds
         Timer timer = new Timer() {
            @Override
            public void run() {
               directoryErrorLabel_.setVisible(false);
            }
         };
         timer.schedule(5000);
      }
   }
   
   public void updateDirectoryPath(String path)
   {
      currentDirectory_ = path;
      if (workingDirectoryInput_ != null) {
         workingDirectoryInput_.setValue(path);
      }
      shouldShowDirectoryPrompt_ = false;
      buildProfileSection();
      showDirectorySuccess("Click the top left + button to start a conversation. Directory updated successfully");
   }
   
   public void onApiKeySaved()
   {
      hasApiKey_ = true;
      shouldShowDirectoryPrompt_ = true;
      updateAllSections();
      loadUserProfile();
      loadSubscriptionStatus();
   }
   
   public void onAuthenticationCompleted()
   {
      hasApiKey_ = true;
      shouldShowDirectoryPrompt_ = true;
      updateAllSections();
      loadUserProfile();
      loadSubscriptionStatus();
   }
   
   public void onApiKeyDeleted()
   {
      hasApiKey_ = false;
      userProfile_ = null;
      subscriptionStatus_ = null;
      updateAllSections();
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
            toggleDiv.style.background = isSecure ? '#4CAF50' : '#ccc';
            
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
            toggleDiv.style.background = isEnabled ? '#4CAF50' : '#ccc';
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
            toggleDiv.style.background = isSecure ? '#4CAF50' : '#ccc';
            
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
            toggleDiv.style.background = isEnabled ? '#4CAF50' : '#ccc';
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
            } else if (buttonText === 'Browse...') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleBrowseDirectory()();
            } else if (buttonText === 'Set Directory') {
               self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleSetDirectory()();
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
   
   private void handleBrowseDirectory() {
      handler_.onBrowseDirectory();
   }
   
   private void handleSetDirectory() {
      if (workingDirectoryInput_ != null) {
         String directory = workingDirectoryInput_.getValue();
         if (directory != null && !directory.trim().isEmpty()) {
            handler_.onWorkingDirectoryChange(directory.trim());
         }
      }
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
            if (securityModeToggle_ != null) {
               updateToggleDisplay(securityModeToggle_.getElement(), currentMode, "secure");
            }
            if (securityModeText_ != null) {
               boolean isSecure = "secure".equals(currentMode);
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
      
      // Create double chevron SVG icon with transparent background and border
      String chevronSvg = 
         "<div style='width: 20px; height: 20px; background: transparent; border: 1px solid #ccc; border-radius: 3px; display: flex; align-items: center; justify-content: center; cursor: pointer; transition: border-color 0.2s ease;' " +
         "onmouseover='this.style.borderColor=\"#999\"' onmouseout='this.style.borderColor=\"#ccc\"'>" +
         "<svg width='10' height='12' viewBox='0 0 10 12' style='flex-shrink: 0;'>" +
         "<path d='M2 4L5 2L8 4' stroke='#666' stroke-width='1.2' fill='none' stroke-linecap='round' stroke-linejoin='round'/>" +
         "<path d='M2 8L5 10L8 8' stroke='#666' stroke-width='1.2' fill='none' stroke-linecap='round' stroke-linejoin='round'/>" +
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
         case "workingDirectory":
            return workingDirectorySection_;
         case "rules":
            return rulesSection_;
         case "security":
            return securitySection_;
         case "automation":
            return automationSection_;
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
            console.log('Section expanded');
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
   
   /**
    * Gets the expanded state for a section
    */
   private boolean getSectionExpandedState(String sectionName) {
      switch (sectionName) {
         case "profile":
            return profileSectionExpanded_;
         case "model":
            return modelSectionExpanded_;
         case "workingDirectory":
            return workingDirectorySectionExpanded_;
         case "rules":
            return rulesSectionExpanded_;
         case "security":
            return securitySectionExpanded_;
         case "automation":
            return automationSectionExpanded_;
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
         case "workingDirectory":
            workingDirectorySectionExpanded_ = expanded;
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
      } else {
         Debug.log("Lists container not found for: " + settingPrefix);
      }
   }
   
   private void updateAllowAnythingToggleDisplay(String settingName, boolean enabled) {
      com.google.gwt.dom.client.Element automationElement = automationSection_.getElement();
      com.google.gwt.dom.client.Element toggleElement = findElementByAttribute(automationElement, "data-setting", settingName);
      
      if (toggleElement != null) {
         // Show the toggle now that we have R data
         toggleElement.getStyle().setProperty("display", "block");
         updateAllowAnythingToggleDisplayStyle(toggleElement, enabled);
      } else {
         Debug.log("Toggle element not found for: " + settingName);
      }
      
      // Also update the descriptive label text and show it
      updateAllowAnythingLabelText(settingName, enabled);
   }
   
   // Native method to update allow-anything toggle display (smaller toggles with different dimensions)
   private native void updateAllowAnythingToggleDisplayStyle(com.google.gwt.dom.client.Element element, boolean enabled) /*-{
      element.setAttribute('data-value', enabled ? 'true' : 'false');
      var slider = element.querySelector('div');
      
      // Allow-anything toggles: 28x14px with 12px slider
      element.style.background = enabled ? '#4CAF50' : '#ccc';
      
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
      } else {
         Debug.log("Label element not found for: " + baseSettingName);
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