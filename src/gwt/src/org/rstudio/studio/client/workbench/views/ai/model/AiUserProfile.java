/*
 * AiUserProfile.java
 *
 * Copyright (C) 2025 by William Nickols
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 */

package org.rstudio.studio.client.workbench.views.ai.model;

import com.google.gwt.core.client.JavaScriptObject;

public class AiUserProfile extends JavaScriptObject
{
   protected AiUserProfile() {}
   
   public final native String getId() /*-{
      if (this.id === null || this.id === undefined) return null;
      return Array.isArray(this.id) ? this.id[0] : this.id;
   }-*/;
   
   public final native String getEmail() /*-{
      if (this.email === null || this.email === undefined) return null;
      return Array.isArray(this.email) ? this.email[0] : this.email;
   }-*/;
   
   public final native String getName() /*-{
      if (this.name === null || this.name === undefined) return null;
      return Array.isArray(this.name) ? this.name[0] : this.name;
   }-*/;
   
   public final native String getUsername() /*-{
      return Array.isArray(this.username) ? this.username[0] : this.username;
   }-*/;
   
   public final native String getSubscriptionStatus() /*-{
      return Array.isArray(this.subscription_status) ? this.subscription_status[0] : this.subscription_status;
   }-*/;
   
   public final native int getMonthlyQueryCount() /*-{
      var count = Array.isArray(this.monthly_query_count) ? this.monthly_query_count[0] : this.monthly_query_count;
      return parseInt(count) || 0;
   }-*/;
   
   public final native int getTrialQueriesUsed() /*-{
      var count = Array.isArray(this.trial_queries_used) ? this.trial_queries_used[0] : this.trial_queries_used;
      return parseInt(count) || 0;
   }-*/;
   
   public final native String getTrialStartDate() /*-{
      return Array.isArray(this.trial_start_date) ? this.trial_start_date[0] : this.trial_start_date;
   }-*/;
   
   public final native String getCurrentPeriodStart() /*-{
      return Array.isArray(this.current_period_start) ? this.current_period_start[0] : this.current_period_start;
   }-*/;
   
   public final native String getCurrentPeriodEnd() /*-{
      return Array.isArray(this.current_period_end) ? this.current_period_end[0] : this.current_period_end;
   }-*/;
   
   public final native boolean getUsageBasedBillingEnabled() /*-{
      var enabled = Array.isArray(this.usage_based_billing_enabled) ? this.usage_based_billing_enabled[0] : this.usage_based_billing_enabled;
      return enabled === true || enabled === "true" || enabled === "TRUE";
   }-*/;
   
   public final native String getCreatedAt() /*-{
      return Array.isArray(this.created_at) ? this.created_at[0] : this.created_at;
   }-*/;
   
   public final native String getError() /*-{
      return this.error;
   }-*/;
} 