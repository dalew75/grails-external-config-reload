package grails.plugins.reloadconfig

import org.codehaus.groovy.grails.plugins.GrailsPlugin
import grails.util.Environment
import org.codehaus.groovy.grails.commons.cfg.ConfigurationHelper

class ReloadConfigService {
	def pluginManager
	def grailsApplication
	List files
	Date lastTimeChecked
	ReloadableTimer timer
	Boolean automerge

	// Notify plugins list - add external-config-reload automatically	
	private def plugins
	public void setPlugins(def pluginList) {
		this.plugins = pluginList ?: []
		if (!this.plugins.contains("external-config-reload"))
			this.plugins << "external-config-reload"
	}

    def notifyPlugins(List changedFiles=null) {
		log.debug("Notifying ${plugins.size()} plugins${changedFiles?' of changed files '+changedFiles:''}")
		plugins.each { plugin ->
			log.debug("Firing onConfigChange event for plugin ${plugin}")
			pluginManager.getGrailsPlugin(plugin)?.notifyOfEvent(GrailsPlugin.EVENT_ON_CONFIG_CHANGE, changedFiles)
		}
    }
	
	def checkNow() {
		if (log.debugEnabled) 'Check now triggered'
		
		// Check for changes
		List<String> changed = []
		for ( String fileName : files ) {
			if (fileName.contains("file:")) {
				fileName = fileName.substring(fileName.indexOf(':')+1)
			}
			File configFile = new File(fileName).absoluteFile
			if (log.debugEnabled) log.debug "Checking external config file location ${configFile} for changes since ${lastTimeChecked}..."
			if (configFile.exists() && configFile.lastModified()>lastTimeChecked.time) {
				log.info "Detected changed configuration in ${configFile.name}, reloading configuration"
				changed << configFile.name
			}
		}
		
		if ( changed ) {
			if ( automerge ) {
				//grailsApplication.config.merge(new ConfigSlurper(Environment.getCurrent().getName()).parse(configFile.text))
				log.info "Reloading configuration after ${changed.size()} config files changed : ${changed}"
				ConfigurationHelper.initConfig(grailsApplication.config)
			}
			else {
				log.info "${changed.size()} config files changed, but grails.plugins.reloadConfig.automerge property is false, so not reloading: ${changed}"
			}
		}
		else {
			if (log.debugEnabled) 'No config files changed after checking for modifications'
		}
		
		// Reset last checked date
		lastTimeChecked = new Date()
		
		// Notify plugins
		if (changed) {
			notifyPlugins(changed);
		}
	}
	
	def reloadNow() {
		log.info("Manual reload of configuration files triggered")
		files?.each { String fileName ->
			if (fileName.contains("file:"))
				fileName = fileName.substring(fileName.indexOf(':')+1)
			File configFile = new File(fileName).absoluteFile
			if (configFile.exists()) {
				if (automerge) {
					log.debug("Reloading ${configFile} manually")
					grailsApplication.config.merge(new ConfigSlurper(Environment.getCurrent().getName()).parse(configFile.text))
				} else
					log.debug("Not performing auto merge of ${configFile} due to configuration")
			} else {
				log.warn("File ${configFile} does not exist, cannot reload")
			}
		}
		lastTimeChecked = new Date();
		notifyPlugins();
	}
}
