package com.github.jcustenborder.jenkins.pipeline

def artifacts = "target/${artifactId}-${version}.*"

def execute() {
    def version
    def artifactId
    def description


    node {
        deleteDir()
        def scmResult = checkout(scm)
        def repositoryName = scmResult.GIT_URL.replaceAll('^.+:(.+)\\.git$', '$1')
        withDockerRegistry(credentialsId: 'custenborder_docker', url: 'https://docker.custenborder.com') {
            docker.image(images.jdk11_docker_image).inside("--net host -e DOCKER_HOST='tcp://127.0.0.1:2375'") {
                withCredentials([file(credentialsId: 'gpg_pubring', variable: 'GPG_PUBRING'), file(credentialsId: 'gpg_secring', variable: 'GPG_SECRING')]) {
                    configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                        withEnv(['DOCKER_HOST=tcp://127.0.0.1:2375']) {
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
                                mvn.execute('package')
                                sh "ls -1 target/"
                                echo "Stashing ${artifacts}"
                                sh "ls -1 ${artifacts}"
                                stash includes: artifacts, name: 'assembly', allowEmpty: false
                            }
                        }
                    }
                }
            }
        }
        stage('publish') {
            unstash 'assembly'
            archiveArtifacts artifacts: artifacts

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
                            includes: artifacts,
                            excludes: 'target/*.jar'
                    )
                }
            }
        }
    }
}
