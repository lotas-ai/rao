/*
 * AiReferralSummary.java
 *
 * Copyright (C) 2025 by Lotas Inc.
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 */

package org.rstudio.studio.client.workbench.views.ai.model;

import com.google.gwt.core.client.JavaScriptObject;

public class AiReferralSummary extends JavaScriptObject
{
   protected AiReferralSummary() {}
   
   public final native boolean getIsEligible() /*-{
      var eligible = this.is_eligible;
      var value = Array.isArray(eligible) ? eligible[0] : eligible;
      return value === true || value === "true";
   }-*/;
   
   public final native String getStatus() /*-{
      var status = this.status;
      return Array.isArray(status) ? status[0] : status;
   }-*/;
   
   public final native String getReferralCode() /*-{
      var code = this.code;
      return Array.isArray(code) ? code[0] : code;
   }-*/;
   
   public final native String getReferralUrl() /*-{
      // Backend returns full_url, not referral_url
      var url = this.full_url || this.referral_url;
      return Array.isArray(url) ? url[0] : url;
   }-*/;
   
   public final native int getSuccessfulRedemptions() /*-{
      var redemptions = this.successful_redemptions;
      var value = Array.isArray(redemptions) ? redemptions[0] : redemptions;
      return value || 0;
   }-*/;
   
   public final native int getMaxRedemptions() /*-{
      var max = this.max_redemptions;
      var value = Array.isArray(max) ? max[0] : max;
      return value || 0;
   }-*/;
   
   public final native String getIneligibilityReason() /*-{
      var reason = this.ineligibility_reason;
      return Array.isArray(reason) ? reason[0] : reason;
   }-*/;
}

