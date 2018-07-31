package com.github.jcustenborder.jenkins.pipeline

properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '30'))
])

triggers {
    upstream(upstreamProjects: "jcustenborder/connect-utils/job/master", threshold: hudson.model.Result.SUCCESS)
}

def execute() {
    def version
    def artifactId

    node {
        stage('checkout') {
            deleteDir()
            checkout scm
        }

        docker.image(images.jdk8_docker_image).inside("--net host -e DOCKER_HOST='tcp://127.0.0.1:2375'") {
            sh 'docker ps'
            configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                withEnv(["JAVA_HOME=${images.jdk8_java_home}", 'DOCKER_HOST=tcp://127.0.0.1:2375']) {
                    def mvn = new MavenUtilities(env, steps, "$MAVEN_SETTINGS")
                    stage('build') {
                        version = mvn.changeVersion()
                        artifactId = mvn.artifactId()
                        try {
                            mvn.execute('clean test')
                        }
                        finally {
                            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'
                        }
                    }
                    stage('integration-test') {
                        try {
                            mvn.execute('integration-test')
                        }
                        finally {
                            junit allowEmptyResults: true, testResults: '**/target/failsafe-reports/TEST-*.xml'
                        }
                    }
                    stage('maven package') {
                        mvn.execute('package')
                    }
                }
            }
        }

        archiveArtifacts artifacts: "target/${artifactId}-${version}.*"
        archiveArtifacts artifacts: "target/**/packages/*${artifactId}-${version}*.zip"
        archiveArtifacts artifacts: "target/confluent-docs/**/**", allowEmptyArchive: true

        if (env.BRANCH_NAME == 'master') {
            def connectHub = new ConfluentConnectHub(env, steps, true)
            connectHub.uploadPlugin('confluentinc', artifactId, version)
        }
    }
}
