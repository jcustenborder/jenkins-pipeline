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

            sh "git tag ${version}"
            sshagent(credentials: ['50a4ec3a-9caf-43d1-bfab-6465b47292da']) {
                sh "git push 'origin' '${version}'"
            }
        }
    }
}
