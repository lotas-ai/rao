/*
 * PostHog.java
 *
 * Copyright (C) 2022 by Posit Software, PBC
 *
 * Unless you have received this program directly from Posit Software pursuant
 * to the terms of a commercial license agreement with Posit Software, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.core.client.analytics;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;

/**
 * Interface to the PostHog JavaScript API for tracking events and sessions.
 */
public class PostHog
{
   /**
    * Track an event with optional properties.
    * 
    * @param eventName The name of the event to track
    */
   public static void trackEvent(String eventName)
   {
      trackEventImpl(eventName, null);
   }
   
   /**
    * Track an event with optional properties.
    * 
    * @param eventName The name of the event to track
    * @param properties Optional properties to include with the event
    */
   public static void trackEvent(String eventName, JSONObject properties)
   {
      trackEventImpl(eventName, properties.getJavaScriptObject());
   }
   
   /**
    * Identify a user.
    * 
    * @param userId The unique identifier for the user
    */
   public static void identifyUser(String userId)
   {
      identifyUserImpl(userId, null);
   }
   
   /**
    * Identify a user with traits.
    * 
    * @param userId The unique identifier for the user
    * @param traits Properties/traits about the user
    */
   public static void identifyUser(String userId, JSONObject traits)
   {
      identifyUserImpl(userId, traits.getJavaScriptObject());
   }
   
   /**
    * Create a simple properties object with a single key/value pair.
    * 
    * @param key The key
    * @param value The value
    * @return A JSONObject with the key/value pair
    */
   public static JSONObject createProperties(String key, String value)
   {
      JSONObject props = new JSONObject();
      props.put(key, new JSONString(value));
      return props;
   }
   
   private static native void trackEventImpl(String eventName, JavaScriptObject properties) /*-{
      if ($wnd.PostHogHelper) {
         $wnd.PostHogHelper.trackEvent(eventName, properties);
      } else {
         console.error("PostHog helper not available");
      }
   }-*/;
   
   private static native void identifyUserImpl(String userId, JavaScriptObject traits) /*-{
      if ($wnd.PostHogHelper) {
         $wnd.PostHogHelper.identifyUser(userId, traits);
      } else {
         console.error("PostHog helper not available");
      }
   }-*/;
}