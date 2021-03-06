package org.gokb

import org.gokb.cred.*;
import org.codehaus.groovy.grails.plugins.orm.auditable.AuditLogEvent

class FwkController {

  def springSecurityService

  def history() { 
    log.debug("FwkController::history...");
    def result = [:]
    result.user = User.get(springSecurityService.principal.id)
    def oid_components = params.id.split(':');
    def qry_params = [oid_components[0],oid_components[1]];

    result.max = params.max ?: 20;
    result.offset = params.offset ?: 0;

    result.historyLines = AuditLogEvent.executeQuery("select e from AuditLogEvent as e where className=? and persistedObjectId=? order by id desc", qry_params, [max:result.max, offset:result.offset]);
    result.historyLinesTotal = AuditLogEvent.executeQuery("select count(e.id) from AuditLogEvent as e where className=? and persistedObjectId=?",qry_params)[0];

    result
  }

  def notes() { 
    log.debug("FwkController::notes...");
    def result = [:]
    result.user = User.get(springSecurityService.principal.id)
    // result.owner = 
    def oid_components = params.id.split(':');
    def qry_params = [oid_components[0],Long.parseLong(oid_components[1])];
    result.ownerClass = oid_components[0]
    result.ownerId = oid_components[1]

    result.max = params.max ?: 20;
    result.offset = params.offset ?: 0;

    result.noteLines = Note.executeQuery("select n from Note as n where ownerClass=? and ownerId=? order by id desc", qry_params, [max:result.max, offset:result.offset]);
    result.noteLinesTotal = AuditLogEvent.executeQuery("select count(n.id) from Note as n where ownerClass=? and ownerId=?",qry_params)[0];

    result
  }

  def attachments() { 
    log.debug("FwkController::attachments...");
  }

  def resolveOID2(oid) {
    def oid_components = oid.split(':');
    def result = null;
    def domain_class=null;
    domain_class = grailsApplication.getArtefact('Domain',oid_components[0])
    if ( domain_class ) {
      if ( oid_components[1]=='__new__' ) {
        result = domain_class.getClazz().refdataCreate(oid_components)
        log.debug("Result of create ${oid} is ${result}");
      }
      else {
        result = domain_class.getClazz().get(oid_components[1])
      }
    }
    else {
      log.error("resolve OID failed to identify a domain class. Input was ${oid_components}");
    }
    result
  }

}
