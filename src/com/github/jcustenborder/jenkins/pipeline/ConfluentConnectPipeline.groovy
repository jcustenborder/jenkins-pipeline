package com.github.jcustenborder.jenkins.pipeline

properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '30'))
])

triggers {
    upstream(upstreamProjects: "jcustenborder/connect-utils/job/master", threshold: hudson.model.Result.SUCCESS)
}

def uploadPlugin(owner, artifactId, version) {
    def zipFiles = findFiles(glob: "target/**/packages/*${artifactId}-${version}*.zip")
    def zipFileName

    if (zipFiles) {
        zipFileName = zipFiles[0].path
    } else {
        error("Could not find artifact that matched 'target/**/packages/*${artifactId}-${version}*.zip'")
    }

    unzip(
            glob: '**/manifest.json',
            zipFile: "${zipFileName}"
    )
    def manifests = findFiles(glob: '**/manifest.json')

    if (manifests) {
        def manifestPath = manifests[0]
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
                        includePathPattern: "${manifestPath.path}",
                        path: "${owner}/${artifactId}/${version}/manifest.json"
                )
            }
        }
    } else {
        error("Could not find manifest in zip that matched '**/manifest.json'")
    }
}


def execute() {
    def version
    def artifactId

    node {
        stage('checkout') {
            deleteDir()
            checkout scm
        }

        docker.image(images.jdk8_docker_image).inside("--net host -e DOCKER_HOST='tcp://127.0.0.1:2375'") {
            sh 'docker ps'
            configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                withEnv(["JAVA_HOME=${images.jdk8_java_home}", 'DOCKER_HOST=tcp://127.0.0.1:2375']) {
                    def mvn = new MavenUtilities(env, steps, "$MAVEN_SETTINGS")
                    stage('build') {
                        version = mvn.changeVersion()
                        artifactId = mvn.artifactId()
                        try {
                            mvn.execute('clean test')
                        }
                        finally {
                            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'
                        }
                    }
                    stage('integration-test') {
                        sh 'echo $DOCKER_HOST'
                        try {
                            mvn.execute('integration-test')
                        }
                        finally {
                            junit allowEmptyResults: true, testResults: '**/target/failsafe-reports/TEST-*.xml'
                        }
                    }
                    stage('maven package') {
                        mvn.execute('package')
                    }
                }
            }
        }

        archiveArtifacts artifacts: "target/${artifactId}-${version}.*"
        archiveArtifacts artifacts: "target/**/packages/${artifactId}-${version}*.zip"
        archiveArtifacts artifacts: "target/confluent-docs/**/**", allowEmptyArchive: true

        if (env.BRANCH_NAME == 'master') {
            stage('publish') {
                uploadPlugin('confluentinc', artifactId, version)
            }
        }
    }
}
