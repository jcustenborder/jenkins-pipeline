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

        def tagsToPush = []
        def tags = []

        if (env.BRANCH_NAME == 'master') {
            repositories.each { credential, repository ->
                mytags = [
                        "${repository}/${imageName}:latest",
                        "${repository}/${majorVersion}.${minorVersion}.${env.BUILD_NUMBER}"
                ]
                if (!tagsToPush.containsKey(credential)) {
                    tagsToPush[credential] = mytags
                } else {
                    tagsToPush[credential].addAll(mytags);
                }
            }
        } else {
            repositories.each { credential, repository ->
                mytags = [
                        "${repository}/${imageName}:${env.BRANCH_NAME}-${env.BUILD_NUMBER}"
                ]
                if (!tagsToPush.containsKey(credential)) {
                    tagsToPush[credential] = mytags
                } else {
                    tagsToPush[credential].addAll(mytags);
                }
            }
        }

        def tagArgument = ''
        tags.each { tag ->
            tagArgument += " -t '${tag}'"
        }

        stage('build') {
            docker.build "${tagArgument}"
        }

        stage('push') {
            tagsToPush.each {credential, tag ->
                wi
            }
            tags.each { tag ->
                sh "docker push '${tag}'"
            }
        }
    }
}
