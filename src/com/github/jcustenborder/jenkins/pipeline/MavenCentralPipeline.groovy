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
        def scm_result = checkout(scm)

        echo "Generating changelog from '${scm_result.GIT_PREVIOUS_SUCCESSFUL_COMMIT}' to '${scm_result.GIT_COMMIT}'"

        def changelog = gitChangelog returnType: 'STRING',
                from: [type: 'REF', value: "${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}"],
                to: [type: 'REF', value: "${env.GIT_COMMIT}"],
                template: """
# Changelog

{{#tags}}
## {{name}}
 {{#issues}}
  {{#hasIssue}}
   {{#hasLink}}
### {{name}} [{{issue}}]({{link}}) {{title}} {{#hasIssueType}} *{{issueType}}* {{/hasIssueType}} {{#hasLabels}} {{#labels}} *{{.}}* {{/labels}} {{/hasLabels}}
   {{/hasLink}}
   {{^hasLink}}
### {{name}} {{issue}} {{title}} {{#hasIssueType}} *{{issueType}}* {{/hasIssueType}} {{#hasLabels}} {{#labels}} *{{.}}* {{/labels}} {{/hasLabels}}
   {{/hasLink}}
  {{/hasIssue}}
  {{^hasIssue}}
### {{name}}
  {{/hasIssue}}

  {{#commits}}
**{{{messageTitle}}}**

{{#messageBodyItems}}
 * {{.}} 
{{/messageBodyItems}}

[{{hash}}](https://github.com/{{ownerName}}/{{repoName}}/commit/{{hash}}) {{authorName}} *{{commitTime}}*

  {{/commits}}

 {{/issues}}
{{/tags}}
 """

        echo "${changelog}"

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

                withCredentials([string(credentialsId: 'github_api_token', variable: 'apiToken')]) {
                    githubRelease(
                            commitish: env.GIT_COMMIT,
                            token: apiToken,
                            description: "${version}",
                            repositoryName: "jcustenborder/${artifactId}",
                            tagName: version
//                            includes: "target/${artifactId}-${version}.*",
//                            excludes: 'target/*.jar'
                    )
                }
//                sshagent(credentials: ['50a4ec3a-9caf-43d1-bfab-6465b47292da']) {
//                    sh "git push origin ${version}"
//                }
            }
        }
    }
}
