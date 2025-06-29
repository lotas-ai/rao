/*
 * SatelliteUtils.java
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
package org.rstudio.studio.client.common.satellite;

import org.rstudio.core.client.StringUtil;

public class SatelliteUtils
{
   public static String getSatelliteWindowName(String mode)
   {
      return SATELLITE_PREFIX + mode;
   }
   
   public static String getWindowNameFromSatelliteName(String windowName)
   {
      return StringUtil.substring(windowName, SATELLITE_PREFIX.length());
   }
   
   public static boolean windowNameIsSatellite(String windowName)
   {
      return windowName.startsWith(SATELLITE_PREFIX);
   }
   
   private final static String SATELLITE_PREFIX = "_rstudio_satellite_";
}
