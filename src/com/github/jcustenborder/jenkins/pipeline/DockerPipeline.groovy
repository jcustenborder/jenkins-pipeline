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
                //['credential': 'custenborder_docker', 'registry': 'https://docker.custenborder.com', 'repository': 'jcustenborder']
                withDockerRegistry(url: "docker.custenborder.com", credentialsId: repository['credential']) {
                    versions.each { version ->
                        def image = docker.build("docker.custenborder.com/${repository['repository']}/${imageName}:${version}")
                        image.push()
                    }
                }
            }
        }
    }
}
