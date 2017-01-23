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
    echo "Finished building ${outputPath}, stashing ${outputPath}"
    stash includes: "target/*.${type}", name: type
}

def createDockerfile(String name, String version, String baseImage = 'confluentinc/cp-kafka-connect-base:3.1.1-1') {
    def text = "FROM ${baseImage}\n" +
            "ADD ${name}-${version}.tar.gz /\n"

    writeFile file: 'Dockerfile', text: text
    stash includes: 'Dockerfile', name: 'Dockerfile'
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
            stash includes: "target/${artifactId}-${version}.tar.gz", name: 'tar'
//            stash includes: "target/CHANGELOG.md", name: 'changelog'
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
    }

    node {
        unstash 'rpm'
        unstash 'deb'
        unstash 'tar'
//        unstash 'changelog'

        def image

        stage('docker') {
            dir('target') {
                createDockerfile(artifactId, version)
                image = docker.build("jcustenborder/${artifactId}")
            }
        }

        archiveArtifacts "target/${artifactId}-${version}.*"

        if (env.BRANCH_NAME == 'master') {
            stage('publish') {
                image.push 'latest'
                image.push version
                archiveArtifacts 'target/Dockerfile'

                withCredentials([string(credentialsId: 'github_api_token', variable: 'apiToken')]) {
                    dir('target') {
                        githubRelease(
                                token: apiToken,
                                repositoryName: "jcustenborder/${artifactId}",
                                tagName: version,
                                description: 'Testing',
                                includes: "${artifactId}-${version}.*",
                                excludes: '**/*.jar'
                        )
                    }
                }
            }
        }
    }
}