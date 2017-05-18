/*

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.

 */

import hudson.security.*
import jenkins.model.*
import org.jenkinsci.plugins.*

def env(key, defaultValue=null) {
    def value = java.lang.System.getenv(key)
    if( value == null )
    {
      value = defaultValue
    }
    if( value == null )
    {
        throw new RuntimeException("Unable to locate environment variable: " + key );
    }
    return value;
}

String emailReplyTo = env("JENKINS_EMAIL_REPLY_TO") // "no-reply@example.com"
String emailHost = env("JENKINS_EMAIL_HOST") // "mail.example.com"
String adminPublicUrl = env("JENKINS_PUBLIC_URL", "")
String adminEmailAddress = env("JENKINS_EMAIL_ADMIN_ADDRESS") // "admin@example.com"
String gitConfigName = env("JENKINS_GIT_CONFIG_NAME")
String gitConfigEmail = env("JENKINS_GIT_CONFIG_EMAIL")
String jenkinsUsername = env("JENKINS_USERNAME") // The username to log into jenkins
String jenkinsPassword = env("JENKINS_PASSWORD") // The username to log into jenkins

def jenkins = Jenkins.getInstance()
def config = JenkinsLocationConfiguration.get()

config.setUrl(adminPublicUrl)
config.setAdminAddress(adminEmailAddress)

// Set the executors to a respectable number
jenkins.setNumExecutors(5)
jenkins.save()

// Setup security realm
HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false)
realm.createAccount(jenkinsUsername, jenkinsPassword)
jenkins.setSecurityRealm(realm)
jenkins.save()

// Configure mailer
def mailer = jenkins.getDescriptor("hudson.tasks.Mailer")
mailer.setSmtpAuth("", "")
mailer.setReplyToAddress(emailReplyTo)
mailer.setSmtpHost(emailHost)
mailer.setUseSsl(false)
mailer.setSmtpPort("25")
mailer.setHudsonUrl(adminPublicUrl)
mailer.setCharset("UTF-8")
mailer.save()

def extMailer = jenkins.getDescriptor("hudson.plugins.emailext.ExtendedEmailPublisher")
extMailer.defaultReplyTo = emailReplyTo
extMailer.smtpHost = emailHost
extMailer.useSsl = false
extMailer.precedenceBulk = true
extMailer.smtpPort = "25"
extMailer.listId = "\"Build Notifications\" <" + emailReplyTo.replaceAll(/.*@/,'ci-')  +  ">"
extMailer.charset = "UTF-8"
extMailer.save()

def gitConfig = jenkins.getDescriptor("hudson.plugins.git.GitSCM")

gitConfig.setGlobalConfigName(gitConfigName)
gitConfig.setGlobalConfigEmail(gitConfigEmail)

globalNodeProperties = jenkins.getGlobalNodeProperties()
envVarsNodePropertyList = globalNodeProperties.getAll(hudson.slaves.EnvironmentVariablesNodeProperty.class)

envVars = null

if ( envVarsNodePropertyList == null || envVarsNodePropertyList.size() == 0 ) {
  newEnvVarsNodeProperty = new hudson.slaves.EnvironmentVariablesNodeProperty();
  globalNodeProperties.add(newEnvVarsNodeProperty)
  envVars = newEnvVarsNodeProperty.getEnvVars()
} else {
  envVars = envVarsNodePropertyList.get(0).getEnvVars()
}

envVars.clear()

java.lang.System.getenv().each {
  if ( it.key.startsWith("JENKINS_ENV_") )
  {
    envVars.put(it.key.substring(12), it.value)
  }
}

jenkins.save()

def analysisConfig = jenkins.getDescriptor("hudson.plugins.analysis.core.GlobalSettings")
analysisConfig.setQuietMode(true)
analysisConfig.setFailOnCorrupt(true)

jenkins.save()

// Turn off script security for JobDSL otherwise have to approve every script!
jenkins.model.GlobalConfiguration.all().get(javaposse.jobdsl.plugin.GlobalJobDslSecurityConfiguration.class).useScriptSecurity = false

//Turn off anonymous access to jenkins!
def strategy = new hudson.security.FullControlOnceLoggedInAuthorizationStrategy()
strategy.setAllowAnonymousRead( false )
jenkins.setAuthorizationStrategy( strategy )

jenkins.save()
