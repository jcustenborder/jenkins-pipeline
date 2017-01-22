package com.github.jcustenborder.jenkins.pipeline

class MavenUtilities implements Serializable {
    def env
    def steps
    def settings

    MavenUtilities(env, steps, settings) { this.env = env; this.steps = steps; this.settings = settings }

    def shouldChangeVersion() {
        return env.BRANCH_NAME == 'master'
    }

    def changeVersion() {
        if (!shouldChangeVersion()) {
            return
        }

        pom = steps.readMavenPom
        def oldVersion = pom.version
        def matcher = (pom.version =~ /-SNAPSHOT$/)

        if (!matcher.find()) {
            return
        }

        def newVersion = matcher.replaceFirst(".${env.BUILD_NUMBER}")
        sh "mvn -B versions:set -DgenerateBackupPoms=false -DnewVersion=${newVersion}"
        steps.echo "Changed pom version from ${oldVersion} to ${newVersion}"
    }

    def execute(String goals, String profiles = null) {
        def commandLine = 'mvn -U -B' << ''

        if (null != this.settings) {
            commandLine << " --settings ${this.settings}"
        }

        if (null != profiles) {
            commandLine << " -P ${profiles}"
        }

        commandLine << " ${goals}"

        steps.sh commandLine.toString()
    }
}

