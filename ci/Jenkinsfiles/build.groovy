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
*     Kevin Leturc <kevin.leturc@hyland.com>
*/
library identifier: "platform-ci-shared-library@v0.0.25"

Closure buildUnitTestStage(env) {
  return {
    container('maven') {
      nxWithGitHubStatus(context: "utests/backend/${env}") {
        script {
          def testNamespace = "${CURRENT_NAMESPACE}-compound-${BRANCH_NAME}-${BUILD_NUMBER}-${env}".replaceAll('\\.', '-').toLowerCase()
          nxWithHelmfileDeployment(namespace: testNamespace, environment: "${env}UnitTests") {
            try {
              sh """
                cat ci/mvn/nuxeo-test-${env}.properties \
                  ci/mvn/nuxeo-test-elasticsearch.properties \
                | envsubst > /root/nuxeo-test-${env}.properties
              """
              retry(3) {
                sh """
                  mvn -B -nsu -pl :nuxeo-compound-documents \
                    -Dcustom.environment=${env} \
                    -Dcustom.environment.log.dir=target-${env} \
                    -Dnuxeo.test.core=${env == 'mongodb' ? 'mongodb' : 'vcs'} \
                    -Pkafka -Dkafka.bootstrap.servers=kafka.${testNamespace}.svc.cluster.local:9092 \
                    test
                """
              }
            } finally {
              archiveArtifacts artifacts: "**/target-${env}/**/*.log"
              junit allowEmptyResults: true, testResults: "**/target-${env}/surefire-reports/*.xml"
            }
          }
        }
      }
    }
  }
}

pipeline {
  agent {
    label 'jenkins-nuxeo-package-lts-2021'
  }
  options {
    buildDiscarder(logRotator(daysToKeepStr: '60', numToKeepStr: '60', artifactNumToKeepStr: '5'))
    disableConcurrentBuilds(abortPrevious: true)
    githubProjectProperty(projectUrlStr: 'https://github.com/nuxeo/nuxeo-compound-documents')
  }
  environment {
    CURRENT_NAMESPACE = nxK8s.getCurrentNamespace()
    VERSION = nxUtils.getVersion()
    NUXEO_COMPOUND_PACKAGE_PATH = "nuxeo-compound-documents-package/target/nuxeo-compound-documents-package-${VERSION}.zip"
    TEST_NAMESPACE_PREFIX = "${CURRENT_NAMESPACE}-compound-documents-unit-tests-${BRANCH_NAME}-${BUILD_NUMBER}".toLowerCase()
    HELMFILE_COMMAND = "helmfile --file ci/helm/helmfile.yaml --helm-binary /usr/bin/helm3"
    HOME = '/root'
  }
  stages {
    stage('Set Labels') {
      steps {
        container('maven') {
          script {
            nxK8s.setPodLabels()
          }
        }
      }
    }
    stage('Update Version') {
      steps {
        container('maven') {
          script {
            nxMvn.updateVersion()
          }
        }
      }
    }
    stage('Compile') {
      steps {
        container('maven') {
          nxWithGitHubStatus(context: 'compile') {
            echo """
            ----------------------------------------
            Compile
            ----------------------------------------"""
            echo "MAVEN_OPTS=$MAVEN_OPTS"
            sh 'mvn -B -nsu -T4C install -DskipTests'
          }
        }
      }
      post {
        success {
          archiveArtifacts artifacts: '**/target/*.jar, **/target/nuxeo-*-package-*.zip'
          junit testResults: '**/target/surefire-reports/*.xml, **/target/failsafe-reports/*.xml', allowEmptyResults: true
        }
      }
    }
    stage('Run unit tests') {
      steps {
        script {
          def stages = [:]
          // tests disable see NXP-31942
//          stages['Frontend'] = {
//            container('maven') {
//              nxWithGitHubStatus(context: 'utests/frontend') {
//                withCredentials([usernamePassword(credentialsId: 'saucelabs-credentials', passwordVariable: 'SAUCE_ACCESS_KEY', usernameVariable: 'SAUCE_USERNAME')]) {
//                  dir('nuxeo-compound-documents-web') {
//                    sh 'npm run test'
//                  }
//                }
//              }
//            }
//          }
          stages['Backend - dev'] = {
            container('maven') {
              nxWithGitHubStatus(context: 'utests/backend/dev') {
                try {
                  // empty file required by the read-project-properties goal of the properties-maven-plugin with the
                  // customEnvironment profile
                  sh 'touch /root/nuxeo-test-dev.properties'
                  retry(3) {
                    sh 'mvn -B -nsu -pl :nuxeo-compound-documents -Dcustom.environment=dev -Dcustom.environment.log.dir=target-dev test'
                  }
                } finally {
                  archiveArtifacts artifacts: '**/target-dev/**/*.log'
                  junit allowEmptyResults: true, testResults: "**/target-dev/surefire-reports/*.xml"
                }
              }
            }
          }
          stages['Backend - MongoDB'] = buildUnitTestStage('mongodb')
          parallel stages
        }
      }
    }
    stage('Run functional tests') {
      steps {
        container('maven') {
          script {
            nxWithGitHubStatus(context: "ftests/dev") {
              echo """
              ----------------------------------------
              Run Webdriver functional tests
              ----------------------------------------"""
              withCredentials([string(credentialsId: 'instance-clid', variable: 'INSTANCE_CLID')]) {
                sh(script: '''#!/bin/bash +x
                  echo -e "$INSTANCE_CLID" >| /tmp/instance.clid
                ''')
                withEnv(["TEST_CLID_PATH=/tmp/instance.clid"]) {
                  try {
                    sh "mvn -B -nsu -f nuxeo-compound-documents-web/ftest/pom.xml verify"
                  } catch (err) {
                    //Allow ftest to fail
                    echo hudson.Functions.printThrowable(err)
                  }
                  nxUtils.lookupText(regexp: ".*ERROR.*(?=(?:\\n.*)*\\[.*FrameworkLoader\\] Nuxeo Platform is Trying to Shut Down)",
                    fileSet: "**/log/server.log")
                }
              }
            }
          }
        }
      }
      post {
        always {
          archiveArtifacts artifacts: '**/ftest/target/screenshots/**', allowEmptyArchive: true
          cucumber(fileIncludePattern: '**/*.json', jsonReportDirectory: 'nuxeo-compound-documents-web/ftest/target/cucumber-reports/',
              sortingMethod: 'NATURAL')
        }
      }
    }
    stage('Git commit, tag and push') {
      when {
        expression { !nxUtils.isPullRequest() }
      }
      steps {
        container('maven') {
          script {
            echo """
            ----------------------------------------
            Git commit, tag and push
            ----------------------------------------
            """
            nxGit.commitTagPush()
          }
        }
      }
    }
    stage('Deploy Maven artifacts') {
      when {
        expression { !nxUtils.isPullRequest() }
      }
      steps {
        container('maven') {
          nxWithGitHubStatus(context: 'maven/deploy', message: 'Deploy Maven artifacts') {
            script {
              echo """
              ----------------------------------------
              Deploy Maven artifacts
              ----------------------------------------"""
              nxMvn.deploy()
            }
          }
        }
      }
    }
    stage('Deploy Nuxeo package') {
      when {
        expression { !nxUtils.isPullRequest() }
      }
      steps {
        container('maven') {
          nxWithGitHubStatus(context: 'package/deploy', message: 'Deploy Nuxeo packages') {
            script {
              echo """
              ----------------------------------------
              Upload Nuxeo Package to ${CONNECT_PREPROD_SITE_URL}
              ----------------------------------------"""
              nxUtils.postForm(credentialsId: 'connect-preprod', url: "${CONNECT_PREPROD_SITE_URL}marketplace/upload?batch=true",
                  form: ["package=@${NUXEO_COMPOUND_PACKAGE_PATH}"])
            }
          }
        }
      }
    }
  }
  post {
    always {
      script {
        currentBuild.description = "Build ${VERSION}"
        nxJira.updateIssues()
      }
    }
  }
}
