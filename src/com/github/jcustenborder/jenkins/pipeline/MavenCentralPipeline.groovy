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
                withCredentials([file(credentialsId: 'gpg_pubring', variable: 'GPG_PUBRING'), file(credentialsId: 'gpg_secring', variable: 'GPG_SECRING')]) {
                    configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                        withEnv(["JAVA_HOME=${images.jdk8_java_home}"]) {
                            def mvn = new MavenUtilities(env, steps, MAVEN_SETTINGS, GPG_PUBRING, GPG_SECRING)
                            version = mvn.changeVersion()
                            artifactId = mvn.artifactId()
                            description = mvn.description();

                            def goals
                            def profiles = null

                            if (env.BRANCH_NAME == 'master') {
                                goals = 'clean deploy'
                                profiles = 'gpg-signing,maven-central'
                            } else {
                                goals = 'clean package'
                            }

                            url = mvn.url()
                            try {
                                mvn.execute(goals, profiles)
                            } finally {
                                junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'
                            }
                        }
                    }
                }
            }
        }
    }
}