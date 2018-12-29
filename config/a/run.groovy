#!/usr/bin/env groovy
// v16
def config = [
    components: [
       [ name: "itc-test1", kind: "service",repo: null, ci: [pr: "code2pr", master:"code2pr"] ],
       [ name: "itc-ref-calculator-service", kind: "service",repo: null, ci: [pr: "code2pr", master:"code2"] ],
       [ name: "itc-auth-service", kind: "service",repo: null, ci: [pr: "code2pr", master:"code2"] ],
       [ name: "itc-registry-service", kind: "service",repo: null, ci: [pr: "code2pr", master:"code2"] ],
       [ name: "itc-activity-service", kind: "service",repo: null, ci: [pr: "code2pr", master:"code2"] ],
       [ name: "itc-workbench-service", kind: "service",repo: null, ci: [pr: "code2pr", master:"code2"] ],
       [ name: "itc-portal-application", kind: "application",repo: null, ci: [pr: "code2pr", master:"code2"] ],
       [ name: "itc-backoffice-application", kind: "application",repo: null, ci: [pr: "code2pr", master:"code2"] ],
       [ name: "itc-search-application", kind: "application",repo: null, ci: [pr: "code2pr", master:"code2"] ],
       [ name: "itc-login-application", kind: "application",repo: null, ci: [pr: "code2pr", master:"code2"] ],
       [ name: "itc-player-application", kind: "application",repo: null, ci: [pr: "code2pr", master:"code2"] ],
       [ name: "itc-build-tools", kind: "tools", repo: null, blockOn: [ "itc-build-tools" ] ,ci:[] ],
       [ name: "itc-deployment-tools", kind: "tools",repo: null  ,ci:[]],
       [ name: "itc-db-tools", kind: "tools",repo: null  ,ci:[]],
       [ name: "itc-nodejs-service-library", kind: "npm", repo: null  ,ci:[]],
       [ name: "itc-nodejs-security-library", kind: "npm", repo: null ,ci:[] ],
       [ name: "itc-activity-ingest-stack", kind: "stack",repo: null  ,ci:[]],
       [ name: "itc-services-stack", kind: "stack",repo: null  ,ci:[]],
       [ name: "itc-agent-simulation-diagnostics", kind: "diagnostics",repo: null  ,ci:[]],
       [ name: "itc-diagnostic-tools", kind: "tools",repo: null  ,ci:[]],
       [ name: "itc-ui-library", kind: "npm",repo: null  ,ci:[]],
       [ name: "terraform", kind: "rpm",repo: null ,ci:[] ],
       [ name: "itc-example-stack", kind: "stack",repo: null ,ci:[] ],
       [ name: "itc-security-tools", kind: "tools",repo: null ,ci:[]],
       [ name: "itc-environment-stack", kind: "stack",repo: null ,ci:[] ],
    ],
    environments: [
         [ name: "code2", api: [ host: "code2-api01" ], witness: [ host: "code2-witness01" ] ],
         [ name: "code2pr", api: [ host: "code2pr-api01" ], witness: [ host: "code2pr-witness01" ] ],
         [ name: "code4", api: [ host: "code4-api01" ], witness: [ host: "code4-witness01" ] ],
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
        versionsPushJob: "versions-push",
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

job(config.jenkins.jobPrefix + config.jenkins.versionsPushJob) {
    label('docker && linux')

    parameters {
        stringParam('SOURCE_WORKSPACE', '', 'Which workspace container Versions artifacts to push')
        stringParam('TARGET_CONF', '', 'Target configs path in deployment-configs')
    }

    steps {
        shell('''
            echo "Running ${JOB_NAME}"
            env | sort
            # cp ${SOURCE_WORKSPACE}/containers.conf \${JENKINS_HOME}/workspace/itc-deployment-configs/versions/
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

    label('docker && linux')

   stage('API'){

     sh "ssh ${environment.api.host} \\"cd itc-simple-composition && ./update \${TAGNEW} \${REPONAME} \\" "

    }

  stage('Witness'){
        sh "sleep 20 && ssh ${environment.witness.host} \\"sudo yum update -y itc-diagnostic-tools-ctl && cd itc-simple-composition && /opt/it/bin/it-diagnostics-ctl init --ecr-login && /opt/it/bin/it-diagnostics-ctl run-modules --pull --debug --tag=\${TAGNEW} --repo=\${REPONAME}\\"  "
   }

    stage('Docker-Logs'){
       sh 'ssh ${environment.api.host} "cd && cd itc-simple-composition && docker-compose logs"'
    }

 }
 """
    script(scriptString)
    scriptString = ""
            }
         }
     }
   }
// ===============================  Diagnostics jobs  ===============================

 
