/**
 * PostHog helper functions for RStudio
 */

// Initialize PostHog according to security mode when the page loads
(function() {
  // Wait for PostHog to be available
  function waitForPostHog() {
    if (window.posthog) {
      initializePostHogFromSettings();
    } else {
      setTimeout(waitForPostHog, 100);
    }
  }
  
  function initializePostHogFromSettings() {
    // Check if we have access to sendRPC function (web interface)
    if (typeof window.sendRPC === 'function') {
      window.sendRPC('get_security_mode', {}, function(response) {
        if (response && typeof response === 'string') {
          var securityMode = response.replace(/"/g, ''); // Remove quotes
          updatePostHogForSecurityMode(securityMode);
        } else {
          // Default to secure mode if we can't determine the setting
          updatePostHogForSecurityMode('secure');
        }
      });
    } else {
      // For Electron, the preload script handles initialization
      // Default to secure mode for other contexts
      updatePostHogForSecurityMode('secure');
    }
  }
  
  function updatePostHogForSecurityMode(securityMode) {
    if (window.posthog) {
      if (securityMode === 'secure') {
        window.posthog.opt_out_capturing();
      } else {
        window.posthog.opt_in_capturing();
      }
    }
  }
  
  // Start the initialization process
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', waitForPostHog);
  } else {
    waitForPostHog();
  }
})();

// Make these helper functions available to the GWT application
window.PostHogHelper = {
  /**
   * Capture an event
   * @param {string} eventName - Name of the event to capture
   * @param {Object} [properties] - Optional properties to send with the event
   */
  trackEvent: function(eventName, properties) {
    if (window.posthog && !window.posthog.has_opted_out_capturing()) {
      window.posthog.capture(eventName, properties);
    } else if (!window.posthog) {
      console.error('PostHog not initialized');
    }
  },

  /**
   * Identify a user
   * @param {string} userId - Unique identifier for the user
   * @param {Object} [traits] - Optional user properties
   */
  identifyUser: function(userId, traits) {
    if (window.posthog && !window.posthog.has_opted_out_capturing()) {
      window.posthog.identify(userId, traits);
    } else if (!window.posthog) {
      console.error('PostHog not initialized');
    }
  },

  /**
   * Disable PostHog tracking (for secure mode)
   */
  disableTracking: function() {
    if (window.posthog) {
      window.posthog.opt_out_capturing();
    }
  },

  /**
   * Enable PostHog tracking (for improve mode)
   */
  enableTracking: function() {
    if (window.posthog) {
      window.posthog.opt_in_capturing();
    }
  },

  /**
   * Check if tracking is currently enabled
   * @returns {boolean} True if tracking is enabled, false if disabled
   */
  isTrackingEnabled: function() {
    if (window.posthog) {
      return !window.posthog.has_opted_out_capturing();
    }
    return false;
  },

  /**
   * Update PostHog tracking based on security mode
   * @param {string} securityMode - Either "secure" or "improve"
   */
  updateTrackingForSecurityMode: function(securityMode) {
    if (securityMode === 'secure') {
      this.disableTracking();
    } else {
      this.enableTracking();
    }
    
    // If running in Electron, notify the main process about the security mode change
    if (window.desktopBridge && window.desktopBridge.consoleLog) {
      window.desktopBridge.consoleLog('PostHog security mode changed to: ' + securityMode);
    }
  }
}; 