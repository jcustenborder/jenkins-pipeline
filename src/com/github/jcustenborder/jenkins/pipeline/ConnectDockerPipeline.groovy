package com.github.jcustenborder.jenkins.pipeline

def imageName = null;
def majorVersion = null;
def minorVersion = null;
def patchVersion = null;
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
    if (null == patchVersion) {
        error("patchVersion must be set.")
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
        def repositoryName = scmResult.GIT_URL.replaceAll('^.+:(.+)\\.git$', '$1')
        def version = "${majorVersion}.${minorVersion}.${patchVersion}-${env.BUILD_NUMBER}"
        def labels = [
                "${majorVersion}.${minorVersion}.${patchVersion}",
                version
        ]

        stage('build') {
            repositories.each { repository ->
                def repositoryUrl = repository['registry']
                def uri = new java.net.URI(repositoryUrl);
                //['credential': 'custenborder_docker', 'registry': 'https://docker.custenborder.com', 'repository': 'jcustenborder']
                withDockerRegistry(url: repositoryUrl, credentialsId: repository['credential']) {
                    labels.each { label ->
                        def image = docker.build("${uri.getHost()}/${repository['repository']}/${imageName}:${label}")
                        image.push()
                    }
                }
            }

            def changelogGenerator = new ReleaseNoteGenerator(scmResult, steps)
            def changelog = changelogGenerator.generate()

            writeFile file: "RELEASENOTES.md", text: changelog
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
