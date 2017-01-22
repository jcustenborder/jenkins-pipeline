package com.github.jcustenborder.jenkins.pipeline

properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10'))
])

def createPackage(String name, String type, String version, String description, String url) {
    def inputPath = "${pwd()}/target/${name}-${version}.tar.gz"
    def outputPath = "${pwd()}/target/${name}-${version}.${type}"

    sh "/usr/local/bin/fpm --input-type tar " +
            "--output-type ${type} " +
            "--version ${version} " +
            "--name ${name} " +
            "--url ${url} " +
            "--description '${description}' " +
            "--license 'The Apache License, Version 2.0' " +
            "--vendor 'Jeremy Custenborder' " +
            "--architecture all " +
            "--maintainer jcustenborder@gmail.com " +
            "--config-files /etc " +
            "--package '${outputPath}' " +
            "'${inputPath}'"
    echo "Finished building ${outputPath}"
    stash includes: outputPath, name: type
}

def execute() {
    def version
    def artifactId
    def description
    def url

    stage('build') {
        node {
            checkout scm

            docker.image(images.jdk8_docker_image).inside {
                configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                    def mvn = new MavenUtilities(env, steps, "$MAVEN_SETTINGS")
                    version = mvn.changeVersion()
                    artifactId = mvn.artifactId()
                    description = mvn.description();
                    url = mvn.url()
                    mvn.execute('clean package')
                }
                junit '**/target/surefire-reports/TEST-*.xml'
            }
            stash includes: 'target/*.tar.*', name: 'tar'
        }
    }

    stage('package') {
        parallel 'rpm': {
            node {
                unstash 'tar'
                docker.image('jcustenborder/packaging-centos-7:latest').inside {
                    createPackage(artifactId, 'rpm', version, description, url)
                }
            }
        }, 'deb': {
            node {
                unstash 'tar'
                docker.image('jcustenborder/packaging-centos-7:latest').inside {
                    createPackage(artifactId, 'deb', version, description, url)
                }
            }
        }
        node {
            unstash 'rpm'
            unstash 'deb'
            unstash 'tar'
            archiveArtifacts "target/${artifactId}-${version}.*"
        }
    }
}