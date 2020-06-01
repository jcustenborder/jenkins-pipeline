package com.github.jcustenborder.jenkins.pipeline

def execute() {
    def scmResult

    node() {
        stage('checkout') {
            deleteDir()
            scmResult = checkout(scm)
        }

        imageSettings = readYaml(file: 'image.yml')

        def tags=[]

        if (env.BRANCH_NAME == 'master') {
            tags.add("${imageSettings['name']}:latest")
        } else {
            tags.add("${imageSettings['name']}:${env.BRANCH_NAME}-${env.BUILD_NUMBER}")
        }

        def tagArgument=''
        tags.each { tag->
            tagArgument+=" -t '${tag}'"
        }

        stage('build') {
            sh "docker build . ${tagArgument}"
        }
    }
}
