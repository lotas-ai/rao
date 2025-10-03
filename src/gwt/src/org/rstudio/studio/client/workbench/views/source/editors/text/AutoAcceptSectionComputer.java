/*
 * AutoAcceptSectionComputer.java
 *
 * Copyright (C) 2025 by Lotas Inc.
 *
 * Computes and manages diff sections for auto-accept operations
 */
package org.rstudio.studio.client.workbench.views.source.editors.text;

import java.util.*;

public class AutoAcceptSectionComputer
{
   public static class SectionInfo
   {
      public String sectionId;
      public String sectionType;  // "added-only", "deleted-only", "combined"
      public List<LineInfo> addedLines;
      public List<LineInfo> deletedLines;
      
      public SectionInfo()
      {
         this.addedLines = new ArrayList<>();
         this.deletedLines = new ArrayList<>();
      }
   }
   
   public static class LineInfo
   {
      public int lineNumber;         // Line number in current file
      public String content;
      public int acceptedPosition;   // Position in accepted content
      public int insertPosition;     // For reject operations
      
      public LineInfo(int lineNumber, String content, int acceptedPosition, int insertPosition)
      {
         this.lineNumber = lineNumber;
         this.content = content;
         this.acceptedPosition = acceptedPosition;
         this.insertPosition = insertPosition;
      }
   }
   
   public static Map<String, SectionInfo> computeSections(List<LineDiffComputer.DiffEntry> diffData, String acceptedContent, String currentContent)
   {
      Map<String, SectionInfo> sections = new LinkedHashMap<>();
      
      int currentLineCount = countLines(currentContent);
      
      int i = 0;
      int sectionsCreated = 0;
      while (i < diffData.size())
      {
         LineDiffComputer.DiffEntry entry = diffData.get(i);
         
         if ("added".equals(entry.type) || "deleted".equals(entry.type))
         {
            SectionInfo section = new SectionInfo();
            List<Integer> addedLineNumbers = new ArrayList<>();
            int startLine = entry.newLine > 0 ? entry.newLine : entry.oldLine;
            int endLine = startLine;
            
            // Collect consecutive added or deleted lines
            while (i < diffData.size() && 
                   ("added".equals(diffData.get(i).type) || "deleted".equals(diffData.get(i).type)))
            {
               LineDiffComputer.DiffEntry current = diffData.get(i);
               
               if ("added".equals(current.type))
               {
                  int acceptedPos = calculateAcceptedInsertPosition(diffData, i);
                  addedLineNumbers.add(current.newLine);
                  section.addedLines.add(new LineInfo(current.newLine, current.content, acceptedPos, 0));
                  if (current.newLine > 0)
                  {
                     endLine = Math.max(endLine, current.newLine);
                  }
               }
               else if ("deleted".equals(current.type))
               {
                  section.deletedLines.add(new LineInfo(
                     -1,
                     current.content,
                     current.oldLine,  // acceptedPosition is oldLine
                     0  // Will be set below
                  ));
               }
               
               i++;
            }
            
            // Determine section type
            if (!section.addedLines.isEmpty() && !section.deletedLines.isEmpty())
            {
               section.sectionType = "combined";
            }
            else if (!section.addedLines.isEmpty())
            {
               section.sectionType = "added-only";
            }
            else
            {
               section.sectionType = "deleted-only";
            }
            
            // Calculate insertPosition for deleted lines (for reject operations)
            if (!section.deletedLines.isEmpty())
            {
               int insertPos;
               if ("combined".equals(section.sectionType) && !addedLineNumbers.isEmpty())
               {
                  insertPos = Collections.min(addedLineNumbers);
               }
               else if ("deleted-only".equals(section.sectionType))
               {
                  int zoneLineNumber = findDeletedContentPosition(diffData, section.deletedLines, currentLineCount);
                  insertPos = zoneLineNumber + 1;
               }
               else
               {
                  insertPos = startLine;
               }
               
               for (int j = 0; j < section.deletedLines.size(); j++)
               {
                  LineInfo line = section.deletedLines.get(j);
                  section.deletedLines.set(j, new LineInfo(
                     line.lineNumber,
                     line.content,
                     line.acceptedPosition,
                     insertPos + j
                  ));
               }
            }
            
            // Create section ID
            section.sectionId = "section-" + section.sectionType + "-" + startLine + "-" + endLine;
            sections.put(section.sectionId, section);
            sectionsCreated++;
         }
         else
         {
            i++;
         }
      }
      
      return sections;
   }
   
   private static int calculateAcceptedInsertPosition(List<LineDiffComputer.DiffEntry> diffData, int addedEntryIndex)
   {
      // Look backwards for the last unchanged line as reference point
      int referenceOldLine = 0;
      for (int i = addedEntryIndex - 1; i >= 0; i--)
      {
         LineDiffComputer.DiffEntry entry = diffData.get(i);
         if ("unchanged".equals(entry.type) && entry.oldLine > 0)
         {
            referenceOldLine = entry.oldLine;
            break;
         }
      }
      
      // Count how many added lines come before this one (after the reference point)
      int addedLinesBefore = 0;
      for (int i = addedEntryIndex - 1; i >= 0; i--)
      {
         LineDiffComputer.DiffEntry entry = diffData.get(i);
         if ("unchanged".equals(entry.type) && entry.oldLine > 0 && entry.oldLine == referenceOldLine)
         {
            break;
         }
         if ("added".equals(entry.type))
         {
            addedLinesBefore++;
         }
      }
      
      return referenceOldLine + 1 + addedLinesBefore;
   }
   
   private static int findDeletedContentPosition(List<LineDiffComputer.DiffEntry> diffData, 
                                                   List<LineInfo> deletedLines, 
                                                   int currentLineCount)
   {
      if (deletedLines.isEmpty())
      {
         return Math.max(1, currentLineCount);
      }
      
      int firstDeletedOldLine = deletedLines.get(0).acceptedPosition;
      
      // Find the next unchanged line after this deletion
      int minOldLineAfterDeletion = Integer.MAX_VALUE;
      LineDiffComputer.DiffEntry nextUnchangedEntry = null;
      
      for (LineDiffComputer.DiffEntry entry : diffData)
      {
         if ("unchanged".equals(entry.type) && 
             entry.oldLine > 0 &&
             entry.oldLine > firstDeletedOldLine &&
             entry.oldLine < minOldLineAfterDeletion &&
             entry.newLine > 0)
         {
            minOldLineAfterDeletion = entry.oldLine;
            nextUnchangedEntry = entry;
         }
      }
      
      if (nextUnchangedEntry != null)
      {
         return Math.max(1, nextUnchangedEntry.newLine - 1);
      }
      else
      {
         return Math.max(1, currentLineCount);
      }
   }
   
   public static String acceptSection(String acceptedContent, SectionInfo section)
   {
      String[] lines = splitLines(acceptedContent);
      
      // Step 1: Remove deleted lines
      if (!section.deletedLines.isEmpty())
      {
         List<Integer> lineNumbersToRemove = new ArrayList<>();
         for (LineInfo line : section.deletedLines)
         {
            lineNumbersToRemove.add(line.acceptedPosition);
         }
         lines = removeLines(lines, lineNumbersToRemove);
      }
      
      // Step 2: Add added lines
      if (!section.addedLines.isEmpty())
      {
         for (LineInfo line : section.addedLines)
         {
            lines = insertLine(lines, line.acceptedPosition, line.content);
         }
      }
      
      return joinLines(lines);
   }
   
   public static String rejectSection(String currentContent, SectionInfo section)
   {
      String[] lines = splitLines(currentContent);
      
      // Step 1: Remove added lines
      if (!section.addedLines.isEmpty())
      {
         List<Integer> lineNumbersToRemove = new ArrayList<>();
         for (LineInfo line : section.addedLines)
         {
            lineNumbersToRemove.add(line.lineNumber);
         }
         lines = removeLines(lines, lineNumbersToRemove);
      }
      
      // Step 2: Re-insert deleted lines
      if (!section.deletedLines.isEmpty())
      {
         for (LineInfo line : section.deletedLines)
         {
            lines = insertLine(lines, line.insertPosition, line.content);
         }
      }
      
      return joinLines(lines);
   }
   
   private static String[] removeLines(String[] lines, List<Integer> lineNumbersToRemove)
   {
      // Sort in descending order to avoid index shifting
      List<Integer> sorted = new ArrayList<>(lineNumbersToRemove);
      Collections.sort(sorted, Collections.reverseOrder());
      
      List<String> result = new ArrayList<>(Arrays.asList(lines));
      for (int lineNumber : sorted)
      {
         int index = lineNumber - 1;  // Convert to 0-based
         if (index >= 0 && index < result.size())
         {
            result.remove(index);
         }
      }
      
      return result.toArray(new String[0]);
   }
   
   private static String[] insertLine(String[] lines, int lineNumber, String content)
   {
      List<String> result = new ArrayList<>(Arrays.asList(lines));
      int index = Math.max(0, Math.min(lineNumber - 1, result.size()));
      result.add(index, content);
      return result.toArray(new String[0]);
   }
   
   private static String[] splitLines(String content)
   {
      if (content == null || content.isEmpty())
      {
         return new String[0];
      }
      return content.split("\n", -1);
   }
   
   private static String joinLines(String[] lines)
   {
      if (lines == null || lines.length == 0)
      {
         return "";
      }
      
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < lines.length; i++)
      {
         if (i > 0)
         {
            sb.append("\n");
         }
         sb.append(lines[i]);
      }
      return sb.toString();
   }
   
   private static int countLines(String content)
   {
      if (content == null || content.isEmpty())
      {
         return 0;
      }
      return content.split("\n", -1).length;
   }
}


