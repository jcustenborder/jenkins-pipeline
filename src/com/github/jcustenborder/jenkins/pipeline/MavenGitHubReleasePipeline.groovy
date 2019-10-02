package com.github.jcustenborder.jenkins.pipeline

def execute() {
    def version
    def artifactId
    def description


    node {
        deleteDir()
        checkout scm


        docker.image(images.jdk8_docker_image).inside("--net host -e DOCKER_HOST='tcp://127.0.0.1:2375'") {
            withCredentials([file(credentialsId: 'gpg_pubring', variable: 'GPG_PUBRING'), file(credentialsId: 'gpg_secring', variable: 'GPG_SECRING')]) {
                configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                    withEnv(["JAVA_HOME=${images.jdk8_java_home}", 'DOCKER_HOST=tcp://127.0.0.1:2375']) {
                        def mvn = new MavenUtilities(env, steps, MAVEN_SETTINGS, GPG_PUBRING, GPG_SECRING)

                        stage('build') {
                            try {
                                mvn.execute('clean test')
                            }
                            finally {
                                junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'
                            }
                        }
                        stage('integration-test') {
                            try {
                                mvn.execute('integration-test')
                            }
                            finally {
                                junit allowEmptyResults: true, testResults: '**/target/failsafe-reports/TEST-*.xml'
                            }
                        }
                        stage('maven package') {
                            mvn.execute('package')
                            sh "ls -1 target/"
                            echo 'Stashing target/*.tar.gz'
                            stash includes: "target/${artifactId}-${version}.*", name: 'assembly', allowEmpty: false
                        }
                    }
                }
            }

            if (env.BRANCH_NAME == 'master') {
                stage('publish') {
                    unstash 'assembly'
                    withCredentials([string(credentialsId: 'github_api_token', variable: 'apiToken')]) {
                        githubRelease(
                                commitish: env.GIT_COMMIT,
                                token: apiToken,
                                repositoryName: "jcustenborder/${artifactId}",
                                tagName: version,
                                includes: "target/${artifactId}-${version}.*",
                                excludes: 'target/*.jar'
                        )
                    }
                }
            }
        }
    }
}