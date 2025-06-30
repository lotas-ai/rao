/*
 * SessionSymbolIndex.cpp
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

#include "SessionSymbolIndex.hpp"

#include <vector>
#include <string>
#include <unordered_map>
#include <iostream>
#include <fstream>
#include <memory>
#include <mutex>
#include <regex>
#include <boost/algorithm/string.hpp>
#include <boost/filesystem.hpp>
#include <boost/bind/bind.hpp>
#include <boost/uuid/uuid.hpp>
#include <boost/uuid/uuid_generators.hpp>
#include <boost/uuid/uuid_io.hpp>
#include <boost/lexical_cast.hpp>
#include <boost/algorithm/string/predicate.hpp>
#include <boost/functional/hash.hpp>
#include <chrono>

#include <shared_core/Error.hpp>
#include <core/Exec.hpp>
#include <core/FileSerializer.hpp>
#include <shared_core/FilePath.hpp>
#include <core/FileUtils.hpp>
#include <core/json/JsonRpc.hpp>
#include <core/system/System.hpp>
#include <core/Algorithm.hpp>
#include <core/http/Util.hpp>
#include <shared_core/Hash.hpp>
#include <core/Log.hpp>

#include <r/RSexp.hpp>
#include <r/RExec.hpp>
#include <r/RJson.hpp>
#include <r/RRoutines.hpp>
#include <r/session/RSessionUtils.hpp>

#include <session/SessionModuleContext.hpp>
#include <session/SessionSourceDatabase.hpp>

using namespace rstudio::core;
namespace fs = boost::filesystem;

namespace rstudio {
namespace session {
namespace modules {
namespace symbol_index {

// Structure to hold symbol information
struct Symbol {
   std::string name;           // Symbol name
   std::string type;           // Function, class, variable, etc.
   std::string filePath;       // Absolute file path
   std::string fileName;       // Base file name
   int lineStart;              // Start line
   int lineEnd;                // End line (if applicable)
   std::string parents;        // Parent context (namespace, class, file, directory, etc.)
   std::string signature;      // For functions, the signature
   std::vector<std::string> children; // Child symbols (for directories and files)
   
   Symbol() : lineStart(0), lineEnd(0) {}
   
   Symbol(const std::string& name, 
          const std::string& type,
          const std::string& filePath,
          int lineStart,
          int lineEnd = 0,
          const std::string& parents = "",
          const std::string& signature = "") 
      : name(name), type(type), filePath(filePath), 
        lineStart(lineStart), lineEnd(lineEnd),
        parents(parents), signature(signature) {
        // Extract filename from path
        size_t lastSlash = filePath.find_last_of("/\\");
        if (lastSlash != std::string::npos) {
           fileName = filePath.substr(lastSlash + 1);
        } else {
           fileName = filePath;
        }
   }
   
   // Add a child symbol name
   void addChild(const std::string& childName) {
      if (std::find(children.begin(), children.end(), childName) == children.end()) {
         children.push_back(childName);
      }
   }
   
   // Check if two symbols are the same instance (same name, file, and line)
   bool isSameInstance(const Symbol& other) const {
      return name == other.name && 
             filePath == other.filePath && 
             lineStart == other.lineStart;
   }
};

// Structure to hold file checksum information
struct FileChecksum {
   std::string path;           // Absolute file path
   std::string checksum;       // SHA-256 checksum of the file
   std::string lastModified;   // Last modified timestamp
   
   FileChecksum() {}
   
   FileChecksum(const std::string& path, 
                const std::string& checksum,
                const std::string& lastModified) 
      : path(path), checksum(checksum), lastModified(lastModified) {}
};

// Global constant for maximum files to index at once
const size_t MAX_FILES_PER_BATCH = 100;

// Global constant for indexing timeout in milliseconds
const int INDEXING_TIMEOUT_MS = 1000;

// Global constants for directory and file management
namespace {
   // Base directory for storing symbol indexes
   std::string getIndexBaseDir() 
   {
      // Call get_ai_base_dir() directly to get the base rstudio-ai directory
      std::string baseAiDir;
      Error error = r::exec::evaluateString(".rs.get_ai_base_dir()", &baseAiDir);
      
      if (error) {
         return std::string();
      }
      
      FilePath baseDir(baseAiDir);
      FilePath symbolIndexPath = baseDir.completePath("symbol_index");
      
      // Ensure the directory exists
      if (!symbolIndexPath.exists()) {
         Error dirError = symbolIndexPath.ensureDirectory();
         if (dirError) {
            return std::string();
         }
      }
      
      return symbolIndexPath.getAbsolutePath();
   }
   
   // COMPREHENSIVE list of excluded filenames - complete exclusion from all indexing
   std::set<std::string> getExcludedFilenames() {
      static std::set<std::string> excludedFilenames = {
         // macOS system files
         ".DS_Store",
         "._.DS_Store",
         "._*",  // AppleDouble files
         ".Spotlight-V100",
         ".Trashes",
         ".fseventsd",
         ".VolumeIcon.icns",
         ".com.apple.timemachine.donotpresent",
         
         // Windows system files
         "Thumbs.db",
         "Desktop.ini",
         "System Volume Information",
         "$RECYCLE.BIN",
         
         // Version control
         ".git",
         ".svn", 
         ".hg",
         ".gitignore",
         ".gitattributes",
         ".gitmodules",
         
         // IDE and editor files
         ".vscode",
         ".idea",
         "*.tmp",
         "*.swp",
         "*.swo",
         "*~",
         "#*#",
         ".#*"
      };
      return excludedFilenames;
   }
   
   // Check if a filename should be completely excluded from indexing
   bool isExcludedFilename(const std::string& filename) {
      std::set<std::string> excluded = getExcludedFilenames();
      
      // Direct match
      if (excluded.find(filename) != excluded.end()) {
         return true;
      }
      
      // Pattern matching for wildcard entries
      for (const std::string& pattern : excluded) {
         if (pattern.find('*') != std::string::npos) {
            // Simple wildcard matching
            if (pattern.front() == '*' && pattern.back() != '*') {
               // Pattern like "*.tmp" - check suffix
               std::string suffix = pattern.substr(1);
               if (filename.length() >= suffix.length() && 
                   filename.substr(filename.length() - suffix.length()) == suffix) {
                  return true;
               }
            } else if (pattern.front() != '*' && pattern.back() == '*') {
               // Pattern like "._*" - check prefix  
               std::string prefix = pattern.substr(0, pattern.length() - 1);
               if (filename.length() >= prefix.length() &&
                   filename.substr(0, prefix.length()) == prefix) {
                  return true;
               }
            }
         }
      }
      
      return false;
   }
   
   // Helper for building file paths within the index directory
   std::string getIndexFilePath(const std::string& dirId, const std::string& filename = "") 
   {
      FilePath baseDir(getIndexBaseDir());
      FilePath dirPath = baseDir.completeChildPath(dirId);
      
      // Check if directory exists
      if (!dirPath.exists()) {
         // Try to create the directory if it doesn't exist
         Error error = dirPath.ensureDirectory();
         if (error) {
            return std::string();
         }
      }
      
      if (filename.empty()) {
         return dirPath.getAbsolutePath();
      }
      
      FilePath filePath = dirPath.completeChildPath(filename);
      return filePath.getAbsolutePath();
   }
   
   // Path to CSV file for directory mapping
   std::string getDirMappingFile() 
   {
      std::string baseDir = getIndexBaseDir();
      if (baseDir.empty()) {
         return "";
      }
      
      FilePath baseFilePath(baseDir);
      if (!baseFilePath.exists()) {
         // Try to create the directory
         Error error = baseFilePath.ensureDirectory();
         if (error) {
            return "";
         }
      }
      
      FilePath mappingPath = baseFilePath.completeChildPath("directory_mapping.csv");
      
      return mappingPath.getAbsolutePath();
   }
   
   // Path to the checksums file for a specific directory ID
   std::string getChecksumFile(const std::string& dirId) 
   {
      return getIndexFilePath(dirId, "file_checksums.json");
   }
   
   // Path to the directory structure file for a specific directory ID
   std::string getDirStructureFile(const std::string& dirId) 
   {
      return getIndexFilePath(dirId, "dir_structure.json");
   }
   
   // Normalize a directory path for safe comparison
   std::string normalizeDirPath(const std::string& dirPath) 
   {
      FilePath path(dirPath);
      std::string normalized = path.getAbsolutePath();
      if (!boost::algorithm::ends_with(normalized, "/"))
         normalized += "/";
      
      return normalized;
   }
   
   // Generate a unique ID for directories
   std::string generateUniqueId() 
   {
      boost::uuids::uuid uuid = boost::uuids::random_generator()();
      return boost::lexical_cast<std::string>(uuid);
   }
   
   // Generate SHA-256 checksum for a file
   std::string generateFileChecksum(const FilePath& filePath) 
   {
      if (!filePath.exists()) {
         return "";
      }
      
      // Use modification time instead of content for more stable checksums
      std::time_t modTime = filePath.getLastWriteTime();
      
      // Convert the modification time to a string and hash it
      std::string modTimeStr = std::to_string(modTime);
      std::string result = core::hash::crc32HexHash(modTimeStr);
      
      return result;
   }
   
   // Get a file's last modified time as string
   std::string getFileModifiedTime(const FilePath& filePath) 
   {
      if (!filePath.exists())
         return "";
      
      std::time_t modTime = filePath.getLastWriteTime();
      return std::to_string(modTime);
   }

   // Check if a file is likely to be a binary file based on its extension
   bool isBinaryFileType(const std::string& extension) {
      // Common binary file extensions (case insensitive)
      static const std::set<std::string> binaryExtensions = {
         // Images
         ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".svg", ".tiff", ".webp", ".ico", ".psd",
         // Compiled code
         ".exe", ".dll", ".so", ".dylib", ".obj", ".o", ".a", ".lib",
         // Compressed
         ".zip", ".gz", ".tar", ".7z", ".rar", ".jar", ".war", ".ear",
         // Media
         ".mp3", ".mp4", ".avi", ".mov", ".mkv", ".wav", ".flac", ".ogg",
         // Documents
         ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
         // Database
         ".db", ".sqlite", ".mdb", ".accdb", ".frm", ".dbf",
         // Other
         ".bin", ".dat", ".class", ".pyc", ".pyo"
      };
      
      return binaryExtensions.find(extension) != binaryExtensions.end();
   }

   // Check if a file is an image file based on its extension
   bool isImageFileType(const std::string& extension) {
      // Common image file extensions (case insensitive)
      static const std::set<std::string> imageExtensions = {
         ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".svg", ".tiff", ".webp", ".ico", ".psd"
      };
      
      return imageExtensions.find(extension) != imageExtensions.end();
   }

   // Check if a file should be indexed based on its extension
   bool isIndexableFileType(const std::string& extension) {
      // List of file extensions that should be indexed
      static const std::set<std::string> indexableExtensions = {
         // R
         ".r",
         // C/C++
         ".c", ".cpp", ".cc", ".h", ".hpp", ".cxx", ".hxx",
         // Python
         ".py", ".pyi", ".pyw",
         // Markdown
         ".md", ".rmd", ".qmd", ".markdown",
         // Scripts
         ".sh", ".bash", ".zsh", ".bat", ".cmd", ".ps1",
         // SQL
         ".sql",
         // Documentation
         ".rd", ".Rd",
         // Stan
         ".stan"
      };
      
      return indexableExtensions.find(extension) != indexableExtensions.end();
   }
}

// Forward declarations for event handlers
void onSourceDocUpdated(boost::shared_ptr<source_database::SourceDocument> pDoc);
void onSourceDocRemoved(const std::string& id, const std::string& path);
void onAllSourceDocsRemoved();

// Main index class
class SymbolIndex {
public:
   SymbolIndex() : indexBuilt_(false) {}
   
   // Singleton accessor
   static SymbolIndex& getInstance() {
      static SymbolIndex instance;
      return instance;
   }
   
   // Build index from directory
   Error buildIndex(const FilePath& dir);
   
   // Lookup symbol in index
   std::vector<Symbol> findSymbol(const std::string& name);
   
   // Get all symbols
   std::vector<Symbol> getAllSymbols();
   
   // Check if index is built
   bool isIndexBuilt() const { return indexBuilt_; }
   
   // Add a symbol to the index (thread-safe)
   void addSymbol(const Symbol& symbol);
   
   // Check if index exists for a directory
   bool indexExistsForDirectory(const FilePath& dir);
   
   // Check if a directory has changed since last indexed
   bool hasDirectoryChanged(const FilePath& dir);
   
   // Check if there are pending files to index
   bool hasPendingFiles() const { return !traversalPath_.empty(); }
   
   // Get the estimate of files remaining to index (just return a large number if in progress)
   size_t getPendingFileCount() const { return !traversalPath_.empty() ? 1000 : 0; }
   
   // Index a specific file or directory (bypassing tree traversal)
   void indexSpecificTarget(const FilePath& target);
   
   // Remove the entire symbol index for the current working directory
   Error removeSymbolIndex();
   
   // Build symbol index framework quickly without actual indexing
   Error buildIndexQuick(const FilePath& dir);
   
   // Index open documents from editor
   void indexOpenDocuments();
   
   // Remove all symbols for a given file path (public for event handlers)
   void removeSymbolsForFile(const std::string& filePath);
   
   // Index content by document type (public for event handlers)
   void indexContentByDocumentType(const std::string& content, const std::string& filePath, const std::string& docType);
   
   // Get access to symbol map for event handlers
   std::unordered_map<std::string, std::vector<Symbol>>& getSymbolMap() { return symbolMap_; }

private:
   // Symbol index storage - map from symbol name to vector of symbols (for duplicates)
   std::unordered_map<std::string, std::vector<Symbol>> symbolMap_;
   
   // File checksum map - from file path to checksum info
   std::unordered_map<std::string, FileChecksum> fileChecksums_;
   
   // Directory structure - list of all files in the directory
   std::vector<std::string> directoryFiles_;
   
   // Pending files that still need to be indexed
   std::vector<std::string> pendingFiles_;
   
   // Position tracking for directory traversal (0 = complete, >0 = in progress)
   std::vector<size_t> traversalPath_; // Tracks the complete path through directory hierarchy
   
   // Timestamp for indexing timeout
   std::chrono::time_point<std::chrono::steady_clock> indexingStartTime_;
   
   // Add a symbol to the index without locking (for internal use)
   void addSymbolNoLock(const Symbol& symbol);
   
   // Process pending files up to the batch limit
   Error processPendingFiles(const std::string& dirId);
   
   // File type handlers
   void indexRFile(const FilePath& filePath);
   void indexCppFile(const FilePath& filePath);
   void indexPythonFile(const FilePath& filePath);
   void indexMarkdownFile(const FilePath& filePath);
   void indexSqlFile(const FilePath& filePath);
   void indexStanFile(const FilePath& filePath);
   void indexShellScript(const FilePath& filePath);
   void indexRdFile(const FilePath& filePath);
   
   // Helper to determine which indexer to use based on file extension
   void indexFileByType(const FilePath& filePath);
   
   // Safe version that doesn't hold the lock during file I/O
   void indexFileByTypeSafe(const FilePath& filePath);
   
   // Index content directly from string (for open documents)
   void indexRFromString(const std::string& content, const std::string& filePath);
   void indexCppFromString(const std::string& content, const std::string& filePath);
   void indexPythonFromString(const std::string& content, const std::string& filePath);
   void indexMarkdownFromString(const std::string& content, const std::string& filePath);
   void indexSqlFromString(const std::string& content, const std::string& filePath);
   void indexStanFromString(const std::string& content, const std::string& filePath);
   void indexShellFromString(const std::string& content, const std::string& filePath);
   void indexRdFromString(const std::string& content, const std::string& filePath);
   void indexContentByFileType(const std::string& content, const std::string& filePath);
   
   // Helpers for processing code blocks in markdown files
   void processRChunkFunction(const std::smatch& match, const std::string& code, 
                             int startLine, const std::string& path, const std::string& chunkName);
   
   // Check if the indexing has timed out
   bool hasTimedOut() const;
   
   // File traversal
   void traverseDirectory(const FilePath& dir, size_t& filesIndexed);
   
   // Get the current directory structure
   std::vector<std::string> getCurrentDirectoryStructure(const FilePath& dir);
   
   // Calculate checksums for all files in a directory
   void calculateFileChecksums(const FilePath& dir);
   
   // Update checksums for modified files
   void updateFileChecksums();
   
   // New method to update file and directory contexts
   void updateFileAndDirectoryContexts();
   
   // Save and load checksums to/from storage
   Error saveChecksumsToStorage(const std::string& dirId);
   Error loadChecksumsFromStorage(const std::string& dirId);
   
   // Save and load directory structure to/from storage
   Error saveDirStructureToStorage(const std::string& dirId);
   Error loadDirStructureFromStorage(const std::string& dirId);
   
   // Save and load pending files to/from storage
   Error savePendingFilesToStorage(const std::string& dirId);
   Error loadPendingFilesFromStorage(const std::string& dirId);
   
   // Save and load all storage components together
   Error saveAllToStorage(const std::string& dirId);
   Error loadAllFromStorage(const std::string& dirId);
   
   // Incremental index update
   Error updateIndexIncrementally(const FilePath& dir);
   
   // Flag indicating if index is built
   bool indexBuilt_;
   
   // Mutex for thread safety
   std::mutex mutex_;
   
   // Directory storage management
   std::string ensureStorageDir(const FilePath& workingDir);
   std::string getDirectoryId(const std::string& dirPath);
   Error saveIndexToStorage(const std::string& dirId);
   Error loadIndexFromStorage(const std::string& dirId);
   
   // Non-locking version of loadIndexFromStorage for internal use
   Error loadIndexFromStorageNoLock(const std::string& dirId);
   
   // Current working directory for which the index is built
   std::string currentWorkingDir_;
};

// Forward declarations
std::string normalizeWhitespace(const std::string& input);
std::string formatFunctionParameters(const std::string& input);
std::string extractRFunctionSignature(const std::vector<std::string>& lines, size_t startLineIndex, size_t funcPos);
std::string extractFunctionName(const std::string& line, size_t assignmentPos, const std::string& assignmentOp);

// Helper function to extract the actual function name from an assignment
std::string extractFunctionName(const std::string& line, size_t assignmentPos, const std::string& assignmentOp) {
   // Go backwards from the assignment operator to find the function name
   size_t nameEnd = assignmentPos;
   
   // Skip whitespace before the assignment operator
   while (nameEnd > 0 && std::isspace(line[nameEnd - 1])) {
      nameEnd--;
   }
   
   // Find the beginning of the name
   size_t nameStart = nameEnd;
   
   // We need to handle cases like dots in R function names (e.g., "coef.")
   // and other special characters that can appear in function names
   while (nameStart > 0) {
      char c = line[nameStart - 1];
      
      // Valid identifier characters: alphanumeric, dot, underscore
      bool isValidChar = std::isalnum(c) || c == '.' || c == '_';
      
      // If it's not a valid identifier character, we've reached the start
      if (!isValidChar) {
         // But if it's whitespace and we haven't found any valid chars yet,
         // skip past the whitespace to find the actual identifier
         if (std::isspace(c) && nameStart == nameEnd) {
            nameStart--;
            nameEnd--;
            continue;
         }
         
         // Otherwise we've found the boundary
         break;
      }
      
      nameStart--;
   }
   
   // If we didn't find a name at all, return empty string
   if (nameStart == nameEnd) {
      return "";
   }
   
   // Extract just the name
   return line.substr(nameStart, nameEnd - nameStart);
}

// Implementation of SymbolIndex methods
void SymbolIndex::addSymbolNoLock(const Symbol& symbol) {
   
   // Convert symbol name to lowercase for case-insensitive search
   std::string lowerName = symbol.name;
   boost::algorithm::to_lower(lowerName);
   
   // Check if this exact symbol instance already exists
   auto& symbolList = symbolMap_[lowerName];
   for (const auto& existingSymbol : symbolList) {
      if (existingSymbol.isSameInstance(symbol)) {
         return; // Skip duplicates
      }
   }
   
   // Add the new symbol instance
   symbolMap_[lowerName].push_back(symbol);
}

void SymbolIndex::addSymbol(const Symbol& symbol) {
   std::lock_guard<std::mutex> lock(mutex_);
   addSymbolNoLock(symbol);
}

std::vector<Symbol> SymbolIndex::findSymbol(const std::string& name) {
   std::lock_guard<std::mutex> lock(mutex_);
   
   // Convert search term to lowercase for case-insensitive search
   std::string lowerName = name;
   boost::algorithm::to_lower(lowerName);
   
   // Clean search term by removing trailing whitespace and hash symbols
   boost::algorithm::trim_right(lowerName);
   size_t lastNonHash = lowerName.find_last_not_of('#');
   if (lastNonHash != std::string::npos && lastNonHash < lowerName.length() - 1) {
      lowerName = lowerName.substr(0, lastNonHash + 1);
      boost::algorithm::trim_right(lowerName);
   }
   
   // Check for type filter in the format "name (type)"
   std::string typeFilter;
   std::string searchName = lowerName;
   std::regex typeFilterRegex(R"(^(.+?)\s*\(([a-z]+)\)\s*$)");
   std::smatch typeMatch;
   
   if (std::regex_search(lowerName, typeMatch, typeFilterRegex)) {
      searchName = boost::algorithm::trim_copy(std::string(typeMatch[1]));
      typeFilter = boost::algorithm::trim_copy(std::string(typeMatch[2]));
   }
   
   // If the index is empty and we have a current working directory, try to load from storage
   if (symbolMap_.empty() && !currentWorkingDir_.empty()) {
      FilePath dir(currentWorkingDir_);
      std::string dirId = getDirectoryId(dir.getAbsolutePath());
      if (!dirId.empty()) {
         Error error = loadIndexFromStorageNoLock(dirId);
         if (error)
            LOG_ERROR(error);
      }
   }
   
   std::vector<Symbol> results;
   
   // First try exact match
   auto it = symbolMap_.find(searchName);
   if (it != symbolMap_.end()) {
      // If we have a type filter, apply it
      if (!typeFilter.empty()) {
         for (const Symbol& symbol : it->second) {
            // Match symbol type with our filter (allow partial matches, e.g., "var" matches "variable")
            if (boost::algorithm::starts_with(symbol.type, typeFilter)) {
               results.push_back(symbol);
            }
         }
      } else {
         return it->second;  // Return all instances of the symbol
      }
      
      // If we found type-filtered results, return them
      if (!results.empty()) {
         return results;
      }
   }
   
   // No special handling for search queries - treat all characters equally
   bool skipFuzzyMatch = false;
   
   // Next, try to find headers by checking for partial matches
   // This helps with header symbols where users might include only part of the header text
   if (!skipFuzzyMatch) {  // REMOVED:  && searchName.length() >= 3 Only attempt this for queries of reasonable length
      // Look through all symbols for headers that contain the search query
      for (const auto& pair : symbolMap_) {
         for (const auto& symbol : pair.second) {
            // If we have a type filter, skip symbols that don't match the filter
            if (!typeFilter.empty() && !boost::algorithm::starts_with(symbol.type, typeFilter)) {
               continue;
            }
            
            // Check if it's a header type
            if (boost::algorithm::starts_with(symbol.type, "header")) {
               std::string lowerSymbolName = symbol.name;
               boost::algorithm::to_lower(lowerSymbolName);
               
               // Clean symbol name for comparison using the same rules as the search term
               boost::algorithm::trim_right(lowerSymbolName);
               size_t symbolLastNonHash = lowerSymbolName.find_last_not_of('#');
               if (symbolLastNonHash != std::string::npos && symbolLastNonHash < lowerSymbolName.length() - 1) {
                  lowerSymbolName = lowerSymbolName.substr(0, symbolLastNonHash + 1);
                  boost::algorithm::trim_right(lowerSymbolName);
               }
               
               // MORE STRICT: Match only if:
               // 1. The symbol contains all the words in the query, or
               // 2. The symbol contains a full phrase match
               
               // Case 1: Check if symbol contains all words in the query
               bool allWordsMatch = true;
               std::vector<std::string> queryWords;
               boost::algorithm::split(queryWords, searchName, boost::is_any_of(" \t"));
               
               // Skip if the query has too many words, indicating it might be unrelated text
               if (queryWords.size() > 10) {
                  allWordsMatch = false;
               } else {
                  for (const std::string& queryWord : queryWords) {
                     // Check if this word appears in the symbol name - no special handling for any characters
                     if (lowerSymbolName.find(queryWord) == std::string::npos) {
                        allWordsMatch = false;
                        break;
                     }
                  }
               }
               
               // Case 2: Check for full phrase match if all query words don't match
               bool phraseMatch = false;
               if (!allWordsMatch && searchName.length() >= 4) {
                  phraseMatch = (lowerSymbolName.find(searchName) != std::string::npos);
               }
               
               // Calculate similarity/relevance score
               float similarityScore = 0.0f;
               if (allWordsMatch || phraseMatch) {
                  // Calculate some basic similarity measure
                  size_t minLength = std::min(searchName.length(), lowerSymbolName.length());
                  size_t maxLength = std::max(searchName.length(), lowerSymbolName.length());
                  
                  if (maxLength > 0) {
                     similarityScore = static_cast<float>(minLength) / maxLength;
                  }
                  
                  // Only include results with sufficient similarity
                  if (similarityScore > 0.15f) {
                     results.push_back(symbol);
                  }
               }
            }
         }
      }
      
      // Return partial matches if found
      if (!results.empty()) {
         return results;
      }
   }
   
   // Finally, try a stricter fuzzy match for any symbol type
   // This is especially helpful for symbols with spaces
   if (!skipFuzzyMatch && searchName.length() >= 3) {
      std::vector<std::string> queryWords;
      boost::algorithm::split(queryWords, searchName, boost::is_any_of(" \t"));
      
      // Skip if the query has only very short words
      bool hasSubstantialWord = false;
      
      for (const std::string& queryWord : queryWords) {
         if (queryWord.length() >= 3) {
            hasSubstantialWord = true;
            break;
         }
      }
      
      // Skip fuzzy matching for queries with no substantial words
      if (!hasSubstantialWord && queryWords.size() <= 1) {
         return std::vector<Symbol>();
      }
      
      // Skip fuzzy matching for queries with too many words (likely not a symbol name)
      if (queryWords.size() > 8) {
         return std::vector<Symbol>();
      }
      
      for (const auto& pair : symbolMap_) {
         for (const auto& symbol : pair.second) {
            // Skip if we have a type filter and this symbol doesn't match
            if (!typeFilter.empty() && !boost::algorithm::starts_with(symbol.type, typeFilter)) {
               continue;
            }
            
            std::string lowerSymbolName = symbol.name;
            boost::algorithm::to_lower(lowerSymbolName);
            
            // Clean symbol name for fuzzy matching
            if (boost::algorithm::starts_with(symbol.type, "header")) {
               boost::algorithm::trim_right(lowerSymbolName);
               size_t symbolLastNonHash = lowerSymbolName.find_last_not_of('#');
               if (symbolLastNonHash != std::string::npos && symbolLastNonHash < lowerSymbolName.length() - 1) {
                  lowerSymbolName = lowerSymbolName.substr(0, symbolLastNonHash + 1);
                  boost::algorithm::trim_right(lowerSymbolName);
               }
            }
            
            std::vector<std::string> symbolWords;
            boost::algorithm::split(symbolWords, lowerSymbolName, boost::is_any_of(" \t"));
            
            // IMPROVED MATCHING LOGIC:
            // For a match to be found:
            // 1. At least 70% of query words must match symbol words OR
            // 2. For 2+ word queries, all words must match symbol words
            
            int matchCount = 0;
            std::vector<bool> wordMatched(queryWords.size(), false);
            
            // For each query word, check if it's a substantial part of any symbol word
            for (size_t i = 0; i < queryWords.size(); i++) {
               const std::string& queryWord = queryWords[i];
               for (const std::string& symbolWord : symbolWords) {
                  // Skip very short symbol words                  
                  // Consider it a match if:
                  // - Query word is a prefix of symbol word (no length restriction), OR
                  // - Query word is exactly equal to symbol word, OR
                  // - Query word is at least 5 chars and contained within symbol word
                  if ((boost::algorithm::starts_with(symbolWord, queryWord) && queryWord.length() >= 4) ||
                      (symbolWord == queryWord) ||
                      (symbolWord.find(queryWord) != std::string::npos)) {
                     wordMatched[i] = true;
                     matchCount++;
                     break;
                  }
               }
            }
            
            // Calculate match percentage (don't skip short words)
            int substantialWords = queryWords.size(); // Count all words now
            
            // Avoid division by zero
            float matchPercentage = (substantialWords > 0) 
               ? static_cast<float>(matchCount) / substantialWords
               : 0.0f;
               
            // For single-word queries, require at least an exact match or prefix (no length restriction)
            if (queryWords.size() == 1) {
               const std::string& queryWord = queryWords[0];
               
               bool foundMatch = false;
               for (const std::string& symbolWord : symbolWords) {
                  // Exact match
                  if (symbolWord == queryWord) {
                     foundMatch = true;
                     break;
                  }
                  
                  // Prefix match with at least 5 chars
                  if (boost::algorithm::starts_with(symbolWord, queryWord) && queryWord.length() >= 5) {
                     foundMatch = true;
                     break;
                  }
               }
               
               if (foundMatch) {
                  results.push_back(symbol);
               }
            }
            // For multi-word queries, require at least 70% match or all words to match
            else if (matchPercentage >= 0.7 || (queryWords.size() >= 2 && matchCount == substantialWords)) {
               // Make sure there's actual content similarity, not just coincidental word matches
               float overallSimilarity = static_cast<float>(std::min(searchName.length(), lowerSymbolName.length())) / 
                                        std::max(searchName.length(), lowerSymbolName.length());
               
               // Only include results with sufficient overall similarity
               if (overallSimilarity > 0.15f) {
                  results.push_back(symbol);
               }
            }
         }
      }
      
      if (!results.empty()) {
         return results;
      }
   }
   
   return std::vector<Symbol>();
}

std::vector<Symbol> SymbolIndex::getAllSymbols() {
   std::lock_guard<std::mutex> lock(mutex_);
   
   // If the map is empty and we have a current working directory, try to load from storage
   if (symbolMap_.empty() && !currentWorkingDir_.empty()) {
      FilePath dir(currentWorkingDir_);
      std::string dirId = getDirectoryId(dir.getAbsolutePath());
      if (!dirId.empty()) {
         Error error = loadIndexFromStorageNoLock(dirId);
         if (error)
            LOG_ERROR(error);
      }
   }
   
   std::vector<Symbol> allSymbols;
   for (const auto& pair : symbolMap_) {
      allSymbols.insert(allSymbols.end(), pair.second.begin(), pair.second.end());
   }
   return allSymbols;
}

void SymbolIndex::traverseDirectory(const FilePath& dir, size_t& filesIndexed) {
   
   std::vector<FilePath> children;
   Error error = dir.getChildren(children);
   if (error) {
      LOG_ERROR(error);
      return;
   }
   
   // Sort the children to ensure consistent traversal order
   std::sort(children.begin(), children.end(), 
             [](const FilePath& a, const FilePath& b) {
                return a.getAbsolutePath() < b.getAbsolutePath();
             });
   
   // Limit the number of files/directories we process per batch or by time
   if (filesIndexed >= MAX_FILES_PER_BATCH || hasTimedOut()) {
      // We've already reached our limit, so store our position and stop
      if (!children.empty()) {
         // If this is a new traversal path, add the starting position
         if (traversalPath_.empty() || traversalPath_.back() != 1) {
            traversalPath_.push_back(1); // Start position for next run
         }
      }
      return;
   }
   
   // Start from the saved position in this directory's children list
   size_t startPosition = 0;
   
   // If we have a valid traversal path and we're at the right level
   if (!traversalPath_.empty()) {
      // Get the position for this level and remove it from the path
      startPosition = traversalPath_.back();
      traversalPath_.pop_back();
   }
   
   // Process each child starting from the saved position
   for (size_t i = startPosition; i < children.size(); i++) {
      const FilePath& child = children[i];
      
      // Check if we've reached the file limit or timeout
      if (filesIndexed >= MAX_FILES_PER_BATCH || hasTimedOut()) {
         // Save position for next call (i+1 because we want to resume with the next item)
         size_t nextPosition = i + 1;
         
         // If we're at the end of this directory's children, don't add to path
         if (i < children.size() - 1) {
            traversalPath_.push_back(nextPosition);
         }
         
         return;
      }
      
      if (child.isDirectory()) {
         // Skip hidden directories and those that might contain too many files
         std::string dirName = child.getFilename();
         if (dirName[0] == '.' || dirName == ".git" || 
             boost::algorithm::ends_with(dirName, "_cache")) {
            continue;
         }
         
         // Index the directory itself as a symbol
         std::string dirPath = child.getAbsolutePath();
         Symbol dirSymbol(child.getFilename(), "directory", dirPath, 0, 0);
         addSymbolNoLock(dirSymbol);
         filesIndexed++; // Count directories toward our file limit
         
         // Stop if we've reached the maximum or timed out
         if (filesIndexed >= MAX_FILES_PER_BATCH || hasTimedOut()) {
            // Save position for next call
            traversalPath_.push_back(i + 1);
            return;
         }
         
         // Recursively process subdirectory
         traverseDirectory(child, filesIndexed);
         
         // If we hit the limit during recursion, we need to stop and
         // preserve our position in the parent directory
         if (filesIndexed >= MAX_FILES_PER_BATCH || hasTimedOut()) {
            // Add this directory's position to the traversal path
            // so we can continue with this child next time
            traversalPath_.push_back(i);
            return;
         }
      } else {
         // Add the file itself as a symbol
         std::string fileName = child.getFilename();
         std::string filePath = child.getAbsolutePath();
         std::string extension = child.getExtensionLowerCase();
         
         // COMPLETELY exclude certain filenames from all indexing
         if (isExcludedFilename(fileName)) {
            continue;
         }
         
         // Determine if this is a binary file
         bool isBinary = isBinaryFileType(extension);
         bool isImage = isImageFileType(extension);
         bool shouldIndex = isIndexableFileType(extension);
         
         if (isImage) {
            // For image files, use the "image" type and don't set line numbers
            Symbol fileSymbol(fileName, "image", filePath, 0, 0);
            addSymbolNoLock(fileSymbol);
         } else if (isBinary) {
            // For other binary files, use "binary" type and don't set line numbers
            Symbol fileSymbol(fileName, "binary", filePath, 0, 0);
            addSymbolNoLock(fileSymbol);
         } else {
            // For all text files, add as a symbol with proper line counts
            int fileLines = 0;
            
            std::string content;
            Error error = readStringFromFile(child, &content);
            if (!error) {
               // Count the number of lines in the file
               fileLines = std::count(content.begin(), content.end(), '\n') + 1;
            }
            
            Symbol fileSymbol(fileName, "file", filePath, 1, fileLines);
            addSymbolNoLock(fileSymbol);
            
            // Only index the content of recognized file types
            if (shouldIndex) {
               indexFileByType(child);
            }
         }
         
         filesIndexed++;
      }
   }
   
   // We've processed all children in this directory, no need to add to path
}

Error SymbolIndex::buildIndex(const FilePath& dir) {   
   // Store the current working directory
   std::string workingDir;
   std::string dirId;
   bool indexExists = false;
   bool dirChanged = true;
   
   // First use a scoped lock to check if we can use existing index
   {
      std::lock_guard<std::mutex> lock(mutex_);
      workingDir = dir.getAbsolutePath();
      
      // If this is a different directory than the last one we indexed,
      // reset traversal path
      if (workingDir != currentWorkingDir_) {
         traversalPath_.clear();
      }
      
      currentWorkingDir_ = workingDir;
      
      // Check if an index already exists for this directory
      dirId = getDirectoryId(workingDir);
      indexExists = !dirId.empty();

      if (indexExists) {
         // Load the existing index
         Error error = loadIndexFromStorageNoLock(dirId);
         if (error) {
            // If we can't load the index, reset traversal path
            traversalPath_.clear();
         }
         
         // Check if traversal is in progress
         if (!traversalPath_.empty()) {
            // Continue traversal from where we left off - no need to check for changes
            // The indexBuilt_ flag will already be true from loadIndexFromStorageNoLock
         } else {
            // Check if anything has changed since the last indexing
            dirChanged = hasDirectoryChanged(dir);
            if (!dirChanged) {
               // Nothing changed on disk, but we still need to re-index open documents
               // since unsaved files can change without affecting disk files
               indexBuilt_ = true;
               indexOpenDocuments();
               return Success();
            }
         }
      }
   }
   
   // Initialize the start time for timeout tracking
   indexingStartTime_ = std::chrono::steady_clock::now();
   
   // If traversal is in progress, don't rebuild or update incrementally
   if (!traversalPath_.empty()) {
      // We'll continue indexing below
   }
   // Otherwise if index exists but has changed, try incremental update
   else if (indexExists && dirChanged) {
      // Something changed, do incremental update
      try {
         Error error = updateIndexIncrementally(dir);
         if (!error) {
            // Re-acquire the lock to set indexBuilt_ flag
            std::lock_guard<std::mutex> lock(mutex_);
            indexBuilt_ = true;
            return Success();
         }
      } catch (...) {
         // If incremental update fails, fallback to full reindex
      }
   }
   
   // Re-acquire the lock for full traversal
   std::lock_guard<std::mutex> lock(mutex_);
   
   // Get or create directory ID if needed
   if (dirId.empty()) {
      dirId = ensureStorageDir(dir);
      if (dirId.empty()) {
         return systemError(boost::system::errc::operation_not_permitted,
                          "Failed to create storage directory", ERROR_LOCATION);
      }
   }
   
   // If traversal isn't already in progress, initialize state for a full index
   if (traversalPath_.empty()) {
      // Full reindex
      symbolMap_.clear();
      pendingFiles_.clear();
      
      // Get directory structure
      directoryFiles_ = getCurrentDirectoryStructure(dir);
      
      // Calculate checksums
      calculateFileChecksums(dir);
   }
   
   // Start or continue traversal
   size_t filesIndexed = 0;
   traverseDirectory(dir, filesIndexed);
   
   // Mark as built
   indexBuilt_ = true;
   
   // Index open documents from editor (this will override disk-based symbols for open files)
   indexOpenDocuments();
   
   // Save all index data, including traversal path
   Error error = saveAllToStorage(dirId);
   if (error)
      LOG_ERROR(error);
   
   return Success();
}

// Process pending files up to the batch limit
Error SymbolIndex::processPendingFiles(const std::string& dirId) {
   std::lock_guard<std::mutex> lock(mutex_);
   
   // Initialize the start time for timeout tracking
   indexingStartTime_ = std::chrono::steady_clock::now();
   
   // Check if we have pending files
   if (pendingFiles_.empty()) {
      return Success();
   }
   
   // Create a copy of the pending files list and clear the original
   std::vector<std::string> filesToProcess;
   
   // Take up to MAX_FILES_PER_BATCH files
   size_t count = std::min(pendingFiles_.size(), MAX_FILES_PER_BATCH);
   filesToProcess.insert(filesToProcess.end(), 
                        pendingFiles_.begin(), 
                        pendingFiles_.begin() + count);
   
   // Remove the files we're about to process from the pending list
   pendingFiles_.erase(pendingFiles_.begin(), pendingFiles_.begin() + count);
   
   // Process each file
   size_t filesIndexed = 0;
   for (const std::string& filePath : filesToProcess) {
      // Check for timeout after each file
      if (hasTimedOut()) {
         // Put remaining files back in the pending list
         for (size_t i = filesIndexed; i < filesToProcess.size(); i++) {
            pendingFiles_.insert(pendingFiles_.begin(), filesToProcess[i]);
         }
         break;
      }
      
      FilePath file(filePath);
      
      // Skip if file doesn't exist or if it's a directory (we'll handle directories differently)
      if (!file.exists() || file.isDirectory()) {
         continue;
      }
      
      // Add the file as a symbol
      std::string fileName = file.getFilename();
      std::string extension = file.getExtensionLowerCase();
      bool isBinary = isBinaryFileType(extension);
      bool isImage = isImageFileType(extension);
      if (isImage) {
         // For image files, use "image" type
         Symbol fileSymbol(fileName, "image", filePath, 0, 0);
         addSymbolNoLock(fileSymbol);
      } else if (isBinary) {
         // For other binary files, use "binary" type
         Symbol fileSymbol(fileName, "binary", filePath, 0, 0);
         addSymbolNoLock(fileSymbol);
      } else {
         // Get the file size to determine how many lines it has
         int fileLines = 0;
         std::string content;
         Error error = readStringFromFile(file, &content);
         if (!error) {
            // Count the number of lines in the file
            fileLines = std::count(content.begin(), content.end(), '\n') + 1;
         }
         
         Symbol fileSymbol(fileName, "file", filePath, 1, fileLines);
         addSymbolNoLock(fileSymbol);
         
         // Index the file if it's indexable
         // Count lines for all text files (not just indexable ones)
         indexFileByType(file);
      }
      
      filesIndexed++;
      
      // If it's a directory, traverse it
      if (file.isDirectory()) {
         traverseDirectory(file, filesIndexed);
      }
      
      // Stop if we've reached the limit or timed out
      if (filesIndexed >= MAX_FILES_PER_BATCH || hasTimedOut()) {
         break;
      }
   }
   
   // Save the updated index and pending files
   Error error = saveAllToStorage(dirId);
   if (error) {
      LOG_ERROR(error);
   }
   
   return Success();
}

// Helper to determine which indexer to use based on file extension
void SymbolIndex::indexFileByType(const FilePath& filePath) {
   // First check if the file exists and is a regular file
   if (!filePath.exists() || !filePath.isRegularFile()) {
      return;
   }
   
   std::string ext = filePath.getExtensionLowerCase();
   std::string absPath = filePath.getAbsolutePath();
   std::string filename = filePath.getFilename();
   
   // Skip binary files and non-indexable file types
   if (isBinaryFileType(ext) || !isIndexableFileType(ext)) {
      return;
   }
   
   if (ext == ".r") {
      indexRFile(filePath);
   } else if (ext == ".cpp" || ext == ".cc" || ext == ".c" || ext == ".h" || ext == ".hpp") {
      indexCppFile(filePath);
   } else if (ext == ".py") {
      indexPythonFile(filePath);
   } else if (ext == ".md" || ext == ".rmd" || ext == ".qmd") {
      indexMarkdownFile(filePath);
   } else if (ext == ".sql") {
      indexSqlFile(filePath);
   } else if (ext == ".stan") {
      indexStanFile(filePath);
   } else if (ext == ".sh" || ext == ".bash") {
      indexShellScript(filePath);
   } else if (ext == ".rd") {
      indexRdFile(filePath);
   } else if (boost::algorithm::ends_with(boost::algorithm::to_lower_copy(filename), ".rd")) {
      // Alternative check for Rd files that might have uppercase extension
      indexRdFile(filePath);
   }
}

// Safe version that doesn't hold the lock during file I/O
void SymbolIndex::indexFileByTypeSafe(const FilePath& filePath) {
   // First check if the file exists and is a regular file
   if (!filePath.exists() || !filePath.isRegularFile()) {
      return;
   }
   
   std::string ext = filePath.getExtensionLowerCase();
   std::string absPath = filePath.getAbsolutePath();
   std::string filename = filePath.getFilename();
   
   // COMPLETELY exclude certain filenames from content indexing
   if (isExcludedFilename(filename)) {
      return;
   }
   
   // Skip binary files and non-indexable file types
   if (isBinaryFileType(ext) || !isIndexableFileType(ext)) {
      return;
   }
   
   // Read file content outside of the lock
   std::string content;
   Error error = readStringFromFile(filePath, &content);
   if (error) {
      return;
   }
   
   // Perform the indexing based on the file type
   // and hold the lock only while updating the symbol map
   {
      std::lock_guard<std::mutex> lock(mutex_);
      if (ext == ".r") {
         indexRFile(filePath);
      } else if (ext == ".cpp" || ext == ".cc" || ext == ".c" || ext == ".h" || ext == ".hpp") {
         indexCppFile(filePath);
      } else if (ext == ".py") {
         indexPythonFile(filePath);
      } else if (ext == ".md" || ext == ".rmd" || ext == ".qmd") {
         indexMarkdownFile(filePath);
      } else if (ext == ".sql") {
         indexSqlFile(filePath);
      } else if (ext == ".stan") {
         indexStanFile(filePath);
      } else if (ext == ".sh" || ext == ".bash") {
         indexShellScript(filePath);
      } else if (ext == ".rd") {
         indexRdFile(filePath);
      } else if (boost::algorithm::ends_with(boost::algorithm::to_lower_copy(filename), ".rd")) {
         // Alternative check for Rd files that might have uppercase extension
         indexRdFile(filePath);
      }
   }
}

// R file indexing - using patterns from r_highlight_rules.js
void SymbolIndex::indexRFile(const FilePath& filePath) {
   
   std::string content;
   Error error = readStringFromFile(filePath, &content);
   if (error) {
      return;
   }
   
   std::string path = filePath.getAbsolutePath();
   indexRFromString(content, path);
}

void SymbolIndex::indexRFromString(const std::string& content, const std::string& path) {
   
   std::vector<std::string> lines;
   boost::split(lines, content, boost::is_any_of("\n"));
   
   // R ACEMODE PATTERNS - Exact from r_highlight_rules.js
   // reIdentifier = String.raw`(?:\\|_|[\p{L}\p{Nl}.][\p{L}\p{Nl}\p{Mn}\p{Mc}\p{Nd}\p{Pc}.]*)`;
   // Simplified version for C++ regex compatibility
   std::regex reIdentifier(R"([A-Za-z._][A-Za-z0-9._]*)");
   
   // R assignment operators from acemode - use exact pattern from r_highlight_rules.js
   // regex : ":::|::|:=|\\|>|=>|%%|>=|<=|==|!=|<<-|->>|->|<-|\\|\\||&&|=|\\+|-|\\*\\*?|/|\\^|>|<|!|&|\\||~|\\$|:|@|\\?"
   // Extract just the assignment operators (not all operators)
   std::regex assignmentOps(R"((<<-|->>|->|<-|:=|=))");
   
   // Function keyword patterns
   std::regex functionKeyword(R"(\bfunction\s*\()");
   std::regex lambdaFunction(R"(\\)"); // Backslash as function alias (R 4.2.0+)
   
   // R keywords from acemode
   std::set<std::string> keywords = {"function", "if", "else", "in", "break", "next", "repeat", "for", "while"};
   
   // Special functions from acemode
   std::set<std::string> specialFunctions = {
      "return", "switch", "try", "tryCatch", "stop", "warning", "message",
      "require", "library", "attach", "detach", "source", "setMethod", 
      "setGeneric", "setGroupGeneric", "setClass", "setRefClass", "R6Class", 
      "UseMethod", "NextMethod"
   };
   
   // Built-in constants from acemode
   std::set<std::string> builtinConstants = {
      "NULL", "NA", "TRUE", "FALSE", "T", "F", "Inf", "NaN", 
      "NA_integer_", "NA_real_", "NA_character_", "NA_complex_"
   };
   
   // Pattern for function calls: reIdentifier + "(?=\\s*\\()"
   std::regex functionCallPattern(R"(([A-Za-z._][A-Za-z0-9._]*)\s*\()");
   
   // Pattern for package access: reIdentifier + "(?=\\s*::)"
   std::regex packageAccessPattern(R"(([A-Za-z._][A-Za-z0-9._]*)\s*::)");
   
   // Context tracking
   std::string currentNamespace = "";
   std::string currentFunction = "";
   int currentFunctionStart = -1;
   std::vector<std::pair<std::string, int>> functionStack; // Track nested functions
   
   for (size_t i = 0; i < lines.size(); ++i) {
      const std::string& line = lines[i];
      std::string trimmedLine = line;
      size_t firstNonSpace = trimmedLine.find_first_not_of(" \t");
      if (firstNonSpace != std::string::npos) {
         trimmedLine.erase(0, firstNonSpace);
      } else {
         trimmedLine.clear(); // String contains only whitespace
      }
      
      // Skip comments (starts with #)
      if (trimmedLine.empty() || (!trimmedLine.empty() && trimmedLine[0] == '#')) {
         continue;
      }
      
      // Check for function definitions using assignment operators
      std::smatch assignMatch;
      if (std::regex_search(line, assignMatch, assignmentOps)) {
         size_t assignPos = assignMatch.position();
         std::string assignOp = assignMatch.str();
         
         // Extract potential function name before assignment
         std::string beforeAssign = line.substr(0, assignPos);
         
         // Trim trailing whitespace from beforeAssign
         size_t lastNonSpace = beforeAssign.find_last_not_of(" \t");
         if (lastNonSpace != std::string::npos) {
            beforeAssign = beforeAssign.substr(0, lastNonSpace + 1);
         }
         
         std::smatch nameMatch;
         if (std::regex_search(beforeAssign, nameMatch, reIdentifier)) {
            std::string potentialName = nameMatch.str();
            
            // Check if function keyword appears immediately after assignment (function definition)
            std::string afterAssign = line.substr(assignPos + assignOp.length());
            
            // Trim leading whitespace from afterAssign
            size_t firstNonSpace = afterAssign.find_first_not_of(" \t");
            if (firstNonSpace != std::string::npos) {
               afterAssign = afterAssign.substr(firstNonSpace);
            }
            
            // Check current line first
            bool foundFunction = false;
            if (afterAssign.length() >= 9 && afterAssign.substr(0, 9) == "function(") {
               foundFunction = true;
            }
            
            // If not found on current line, check next few lines for function keyword
            // (handles multi-line assignments like "name \n <- \n function(...)")
            if (!foundFunction && afterAssign.empty() && i + 1 < lines.size()) {
               for (size_t nextLine = i + 1; nextLine < std::min(i + 4, lines.size()); ++nextLine) {
                  std::string nextLineContent = lines[nextLine];
                  
                  // Trim whitespace
                  size_t nextFirstNonSpace = nextLineContent.find_first_not_of(" \t");
                  if (nextFirstNonSpace != std::string::npos) {
                     nextLineContent = nextLineContent.substr(nextFirstNonSpace);
                  }
                  
                  // Skip empty lines and comments
                  if (nextLineContent.empty() || nextLineContent[0] == '#') {
                     continue;
                  }
                  
                  // Check if this line starts with function(
                  if (nextLineContent.length() >= 9 && nextLineContent.substr(0, 9) == "function(") {
                     foundFunction = true;
                     break;
                  } else {
                     // If we hit non-whitespace that's not function(, stop looking
                     break;
                  }
               }
            }
            
            if (foundFunction) {
               // Found function definition
               std::string signature = "function()";
               
               // Extract signature
               std::smatch funcMatch;
               if (std::regex_search(afterAssign, funcMatch, functionKeyword)) {
                  size_t parenPos = afterAssign.find('(');
                  if (parenPos != std::string::npos) {
                     // Find matching closing paren
                     int parenCount = 0;
                     size_t endPos = parenPos;
                     for (size_t p = parenPos; p < afterAssign.length(); ++p) {
                        if (afterAssign[p] == '(') parenCount++;
                        else if (afterAssign[p] == ')') {
                           parenCount--;
                           if (parenCount == 0) {
                              endPos = p;
                              break;
                           }
                        }
                     }
                     if (endPos > parenPos && parenPos >= 8) {
                     signature = afterAssign.substr(parenPos - 8, endPos - parenPos + 9); // Include "function"
                     }
                  }
               }
               
               // Find function end by tracking braces
               int endLine = i;
               int braceCount = 0;
               bool foundOpenBrace = false;
               
               for (size_t j = i; j < lines.size(); ++j) {
                  const std::string& fLine = lines[j];
                  for (char c : fLine) {
                     if (c == '{') {
                        braceCount++;
                        foundOpenBrace = true;
                     } else if (c == '}') {
                        braceCount--;
                        if (foundOpenBrace && braceCount == 0) {
                           endLine = j;
                           goto function_end_found;
                        }
                     }
                  }
               }
               function_end_found:
               
               // Create symbol
               std::string parents = currentNamespace;
               if (!currentFunction.empty()) {
                  parents += (parents.empty() ? "" : "::") + currentFunction;
               }
               
               Symbol symbol(potentialName, "function", path, i + 1, endLine + 1, parents, signature);
               addSymbolNoLock(symbol);
               
               // Update function context
               functionStack.push_back({currentFunction, currentFunctionStart});
               currentFunction = potentialName;
               currentFunctionStart = i;
            }
         }
      }
      
      // Check for lambda functions (backslash syntax)
      if (std::regex_search(line, lambdaFunction)) {
         // Lambda function found - these are typically inline
         Symbol symbol("(lambda)", "function", path, i + 1, i + 1, currentFunction, "\\(...)");
         addSymbolNoLock(symbol);
      }
      
      // Check for function calls and method definitions
      std::smatch callMatch;
      std::string searchLine = line;
      while (std::regex_search(searchLine, callMatch, functionCallPattern)) {
         std::string funcName = callMatch[1].str();
         
         // Continue searching in the rest of the line
         size_t pos = callMatch.position() + callMatch.length();
         if (pos >= searchLine.length()) break;
         searchLine = searchLine.substr(pos);
      }
      
      // Check for S4 method definitions
      std::regex setMethodPattern(R"(setMethod\s*\(\s*["']([^"']+)["'])");
      if (std::regex_search(line, callMatch, setMethodPattern)) {
         std::string methodName = callMatch[1].str();
         Symbol symbol(methodName, "method", path, i + 1, i + 1, currentNamespace, "setMethod(\"" + methodName + "\")");
         addSymbolNoLock(symbol);
      }
      
      // Check for namespace context
      std::regex namespacePattern(R"((library|require)\s*\(\s*["']?([^"')]+)["']?\s*\))");
      if (std::regex_search(line, callMatch, namespacePattern)) {
         currentNamespace = callMatch[2].str();
      }
      
      // Track function scope ending
      if (!functionStack.empty()) {
         int braceCount = 0;
         for (char c : line) {
            if (c == '}') braceCount--;
         }
         if (braceCount < 0) {
            // Function ended, restore previous context
            auto prev = functionStack.back();
            functionStack.pop_back();
            currentFunction = prev.first;
            currentFunctionStart = prev.second;
         }
      }
   }
}

// C/C++ file indexing - using patterns from c_cpp_highlight_rules.js
void SymbolIndex::indexCppFile(const FilePath& filePath) {
   std::string content;
   Error error = readStringFromFile(filePath, &content);
   if (error) {
      return;
   }
   
   std::string path = filePath.getAbsolutePath();
   indexCppFromString(content, path);
}

void SymbolIndex::indexCppFromString(const std::string& content, const std::string& path) {
   std::vector<std::string> lines;
   boost::split(lines, content, boost::is_any_of("\n"));
   
   // C++ ACEMODE PATTERNS - Exact from c_cpp_highlight_rules.js
   // C++ keywords from acemode
   std::set<std::string> keywords = {
      "alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand",
      "bitor", "bool", "break", "case", "catch", "char", "char16_t",
      "char32_t", "class", "compl", "const", "constexpr",
      "const_cast", "continue", "decltype", "default", "delete",
      "do", "double", "dynamic_cast", "else", "enum", "explicit",
      "export", "extern", "false", "float", "for", "friend", "goto",
      "if", "inline", "int", "in", "long", "mutable", "namespace",
      "new", "noexcept", "not", "not_eq", "nullptr", "or", "or_eq",
      "private", "protected", "public", "register", "reinterpret_cast",
      "return", "short", "signed", "sizeof", "sizeof...",
      "static", "static_assert", "static_cast", "struct", "switch",
      "template", "this", "thread_local", "throw", "true", "try",
      "typedef", "typeid", "typeof", "typename", "union", "unsigned",
      "using", "virtual", "void", "volatile", "wchar_t", "while",
      "xor", "xor_eq"
   };
   
   // Built-in constants from acemode
   std::set<std::string> builtinConstants = {"NULL"};
   
   // Identifier pattern: [a-zA-Z_$][a-zA-Z0-9_$]*\b
   std::regex identifierPattern(R"([a-zA-Z_$][a-zA-Z0-9_$]*)");
   
   // Operator pattern from acemode
   std::regex operatorPattern(R"(operator\s*(?:new\s*\[\]|delete\s*\[\]|>>=|<<=|->*|<<|>>|&&|\|\||==|!=|<=|>=|::|->|\.\*|\+\+|--|&=|\^=|%=|\+=|-=|\*=|/=|<|>|!|\$|&|\||[+\-*/\^~=%()])| operator\s+[a-zA-Z_]+(?:&&|&|\*)?/)");
   
   // Class/struct definition
   std::regex classPattern(R"(^\s*(class|struct|union)\s+([a-zA-Z_][a-zA-Z0-9_]*))");
   
   // Namespace definition
   std::regex namespacePattern(R"(^\s*namespace\s+([a-zA-Z_][a-zA-Z0-9_]*))");
   
   // Function definition patterns
   std::regex functionPattern(R"(^\s*(?:static\s+|inline\s+|virtual\s+|explicit\s+|constexpr\s+|extern\s+)*(?:[a-zA-Z_][a-zA-Z0-9_]*\s*[*&]*\s+)+([a-zA-Z_][a-zA-Z0-9_]*)\s*\()");
   
   // Constructor/destructor pattern
   std::regex constructorPattern(R"(^\s*(?:explicit\s+)?([a-zA-Z_][a-zA-Z0-9_]*)\s*\()");
   std::regex destructorPattern(R"(^\s*~([a-zA-Z_][a-zA-Z0-9_]*)\s*\()");
   
   // Template definition
   std::regex templatePattern(R"(^\s*template\s*<)");
   
   // Typedef/using
   std::regex typedefPattern(R"(^\s*(?:typedef|using)\s+.+\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*[;=])");
   
   // Enum definition
   std::regex enumPattern(R"(^\s*enum\s+(?:class\s+)?([a-zA-Z_][a-zA-Z0-9_]*))");
   
   // Preprocessor directives
   std::regex includePattern(R"(^\s*#\s*include\s*[<"](.*)[>"])");
   std::regex definePattern(R"(^\s*#\s*define\s+([a-zA-Z_][a-zA-Z0-9_]*))");
   
   std::string currentNamespace;
   std::string currentClass;
   std::vector<std::pair<std::string, int>> namespaceStack;
   std::vector<std::pair<std::string, int>> classStack;
   bool inTemplate = false;
   
   for (size_t i = 0; i < lines.size(); ++i) {
      const std::string& line = lines[i];
      std::string trimmedLine = boost::algorithm::trim_copy(line);
      
      // Skip empty lines and comments
      if (trimmedLine.empty() || 
          (trimmedLine.length() >= 2 && (trimmedLine.substr(0, 2) == "//" || trimmedLine.substr(0, 2) == "/*")))
         continue;
      
      // Check for template declaration
      if (std::regex_search(line, templatePattern)) {
         inTemplate = true;
         continue;
      }
            
      // Check for #define macros
      std::smatch defineMatch;
      if (std::regex_search(line, defineMatch, definePattern)) {
         std::string macroName = defineMatch[1].str();
         Symbol sym(macroName, "macro", path, i+1, i+1, "", trimmedLine);
         addSymbolNoLock(sym);
         continue;
      }
      
      // Check for namespace declaration
      std::smatch nsMatch;
      if (std::regex_search(line, nsMatch, namespacePattern)) {
         std::string namespaceName = nsMatch[1].str();
         
         // Find namespace end by tracking braces
         int endLine = i;
         int braceCount = 0;
         bool foundOpenBrace = false;
         
         for (size_t j = i; j < lines.size(); ++j) {
            const std::string& nsLine = lines[j];
            for (char c : nsLine) {
               if (c == '{') {
                  braceCount++;
                  foundOpenBrace = true;
               } else if (c == '}') {
                  braceCount--;
                  if (foundOpenBrace && braceCount == 0) {
                     endLine = j;
                     goto namespace_end_found;
                  }
               }
            }
         }
         namespace_end_found:
         
         namespaceStack.push_back({currentNamespace, i});
         currentNamespace = namespaceName;
         
         Symbol sym(namespaceName, "namespace", path, i+1, endLine+1, "", "namespace " + namespaceName);
         addSymbolNoLock(sym);
         continue;
      }
      
      // Check for class/struct/union declaration
      std::smatch classMatch;
      if (std::regex_search(line, classMatch, classPattern)) {
         std::string classType = classMatch[1].str(); // class, struct, or union
         std::string className = classMatch[2].str();
         
         // Find class end by tracking braces
         int endLine = i;
         int braceCount = 0;
         bool foundOpenBrace = false;
         
         for (size_t j = i; j < lines.size(); ++j) {
            const std::string& cLine = lines[j];
            for (char c : cLine) {
               if (c == '{') {
                  braceCount++;
                  foundOpenBrace = true;
               } else if (c == '}') {
                  braceCount--;
                  if (foundOpenBrace && braceCount == 0) {
                     endLine = j;
                     goto class_end_found;
                  }
               }
            }
         }
         class_end_found:
         
         std::string signature = trimmedLine;
         if (inTemplate) {
            signature = "template " + signature;
            inTemplate = false;
         }
         
         classStack.push_back({currentClass, i});
         currentClass = className;
         
         std::string parents = currentNamespace;
         Symbol sym(className, classType, path, i+1, endLine+1, parents, signature);
         addSymbolNoLock(sym);
         continue;
      }
      
      // Check for enum declaration
      std::smatch enumMatch;
      if (std::regex_search(line, enumMatch, enumPattern)) {
         std::string enumName = enumMatch[1].str();
         
         // Find enum end
         int endLine = i;
         for (size_t j = i; j < lines.size(); ++j) {
            const std::string& eLine = lines[j];
            if (eLine.find("};") != std::string::npos) {
               endLine = j;
               break;
            }
         }
         
         std::string parents = currentClass.empty() ? currentNamespace : currentNamespace + "::" + currentClass;
         Symbol sym(enumName, "enum", path, i+1, endLine+1, parents, trimmedLine);
         addSymbolNoLock(sym);
         continue;
      }
            
      // Check for destructors
      std::smatch destructorMatch;
      if (std::regex_search(line, destructorMatch, destructorPattern)) {
         std::string destructorName = "~" + destructorMatch[1].str();
         
         // Find function end
         int endLine = i;
         if (line.find("{") != std::string::npos) {
            int braceCount = 1;
            for (size_t j = i + 1; j < lines.size(); ++j) {
               const std::string& fLine = lines[j];
               for (char c : fLine) {
                  if (c == '{') braceCount++;
                  else if (c == '}') {
                     braceCount--;
                     if (braceCount == 0) {
                        endLine = j;
                        goto destructor_end_found;
                     }
                  }
               }
            }
            destructor_end_found:;
         }
         
         std::string signature = trimmedLine;
         std::string parents = currentClass.empty() ? currentNamespace : currentNamespace + "::" + currentClass;
         Symbol sym(destructorName, "destructor", path, i+1, endLine+1, parents, signature);
         addSymbolNoLock(sym);
         continue;
      }
      
      // Check for constructors (same name as current class)
      if (!currentClass.empty()) {
         std::smatch constructorMatch;
         if (std::regex_search(line, constructorMatch, constructorPattern)) {
            std::string name = constructorMatch[1].str();
            if (name == currentClass) {
               // This is a constructor
               int endLine = i;
               if (line.find("{") != std::string::npos) {
                  int braceCount = 1;
                  for (size_t j = i + 1; j < lines.size(); ++j) {
                     const std::string& fLine = lines[j];
                     for (char c : fLine) {
                        if (c == '{') braceCount++;
                        else if (c == '}') {
                           braceCount--;
                           if (braceCount == 0) {
                              endLine = j;
                              goto constructor_end_found;
                           }
                        }
                     }
                  }
                  constructor_end_found:;
               }
               
               std::string signature = trimmedLine;
               std::string parents = currentNamespace;
               Symbol sym(name, "constructor", path, i+1, endLine+1, parents, signature);
               addSymbolNoLock(sym);
               continue;
            }
         }
      }
      
      // Check for function declarations/definitions
      std::smatch funcMatch;
      if (std::regex_search(line, funcMatch, functionPattern)) {
         std::string funcName = funcMatch[1].str();
         
         // Skip keywords and operators
         if (keywords.count(funcName) > 0 || builtinConstants.count(funcName) > 0)
            continue;
         
         // Skip if it's the class name (constructor handled above)
         if (funcName == currentClass)
            continue;
         
         // Find function end
         int endLine = i;
         std::string signature = trimmedLine;
         
         // Check if it's a declaration (ends with ;) or definition (has {)
         if (line.find(";") != std::string::npos) {
            // Declaration only
            endLine = i;
         } else if (line.find("{") != std::string::npos) {
            // Function definition - find end of body
            int braceCount = 1;
            for (size_t j = i + 1; j < lines.size(); ++j) {
               const std::string& fLine = lines[j];
               for (char c : fLine) {
                  if (c == '{') braceCount++;
                  else if (c == '}') {
                     braceCount--;
                     if (braceCount == 0) {
                        endLine = j;
                        goto function_end_found;
                     }
                  }
               }
            }
            function_end_found:;
         } else {
            // Multi-line signature - find the opening brace or semicolon
            for (size_t j = i + 1; j < std::min(i + 10, lines.size()); ++j) {
               const std::string& nextLine = lines[j];
               signature += " " + boost::algorithm::trim_copy(nextLine);
               
               if (nextLine.find(";") != std::string::npos) {
                  endLine = j;
                  break;
               } else if (nextLine.find("{") != std::string::npos) {
                  int braceCount = 1;
                  for (size_t k = j + 1; k < lines.size(); ++k) {
                     const std::string& bodyLine = lines[k];
                     for (char c : bodyLine) {
                        if (c == '{') braceCount++;
                        else if (c == '}') {
                           braceCount--;
                           if (braceCount == 0) {
                              endLine = k;
                              goto multiline_function_end_found;
                           }
                        }
                     }
                  }
                  multiline_function_end_found:
                  break;
               }
            }
         }
         
         signature = normalizeWhitespace(signature);
         if (inTemplate) {
            signature = "template " + signature;
            inTemplate = false;
         }
         
         std::string parents = currentClass.empty() ? currentNamespace : currentNamespace + "::" + currentClass;
         Symbol sym(funcName, "function", path, i+1, endLine+1, parents, signature);
         addSymbolNoLock(sym);
      }
      
      // Reset template flag if we processed something
      if (inTemplate && !trimmedLine.empty() && trimmedLine.back() != '\\') {
         inTemplate = false;
      }
   }
}

// Python file indexing - using patterns from python_highlight_rules.js
void SymbolIndex::indexPythonFile(const FilePath& filePath) {   
   std::string content;
   Error error = readStringFromFile(filePath, &content);
   if (error) {
      return;
   }
   
   std::string path = filePath.getAbsolutePath();
   indexPythonFromString(content, path);
}

void SymbolIndex::indexPythonFromString(const std::string& content, const std::string& path) {
   std::vector<std::string> lines;
   boost::split(lines, content, boost::is_any_of("\n"));
   
   // PYTHON ACEMODE PATTERNS - Exact from python_highlight_rules.js
   // Python keywords
   std::set<std::string> keywords = {
      "False", "None", "True", "and", "as", "assert", "async", "await", "break", 
      "class", "continue", "def", "del", "elif", "else", "except", "finally", 
      "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal", 
      "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"
   };
   
   // Built-in constants from acemode
   std::set<std::string> builtinConstants = {
      "NotImplemented", "Ellipsis", "__debug__"
   };
   
   // Built-in functions from acemode
   std::set<std::string> builtinFunctions = {
      "abs", "all", "any", "ascii", "basestring", "bin", "bool", "breakpoint", 
      "bytearray", "bytes", "callable", "chr", "classmethod", "cmp", "compile", 
      "complex", "delattr", "dict", "dir", "divmod", "eumerate", "eval", "execfile", 
      "exec", "filter", "float", "format", "frozenset", "getattr", "globals", 
      "hasattr", "hash", "help", "hex", "id", "input", "int", "isinstance", 
      "issubclass", "iter", "len", "list", "locals", "long", "map", "max", 
      "memoryview", "min", "next", "object", "oct", "open", "ord", "pow", "print", 
      "property", "range", "raw_input", "reduce", "reload", "repr", "reversed", 
      "round", "set", "setattr", "slice", "sorted", "staticmethod", "str", "sum", 
      "super", "tuple", "type", "unichr", "unicode", "vars", "xrange", "zip", 
      "__import__"
   };
   
   // Python identifier pattern: [a-zA-Z_][a-zA-Z0-9_]*\b
   std::regex identifierPattern(R"([a-zA-Z_][a-zA-Z0-9_]*)");
   
   // Function definition: def + identifier + (
   std::regex funcDefPattern(R"(^\s*def\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*\()");
   
   // Class definition: class + identifier
   std::regex classDefPattern(R"(^\s*class\s+([a-zA-Z_][a-zA-Z0-9_]*))");
   
   // Lambda function: lambda
   std::regex lambdaPattern(R"(\blambda\b)");
   
   // Decorator pattern: @[a-zA-Z_][a-zA-Z0-9._]*\b
   std::regex decoratorPattern(R"(@[a-zA-Z_][a-zA-Z0-9._]*\b)");
   
   // Import statements: from/import
   std::regex importPattern(R"(^\s*(from|import)\s+([a-zA-Z_][a-zA-Z0-9_.]*)(?:\s+import\s+(.+))?)");
   
   std::string currentClass;
   std::string currentModule;
   int currentIndentation = 0;
   std::vector<std::pair<std::string, int>> classStack; // Track nested classes
   
   for (size_t i = 0; i < lines.size(); ++i) {
      const std::string& line = lines[i];
      
      // Skip empty lines and comments (starts with #)
      std::string trimmedLine = boost::algorithm::trim_copy(line);
      if (trimmedLine.empty() || (!trimmedLine.empty() && trimmedLine[0] == '#'))
         continue;
      
      // Calculate current indentation
      int indentation = 0;
      while (indentation < static_cast<int>(line.length()) && (line[indentation] == ' ' || line[indentation] == '\t'))
         indentation++;
         
      // Handle indentation changes (Python scope management)
      if (indentation < currentIndentation) {
         // Leaving nested scopes
         while (!classStack.empty() && classStack.back().second >= indentation) {
            classStack.pop_back();
         }
         currentClass = classStack.empty() ? "" : classStack.back().first;
      }
      currentIndentation = indentation;
      
      // Check for decorators
      std::smatch decoratorMatch;
      if (std::regex_search(line, decoratorMatch, decoratorPattern)) {
         std::string decoratorName = decoratorMatch.str().substr(1); // Remove @
         Symbol sym(decoratorName, "decorator", path, i+1, i+1, currentClass, "@" + decoratorName);
         addSymbolNoLock(sym);
      }
      
      // Check for class definition
      std::smatch classMatch;
      if (std::regex_search(line, classMatch, classDefPattern)) {
         std::string className = classMatch[1].str();
         
         // Find class end by tracking indentation
         int endLine = i;
         for (size_t j = i + 1; j < lines.size(); ++j) {
            const std::string& cLine = lines[j];
            if (cLine.empty()) continue;
            
            int cIndent = 0;
            while (cIndent < static_cast<int>(cLine.length()) && (cLine[cIndent] == ' ' || cLine[cIndent] == '\t'))
               cIndent++;
               
            if (cIndent <= indentation && !boost::algorithm::trim_copy(cLine).empty())
               break;
               
            endLine = j;
         }
         
         // Extract full class signature
         std::string signature = boost::algorithm::trim_copy(line);
         if (signature.back() != ':') {
            // Multi-line class definition, find the colon
            for (size_t j = i + 1; j < lines.size(); ++j) {
               std::string nextLine = boost::algorithm::trim_copy(lines[j]);
               signature += " " + nextLine;
               if (nextLine.find(':') != std::string::npos) break;
            }
         }
         
         std::string parents = currentClass;
         Symbol sym(className, "class", path, i+1, endLine+1, parents, signature);
         addSymbolNoLock(sym);
         
         // Update class context
         classStack.push_back({currentClass, indentation});
         currentClass = className;
         continue;
      }
      
      // Check for function definition
      std::smatch funcMatch;
      if (std::regex_search(line, funcMatch, funcDefPattern)) {
         std::string funcName = funcMatch[1].str();
         
         // Extract complete function signature
         std::string signature = boost::algorithm::trim_copy(line);
         int parenCount = 0;
         bool signatureComplete = false;
         
         // Check if signature is complete on first line
         for (char c : line) {
            if (c == '(') parenCount++;
            else if (c == ')') {
               parenCount--;
               if (parenCount == 0) {
                  signatureComplete = true;
                  break;
               }
            }
         }
         
         // If signature spans multiple lines
         if (!signatureComplete) {
            for (size_t j = i + 1; j < lines.size() && !signatureComplete; ++j) {
               const std::string& nextLine = lines[j];
               signature += " " + boost::algorithm::trim_copy(nextLine);
               
               for (char c : nextLine) {
                  if (c == '(') parenCount++;
                  else if (c == ')') {
                     parenCount--;
                     if (parenCount == 0) {
                        signatureComplete = true;
                        break;
                     }
                  }
               }
               
               if (nextLine.find(':') != std::string::npos) break;
            }
         }
         
         signature = normalizeWhitespace(signature);
         
         // Find function end by tracking indentation
         int endLine = i;
         for (size_t j = i + 1; j < lines.size(); ++j) {
            const std::string& fLine = lines[j];
            if (fLine.empty()) continue;
            
            int fIndent = 0;
            while (fIndent < static_cast<int>(fLine.length()) && (fLine[fIndent] == ' ' || fLine[fIndent] == '\t'))
               fIndent++;
               
            if (fIndent <= indentation && !boost::algorithm::trim_copy(fLine).empty())
               break;
               
            endLine = j;
         }
         
         std::string parents = currentClass;
         Symbol sym(funcName, "function", path, i+1, endLine+1, parents, signature);
         addSymbolNoLock(sym);
         continue;
      }
      
      // Check for lambda functions
      if (std::regex_search(line, lambdaPattern)) {
         Symbol sym("(lambda)", "function", path, i+1, i+1, currentClass, "lambda");
         addSymbolNoLock(sym);
      }
   }
}

// Markdown/R Markdown/Quarto file indexing
void SymbolIndex::indexMarkdownFile(const FilePath& filePath) {   
   std::string content;
   Error error = readStringFromFile(filePath, &content);
   if (error) {
      return;
   }
   
   std::string path = filePath.getAbsolutePath();
   indexMarkdownFromString(content, path);
}

void SymbolIndex::indexMarkdownFromString(const std::string& content, const std::string& path) {
   std::vector<std::string> lines;
   boost::split(lines, content, boost::is_any_of("\n"));
   
   // Regex for headers - capture the hashes separately from the title
   std::regex headerRegex(R"(^(#{1,6})\s+(.*))");
   
   // Regex for code blocks
   std::regex codeBlockStartRegex(R"(^```\{?(\w*)(.*)\}?)");
   std::regex codeBlockEndRegex(R"(^```)");
   
   bool inCodeBlock = false;
   std::string codeBlockLanguage;
   std::string chunkName;
   std::vector<std::string> codeBlockLines;
   int codeBlockStartLine = 0;
   int chunkCounter = 1; // Counter for unnamed chunks
   
   // Keep track of last header for setting the correct line end
   Symbol* lastHeaderSymbol = nullptr;
   
   for (size_t i = 0; i < lines.size(); ++i) {
      const std::string& line = lines[i];
      
      if (!inCodeBlock) {
         // Outside code block
         
         // Check for headers - store title without hash marks
         std::smatch headerMatch;
         if (std::regex_search(line, headerMatch, headerRegex)) {
            // If we have a previous header, set its end line to the current line - 1
            if (lastHeaderSymbol != nullptr) {
               lastHeaderSymbol->lineEnd = i; // End line is exclusive (just before the new header)
            }
            
            int level = headerMatch[1].length();
            // Store only the header text without hash symbols and trim whitespace
            std::string title = headerMatch[2];
            
            // Clean the title - remove trailing whitespace and any trailing hash characters
            boost::algorithm::trim_right(title);
            size_t lastNonHash = title.find_last_not_of('#');
            if (lastNonHash != std::string::npos && lastNonHash < title.length() - 1) {
               // If there are trailing hashes, trim them and any whitespace before them
               title = title.substr(0, lastNonHash + 1);
               boost::algorithm::trim_right(title);
            }
            
            // Add the new header symbol
            Symbol sym(title, "header" + std::to_string(level), path, i+1, i+1);
            addSymbolNoLock(sym);
            
            // Find the newly added symbol to update later
            std::string lowerName = title;
            boost::algorithm::to_lower(lowerName);
            auto it = symbolMap_.find(lowerName);
            if (it != symbolMap_.end() && !it->second.empty()) {
               // Set the last added symbol as our new lastHeaderSymbol
               lastHeaderSymbol = &it->second.back();
            }
            
            continue;
         }
         
         // Check for code block start
         std::smatch codeBlockMatch;
         if (std::regex_search(line, codeBlockMatch, codeBlockStartRegex)) {
            inCodeBlock = true;
            codeBlockLanguage = codeBlockMatch[1];
            codeBlockLines.clear();
            codeBlockStartLine = i;
            
            // Extract chunk name if it's an R chunk
            if (boost::algorithm::iequals(codeBlockLanguage, "r")) {
               std::string chunkOptions = codeBlockMatch[2];
               
               // Extract chunk name according to the specified format
               // Name is everything after the first space after "r" until the first comma, close bracket, or whitespace
               size_t startPos = chunkOptions.find_first_not_of(" \t");
               
               if (startPos != std::string::npos) {
                  // Find the end of the name (first comma, close bracket, or whitespace)
                  size_t endPos = chunkOptions.find_first_of(" \t,}", startPos);
                  if (endPos != std::string::npos) {
                     chunkName = chunkOptions.substr(startPos, endPos - startPos);
                  } else {
                     // If no delimiter found, take the rest of the string
                     chunkName = chunkOptions.substr(startPos);
                  }
                  
                  // Trim any remaining whitespace
                  chunkName = boost::algorithm::trim_copy(chunkName);
               }
               
               // If we didn't find a valid name, use a numeric counter
               if (chunkName.empty()) {
                  chunkName = "chunk_" + std::to_string(chunkCounter++);
               }
               
               // Add the chunk as a symbol
               Symbol chunkSym(chunkName, "chunk", path, i+1, 0); // End line will be set when we find ```
               addSymbolNoLock(chunkSym);
            }
            
            continue;
         }
      } else {
         // Inside code block
         
         // Check for code block end
         if (std::regex_search(line, codeBlockEndRegex)) {
            inCodeBlock = false;
            
            // If this was an R chunk, update the end line of the chunk symbol
            if (boost::algorithm::iequals(codeBlockLanguage, "r") && !chunkName.empty()) {
               // Find the chunk symbol we added and update its end line
               for (auto& pair : symbolMap_) {
                  for (auto& sym : pair.second) {
                     if (sym.type == "chunk" && sym.name == chunkName && 
                         sym.filePath == path && sym.lineStart == codeBlockStartLine+1) {
                        sym.lineEnd = i+1;
                        break;
                     }
                  }
               }
            }
            
            // Process code block based on language
            if (boost::algorithm::iequals(codeBlockLanguage, "r")) {
               // Parse R code in the block
               std::string rCode = boost::algorithm::join(codeBlockLines, "\n");
               
               // Regex for function definitions in R code blocks - support both <- and =
               std::regex rFuncRegexArrow(R"((\w+)\s*<-\s*(?:(?:\s|\n)*)function\s*\()");
               std::regex rFuncRegexEquals(R"((\w+)\s*=\s*(?:(?:\s|\n)*)function\s*\()");
               
               // REMOVED: Variable regex - no longer indexing variables
               
               // We need to also detect multi-line function declarations in R chunks
               // First, find all potential variable assignments
               std::vector<std::pair<std::string, size_t>> potentialFuncs;
               // Track functions being processed to avoid duplicates with variable detection
               std::set<std::string> processedFunctions;
               {
                  std::regex potentialFuncRegex(R"((\w+)\s*(?:<-|=)\s*$)");
                  std::string::const_iterator searchStart(rCode.cbegin());
                  std::smatch potentialMatch;
                  
                  while (std::regex_search(searchStart, rCode.cend(), potentialMatch, potentialFuncRegex)) {
                     // Store the variable name and position
                     potentialFuncs.push_back(std::make_pair(potentialMatch[1], 
                                             std::distance(rCode.cbegin(), potentialMatch[0].second)));
                     searchStart = potentialMatch[0].second;
                  }
               }
               
               // Now check each potential function definition to see if it's followed by 'function'
               for (const auto& potential : potentialFuncs) {
                  // Find the next non-whitespace token after the assignment
                  size_t pos = potential.second;
                  while (pos < rCode.length() && (std::isspace(rCode[pos]) || rCode[pos] == '\n')) {
                     pos++;
                  }
                  
                  // If we find 'function' as the next token, it's a function definition
                  if (pos + 8 <= rCode.length() && rCode.substr(pos, 8) == "function") {
                     // Find opening parenthesis after "function"
                     size_t openParenPos = rCode.find("(", pos);
                     if (openParenPos == std::string::npos) continue;
                     
                     // Track parentheses to extract parameter list
                     int openParens = 1;
                     size_t paramEndPos = 0;
                     
                     // Find the matching closing parenthesis for parameters
                     for (size_t paramPos = openParenPos + 1; paramPos < rCode.length(); paramPos++) {
                        char c = rCode[paramPos];
                        if (c == '(') {
                           openParens++;
                        } else if (c == ')') {
                           openParens--;
                           if (openParens == 0) {
                              // Found the closing parenthesis for parameters
                              paramEndPos = paramPos;
                              break;
                           }
                        }
                     }
                     
                     // Extract parameter text and build signature
                     std::string signature = "function(";
                     if (paramEndPos > openParenPos) {
                        // Extract parameter text (excluding opening and closing parentheses)
                        std::string params = rCode.substr(openParenPos + 1, paramEndPos - openParenPos - 1);
                        
                        // Add parameters and closing parenthesis to signature
                        signature += params + ")";
                        
                        // Trim and normalize whitespace
                        signature = boost::algorithm::trim_copy(signature);
                        signature = normalizeWhitespace(signature);
                     } else {
                        // If we couldn't find a proper closing parenthesis
                        signature = "function()";
                     }
                     
                     // Calculate the line number in the file
                     int lineCount = 0;
                     std::string::const_iterator linePos = rCode.cbegin();
                     while (linePos < rCode.cbegin() + pos) {
                        if (*linePos == '\n') lineCount++;
                        linePos++;
                     }
                     
                     // Add 1 for 1-based indexing of R
                     int functionLine = codeBlockStartLine + 1 + lineCount + 1;
                     
                     // Calculate the end line by finding the function body's closing brace
                     int functionEndLine = functionLine;
                     
                     // Find opening brace after the "function" keyword
                     size_t openBracePos = rCode.find("{", pos + 8); // pos + 8 to skip "function"
                     if (openBracePos != std::string::npos) {
                        // Count braces to find the end of the function body
                        int openBraces = 1;
                        
                        // Find the matching closing brace
                        for (size_t i = openBracePos + 1; i < rCode.length(); i++) {
                           char c = rCode[i];
                           if (c == '{') {
                              openBraces++;
                           } else if (c == '}') {
                              openBraces--;
                              if (openBraces == 0) {
                                 // Calculate the ending line number by counting newlines
                                 int endLineCount = 0;
                                 std::string::const_iterator endPos = rCode.cbegin();
                                 while (endPos < rCode.cbegin() + i) {
                                    if (*endPos == '\n') endLineCount++;
                                    endPos++;
                                 }
                                 
                                 // Add 1 for 1-based indexing of R
                                 functionEndLine = codeBlockStartLine + 1 + endLineCount + 1;
                                 break;
                              }
                           }
                        }
                     }
                     
                     // Process this as a function
                     Symbol sym(potential.first, "function", path, functionLine, functionEndLine, chunkName, signature);
                     addSymbolNoLock(sym);
                     // Add to processed functions to avoid detecting as variable
                     processedFunctions.insert(potential.first);
                  }
               }
               
               // Process functions with <-
               std::smatch rFuncMatch;
               std::string::const_iterator searchStart(rCode.cbegin());
               
               while (std::regex_search(searchStart, rCode.cend(), rFuncMatch, rFuncRegexArrow)) {
                  processRChunkFunction(rFuncMatch, rCode, codeBlockStartLine, path, chunkName);
                  processedFunctions.insert(rFuncMatch[1]);
                  searchStart = rFuncMatch[0].second;
               }
               
               // Process functions with =
               searchStart = rCode.cbegin();
               while (std::regex_search(searchStart, rCode.cend(), rFuncMatch, rFuncRegexEquals)) {
                  processRChunkFunction(rFuncMatch, rCode, codeBlockStartLine, path, chunkName);
                  processedFunctions.insert(rFuncMatch[1]);
                  searchStart = rFuncMatch[0].second;
               }
               
               // REMOVED: Variable processing with <- and = - no longer indexing variables
            }
            
            // Reset chunk name for next chunk
            chunkName = "";
            continue;
         }
         
         // Collect code block content
         codeBlockLines.push_back(line);
      }
   }
   
   // Set the end line of the last header to the end of the file
   if (lastHeaderSymbol != nullptr) {
      lastHeaderSymbol->lineEnd = lines.size();
   }
}

// Helper function to process R functions found in R chunks
void SymbolIndex::processRChunkFunction(const std::smatch& match, const std::string& code, 
                                      int startLine, const std::string& path, const std::string& chunkName) {
   std::string name = match[1];
   
   // Calculate the line number in the file
   int lineCount = 0;
   std::string::const_iterator pos = code.cbegin();
   while (pos < match[0].first) {
      if (*pos == '\n') lineCount++;
      pos++;
   }
   
   // Add 1 for 1-based indexing of R
   int functionLine = startLine + 1 + lineCount + 1;
   
   // Find the position of "function" keyword
   size_t functionPos = code.find("function", std::distance(code.cbegin(), match[0].first));
   if (functionPos == std::string::npos) return;
   
   // Find opening parenthesis after "function"
   size_t openParenPos = code.find("(", functionPos);
   if (openParenPos == std::string::npos) return;
   
   // Build the signature as "function("
   std::string signature = "function(";
   
   // Track parentheses to extract only the parameter list
   int openParens = 1; // Start with 1 for the opening parenthesis
   size_t paramEndPos = 0;
   
   // Find the matching closing parenthesis for parameters
   for (size_t i = openParenPos + 1; i < code.length(); i++) {
      char c = code[i];
      if (c == '(') {
         openParens++;
      } else if (c == ')') {
         openParens--;
         if (openParens == 0) {
            // Found the closing parenthesis for parameters
            paramEndPos = i;
            break;
         }
      }
   }
   
   // If we found the parameter list closing parenthesis
   if (paramEndPos > openParenPos) {
      // Extract parameter text (excluding opening and closing parentheses)
      std::string params = code.substr(openParenPos + 1, paramEndPos - openParenPos - 1);
      
      // Add parameters and closing parenthesis to signature
      signature += params + ")";
      
      // Trim and normalize whitespace
      signature = boost::algorithm::trim_copy(signature);
      signature = normalizeWhitespace(signature);
   } else {
      // If we couldn't find a proper closing parenthesis
      signature = "function()";
   }
   
   // Now find the body of the function and calculate the end line
   int functionEndLine = functionLine;
   
   // Look for opening brace after the parameter list
   size_t openBracePos = code.find("{", paramEndPos);
   if (openBracePos != std::string::npos) {
      // Count braces to find the end of the function body
      int openBraces = 1;
      
      // Find the matching closing brace
      for (size_t i = openBracePos + 1; i < code.length(); i++) {
         char c = code[i];
         if (c == '{') {
            openBraces++;
         } else if (c == '}') {
            openBraces--;
            if (openBraces == 0) {
               // Calculate the ending line number by counting newlines
               int endLineCount = 0;
               std::string::const_iterator endPos = code.cbegin();
               while (endPos < code.cbegin() + i) {
                  if (*endPos == '\n') endLineCount++;
                  endPos++;
               }
               
               // Add 1 for 1-based indexing of R
               functionEndLine = startLine + 1 + endLineCount + 1;
               break;
            }
         }
      }
   }
   
   Symbol sym(name, "function", path, functionLine, functionEndLine, chunkName, signature);
   addSymbolNoLock(sym);
}

// SQL file indexing - using patterns from sql_highlight_rules.js
void SymbolIndex::indexSqlFile(const FilePath& filePath) {
   std::string content;
   Error error = readStringFromFile(filePath, &content);
   if (error) {
      return;
   }
   
   std::string path = filePath.getAbsolutePath();
   indexSqlFromString(content, path);
}

void SymbolIndex::indexSqlFromString(const std::string& content, const std::string& path) {
   std::vector<std::string> lines;
   boost::split(lines, content, boost::is_any_of("\n"));
   
   // SQL ACEMODE PATTERNS - Exact from sql_highlight_rules.js
   // SQL keywords from acemode - case insensitive
   std::set<std::string> keywords = {
      "abort", "action", "add", "after", "all", "alter", "analyze", "and", "as", 
      "asc", "attach", "autoincrement", "before", "begin", "between", "by", 
      "cascade", "case", "cast", "check", "collate", "column", "commit", 
      "conflict", "constraint", "create", "cross", "current_date", "current_time", 
      "current_timestamp", "database", "default", "deferrable", "deferred", 
      "delete", "desc", "detach", "distinct", "drop", "each", "else", "end", 
      "escape", "except", "exclusive", "exists", "explain", "fail", "for", 
      "foreign", "from", "full", "glob", "group", "having", "if", "ignore", 
      "immediate", "in", "index", "indexed", "initially", "inner", "insert", 
      "instead", "intersect", "into", "is", "isnull", "join", "key", "left", 
      "like", "limit", "match", "natural", "no", "not", "notnull", "null", 
      "of", "offset", "on", "or", "order", "outer", "plan", "pragma", "primary", 
      "query", "raise", "recursive", "references", "regexp", "reindex", "release", 
      "rename", "replace", "restrict", "right", "rollback", "row", "savepoint", 
      "select", "set", "table", "temp", "temporary", "then", "to", "transaction", 
      "trigger", "union", "unique", "update", "using", "vacuum", "values", 
      "view", "virtual", "when", "where", "with", "without"
   };
   
   // Built-in functions from acemode
   std::set<std::string> builtinFunctions = {
      "avg", "count", "first", "last", "max", "min", "sum", "ucase", "lcase", 
      "mid", "len", "round", "rank", "now", "format", "coalesce", "ifnull", 
      "iif", "isnull"
   };
   
   // Data types from acemode
   std::set<std::string> dataTypes = {
      "int", "numeric", "decimal", "date", "varchar", "char", "bigint", "float", 
      "double", "bit", "binary", "text", "set", "timestamp", "money", "real", 
      "number", "integer"
   };
   
   // SQL identifier pattern: [a-zA-Z_][a-zA-Z0-9_$]*
   std::regex identifierPattern(R"([a-zA-Z_][a-zA-Z0-9_$]*)");
   
   // CREATE statements from acemode
   std::regex createTablePattern(R"(^\s*CREATE\s+(?:(?:GLOBAL|LOCAL)\s+)?(?:TEMPORARY\s+|TEMP\s+)?TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:`|"|\[)?([a-zA-Z_][a-zA-Z0-9_$]*)(?:`|"|\])?)", std::regex::icase);
   std::regex createViewPattern(R"(^\s*CREATE\s+(?:OR\s+REPLACE\s+)?(?:MATERIALIZED\s+)?VIEW\s+(?:`|"|\[)?([a-zA-Z_][a-zA-Z0-9_$]*)(?:`|"|\])?)", std::regex::icase);
   std::regex createIndexPattern(R"(^\s*CREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:`|"|\[)?([a-zA-Z_][a-zA-Z0-9_$]*)(?:`|"|\])?)", std::regex::icase);
   std::regex createTriggerPattern(R"(^\s*CREATE\s+(?:OR\s+REPLACE\s+)?TRIGGER\s+(?:`|"|\[)?([a-zA-Z_][a-zA-Z0-9_$]*)(?:`|"|\])?)", std::regex::icase);
   std::regex createFunctionPattern(R"(^\s*CREATE\s+(?:OR\s+REPLACE\s+)?FUNCTION\s+(?:`|"|\[)?([a-zA-Z_][a-zA-Z0-9_$]*)(?:`|"|\])?)", std::regex::icase);
   std::regex createProcedurePattern(R"(^\s*CREATE\s+(?:OR\s+REPLACE\s+)?PROCEDURE\s+(?:`|"|\[)?([a-zA-Z_][a-zA-Z0-9_$]*)(?:`|"|\])?)", std::regex::icase);
   std::regex createDatabasePattern(R"(^\s*CREATE\s+DATABASE\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:`|"|\[)?([a-zA-Z_][a-zA-Z0-9_$]*)(?:`|"|\])?)", std::regex::icase);
   std::regex createSchemaPattern(R"(^\s*CREATE\s+SCHEMA\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:`|"|\[)?([a-zA-Z_][a-zA-Z0-9_$]*)(?:`|"|\])?)", std::regex::icase);
   
   // ALTER statements
   std::regex alterTablePattern(R"(^\s*ALTER\s+TABLE\s+(?:`|"|\[)?([a-zA-Z_][a-zA-Z0-9_$]*)(?:`|"|\])?)", std::regex::icase);
   
   // DROP statements
   std::regex dropPattern(R"(^\s*DROP\s+(TABLE|VIEW|INDEX|TRIGGER|FUNCTION|PROCEDURE|DATABASE|SCHEMA)\s+(?:IF\s+EXISTS\s+)?(?:`|"|\[)?([a-zA-Z_][a-zA-Z0-9_$]*)(?:`|"|\])?)", std::regex::icase);
   
   // WITH clauses (CTEs - Common Table Expressions)
   std::regex withPattern(R"(^\s*WITH\s+(?:RECURSIVE\s+)?(?:`|"|\[)?([a-zA-Z_][a-zA-Z0-9_$]*)(?:`|"|\])?\s+AS)", std::regex::icase);
   
   std::string currentDatabase;
   std::string currentSchema;
   
   for (size_t i = 0; i < lines.size(); ++i) {
      const std::string& line = lines[i];
      std::string trimmedLine = boost::algorithm::trim_copy(line);
      
      // Skip empty lines and comments (-- or /* */)
      if (trimmedLine.empty() || 
          (trimmedLine.length() >= 2 && (trimmedLine.substr(0, 2) == "--" || trimmedLine.substr(0, 2) == "/*")))
         continue;
      
      // Convert to lowercase for keyword matching
      std::string lowerLine = boost::algorithm::to_lower_copy(trimmedLine);
      
      // Check for CREATE DATABASE
      std::smatch dbMatch;
      if (std::regex_search(line, dbMatch, createDatabasePattern)) {
         std::string dbName = dbMatch[1].str();
         currentDatabase = dbName;
         Symbol sym(dbName, "database", path, i+1, i+1, "", "CREATE DATABASE " + dbName);
         addSymbolNoLock(sym);
         continue;
      }
      
      // Check for CREATE SCHEMA
      std::smatch schemaMatch;
      if (std::regex_search(line, schemaMatch, createSchemaPattern)) {
         std::string schemaName = schemaMatch[1].str();
         currentSchema = schemaName;
         Symbol sym(schemaName, "schema", path, i+1, i+1, currentDatabase, "CREATE SCHEMA " + schemaName);
         addSymbolNoLock(sym);
         continue;
      }
      
      // Check for CREATE TABLE
      std::smatch tableMatch;
      if (std::regex_search(line, tableMatch, createTablePattern)) {
         std::string tableName = tableMatch[1].str();
         
         // Find table end by looking for closing parenthesis and semicolon
         int endLine = i;
         std::string fullDef = trimmedLine;
         
         if (line.find("(") != std::string::npos) {
            int parenCount = 0;
            for (char c : line) {
               if (c == '(') parenCount++;
               else if (c == ')') parenCount--;
            }
            
            if (parenCount > 0) {
               for (size_t j = i + 1; j < lines.size(); ++j) {
                  const std::string& nextLine = lines[j];
                  fullDef += " " + boost::algorithm::trim_copy(nextLine);
                  
                  for (char c : nextLine) {
                     if (c == '(') parenCount++;
                     else if (c == ')') {
                        parenCount--;
                        if (parenCount == 0) {
                           endLine = j;
                           goto table_end_found;
                        }
                     }
                  }
               }
               table_end_found:;
            }
         }
         
         std::string parents = currentSchema.empty() ? currentDatabase : currentDatabase + "." + currentSchema;
         Symbol sym(tableName, "table", path, i+1, endLine+1, parents, "CREATE TABLE " + tableName);
         addSymbolNoLock(sym);
         continue;
      }
      
      // Check for CREATE VIEW
      std::smatch viewMatch;
      if (std::regex_search(line, viewMatch, createViewPattern)) {
         std::string viewName = viewMatch[1].str();
         
         // Find view end by looking for the end of the SELECT statement
         int endLine = i;
         std::string viewDef = trimmedLine;
         
         // Views can span multiple lines until semicolon
         for (size_t j = i + 1; j < lines.size(); ++j) {
            const std::string& nextLine = lines[j];
            viewDef += " " + boost::algorithm::trim_copy(nextLine);
            
            if (nextLine.find(";") != std::string::npos) {
               endLine = j;
               break;
            }
         }
         
         std::string parents = currentSchema.empty() ? currentDatabase : currentDatabase + "." + currentSchema;
         Symbol sym(viewName, "view", path, i+1, endLine+1, parents, "CREATE VIEW " + viewName);
         addSymbolNoLock(sym);
         continue;
      }
      
      // Check for CREATE TRIGGER
      std::smatch triggerMatch;
      if (std::regex_search(line, triggerMatch, createTriggerPattern)) {
         std::string triggerName = triggerMatch[1].str();
         
         // Find trigger end
         int endLine = i;
         for (size_t j = i + 1; j < lines.size(); ++j) {
            const std::string& nextLine = boost::algorithm::trim_copy(lines[j]);
            if (boost::algorithm::to_lower_copy(nextLine) == "end;" || 
                boost::algorithm::ends_with(boost::algorithm::to_lower_copy(nextLine), "end;")) {
               endLine = j;
               break;
            }
         }
         
         std::string parents = currentSchema.empty() ? currentDatabase : currentDatabase + "." + currentSchema;
         Symbol sym(triggerName, "trigger", path, i+1, endLine+1, parents, "CREATE TRIGGER " + triggerName);
         addSymbolNoLock(sym);
         continue;
      }
      
      // Check for CREATE FUNCTION
      std::smatch functionMatch;
      if (std::regex_search(line, functionMatch, createFunctionPattern)) {
         std::string functionName = functionMatch[1].str();
         
         // Find function end
         int endLine = i;
         std::string functionDef = trimmedLine;
         
         // Functions can have bodies with BEGIN/END or just be declarations
         bool hasBody = false;
         for (size_t j = i; j < std::min(i + 20, lines.size()); ++j) {
            const std::string& nextLine = boost::algorithm::trim_copy(lines[j]);
            std::string lowerNext = boost::algorithm::to_lower_copy(nextLine);
            
            if (lowerNext.find("begin") != std::string::npos) {
               hasBody = true;
            }
            
            if (hasBody && (lowerNext == "end;" || boost::algorithm::ends_with(lowerNext, "end;"))) {
               endLine = j;
               break;
            } else if (!hasBody && nextLine.find(";") != std::string::npos) {
               endLine = j;
               break;
            }
         }
         
         std::string parents = currentSchema.empty() ? currentDatabase : currentDatabase + "." + currentSchema;
         Symbol sym(functionName, "function", path, i+1, endLine+1, parents, "CREATE FUNCTION " + functionName);
         addSymbolNoLock(sym);
         continue;
      }
      
      // Check for CREATE PROCEDURE
      std::smatch procedureMatch;
      if (std::regex_search(line, procedureMatch, createProcedurePattern)) {
         std::string procedureName = procedureMatch[1].str();
         
         // Find procedure end (similar to functions)
         int endLine = i;
         bool hasBody = false;
         for (size_t j = i; j < std::min(i + 20, lines.size()); ++j) {
            const std::string& nextLine = boost::algorithm::trim_copy(lines[j]);
            std::string lowerNext = boost::algorithm::to_lower_copy(nextLine);
            
            if (lowerNext.find("begin") != std::string::npos) {
               hasBody = true;
            }
            
            if (hasBody && (lowerNext == "end;" || boost::algorithm::ends_with(lowerNext, "end;"))) {
               endLine = j;
               break;
            } else if (!hasBody && nextLine.find(";") != std::string::npos) {
               endLine = j;
               break;
            }
         }
         
         std::string parents = currentSchema.empty() ? currentDatabase : currentDatabase + "." + currentSchema;
         Symbol sym(procedureName, "procedure", path, i+1, endLine+1, parents, "CREATE PROCEDURE " + procedureName);
         addSymbolNoLock(sym);
         continue;
      }
      
      // Check for WITH clauses (CTEs)
      std::smatch withMatch;
      if (std::regex_search(line, withMatch, withPattern)) {
         std::string cteName = withMatch[1].str();
         Symbol sym(cteName, "cte", path, i+1, i+1, "", "WITH " + cteName + " AS");
         addSymbolNoLock(sym);
         continue;
      }
      
      // Check for ALTER TABLE
      std::smatch alterMatch;
      if (std::regex_search(line, alterMatch, alterTablePattern)) {
         std::string tableName = alterMatch[1].str();
         Symbol sym(tableName, "alter_table", path, i+1, i+1, "", "ALTER TABLE " + tableName);
         addSymbolNoLock(sym);
         continue;
      }
      
      // Check for DROP statements
      std::smatch dropMatch;
      if (std::regex_search(line, dropMatch, dropPattern)) {
         std::string objectType = boost::algorithm::to_lower_copy(dropMatch[1].str());
         std::string objectName = dropMatch[2].str();
         Symbol sym(objectName, "drop_" + objectType, path, i+1, i+1, "", "DROP " + boost::algorithm::to_upper_copy(objectType) + " " + objectName);
         addSymbolNoLock(sym);
         continue;
      }
   }
}

// Stan file indexing - using patterns from stan_highlight_rules.js
void SymbolIndex::indexStanFile(const FilePath& filePath) {
   std::string content;
   Error error = readStringFromFile(filePath, &content);
   if (error) {
      return;
   }
   
   std::string path = filePath.getAbsolutePath();
   indexStanFromString(content, path);
}

void SymbolIndex::indexStanFromString(const std::string& content, const std::string& path) {
   std::vector<std::string> lines;
   boost::split(lines, content, boost::is_any_of("\n"));
   
   // STAN ACEMODE PATTERNS - Exact from stan_highlight_rules.js
   // Stan keywords from acemode
   std::set<std::string> keywords = {
      "for", "while", "if", "else", "return", "break", "continue", "in", "print",
      "reject", "increment_log_prob", "integrate_ode", "integrate_ode_rk45",
      "integrate_ode_bdf"
   };
   
   // Stan data types from acemode
   std::set<std::string> dataTypes = {
      "int", "real", "vector", "row_vector", "matrix", "simplex", "ordered",
      "positive_ordered", "unit_vector", "cholesky_factor_cov", "cholesky_factor_corr",
      "cov_matrix", "corr_matrix"
   };
   
   // Stan distributions from acemode
   std::set<std::string> distributions = {
      "bernoulli", "binomial", "beta_binomial", "categorical", "multinomial",
      "normal", "student_t", "cauchy", "double_exponential", "logistic",
      "gamma", "inv_gamma", "exponential", "chi_square", "inv_chi_square",
      "scaled_inv_chi_square", "beta", "uniform", "lkj_corr", "lkj_corr_cholesky",
      "wishart", "inv_wishart", "pareto", "weibull"
   };
   
   // Stan functions from acemode
   std::set<std::string> functions = {
      "abs", "acos", "acosh", "asin", "asinh", "atan", "atan2", "atanh",
      "cbrt", "ceil", "cos", "cosh", "erf", "erfc", "exp", "exp2", "expm1",
      "fabs", "floor", "fma", "fmax", "fmin", "fmod", "hypot", "inv", "inv_sqrt",
      "ldexp", "lgamma", "log", "log10", "log1p", "log2", "logb", "pow", "round",
      "sin", "sinh", "sqrt", "square", "step", "tan", "tanh", "tgamma", "trunc"
   };
   
   // Stan block keywords
   std::set<std::string> blocks = {
      "functions", "data", "transformed data", "parameters", "transformed parameters",
      "model", "generated quantities"
   };
   
   // Stan block pattern
   std::regex blockPattern(R"(^\s*(functions|data|transformed\s+data|parameters|transformed\s+parameters|model|generated\s+quantities)\s*\{)");
   
   // Function definition pattern: returnType functionName(params)
   std::regex functionPattern(R"(^\s*([a-zA-Z_][a-zA-Z0-9_]*(?:\[\s*,?\s*\])?)\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*\()");
   
   // Variable declaration pattern
   std::regex varPattern(R"(^\s*([a-zA-Z_][a-zA-Z0-9_]*(?:\[\s*,?\s*\])?)\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*(?:\[.*\])?\s*;)");
   
   std::string currentBlock;
   
   for (size_t i = 0; i < lines.size(); ++i) {
      const std::string& line = lines[i];
      std::string trimmedLine = boost::algorithm::trim_copy(line);
      
      // Skip empty lines and comments (// or /* */)
      if (trimmedLine.empty() || 
          (trimmedLine.length() >= 2 && (trimmedLine.substr(0, 2) == "//" || trimmedLine.substr(0, 2) == "/*")))
         continue;
      
      // Check for block definitions
      std::smatch blockMatch;
      if (std::regex_search(line, blockMatch, blockPattern)) {
         std::string blockName = blockMatch[1].str();
         // Normalize multi-word block names
         boost::algorithm::replace_all(blockName, " ", "_");
         currentBlock = blockName;
         
         // Find block end by tracking braces
         int endLine = i;
         int braceCount = 1; // We already found the opening brace
         
         for (size_t j = i + 1; j < lines.size(); ++j) {
            const std::string& blockLine = lines[j];
            for (char c : blockLine) {
               if (c == '{') braceCount++;
               else if (c == '}') {
                  braceCount--;
                  if (braceCount == 0) {
                     endLine = j;
                     goto block_end_found;
                  }
               }
            }
         }
         block_end_found:
         
         Symbol sym(blockName, "block", path, i+1, endLine+1, "", blockName + " { ... }");
         addSymbolNoLock(sym);
         continue;
      }
      
      // Check for function definitions (only in functions block)
      if (currentBlock == "functions") {
         std::smatch functionMatch;
         if (std::regex_search(line, functionMatch, functionPattern)) {
            std::string returnType = functionMatch[1].str();
            std::string functionName = functionMatch[2].str();
            
            // Skip if it's a variable declaration (ends with semicolon)
            if (line.find(";") != std::string::npos)
               continue;
               
            // Skip if return type is a known data type for simple declarations
            if (dataTypes.count(returnType) > 0 && line.find("(") == std::string::npos)
               continue;
            
            // Extract complete function signature
            std::string signature = trimmedLine;
            int parenCount = 0;
            bool signatureComplete = false;
            
            // Count parentheses to find complete signature
            for (char c : line) {
               if (c == '(') parenCount++;
               else if (c == ')') {
                  parenCount--;
                  if (parenCount == 0) {
                     signatureComplete = true;
                     break;
                  }
               }
            }
            
            // If signature spans multiple lines
            if (!signatureComplete) {
               for (size_t j = i + 1; j < lines.size() && !signatureComplete; ++j) {
                  const std::string& nextLine = lines[j];
                  signature += " " + boost::algorithm::trim_copy(nextLine);
                  
                  for (char c : nextLine) {
                     if (c == '(') parenCount++;
                     else if (c == ')') {
                        parenCount--;
                        if (parenCount == 0) {
                           signatureComplete = true;
                           break;
                        }
                     }
                  }
               }
            }
            
            // Find function end by tracking braces
            int endLine = i;
            int braceCount = 0;
            bool foundOpenBrace = false;
            
            for (size_t j = i; j < lines.size(); ++j) {
               const std::string& funcLine = lines[j];
               for (char c : funcLine) {
                  if (c == '{') {
                     braceCount++;
                     foundOpenBrace = true;
                  } else if (c == '}') {
                     braceCount--;
                     if (foundOpenBrace && braceCount == 0) {
                        endLine = j;
                        goto function_end_found;
                     }
                  }
               }
            }
            function_end_found:
            
            signature = normalizeWhitespace(signature);
            Symbol sym(functionName, "function", path, i+1, endLine+1, currentBlock, signature);
            addSymbolNoLock(sym);
            continue;
         }
      }
      
      // Check for variable declarations in data/parameters blocks
      if (currentBlock == "data" || currentBlock == "parameters" || 
          currentBlock == "transformed_data" || currentBlock == "transformed_parameters") {
         std::smatch varMatch;
         if (std::regex_search(line, varMatch, varPattern)) {
            std::string varType = varMatch[1].str();
            std::string varName = varMatch[2].str();
            
            // Only index if it's a known data type
            if (dataTypes.count(varType) > 0) {
               Symbol sym(varName, "variable", path, i+1, i+1, currentBlock, trimmedLine);
               addSymbolNoLock(sym);
               continue;
            }
         }
      }
   }
}

// Shell script indexing - using patterns from sh_highlight_rules.js  
void SymbolIndex::indexShellScript(const FilePath& filePath) {
   std::string content;
   Error error = readStringFromFile(filePath, &content);
   if (error) {
      return;
   }
   
   std::string path = filePath.getAbsolutePath();
   indexShellFromString(content, path);
}

void SymbolIndex::indexShellFromString(const std::string& content, const std::string& path) {
   std::vector<std::string> lines;
   boost::split(lines, content, boost::is_any_of("\n"));
   
   // SHELL ACEMODE PATTERNS - Exact from sh_highlight_rules.js
   // Shell keywords from acemode
   std::set<std::string> keywords = {
      "if", "then", "else", "elif", "fi", "case", "esac", "for", "select", 
      "while", "until", "do", "done", "in", "function", "time", "coproc"
   };
   
   // Shell built-ins from acemode
   std::set<std::string> builtins = {
      "alias", "bg", "bind", "break", "builtin", "caller", "cd", "command",
      "compgen", "complete", "continue", "declare", "dirs", "disown", "echo",
      "enable", "eval", "exec", "exit", "export", "fc", "fg", "getopts",
      "hash", "help", "history", "jobs", "kill", "let", "local", "logout",
      "popd", "printf", "pushd", "pwd", "read", "readonly", "return", "set",
      "shift", "shopt", "source", "suspend", "test", "times", "trap", "type",
      "typeset", "ulimit", "umask", "unalias", "unset", "wait"
   };
   
   // Function definition patterns from acemode
   // Pattern 1: function name() { ... }
   std::regex functionPattern1(R"(^\s*function\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*\(\s*\)\s*\{?)");
   // Pattern 2: name() { ... }
   std::regex functionPattern2(R"(^\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\(\s*\)\s*\{?)");
   
   // Variable assignment pattern: VAR=value
   std::regex varAssignPattern(R"(^\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*=)");
   
   // Export pattern: export VAR=value
   std::regex exportPattern(R"(^\s*export\s+([a-zA-Z_][a-zA-Z0-9_]*))");
   
   // Command alias pattern: alias name='command'
   std::regex aliasPattern(R"(^\s*alias\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*=)");
   
   for (size_t i = 0; i < lines.size(); ++i) {
      const std::string& line = lines[i];
      std::string trimmedLine = boost::algorithm::trim_copy(line);
      
      // Skip empty lines and comments (starts with #)
      if (trimmedLine.empty() || (!trimmedLine.empty() && trimmedLine[0] == '#'))
         continue;
      
      // Check for function definitions - Pattern 1: function name() { ... }
      std::smatch funcMatch1;
      if (std::regex_search(line, funcMatch1, functionPattern1)) {
         std::string functionName = funcMatch1[1].str();
         
         // Find function end by tracking braces
         int endLine = i;
         int braceCount = 0;
         bool foundOpenBrace = false;
         
         // Check if opening brace is on the same line
         for (char c : line) {
            if (c == '{') {
               braceCount++;
               foundOpenBrace = true;
            }
         }
         
         // If no opening brace found, look for it in the next few lines
         if (!foundOpenBrace) {
            for (size_t j = i + 1; j < std::min(i + 3, lines.size()); ++j) {
               const std::string& nextLine = lines[j];
               if (boost::algorithm::trim_copy(nextLine) == "{") {
                  braceCount = 1;
                  foundOpenBrace = true;
                  break;
               }
            }
         }
         
         // Find closing brace
         if (foundOpenBrace) {
            for (size_t j = i + 1; j < lines.size(); ++j) {
               const std::string& funcLine = lines[j];
               for (char c : funcLine) {
                  if (c == '{') braceCount++;
                  else if (c == '}') {
                     braceCount--;
                     if (braceCount == 0) {
                        endLine = j;
                        goto function1_end_found;
                     }
                  }
               }
            }
            function1_end_found:;
         }
         
         std::string signature = "function " + functionName + "()";
         Symbol sym(functionName, "function", path, i+1, endLine+1, "", signature);
         addSymbolNoLock(sym);
         continue;
      }
      
      // Check for function definitions - Pattern 2: name() { ... }
      std::smatch funcMatch2;
      if (std::regex_search(line, funcMatch2, functionPattern2)) {
         std::string functionName = funcMatch2[1].str();
         
         // Skip if it's a keyword or builtin
         if (keywords.count(functionName) > 0 || builtins.count(functionName) > 0)
            continue;
         
         // Find function end by tracking braces  
         int endLine = i;
         int braceCount = 0;
         bool foundOpenBrace = false;
         
         // Check if opening brace is on the same line
         for (char c : line) {
            if (c == '{') {
               braceCount++;
               foundOpenBrace = true;
            }
         }
         
         // If no opening brace found, look for it in the next few lines
         if (!foundOpenBrace) {
            for (size_t j = i + 1; j < std::min(i + 3, lines.size()); ++j) {
               const std::string& nextLine = lines[j];
               if (boost::algorithm::trim_copy(nextLine) == "{") {
                  braceCount = 1;
                  foundOpenBrace = true;
                  break;
               }
            }
         }
         
         // Find closing brace
         if (foundOpenBrace) {
            for (size_t j = i + 1; j < lines.size(); ++j) {
               const std::string& funcLine = lines[j];
               for (char c : funcLine) {
                  if (c == '{') braceCount++;
                  else if (c == '}') {
                     braceCount--;
                     if (braceCount == 0) {
                        endLine = j;
                        goto function2_end_found;
                     }
                  }
               }
            }
            function2_end_found:;
         }
         
         std::string signature = functionName + "()";
         Symbol sym(functionName, "function", path, i+1, endLine+1, "", signature);
         addSymbolNoLock(sym);
         continue;
      }
      
      // Check for exported variables
      std::smatch exportMatch;
      if (std::regex_search(line, exportMatch, exportPattern)) {
         std::string varName = exportMatch[1].str();
         Symbol sym(varName, "exported_variable", path, i+1, i+1, "", "export " + varName);
         addSymbolNoLock(sym);
         continue;
      }
      
      
      // Check for command aliases
      std::smatch aliasMatch;
      if (std::regex_search(line, aliasMatch, aliasPattern)) {
         std::string aliasName = aliasMatch[1].str();
         Symbol sym(aliasName, "alias", path, i+1, i+1, "", trimmedLine);
         addSymbolNoLock(sym);
         continue;
      }
   }
}

// Rd (R documentation) file indexing
void SymbolIndex::indexRdFile(const FilePath& filePath) {
   std::string content;
   Error error = readStringFromFile(filePath, &content);
   if (error) {
      return;
   }
   
   std::string path = filePath.getAbsolutePath();
   indexRdFromString(content, path);
}

void SymbolIndex::indexRdFromString(const std::string& content, const std::string& path) {
   std::vector<std::string> lines;
   boost::split(lines, content, boost::is_any_of("\n"));
   
   // Regex for Rd name field
   std::regex nameRegex(R"(\\name\{([^}]+)\})");
   
   // Regex for Rd alias fields
   std::regex aliasRegex(R"(\\alias\{([^}]+)\})");
   
   // Regex for Rd title field
   std::regex titleRegex(R"(\\title\{([^}]+)\})");
   
   // Regex for Rd function usage (may include multiple lines)
   std::regex usageStartRegex(R"(\\usage\{)");
   std::regex usageEndRegex(R"(\})");
   
   // Keep track of our current documentation item
   std::string currentName = "";
   std::string currentTitle = "";
   std::string currentUsage = "";
   bool inUsageSection = false;
   int usageStartLine = 0;
   
   for (size_t i = 0; i < lines.size(); ++i) {
      const std::string& line = lines[i];
      
      // Check for name field
      std::smatch nameMatch;
      if (std::regex_search(line, nameMatch, nameRegex)) {
         // If we already have a current name, add it as a symbol before starting a new one
         if (!currentName.empty()) {
            Symbol sym(currentName, "function", path, usageStartLine+1, usageStartLine+1, "", currentUsage);
            addSymbolNoLock(sym);
         }
         
         // Start a new documentation item
         currentName = nameMatch[1];
         currentTitle = "";
         currentUsage = "";
         inUsageSection = false;
      }
      
      // Check for title field
      std::smatch titleMatch;
      if (std::regex_search(line, titleMatch, titleRegex)) {
         currentTitle = titleMatch[1];
      }
      
      // Check for alias field - each alias is a separate symbol
      std::smatch aliasMatch;
      std::string lineStr = line;
      std::string::const_iterator searchStart(lineStr.cbegin());
      
      while (std::regex_search(searchStart, lineStr.cend(), aliasMatch, aliasRegex)) {
         std::string alias = aliasMatch[1];
         
         // Add alias as a separate symbol
         Symbol sym(alias, "function", path, i+1, i+1, currentName);
         addSymbolNoLock(sym);
         
         // Move the search position forward
         searchStart = aliasMatch.suffix().first;
      }
      
      // Handle usage section which can span multiple lines
      if (std::regex_search(line, usageStartRegex)) {
         inUsageSection = true;
         usageStartLine = i;
         
         // Extract usage text from this line
         size_t usagePos = line.find("\\usage{");
         if (usagePos != std::string::npos) {
            currentUsage += line.substr(usagePos + 7); // Skip "\\usage{"
         }
         continue;
      }
      
      // If we're in the usage section, collect the content
      if (inUsageSection) {
         // Look for the end of the usage section
         if (std::regex_search(line, usageEndRegex)) {
            // Extract text up to the closing brace
            size_t bracePos = line.find("}");
            if (bracePos != std::string::npos) {
               currentUsage += line.substr(0, bracePos);
            }
            inUsageSection = false;
            
            // Clean up usage text
            currentUsage = normalizeWhitespace(currentUsage);
         } else {
            // Add the whole line to usage
            currentUsage += line;
         }
      }
   }
   
   // Add the last documentation item if there is one
   if (!currentName.empty()) {
      Symbol sym(currentName, "function", path, usageStartLine+1, usageStartLine+1, "", currentUsage);
      addSymbolNoLock(sym);
   }
}

// Index open documents from editor
void SymbolIndex::indexOpenDocuments() {
   // Get all open documents using the source database
   std::vector<boost::shared_ptr<source_database::SourceDocument>> docs;
   Error error = source_database::list(&docs);
   if (error) {
      return;
   }

   // Process each open document
   for (size_t i = 0; i < docs.size(); i++) {
      boost::shared_ptr<source_database::SourceDocument> pDoc = docs[i];
      
      // Skip documents without content
      if (pDoc->contents().empty()) {
         continue;
      }
      
      // Determine the file path to use
      std::string filePath;
      if (!pDoc->path().empty()) {
         // Document has a file path - use it and override any existing symbols for this file
         std::string originalPath = pDoc->path();
         FilePath resolvedPath = module_context::resolveAliasedPath(originalPath);
         filePath = resolvedPath.getAbsolutePath();
         
         // Remove existing symbols for this file since we're replacing with editor content
         removeSymbolsForFile(filePath);
      } else {
         // Unsaved document without a file path - use tempName if available
         std::string tempName = pDoc->getProperty("tempName");
         
         if (!tempName.empty()) {
            // Use directory-based uniqueness while keeping filename exactly as displayed
            // The uniqueness comes from the directory path, not the filename
            if (!pDoc->id().empty()) {
               filePath = "__UNSAVED_" + pDoc->id().substr(0, 4) + "__/" + tempName;
            } else {
               filePath = "__UNSAVED__/" + tempName;
            }
         } else {
            // Fallback to ID-based naming if no tempName
            if (!pDoc->id().empty()) {
               filePath = "__UNSAVED_" + pDoc->id().substr(0, 4) + "__/Untitled";
            } else {
               filePath = "__UNSAVED__/Untitled";
            }
         }
         
         // Remove existing symbols for this unsaved file since we're replacing with current editor content
         // This ensures deleted/moved symbols are properly cleaned up
         removeSymbolsForFile(filePath);
      }
      
      // Index the content based on document type for open documents
      indexContentByDocumentType(pDoc->contents(), filePath, pDoc->type());
      
      // Add the file itself as a symbol (similar to how disk files are indexed)
      // Count lines in the content
      int fileLines = std::count(pDoc->contents().begin(), pDoc->contents().end(), '\n') + 1;
      
      // Extract just the filename from the path for the symbol name
      std::string fileName;
      size_t lastSlash = filePath.find_last_of("/\\");
      if (lastSlash != std::string::npos) {
         fileName = filePath.substr(lastSlash + 1);
      } else {
         fileName = filePath;
      }
      
      // For unsaved files, use the path as both the file path and parent
      std::string parentContext = (pDoc->path().empty()) ? "" : filePath.substr(0, lastSlash != std::string::npos ? lastSlash : 0);
      
      Symbol fileSymbol(fileName, "file", filePath, 1, fileLines, parentContext);
      addSymbolNoLock(fileSymbol);
   }
}

// Index content by file type (string-based version)
void SymbolIndex::indexContentByFileType(const std::string& content, const std::string& filePath) {
   // Determine file extension from path
   std::string ext;
   size_t lastDot = filePath.find_last_of('.');
   if (lastDot != std::string::npos) {
      ext = filePath.substr(lastDot);
      boost::algorithm::to_lower(ext);
   }
   
   // Skip excluded filenames
   std::string filename;
   size_t lastSlash = filePath.find_last_of("/\\");
   if (lastSlash != std::string::npos) {
      filename = filePath.substr(lastSlash + 1);
   } else {
      filename = filePath;
   }
   
   if (isExcludedFilename(filename)) {
      return;
   }
   
   // Skip binary and non-indexable file types
   if (isBinaryFileType(ext) || !isIndexableFileType(ext)) {
      return;
   }
   
   // Index based on file type
   if (ext == ".r") {
      indexRFromString(content, filePath);
   } else if (ext == ".cpp" || ext == ".cc" || ext == ".c" || ext == ".h" || ext == ".hpp") {
      indexCppFromString(content, filePath);
   } else if (ext == ".py") {
      indexPythonFromString(content, filePath);
   } else if (ext == ".md" || ext == ".rmd" || ext == ".qmd") {
      indexMarkdownFromString(content, filePath);
   } else if (ext == ".sql") {
      indexSqlFromString(content, filePath);
   } else if (ext == ".stan") {
      indexStanFromString(content, filePath);
   } else if (ext == ".sh" || ext == ".bash") {
      indexShellFromString(content, filePath);
   } else if (ext == ".rd") {
      indexRdFromString(content, filePath);
   }
}

// Index content by document type (using RStudio document type instead of file extension)
void SymbolIndex::indexContentByDocumentType(const std::string& content, const std::string& filePath, const std::string& docType) {
   
   
   // Skip excluded filenames
   std::string filename;
   size_t lastSlash = filePath.find_last_of("/\\");
   if (lastSlash != std::string::npos) {
      filename = filePath.substr(lastSlash + 1);
   } else {
      filename = filePath;
   }
   
   if (isExcludedFilename(filename)) {
      return;
   }
   
   // Index based on document type using kSourceDocumentType constants
   if (docType == kSourceDocumentTypeRSource) {
      indexRFromString(content, filePath);
   } else if (docType == kSourceDocumentTypeCpp) {
      indexCppFromString(content, filePath);
   } else if (docType == kSourceDocumentTypePython) {
      indexPythonFromString(content, filePath);
   } else if (docType == kSourceDocumentTypeRMarkdown || docType == kSourceDocumentTypeQuartoMarkdown) {
      indexMarkdownFromString(content, filePath);
   } else if (docType == kSourceDocumentTypeSQL) {
      indexSqlFromString(content, filePath);
   } else if (docType == kSourceDocumentTypeShell) {
      indexShellFromString(content, filePath);
   } else {
      // For unrecognized document types, fall back to file extension-based detection
      // This handles cases like Stan files, Rd files, or other file types
      indexContentByFileType(content, filePath);
   }
}

// Conversion functions between C++ and R objects
SEXP symbolToRObject(const symbol_index::Symbol& symbol) {
   r::sexp::Protect protect;
   
   // Create a named list
   std::vector<std::string> names = {
      "name", "type", "file", "filename", "line_start", "line_end", "parents", "signature", "children"
   };
   SEXP resultSEXP = r::sexp::createList(names, &protect);
   
   // Set values using SET_VECTOR_ELT and r::sexp::create
   SET_VECTOR_ELT(resultSEXP, 0, r::sexp::create(symbol.name, &protect));
   SET_VECTOR_ELT(resultSEXP, 1, r::sexp::create(symbol.type, &protect));
   SET_VECTOR_ELT(resultSEXP, 2, r::sexp::create(symbol.filePath, &protect));
   SET_VECTOR_ELT(resultSEXP, 3, r::sexp::create(symbol.fileName, &protect));
   SET_VECTOR_ELT(resultSEXP, 4, r::sexp::create(symbol.lineStart, &protect));
   SET_VECTOR_ELT(resultSEXP, 5, r::sexp::create(symbol.lineEnd, &protect));
   SET_VECTOR_ELT(resultSEXP, 6, r::sexp::create(symbol.parents, &protect));
   SET_VECTOR_ELT(resultSEXP, 7, r::sexp::create(symbol.signature, &protect));
   
   // Convert children vector to an R character vector
   SEXP childrenSEXP = Rf_allocVector(STRSXP, symbol.children.size());
   protect.add(childrenSEXP);
   for (size_t i = 0; i < symbol.children.size(); ++i) {
      SET_STRING_ELT(childrenSEXP, i, Rf_mkChar(symbol.children[i].c_str()));
   }
   SET_VECTOR_ELT(resultSEXP, 8, childrenSEXP);
   
   return resultSEXP;
}

SEXP symbolVectorToRObject(const std::vector<symbol_index::Symbol>& symbols) {
   r::sexp::Protect protect;
   
   // Create a list with the symbols
   SEXP resultSEXP = Rf_allocVector(VECSXP, symbols.size());
   protect.add(resultSEXP);
   
   // Fill the list
   for (size_t i = 0; i < symbols.size(); ++i) {
      SET_VECTOR_ELT(resultSEXP, i, symbolToRObject(symbols[i]));
   }
   
   return resultSEXP;
}

// Index a specific file or directory (same logic as traverseDirectory but for a single target)
void SymbolIndex::indexSpecificTarget(const FilePath& target) {
   std::lock_guard<std::mutex> lock(mutex_);
   
   std::string targetPath = target.getAbsolutePath();
   
   // Get all open documents to check for matches
   std::vector<boost::shared_ptr<source_database::SourceDocument>> docs;
   Error error = source_database::list(&docs);
   if (error) {
      // If we can't get documents, fall back to disk-only logic
   } else {
      // Find matching document using the canonical pattern matching logic from SessionAi.cpp
      boost::shared_ptr<source_database::SourceDocument> matchingDoc;
      
      for (const auto& doc : docs) {
         bool matches = false;
         
         // First check if document has a saved path and it matches
         if (!doc->path().empty()) {
            FilePath docPath = module_context::resolveAliasedPath(doc->path());
            std::string normalizedDoc = docPath.getAbsolutePath();
            
            if (targetPath == normalizedDoc) {
               matches = true;
            }
         }
         // If no path match, check for tempName match (for unsaved documents)
         else {
            std::string tempName = doc->getProperty("tempName");
            if (!tempName.empty()) {
               // For tempName matching, use prefix patterns from symbol index:
               // 1. "__UNSAVED__/" + tempName
               // 2. "__UNSAVED_" + id + "__/" + tempName
               
               std::string unsavedPathPattern1 = "__UNSAVED__/" + tempName;
               std::string unsavedPathPattern2;
               if (!doc->id().empty()) {
                  unsavedPathPattern2 = "__UNSAVED_" + doc->id().substr(0, 4) + "__/" + tempName;
               }
               
               // Check various matching patterns (following SessionAi.cpp logic)
               if (targetPath == tempName ||                          // Direct tempName match
                   targetPath == unsavedPathPattern1 ||               // Symbol index pattern 1
                   (!unsavedPathPattern2.empty() && targetPath == unsavedPathPattern2)) // Symbol index pattern 2
               {
                  matches = true;
               }
            }
         }
         
         if (matches) {
            matchingDoc = doc;
            break;
         }
      }
      
      // If we found a matching open document, index its content instead of disk content
      if (matchingDoc && !matchingDoc->contents().empty()) {
         // Remove existing symbols for this file
         removeSymbolsForFile(targetPath);
         
         // Index the content using the existing helper
         indexContentByDocumentType(matchingDoc->contents(), targetPath, matchingDoc->type());
         
         // Add the file itself as a symbol
         int fileLines = std::count(matchingDoc->contents().begin(), matchingDoc->contents().end(), '\n') + 1;
         
         std::string fileName;
         size_t lastSlash = targetPath.find_last_of("/\\");
         if (lastSlash != std::string::npos) {
            fileName = targetPath.substr(lastSlash + 1);
         } else {
            fileName = targetPath;
         }
         
         std::string parentContext = (matchingDoc->path().empty()) ? "" : 
            targetPath.substr(0, lastSlash != std::string::npos ? lastSlash : 0);
         Symbol fileSymbol(fileName, "file", targetPath, 1, fileLines, parentContext);
         addSymbolNoLock(fileSymbol);
         
         return; // Successfully indexed from editor content
      }
   }
   
   // Handle disk files (original logic)
   if (!target.exists()) {
      return;
   }
   
   if (target.isDirectory()) {
      // Index the directory itself as a symbol (same as traverseDirectory line 971)
      std::string dirPath = target.getAbsolutePath();
      Symbol dirSymbol(target.getFilename(), "directory", dirPath, 0, 0);
      addSymbolNoLock(dirSymbol);
   } else {
      // Index the file (same logic as traverseDirectory lines 1003-1040)
      std::string fileName = target.getFilename();
      std::string filePath = target.getAbsolutePath();
      std::string extension = target.getExtensionLowerCase();
      
      // COMPLETELY exclude certain filenames from all indexing
      if (isExcludedFilename(fileName)) {
         return;
      }
      
      // Determine if this is a binary file
      bool isBinary = isBinaryFileType(extension);
      bool isImage = isImageFileType(extension);
      bool shouldIndex = isIndexableFileType(extension);
      
      if (isImage) {
         // For image files, use the "image" type and don't set line numbers
         Symbol fileSymbol(fileName, "image", filePath, 0, 0);
         addSymbolNoLock(fileSymbol);
      } else if (isBinary) {
         // For other binary files, use "binary" type and don't set line numbers
         Symbol fileSymbol(fileName, "binary", filePath, 0, 0);
         addSymbolNoLock(fileSymbol);
      } else {
         // For all text files, add as a symbol with proper line counts
         int fileLines = 0;
         
         std::string content;
         Error error = readStringFromFile(target, &content);
         if (!error) {
            // Count the number of lines in the file
            fileLines = std::count(content.begin(), content.end(), '\n') + 1;
         }
         
         Symbol fileSymbol(fileName, "file", filePath, 1, fileLines);
         addSymbolNoLock(fileSymbol);
         
         // Only index the content of recognized file types
         if (shouldIndex) {
            // Remove the lock temporarily for file content indexing to avoid deadlock
            mutex_.unlock();
            indexFileByTypeSafe(target);
            mutex_.lock();
         }
      }
   }
}

// Remove the entire symbol index for the current working directory
Error SymbolIndex::removeSymbolIndex() {
   std::lock_guard<std::mutex> lock(mutex_);
   
   std::string workingDir = currentWorkingDir_;
   if (workingDir.empty()) {
      workingDir = FilePath::safeCurrentPath(FilePath()).getAbsolutePath();
   }
   
   // Get the directory ID for the current working directory
   std::string dirId = getDirectoryId(workingDir);
   
   // Clear all in-memory data structures
   symbolMap_.clear();
   fileChecksums_.clear();
   directoryFiles_.clear();
   pendingFiles_.clear();
   traversalPath_.clear();
   currentWorkingDir_.clear();
   indexBuilt_ = false;
   
   // If we have a directory ID, remove all storage files
   if (!dirId.empty()) {
      FilePath baseDir(getIndexBaseDir());
      if (baseDir.exists()) {
         // Remove the directory storage folder
         FilePath dirStorageDir = baseDir.completeChildPath(dirId);
         if (dirStorageDir.exists()) {
            Error error = dirStorageDir.remove();
            if (error) {
               LOG_ERROR(error);
            }
         }
      }
      
      // Remove the directory mapping entry
      FilePath mappingFilePath(getDirMappingFile());
      if (mappingFilePath.exists()) {
         // Read the current mapping file
         std::vector<std::string> lines;
         std::ifstream mappingFile(mappingFilePath.getAbsolutePath().c_str());
         if (mappingFile.is_open()) {
            std::string line;
            while (std::getline(mappingFile, line)) {
               lines.push_back(line);
            }
            mappingFile.close();
            
            // Rewrite the mapping file without the entry for this directory
            std::ofstream outFile(mappingFilePath.getAbsolutePath().c_str());
            if (outFile.is_open()) {
               std::string normalizedWorkingDir = normalizeDirPath(workingDir);
               
               for (const std::string& fileLine : lines) {
                  size_t commaPos = fileLine.find(',');
                  if (commaPos != std::string::npos) {
                     std::string encodedPath = fileLine.substr(0, commaPos);
                     std::string decodedPath = http::util::urlDecode(encodedPath);
                     
                     // Skip the line for the current working directory
                     if (decodedPath != normalizedWorkingDir) {
                        outFile << fileLine << std::endl;
                     }
                  } else {
                     // Keep header line and any malformed lines
                     outFile << fileLine << std::endl;
                  }
               }
               outFile.close();
            }
         }
      }
   }
   
   return Success();
}

// Build symbol index framework quickly without actual indexing
Error SymbolIndex::buildIndexQuick(const FilePath& dir) {
   std::string workingDir;
   std::string dirId;
   bool indexExists = false;
   
   // Use a scoped lock to set up the framework
   {
      std::lock_guard<std::mutex> lock(mutex_);
      workingDir = dir.getAbsolutePath();
      currentWorkingDir_ = workingDir;
      
      // Clear traversal path since we're not doing incremental indexing
      traversalPath_.clear();
      
      // Check if an index already exists for this directory
      dirId = getDirectoryId(workingDir);
      indexExists = !dirId.empty();

      if (indexExists) {
         // Load the existing index
         Error error = loadIndexFromStorageNoLock(dirId);
         if (!error) {
            // Index loaded successfully
            indexBuilt_ = true;
            return Success();
         }
         // If loading failed, continue to create new framework
      }
      
      // If no existing index, create the framework
      if (dirId.empty()) {
         dirId = ensureStorageDir(dir);
         if (dirId.empty()) {
            return systemError(boost::system::errc::operation_not_permitted,
                             "Failed to create storage directory for symbol index",
                             ERROR_LOCATION);
         }
      }
      
      // Initialize empty data structures
      symbolMap_.clear();
      fileChecksums_.clear();
      directoryFiles_.clear();
      pendingFiles_.clear();
      
      // Mark as built even though we haven't indexed anything yet
      // This allows index_specific_symbol to add to the index
      indexBuilt_ = true;
   }
   
   return Success();
}

// R API functions
SEXP rs_buildSymbolIndex(SEXP dirPathSEXP) {
   
   std::string dirPath = r::sexp::asString(dirPathSEXP);
   FilePath dir(dirPath);
   
   if (!dir.exists()) {
      r::exec::error("Directory does not exist: " + dirPath);
      return R_NilValue;
   }
   
   Error error = symbol_index::SymbolIndex::getInstance().buildIndex(dir);
   if (error) {
      r::exec::error(error.getMessage());
      return R_NilValue;
   }
   
   r::sexp::Protect protect;
   return r::sexp::create(true, &protect);
}

SEXP rs_findSymbol(SEXP nameSEXP) {
   
   std::string name = r::sexp::asString(nameSEXP);
   
   if (!symbol_index::SymbolIndex::getInstance().isIndexBuilt()) {
      r::exec::error("Symbol index has not been built");
      return R_NilValue;
   }
   
   std::vector<symbol_index::Symbol> symbols = symbol_index::SymbolIndex::getInstance().findSymbol(name);
   return symbolVectorToRObject(symbols);
}

SEXP rs_getAllSymbols() {
   
   if (!symbol_index::SymbolIndex::getInstance().isIndexBuilt()) {
      r::exec::error("Symbol index has not been built");
      return R_NilValue;
   }
   
   std::vector<symbol_index::Symbol> symbols = symbol_index::SymbolIndex::getInstance().getAllSymbols();
   return symbolVectorToRObject(symbols);
}

SEXP rs_hasPendingFiles() {
   r::sexp::Protect protect;
   return r::sexp::create(symbol_index::SymbolIndex::getInstance().hasPendingFiles(), &protect);
}

SEXP rs_getPendingFileCount() {
   r::sexp::Protect protect;
   return r::sexp::create((int)symbol_index::SymbolIndex::getInstance().getPendingFileCount(), &protect);
}

SEXP rs_indexSpecificSymbol(SEXP pathSEXP) {
   
   std::string path = r::sexp::asString(pathSEXP);
   FilePath target(path);
   
   // Allow indexing for unsaved files (which have __UNSAVED_ prefix) even if they don't exist on disk
   // The indexSpecificTarget method has logic to handle these by looking at open documents
   bool isUnsavedFile = path.find("__UNSAVED") != std::string::npos;
   
   if (!target.exists() && !isUnsavedFile) {
      r::exec::error("Path does not exist: " + path);
      return R_NilValue;
   }
   
   // Index the specific file or directory (same logic as traverseDirectory)
   symbol_index::SymbolIndex::getInstance().indexSpecificTarget(target);
   
   r::sexp::Protect protect;
   return r::sexp::create(true, &protect);
}

SEXP rs_removeSymbolIndex() {
   
   Error error = symbol_index::SymbolIndex::getInstance().removeSymbolIndex();
   if (error) {
      r::exec::error(error.getMessage());
      return R_NilValue;
   }
   
   r::sexp::Protect protect;
   return r::sexp::create(true, &protect);
}

SEXP rs_buildSymbolIndexQuick(SEXP dirPathSEXP) {
   
   std::string dirPath = r::sexp::asString(dirPathSEXP);
   FilePath dir(dirPath);
   
   if (!dir.exists()) {
      r::exec::error("Directory does not exist: " + dirPath);
      return R_NilValue;
   }
   
   Error error = symbol_index::SymbolIndex::getInstance().buildIndexQuick(dir);
   if (error) {
      r::exec::error(error.getMessage());
      return R_NilValue;
   }
   
   r::sexp::Protect protect;
   return r::sexp::create(true, &protect);
}

// R functions registrations
Error initSymbolIndex()
{
   
   // Register R API functions using the RS_REGISTER_CALL_METHOD macro
   RS_REGISTER_CALL_METHOD(rs_buildSymbolIndex, 1);
   RS_REGISTER_CALL_METHOD(rs_findSymbol, 1);
   RS_REGISTER_CALL_METHOD(rs_getAllSymbols, 0);
   RS_REGISTER_CALL_METHOD(rs_hasPendingFiles, 0);
   RS_REGISTER_CALL_METHOD(rs_getPendingFileCount, 0);
   RS_REGISTER_CALL_METHOD(rs_indexSpecificSymbol, 1);
   RS_REGISTER_CALL_METHOD(rs_removeSymbolIndex, 0);
   RS_REGISTER_CALL_METHOD(rs_buildSymbolIndexQuick, 1);
   
   return Success();
}

// Module initialization
Error initialize()
{
   // Create base directory structure if it doesn't exist
   FilePath baseDir(getIndexBaseDir());
   if (!baseDir.exists())
   {
      Error error = baseDir.ensureDirectory();
      if (error) {
         LOG_ERROR(error);
      }
   }
   
   // Create the directory mapping file if it doesn't exist
   FilePath mappingFilePath(getDirMappingFile());
   
   // First ensure the parent directory exists
   FilePath parentDir = mappingFilePath.getParent();
   if (!parentDir.exists()) {
      Error error = parentDir.ensureDirectory();
      if (error) {
         LOG_ERROR(error);
      }
   }
   
   // If the mapping file doesn't exist, create it with a header
   if (!mappingFilePath.exists()) {
      std::ofstream mappingFile(mappingFilePath.getAbsolutePath().c_str());
      if (mappingFile.is_open()) {
         // Write header line
         mappingFile << "directory_path,directory_id" << std::endl;
         mappingFile.close();
      }
   }
   
   Error error = initSymbolIndex();
   if (error)
      return error;
   
   // Register RPC methods
   using boost::bind;
   using namespace module_context;
   
   // Helper function to convert C++ Symbol objects to JSON objects
   auto symbolToJson = [](const symbol_index::Symbol& symbol) -> json::Object {
      json::Object symbolJson;
      symbolJson["name"] = symbol.name;
      symbolJson["type"] = symbol.type;
      symbolJson["file"] = symbol.filePath;
      symbolJson["filename"] = symbol.fileName;
      symbolJson["line_start"] = symbol.lineStart;
      symbolJson["line_end"] = symbol.lineEnd;
      symbolJson["parents"] = symbol.parents;
      symbolJson["signature"] = symbol.signature;
      return symbolJson;
   };
   
   // Helper function to convert Symbol vectors to JSON arrays
   auto symbolsToJsonArray = [&symbolToJson](const std::vector<symbol_index::Symbol>& symbols) -> json::Array {
      json::Array resultsJson;
      for (const symbol_index::Symbol& symbol : symbols) {
         resultsJson.push_back(symbolToJson(symbol));
      }
      return resultsJson;
   };
   
   // Build index RPC
   ExecBlock initBlock;
   initBlock.addFunctions()
      (bind(registerRpcMethod, "build_symbol_index", boost::function<Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
         [](const json::JsonRpcRequest& request, json::JsonRpcResponse* pResponse) -> Error {
            std::string dirPath;
            Error error = json::readParams(request.params, &dirPath);
            if (error)
               return error;
            
            FilePath dir(dirPath);
            if (!dir.exists())
               return rstudio::core::systemError(boost::system::errc::no_such_file_or_directory,
                                      "Directory does not exist: " + dirPath,
                                      ERROR_LOCATION);
            
            error = symbol_index::SymbolIndex::getInstance().buildIndex(dir);
            if (error)
               return error;
            
            pResponse->setResult(true);
            return Success();
         })))
      (bind(registerRpcMethod, "find_symbol", boost::function<Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
         [symbolsToJsonArray](const json::JsonRpcRequest& request, json::JsonRpcResponse* pResponse) -> Error {
            std::string name;
            Error error = json::readParams(request.params, &name);
            if (error)
               return error;
            
            if (!symbol_index::SymbolIndex::getInstance().isIndexBuilt())
               return rstudio::core::systemError(boost::system::errc::operation_not_permitted,
                                  "Symbol index has not been built",
                                  ERROR_LOCATION);
            
            std::vector<symbol_index::Symbol> symbols = symbol_index::SymbolIndex::getInstance().findSymbol(name);
            pResponse->setResult(symbolsToJsonArray(symbols));
            return Success();
         })))
      (bind(registerRpcMethod, "get_all_symbols", boost::function<Error(const json::JsonRpcRequest&, json::JsonRpcResponse*)>(
         [symbolsToJsonArray](const json::JsonRpcRequest& request, json::JsonRpcResponse* pResponse) -> Error {
            if (!symbol_index::SymbolIndex::getInstance().isIndexBuilt())
               return rstudio::core::systemError(boost::system::errc::operation_not_permitted,
                                  "Symbol index has not been built",
                                  ERROR_LOCATION);
            
            std::vector<symbol_index::Symbol> symbols = symbol_index::SymbolIndex::getInstance().getAllSymbols();
            pResponse->setResult(symbolsToJsonArray(symbols));
            return Success();
         })));

   ExecBlock sourceBlock;
   sourceBlock.addFunctions()
      (bind(sourceModuleRFile, "SessionSymbolIndex.R"));
   
   error = sourceBlock.execute();
   if (error)
      return error;
   
   // Subscribe to source document events for real-time symbol index updates
   source_database::events().onDocUpdated.connect(onSourceDocUpdated);
   source_database::events().onDocRemoved.connect(onSourceDocRemoved);
   source_database::events().onRemoveAll.connect(onAllSourceDocsRemoved);
   
   return initBlock.execute();
}

// New method to ensure storage directory exists
std::string SymbolIndex::ensureStorageDir(const FilePath& workingDir) 
{
   
   // Ensure base directory exists
   FilePath baseDir(getIndexBaseDir());
   if (!baseDir.exists())
   {
      Error error = baseDir.ensureDirectory();
      if (error)
      {
         LOG_ERROR(error);
         return "";
      }
   }
   
   // Get or create directory ID for this working directory
   std::string dirId = getDirectoryId(workingDir.getAbsolutePath());
   if (dirId.empty())
   {
      // Generate a new ID and add to mapping
      dirId = generateUniqueId();
      
      // Ensure the directory mapping file exists
      FilePath mappingFilePath(getDirMappingFile());
      if (!mappingFilePath.getParent().exists())
      {
         Error error = mappingFilePath.getParent().ensureDirectory();
         if (error)
         {
            LOG_ERROR(error);
            return "";
         }
      }
      
      // Append new mapping to CSV
      std::ofstream mappingFile;
      bool fileExists = mappingFilePath.exists();
      mappingFile.open(mappingFilePath.getAbsolutePath().c_str(), std::ios::app);
      
      if (!fileExists)
      {
         // Write header if file is new
         mappingFile << "directory_path,directory_id" << std::endl;
      }
      
      if (mappingFile.is_open())
      {
         std::string encodedPath = http::util::urlEncode(normalizeDirPath(workingDir.getAbsolutePath()));
         mappingFile << encodedPath << "," << dirId << std::endl;
         mappingFile.close();
      }
      else
      {
         LOG_ERROR(systemError(boost::system::errc::io_error, 
                   "Failed to open directory mapping file", ERROR_LOCATION));
         return "";
      }
   }
   
   // Ensure directory for this ID exists
   FilePath dirPath = baseDir.completeChildPath(dirId);
   if (!dirPath.exists())
   {
      Error error = dirPath.ensureDirectory();
      if (error)
      {
         LOG_ERROR(error);
         return "";
      }
   }
   
   return dirId;
}

// New method to get directory ID from mapping file
std::string SymbolIndex::getDirectoryId(const std::string& dirPath)
{
   FilePath mappingFilePath(getDirMappingFile());
   
   if (!mappingFilePath.exists()) {
      // Check if parent directory exists
      FilePath parentDir = mappingFilePath.getParent();
      
      // Try to create the mapping file on-the-fly if parent directory exists
      if (parentDir.exists()) {
         std::ofstream mappingFile(mappingFilePath.getAbsolutePath().c_str());
         if (mappingFile.is_open()) {
            mappingFile << "directory_path,directory_id" << std::endl;
            mappingFile.close();
         }
      }
      
      return "";
   }
      
   std::ifstream mappingFile(mappingFilePath.getAbsolutePath().c_str());
   if (!mappingFile.is_open()) {
      return "";
   }
   
   std::string line;
   std::string normalizedDirPath = normalizeDirPath(dirPath);
   
   // Skip header
   std::getline(mappingFile, line);
   
   while (std::getline(mappingFile, line))
   {
      size_t commaPos = line.find(',');
      if (commaPos != std::string::npos)
      {
         std::string encodedPath = line.substr(0, commaPos);
         std::string decodedPath = http::util::urlDecode(encodedPath);
         std::string dirId = line.substr(commaPos + 1);
         
         if (decodedPath == normalizedDirPath)
         {
            return dirId;
         }
      }
   }
   
   return "";
}

// New method to save index to storage
Error SymbolIndex::saveIndexToStorage(const std::string& dirId)
{
   
   FilePath baseDir(getIndexBaseDir());
   FilePath storageDir = baseDir.completeChildPath(dirId);
   
   // Ensure storage directory exists
   if (!storageDir.exists())
   {
      Error error = storageDir.ensureDirectory();
      if (error)
         return error;
   }
   
   // Save the symbol map to a file
   FilePath symbolFile = storageDir.completeChildPath("symbol_index.json");
   
   // Convert symbol map to JSON format
   json::Object indexObj;
   json::Array symbolsArray;
   
   for (const auto& pair : symbolMap_)
   {
      for (const Symbol& symbol : pair.second)
      {
         json::Object symbolObj;
         symbolObj["name"] = symbol.name;
         symbolObj["type"] = symbol.type;
         symbolObj["file"] = symbol.filePath;
         symbolObj["filename"] = symbol.fileName;
         symbolObj["line_start"] = symbol.lineStart;
         symbolObj["line_end"] = symbol.lineEnd;
         symbolObj["parents"] = symbol.parents;
         symbolObj["signature"] = symbol.signature;
         
         // Save children array
         json::Array childrenArray;
         for (const std::string& child : symbol.children) {
            childrenArray.push_back(child);
         }
         symbolObj["children"] = childrenArray;
         
         symbolsArray.push_back(symbolObj);
      }
   }
   
   indexObj["symbols"] = symbolsArray;
   indexObj["working_directory"] = currentWorkingDir_;
   
   // Save the traversal path
   json::Array pathArray;
   for (size_t pos : traversalPath_) {
      pathArray.push_back(static_cast<int>(pos));
   }
   indexObj["traversal_path"] = pathArray;
   
   std::ostringstream jsonStream;
   indexObj.writeFormatted(jsonStream);
   
   return writeStringToFile(symbolFile, jsonStream.str());
}

// New method to load index from storage
Error SymbolIndex::loadIndexFromStorage(const std::string& dirId)
{
   
   // Acquire lock and delegate to the no-lock version
   std::lock_guard<std::mutex> lock(mutex_);
   return loadIndexFromStorageNoLock(dirId);
}

// Helper to read and parse JSON from a file
Error readAndParseJson(const FilePath& filePath, json::Value* pJsonValue) {
   if (!filePath.exists()) {
      return systemError(boost::system::errc::no_such_file_or_directory,
                       "File not found: " + filePath.getAbsolutePath(), ERROR_LOCATION);
   }
   
   std::string content;
   Error error = readStringFromFile(filePath, &content);
   if (error) {
      return error;
   }
   
   if (content.empty()) {
      return systemError(boost::system::errc::invalid_argument,
                       "Empty JSON file: " + filePath.getAbsolutePath(), ERROR_LOCATION);
   }
   
   // Missing parse step! Need to actually parse the JSON content
   error = pJsonValue->parse(content);
   if (error) {
      return error;
   }
      
   return Success();
}

// Private method to load index without acquiring the mutex (for internal use)
Error SymbolIndex::loadIndexFromStorageNoLock(const std::string& dirId)
{   
   // Clear existing data first
   symbolMap_.clear();
   traversalPath_.clear(); // Reset traversal path
   
   // Construct the path to the index file
   FilePath symbolFile = FilePath(getIndexFilePath(dirId, "symbol_index.json"));
   
   // Read and parse JSON
   json::Value jsonValue;
   Error error = readAndParseJson(symbolFile, &jsonValue);
   if (error)
      return error;
   
   json::Object indexObj = jsonValue.getObject();
   json::Array symbolsArray = indexObj["symbols"].getArray();
   
   // Load working directory
   currentWorkingDir_ = indexObj["working_directory"].getString();
   
   // Load traversal path if available
   if (indexObj.find("traversal_path") != indexObj.end() && indexObj["traversal_path"].isArray()) {
      json::Array pathArray = indexObj["traversal_path"].getArray();
      for (const json::Value& posValue : pathArray) {
         if (posValue.isInt()) {
            traversalPath_.push_back(static_cast<size_t>(posValue.getInt()));
         }
      }
   } else if (indexObj.find("traversal_position") != indexObj.end()) {
      // For backward compatibility with older storage format
      size_t position = static_cast<size_t>(indexObj["traversal_position"].getInt());
      if (position > 0) {
         traversalPath_.push_back(position);
      }
   }
   
   // Load symbols
   for (const json::Value& symbolValue : symbolsArray) {
      json::Object symbolObj = symbolValue.getObject();
      
      Symbol symbol;
      symbol.name = symbolObj["name"].getString();
      symbol.type = symbolObj["type"].getString();
      symbol.filePath = symbolObj["file"].getString();
      symbol.fileName = symbolObj["filename"].getString();
      symbol.lineStart = symbolObj["line_start"].getInt();
      symbol.lineEnd = symbolObj["line_end"].getInt();
      symbol.parents = symbolObj["parents"].getString();
      symbol.signature = symbolObj["signature"].getString();
      
      // Load children if available
      if (symbolObj.find("children") != symbolObj.end() && symbolObj["children"].isArray()) {
         json::Array childrenArray = symbolObj["children"].getArray();
         for (const json::Value& childValue : childrenArray) {
            if (childValue.isString()) {
               symbol.children.push_back(childValue.getString());
            }
         }
      }
      
      // Format function signatures for consistency
      if (symbol.type == "function" && !symbol.signature.empty()) {
         // Check if the signature is a function signature
         if (symbol.signature.find("function(") == 0) {
            // Apply parameter formatting to ensure consistency
            symbol.signature = formatFunctionParameters(normalizeWhitespace(symbol.signature));
         }
      }
      
      addSymbolNoLock(symbol);
   }
   
   // Load the checksums and directory structure
   error = loadChecksumsFromStorage(dirId);
   if (error) {
      LOG_ERROR(error);
   }
   
   error = loadDirStructureFromStorage(dirId);
   if (error) {
      LOG_ERROR(error);
   }
   
   indexBuilt_ = true;
   return Success();
}

// Add a new helper function to check if an index exists
bool SymbolIndex::indexExistsForDirectory(const FilePath& dir)
{   
   std::string dirId = getDirectoryId(dir.getAbsolutePath());
   if (dirId.empty())
      return false;
      
   FilePath baseDir(getIndexBaseDir());
   FilePath storageDir = baseDir.completeChildPath(dirId);
   FilePath symbolFile = storageDir.completeChildPath("symbol_index.json");
   
   return symbolFile.exists();
}

void SymbolIndex::removeSymbolsForFile(const std::string& filePath) {   
   for (auto& pair : symbolMap_) {
      std::vector<Symbol>& symbols = pair.second;
      symbols.erase(std::remove_if(symbols.begin(), symbols.end(),
                                  [&filePath](const Symbol& symbol) {
                                      return symbol.filePath == filePath;
                                  }),
                   symbols.end());
   }
   
   // Remove empty entries
   for (auto it = symbolMap_.begin(); it != symbolMap_.end();) {
      if (it->second.empty()) {
         it = symbolMap_.erase(it);
      } else {
         ++it;
      }
   }
}

std::vector<std::string> SymbolIndex::getCurrentDirectoryStructure(const FilePath& dir) {   
   std::vector<std::string> fileList;
   
   // Keep track of files found to respect MAX_FILES_PER_BATCH limit
   size_t filesFound = 0;
   
   std::function<void(const FilePath&)> traverseDirForList = [&](const FilePath& path) {
      // Stop traversing if we've hit our limit
      if (filesFound >= MAX_FILES_PER_BATCH) {
         return;
      }
      
      std::vector<FilePath> children;
      Error error = path.getChildren(children);
      if (error) {
         return;
      }
      
      for (const FilePath& child : children) {
         // Stop if we've hit our limit
         if (filesFound >= MAX_FILES_PER_BATCH) {
            return;
         }
         
         if (child.isDirectory()) {
            // Skip hidden directories and those that might contain too many files
            std::string dirName = child.getFilename();
            if (dirName[0] == '.' || dirName == "node_modules" || dirName == ".git") {
               continue;
            }
            
            traverseDirForList(child);
         } else {
            fileList.push_back(child.getAbsolutePath());
            filesFound++;
            
            // Check limit
            if (filesFound >= MAX_FILES_PER_BATCH) {
               return;
            }
         }
      }
   };
   
   traverseDirForList(dir);
   return fileList;
}

void SymbolIndex::calculateFileChecksums(const FilePath& dir) {   
   fileChecksums_.clear();
   
   for (const std::string& path : directoryFiles_) {
      FilePath filePath(path);
      
      // Skip directories and non-existent files
      if (!filePath.exists() || filePath.isDirectory()) {
         continue;
      }
      
      std::string checksum = generateFileChecksum(filePath);
      std::string modTime = getFileModifiedTime(filePath);
      
      fileChecksums_[path] = FileChecksum(path, checksum, modTime);
      
   }
}

void SymbolIndex::updateFileChecksums() {   
   for (auto& pair : fileChecksums_) {
      std::string path = pair.first;
      FilePath filePath(path);
      
      if (filePath.exists()) {
         std::string newChecksum = generateFileChecksum(filePath);
         std::string newModTime = getFileModifiedTime(filePath);
         
         pair.second.checksum = newChecksum;
         pair.second.lastModified = newModTime;
      }
   }
}

Error SymbolIndex::saveChecksumsToStorage(const std::string& dirId) {   
   FilePath checksumFile(getChecksumFile(dirId));
   
   json::Object checksumObj;
   json::Array checksumArray;
   
   for (const auto& pair : fileChecksums_) {
      json::Object fileObj;
      fileObj["path"] = pair.second.path;
      fileObj["checksum"] = pair.second.checksum;
      fileObj["last_modified"] = pair.second.lastModified;
      
      checksumArray.push_back(fileObj);
   }
   
   checksumObj["file_checksums"] = checksumArray;
   
   std::ostringstream jsonStream;
   checksumObj.writeFormatted(jsonStream);
   
   Error error = writeStringToFile(checksumFile, jsonStream.str());
   if (error) {
   } else {
   }
   
   return error;
}

Error SymbolIndex::loadChecksumsFromStorage(const std::string& dirId) {   
   FilePath checksumFile(getChecksumFile(dirId));
   
   // Clear current checksums
   fileChecksums_.clear();
   
   // Read and parse JSON
   json::Value jsonValue;
   Error error = readAndParseJson(checksumFile, &jsonValue);
   if (error) {
      return error;
   }
   
   json::Object checksumObj = jsonValue.getObject();
   json::Array checksumArray = checksumObj["file_checksums"].getArray();
   
   // Load checksums
   for (const json::Value& checksumValue : checksumArray) {
      json::Object fileObj = checksumValue.getObject();
      
      std::string path = fileObj["path"].getString();
      std::string checksum = fileObj["checksum"].getString();
      std::string lastModified = fileObj["last_modified"].getString();
      
      fileChecksums_[path] = FileChecksum(path, checksum, lastModified);
   }
   
   return Success();
}

Error SymbolIndex::saveDirStructureToStorage(const std::string& dirId) {   
   // Validate dirId
   if (dirId.empty()) {
      return systemError(boost::system::errc::invalid_argument,
                       "Empty directory ID", ERROR_LOCATION);
   }
   
   FilePath structureFile(getDirStructureFile(dirId));
   
   // Ensure the parent directory exists
   FilePath parentDir = structureFile.getParent();
   if (!parentDir.exists()) {
      Error error = parentDir.ensureDirectory();
      if (error) {
         return error;
      }
   }
   
   // Validate directory files
   if (directoryFiles_.empty()) {
   }
   
   json::Object structureObj;
   json::Array fileArray;
   
   for (const std::string& filePath : directoryFiles_) {
      fileArray.push_back(filePath);
   }
   
   structureObj["files"] = fileArray;
   
   std::ostringstream jsonStream;
   structureObj.writeFormatted(jsonStream);
   
   std::string jsonContent = jsonStream.str();
      
   Error error = writeStringToFile(structureFile, jsonContent);
   if (error) {
   } else {
   }
   
   return error;
}

Error SymbolIndex::loadDirStructureFromStorage(const std::string& dirId) {   
   // Validate dirId
   if (dirId.empty()) {
      return systemError(boost::system::errc::invalid_argument,
                       "Empty directory ID", ERROR_LOCATION);
   }
   
   FilePath structureFile(getDirStructureFile(dirId));
   
   // Check if file exists
   if (!structureFile.exists()) {
      // Create an empty structure file
      directoryFiles_.clear();
      Error saveError = saveDirStructureToStorage(dirId);
      if (saveError) {
         return saveError;
      }
      
      return Success();
   }
   
   // Clear current directory structure
   directoryFiles_.clear();
   
   // Read file content for debugging
   std::string content;
   Error error = readStringFromFile(structureFile, &content);
   if (error) {
      return error;
   }
   
   // Read and parse JSON
   json::Value jsonValue;
   error = readAndParseJson(structureFile, &jsonValue);
   if (error) {
      return error;
   }
   
   // Validate JSON structure before accessing it
   if (!jsonValue.isObject()) {
      return systemError(boost::system::errc::invalid_argument,
                        "Invalid JSON format - expected an object", ERROR_LOCATION);
   }
   
   // Try to read the structure object
   try {
      json::Object structureObj = jsonValue.getObject();
      
      // Verify that the 'files' key exists and is an array
      if (!structureObj.hasMember("files") || !structureObj["files"].isArray()) {
         return systemError(boost::system::errc::invalid_argument,
                           "Invalid structure format - missing or invalid 'files' array", ERROR_LOCATION);
      }
      
      json::Array fileArray = structureObj["files"].getArray();
      
      // Load file paths
      for (const json::Value& pathValue : fileArray) {
         // Verify each entry is a string
         if (!pathValue.isString()) {
            continue;
         }
         
         std::string filePath = pathValue.getString();
         directoryFiles_.push_back(filePath);
      }
   }
   catch (const std::exception& e) {
      return systemError(boost::system::errc::invalid_argument,
                       "Exception parsing directory structure: " + std::string(e.what()), ERROR_LOCATION);
   }
   
   return Success();
}

bool SymbolIndex::hasDirectoryChanged(const FilePath& dir) {   
   std::string dirId = getDirectoryId(dir.getAbsolutePath());
   if (dirId.empty()) {
      return true; // No previous index, so consider it changed
   }
   
   // Load previous structure and checksums
   Error error = loadDirStructureFromStorage(dirId);
   if (error) {
      return true;
   }
      
   error = loadChecksumsFromStorage(dirId);
   if (error) {
      return true;
   }
   
   // Get current directory structure
   std::vector<std::string> currentFiles = getCurrentDirectoryStructure(dir);
   
   // Check if file list has changed (different file count)
   if (currentFiles.size() != directoryFiles_.size()) {
      return true;
   }
   
   // Create sorted copies for comparison
   std::vector<std::string> sortedCurrentFiles = currentFiles;
   std::vector<std::string> sortedPreviousFiles = directoryFiles_;
   
   std::sort(sortedCurrentFiles.begin(), sortedCurrentFiles.end());
   std::sort(sortedPreviousFiles.begin(), sortedPreviousFiles.end());
   
   // Check if the sorted file lists are different
   if (sortedCurrentFiles != sortedPreviousFiles) {
      return true;
   }
   
   
   // Check if any file has changed (different checksum or modification time)
   for (const std::string& filePath : currentFiles) {
      FilePath currentFile(filePath);
      std::string currentChecksum = generateFileChecksum(currentFile);
      
      auto it = fileChecksums_.find(filePath);
      if (it == fileChecksums_.end()) {
         return true;
      } 
      
      std::string savedChecksum = it->second.checksum;
      if (savedChecksum != currentChecksum) {
         return true;
      }
   }
   
   return false; // No changes detected
}

Error SymbolIndex::updateIndexIncrementally(const FilePath& dir) {   
   // Load existing index, structure, and checksums
   std::string dirId = getDirectoryId(dir.getAbsolutePath());
   if (dirId.empty())
      return systemError(boost::system::errc::invalid_argument,
                        "No previous index found", ERROR_LOCATION);
   
   // Initialize the start time for timeout tracking
   indexingStartTime_ = std::chrono::steady_clock::now();
   
   // Use a scoped lock for data access
   {
      std::lock_guard<std::mutex> lock(mutex_);
      Error error = loadAllFromStorage(dirId);
      if (error)
         return error;
   }
   
   // Get current directory structure
   std::vector<std::string> currentFiles = getCurrentDirectoryStructure(dir);
   
   // Find removed files and remove their symbols
   std::vector<std::string> removedFiles;
   
   {
      std::lock_guard<std::mutex> lock(mutex_);
      for (const std::string& oldFile : directoryFiles_) {
         auto it = std::find(currentFiles.begin(), currentFiles.end(), oldFile);
         if (it == currentFiles.end()) {
            removedFiles.push_back(oldFile);
         }
      }
      
      for (const std::string& removedFile : removedFiles) {
         removeSymbolsForFile(removedFile);
         fileChecksums_.erase(removedFile);
      }
   }
   
   // Find modified files
   std::vector<std::string> modifiedFiles;
   {
      std::lock_guard<std::mutex> lock(mutex_);
      for (const std::string& currentFile : currentFiles) {
         FilePath filePath(currentFile);
         std::string newChecksum = generateFileChecksum(filePath);
         
         auto it = fileChecksums_.find(currentFile);
         if (it == fileChecksums_.end() || it->second.checksum != newChecksum) {
            modifiedFiles.push_back(currentFile);
         }
      }
   }
   
   // Find new files
   std::vector<std::string> newFiles;
   {
      std::lock_guard<std::mutex> lock(mutex_);
      for (const std::string& currentFile : currentFiles) {
         auto it = std::find(directoryFiles_.begin(), directoryFiles_.end(), currentFile);
         if (it == directoryFiles_.end()) {
            newFiles.push_back(currentFile);
         }
      }
   }
   
   // Limit the number of files we process
   size_t filesProcessed = 0;
   size_t modifiedProcessed = 0;
   size_t newFilesProcessed = 0;
   
   // Process modified files (up to our limit)
   for (const std::string& modifiedFile : modifiedFiles) {
      // Check if we've reached our file limit or timeout
      if (filesProcessed >= MAX_FILES_PER_BATCH || hasTimedOut()) {
         // Add remaining modified files to pending
         for (size_t i = modifiedProcessed; i < modifiedFiles.size(); i++) {
            if (std::find(pendingFiles_.begin(), pendingFiles_.end(), modifiedFiles[i]) == pendingFiles_.end()) {
               pendingFiles_.push_back(modifiedFiles[i]);
            }
         }
         break;
      }
      
      FilePath filePath(modifiedFile);
      
      // Skip if the file no longer exists or if it's a directory
      if (!filePath.exists() || filePath.isDirectory()) {
         modifiedProcessed++;
         continue;
      }
      
      // Check if this is a binary file
      std::string extension = filePath.getExtensionLowerCase();
      bool isBinary = isBinaryFileType(extension);
      bool shouldIndex = isIndexableFileType(extension);
      
      // Update with lock
      {
         std::lock_guard<std::mutex> lock(mutex_);
         // Remove existing symbols for this file
         removeSymbolsForFile(modifiedFile);
         
         // Add the file itself as a symbol (like we do in traverseDirectory)
         std::string fileName = filePath.getFilename();
         
         if (isBinary) {
            // For binary files, use appropriate type and don't set line numbers
            Symbol fileSymbol(fileName, "binary", modifiedFile, 0, 0);
            addSymbolNoLock(fileSymbol);
         } else {
            // For text files, add with line counts for all text files
            int fileLines = 0;
            
            std::string content;
            Error error = readStringFromFile(filePath, &content);
            if (!error) {
               // Count the number of lines in the file
               fileLines = std::count(content.begin(), content.end(), '\n') + 1;
            }
            
            Symbol fileSymbol(fileName, "file", modifiedFile, 1, fileLines);
            addSymbolNoLock(fileSymbol);
         }
      }
      
      // Only index non-binary, indexable files
      if (!isBinary && shouldIndex) {
         // Reindex the file using the safe method that doesn't hold the lock during I/O
         indexFileByTypeSafe(filePath);
      }
      
      // Update checksums
      std::string newChecksum = generateFileChecksum(filePath);
      std::string modTime = getFileModifiedTime(filePath);
      
      {
         std::lock_guard<std::mutex> lock(mutex_);
         fileChecksums_[modifiedFile] = FileChecksum(modifiedFile, newChecksum, modTime);
      }
      
      filesProcessed++;
      modifiedProcessed++;
      
      // Check for timeout after each file
      if (hasTimedOut()) {
         // Add remaining modified files to pending
         for (size_t i = modifiedProcessed; i < modifiedFiles.size(); i++) {
            if (std::find(pendingFiles_.begin(), pendingFiles_.end(), modifiedFiles[i]) == pendingFiles_.end()) {
               pendingFiles_.push_back(modifiedFiles[i]);
            }
         }
         break;
      }
   }
   
   // Process new files (up to our limit)
   for (const std::string& newFile : newFiles) {
      // Check if we've reached our limit or timeout
      if (filesProcessed >= MAX_FILES_PER_BATCH || hasTimedOut()) {
         // Add remaining new files to pending
         for (size_t i = newFilesProcessed; i < newFiles.size(); i++) {
            if (std::find(pendingFiles_.begin(), pendingFiles_.end(), newFiles[i]) == pendingFiles_.end()) {
               pendingFiles_.push_back(newFiles[i]);
            }
         }
         break;
      }
      
      FilePath filePath(newFile);
      
      // Skip if the file no longer exists or is not a regular file
      if (!filePath.exists() || !filePath.isRegularFile()) {
         newFilesProcessed++;
         continue;
      }
      
      // Check if this is a binary file
      std::string extension = filePath.getExtensionLowerCase();
      bool isBinary = isBinaryFileType(extension);
      bool shouldIndex = isIndexableFileType(extension);
      
      // Update with lock
      {
         std::lock_guard<std::mutex> lock(mutex_);
         // Add the file itself as a symbol
         std::string fileName = filePath.getFilename();
         
         if (isBinary) {
            // For binary files, use appropriate type and don't set line numbers
            Symbol fileSymbol(fileName, "binary", newFile, 0, 0);
            addSymbolNoLock(fileSymbol);
         } else {
            // For text files, add with line counts for all text files
            int fileLines = 0;
            
            std::string content;
            Error error = readStringFromFile(filePath, &content);
            if (!error) {
               // Count the number of lines in the file
               fileLines = std::count(content.begin(), content.end(), '\n') + 1;
            }
            
            Symbol fileSymbol(fileName, "file", newFile, 1, fileLines);
            addSymbolNoLock(fileSymbol);
         }
      }
      
      // Only index non-binary, indexable files
      if (!isBinary && shouldIndex) {
         // Index the file using the safe method that doesn't hold the lock during I/O
         indexFileByTypeSafe(filePath);
      }
      
      // Add checksum
      std::string newChecksum = generateFileChecksum(filePath);
      std::string modTime = getFileModifiedTime(filePath);
      
      {
         std::lock_guard<std::mutex> lock(mutex_);
         fileChecksums_[newFile] = FileChecksum(newFile, newChecksum, modTime);
      }
      
      filesProcessed++;
      newFilesProcessed++;
      
      // Check for timeout after each file
      if (hasTimedOut()) {
         // Add remaining new files to pending
         for (size_t i = newFilesProcessed; i < newFiles.size(); i++) {
            if (std::find(pendingFiles_.begin(), pendingFiles_.end(), newFiles[i]) == pendingFiles_.end()) {
               pendingFiles_.push_back(newFiles[i]);
            }
         }
         break;
      }
   }
   
   // Update directory structure and save
   {
      std::lock_guard<std::mutex> lock(mutex_);
      // Update directory files with only the ones we've processed
      std::vector<std::string> processedFiles;
      
      // Add files that weren't modified or new
      for (const std::string& file : directoryFiles_) {
         if (std::find(removedFiles.begin(), removedFiles.end(), file) == removedFiles.end() &&
             std::find(modifiedFiles.begin(), modifiedFiles.end(), file) == modifiedFiles.end()) {
            processedFiles.push_back(file);
         }
      }
      
      // Add modified files we processed
      for (size_t i = 0; i < modifiedProcessed; i++) {
         processedFiles.push_back(modifiedFiles[i]);
      }
      
      // Add new files we processed
      for (size_t i = 0; i < newFilesProcessed; i++) {
         processedFiles.push_back(newFiles[i]);
      }
      
      directoryFiles_ = processedFiles;
      
      // Save updated index, structure, and checksums
      Error error = saveAllToStorage(dirId);
      if (error)
         return error;
   }
   
   return Success();
}

// Save all index data (symbols, checksums, and structure) to storage
Error SymbolIndex::saveAllToStorage(const std::string& dirId) {
   // Before saving, update file and directory contexts
   updateFileAndDirectoryContexts();
   
   // Save the symbol map and traversal path
   Error error = saveIndexToStorage(dirId);
   if (error)
      return error;
   
   error = saveChecksumsToStorage(dirId);
   if (error)
      return error;
   
   error = saveDirStructureToStorage(dirId);
   if (error)
      return error;
   
   return Success();
}

// Load all index data (symbols, checksums, and structure) from storage
Error SymbolIndex::loadAllFromStorage(const std::string& dirId) {
   // Validate directory ID
   if (dirId.empty()) {
      return systemError(boost::system::errc::invalid_argument,
                       "Empty directory ID", ERROR_LOCATION);
   }
   
   FilePath baseDir(getIndexBaseDir());
   FilePath storageDir = baseDir.completeChildPath(dirId);
   
   if (!storageDir.exists()) {
      Error error = storageDir.ensureDirectory();
      if (error) {
         return error;
      }
   }
   
   // This will load the symbols and traversal path
   Error error = loadIndexFromStorageNoLock(dirId);
   if (error) {
      return error;
   }
   
   // These were already loaded by loadIndexFromStorageNoLock
   // but we'll check if they failed and try again
   if (fileChecksums_.empty()) {
      error = loadChecksumsFromStorage(dirId);
      if (error) {
         LOG_ERROR(error);
      }
   }
   
   if (directoryFiles_.empty()) {
      error = loadDirStructureFromStorage(dirId);
      if (error) {
         LOG_ERROR(error);
      }
   }
   
   return Success();
}

// Helper function to normalize whitespace in a string
std::string normalizeWhitespace(const std::string& input) {
   std::string result;
   bool lastWasSpace = false;
   bool inQuotes = false;
   
   for (size_t i = 0; i < input.length(); i++) {
      char c = input[i];
      
      // Preserve whitespace inside quotes
      if (c == '"' || c == '\'') {
         // Toggle quote status only if not escaped
         if (i == 0 || input[i-1] != '\\') {
            inQuotes = !inQuotes;
         }
         result.push_back(c);
         lastWasSpace = false;
         continue;
      }
      
      if (std::isspace(c)) {
         if (!lastWasSpace && !inQuotes) {
            result.push_back(' ');
            lastWasSpace = true;
         } else if (inQuotes) {
            // Preserve whitespace in quotes
            result.push_back(c);
         }
      } else {
         result.push_back(c);
         lastWasSpace = false;
      }
   }
   
   // Trim leading and trailing whitespace while preserving important characters
   if (!result.empty() && result[0] == ' ')
      result.erase(0, 1);
   
   if (!result.empty() && result[result.size() - 1] == ' ')
      result.pop_back();
   
   return result;
}

// Helper function to format parameters in function signatures
// Ensures parameters are separated by a comma followed by a single space
std::string formatFunctionParameters(const std::string& input) {
   std::string result;
   bool inQuotes = false;
   bool lastWasComma = false;
   int parenDepth = 0;
   
   for (size_t i = 0; i < input.length(); i++) {
      char c = input[i];
      
      // Track parentheses
      if (c == '(' && !inQuotes) {
         parenDepth++;
         result.push_back(c);
         continue;
      } else if (c == ')' && !inQuotes) {
         parenDepth--;
         result.push_back(c);
         continue;
      }
      
      // Handle quotes to avoid processing commas inside strings
      if (c == '"' || c == '\'') {
         // Toggle quote status only if not escaped
         if (i == 0 || input[i-1] != '\\') {
            inQuotes = !inQuotes;
         }
         result.push_back(c);
         lastWasComma = false;
         continue;
      }
      
      // Special handling for commas and parameter assignments
      if (!inQuotes && parenDepth == 1) { // Only format at the top level of parameters
         // For a comma, add it followed by a single space
         if (c == ',') {
            result.push_back(',');
            result.push_back(' ');
            lastWasComma = true;
            continue;
         }
         
         // Skip spaces after commas (we already added one)
         if (lastWasComma && std::isspace(c)) {
            continue;
         }
         
         // For parameter assignment (=), ensure single space before and after
         if (c == '=' && i > 0) {
            // If the previous character isn't a space, add one
            if (!result.empty() && result.back() != ' ') {
               result.push_back(' ');
            }
            result.push_back('=');
            result.push_back(' ');
            
            // Skip any spaces that may follow in the input
            while (i + 1 < input.length() && std::isspace(input[i + 1])) {
               i++;
            }
            continue;
         }
      }
      
      result.push_back(c);
      lastWasComma = false;
   }
   
   // Ensure we don't have a hanging comma at the end of the parameter list
   size_t lastParenPos = result.rfind(')');
   if (lastParenPos != std::string::npos) {
      size_t lastCommaPos = result.rfind(',', lastParenPos);
      if (lastCommaPos != std::string::npos) {
         // Check if there's only whitespace between the comma and closing paren
         bool onlyWhitespace = true;
         for (size_t i = lastCommaPos + 1; i < lastParenPos; i++) {
            if (!std::isspace(result[i])) {
               onlyWhitespace = false;
               break;
            }
         }
         if (onlyWhitespace) {
            // Remove the comma and any whitespace after it
            result.erase(lastCommaPos, lastParenPos - lastCommaPos);
         }
      }
   }
   
   return result;
}

// Extract function signature with proper parentheses matching
std::string extractRFunctionSignature(const std::vector<std::string>& lines, size_t startLineIndex, size_t funcPos) {
   // Start with basic signature
   std::string signature = "function(";
   
   // Concatenate all lines into one string for easier processing
   std::string allContent;
   bool foundOpenParen = false;
   
   // Find opening parenthesis and collect all text
   for (size_t i = startLineIndex; i < lines.size(); i++) {
      const std::string& line = lines[i];
      
      // On first line, look after "function"
      if (i == startLineIndex) {
         size_t openPos = line.find('(', funcPos + 8); // 8 is length of "function"
         if (openPos != std::string::npos) {
            foundOpenParen = true;
            // Add everything after the opening parenthesis
            allContent += line.substr(openPos + 1);
         }
      } 
      // If we haven't found opening paren yet, check this line
      else if (!foundOpenParen) {
         size_t openPos = line.find('(');
         if (openPos != std::string::npos) {
            foundOpenParen = true;
            // Add everything after the opening parenthesis
            allContent += line.substr(openPos + 1);
         }
      }
      // Otherwise add entire lines
      else {
         allContent += line;
      }
      
      // Add space between lines
      if (i < lines.size() - 1) {
         allContent += " ";
      }
   }
   
   // If we didn't find an opening parenthesis, return default signature
   if (!foundOpenParen) {
      return "function()";
   }
   
   // Now extract parameters from combined content
   int parenCount = 1; // We start after the first open paren
   std::string params;
   
   for (size_t i = 0; i < allContent.length(); i++) {
      char c = allContent[i];
      
      if (c == '(') {
         parenCount++;
         params += c;
      } 
      else if (c == ')') {
         parenCount--;
         if (parenCount == 0) {
            // We found the matching closing parenthesis
            signature += params + ")";
            
            // Format the signature
            return formatFunctionParameters(normalizeWhitespace(signature));
         }
         params += c;
      }
      else {
         params += c;
      }
   }
   
   // If we get here, we didn't find the matching closing parenthesis
   // Just use what we collected
   signature += params;
   if (signature.find(')') == std::string::npos) {
      signature += ")";
   }
   
   return formatFunctionParameters(normalizeWhitespace(signature));
}

// Make sure the parent directory for a file exists
Error ensureParentDirectoryExists(const FilePath& filePath) {
   FilePath parentDir = filePath.getParent();
   if (!parentDir.exists()) {
      return parentDir.ensureDirectory();
   }
   return Success();
}

// New method to update file and directory contexts
void SymbolIndex::updateFileAndDirectoryContexts() {
   // Maps to track symbols by file and directory
   std::unordered_map<std::string, std::vector<Symbol*>> symbolsByFile;
   std::unordered_map<std::string, std::vector<Symbol*>> symbolsByDir;
   
   // First pass: collect symbols by file and directory
   for (auto& pair : symbolMap_) {
      for (auto& symbol : pair.second) {
         // Skip files, directories, and variables in this collection
         if (symbol.type == "file" || symbol.type == "directory" || symbol.type == "variable")
            continue;
            
         // Add to file map
         symbolsByFile[symbol.filePath].push_back(&symbol);
         
         // Get directory path
         FilePath path(symbol.filePath);
         std::string dirPath = path.getParent().getAbsolutePath();
         symbolsByDir[dirPath].push_back(&symbol);
      }
   }
   
   // Second pass: update file symbols with children (headers and functions)
   for (auto& pair : symbolMap_) {
      for (auto& symbol : pair.second) {
         if (symbol.type == "file" || symbol.type == "image" || symbol.type == "binary") {
            // Clear existing children
            symbol.children.clear();
            
            // Set parent as directory
            FilePath filePath(symbol.filePath);
            symbol.parents = filePath.getParent().getAbsolutePath();
            
            // Get symbols for this file
            auto it = symbolsByFile.find(symbol.filePath);
            if (it != symbolsByFile.end()) {
               // First collect top-level headers (header1)
               for (Symbol* childSymbol : it->second) {
                  if (childSymbol->type == "header1") {
                     symbol.addChild(childSymbol->name);
                  }
               }
               
               // Then collect chunks (for markdown files)
               for (Symbol* childSymbol : it->second) {
                  if (childSymbol->type == "chunk") {
                     symbol.addChild(childSymbol->name);
                  }
               }
               
               // Then collect top-level functions - those not in chunks
               for (Symbol* childSymbol : it->second) {
                  if (childSymbol->type == "function" && !boost::algorithm::starts_with(childSymbol->parents, "chunk_")) {
                     symbol.addChild(childSymbol->name);
                  }
               }
            }
         }
         else if (symbol.type == "directory") {
            // Clear existing children
            symbol.children.clear();
            
            // Directory has no parent
            symbol.parents = "";
            
            // Get the directory path
            std::string dirPath = symbol.filePath;
            
            // Add immediate children (files and directories)
            FilePath dir(dirPath);
            std::vector<FilePath> children;
            Error error = dir.getChildren(children);
            if (!error) {
               for (const FilePath& child : children) {
                  // COMPLETELY exclude certain filenames from directory children
                  if (!isExcludedFilename(child.getFilename())) {
                     symbol.addChild(child.getFilename());
                  }
               }
            }
         }
         else if (boost::algorithm::starts_with(symbol.type, "header")) {
            // Clear existing children
            symbol.children.clear();
            
            // Set parent initially as file
            symbol.parents = symbol.filePath;
            
            // Extract the header level from the type (e.g., "header1" -> 1)
            int headerLevel = 0;
            try {
               headerLevel = std::stoi(symbol.type.substr(6));
            } catch (const std::exception&) {
               // If we can't parse the level, default to 0
               headerLevel = 0;
            }
            
            // Get symbols for this file and header
            auto it = symbolsByFile.find(symbol.filePath);
            if (it != symbolsByFile.end()) {
               // First, identify the parent header for this header
               if (headerLevel > 1) {
                  // Look for the closest parent header with a lower level
                  Symbol* parentHeader = nullptr;
                  int parentHeaderLevel = 0;
                  int closestParentEndLine = 0;
                  
                  for (Symbol* otherHeader : it->second) {
                     if (!boost::algorithm::starts_with(otherHeader->type, "header"))
                        continue;
                        
                     int otherLevel = 0;
                     try {
                        otherLevel = std::stoi(otherHeader->type.substr(6));
                     } catch (const std::exception&) {
                        otherLevel = 0;
                     }
                     
                     // Check if this header could be a parent:
                     // 1. It must have a lower level (e.g., h1 is parent of h2)
                     // 2. It must start before this header
                     // 3. This header must be within its range
                     // 4. It must be the closest such header
                     if (otherLevel < headerLevel && 
                         otherHeader->lineStart < symbol.lineStart && 
                         (otherHeader->lineEnd == 0 || symbol.lineStart <= otherHeader->lineEnd) &&
                         (parentHeader == nullptr || 
                          (otherHeader->lineStart > closestParentEndLine) ||
                          (otherHeader->lineStart == closestParentEndLine && otherLevel > parentHeaderLevel))) {
                        
                        parentHeader = otherHeader;
                        parentHeaderLevel = otherLevel;
                        closestParentEndLine = otherHeader->lineStart;
                     }
                  }
                  
                  // If we found a parent header, update the relationship
                  if (parentHeader != nullptr) {
                     // Set this header's parent to the parent header's name
                     symbol.parents = parentHeader->name;
                     
                     // Add this header as a child of the parent header
                     parentHeader->addChild(symbol.name);
                  }
               }
               
               // Next, collect all direct child headers
               // (a direct child's level is exactly one more than this header's level)
               for (Symbol* childSymbol : it->second) {
                  if (boost::algorithm::starts_with(childSymbol->type, "header")) {
                     int childLevel = 0;
                     try {
                        childLevel = std::stoi(childSymbol->type.substr(6));
                     } catch (const std::exception&) {
                        childLevel = 0;
                     }
                     
                     // Child header must be one level deeper and within this header's range
                     if (childLevel == headerLevel + 1 && 
                         childSymbol->lineStart > symbol.lineStart && 
                         (symbol.lineEnd == 0 || childSymbol->lineStart <= symbol.lineEnd)) {
                        
                        // Make sure it's a direct child (no intermediate headers of the same level)
                        bool isDirectChild = true;
                        for (Symbol* otherHeader : it->second) {
                           if (boost::algorithm::starts_with(otherHeader->type, "header")) {
                              int otherLevel = 0;
                              try {
                                 otherLevel = std::stoi(otherHeader->type.substr(6));
                              } catch (const std::exception&) {
                                 otherLevel = 0;
                              }
                              
                              // Check if there's an intermediate header of the same level
                              if (otherLevel == headerLevel && 
                                  otherHeader != &symbol &&
                                  otherHeader->lineStart > symbol.lineStart &&
                                  otherHeader->lineStart < childSymbol->lineStart) {
                                 isDirectChild = false;
                                 break;
                              }
                           }
                        }
                        
                        if (isDirectChild) {
                           symbol.addChild(childSymbol->name);
                        }
                     }
                  }
               }
               
               // Finally, add direct functions as children
               for (Symbol* childSymbol : it->second) {
                  // Only include functions defined in this header's range that aren't already part of a child header
                  if (childSymbol->type == "function" && 
                      childSymbol->lineStart > symbol.lineStart && 
                      (symbol.lineEnd == 0 || childSymbol->lineStart <= symbol.lineEnd)) {
                     
                     // Check if this function belongs to a child header
                     bool belongsToChildHeader = false;
                     
                     for (Symbol* otherHeader : it->second) {
                        if (boost::algorithm::starts_with(otherHeader->type, "header") &&
                            otherHeader != &symbol &&
                            otherHeader->lineStart > symbol.lineStart &&
                            otherHeader->lineStart < childSymbol->lineStart &&
                            (otherHeader->lineEnd == 0 || childSymbol->lineStart <= otherHeader->lineEnd)) {
                           
                           belongsToChildHeader = true;
                           break;
                        }
                     }
                     
                     if (!belongsToChildHeader) {
                        symbol.addChild(childSymbol->name);
                     }
                  }
               }
            }
         }
         else if (symbol.type == "chunk") {
            // Clear existing children
            symbol.children.clear();
            
            // Set parent as file
            symbol.parents = symbol.filePath;
            
            // Get symbols for this file and check for functions with this chunk as context
            auto it = symbolsByFile.find(symbol.filePath);
            if (it != symbolsByFile.end()) {
               for (Symbol* childSymbol : it->second) {
                  // Add functions that belong to this chunk (no longer adding variables)
                  if (childSymbol->type == "function") {
                     if (childSymbol->parents == symbol.name) {
                        symbol.addChild(childSymbol->name);
                     }
                  }
               }
            }
         }
      }
   }
   
   // Third pass: update header line end points and establish parent-child relationships
   for (auto& pair : symbolMap_) {
      for (auto& symbol : pair.second) {
         if (boost::algorithm::starts_with(symbol.type, "header")) {
            // Extract the header level
            int headerLevel = 0;
            try {
               headerLevel = std::stoi(symbol.type.substr(6));
            } catch (const std::exception&) {
               headerLevel = 0;
            }
            
            // Find the next header of same or lower level to determine the end line
            auto it = symbolsByFile.find(symbol.filePath);
            if (it != symbolsByFile.end()) {
               int nextHeaderLine = INT_MAX;
               
               for (Symbol* nextSymbol : it->second) {
                  if (boost::algorithm::starts_with(nextSymbol->type, "header")) {
                     int nextLevel = 0;
                     try {
                        nextLevel = std::stoi(nextSymbol->type.substr(6));
                     } catch (const std::exception&) {
                        nextLevel = 0;
                     }
                     
                     // Next header must be same or lower level and come after current header
                     if (nextLevel <= headerLevel && nextSymbol->lineStart > symbol.lineStart && 
                         nextSymbol->lineStart < nextHeaderLine) {
                        nextHeaderLine = nextSymbol->lineStart;
                     }
                  }
               }
               
               if (nextHeaderLine != INT_MAX) {
                  // Set end line to one before the next header
                  symbol.lineEnd = nextHeaderLine - 1;
               } else {
                  // No next header, go to end of file
                  // Find the file's line count
                  for (Symbol* fileSymbol : it->second) {
                     if (fileSymbol->type == "file") {
                        symbol.lineEnd = fileSymbol->lineEnd;
                        break;
                     }
                  }
                  
                  // If we didn't find the file symbol, use the end of file marker
                  if (symbol.lineEnd == 0) {
                     FilePath file(symbol.filePath);
                     std::string content;
                     Error error = readStringFromFile(file, &content);
                     if (!error) {
                        // Count the number of lines in the file
                        int lineCount = std::count(content.begin(), content.end(), '\n') + 1;
                        symbol.lineEnd = lineCount;
                     }
                  }
               }
            }
         }
      }
   }
   
   // Now build header hierarchy by sorting headers by line number and establishing parent-child relationships
   for (auto& _ : symbolMap_) {
      (void)_; // Suppress unused variable warning
      std::unordered_map<std::string, std::vector<Symbol*>> headersByFile;
      
      // First, collect all headers by file
      for (auto& filePair : symbolsByFile) {
         std::string filePath = filePair.first;
         std::vector<Symbol*> headers;
         
         // Get all headers in this file
         for (Symbol* sym : filePair.second) {
            if (boost::algorithm::starts_with(sym->type, "header")) {
               headers.push_back(sym);
            }
         }
         
         // Sort headers by line number
         std::sort(headers.begin(), headers.end(), 
                  [](const Symbol* a, const Symbol* b) { 
                     return a->lineStart < b->lineStart; 
                  });
         
         headersByFile[filePath] = headers;
      }
      
      // Now process each file's headers to establish hierarchy
      for (auto& fileHeadersPair : headersByFile) {
         std::string filePath = fileHeadersPair.first;
         std::vector<Symbol*>& headers = fileHeadersPair.second;
         
         // Stack to keep track of parent headers at each level
         std::vector<Symbol*> headerStack(10, nullptr); // Assume max 10 header levels (h1-h9)
         
         for (Symbol* header : headers) {
            // Get header level
            int level = 0;
            try {
               level = std::stoi(header->type.substr(6));
            } catch (const std::exception&) {
               level = 1; // Default to h1 if level can't be determined
            }
            
            // Ensure level is valid for our stack
            if (level <= 0 || level >= 10) {
               level = 1;
            }
            
            // Clear any stack entries at or above current level
            for (int i = level; i < 10; i++) {
               headerStack[i] = nullptr;
            }
            
            // Set parent to the nearest lower-level header
            Symbol* parent = nullptr;
            for (int i = level - 1; i >= 1; i--) {
               if (headerStack[i] != nullptr) {
                  parent = headerStack[i];
                  break;
               }
            }
            
            // Update parent relationship
            if (parent != nullptr) {
               // Set this header's parent to the parent header
               header->parents = parent->name;
               
               // Add this header as a child of the parent header
               parent->addChild(header->name);
            } else {
               // No parent header found, set file as parent
               header->parents = filePath;
               
               // Find the file symbol to add this as a child
               for (Symbol* fileSymbol : symbolsByFile[filePath]) {
                  if (fileSymbol->type == "file") {
                     fileSymbol->addChild(header->name);
                     break;
                  }
               }
            }
            
            // Add this header to the stack at its level
            headerStack[level] = header;
         }
      }
   }

   // Fourth pass: update function parent relationships
   for (auto& pair : symbolMap_) {
      for (auto& symbol : pair.second) {
         // Handle functions - set parent to chunk, header or file
         if (symbol.type == "function") {
            bool foundParent = false;
            
            auto it = symbolsByFile.find(symbol.filePath);
            if (it != symbolsByFile.end()) {
               // First check if function is in a chunk
               for (Symbol* chunkSymbol : it->second) {
                  if (chunkSymbol->type == "chunk" && 
                     symbol.lineStart >= chunkSymbol->lineStart && 
                     symbol.lineStart <= chunkSymbol->lineEnd) {
                     // Function is within this chunk
                     symbol.parents = chunkSymbol->name;
                     foundParent = true;
                     break;
                  }
               }
               
               // If not in a chunk, check if in a header section
               if (!foundParent) {
                  // Find the most specific header (highest level) that contains this function
                  Symbol* bestHeaderParent = nullptr;
                  int bestHeaderLevel = 0;
                  
                  for (Symbol* headerSymbol : it->second) {
                     if (boost::algorithm::starts_with(headerSymbol->type, "header") &&
                        symbol.lineStart > headerSymbol->lineStart &&
                        symbol.lineStart <= headerSymbol->lineEnd) {
                        
                        // Extract the header level
                        int headerLevel = 0;
                        try {
                           headerLevel = std::stoi(headerSymbol->type.substr(6));
                        } catch (const std::exception&) {
                           headerLevel = 0;
                        }
                        
                        // Higher level number means more specific header
                        if (bestHeaderParent == nullptr || headerLevel > bestHeaderLevel) {
                           bestHeaderParent = headerSymbol;
                           bestHeaderLevel = headerLevel;
                        }
                     }
                  }
                  
                  if (bestHeaderParent != nullptr) {
                     symbol.parents = bestHeaderParent->name;
                     // Also add this function as a child of the header
                     bestHeaderParent->addChild(symbol.name);
                     foundParent = true;
                  }
               }
            }
            
            // If not in chunk or header, set file as parent
            if (!foundParent) {
               symbol.parents = symbol.filePath;
               
               // Add as child of file
               for (Symbol* fileSymbol : it->second) {
                  if (fileSymbol->type == "file") {
                     fileSymbol->addChild(symbol.name);
                     break;
                  }
               }
            }
         }
         
         // REMOVED: Variable processing code - no longer indexing variables
      }
   }
}

// Path to the pending files file for a specific directory ID
std::string getPendingFilesFile(const std::string& dirId) 
{
   return getIndexFilePath(dirId, "pending_files.json");
}

// Save pending files list to storage
Error SymbolIndex::savePendingFilesToStorage(const std::string& dirId) 
{   
   FilePath pendingFilesFile(getPendingFilesFile(dirId));
   
   json::Object pendingObj;
   json::Array fileArray;
   
   for (const std::string& filePath : pendingFiles_) {
      fileArray.push_back(filePath);
   }
   
   pendingObj["pending_files"] = fileArray;
   
   std::ostringstream jsonStream;
   pendingObj.writeFormatted(jsonStream);
   
   Error error = writeStringToFile(pendingFilesFile, jsonStream.str());
   if (error) {
      LOG_ERROR(error);
   }
   
   return error;
}

// Load pending files list from storage
Error SymbolIndex::loadPendingFilesFromStorage(const std::string& dirId) 
{   
   FilePath pendingFilesFile(getPendingFilesFile(dirId));
   
   // Clear current pending files
   pendingFiles_.clear();
   
   // If the file doesn't exist, that's fine (no pending files)
   if (!pendingFilesFile.exists()) {
      return Success();
   }
   
   // Read and parse JSON
   json::Value jsonValue;
   Error error = readAndParseJson(pendingFilesFile, &jsonValue);
   if (error) {
      return error;
   }
   
   json::Object pendingObj = jsonValue.getObject();
   json::Array fileArray = pendingObj["pending_files"].getArray();
   
   // Load file paths
   for (const json::Value& pathValue : fileArray) {
      if (!pathValue.isString()) {
         continue;
      }
      
      std::string filePath = pathValue.getString();
      pendingFiles_.push_back(filePath);
   }
   
   return Success();
}

// Check if the indexing has timed out
bool SymbolIndex::hasTimedOut() const 
{
   auto now = std::chrono::steady_clock::now();
   auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(now - indexingStartTime_).count();
   return elapsed >= INDEXING_TIMEOUT_MS;
}

// Event handlers for real-time symbol index updates
void onSourceDocUpdated(boost::shared_ptr<source_database::SourceDocument> pDoc)
{
   // Only update if we have a built index
   if (!SymbolIndex::getInstance().isIndexBuilt()) {
      return;
   }
   
   // Skip documents without content
   if (pDoc->contents().empty()) {
      return;
   }
   
   // Determine the file path to use (same logic as indexOpenDocuments)
   std::string filePath;
   if (!pDoc->path().empty()) {
      // Document has a file path - use it and override any existing symbols for this file
      filePath = module_context::resolveAliasedPath(pDoc->path()).getAbsolutePath();
   } else {
      // Unsaved document without a file path - use tempName if available
      std::string tempName = pDoc->getProperty("tempName");
      
      if (!tempName.empty()) {
         if (!pDoc->id().empty()) {
            filePath = "__UNSAVED_" + pDoc->id().substr(0, 4) + "__/" + tempName;
         } else {
            filePath = "__UNSAVED__/" + tempName;
         }
      } else {
         if (!pDoc->id().empty()) {
            filePath = "__UNSAVED_" + pDoc->id().substr(0, 4) + "__/Untitled";
         } else {
            filePath = "__UNSAVED__/Untitled";
         }
      }
   }
   
   // Remove existing symbols for this file and re-index with current content
   SymbolIndex::getInstance().removeSymbolsForFile(filePath);
   
   SymbolIndex::getInstance().indexContentByDocumentType(pDoc->contents(), filePath, pDoc->type());
   
   
   // Add the file itself as a symbol
   int fileLines = std::count(pDoc->contents().begin(), pDoc->contents().end(), '\n') + 1;
   
   std::string fileName;
   size_t lastSlash = filePath.find_last_of("/\\");
   if (lastSlash != std::string::npos) {
      fileName = filePath.substr(lastSlash + 1);
   } else {
      fileName = filePath;
   }
   
   std::string parentContext = (pDoc->path().empty()) ? "" : filePath.substr(0, lastSlash != std::string::npos ? lastSlash : 0);
   Symbol fileSymbol(fileName, "file", filePath, 1, fileLines, parentContext);
   
   SymbolIndex::getInstance().addSymbol(fileSymbol);
}

void onSourceDocRemoved(const std::string& id, const std::string& path)
{
   // Only update if we have a built index
   if (!SymbolIndex::getInstance().isIndexBuilt()) {
      return;
   }
      
   if (!path.empty()) {
      // Remove symbols for saved file
      std::string filePath = module_context::resolveAliasedPath(path).getAbsolutePath();
      SymbolIndex::getInstance().removeSymbolsForFile(filePath);
   }
   
   // Also try to remove unsaved file patterns (we don't have access to the document here,
   // so we'll need to remove any potential unsaved paths for this ID)
   if (!id.empty() && id.length() >= 4) {
      std::string unsavedPattern = "__UNSAVED_" + id.substr(0, 4) + "__/";
      // Note: We can't easily remove by pattern, so this is a limitation
      // The symbols will remain until the next full reindex
   }
}

void onAllSourceDocsRemoved()
{
   // Only update if we have a built index
   if (!SymbolIndex::getInstance().isIndexBuilt()) {
      return;
   }
      
   // Clear all unsaved symbols (those with "__UNSAVED" in the path)
   // Note: This is a simplification - ideally we'd have a more efficient way to do this
   auto& symbolMap = SymbolIndex::getInstance().getSymbolMap();
   for (auto& pair : symbolMap) {
      std::vector<Symbol>& symbols = pair.second;
      symbols.erase(std::remove_if(symbols.begin(), symbols.end(),
                                   [](const Symbol& symbol) {
                                       return symbol.filePath.find("__UNSAVED") != std::string::npos;
                                   }),
                    symbols.end());
   }
   
   // Remove empty entries
   for (auto it = symbolMap.begin(); it != symbolMap.end();) {
      if (it->second.empty()) {
         it = symbolMap.erase(it);
      } else {
         ++it;
      }
   }
}

} // namespace symbol_index
} // namespace modules
} // namespace session
} // namespace rstudio 