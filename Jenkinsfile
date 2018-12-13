#!/usr/bin/env groovy

pipeline {
    agent any

    environment {
        APPLICATION_NAME = 'syfosmpapirmottak'
        FASIT_ENVIRONMENT = 'q1'
        ZONE = 'fss'
        DOCKER_SLUG = 'syfo'
        DISABLE_SLACK_MESSAGES = true
    }

     stages {
        stage('initialize') {
            steps {
                init action: 'gradle'
            }
        }
        stage('build') {
            steps {
                sh './gradlew build -x test'
            }
        }
        stage('run tests (unit & intergration)') {
            steps {
                sh './gradlew test'
            }
        }
        stage('create uber jar') {
            steps {
                sh './gradlew shadowJar'
                slackStatus status: 'passed'
            }
        }
       stage('push docker image') {
              steps {
                  dockerUtils action: 'createPushImage'
              }
         }
        stage('Create kafka topics') {
            steps {
                sh 'echo TODO'
                // TODO
            }
        }
        stage('validate & upload nais.yaml to nexus m2internal') {
             steps {
                 nais action: 'validate'
                 nais action: 'upload'
             }
         }
        stage('deploy to preprod') {
             steps {
                     deployApp action: 'jiraPreprod'
             }
         }
        stage('deploy to production') {
            when { environment name: 'DEPLOY_TO', value: 'production' }
            steps {
                deployApp action: 'jiraProd'
                githubStatus action: 'tagRelease'
            }
         }
        }
        post {
            always {
                postProcess action: 'always'
            }
            success {
                postProcess action: 'success'
            }
            failure {
                postProcess action: 'failure'
            }
        }
}
