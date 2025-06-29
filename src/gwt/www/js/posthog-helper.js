/**
 * PostHog helper functions for RStudio
 */

// Make these helper functions available to the GWT application
window.PostHogHelper = {
  /**
   * Capture an event
   * @param {string} eventName - Name of the event to capture
   * @param {Object} [properties] - Optional properties to send with the event
   */
  trackEvent: function(eventName, properties) {
    if (window.posthog) {
      window.posthog.capture(eventName, properties);
    } else {
      console.error('PostHog not initialized');
    }
  },

  /**
   * Identify a user
   * @param {string} userId - Unique identifier for the user
   * @param {Object} [traits] - Optional user properties
   */
  identifyUser: function(userId, traits) {
    if (window.posthog) {
      window.posthog.identify(userId, traits);
    } else {
      console.error('PostHog not initialized');
    }
  }
};

// Track page view automatically
window.PostHogHelper.trackEvent('page_view', {
  page: window.location.pathname
}); 