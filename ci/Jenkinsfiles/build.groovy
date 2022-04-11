/*
* (C) Copyright 2022 Nuxeo (http://nuxeo.com/) and others.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
* Contributors:
*     Thomas Fowley
*/

def abortRunningBuilds() {
  // see https://issues.jenkins.io/browse/JENKINS-43353
  def buildNumber = BUILD_NUMBER as int
  if (buildNumber > 1) {
    milestone(buildNumber - 1)
  }
  milestone(buildNumber)
}
abortRunningBuilds()

repositoryUrl = 'https://github.com/nuxeo/nuxeo-compound-documents'
testEnvironments = [
  'dev',
  'mongodb',
]

void setLabels() {
  echo '''
    ----------------------------------------
    Set Kubernetes resource labels
    ----------------------------------------
  '''
  echo "Set label 'branch: ${BRANCH_NAME}' on pod ${NODE_NAME}"
  sh "kubectl label pods ${NODE_NAME} branch=${BRANCH_NAME}"
  // output pod description
  echo "Describe pod ${NODE_NAME}"
  sh "kubectl describe pod ${NODE_NAME}"
}

String getMavenArgs() {
  def args = '-B -nsu -Dnuxeo.skip.enforcer=true -P-nexus,nexus-private'
  if (!isPullRequest()) {
    args += ' -Prelease'
  }
  return args
}

def isPullRequest() {
  return "${BRANCH_NAME}" =~ /PR-.*/
}

String getPullRequestVersion() {
  return "${BRANCH_NAME}-" + getCurrentVersion()
}

String getCurrentVersion() {
  return readMavenPom().getVersion();
}

String getReleaseVersion() {
  container('ftests') {
    String nuxeoVersion = getCurrentVersion()
    String noSnapshot = nuxeoVersion.replace('-SNAPSHOT', '')
    String version = noSnapshot + '.1' // first version ever
    // find the latest tag if any
    sh """
      git fetch origin 'refs/tags/v${noSnapshot}*:refs/tags/v${noSnapshot}*'
    """
    def tag = sh(returnStdout: true, script: "git tag --sort=taggerdate --list 'v${noSnapshot}*' | tail -1 | tr -d '\n'")
    if (tag) {
      version = sh(returnStdout: true, script: "semver bump patch ${tag} | tr -d '\n'")
    }
    return version
  }
}

void setGitHubBuildStatus(String context, String message, String state) {
  if (env.DRY_RUN != "true") {
    step([
      $class            : 'GitHubCommitStatusSetter',
      reposSource       : [$class: 'ManuallyEnteredRepositorySource', url: repositoryUrl],
      contextSource     : [$class: 'ManuallyEnteredCommitContextSource', context: context],
      statusResultSource: [$class: 'ConditionalStatusResultSource', results: [[$class: 'AnyBuildResult', message: message, state: state]]],
    ])
  }
}

String getVersion() {
  return isPullRequest() ? getPullRequestVersion() : getReleaseVersion()
}

void helmfileSync(namespace, environment) {
  withEnv(["NAMESPACE=${namespace}"]) {
    sh """
      ${HELMFILE_COMMAND} deps
      ${HELMFILE_COMMAND} --environment ${environment} sync
    """
  }
}

void helmfileDestroy(namespace, environment) {
  withEnv(["NAMESPACE=${namespace}"]) {
    sh """
      ${HELMFILE_COMMAND} --environment ${environment} destroy
    """
  }
}

def buildUnitTestStage(env) {
  def isDev = env == 'dev'
  def testNamespace = "${TEST_NAMESPACE_PREFIX}-${env}"
  // Helmfile environment
  def environment = "${env}UnitTests"
  def kafkaHost = "${TEST_KAFKA_K8S_OBJECT}.${testNamespace}.${TEST_SERVICE_DOMAIN_SUFFIX}:${TEST_KAFKA_PORT}"
  return {
    stage("Run ${env} unit tests") {
      container(DEFAULT_CONTAINER) {
        // TODO NXP-29512: on a PR, make the build continue even if there is a test error
        // on other environments than the dev one
        // to remove when all test environments will be mandatory
        catchError(buildResult: isPullRequest() && "dev" != env ? 'SUCCESS' : 'FAILURE', stageResult: 'FAILURE', catchInterruptions: false) {
          script {
            setGitHubBuildStatus("utests/${env}", "Unit tests - ${env} environment", 'PENDING')
            if(!isDev){
              sh "kubectl create namespace ${testNamespace}"
            }
            try {
              echo """
              ----------------------------------------
              Run ${env} unit tests
              ----------------------------------------"""

              if (isDev) {
                // empty file required by the read-project-properties goal of the properties-maven-plugin with the
                // customEnvironment profile
                sh "touch ${HOME}/nuxeo-test-${env}.properties"
              } else {
                echo "${env} unit tests: install external services"
                helmfileSync("${testNamespace}", "${environment}")
                // prepare test framework system properties
                sh """
                  cat ci/mvn/nuxeo-test-${env}.properties \
                    ci/mvn/nuxeo-test-elasticsearch.properties \
                    > ci/mvn/nuxeo-test-${env}.properties~gen
                    NAMESPACE=${testNamespace} \
                    DOMAIN=${TEST_SERVICE_DOMAIN_SUFFIX} \
                    envsubst < ci/mvn/nuxeo-test-${env}.properties~gen > ${HOME}/nuxeo-test-${env}.properties
                """
              }
              // run unit tests for the given environment (see the customEnvironment profile in pom.xml):
              //   - in an alternative build directory
              //   - loading some test framework system properties
              def testCore = env == 'mongodb' ? 'mongodb' : 'vcs'
              def kafkaOptions = isDev ? '' : "-Pkafka -Dkafka.bootstrap.servers=${kafkaHost}"
              def mvnCommand = """                 
                mvn ${MAVEN_ARGS} \
                  -Dcustom.environment=${env} \
                  -Dcustom.environment.log.dir=target-${env} \
                  -Dnuxeo.test.core=${testCore} \
                  ${kafkaOptions} \
                  test
              """
              echo "${env} unit tests: run Maven"
              sh "${mvnCommand}"

              setGitHubBuildStatus("utests/${env}", "Unit tests - ${env} environment", 'SUCCESS')
            } catch (err) {
              echo "${env} unit tests error: ${err}"
              setGitHubBuildStatus("utests/${env}", "Unit tests - ${env} environment", 'FAILURE')
              throw err
            } finally {
              try {
                junit allowEmptyResults: true, testResults: "**/target-${env}/surefire-reports/*.xml"
                if (!isDev) {
                  archiveKafkaLogs(testNamespace, "${env}-kafka.log")
                }
              } finally {
                echo "${env} unit tests: clean up test namespace"
                try {
                  if(!isDev){
                    helmfileDestroy("${testNamespace}", "${environment}")
                  }
                } finally {
                  // clean up test namespace
                  sh "kubectl delete namespace ${testNamespace} --ignore-not-found=true"
                }
              }
            }
          }
        }
      }
    }
  }
}

def buildFrontendTestStage() {
  return {
    stage('Run frontend unit tests') {
      container(DEFAULT_CONTAINER) {
        script {
          try {
            setGitHubBuildStatus('utests/frontend', 'Unit tests - frontend', 'PENDING')
            sh 'cd nuxeo-compound-documents-web && npm run test'
            setGitHubBuildStatus('utests/frontend', 'Unit tests - frontend', 'SUCCESS')
          } catch(err) {
            currentBuild.result = "FAILURE"
            setGitHubBuildStatus('utests/frontend', 'Unit tests - frontend', 'FAILURE')
            throw err
          }
        }
      }
    }
  }
}

String getMavenFailArgs() {
  return (isPullRequest() && pullRequest.labels.contains('failatend')) ? '--fail-at-end' : ' '
}

void runFunctionalTests(String baseDir) {
  try {
    retry(2) {
      sh "mvn ${MAVEN_ARGS} ${MAVEN_FAIL_ARGS} -f ${baseDir}/pom.xml verify"
    }
    findText regexp: ".*ERROR.*", fileSet: "ftests/**/log/server.log"
  } catch(err) {
    echo "${baseDir} functional tests error: ${err}"
    throw err
  } finally {
    try {
      archiveArtifacts allowEmptyArchive: true, artifacts: "${baseDir}/**/target/failsafe-reports/*, ${baseDir}/**/target/**/*.log, ${baseDir}/**/target/*.png, ${baseDir}**/target/cucumber-reports/*.json, ${baseDir}/**/target/*.html, ${baseDir}/**/target/**/distribution.properties, ${baseDir}/**/target/**/configuration.properties"
    } catch (err) {
      echo hudson.Functions.printThrowable(err)
    }
  }
}

String getCurrentNamespace() {
  container('ftests') {
    return sh(returnStdout: true, script: "kubectl get pod ${NODE_NAME} -ojsonpath='{..namespace}'")
  }
}

void archiveKafkaLogs(namespace, logFile) {
  // don't fail if pod doesn't exist
  sh "kubectl logs ${TEST_KAFKA_POD_NAME} --namespace=${namespace} > ${logFile} || true"
  archiveArtifacts allowEmptyArchive: true, artifacts: "${logFile}"
}

pipeline {
  agent {
    // label 'jenkins-nuxeo-package-lts-2021'
    label 'nuxeo-web-ui-ftests'
  }
  options {
    buildDiscarder(logRotator(daysToKeepStr: '60', numToKeepStr: '60', artifactNumToKeepStr: '5'))
  }
  environment {
    // DEFAULT_CONTAINER = 'maven'
    DEFAULT_CONTAINER = 'ftests'
    TEST_KAFKA_K8S_OBJECT = 'kafka'
    TEST_KAFKA_PORT = '9092'
    TEST_KAFKA_POD_NAME = "${TEST_KAFKA_K8S_OBJECT}-0"
    TEST_SERVICE_DOMAIN_SUFFIX = 'svc.cluster.local'
    SLACK_CHANNEL = 'platform-notifs'
    CONNECT_PREPROD_URL = 'https://nos-preprod-connect.nuxeocloud.com/nuxeo'
    MAVEN_ARGS = getMavenArgs()
    MAVEN_FAIL_ARGS = getMavenFailArgs()
    VERSION = getVersion()
    NUXEO_PACKAGE_PATH = "nuxeo-compound-documents-package/target/nuxeo-compound-documents-package-${VERSION}.zip"
    CURRENT_NAMESPACE = getCurrentNamespace()
    TEST_NAMESPACE_PREFIX = "${CURRENT_NAMESPACE}-compound-documents-unit-tests-${BRANCH_NAME}-${BUILD_NUMBER}".toLowerCase()
    HELMFILE_COMMAND = "helmfile --file ci/helm/helmfile.yaml --helm-binary /usr/bin/helm3"
    HOME = '/root'
  }
  stages {
    stage('Set Labels') {
      steps {
        container(DEFAULT_CONTAINER) {
          setLabels()
        }
      }
    }
    stage('Update Version') {
      steps {
        container(DEFAULT_CONTAINER) {
          script {
            echo """
            ----------------------------------------
            Update version
            ----------------------------------------
            New version: ${VERSION}
            """
            sh "mvn ${MAVEN_ARGS} versions:set -DnewVersion=${VERSION} -DgenerateBackupPoms=false"
          }
        }
      }
    }
    stage('Git commit') {
      when {
        allOf {
          not {
            branch 'PR-*'
          }
          not {
            environment name: 'DRY_RUN', value: 'true'
          }
        }
      }
      steps {
        container('ftests') {
          echo """
          ----------------------------------------
          Git commit
          ----------------------------------------
          """
          sh """
            git commit -a -m "Release ${VERSION}"
          """
        }
      }
    }

    stage('Build') {
      steps {
        container(DEFAULT_CONTAINER) {
          setGitHubBuildStatus('maven/build', 'Build', 'PENDING')
          sh "mvn ${MAVEN_ARGS} -V -T4C -DskipTests install"
        }
      }
      post {
        success {
          script {
            if(!isPullRequest()){
              archiveArtifacts allowEmptyArchive: true, artifacts: "${NUXEO_PACKAGE_PATH}"
            }
          }
          setGitHubBuildStatus('maven/build', 'Build', 'SUCCESS')
        }
        unsuccessful {
          setGitHubBuildStatus('maven/build', 'Build', 'FAILURE')
        }
      }
    }
    stage('Linting') {
      steps {
        container(DEFAULT_CONTAINER) {
          script {
            setGitHubBuildStatus('npm/lint', 'Lint', 'PENDING')
            sh 'cd nuxeo-compound-documents-web && npm run lint'
          }
        }
      }
      post {
        success {
          setGitHubBuildStatus('npm/lint', 'Lint', 'SUCCESS')
        }
        unsuccessful {
          setGitHubBuildStatus('npm/lint', 'Lint', 'FAILURE')
        }
      }
    }
    stage('Run unit tests') {
      steps {
        script {
          def stages = [:]
          for (env in testEnvironments) {
            stages["Run ${env} unit tests"] = buildUnitTestStage(env);
          }
          stages["Run frontend unit tests"] = buildFrontendTestStage();
          parallel stages
        }
      }
    }
    stage('Run functional tests') {
      steps {
        setGitHubBuildStatus('ftests/dev', 'Functional tests - frontend', 'PENDING')
        container(DEFAULT_CONTAINER) {
          script {
            try {
              echo """
              ----------------------------------------
              Run Webdriver functional tests
              ----------------------------------------"""
              withCredentials([string(credentialsId: 'instance-clid', variable: 'INSTANCE_CLID')]) {
                sh(
                  script: '''#!/bin/bash +x
                    echo -e "$INSTANCE_CLID" >| /tmp/instance.clid
                  '''
                )
                withEnv(["TEST_CLID_PATH=/tmp/instance.clid"]) {
                  runFunctionalTests('nuxeo-compound-documents-web/ftest')
                  setGitHubBuildStatus('ftests/dev', 'Functional tests - dev environment', 'SUCCESS')
                }
              }
            } catch(err) {
              setGitHubBuildStatus('ftests/dev', 'Functional tests - frontend', 'FAILURE')
              throw err
            }
          }
        }
      }
    }
    stage('Git tag and push') {
      when {
        allOf {
          not {
            branch 'PR-*'
          }
          not {
            environment name: 'DRY_RUN', value: 'true'
          }
        }
      }
      steps {
        container('ftests') {
          echo """
          ----------------------------------------
          Git tag and push
          ----------------------------------------
          """
          sh """
            #!/usr/bin/env bash -xe
            # create the Git credentials
            jx step git credentials
            git config credential.helper store
            # Git tag
            git tag -a v${VERSION} -m "Release ${VERSION}"
            git push origin v${VERSION}
          """
        }
      }
    }
    stage('Deploy Maven artifacts') {
      when {
        allOf {
          not {
            branch 'PR-*'
          }
          not {
            environment name: 'DRY_RUN', value: 'true'
          }
        }
      }
      steps {
        setGitHubBuildStatus('maven/deploy', 'Deploy Maven artifacts', 'PENDING')
        container('ftests') {
          echo """
          ----------------------------------------
          Deploy Maven artifacts
          ----------------------------------------"""
          sh "mvn ${MAVEN_ARGS} -DskipTests deploy"
        }
      }
      post {
        success {
          setGitHubBuildStatus('maven/deploy', 'Deploy Maven artifacts', 'SUCCESS')
        }
        unsuccessful {
          setGitHubBuildStatus('maven/deploy', 'Deploy Maven artifacts', 'FAILURE')
        }
      }
    }
    stage('Deploy Nuxeo Package') {
      when {
        allOf {
          not {
            branch 'PR-*'
          }
          not {
            environment name: 'DRY_RUN', value: 'true'
          }
        }
      }
      steps {
        setGitHubBuildStatus('package/deploy', 'Deploy Nuxeo Package', 'PENDING')
        container('ftests') {
          echo """
          ----------------------------------------
          Upload Nuxeo Package to ${CONNECT_PREPROD_URL}
          ----------------------------------------"""
          withCredentials([usernameColonPassword(credentialsId: 'connect-preprod', variable: 'CONNECT_PASS')]) {
            sh '''
              curl --fail -i -u $CONNECT_PASS -F package=@$NUXEO_PACKAGE_PATH $CONNECT_PREPROD_URL/site/marketplace/upload?batch=true;
            '''
          }
        }
      }
      post {
        success {
          setGitHubBuildStatus('package/deploy', 'Deploy Nuxeo Package', 'SUCCESS')
        }
        unsuccessful {
          setGitHubBuildStatus('package/deploy', 'Deploy Nuxeo Package', 'FAILURE')
        }
      }
    }
  }
  post {
    always {
      script {
        if (!isPullRequest()) {
          // update JIRA issue
          step([$class: 'JiraIssueUpdater', issueSelector: [$class: 'DefaultIssueSelector'], scm: scm])
        }
      }
    }
    success {
      script {
        if (!isPullRequest()) {
          currentBuild.description = "Build ${VERSION}"
          if (env.DRY_RUN != 'true'
            && !hudson.model.Result.SUCCESS.toString().equals(currentBuild.getPreviousBuild()?.getResult())) {
            slackSend(channel: "${SLACK_CHANNEL}", color: 'good', message: "Successfully built nuxeo/nuxeo-compound-documents ${BRANCH_NAME} #${BUILD_NUMBER}: ${BUILD_URL}")
          }
        }
      }
    }
    unsuccessful {
      script {
        if (!isPullRequest()
          && env.DRY_RUN != 'true'
          && ![hudson.model.Result.ABORTED.toString(), hudson.model.Result.NOT_BUILT.toString()].contains(currentBuild.result)) {
          slackSend(channel: "${SLACK_CHANNEL}", color: 'danger', message: "Failed to build nuxeo/nuxeo-compound-documents ${BRANCH_NAME} #${BUILD_NUMBER}: ${BUILD_URL}")
        }
      }
    }
  }
}
