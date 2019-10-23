package com.github.jcustenborder.jenkins.pipeline

properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10'))
])

triggers {
    upstream(upstreamProjects: "jcustenborder/connect-utils/job/master", threshold: hudson.model.Result.SUCCESS)
}

def execute() {
    def version
    def artifactId
    def description
    def url
    def scmResult
    node {

        stage('checkout') {
            deleteDir()
            scmResult = checkout(scm)
        }

        docker.image(images.jdk8_docker_image).inside("--net host -e DOCKER_HOST='tcp://127.0.0.1:2375'") {
            configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                withEnv(["JAVA_HOME=${images.jdk8_java_home}", 'DOCKER_HOST=tcp://127.0.0.1:2375']) {
                    def mvn = new MavenUtilities(env, steps, "$MAVEN_SETTINGS")
                    artifactId = mvn.artifactId()
                    description = mvn.description();
                    url = mvn.url()
                    version = mvn.changeVersion()
                    stage('build') {
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
                        sh "ls -1 target/"
                        echo 'Stashing target/docs/**/**'
                        stash includes: 'target/docs/**/**', name: 'docs'
                        echo 'Stashing target/**/*.zip'
                        stash includes: 'target/**/*.zip', name: 'plugin', allowEmpty: true
                    }
                }
            }
        }
    }

    node {
        unstash 'docs'
        unstash 'plugin'
        archiveArtifacts artifacts: "target/docs/**/*"
        archiveArtifacts artifacts: "target/**/*.zip", allowEmptyArchive: true

        if (env.BRANCH_NAME == 'master') {
            def changelogGenerator = new ReleaseNoteGenerator(scmResult, steps)
            def changelog = changelogGenerator.generate()
            withCredentials([string(credentialsId: 'github_api_token', variable: 'apiToken')]) {
                githubRelease(
                        commitish: scmResult.GIT_COMMIT,
                        token: apiToken,
                        description: "${changelog}",
                        repositoryName: "jcustenborder/${artifactId}",
                        tagName: version
                )
            }

            def connectHub = new ConfluentConnectHub(env, steps, true)
            connectHub.uploadPlugin('jcustenborder', artifactId, version)
        }
    }
}
