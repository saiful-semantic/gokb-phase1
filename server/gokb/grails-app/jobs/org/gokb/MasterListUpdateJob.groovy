package org.gokb


class MasterListUpdateJob {

  def grailsApplication
  
  // Allow only one run at a time.
  def concurrent = false
  
  static triggers = {
    // Cron timer.            
    cron name: 'MasterListUpdateJobTrigger', cronExpression: "0 0 0 * * ?", startDelay:60000
//    cron name: 'MasterListUpdateJobTrigger', cronExpression: "0/15 * * * * ?", startDelay:10000
  }
  
  PackageService packageService

  def execute() {
    log.debug ("Beginning scheduled Master Package update job.")
    if ( grailsApplication.config.masterListGenerationEnabled ) {
      packageService.updateAllMasters(true)
    }
    else {
      log.debug("grailsApplication.config.masterListGenerationEnabled not set - no master list generation");
    }
    log.debug ("Master Package update job completed.")
  }
}
