package com.github.jcustenborder.jenkins.pipeline

properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10'))
])

def execute() {
    stage('build') {
        node {
            checkout scm

            def version
            docker.image(images.jdk8_docker_image).inside {
                configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                    def mvn = new MavenUtilities(env, steps, "$MAVEN_SETTINGS")
                    version = mvn.changeVersion()
                    mvn.execute('clean package')
                }
                junit '**/target/surefire-reports/TEST-*.xml'
            }
            stash includes: 'target/*.tar.*', name: 'connector'
        }
    }

    stage('package') {
        node {
            unstash 'connector'
            archiveArtifacts 'target/*.tar.*'
        }
    }
}