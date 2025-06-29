/*
 * SessionQuartoXRefs.cpp
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

#include "SessionQuartoXRefs.hpp"

#include <algorithm>

#include <shared_core/Error.hpp>
#include <shared_core/json/Json.hpp>
#include <core/Exec.hpp>
#include <core/Base64.hpp>
#include <core/Version.hpp>
#include <core/FileSerializer.hpp>
#include <core/PerformanceTimer.hpp>
#include <core/system/FileScanner.hpp>
#include <core/system/Process.hpp>
#include <core/json/JsonRpc.hpp>

#include <session/projects/SessionProjects.hpp>
#include <session/SessionModuleContext.hpp>
#include <session/IncrementalFileChangeHandler.hpp>

#include <session/SessionQuarto.hpp>

#include "SessionQuarto.hpp"

using namespace rstudio::core;
using namespace rstudio::session::module_context;
using namespace boost::placeholders;

namespace rstudio {
namespace session {

using namespace quarto;

namespace {

const char * const kBaseDir = "baseDir";
const char * const kRefs = "refs";
const char * const kFile = "file";
const char * const kType = "type";
const char * const kId = "id";
const char * const kSuffix = "suffix";
const char * const kTitle = "title";

const char * const kFigType = "fig";
const char * const kTblType = "tbl";


FilePath quartoCrossrefDirV1(const FilePath& projectDir)
{
   return projectDir
       .completeChildPath(".quarto")
       .completeChildPath("crossref");
}

FilePath quartoCrossrefDirV2(const FilePath& projectDir)
{
   return projectDir
      .completeChildPath(".quarto")
      .completeChildPath("xref");
}

json::Array readXRefIndex(const FilePath& indexPath, const std::string& filename, bool fileCache = false, bool* pExists = nullptr)
{
   // default to not exists
   if (pExists)
   {
      *pExists = false;
   }

   // tolerate a missing index
   if (!indexPath.exists())
       return json::Array();

   // read the index as a string (tolerate empty file)
   std::string index;
   Error error = core::readStringFromFile(indexPath, &index);
   if (error)
   {
      LOG_ERROR(error);
   }
   if (boost::algorithm::trim_copy(index).empty())
      return json::Array();

   // parse json w/ validation
   json::Object quartoIndexJson;

   if (fileCache)
   {
      error = quartoIndexJson.parse(index);
   } else
   {
      error = quartoIndexJson.parseAndValidate(
          index,
          resourceFileAsString("schema/quarto-xref.json")
      );
   }

   if (error)
   {
      LOG_ERROR(error);
      return json::Array();
   }

   json::Array xrefs;

   if (fileCache)
   {
      json::Array entries = quartoIndexJson["entries"].getArray();
      for (const json::Value& entry : entries)
      {
         std::string filename, type, id;
         json::Object valObject = entry.getObject();
         json::readObject(valObject, "file", filename, "type", type, "id", id);
         if (!filename.empty() && !type.empty() && !id.empty())
         {
           xrefs.push_back(entry);
         }
      }
   }
   else
   {
      // read xrefs (already validated so don't need to dance around types/existence)
      boost::regex keyRegex("^(\\w+)-(.*?)(-\\d+)?$");
      json::Array entries = quartoIndexJson["entries"].getArray();
      for (const json::Value& entry : entries)
      {
          json::Object valObject = entry.getObject();
          std::string key, caption;
          json::readObject(valObject, "key", key, "caption", caption);
          boost::smatch match;
          if (boost::regex_search(key, match, keyRegex))
          {
            json::Object xref;
            xref[kFile] = filename;
            xref[kType] = match[1].str();
            xref[kId] = match[2].str();
            xref[kSuffix] = (match.length() > 3) ? match[3].str() : "";
            xref[kTitle] = caption;
            xrefs.push_back(xref);
          }
      }
   }

   if (pExists)
   {
      *pExists = true;
   }

   return xrefs;
}

json::Array indexSourceFile(const std::string& contents, const std::string& filename)
{
   QuartoConfig config = quartoConfig();
   FilePath resourcesPath(config.resources_path);
   FilePath filtersPath = resourcesPath.completePath("filters");

   static FilePath xrefIndexingDir;
   if (xrefIndexingDir.isEmpty())
   {
      // generate and create dir
      xrefIndexingDir = module_context::tempDir();
      Error error = xrefIndexingDir.ensureDirectory();
      if (error)
      {
         LOG_ERROR(error);
         return json::Array();
      }

      // write defaults file with filters
      FilePath defaultsFile = xrefIndexingDir.completePath("defaults.yml");
      boost::format fmt("filters:\n  - %1%\n  - %2%\n");
      std::string defaults = boost::str(fmt %
         string_utils::utf8ToSystem(filtersPath.completePath("quarto-init/quarto-init.lua").getAbsolutePath()) %
         string_utils::utf8ToSystem(filtersPath.completePath("crossref/crossref.lua").getAbsolutePath())
      );
      error = core::writeStringToFile(defaultsFile, defaults);
      if (error)
      {
         LOG_ERROR(error);
         return json::Array();
      }
   }

   // create index
   std::string filterParams;
   std::string filterParamsJson =
     "{ \"crossref-index-file\": \"index.json\", \"crossref-input-type\": \"qmd\" }";
   Error error = core::base64::encode(filterParamsJson, &filterParams);
   if (error)
   {
      LOG_ERROR(error);
      return json::Array();
   }
   core::system::ProcessOptions options;
   options.workingDir = xrefIndexingDir;
   core::system::Options env;
   core::system::environment(&env);
   core::system::setenv(&env, "QUARTO_FILTER_PARAMS", filterParams);
   core::system::setenv(&env, "QUARTO_SHARE_PATH", resourcesPath.getAbsolutePath());
   options.environment = env;
   std::vector<std::string> args;

   // use qmd-reader.lua for --from if available
   args.push_back("--from");
   auto qmdReaderPath = filtersPath.completePath("qmd-reader.lua");
   if (qmdReaderPath.exists()) {
      args.push_back(string_utils::utf8ToSystem(qmdReaderPath.getAbsolutePath()));
   } else {
      args.push_back("markdown");
   }
   args.push_back("--to");
   args.push_back("native");
   args.push_back("--defaults");
   args.push_back("defaults.yml");

   // add data-dir
   FilePath dataDirPath = resourcesPath.completePath("pandoc/datadir");
   args.push_back(("--data-dir"));
   args.push_back(core::string_utils::utf8ToSystem(dataDirPath.getAbsolutePath()));

   core::system::ProcessResult result;
   error = module_context::runPandoc(
            config.pandoc_path,
            args,
            contents,
            options,
            &result);
   
   if (!error)
   {
      if (result.exitStatus == EXIT_SUCCESS)
      {
         return readXRefIndex(FilePath(xrefIndexingDir).completeChildPath("index.json"), filename);
      }
      else
      {
         LOG_ERROR_MESSAGE(result.stdErr);
      }
   }
   else
   {
      LOG_ERROR(error);
   }

   return json::Array();
}


json::Array indexSourceFile(const FilePath& srcFile, const std::string& filename)
{
   // keep a cache of previously indexed src files -- use it if the cached index
   // has content and its modification time is after the src file modification time
   const char * const kQuartoCrossrefSrcFileIndexes = "quarto-crossref-qmd";
   FilePath srcFileIndex;
   Error error = perFilePathStorage(kQuartoCrossrefSrcFileIndexes, srcFile, false, &srcFileIndex);
   if (!error)
   {
      if (srcFileIndex.getLastWriteTime() > srcFile.getLastWriteTime())
      {
         bool exists = false;
         json::Array xrefs = readXRefIndex(srcFileIndex, filename, true, &exists);
         if (exists)
            return xrefs;
      }
   }
   else
   {
      LOG_ERROR(error);
   }

   // index source file
   std::string contents;
   error = readStringFromFile(srcFile, &contents);
   if (error)
   {
      LOG_ERROR(error);
      return json::Array();
   }
   json::Array xrefs = indexSourceFile(contents, filename);

   // write to cache if we have one
   if (!srcFileIndex.isEmpty())
   {
      json::Object indexJson;
      indexJson["entries"] = xrefs;
      error = writeStringToFile(srcFileIndex, indexJson.writeFormatted());
      if (error)
         LOG_ERROR(error);
   }

   return xrefs;
}


boost::optional<std::string> unsavedSrcFileContents(const FilePath& srcPath)
{
   // see if this file is currently in the source database (ignore errors as it might not be there)
   std::string id;
   Error error = source_database::getId(srcPath, &id);
   if (!error)
   {
      boost::shared_ptr<source_database::SourceDocument> pDoc(new source_database::SourceDocument());
      Error error = source_database::get(id, pDoc);
      if (!error)
      {
         if (pDoc->dirty())
            return pDoc->contents();
      }
      else
      {
         LOG_ERROR(error);
      }
   }
   else if (error != core::systemError(boost::system::errc::no_such_file_or_directory, ErrorLocation()))
   {
      LOG_ERROR(error);
   }
   return boost::optional<std::string>();
}


json::Array resolvedXRefIndex(const FilePath& renderedIndexPath, const FilePath& srcPath, const std::string& filename)
{
   // read any rendered xref index we have on disk. this will either be the definitive
   // list of xrefs (in the case where there are no subsequent in-memory or on disk
   // updates to the srcPath) or will be used as a supplement to discover xrefs created
   // by computations (e.g. subfigures)
   json::Array renderedXrefs = readXRefIndex(renderedIndexPath, filename);

   // see if we can get some srcXrefs as the baseline
   json::Value srcXrefs;

   // is there unsaved src file contents for this file?
   auto unsaved = unsavedSrcFileContents(srcPath);
   if (unsaved.has_value())
   {
      srcXrefs = indexSourceFile(unsaved.get(), filename);
   }
   // otherwise, check to see if the src file is more recent than the renderedIndexPath
   else if (!renderedIndexPath.exists() || renderedIndexPath.getSize() == 0 ||
            (srcPath.getLastWriteTime() > renderedIndexPath.getLastWriteTime()))
   {
      srcXrefs = indexSourceFile(srcPath, filename);
   }

   // if we have src xrefs, use those as the baseline then supplement w/ computational refs
   if (srcXrefs.isArray())
   {
      json::Array xrefs = srcXrefs.getArray();
      std::copy_if(renderedXrefs.begin(), renderedXrefs.end(), std::back_inserter(xrefs),
                   [&xrefs](json::Value xrefValue) {
         json::Object xref = xrefValue.getObject();
         std::string type = xref[kType].getString();
         std::string id = xref[kId].getString();
         std::string suffix = xref[kSuffix].getString();
         if ((type == kFigType || type == kTblType) && !suffix.empty())
         {
            auto it = std::find_if(xrefs.begin(), xrefs.end(), [type, id](json::Value srcXrefValue) {
              json::Object srcXref = srcXrefValue.getObject();
              return srcXref[kType].getString() == type &&
                     srcXref[kId].getString() == id &&
                     srcXref[kSuffix].getString().empty();
            });
            return it != xrefs.end();
         }
         else
         {
            return false;
         }
      });
      return xrefs;
   }
   else
   {
      return renderedXrefs;
   }
}

json::Array readProjectXRefIndex(const FilePath& indexPath, const FilePath& srcPath, std::string filename)
{
   if (indexPath.isDirectory())
   {
      // there will be one or more json files in here (for each format). just
      // pick the most recently written one
      std::vector<FilePath> indexFiles;
      Error error = indexPath.getChildren(indexFiles);
      if (error)
      {
         LOG_ERROR(error);
         return json::Array();
      }
      FilePath mostRecentIndex;
      for (auto indexFile : indexFiles)
      {
         if (indexFile.getExtensionLowerCase() == ".json")
         {
            if (mostRecentIndex.isEmpty())
            {
               mostRecentIndex = indexFile;
            }
            else if (indexFile.getLastWriteTime() > mostRecentIndex.getLastWriteTime())
            {
               mostRecentIndex = indexFile;
            }
         }
      }
      if (!mostRecentIndex.isEmpty())
      {
         return resolvedXRefIndex(mostRecentIndex, srcPath, filename);
      }
      else
      {
         return json::Array();
      }
   }
   else
   {
      return json::Array();
   }
}

json::Array readProjectXRefIndexV1(const FilePath& projectDir, const FilePath& srcFile)
{
   std::string projRelative = srcFile.getRelativePath(projectDir);
   FilePath indexPath = quartoCrossrefDirV1(projectDir).completeChildPath(projRelative);
   return readProjectXRefIndex(indexPath, srcFile, projRelative);

}

bool projectXRefIndexFilter(const FilePath& projectDir,
                            const FilePath& crossrefDir,
                            const FileInfo& fileInfo)
{
   if (fileInfo.isDirectory())
   {
      // see if this corresponds to an actual source file
      std::string relativePath = FilePath(fileInfo.absolutePath()).getRelativePath(crossrefDir);
      FilePath srcFilePath = projectDir.completeChildPath(relativePath);
      return srcFilePath.exists();
   }
   else
   {
      return false;
   }
}

json::Array readAllProjectXRefIndexesV1(const core::FilePath& projectDir)
{
   FilePath crossrefDir = quartoCrossrefDirV1(projectDir);
   if (!crossrefDir.exists())
      return json::Array();

   core::system::FileScannerOptions options;
   options.recursive = true;
   options.filter = boost::bind(projectXRefIndexFilter, projectDir, crossrefDir, _1);

   // scan for directories
   tree<FileInfo> indexFiles;
   Error error = scanFiles(FileInfo(crossrefDir), options, &indexFiles);
   if (error)
   {
      LOG_ERROR(error);
      return json::Array();
   }

   // now read the indexes
   json::Array projectXRefs;
   for (auto indexFile : indexFiles)
   {
      FilePath indexFilePath(indexFile.absolutePath());
      std::string projRelative = indexFilePath.getRelativePath(crossrefDir);
      json::Array xrefs = readProjectXRefIndex(FilePath(indexFile.absolutePath()),
                                               projectDir.completeChildPath(projRelative),
                                               projRelative);
      std::copy(xrefs.begin(), xrefs.end(), std::back_inserter(projectXRefs));
   }

   return projectXRefs;
}


bool useXRefIndexV2()
{
   QuartoConfig config = quarto::quartoConfig();
   return Version(config.version) >= Version("1.1.62");
}

std::map<std::string,FilePath> readProjectXRrefMainIndex(const FilePath& projectDir)
{
   std::map<std::string,FilePath>  mainIndex;
   FilePath xrefDir = quartoCrossrefDirV2(projectDir);
   if (xrefDir.exists())
   {
      FilePath mainIndexFile = xrefDir.completeChildPath("INDEX");
      if (mainIndexFile.exists())
      {
         std::string mainIndexSrc;
         Error error = core::readStringFromFile(mainIndexFile, &mainIndexSrc);
         if (error)
         {
            LOG_ERROR(error);
            return mainIndex;
         }
         json::Object mainIndexJson;
         error = mainIndexJson.parse(mainIndexSrc);
         if (error)
         {
           LOG_ERROR(error);
           return mainIndex;
         }
         // iterate over input files
         for (auto member : mainIndexJson)
         {
            // ensure the input maps to an existing source file
            std::string input = member.getName();
            FilePath inputFilePath = projectDir.completeChildPath(input);
            if (inputFilePath.exists())
            {
               // pick the most recently written output
               for (auto outputMember : member.getValue().getObject())
               {
                  FilePath jsonPath = xrefDir.completeChildPath(outputMember.getValue().getString());
                  if (jsonPath.exists())
                  {
                     // if there is already an entry, compare the file last write time
                     auto it = mainIndex.find(input);
                     if (it != mainIndex.end())
                     {
                        if (jsonPath.getLastWriteTime() > it->second.getLastWriteTime())
                        {
                           it->second = jsonPath;
                        }
                     }
                     // if there is no entry just use this output
                     else
                     {
                        mainIndex[input] = jsonPath;
                     }
                  }

               }
            }
         }
      }
   }

   return mainIndex;
}

json::Array readProjectXRefIndexV2(const FilePath& projectDir, const FilePath& srcFile)
{
   auto mainIndex = readProjectXRrefMainIndex(projectDir);
   std::string projRelative = srcFile.getRelativePath(projectDir);
   boost::algorithm::replace_all(projRelative, "\\", "/");
   auto it = mainIndex.find(projRelative);
   if (it != mainIndex.end())
   {
      return resolvedXRefIndex(it->second, srcFile, projRelative);
   }
   else
   {
      return resolvedXRefIndex(FilePath(), srcFile, projRelative);
   }
}

json::Array readAllProjectXRefIndexesV2(const core::FilePath& projectDir)
{
   json::Array projectXRefs;
   auto mainIndex = readProjectXRrefMainIndex(projectDir);
   for (auto member : mainIndex)
   {
      std::string projRelative = member.first;
      FilePath indexPath = member.second;
      json::Array xrefs = resolvedXRefIndex(indexPath,
                                            projectDir.completeChildPath(projRelative),
                                            projRelative);
      std::copy(xrefs.begin(), xrefs.end(), std::back_inserter(projectXRefs));
   }
   return projectXRefs;
}


json::Array readProjectXRefIndex(const FilePath& projectDir, const FilePath& srcFile)
{
   if (useXRefIndexV2())
      return readProjectXRefIndexV2(projectDir, srcFile);
   else
      return readProjectXRefIndexV1(projectDir, srcFile);
}

json::Array readAllProjectXRefIndexes(const core::FilePath& projectDir)
{
   if (useXRefIndexV2())
      return readAllProjectXRefIndexesV2(projectDir);
   else
      return readAllProjectXRefIndexesV1(projectDir);
}

} // anonymous namespace

namespace modules {
namespace quarto {
namespace xrefs {

namespace {


Error xrefIndexForFile(const FilePath& filePath, json::Object& indexJson)
{
   indexJson[kRefs] = json::Array();

   // is this file in a project and is it a book project?
   FilePath projectDir;
   bool isBook = false;
   FilePath projectConfig = quartoProjectConfigFile(filePath);
   if (!projectConfig.isEmpty())
   {
      // set base dir
      projectDir = projectConfig.getParent();
      indexJson[kBaseDir] = createAliasedPath(projectDir);

      // check whether this is a booo short circuit for this being in the current project
      // (since we already have the config)
      if (isFileInSessionQuartoProject(filePath))
      {
         isBook = quartoConfig().project_type == kQuartoProjectBook;
      }
      else
      {
         std::string type;
         readQuartoProjectConfig(projectConfig, &type);
         isBook = type == kQuartoProjectBook;
      }

      // books get the entire index, non-books get just the file
      if (isBook)
      {
         indexJson[kRefs] = readAllProjectXRefIndexes(projectDir);
      }
      else
      {
         indexJson[kRefs] = readProjectXRefIndex(projectDir, filePath);
      }
   }
   else
   {
      // basedir is this file's parent dir
      indexJson[kBaseDir] = createAliasedPath(filePath.getParent());

      // get storage for this file
      FilePath indexPath;
      Error error = perFilePathStorage(kQuartoCrossrefScope, filePath, false, &indexPath);
      if (error)
      {
         LOG_ERROR(error);
         return error;
      }

      indexJson[kRefs] = resolvedXRefIndex(indexPath, filePath, filePath.getFilename());

   }
   return Success();
}


Error quartoXRefIndexForFile(const json::JsonRpcRequest& request,
                          json::JsonRpcResponse* pResponse)
{
   // read params
   std::string file;
   Error error = json::readParams(request.params, &file);
   if (error)
      return error;

   // resolve path
   FilePath filePath = resolveAliasedPath(file);

   // read index
   json::Object indexJson;
   error = xrefIndexForFile(filePath, indexJson);
   if (error)
      return error;

   // return success
   pResponse->setResult(indexJson);
   return Success();
}

Error quartoXRefForId(const json::JsonRpcRequest& request,
                      json::JsonRpcResponse* pResponse)
{
   // read params
   std::string file, id;
   Error error = json::readParams(request.params, &file, &id);
   if (error)
      return error;

   // resolve path
   FilePath filePath = resolveAliasedPath(file);

   // read index
   json::Object indexJson;
   error = xrefIndexForFile(filePath, indexJson);
   if (error)
      return error;

   // search it the id
   const json::Array& xrefs = indexJson[kRefs].getArray();
   auto it = std::find_if(xrefs.begin(), xrefs.end(), [&id](const json::Value& xref) {
      json::Object xrefJson = xref.getObject();
      std::string xrefId = xrefJson[kType].getString() + "-" +
                           xrefJson[kId].getString() +
                           xrefJson[kSuffix].getString();
      return xrefId == id;
   });
   if (it != xrefs.end())
   {
      json::Array xrefArray;
      xrefArray.push_back(*it);
      indexJson[kRefs] = xrefArray;
   }
   else
   {
      indexJson[kRefs] = json::Array();
   }

   pResponse->setResult(indexJson);

   return Success();
}

} // anonymous namespace

Error initialize()
{
   // register rpc functions
   ExecBlock initBlock;
   initBlock.addFunctions()
     (boost::bind(registerRpcMethod, "quarto_xref_index_for_file", quartoXRefIndexForFile))
     (boost::bind(registerRpcMethod, "quarto_xref_for_id", quartoXRefForId))
   ;
   return initBlock.execute();
}

} // namespace xrefs
} // namespace quarto
} // namespace modules

namespace quarto {

core::json::Value quartoXRefIndex()
{
   QuartoConfig config = quarto::quartoConfig();
   if (config.is_project)
   {
      json::Object indexJson;
      indexJson[kBaseDir] = config.project_dir;
      indexJson[kRefs] =  readAllProjectXRefIndexes(
         module_context::resolveAliasedPath(config.project_dir)
      );
      json::Value resultValue = indexJson;
      return resultValue;
   }
   else
   {
      return json::Value();
   }
}

} // namespace quarto

} // namespace session
} // namespace rstudio
