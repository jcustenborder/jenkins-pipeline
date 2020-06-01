package com.github.jcustenborder.jenkins.pipeline

def imageName = null;
def majorVersion = null;
def minorVersion = null;
def repositories = null;


def execute() {
    if (null == imageName) {
        error("imageName must be set.")
    }
    if (null == majorVersion) {
        error("majorVersion must be set.")
    }
    if (null == minorVersion) {
        error("minorVersion must be set.")
    }
    if (null == repositories) {
        error("repositories must be set.")
    }

    def scmResult

    node() {
        stage('checkout') {
            deleteDir()
            scmResult = checkout(scm)
        }

        def versions = []
        if (env.BRANCH_NAME == 'master') {
            versions.add("latest")
            versions.add("${majorVersion}.${minorVersion}.${env.BUILD_NUMBER}")
        } else {
            versions.add("${env.BRANCH_NAME}-${env.BUILD_NUMBER}")
        }


        stage('build') {
            repositories.each { repository ->
                def repositoryUrl = repository['registry']
                def uri = new java.net.URI(repositoryUrl);
                //['credential': 'custenborder_docker', 'registry': 'https://docker.custenborder.com', 'repository': 'jcustenborder']
                withDockerRegistry(url: repositoryUrl, credentialsId: repository['credential']) {
                    versions.each { version ->
                        def image = docker.build("${uri.getHost()}/${repository['repository']}/${imageName}:${version}")
                        image.push()
                    }
                }
            }
            def changelogGenerator = new ReleaseNoteGenerator(scmResult, steps)
            def changelog = changelogGenerator.generate()

            writeFile file: "target/RELEASENOTES.md", text: changelog
            archiveArtifacts artifacts: "RELEASENOTES.md", allowEmptyArchive: true

            if (env.BRANCH_NAME == 'master') {
                withCredentials([string(credentialsId: 'github_api_token', variable: 'apiToken')]) {
                    githubRelease(
                            commitish: scmResult.GIT_COMMIT,
                            token: apiToken,
                            description: "${changelog}",
                            repositoryName: repositoryName,
                            tagName: version,
                            includes: "RELEASENOTES.md"
                    )
                }
            }

        }
    }
}
