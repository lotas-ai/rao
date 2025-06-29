/*
 * AiScrollManager.java
 *
 * Copyright (C) 2025 by William Nickols
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 * 
 * SCROLLING SYSTEM:
 * This class manages automatic scrolling for AI conversation interfaces with the following behavior:
 * 
 * 1. SMART SCROLLING: Only scrolls if user is already near bottom (within 40px or 90% scroll distance)
 *    - smartScrollToBottom() - Used during AI streaming to maintain "sticky bottom" behavior
 *    - If user scrolls up, streaming continues without forcing them back to bottom
 * 
 * 2. FORCE SCROLLING: Always scrolls regardless of position
 *    - forceScrollToBottom() - Used for user messages and conversation navigation
 *    - Used for "thinking..." messages to show AI is responding
 * 
 * 3. NAVIGATION BEHAVIOR: Simple rules for when to scroll
 *    - Navigation to different conversation: Always scroll to bottom
 *    - Staying in same conversation: Never scroll on refresh, only on new content
 *    - New user messages: Always force scroll to show user's content
 *    - AI streaming: Smart scroll (only if user is near bottom)
 */

package org.rstudio.studio.client.workbench.views.ai;

import com.google.gwt.animation.client.Animation;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;

/**
 * Manages automatic scrolling behavior for AI conversation interfaces.
 * Provides smart scrolling that respects user position during streaming
 * and force scrolling for navigation and user messages.
 */
public class AiScrollManager
{
   private Widget scrollContainer_;
   private boolean isActivelyStreaming_ = false;
   private Animation currentScrollAnimation_ = null;
   private boolean animationsEnabled_ = true;
   
   /**
    * Constructor for AiScrollManager
    * @param scrollContainer The widget that contains the scrollable content
    */
   public AiScrollManager(Widget scrollContainer)
   {
      scrollContainer_ = scrollContainer;
   }
   
   /**
    * Set whether content is actively streaming
    */
   public void setActivelyStreaming(boolean streaming)
   {
      isActivelyStreaming_ = streaming;
   }
   
   /**
    * Smart scroll that only scrolls if user is already near the bottom
    * This implements the "sticky bottom" behavior users expect
    */
   public void smartScrollToBottom()
   {
      com.google.gwt.core.client.Scheduler.get().scheduleDeferred(() -> {
         Element element = scrollContainer_.getElement();
         if (element == null) return;
         
         int scrollTop = element.getScrollTop();
         int scrollHeight = element.getScrollHeight();
         int clientHeight = element.getClientHeight();
         
         // Check if user is already near the bottom (within 50px or at >90% of scroll distance)
         boolean isNearBottom = false;
         if (scrollHeight > clientHeight) {
            int scrollableDistance = scrollHeight - clientHeight;
            int distanceFromBottom = scrollableDistance - scrollTop;
            
            isNearBottom = distanceFromBottom < 50 || 
                          (scrollTop / (double)scrollableDistance) > 0.9;
         } else {
            // Content fits in viewport, always scroll
            isNearBottom = true;
         }
         
         if (isNearBottom) {
            animateScrollToBottom();
         }
      });
   }
   
   /**
    * Force scroll to bottom regardless of current position
    * Used for user messages where we always want to show the new content
    */
   public void forceScrollToBottom()
   {
      com.google.gwt.core.client.Scheduler.get().scheduleDeferred(() -> {
         animateScrollToBottom();
      });
   }
   
   /**
    * Basic scroll to bottom (for backwards compatibility)
    */
   public void scrollToBottom()
   {
      forceScrollToBottom();
   }
   
   /**
    * Smoothly animates scroll to bottom over ~200ms
    * Cancels any existing scroll animation before starting new one
    * If animations are disabled, scrolls instantly
    */
   private void animateScrollToBottom()
   {
      Element element = scrollContainer_.getElement();
      if (element == null) return;
      
      final int targetPosition = element.getScrollHeight();
      
      // If animations are disabled, scroll instantly
      if (!animationsEnabled_) {
         element.setScrollTop(targetPosition);
         return;
      }
      
      // Cancel any existing scroll animation
      if (currentScrollAnimation_ != null) {
         currentScrollAnimation_.cancel();
         currentScrollAnimation_ = null;
      }
      
      final int startPosition = element.getScrollTop();
      final int distance = targetPosition - startPosition;
      
      // If we're already at the bottom or very close, no need to animate
      if (Math.abs(distance) < 5) {
         element.setScrollTop(targetPosition);
         return;
      }
      
      currentScrollAnimation_ = new Animation() {
         @Override
         protected void onUpdate(double progress) {
            // Use smooth easing curve for natural feel
            double easedProgress = easeOutCubic(progress);
            int currentPosition = (int) (startPosition + (distance * easedProgress));
            element.setScrollTop(currentPosition);
         }
         
         @Override
         protected void onComplete() {
            // Ensure we end exactly at target position
            element.setScrollTop(targetPosition);
            currentScrollAnimation_ = null;
         }
         
         @Override
         protected void onCancel() {
            currentScrollAnimation_ = null;
         }
      };
      
      // Run animation for 200ms (similar to other RStudio animations)
      currentScrollAnimation_.run(200);
   }
   
   /**
    * Easing function for smooth animation curve
    * Creates natural deceleration effect
    */
   private double easeOutCubic(double t) {
      return 1 - Math.pow(1 - t, 3);
   }
   
   /**
    * Disable scroll animations - scrolling will be instant
    */
   public void disableAnimations()
   {
      animationsEnabled_ = false;
      // Cancel any currently running animation
      if (currentScrollAnimation_ != null) {
         currentScrollAnimation_.cancel();
         currentScrollAnimation_ = null;
      }
   }
   
   /**
    * Enable scroll animations - scrolling will be smooth
    */
   public void enableAnimations()
   {
      animationsEnabled_ = true;
   }
   
   /**
    * Get current scroll position for debugging purposes
    */
   public int getScrollTop()
   {
      Element element = scrollContainer_.getElement();
      if (element == null) return 0;
      return element.getScrollTop();
   }
} 