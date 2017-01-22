package com.github.jcustenborder.jenkins.pipeline

properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10'))
])

def execute() {
    def version
    def artifactId;

    stage('build') {
        node {
            checkout scm


            docker.image(images.jdk8_docker_image).inside {
                configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                    def mvn = new MavenUtilities(env, steps, "$MAVEN_SETTINGS")
                    version = mvn.changeVersion()
                    artifactId = mvn.artifactId()
                    mvn.execute('clean package')
                }
                junit '**/target/surefire-reports/TEST-*.xml'
            }
            stash includes: 'target/*.tar.*', name: 'connector'
        }
    }

    stage('package') {
        node {
            unstash 'connector'
            archiveArtifacts 'target/*.tar.*'
            def input_package = "${version}"

            docker.image('jcustenborder/packaging-centos-7:latest').inside {
                sh "/usr/local/bin/fpm --input-type tar --output-type rpm --version ${version} --name ${artifactId} --license 'The Apache License, Version 2.0' --vendor 'Jeremy Custenborder' --architecture all --maintainer jcustenborder@gmail.com --config-files /etc --package target/${artifactId}-${version}.rpm target/${artifactId}-${version}.tar.gz"
            }
        }
    }
}