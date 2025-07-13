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
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArrayString;
import org.rstudio.core.client.Debug;

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
      void onModelChange(String model);
      void onWorkingDirectoryChange(String directory);
      void onBrowseDirectory();
      void onTemperatureChange(double temperature);
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
   private Label userNameLabel_;
   private HTML subscriptionStatusLabel_;
   private ListBox modelSelect_;
   private com.google.gwt.user.client.ui.HTML temperatureSlider_;
   private TextBox temperatureInput_;
   private Label temperatureLabel_;
   private TextBox workingDirectoryInput_;
   private Button browseDirectoryButton_;
   private Button setDirectoryButton_;
   private Label profileErrorLabel_;
   private Label directorySuccessLabel_;
   private Label directoryErrorLabel_;
   private HTML profileSection_;
   private HTML modelSection_;
   private HTML workingDirectorySection_;
   
   // State
   private boolean hasApiKey_ = false;
   private String currentModel_ = null;
   private String currentDirectory_ = null;
   private double currentTemperature_ = 0.7; // Default temperature
   private AiUserProfile userProfile_ = null;
   private AiSubscriptionStatus subscriptionStatus_ = null;
   private boolean subscriptionDetailsAdded_ = false;
   
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
      
      // Model Section
      modelSection_ = new HTML();
      modelSection_.addStyleName(styles_.settingsSection());
      mainPanel.add(modelSection_);
      
      // Working Directory Section
      workingDirectorySection_ = new HTML();
      workingDirectorySection_.addStyleName(styles_.settingsSection());
      mainPanel.add(workingDirectorySection_);
      
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
      
      // Section title with name on the right - using FlowPanel with CSS Grid
      FlowPanel titleContainer = new FlowPanel();
      titleContainer.setWidth("100%");
      titleContainer.addStyleName(styles_.profileTitlePanel());
      
      Label title = new Label("Profile");
      title.addStyleName(styles_.sectionTitle());
      titleContainer.add(title);
      
      userNameLabel_ = new Label();
      userNameLabel_.addStyleName(styles_.profileName());
      titleContainer.add(userNameLabel_);
      
      section.add(titleContainer);
      
      if (!hasApiKey_) {
         // API Key input section
         HorizontalPanel keyInputPanel = new HorizontalPanel();
         keyInputPanel.setWidth("100%");
         keyInputPanel.addStyleName(styles_.settingRow());
         
         VerticalPanel keyInputContainer = new VerticalPanel();
         keyInputContainer.setWidth("100%");
         
         Label keyLabel = new Label("API Key");
         keyLabel.addStyleName(styles_.settingLabel());
         keyInputContainer.add(keyLabel);
         
         apiKeyInput_ = new PasswordTextBox();
         apiKeyInput_.addStyleName(styles_.settingInput());
         apiKeyInput_.getElement().setAttribute("placeholder", "Enter your Rao API key from www.lotas.ai/account");
         keyInputContainer.add(apiKeyInput_);
         
         saveApiKeyButton_ = new Button("Save API Key");
         saveApiKeyButton_.addStyleName(styles_.settingButton());
         saveApiKeyButton_.addStyleName(styles_.primaryButton());
         
         // Add native DOM click event listener (same pattern as console/terminal/edit file widgets)
         addNativeClickHandler(saveApiKeyButton_.getElement(), "Save API Key");
         keyInputContainer.add(saveApiKeyButton_);
         
         keyInputPanel.add(keyInputContainer);
         section.add(keyInputPanel);
         
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
         
         // Add native DOM click event listener (same pattern as console/terminal/edit file widgets)
         addNativeClickHandler(deleteApiKeyButton_.getElement(), "Sign out");
         signOutContainer.add(deleteApiKeyButton_);
         profileInfo.add(signOutContainer);
         
         section.add(profileInfo);
      }
      
      // Error message label
      profileErrorLabel_ = new Label();
      profileErrorLabel_.addStyleName(styles_.errorMessage());
      profileErrorLabel_.setVisible(false);
      section.add(profileErrorLabel_);
      
      profileSection_.getElement().setInnerHTML("");
      profileSection_.getElement().appendChild(section.getElement());
   }
   
   private void buildModelSection()
   {
      VerticalPanel section = new VerticalPanel();
      section.setWidth("100%");
      
      // Section title
      Label title = new Label("Model");
      title.addStyleName(styles_.sectionTitle());
      section.add(title);
      
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
         section.add(modelPanel);
         
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
         temperatureInput_.getElement().setAttribute("placeholder", "0.7");
         
         // Add native event handlers for input
         addNativeInputChangeHandler(temperatureInput_.getElement());
         
         sliderInputPanel.add(temperatureInput_);
         
         temperatureContainer.add(sliderInputPanel);
         temperaturePanel.add(temperatureContainer);
         section.add(temperaturePanel);
         
         // Load available models
         loadAvailableModels();
      } else {
         Label noKeyLabel = new Label("Please add your API key first to select a model.");
         noKeyLabel.addStyleName(styles_.settingLabel());
         section.add(noKeyLabel);
      }
      
      modelSection_.getElement().setInnerHTML("");
      modelSection_.getElement().appendChild(section.getElement());
   }
   
   private void buildWorkingDirectorySection()
   {
      VerticalPanel section = new VerticalPanel();
      section.setWidth("100%");
      
      // Section title
      Label title = new Label("Working Directory");
      title.addStyleName(styles_.sectionTitle());
      section.add(title);
      
      Label description = new Label("Setting a narrow working directory helps Rao understand your project context better.");
      description.addStyleName(styles_.settingLabel());
      section.add(description);
      
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
      
      // Add native DOM click event listener (same pattern as console/terminal/edit file widgets)
      addNativeClickHandler(browseDirectoryButton_.getElement(), "Browse...");
      inputPanel.add(browseDirectoryButton_);
      
      directoryContainer.add(inputPanel);
      
      // Set Directory button below
      setDirectoryButton_ = new Button("Set Directory");
      setDirectoryButton_.addStyleName(styles_.settingButton());
      setDirectoryButton_.addStyleName(styles_.primaryButton());
      setDirectoryButton_.getElement().getStyle().setProperty("marginTop", "8px");
      
      // Add native DOM click event listener (same pattern as console/terminal/edit file widgets)
      addNativeClickHandler(setDirectoryButton_.getElement(), "Set Directory");
      directoryContainer.add(setDirectoryButton_);
      section.add(directoryContainer);
      
      // Success/Error messages
      directorySuccessLabel_ = new Label();
      directorySuccessLabel_.addStyleName(styles_.successMessage());
      directorySuccessLabel_.setVisible(false);
      section.add(directorySuccessLabel_);
      
      directoryErrorLabel_ = new Label();
      directoryErrorLabel_.addStyleName(styles_.errorMessage());
      directoryErrorLabel_.setVisible(false);
      section.add(directoryErrorLabel_);
      
      workingDirectorySection_.getElement().setInnerHTML("");
      workingDirectorySection_.getElement().appendChild(section.getElement());
   }
   
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
            Debug.log("Error loading user profile: " + error.getMessage());
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
            currentTemperature_ = temperature != null ? temperature : 0.7;
            updateTemperatureDisplay();
         }
         
         @Override
         public void onError(ServerError error) {
            Debug.log("Error loading temperature: " + error.getMessage());
            currentTemperature_ = 0.7; // Default value
            updateTemperatureDisplay();
         }
      });
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
      buildModelSection();
      buildWorkingDirectorySection();
      
      // Ensure directory input is populated after UI is built
      updateWorkingDirectorySection();
   }
   
   private void updateProfileSection()
   {
      if (userNameLabel_ != null && userProfile_ != null) {
         // Use the name from the profile (already formatted as full name)
         String name = userProfile_.getName();
         userNameLabel_.setText(name != null ? name : "Loading...");
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
            return "Trial";
         case "active":
            return "Active";
         case "past_due":
            return "Past Due";
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
         
         // Auto-hide after 3 seconds
         Timer timer = new Timer() {
            @Override
            public void run() {
               directorySuccessLabel_.setVisible(false);
            }
         };
         timer.schedule(3000);
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
      showDirectorySuccess("Directory updated successfully");
   }
   
   public void onApiKeySaved()
   {
      hasApiKey_ = true;
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
   
   // Add native DOM event handler using JSNI (same pattern as console/terminal/edit file widgets)
   private native void addNativeChangeHandler(com.google.gwt.dom.client.Element element) /*-{
      var self = this;
      
      element.addEventListener('change', function(event) {
         self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleModelChange()();
         event.preventDefault();
         event.stopPropagation();
      }, true); // Use capture phase
   }-*/;
   
   // Add native DOM event handler for temperature slider
   private native void addNativeSliderChangeHandler(com.google.gwt.dom.client.Element element) /*-{
      var self = this;
      
      element.addEventListener('input', function(event) {
         self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleSliderChange()();
         event.preventDefault();
         event.stopPropagation();
      }, true); // Use capture phase
   }-*/;
   
   // Add native DOM event handler for temperature input
   private native void addNativeInputChangeHandler(com.google.gwt.dom.client.Element element) /*-{
      var self = this;
      
      // Handle blur event (when field loses focus)
      element.addEventListener('blur', function(event) {
         self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleInputChange()();
         event.preventDefault();
         event.stopPropagation();
      }, true);
      
      // Handle Enter key
      element.addEventListener('keydown', function(event) {
         if (event.key === 'Enter' || event.keyCode === 13) {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleInputChange()();
            event.preventDefault();
            event.stopPropagation();
         }
      }, true);
      }-*/;
   
   // Native method to get slider value
   private native double getSliderValue() /*-{
      if (this.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::temperatureSlider_) {
         var slider = this.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::temperatureSlider_.@com.google.gwt.user.client.ui.HTML::getElement()().firstChild;
         if (slider) {
            return parseFloat(slider.value);
         }
      }
      return 0.7; // Default value
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
         if (buttonText === 'Save API Key') {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleSaveApiKey()();
         } else if (buttonText === 'Sign out') {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleDeleteApiKey()();
         } else if (buttonText === 'Browse...') {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleBrowseDirectory()();
         } else if (buttonText === 'Set Directory') {
            self.@org.rstudio.studio.client.workbench.views.ai.widgets.AiSettingsWidget::handleSetDirectory()();
         }
         
         event.preventDefault();
         event.stopPropagation();
      }, true); // Use capture phase
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
}