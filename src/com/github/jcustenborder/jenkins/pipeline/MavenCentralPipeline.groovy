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
        def repositoryName = scmResult.GIT_URL.replaceAll('^.+:(.+)\\.git$', '$1')

        stage('build') {
            withDockerRegistry(credentialsId: 'custenborder_docker', url: 'https://docker.custenborder.com') {
                docker.image(images.jdk11_docker_image).inside("--net host -e DOCKER_HOST='tcp://127.0.0.1:2375'") {
                    withCredentials([file(credentialsId: 'gpg_pubring', variable: 'GPG_PUBRING'), file(credentialsId: 'gpg_secring', variable: 'GPG_SECRING')]) {

                        configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                            withEnv(['DOCKER_HOST=tcp://127.0.0.1:2375']) {
                                def mvn = new MavenUtilities(env, steps, MAVEN_SETTINGS, GPG_PUBRING, GPG_SECRING)
                                version = mvn.changeVersion()
                                artifactId = mvn.artifactId()
                                description = mvn.description();

                                def goals
                                def profiles = null

                                if (branches.isMainBranch(env.BRANCH_NAME)) {
                                    goals = 'clean deploy'
                                    profiles = 'gpg-signing,maven-central'
                                    sh 'gpg --list-keys'
                                } else {
                                    goals = 'clean verify'
                                }

                                url = mvn.url()
                                try {
                                    mvn.execute(goals, profiles)
                                } finally {
                                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'
                                }
                                try {
                                    recordIssues(tools: [junitParser(pattern: '**/target/surefire-reports/TEST-*.xml')])
                                } catch (Exception e) {

                                }
                                try {
                                    recordIssues(tools: [spotBugs(pattern: '**/spotbugsXml.xml', useRankAsPriority: true)])
                                } catch (Exception e) {

                                }
                            }
                        }
                    }
                }
            }

            def changelogGenerator = new ReleaseNoteGenerator(scmResult, steps)
            def changelog = changelogGenerator.generate()

            writeFile file: "target/RELEASENOTES.md", text: changelog
            archiveArtifacts artifacts: "target/RELEASENOTES.md", allowEmptyArchive: true

            if (branches.isMainBranch(env.BRANCH_NAME)) {
                withCredentials([string(credentialsId: 'github_api_token', variable: 'apiToken')]) {
                    githubRelease(
                            commitish: scmResult.GIT_COMMIT,
                            token: apiToken,
                            description: "${changelog}",
                            repositoryName: repositoryName,
                            tagName: version,
                            includes: "target/${artifactId}-${version}.*",
                            excludes: 'target/*'
                    )
                }
            }
        }
    }
}
