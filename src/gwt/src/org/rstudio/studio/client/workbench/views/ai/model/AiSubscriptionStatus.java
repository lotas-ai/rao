/*
 * AiSubscriptionStatus.java
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

public class AiSubscriptionStatus extends JavaScriptObject
{
   protected AiSubscriptionStatus() {}
   
   public final native String getSubscriptionStatus() /*-{
      var status = this.subscription_status;
      return Array.isArray(status) ? status[0] : status;
   }-*/;
   
   public final native String getCurrentPeriodStart() /*-{
      var start = this.current_period_start;
      return Array.isArray(start) ? start[0] : start;
   }-*/;
   
   public final native String getCurrentPeriodEnd() /*-{
      var end = this.current_period_end;
      return Array.isArray(end) ? end[0] : end;
   }-*/;
   
   public final native int getQueriesRemaining() /*-{
      var remaining = this.queries_remaining;
      var value = Array.isArray(remaining) ? remaining[0] : remaining;
      return parseInt(value) || 0;
   }-*/;
   
   public final native int getQueriesLimit() /*-{
      var limit = this.queries_limit;
      var value = Array.isArray(limit) ? limit[0] : limit;
      return parseInt(value) || 0;
   }-*/;
   
   public final native int getOverageCount() /*-{
      var overage = this.overage_count;
      var value = Array.isArray(overage) ? overage[0] : overage;
      return parseInt(value) || 0;
   }-*/;
   
   public final native boolean getUsageBasedBillingEnabled() /*-{
      var enabled = this.usage_based_billing_enabled;
      var value = Array.isArray(enabled) ? enabled[0] : enabled;
      return value === true || value === "true";
   }-*/;
   
   public final native double getPendingOverageCents() /*-{
      var cents = this.pending_overage_cents;
      var value = Array.isArray(cents) ? cents[0] : cents;
      return parseFloat(value) || 0;
   }-*/;
   
   public final native boolean getTrialActive() /*-{
      var active = this.trial_active;
      var value = Array.isArray(active) ? active[0] : active;
      return value === true || value === "true";
   }-*/;
   
   public final native String getTrialEndsAt() /*-{
      var ends = this.trial_ends_at;
      return Array.isArray(ends) ? ends[0] : ends;
   }-*/;
   
   public final native int getTrialQueriesUsed() /*-{
      var used = this.trial_queries_used;
      var value = Array.isArray(used) ? used[0] : used;
      return parseInt(value) || 0;
   }-*/;
   
   public final native int getTrialQueriesLimit() /*-{
      var limit = this.trial_queries_limit;
      var value = Array.isArray(limit) ? limit[0] : limit;
      return parseInt(value) || 0;
   }-*/;
} 