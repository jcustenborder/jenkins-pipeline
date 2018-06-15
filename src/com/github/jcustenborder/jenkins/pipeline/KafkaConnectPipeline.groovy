package com.github.jcustenborder.jenkins.pipeline

properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10'))
])

triggers {
    upstream(upstreamProjects: "jcustenborder/connect-utils/job/master", threshold: hudson.model.Result.SUCCESS)
}

def uploadPlugin(zipFileName, owner, artifactId, version) {
    unzip(
            glob: '**/manifest.json',
            zipFile: "${zipFileName}"
    )
    manifest_path = findFiles(glob: '**/manifest.json')

    withAWS(credentials: 'confluent_aws', region: 'us-west-1') {
        withCredentials([string(credentialsId: 'plugin_staging', variable: 'BUCKET')]) {
            s3Upload(
                    acl: 'Private',
                    bucket: "${BUCKET}",
                    includePathPattern: "${zipFileName}",
                    path: "${owner}/${artifactId}/${version}/${zipFileName}"
            )
            s3Upload(
                    acl: 'Private',
                    bucket: "${BUCKET}",
                    includePathPattern: "${manifest_path[0].path}",
                    path: "${owner}/${artifactId}/${version}/manifest.json"
            )
        }
    }
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
            (env.BRANCH_NAME == 'master' ? "" : "--iteration ${env.BUILD_NUMBER} ") +
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
                echo 'Stashing target/confluent-docs/**/**'
                stash includes: 'target/confluent-docs/**/**', name: 'confluent-docs', allowEmpty: true
                echo 'Stashing target/plugins/packages/*.zip'
                stash includes: 'target/plugins/packages/*.zip', name: 'plugin', allowEmpty: true
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
        unstash 'confluent-docs'

        archiveArtifacts artifacts: "target/${artifactId}-${version}.*"
        archiveArtifacts artifacts: "target/docs/**/*"
        archiveArtifacts artifacts: "target/plugins/packages/*.zip", allowEmptyArchive: true
        archiveArtifacts artifacts: "target/confluent-docs/**/**", allowEmptyArchive: true

        if (env.BRANCH_NAME == 'master') {
            stage('publish') {
                if (fileExists('target/plugins/packages')) {
                    dir('target/plugins/packages') {
                        def zipFileName = "jcustenborder-${artifactId}-${version}-plugin.zip"
                        if(fileExists(zipFileName)) {
                            uploadPlugin(zipFileName, 'jcustenborder', artifactId, version)
                        }
                        zipFileName = "confluentinc-${artifactId}-${version}-plugin.zip"
                        if(fileExists(zipFileName)) {
                            uploadPlugin(zipFileName, 'confluentinc', artifactId, version)
                        }
                    }
                }

//                docker.image(images.jdk8_docker_image).inside {
//                    withCredentials([file(credentialsId: 'gpg_pubring', variable: 'GPG_PUBRING'), file(credentialsId: 'gpg_secring', variable: 'GPG_SECRING')]) {
//                        configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
//                            withEnv(["JAVA_HOME=${images.jdk8_java_home}", 'DOCKER_HOST=tcp://127.0.0.1:2375']) {
//                                if (env.BRANCH_NAME == 'master') {
//                                    goals = 'deploy'
//                                    profiles = 'gpg-signing,maven-central'
//
//                                    def mvn = new MavenUtilities(env, steps, MAVEN_SETTINGS, GPG_PUBRING, GPG_SECRING)
//                                    mvn.execute(goals, profiles)
//                                }
//                            }
//                        }
//                    }
//                }
            }
        }
    }
}
