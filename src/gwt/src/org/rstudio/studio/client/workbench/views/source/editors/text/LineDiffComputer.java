/*
 * LineDiffComputer.java
 *
 * Copyright (C) 2025 by Lotas Inc.
 *
 * Computes line-based diffs using Longest Common Subsequence (LCS) algorithm
 */
package org.rstudio.studio.client.workbench.views.source.editors.text;

import java.util.ArrayList;
import java.util.List;

public class LineDiffComputer
{
   public static class DiffEntry
   {
      public String type;      // "added", "deleted", "unchanged"
      public String content;
      public int oldLine;      // 1-based, -1 for added lines
      public int newLine;      // 1-based, -1 for deleted lines
      
      public DiffEntry(String type, String content, int oldLine, int newLine)
      {
         this.type = type;
         this.content = content;
         this.oldLine = oldLine;
         this.newLine = newLine;
      }
   }
   
   public static class DiffResult
   {
      public List<DiffEntry> diff;
      public int added;
      public int deleted;
      
      public DiffResult(List<DiffEntry> diff, int added, int deleted)
      {
         this.diff = diff;
         this.added = added;
         this.deleted = deleted;
      }
   }
   
   public static DiffResult computeDiff(String acceptedContent, String currentContent)
   {
      String[] oldLines = splitLines(acceptedContent);
      String[] newLines = splitLines(currentContent);
      return computeLineDiff(oldLines, newLines);
   }
   
   public static DiffResult computeLineDiff(String[] oldLines, String[] newLines)
   {
      // Handle edge cases
      if (oldLines == null || oldLines.length == 0)
      {
         List<DiffEntry> result = new ArrayList<>();
         if (newLines != null)
         {
            for (int i = 0; i < newLines.length; i++)
            {
               result.add(new DiffEntry("added", newLines[i], -1, i + 1));
            }
         }
         return new DiffResult(result, newLines == null ? 0 : newLines.length, 0);
      }
      
      if (newLines == null || newLines.length == 0)
      {
         List<DiffEntry> result = new ArrayList<>();
         for (int i = 0; i < oldLines.length; i++)
         {
            result.add(new DiffEntry("deleted", oldLines[i], i + 1, -1));
         }
         return new DiffResult(result, 0, oldLines.length);
      }
      
      // Compute LCS matrix
      int m = oldLines.length;
      int n = newLines.length;
      int[][] lcs = new int[m + 1][n + 1];
      
      for (int i = 1; i <= m; i++)
      {
         for (int j = 1; j <= n; j++)
         {
            if (oldLines[i - 1].equals(newLines[j - 1]))
            {
               lcs[i][j] = lcs[i - 1][j - 1] + 1;
            }
            else
            {
               lcs[i][j] = Math.max(lcs[i][j - 1], lcs[i - 1][j]);
            }
         }
      }
      
      // Backtrack to build diff
      List<DiffEntry> diff = new ArrayList<>();
      int i = m;
      int j = n;
      int added = 0;
      int deleted = 0;
      
      while (i > 0 || j > 0)
      {
         if (i > 0 && j > 0 && oldLines[i - 1].equals(newLines[j - 1]))
         {
            // Lines match - unchanged
            diff.add(0, new DiffEntry("unchanged", oldLines[i - 1], i, j));
            i--;
            j--;
         }
         else if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j]))
         {
            // Line added in new version
            diff.add(0, new DiffEntry("added", newLines[j - 1], -1, j));
            j--;
            added++;
         }
         else if (i > 0)
         {
            // Line deleted from old version
            diff.add(0, new DiffEntry("deleted", oldLines[i - 1], i, -1));
            i--;
            deleted++;
         }
      }
      
      return new DiffResult(diff, added, deleted);
   }
   
   private static String[] splitLines(String content)
   {
      if (content == null || content.isEmpty())
      {
         return new String[0];
      }
      return content.split("\n", -1);
   }
}


