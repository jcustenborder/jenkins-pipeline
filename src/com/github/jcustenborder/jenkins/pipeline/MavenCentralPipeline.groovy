package com.github.jcustenborder.jenkins.pipeline

properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10'))
])


def execute() {
    def version
    def artifactId
    def description
    def url


    node {
        deleteDir()
        checkout scm

        stage('build') {
            docker.image(images.jdk8_docker_image).inside {
                configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                    def mvn = new MavenUtilities(env, steps, "$MAVEN_SETTINGS")
                    version = mvn.changeVersion()
                    artifactId = mvn.artifactId()
                    description = mvn.description();

                    def goals
                    def profiles = null

                    if(env.BRANCH_NAME == 'master'||env.BRANCH_NAME == 'package-refactor') {
                        goals = 'clean deploy'
                        profiles = 'gpg-signing,maven-central'
                    } else {
                        goals = 'clean package'
                    }


                    url = mvn.url()
                    try {
                        mvn.execute(goals, profiles)
                    } finally {
                        junit '**/target/surefire-reports/TEST-*.xml'
                    }
                }
            }
        }
    }
}