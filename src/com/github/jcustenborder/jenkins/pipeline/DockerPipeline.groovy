package com.github.jcustenborder.jenkins.pipeline

def imageName = null;
def majorVersion = null;
def minorVersion = null;


def execute() {
    if(null == imageName) {
        error("imageName must be set.")
    }
    if(null == majorVersion) {
        error("imageName must be set.")
    }
    if(null == minorVersion) {
        error("imageName must be set.")
    }

    def scmResult

    node() {
        stage('checkout') {
            deleteDir()
            scmResult = checkout(scm)
        }

        def tags = []

        if (env.BRANCH_NAME == 'master') {
            tags.add("${imageName}:latest")
            tags.add("${majorVersion}.${minorVersion}.${env.BUILD_NUMBER}")
        } else {
            tags.add("${imageName}:${env.BRANCH_NAME}-${env.BUILD_NUMBER}")
        }

        def tagArgument = ''
        tags.each { tag ->
            tagArgument += " -t '${tag}'"
        }

        stage('build') {
            sh "docker build . ${tagArgument}"
        }

        stage('push') {
            tags.each { tag ->
                sh "docker push '${tag}'"
            }
        }
    }
}
