package com.github.jcustenborder.jenkins.pipeline

properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10'))
])

def execute() {
    def version
    def artifactId
    def description
    def repositoryName

    node {
        deleteDir()
        def scmResult = checkout(scm)
        repositoryName = scmResult.GIT_URL.replaceAll('^.+:(.+)\\.git$', '$1')
        docker.image(images.jdk8_docker_image).inside("--net host -e DOCKER_HOST='tcp://127.0.0.1:2375'") {
            withCredentials([file(credentialsId: 'gpg_pubring', variable: 'GPG_PUBRING'), file(credentialsId: 'gpg_secring', variable: 'GPG_SECRING')]) {
                configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                    withEnv(["JAVA_HOME=${images.jdk8_java_home}", 'DOCKER_HOST=tcp://127.0.0.1:2375']) {
                        def mvn = new MavenUtilities(env, steps, MAVEN_SETTINGS, GPG_PUBRING, GPG_SECRING)
                        stage('build') {
                            version = mvn.changeVersion()
                            artifactId = mvn.artifactId()
                            description = mvn.description();
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
                            def profiles = env.BRANCH_NAME == 'master' ? 'docker-release':'docker-snapshot'
                            mvn.execute('package', profiles)
                            sh "ls -1 target/"
                            echo 'Stashing target/${artifactId}-${version}.*'
                            sh "ls -1 target/${artifactId}-${version}.*"
                            stash includes: "target/${artifactId}-${version}.*", name: 'assembly', allowEmpty: false
                        }
                    }
                }
            }
        }

        stage('publish') {
            unstash 'assembly'
            archiveArtifacts artifacts: "target/${artifactId}-${version}.*"

            def changelogGenerator = new ReleaseNoteGenerator(scmResult, steps)
            def changelog = changelogGenerator.generate()

            writeFile file: "target/RELEASENOTES.md", text: changelog
            archiveArtifacts artifacts: "target/RELEASENOTES.md", allowEmptyArchive: true

            if (Branches.isMainBranch(env.BRANCH_NAME)) {
                withCredentials([string(credentialsId: 'github_api_token', variable: 'apiToken')]) {
                    githubRelease(
                            commitish: scmResult.GIT_COMMIT,
                            token: apiToken,
                            description: "${changelog}",
                            repositoryName: repositoryName,
                            tagName: version,
                            includes: "target/RELEASENOTES.md"
                    )
                }
            }
        }
    }
}