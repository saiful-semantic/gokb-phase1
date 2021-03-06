package com.k_int.grgit

import groovy.util.logging.Log4j

import org.ajoberstar.grgit.Branch
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Tag
import org.ajoberstar.grgit.operation.*
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.lib.NullProgressMonitor
import org.eclipse.jgit.lib.ProgressMonitor

@Log4j
class GitUtils {
  
  /**
   * Try switching to a particular branch.
   * @param git
   * @param branchName
   * @return
   */
  public static String tryGettingBranch (Grgit git, String branchName) {
    
    if (branchName) {
    
      // Grab the branches.
      List<Branch> branches = git.branch.list {
        mode = BranchListOp.Mode.REMOTE
      }
      
      // Search for a matching branch
      Branch the_branch = branches.find { it.getName() == branchName }
      
      if (the_branch) {
        
        // Checkout the branch.
        git.checkout {
          branch = (the_branch.fullName)
          createBranch = true
        }
        
        // The branch isn't configured to track a remote branch.
        if (!the_branch.trackingBranch) {
          
          // Add the tracking here.
          git.branch.change {
            name = git.branch.current.name
            startPoint = "${the_branch.fullName}"
            mode = BranchChangeOp.Mode.TRACK
          }
        }
        
        log.debug ("Found and checked out branch ${git.branch.current}")
      }
    }
    
    // Return the name of the current branch.
    git.branch.current.getName()
  }

  
  /**
   * Look for a tag matching the pattern supplied.
   * @param git
   * @param tagPattern
   * @return the Grgit instance
   */
  public static String tryGettingTag (Grgit git, String tagPattern) {
    
    // Tag name found.
    String found_tag = null
    
    if (tagPattern) {
    
      // Grab the tags.
      List<Tag> tags = git.tag.list()
      
      Tag release = null
      tags.each { Tag t ->
        // Get the current tag.
        if (t.getName() ==~ tagPattern) {
          if (release?.commit?.time < t.commit.time ) {
            release = t
          }
        }
      }
      
      if (release) {
        
        found_tag = release.getName()
        
        // Found the correct tag.
        log.debug ("Found tag ${found_tag}")
        
        // Reset to this tag.
        git.reset {
          mode = ResetOp.Mode.HARD
          commit = release.fullName
        }
      }
    }
    
    found_tag
  }
  
  
  /**
   * Opens a local repo or clones and then opens.
   * @param loc The File representing the folder location of the Git repo.
   * @param uri The URI to clone from if it isn't found.
   * @return
   */
  public static Grgit getOrCreateRepo (File loc, String uri, ProgressMonitor monitor = NullProgressMonitor.INSTANCE) {
    doGetOrCreateRepo (loc, uri, monitor, true)
  }
  
  private static Grgit doGetOrCreateRepo (File loc, String uri, ProgressMonitor monitor, retry) {
    Grgit git
    
    log.debug ("Checking ${loc}...")
    try {
      
      // Try opening the repo.
      git = Grgit.open(loc)
      log.debug ("Found repo!")
      
    } catch (RepositoryNotFoundException e) {
    
      // Clone the repo.
      log.debug ("No repo found. Cloning from ${uri}")
      com.k_int.grgit.MonitoredGit.monitor = monitor
      
      git = com.k_int.grgit.MonitoredGit.cloneMonitored(dir: loc, uri: (uri))
    }
    
    // Now fetch everything from the remote and ensure the local is in sync.
    log.debug ("Fetching all branches.")
    
    git.fetch {
      tagMode = FetchOp.TagMode.ALL
      prune   = true
      refSpecs = ["*:*"]
    }
    
    // Clean to remove all none-tracked changes.
    log.debug ("Cleaning none trackable changes.")
    try {
      git.clean {
        directories = true
        ignore = false
      }
    } catch (JGitInternalException e) {
      // SO: Suppressing this exception here. The reason is that if there are directories
      // that have come from another repository we can get an error thrown as it attempts to
      // remove the directory. Even though the error is reported the delete operation still
      // succeeds. This obviously isn't ideal, but is the only way I could see around it.
      // Rerunning the method after the exception doesn't seem to throw an exception the second
      // time.
      if (e.getCause() instanceof IOException) {
        // Retry.
        git.clean {
          directories = true
          ignore = false
        }
      } else {
        throw e
      }
    }
    
    // Reset the repo now that it has been cleaned.
    log.debug ("Reseting now..")
    git.reset {
      mode = ResetOp.Mode.HARD
    }
    
    try {
      
      // Try pulling latest changes.
      log.debug ("Pull changes now..")
      git.pull()
      
    } catch( Exception e )  {
    
      if (retry) {
        
        
        log.debug ("Error pulling changes. Let's delete the local repo and retry.")
    
        // We have failed (probably due to conflicts in working tree.
        // Retry but remove the directory first.
        FileUtils.deleteDirectory(loc)
        FileUtils.forceMkdir(loc)
        
        // Ensure we pass recursive = false here to prevent a continuous loop.
        git = doGetOrCreateRepo  (loc, uri, monitor, false)
        
      } else {
        throw e
      }
    }
    
    git
  }
  
  /**
   * Pull all changes donw from the upstream repo.
   */
  public static Grgit getChanges( Grgit git ) {
    git.pull()
    git
  }
}
