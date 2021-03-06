package com.k_int.gokb.module;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletConfig;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections.ExtendedProperties;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.k_int.gokb.module.util.TextUtils;
import com.k_int.gokb.refine.RefineWorkspace;
import com.k_int.gokb.refine.commands.GerericProxiedCommand;
import com.k_int.gokb.refine.functions.GenericMatchRegex;
import com.k_int.gokb.refine.notifications.Notification;
import com.k_int.gokb.refine.notifications.NotificationStack;

import com.google.refine.Jsonizable;
import com.google.refine.ProjectManager;
import com.google.refine.RefineServlet;
import com.google.refine.grel.ControlFunctionRegistry;
import com.google.refine.importing.ImportingManager;
import com.google.refine.io.FileProjectManager;

import edu.mit.simile.butterfly.ButterflyClassLoader;
import edu.mit.simile.butterfly.ButterflyModule;
import edu.mit.simile.butterfly.ButterflyModuleImpl;

public class GOKbModuleImpl extends ButterflyModuleImpl implements Jsonizable {

  final static Logger _logger = LoggerFactory.getLogger("GOKb-ModuleImpl");
  
  private boolean updated = false;

  public static ExtendedProperties properties;

  public static GOKbModuleImpl singleton;

  public boolean isUpdated () {
    return updated;
  }

  private static String userDetails = null;
  private static String version = null;

  public static GOKbService[] getAllServices() {
    return singleton.getServices();
  }

  public static RefineWorkspace[] getAllWorkspaces() {
    return singleton.getWorkspaces();
  }

  public static String getCurrentUserDetails() {
    return userDetails;
  }
  
  static JSONObject currentUser = null;
  public JSONObject getCurrentUser() {
    if (currentUser == null) {
      currentUser = getCurrentService().getCurrentUser();
    }
    
    return currentUser;
  }
  
  public GOKbService getCurrentService() {
    return workspaces[currentWorkspaceId].getService();
  }

  public static File getTemporaryDirectory() {
    return singleton.getTemporaryDir();
  }

  public static String getVersion() {
    if (version == null) {
      version = singleton.getProperties().getString("module.version");
    }

    return version;
  }

  public ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private Set<A_ScheduledUpdates> scheduledObjects = new HashSet<A_ScheduledUpdates> ();
  public void registerScheduledObject (A_ScheduledUpdates obj) {

    _logger.info ("Adding scheduled update object of type " + obj.getClass().getName());
    scheduledObjects.add(obj);
  }

  public static void setCurrentUserDetails(String username, String password) {
    userDetails = Base64.encodeBase64String((username + ":" + password).getBytes());
  }

  private int currentWorkspaceId;

  private GOKbService[] services = null;

  private RefineWorkspace[] workspaces;

  private void addProxiedCommands() {

    _logger.debug("Adding proxied commands from the properties.");

    @SuppressWarnings("unchecked")
    List<String> commands = getProperties().getList("proxyCommands");

    // Register each command from the list.
    for (String command : commands) {
      RefineServlet.registerCommand(this, command, new GerericProxiedCommand(command));
    }
  }

  private void addRegexFunctions() {

    _logger.info("Adding regex functions from the properties.");
    @SuppressWarnings("unchecked")
    List<String> names = getProperties().getList("regex.name");

    @SuppressWarnings("unchecked")
    List<String> patterns = getProperties().getList("regex.pattern");

    @SuppressWarnings("unchecked")
    List<String> offsets = getProperties().getList("regex.skip");

    if (names.size() == patterns.size() && patterns.size() == offsets.size()) {
      // Register each regex function from the properties.
      for (int i=0; i<names.size(); i++) {

        try {
          ControlFunctionRegistry.registerFunction(
              this.getName() + "Match" + names.get(i),
              new GenericMatchRegex (patterns.get(i), Integer.parseInt(offsets.get(i)))
              );
        } catch (Exception e){

          // Log the error
          _logger.error(e.getLocalizedMessage(), e);
        }
      }
    } else {
      _logger.error("regex items need to declare name, pattern and skip");
    }
  }
  @SuppressWarnings("unchecked")
  private void addWorkspaces () throws IOException {

    // Get the file-based project manager.
    File current_ws = ((FileProjectManager)FileProjectManager.singleton).getWorkspaceDir();

    // Load the list from the properties file.
    List<String> apis = properties.getList("api.entry");

    // Include local?
    if (properties.containsKey("apis")) {

      // Add any passed in via command line too. We add these as priorities.
      apis.addAll(0, properties.getList("apis"));
    }

    // Check that the list length is even as each should be in pairs.
    if (apis.size() % 3 != 0) {
      _logger.error("APIs must be defined as \"name,folder_suffix,url\" tuples.");
    } else {

      // The workspaces.
      workspaces = new RefineWorkspace[apis.size() / 3];

      _logger.info("Configuring workspaces...");

      // Go through each group and add the file and URL.
      for (int i=0; i<apis.size(); i+=3) {

        RefineWorkspace ws = new RefineWorkspace (
            apis.get(i),
            apis.get(i+2),
            new File(current_ws.getCanonicalPath() + "_" + apis.get(i+1))
            );

        // Add to the array.
        workspaces[i/3] = ws;

        _logger.info("\tAdded " + ws.getName() + " (" + ws.getService().getURL() + ")" + (!ws.isAvailable() ? " [offline]" : "") );
      }

      // Try and find the first available workspace.
      int wsindex = -1;
      for (int i=0; i < workspaces.length && wsindex < 0; i++) {
        RefineWorkspace ws = workspaces[i];
        if (ws.isAvailable() && ws.getService().isCompatible()) {
          wsindex = i;
        }
      }

      // Set active workspace.
      if (wsindex >= 0) {
        setActiveWorkspace(wsindex);
      } else {
        // Could not connect to any gokb workspace.
        // TODO: We should disable the whole module here if we can.
        // for now we shall throw an exception.

        throw new IOException("Could not connect to any of the defined GOKb services.");
      }
    }
  }

  private void extendModuleProperties() {
    // The module path
    File f = getPath();

    // Load our custom properties.
    File modFile = new File(f,"MOD-INF");
    if (modFile.exists()) {

      // Read in the gokb properties.
      ExtendedProperties p = loadProperties (new File(modFile,"gokb.properties"));

      // Also need to add the regex properties from the regex file.
      p.combine(
          loadProperties (new File(modFile,"regex.properties"))
          );

      // Add module properties to the GOKb properties to allow,
      // command-line passed params to override these values.
      p.combine(getProperties());

      // Set this modules properties.
      setProperties(p);
    }
  }

  public int getCurrentWorkspaceId () {
    return currentWorkspaceId;
  }

  public String getCurrentWorkspaceURL() {
    return getCurrentService().getURL();
  }

  public GOKbService[] getServices() {
    if (services == null) {
      RefineWorkspace[] ws = getAllWorkspaces();
      services = new GOKbService[ws.length];
      for (int i=0; i<ws.length; i++) {
        services[i] = ws[i].getService();
      }
    }

    return services;
  }

  public RefineWorkspace[] getWorkspaces() {
    return workspaces;
  }

  @Override
  public void init(ServletConfig config) throws Exception {

    // Run default init method.
    super.init(config);

    // Perform our extended initialisation...
    extendModuleProperties();
    swapImportControllers();

    // Add our proxied Commands from the config file.
    addProxiedCommands();

    // Add the generic regex functions.
    addRegexFunctions();

    // Set the singleton.
    singleton = this;

    // Set the properties
    properties = singleton.getProperties();

    // Add the workspaces detailed in the properties file.
    addWorkspaces();

    _logger.info("Connection timeout set to " + (double)((double)properties.getInt("timeout") / (double)60000) + " minutes");

    scheduler.scheduleWithFixedDelay(new Runnable() {

      @Override
      public void run () {
        // Execute the scheduled tasks method.
        try {
          scheduledTasks();
        } catch (Throwable e) {
          _logger.error("Error when trying to schedule repeating tasks.", e);
        }
      }

    }, 1, 60, TimeUnit.SECONDS);
  }

  /**
   * This is the entry point that runs all registered scheduled updates.
   * @throws Throwable
   */
  private synchronized void scheduledTasks() throws Throwable {
    _logger.debug("Running scheduled tasks.");

    // Run all scheduled updates.
    for (A_ScheduledUpdates up : scheduledObjects) {
      up.doScheduledUpdates();
    }
    
    // Update the core data once we have finished executing our scheduled tasks.
    updateCoreData();
  }
  
  /**
   * Update the core module data
   * @throws Throwable
   */
  private synchronized void updateCoreData() throws Throwable {
    
    if (!updated) {
    
      // Now we can see if we have any module updates available.
      GOKbService updateService = null;
      
      for (GOKbService s : getAllServices()) {
  
        if (s.hasUpdate()) {
          if (updateService == null) {
            updateService = s;
          } else {
  
            // Compare existing with available.
            String available = s.getAvailableModuleVersion();
            if (TextUtils.versionCompare(updateService.getAvailableModuleVersion(), available) > 0) {
              // Later version here.
              updateService = s;
            }
          }
        }
      }
  
      // If we have an update then we should add a system message.
      if (updateService != null) {
        
        // Let's update...
        final File module_path = getPath().getParentFile().getParentFile();
        
        // Final references so they can be passed across threads.
        final GOKbService s = updateService;
        
        // Because of how jars are locked on the windows platform, we need to update after we release all resources. To do
        // this we add a shutdown hook which will fire as the VM shuts down.
        Runtime.getRuntime().addShutdownHook(new Thread() {
          
          @Override
          public void run() {
            
            // Construct the updater.
            Updater updt = new Updater(s, module_path, (ButterflyClassLoader) _classLoader);

            try {
              // Do the update.
              updt.update();
            } catch (Exception e) {
              // Any error here will mean that the file needs to be manually downloaded.
              _logger.error("An error occured while updating the file. You should re-download the file fully and replace it manually.");
            }
          }
        });
        
        // Now lets raise a notification.
        Notification n = Notification.fromJSON("{"
            + "id:'module-update',"
            + "text:'A system update (version " + updateService.getAvailableModuleVersion() + ") has been donwloaded from the service at \\'"
                + updateService.getURL() + "\\'. Refine has now been shutdown, and you will now need to restart refine and clear your browser caches to continue working.',"
            + "title:'GOKb Update',"
            + "block:true,"
            + "hide:false}"
        );
  
        // Remove the buttons.
        n.getButtons().put("closer", false);
        n.getButtons().put("sticker", false);
  
        // Add to the system notification stack.
        NotificationStack.getSystemStack().add(n);
        
        // Flag that this has run already... No need to keep downloading updates.
        updated = true;
      }
    }
  }

  private ExtendedProperties loadProperties (File propFile) {
    ExtendedProperties p = new ExtendedProperties();
    try {
      if (propFile.exists()) {
        _logger.info("Loading GOKb properties ({})", propFile);
        BufferedInputStream stream = null;
        try {
          Properties ps = new Properties();
          stream = new BufferedInputStream(new FileInputStream(propFile));
          ps.load(stream);
          
          // Go through each property and clean the value first.
          for (Object key : ps.keySet()) {
            String k = (String)key;
            p.addProperty(k, ps.getProperty(k));
          }
        } finally {
          // Close the stream.
          if (stream != null) stream.close();
        }

        // Add module properties to the GOKb properties to allow,
        // command-line passed params to override these values.
        p.combine(getProperties());

        // Set this modules properties.
        setProperties(p);
      }
    } catch (Exception e) {
      _logger.error("Error loading GOKb properties", e);
    }

    return p;
  }

  public void setActiveWorkspace(int workspace_id) {

    // We should now set the new workspace.
    ProjectManager.singleton.dispose();
    ProjectManager.singleton = null;

    // Set the id. 
    currentWorkspaceId = workspace_id;

    // Get the current WS.
    RefineWorkspace newWorkspace = workspaces[currentWorkspaceId];

    // Now we re-init the project manager, with our new directory.
    FileProjectManager.initialize(newWorkspace.getWsFolder());

    _logger.info(
        "Now using workspace '" + newWorkspace.getName() + "' at URL '" +
            newWorkspace.getService().getURL() + "'");

    // Need to clear login information too.
    userDetails = null;
    _logger.info("User login details reset to force login on workspace change.");
  }

  private void swapImportControllers() {
    // Get the core module.
    ButterflyModule coreMod = getModule("core");
    String controllerName = "default-importing-controller";

    // Remove default controller.
    ImportingManager.controllers.remove(
        coreMod.getName() + "/" + controllerName
        );

    // Now register our controller at the default key.
    ImportingManager.registerController(
        coreMod,
        controllerName,
        new GOKbImportingController()
        );

  }

  @Override
  public void write (JSONWriter writer, Properties options)
      throws JSONException {

    // This is the set of data returned by .
    writer.object()
      .key("notification-stacks").object()
        .key("system");
  
          NotificationStack.getSystemStack().write(writer, options);
      
       writer.endObject()
      .key("workspaces").array();

        // Add all the workspaces.
        for (RefineWorkspace w : workspaces) {
          w.write(writer, options);
        }

      writer.endArray()
      .key("current").value(getCurrentWorkspaceId())
      
      // Add the current user.
      .key("current-user").value(getCurrentUser())
    .endObject();
  }

  @Override
  public void destroy () throws Exception {
    this.scheduler.shutdown();
    super.destroy();
  }
}
