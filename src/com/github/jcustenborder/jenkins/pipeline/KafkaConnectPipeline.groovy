package com.github.jcustenborder.jenkins.pipeline

properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10'))
])

triggers {
    upstream(upstreamProjects: "jcustenborder/connect-utils/job/master", threshold: hudson.model.Result.SUCCESS)
}

//def createPackage(String name, String type, String version, String description, String url) {
//    def inputPath = "${pwd()}/target/${name}-${version}.tar.gz"
//    def outputPath = "${pwd()}/target/${name}-${version}.${type}"
//
//    if (fileExists(outputPath)) {
//        sh "rm '${outputPath}'"
//    }
//
//    sh "/usr/local/bin/fpm --input-type tar " +
//            "--output-type ${type} " +
//            "--version ${version} " +
//            (env.BRANCH_NAME == 'master' ? "" : "--iteration ${env.BUILD_NUMBER} ") +
//            "--name ${name} " +
//            "--url ${url} " +
//            "--description '${description}' " +
//            "--license 'The Apache License, Version 2.0' " +
//            "--vendor 'Jeremy Custenborder' " +
//            "--architecture all " +
//            "--maintainer jcustenborder@gmail.com " +
//            "--config-files /etc " +
//            "--package '${outputPath}' " +
//            "'${inputPath}'"
//    echo "Finished building ${outputPath}, stashing ${outputPath}"
//    stash includes: "target/*.${type}", name: type
//}

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
                        mvn.execute('-DskipTests clean package')
                    }
                }
                sh "ls -1 target/"
//                echo "Stashing target/*.tar.gz"
//                stash includes: "target/*.tar.gz", name: 'tar'
                echo 'Stashing target/docs/**/**'
                stash includes: 'target/docs/**/**', name: 'docs'
//                echo 'Stashing target/confluent-docs/**/**'
//                stash includes: 'target/confluent-docs/**/**', name: 'confluent-docs', allowEmpty: true
                echo 'Stashing target/**/*.zip'
                stash includes: 'target/**/*.zip', name: 'plugin', allowEmpty: true
            }
        }
    }

//    stage('os packages') {
//        parallel 'rpm': {
//            node {
//                unstash 'tar'
//                docker.image(images.jdk8_docker_image).inside {
//                    createPackage(artifactId, 'rpm', version, description, url)
//                }
//            }
//        }, 'deb': {
//            node {
//                unstash 'tar'
//                docker.image(images.jdk8_docker_image).inside {
//                    createPackage(artifactId, 'deb', version, description, url)
//                }
//            }
//        }
//    }


    node {
//        unstash 'rpm'
//        unstash 'deb'
//        unstash 'tar'
        unstash 'docs'
        unstash 'plugin'

//        archiveArtifacts artifacts: "target/*.tar.gz"
//        archiveArtifacts artifacts: "target/*.rpm", allowEmptyArchive: true
//        archiveArtifacts artifacts: "target/*.deb", allowEmptyArchive: true
        archiveArtifacts artifacts: "target/docs/**/*"
        archiveArtifacts artifacts: "target/**/*.zip", allowEmptyArchive: true

        if (env.BRANCH_NAME == 'master') {
            def connectHub = new ConfluentConnectHub(env, steps, true)
            connectHub.uploadPlugin('jcustenborder', artifactId, version)
        }
    }
}
