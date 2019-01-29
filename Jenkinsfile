#!/usr/bin/env groovy

pipeline {
    agent any

    environment {
        APPLICATION_NAME = 'syfosmpapirmottak'
        DOCKER_SLUG = 'syfo'

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
       stage('deploy to preprod') {
              steps {
                   dockerUtils action: 'createPushImage'
                   deployApp action: 'kubectlDeploy', cluster: 'preprod-fss'
                   }
               }
       stage('deploy to production') {
                   when { environment name: 'DEPLOY_TO', value: 'production' }
              steps {
                   deployApp action: 'kubectlDeploy', cluster: 'prod-fss', file: 'naiserator-prod.yaml'
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
