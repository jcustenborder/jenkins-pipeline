package com.github.jcustenborder.jenkins.pipeline

properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10'))
])

def createPackage(String name, String type, String version, String description, String url) {
    def inputPath = "${pwd()}/target/${name}-${version}.tar.gz"
    def outputPath = "${pwd()}/target/${name}-${version}.${type}"

    if(fileExists(outputPath)) {
        sh "rm '${outputPath}'"
    }

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

def createDockerfile(String name, String version, String baseImage = 'confluentinc/cp-kafka-connect:3.2.2-1') {
    def text = "FROM ${baseImage}\n" +
            "MAINTAINER jcustenborder@gmail.com\n" +
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
            deleteDir()
            checkout scm

            docker.image(images.jdk8_docker_image).inside {
                configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                    def mvn = new MavenUtilities(env, steps, "$MAVEN_SETTINGS")
                    version = mvn.changeVersion()
                    artifactId = mvn.artifactId()
                    description = mvn.description();
                    url = mvn.url()
                    try {
                        mvn.execute('clean package')
                    }
                    finally {
                        junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'
                    }
                }
            }
            stash includes: "target/${artifactId}-${version}.tar.gz", name: 'tar'
            stash includes: 'target/CHANGELOG.md', name: 'changelog'
            stash includes: 'target/docs/**/**', name: 'docs'
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
        unstash 'changelog'
        unstash 'docs'

        def image

        archiveArtifacts "target/${artifactId}-${version}.*"
        archiveArtifacts "target/docs/**/*"

        if (env.BRANCH_NAME == 'master') {
            stage('publish') {
                withCredentials([string(credentialsId: 'github_api_token', variable: 'apiToken')]) {
                    githubRelease(
                            token: apiToken,
                            repositoryName: "jcustenborder/${artifactId}",
                            tagName: version,
                            descriptionFile: 'target/CHANGELOG.md',
                            includes: "target/${artifactId}-${version}.*",
                            excludes: 'target/*.jar'
                    )
                }
                withCredentials([file(credentialsId: 'gpg_pubring', variable: 'GPG_PUBRING'), file(credentialsId: 'gpg_secring', variable: 'GPG_SECRING')]) {
                    configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                        if (env.BRANCH_NAME == 'master') {
                            goals = 'deploy'
                            profiles = 'gpg-signing,maven-central'

                            def mvn = new MavenUtilities(env, steps, MAVEN_SETTINGS, GPG_PUBRING, GPG_SECRING)
                            mvn.execute(goals, profiles)
                        }
                    }
                }
            }
        }
    }
}
