package org.rstudio.studio.client.workbench.views.source.editors.text;

import org.rstudio.core.client.Debug;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;

import com.google.gwt.user.client.Timer;

/**
 * Coordinates diff display between auto-accept tracking and persistent diff systems.
 * Determines which system to use and forwards all activation/update events appropriately.
 */
public class DiffDisplayCoordinator
{
   private final AceEditor editor_;
   private final String filePath_;
   private String normalizedFilePath_;  // Store normalized path for auto-accept manager
   
   private AutoAcceptDiffManager autoAcceptManager_;
   private PersistentDiffGutterManager persistentDiffManager_;
   private AutoAcceptFloatingBar reviewBar_;  // For files without changes but other files have changes
   
   private Timer debounceTimer_;
   private boolean isActive_ = false;
   private boolean changeListenerSetup_ = false;
   
   public DiffDisplayCoordinator(AceEditor editor, String filePath)
   {
      editor_ = editor;
      filePath_ = filePath;
   }
   
   /**
    * Initialize the appropriate diff system based on auto-accept tracking status
    */
   public void initialize()
   {
      checkAndActivate();
   }
   
   /**
    * Refresh/reinitialize the diff display (called on tab activation, VCS revert, etc.)
    */
   public void refresh()
   {
      // Deactivate current system
      deactivate();
      
      // Re-check and activate appropriate system
      checkAndActivate();
   }
   
   /**
    * Check for auto-accept tracking and activate the appropriate manager
    */
   private void checkAndActivate()
   {
      // Normalize the path first to ensure consistent format (expand tilde, etc.)
      RStudioGinjector.INSTANCE.getServer().normalizeFilePath(
         filePath_,
         new ServerRequestCallback<String>()
         {
            @Override
            public void onResponseReceived(String normalizedPath)
            {
               // Store the normalized path for use by managers
               normalizedFilePath_ = normalizedPath;
               
               // Check the Java-based tracker with normalized path
               boolean hasTracking = AutoAcceptTracker.getInstance().isTracking(normalizedPath);
               
               if (hasTracking)
               {
                  activateAutoAcceptManager();
               }
               else
               {
                  activatePersistentDiffManager();
               }
            }
            
            @Override
            public void onError(ServerError error)
            {
               Debug.logError(error);
               // Fallback to persistent diff manager on error
               activatePersistentDiffManager();
            }
         });
   }
   
   /**
    * Activate auto-accept diff manager
    */
   private void activateAutoAcceptManager()
   {
      if (autoAcceptManager_ == null)
      {
         // Use normalized path so AutoAcceptDiffManager can find the tracking info
         autoAcceptManager_ = new AutoAcceptDiffManager(editor_, normalizedFilePath_);
      }
      
      autoAcceptManager_.activate();
      isActive_ = true;
      
      // Set up change listener for live updates
      if (!changeListenerSetup_)
      {
         setupChangeListener();
         changeListenerSetup_ = true;
      }
   }
   
   /**
    * Activate persistent diff manager
    */
   private void activatePersistentDiffManager()
   {      
      if (persistentDiffManager_ == null)
      {
         persistentDiffManager_ = new PersistentDiffGutterManager(editor_, filePath_);
      }
      
      persistentDiffManager_.initialize();
      isActive_ = true;
      
      // Check if there are ANY auto-accept tracked files
      boolean hasTrackedFiles = !AutoAcceptTracker.getInstance().getTrackedFiles().isEmpty();
      
      if (hasTrackedFiles)
      {
         // Create a floating bar for navigation to files with changes
         // Use null as diffManager since we don't need diff operations
         if (reviewBar_ == null)
         {
            reviewBar_ = new AutoAcceptFloatingBar(editor_, null, normalizedFilePath_);
         }
         
         // Set section count to 0 to trigger "Review next file" button
         reviewBar_.updateSectionCount(0);
         reviewBar_.show();
      }
      
      // PersistentDiffGutterManager sets up its own change listener
      // No need to set up our own
   }
   
   /**
    * Deactivate current diff system
    */
   public void deactivate()
   {
      if (autoAcceptManager_ != null)
      {
         autoAcceptManager_.deactivate();
      }
      
      if (persistentDiffManager_ != null)
      {
         persistentDiffManager_.clearAll();
      }
      
      if (reviewBar_ != null)
      {
         reviewBar_.hide();
      }
      
      isActive_ = false;
   }
   
   /**
    * Set up change listener with debouncing for auto-accept manager
    * (PersistentDiffGutterManager handles its own change listening)
    */
   private void setupChangeListener()
   {
      // Create debounce timer (500ms delay)
      debounceTimer_ = new Timer()
      {
         @Override
         public void run()
         {
            if (isActive_ && autoAcceptManager_ != null)
            {
               autoAcceptManager_.refresh();
            }
         }
      };
      
      // Add change listener to editor
      addEditorChangeListener();
   }
   
   /**
    * Add change listener to ACE editor
    */
   private native void addEditorChangeListener() /*-{
      var self = this;
      var editor = this.@org.rstudio.studio.client.workbench.views.source.editors.text.DiffDisplayCoordinator::editor_.@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditor::getWidget()().@org.rstudio.studio.client.workbench.views.source.editors.text.AceEditorWidget::getEditor()();
      
      if (!editor) {
         return;
      }
      
      editor.on("change", function(e) {
         self.@org.rstudio.studio.client.workbench.views.source.editors.text.DiffDisplayCoordinator::onEditorChanged()();
      });
   }-*/;
   
   /**
    * Handle editor change events
    */
   private void onEditorChanged()
   {
      if (!isActive_ || autoAcceptManager_ == null)
      {
         return;
      }
      
      // Cancel previous timer
      if (debounceTimer_ != null)
      {
         debounceTimer_.cancel();
      }
      
      // Start new timer with 500ms delay
      if (debounceTimer_ != null)
      {
         debounceTimer_.schedule(500);
      }
   }
}

