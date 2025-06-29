/*
 * AiConstants.java
 *
 * Copyright (C) 2025 by William Nickols
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.studio.client.workbench.views.ai;

public interface AiConstants extends com.google.gwt.i18n.client.Messages {

    /**
     * Translated "AI".
     *
     * @return translated "AI"
     */
    @DefaultMessage("AI")
    @Key("aiText")
    String aiText();

    /**
     * Translated "AI Pane".
     *
     * @return translated "AI Pane"
     */
    @DefaultMessage("AI Pane")
    @Key("aiPaneTitle")
    String aiPaneTitle();

    /**
     * Translated "AI Tab".
     *
     * @return translated "AI Tab"
     */
    @DefaultMessage("AI Tab")
    @Key("aiTabLabel")
    String aiTabLabel();

    /**
     * Translated "AI Tab Second".
     *
     * @return translated "AI Tab Second"
     */
    @DefaultMessage("AI Tab Second")
    @Key("aiTabSecondLabel")
    String aiTabSecondLabel();

    /**
     * Translated "Find next (Enter)".
     *
     * @return translated "Find next (Enter)"
     */
    @DefaultMessage("Find next (Enter)")
    @Key("findNextLabel")
    String findNextLabel();

    /**
     * Translated "Find previous".
     *
     * @return translated "Find previous"
     */
    @DefaultMessage("Find previous")
    @Key("findPreviousLabel")
    String findPreviousLabel();

    /**
     * Translated "Find in chat".
     *
     * @return translated "Find in chat"
     */
    @DefaultMessage("Find in chat")
    @Key("findInTopicLabel")
    String findInTopicLabel();

    /**
     * Translated "No occurrences found".
     *
     * @return translated "No occurrences found"
     */
    @DefaultMessage("No occurrences found")
    @Key("noOccurrencesFoundMessage")
    String noOccurrencesFoundMessage();

    /**
     * Translated "Ask anything".
     *
     * @return translated "Ask anything"
     */
    @DefaultMessage("Ask anything")
    @Key("searchAiLabel")
    String searchAiLabel();

}
