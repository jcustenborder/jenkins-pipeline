package com.github.jcustenborder.jenkins.pipeline

properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10'))
])

triggers {
    upstream(upstreamProjects: "jcustenborder/connect-utils/job/master", threshold: hudson.model.Result.SUCCESS)
}

def createPackage(String name, String type, String version, String description, String url) {
    def inputPath = "${pwd()}/target/${name}-${version}.tar.gz"
    def outputPath = "${pwd()}/target/${name}-${version}.${type}"

    if (fileExists(outputPath)) {
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

def execute() {
    def version
    def artifactId
    def description
    def url

    node {
        stage('checkout') {
            deleteDir()
            checkout scm
        }

        docker.image(images.jdk8_docker_image).inside("--net host -e DOCKER_HOST='tcp://127.0.0.1:2375'") {
            sh 'docker ps'
            stage('build') {
                configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                    withEnv(["JAVA_HOME=${images.jdk8_java_home}"]) {
                        def mvn = new MavenUtilities(env, steps, "$MAVEN_SETTINGS")
                        version = mvn.changeVersion()
                        artifactId = mvn.artifactId()
                        description = mvn.description();
                        url = mvn.url()
                        try {
                            mvn.execute('clean test')
                        }
                        finally {
                            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'
                        }
                    }
                }
            }
            sh 'echo DOCKER_HOST=$DOCKER_HOST'
            stage('integration-test') {
                configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                    withEnv(["JAVA_HOME=${images.jdk8_java_home}", 'DOCKER_HOST=tcp://127.0.0.1:2375']) {
                        sh 'echo $DOCKER_HOST'
                        def mvn = new MavenUtilities(env, steps, "$MAVEN_SETTINGS")
                        try {
                            mvn.execute('integration-test')
                        }
                        finally {
                            junit allowEmptyResults: true, testResults: '**/target/failsafe-reports/TEST-*.xml'
                        }
                    }
                }
            }

            stage('maven package') {
                configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                    withEnv(["JAVA_HOME=${images.jdk8_java_home}", 'DOCKER_HOST=tcp://127.0.0.1:2375']) {
                        def mvn = new MavenUtilities(env, steps, "$MAVEN_SETTINGS")
                        mvn.execute('package')
                    }
                }
                echo "Stashing target/${artifactId}-${version}.tar.gz"
                stash includes: "target/${artifactId}-${version}.tar.gz", name: 'tar'
                echo 'Stashing target/docs/**/**'
                stash includes: 'target/docs/**/**', name: 'docs'
                echo 'Stashing target/plugins/packages/*.zip'
                stash includes: 'target/plugins/packages/*.zip', name: 'plugin', allowEmpty: true

                if (env.BRANCH_NAME == 'master') {
                    echo 'Stashing target/CHANGELOG.md'
                    stash includes: 'target/CHANGELOG.md', name: 'changelog'
                }
            }
        }
    }


    stage('os packages') {
        parallel 'rpm': {
            node {
                unstash 'tar'
                docker.image(images.jdk8_docker_image).inside {
                    createPackage(artifactId, 'rpm', version, description, url)
                }
            }
        }, 'deb': {
            node {
                unstash 'tar'
                docker.image(images.jdk8_docker_image).inside {
                    createPackage(artifactId, 'deb', version, description, url)
                }
            }
        }
    }


    node {
        unstash 'rpm'
        unstash 'deb'
        unstash 'tar'
        unstash 'docs'

        archiveArtifacts artifacts: "target/${artifactId}-${version}.*"
        archiveArtifacts artifacts: "target/docs/**/*"
        archiveArtifacts artifacts: "target/plugins/packages/*.zip", allowEmptyArchive: true

        if (env.BRANCH_NAME == 'master') {
            unstash 'changelog'
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

                if (fileExists('target/plugins/packages')) {
                    dir('target/plugins/packages') {
                        withAWS(credentials: 'confluent_aws') {
                            withCredentials([string(credentialsId: 'plugin_staging', variable: 'BUCKET')]) {
                                s3Upload(
                                        acl: 'Private',
                                        region: 'us-west-1',
                                        bucket: "${BUCKET}",
                                        includePathPattern: '*.zip',
                                        path: "jcustenborder/${artifactId}/${version}"
                                )
                            }
                        }
                    }
                }

                docker.image(images.jdk8_docker_image).inside {
                    withCredentials([file(credentialsId: 'gpg_pubring', variable: 'GPG_PUBRING'), file(credentialsId: 'gpg_secring', variable: 'GPG_SECRING')]) {
                        configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                            withEnv(["JAVA_HOME=${images.jdk8_java_home}", 'DOCKER_HOST=tcp://127.0.0.1:2375']) {
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
    }
}
