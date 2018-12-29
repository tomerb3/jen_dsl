#!/usr/bin/env groovy

// ver 32
// need to check if hook is already there or not  ~/data/ text file 
// need to add better slack for witness stage
// need to tell what stage is failed on 



def config = [
    components: [         
          [ name: "itc-test1", kind: "service",repo: null, ci: [] ], 
    //    [ name: "itc-ref-calculator-service", kind: "service",repo: null, ci: [pr: "code2", master:"code2"] ],
    //    [ name: "itc-auth-service", kind: "service",repo: null, ci: [pr: "code2", master:"code2"] ],
    //    [ name: "itc-registry-service", kind: "service",repo: null, ci: [pr: "code2", master:"code2"] ],
    //    [ name: "itc-activity-service", kind: "service",repo: null, ci: [pr: "code2", master:"code2"] ],
    //    [ name: "itc-workbench-service", kind: "service",repo: null, ci: [pr: "code2", master:"code2"] ],
    //    [ name: "itc-portal-application", kind: "application",repo: null, ci: [pr: "code2", master:"code2"] ],
    //    [ name: "itc-backoffice-application", kind: "application",repo: null, ci: [pr: "code2", master:"code2"] ],
    //    [ name: "itc-search-application", kind: "application",repo: null, ci: [pr: "code2", master:"code2"] ],
    //    [ name: "itc-login-application", kind: "application",repo: null, ci: [pr: "code2", master:"code2"] ],
    //    [ name: "itc-player-application", kind: "application",repo: null, ci: [pr: "code2", master:"code2"] ],
    //    [ name: "itc-build-tools", kind: "tools", repo: null, blockOn: [ "itc-build-tools" ] ,ci:[] ],
    //    [ name: "itc-deployment-tools", kind: "tools",repo: null  ,ci:[]],
    //    [ name: "itc-db-tools", kind: "tools",repo: null  ,ci:[]],
    //    [ name: "itc-nodejs-service-library", kind: "npm", repo: null  ,ci:[]],
    //    [ name: "itc-nodejs-security-library", kind: "npm", repo: null ,ci:[] ], 
    //    [ name: "itc-activity-ingest-stack", kind: "stack",repo: null  ,ci:[]],
    //    [ name: "itc-services-stack", kind: "stack",repo: null  ,ci:[]],
    //    [ name: "itc-agent-simulation-diagnostics", kind: "diagnostics",repo: null  ,ci:[]],
    //    [ name: "itc-diagnostic-tools", kind: "tools",repo: null  ,ci:[]],
    //    [ name: "itc-ui-library", kind: "npm",repo: null  ,ci:[]],
    //    [ name: "terraform", kind: "rpm",repo: null ,ci:[] ],
    //    [ name: "itc-example-stack", kind: "stack",repo: null ,ci:[] ],
    //    [ name: "itc-security-tools", kind: "tools",repo: null ,ci:[]],   
    //    [ name: "itc-environment-stack", kind: "stack",repo: null ,ci:[] ],
    ],
    environments: [
         [ name: "code9", api: [ host: "code2-api01" ], witness: [ host: "code2-witness01" ] ],
         
    ],
    source: [
        git: [
            defaultBase: "git@bitbucket.org:observeit/",
            credentials: "builder"
        ]
    ],
    jenkins: [
        jobPrefix: "seeded.",
        jobSuffix: ".pipeline",
        rootJob: "itc-root",
        triggerJob: "pipeline-trigger",
        rpmPublishJob: "rpm-publish",
        envUpdateJob: "env-update",
        diagnosticsRunJob: "diagnostics-run"
    ],
    tools: [
        path: "${JENKINS_HOME}/workspaces/itc-build-tools"
    ],
    notifications: {
        defaultRecipientList: "alex.kremer@observeit.com"
    }
 ]
 job(config.jenkins.jobPrefix + config.jenkins.rootJob + '.parallel') {
    label('docker && linux')

    publishers {
        config.components.each { component ->
            downstream(config.jenkins.jobPrefix + component['name'] + config.jenkins.jobSuffix)
        }
    }
}


allPipelinesScript ="""
  node {

    currentBuild.result = 'SUCCESS'

    try {
 """
 config.components.each { component ->

    allPipelinesScript += """
        stage('${component.name}') {
            build job: '${config.jenkins.jobPrefix + component['name'] + config.jenkins.jobSuffix}'
        }
    """
}

allPipelinesScript += """
    } catch (err) {

        currentBuild.result = "FAILURE"

        throw err

    } finally {

        slackSend message: "\${env.JOB_NAME} - #\${env.BUILD_NUMBER} \${currentBuild.result} after \${currentBuild.durationString.replace(' and counting', '')} (<\${env.BUILD_URL}|Open>)",
            color: ((currentBuild.result == "FAILURE")?"danger":"good")

    }
 }
 """
 pipelineJob(config.jenkins.jobPrefix + config.jenkins.rootJob + '.pipeline') {
    definition {
        cps {
            sandbox()
            script(allPipelinesScript)
        }
    }

}
job(config.jenkins.jobPrefix + config.jenkins.rpmPublishJob) {
    label('docker && linux')

    parameters {
        stringParam('SOURCE_WORKSPACE', '', 'Which workspace RPM artifacts to publish')
        stringParam('TARGET_REPO', '', 'Target YUM repo path')
    }

    steps {
        shell('''
            echo "Running ${JOB_NAME}"
            env | sort
            cd ${SOURCE_WORKSPACE} && \${JENKINS_HOME}/workspace/itc-build-tools/src/scripts/it-build-ctl publish-rpms
        '''.stripIndent())
    }
}
// ==================================================================================


// ===============================  Diagnostics jobs  ===============================
def scriptString = ""

config.environments.each { environment ->
  def buildJobDiag = config.jenkins.jobPrefix + "env." + environment.name + ".diagnostics";
  pipelineJob(buildJobDiag) {
        def workspacePath = "${JENKINS_HOME}" + "/workspace"
        description("Diagnostics ${name}")
       logRotator(9, 9, 9, 4)
        parameters {
            stringParam('TAGNEW', 'latest', ' ')
            stringParam('REPONAME', 'none', ' ')
            stringParam('USERNAME', 'none', ' ')
        }
       properties { 
               disableConcurrentBuilds() 
              } 
        definition {
            cps {
                sandbox()       
 scriptString = """
    import net.sf.json.JSONArray;
    import net.sf.json.JSONObject;
    def reponame =''
    def stage_name = 'start_job'
    def username = ''
    node{
    

      currentBuild.displayName = "#\${BUILD_ID} \${REPONAME}" 

      if (TAGNEW == "latest") 
      {
        currentBuild.description = "Tag: MASTER by \${USERNAME}"
      } 
      else
      {
        currentBuild.description = "Tag: \${TAGNEW} by \${USERNAME}"  
      }
      wrap([\$class: 'BuildUser'])
                         { 
                             if (env.BUILD_USER)
                              {
								username = "\${BUILD_USER}"
                              } else
                              {
                                 username = "\${USERNAME}"                
                              }
                         }


    label('docker && linux')
   try {
     stage('API'){  stage_name = 'api'
      sh "ssh ${environment.api.host} \\"cd itc-simple-composition && ./update \${TAGNEW} \${REPONAME}  \\"  > /tmp/${environment.witness.host}_${stage_name} 2>&1 && cat /tmp/${environment.witness.host}_${stage_name} "
     }

     stage('Witness'){  stage_name = 'witness' 
       sh "sleep 20 && ssh ${environment.witness.host} \\"sudo yum update -y itc-diagnostic-tools-ctl && cd itc-simple-composition && /opt/it/bin/it-diagnostics-ctl init --ecr-login && /opt/it/bin/it-diagnostics-ctl run-modules --pull --debug --tag=\${TAGNEW} --repo=\${REPONAME}\\" |grep ERROR -B 10 > /tmp/${environment.witness.host}_${stage_name} 2>&1 && cat /tmp/${environment.witness.host}_${stage_name}  "
       sh "asdasd"
     }
   
      stage('Docker-Logs'){ stage_name = 'docker_logs'
       sh 'ssh ${environment.api.host} "cd && cd itc-simple-composition && docker-compose logs"'
      }
   
     } catch (err) {
                currentBuild.result = "FAILURE"
                reponame = 'test'
                 slack_notify(currentBuild.result,\${TAGNEW}, username, stage_name, reponame)
                throw err
        }

 }

 def slack_notify(String buildStatus,String tagname, String username, String stage_name, String reponame)
  {
       JSONArray attachments = new JSONArray();
       JSONObject attachment = new JSONObject();
       attachment.put('text','Fail Diagnostic phase');
       attachment.put('color','#36a64f');
       attachment.put('pretext','Diagnostic');
       attachment.put('author_name','Diagnostic fail log inspect:');
       attachment.put('author_link','http://flickr.com/bobby/');
       attachment.put('author_icon','http://flickr.com/icons/bobby.jpg');
       attachment.put('title',reponame);
       attachment.put('title_link','https://api.slack.com/');
       text_log = readFile '/tmp/${environment.witness.host}_${stage_name}'
       attachment.put('text',text_log);
       attachment.put('footer',"tag: ${tagname} , user: ${username} stage: ${stage_name}");
       attachment.put('footer_icon','https://emojis.slackmojis.com/emojis/images/1545362301/5101/smoothprim.gif?1545362301');
       attachment.put('color','#439FE0');
       attachments.add(attachment);
       slackSend(color: '#00FF00', channel: 'debug', attachments: attachments.toString())   
  }
 """        
    script(scriptString)
    scriptString = ""
            }
         }
     } 
   }
// ===============================  Diagnostics jobs  ===============================

 

// =================================== MASTER/PR ===================================
config.components.each { component ->
      scriptString = "";
    def name = component['name']
    def repo = component['repo'] ?: config.source.git.defaultBase + name;
    def buildJob = config.jenkins.jobPrefix + name + config.jenkins.jobSuffix;
    def scriptsectionStart =
                """
                node('linux') {
                    def username = ''
                    def diag = ''
                    def taginfo = ''
                    currentBuild.result = 'SUCCESS'
                    try {
                        def ciEnv = "${component.ci?:''}"                 
                       
                        wrap([\$class: 'BuildUser'])
                         { 
                             if (env.BUILD_USER)
                              {
                                currentBuild.displayName = "#\${BUILD_ID} \${BUILD_USER}"
								username = "\${BUILD_USER}"
                              } else
                              {
                                 currentBuild.displayName = "#\${BUILD_ID} Push \${BRANCH_NAME_actor_display_name}"
                                 username = "Push \${BRANCH_NAME_actor_display_name}"                            
                              }
                         }
                      
                             if (ciEnv) {
                                 if (env.BRANCH_NAME_pullrequest_id) 
                                   {
                                        diag= "${component.ci.pr?:''}"
                                        taginfo = "PR \${BRANCH_NAME_pullrequest_source_branch_name} ID \${BRANCH_NAME_pullrequest_id}"
                                        currentBuild.description = "\${taginfo} in \${diag}"  
                                   } else
                                   {
                                        taginfo = "MASTER"
                                        diag= "${component.ci.master?:''}"
                                        currentBuild.description = "\${taginfo} in \${diag}"
                                   }

                              }else 
                              {
                                    diag="Skip"
                                    if (env.BRANCH_NAME_pullrequest_id)
                                     {
                                            taginfo = "PR \${BRANCH_NAME_pullrequest_source_branch_name} ID \${BRANCH_NAME_pullrequest_id}"
                                            currentBuild.description = "PR \${BRANCH_NAME_pullrequest_source_branch_name} ID \${BRANCH_NAME_pullrequest_id}"  
                                     } else
                                       {
                                            taginfo = "MASTER"
                                            currentBuild.description = "\${taginfo}"
                                      }
                               }
    
                        stage('Prepare') {
                        slackSend message: "\${env.JOB_NAME} - #\${env.BUILD_NUMBER} Started... (<\${env.BUILD_URL}|Open>)"
                        checkout([
                            \$class: 'GitSCM',
                            branches: [[name: "*/\${BRANCH_NAME_pullrequest_source_branch_name}"]],
                            doGenerateSubmoduleConfigurations: false,
                            extensions: [],
                            submoduleCfg: [],
                            userRemoteConfigs: [[
                                credentialsId: '5a707667-4eed-4f75-89c2-125f74d41057',
                                url: 'git@bitbucket.org:observeit/${name}.git'
                            ]]
                        ])
                
                 stage("Git Checkout") 
                 {

                            if ("\${BRANCH_NAME_pullrequest_source_branch_name}" != 'master') {
                                sh 'env |sort |grep "^BRANCH_NAME_pullrequest"'
                                sh "git checkout master"
                                sh "git pull origin master"
                                sh "git checkout master"
                                sh "git reset --hard HEAD"
                                sh "git clean -fdx"
                                sh "git checkout \${BRANCH_NAME_pullrequest_source_branch_name}"
                                sh "git checkout \${REV_VER}"
                                sh "git merge --no-commit origin/\${BRANCH_NAME_pullrequest_source_branch_name}"
                             } else
                             {
                               if ("\${REV_VER}" != 'master') 
                                {
                                   sh "git checkout \${REV_VER}"
                                }
                             
                            }
                        }

                   sh '\${JENKINS_HOME}/workspace/itc-build-tools/src/scripts/it-build-ctl init --ecr-login'

                }
                """
    def scriptsectionBuildRPM =
                """

                        stage('Build RPMs') {
                         if ("\${DEBUG}" == 'no'){
                            sh '\${JENKINS_HOME}/workspace/itc-build-tools/src/scripts/it-build-ctl build-rpms'
                         }
                        }

                """
   def scriptsectionSonar =
            """
                    stage('Sonarqube') {
                         if ("\${DEBUG}" == 'no'){
                            sh '\${JENKINS_HOME}/workspace/itc-build-tools/src/scripts/it-build-ctl run-codescan'
                        }
                    }
            """
  def scriptsectionBuildContainer =
            """

                    stage('Build Containers') {
                        if ("\${DEBUG}" == 'no'){
                          sh '\${JENKINS_HOME}/workspace/itc-build-tools/src/scripts/it-build-ctl build-containers'
                        }
                    }
             """
  def scriptsectionPublishContainer =
             """

                    stage('Publish Containers') {
                       if ("\${DEBUG}" == 'no'){
                          sh '\${JENKINS_HOME}/workspace/itc-build-tools/src/scripts/it-build-ctl publish-containers'
                        }
                    }


            """
  def scriptsectionPublishNPM =
            """
                    stage('Publish NPM') {
                        if ("\${DEBUG}" == 'no'){ 
                           sh '\${JENKINS_HOME}/workspace/itc-build-tools/src/scripts/it-build-ctl publish-npm'
                        }
                    }
            """

  def scriptsectionEND =
        """
              

                stage('Cleanup')
                 {
                    echo 'prune and cleanup'
                    sh 'docker system prune --force'
                    echo 'sh ./build/builds/current/servicecleanup'
                    // sh ./build/builds/current/servicecleanup'
                    //sh 'rm -rf dist/builds'
                  }

                stage((ciEnv ? ("Diagnostics" ) : "Skip Diagnostics")) {
                    
        if (ciEnv) 
         {
            if (env.BRANCH_NAME_pullrequest_id)
             {    
                if ("\${DEBUG}" == 'no')  
                 {
                        sh 'git describe --long HEAD |cut -d "-" -f3-9 |cut -c2-8|head -1 > myfile.txt'
                        TAGNEW = readFile 'myfile.txt'
                        build job: "${config.jenkins.jobPrefix}env." + diag + ".diagnostics", parameters: [string(name: 'TAGNEW', value:"pr"+env.BRANCH_NAME_pullrequest_id+"-"+TAGNEW.trim() + ""),string(name:'REPONAME', value: env.BRANCH_NAME_pullrequest_destination_repository_name),string(name:'USERNAME', value: username)]
                 }
              } else 
              {
                if ("\${DEBUG}" == 'no') 
                  {
                        sh 'echo \${JOB_NAME}|cut -d "." -f2> myfile.txt'
                        REPONAME = readFile 'myfile.txt'
                        build job: "${config.jenkins.jobPrefix}env." + diag + ".diagnostics", parameters: [ string(name:'REPONAME', value: REPONAME),string(name:'USERNAME', value: username)]
                  }
              }
          } else 
          {
                  sh 'echo "WARNING: CI Environment Not Defined - Skipping..."'
          }
        }
            } catch (err) {
                currentBuild.result = "FAILURE"
                bitbucketStatusNotify ( buildState: 'FAILED' ) 
                throw err
            } finally {
                if (currentBuild.result == 'SUCCESS') { bitbucketStatusNotify ( buildState: 'SUCCESSFUL' )} 
                println "username \${username}"
                println "diag \${diag}"
                if ("\${SLACK}" == 'yes') 
                  {
                    slackSend message: " \${env.JOB_NAME} - #\${env.BUILD_NUMBER} by \${username} Tag: \${BRANCH_NAME_pullrequest_source_branch_name} Diagnostic: \${diag} \${currentBuild.result} after \${currentBuild.durationString.replace(' and counting', '')} (<\${env.BUILD_URL}|Open>)",
                    channel: ((currentBuild.result == "FAILURE")?"Jenkins-failures":"null"),
                    color: ((currentBuild.result == "FAILURE")?"danger":"good")

                     slackSend message: " \${env.JOB_NAME} - #\${env.BUILD_NUMBER} by \${username} Tag: \${BRANCH_NAME_pullrequest_source_branch_name} Diagnostic: \${diag} \${currentBuild.result} after \${currentBuild.durationString.replace(' and counting', '')} (<\${env.BUILD_URL}|Open>)",
                    channel: "Jenkins-all",
                    color: ((currentBuild.result == "FAILURE")?"danger":"good")
                  }
            }
        }

        """    
   def scriptsectionPublishRPM =
            """
               if ("\${BRANCH_NAME_pullrequest_source_branch_name}" == 'master') 
                 {
                    stage('Publish RPMs') {
                        //sh '\${JENKINS_HOME}/workspace/itc-build-tools/src/scripts/it-build-ctl publish-rpms'
                        //sh 'env | sort'
                         if ("\${DEBUG}" == 'no'){
                           build job: 'seeded.rpm-publish', parameters: [string(name: 'SOURCE_WORKSPACE', value: "\${env.WORKSPACE}"), string(name: 'TARGET_REPO', value: '')]
                         }
                     }
                 }

            """
    scriptString += scriptsectionStart
    scriptString += scriptsectionBuildRPM
    scriptString += scriptsectionPublishRPM
    if (component.kind == "npm") {scriptString += scriptsectionPublishNPM} 
    if (component.kind != "npm") {scriptString += scriptsectionBuildContainer
               scriptString += scriptsectionPublishContainer}
    if (component.kind != "tools") {scriptString += scriptsectionSonar}
    scriptString += scriptsectionEND
    pipelineJob(buildJob) {
        def workspacePath = "${JENKINS_HOME}" + "/workspace"
        description("Master and PR Pipeline for ${name}")
        logRotator(9, 9, 9, 4)
        parameters {
             stringParam('REV_VER', 'master', 'Last 7 Hash git commit for specific CI Build')
             stringParam('BRANCH_NAME_pullrequest_source_branch_name', 'master', 'pull request source branch ')
             choiceParam('SLACK', ['no','yes','no'])          
             choiceParam('DEBUG', ['yes','no','yes'])
        }
       properties { 
           disableConcurrentBuilds() 
         } 
       triggers {
            genericTrigger {
                genericVariables {
                genericVariable {
                  key("BRANCH_NAME")
                  value("\$")
                  expressionType("JSONPath") //Optional, defaults to JSONPath
                  regexpFilter("") //Optional, defaults to empty string
                  defaultValue("") //Optional, defaults to empty string
                }
            }
                token("Token-${component.name}")
                printContributedVariables(false)
                printPostContent(false)
                regexpFilterText("")
                regexpFilterExpression("")
            }
        }
        if (component.blockOn)
            blockOn(component.blockOn) {
            blockLevel('GLOBAL')
            scanQueueFor('ALL')
        }
        definition {
            cps {
                sandbox()
    script(scriptString)
    scriptString = ""
            }
         }
     } 
   }
 //=================================== MASTER/PR ===================================







// use    data   folder  in jenkins home folder and also file repo_list_with_hook.txt
// make sure  DSL job has 2 password strings   cid   and  csecret   for ob serveit oAUTH
def dataFile = new File("/var/lib/jenkins/data/repo_list_with_hook.txt")
def result1 = ''
def hookname = 'push_and_pr'
//======================= Hook Add ==========================
print "hook fix phase if needed"
config.components.each { component ->
  def name = component['name']
  //def repo = component['repo'] ?: config.source.git.defaultBase + name;
  def buildJob = config.jenkins.jobPrefix + name + config.jenkins.jobSuffix;
  def lines = dataFile.readLines()
  lines.each { String line ->
    result1 = "0"
    if ( name == line )
      {
        result1 = "1"
      }
    }
    if ( result1 == "0" ) { println 'need to run add hook script for '+name
       def sout = new StringBuilder(), serr = new StringBuilder()
       def proc = "/var/lib/jenkins/workspace/test-seed-groovy-pipeline/scripts/add-webhook-to-all-itc-repo.sh ${name} ${hookname} ${cid} ${csecret}".execute()
       proc.consumeProcessOutput(sout, serr)
       proc.waitForOrKill(1000)
       println "out> $sout err> $serr"                      
      }
  }
// ==========================================================
