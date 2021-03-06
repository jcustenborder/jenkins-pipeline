package com.github.jcustenborder.jenkins.pipeline

properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10'))
])

triggers {
    upstream(upstreamProjects: "jcustenborder/connect-utils/job/master", threshold: hudson.model.Result.SUCCESS)
}

def execute() {
    def version
    def artifactId
    def description
    def url
    def scmResult

    node {

        stage('checkout') {
            deleteDir()
            scmResult = checkout(scm)
        }
        def repositoryName = scmResult.GIT_URL.replaceAll('^.+:(.+)\\.git$', '$1')

        // This step should not normally be used in your script. Consult the inline help for details.
        withDockerRegistry(credentialsId: 'custenborder_docker', url: 'https://docker.custenborder.com') {
            docker.image(images.jdk8_docker_image).inside("--net host -e DOCKER_HOST='tcp://127.0.0.1:2375'") {
                configFileProvider([configFile(fileId: 'mavenSettings', variable: 'MAVEN_SETTINGS')]) {
                    def mvn = new MavenUtilities(env, steps, "$MAVEN_SETTINGS")
                    artifactId = mvn.artifactId()
                    description = mvn.description()
                    url = mvn.url()
                    version = mvn.changeVersion()
                    stage('build') {
                        try {
                            mvn.execute('clean test integration-test package')
                        }
                        finally {
                            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml'
                            junit allowEmptyResults: true, testResults: '**/target/failsafe-reports/TEST-*.xml'
                        }
                        sh "ls -1 target/"
                        echo 'Stashing target/docs/**/**'
                        stash includes: 'target/docs/**/**', name: 'docs'
                        echo 'Stashing target/**/*.zip'
                        stash includes: 'target/**/*.zip', name: 'plugin', allowEmpty: true
                    }
                }
            }
        }
        unstash 'docs'
        unstash 'plugin'
        archiveArtifacts artifacts: "target/docs/**/*"
        archiveArtifacts artifacts: "target/**/*.zip", allowEmptyArchive: true

        def changelogGenerator = new ReleaseNoteGenerator(scmResult, steps)
        def changelog = changelogGenerator.generate()

        writeFile file: "target/RELEASENOTES.md", text: changelog
        archiveArtifacts artifacts: "target/RELEASENOTES.md", allowEmptyArchive: true

        if (Branches.isMainBranch(env.BRANCH_NAME)) {
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

            def connectHub = new ConfluentConnectHub(env, steps, true)
            connectHub.uploadPlugin('jcustenborder', artifactId, version)
        }
    }
}
