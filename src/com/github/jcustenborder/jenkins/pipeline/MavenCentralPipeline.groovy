package com.github.jcustenborder.jenkins.pipeline

properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10'))
])


def execute() {
    def version
    def artifactId
    def description
    def url


    node {
        deleteDir()
        def scmResult = checkout(scm)
        echo "GIT_URL is ${scmResult.GIT_URL}"

        stage('build') {
            docker.image(images.jdk8_docker_image).inside("--net host -e DOCKER_HOST='tcp://127.0.0.1:2375'") {
                withCredentials([file(credentialsId: 'gpg_pubring', variable: 'GPG_PUBRING'), file(credentialsId: 'gpg_secring', variable: 'GPG_SECRING')]) {
                    configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                        withEnv(["JAVA_HOME=${images.jdk8_java_home}", 'DOCKER_HOST=tcp://127.0.0.1:2375']) {
                            def mvn = new MavenUtilities(env, steps, MAVEN_SETTINGS, GPG_PUBRING, GPG_SECRING)
                            version = mvn.changeVersion()
                            artifactId = mvn.artifactId()
                            description = mvn.description();

                            def goals
                            def profiles = null

                            if (env.BRANCH_NAME == 'master') {
                                goals = 'clean deploy'
                                profiles = 'gpg-signing,maven-central'
                            } else {
                                goals = 'clean verify'
                            }

                            url = mvn.url()
                            try {
                                mvn.execute(goals, profiles)
                            } finally {
                                junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'
                            }
                        }
                    }
                }
            }

            if (env.BRANCH_NAME == 'master') {
                def changelogGenerator = new ReleaseNoteGenerator(scmResult, steps)
                def changelog = changelogGenerator.generate()


                withCredentials([string(credentialsId: 'github_api_token', variable: 'apiToken')]) {
                    githubRelease(
                            commitish: scmResult.GIT_COMMIT,
                            token: apiToken,
                            description: "${changelog}",
                            repositoryName: "jcustenborder/${artifactId}",
                            tagName: version,
                            includes: "target/${artifactId}-${version}.*",
                            excludes: 'target/*'
                    )
                }
            }
        }
    }
}
