package com.github.jcustenborder.jenkins.pipeline

class MavenUtilities implements Serializable {
    def env
    def steps
    def settings

    MavenUtilities(env, steps, settings) { this.env = env; this.steps = steps; this.settings = settings }

    def shouldChangeVersion() {
        return env.BRANCH_NAME == 'master'
    }

    def artifactId() {
        def pom = steps.readMavenPom().artifactId
    }

    def changeVersion() {
        def pom = steps.readMavenPom()

        if (!shouldChangeVersion()) {
            steps.echo "version changes only on master. Current branch is ${env.BRANCH_NAME}"
            return pom.version
        }

        if(!pom.version.endsWith("-SNAPSHOT")) {
            steps.echo "Version '${pom.version}' does end with -SNAPSHOT."
            return pom.version
        }

        def oldVersion = pom.version
        pom.version = pom.version.replace("-SNAPSHOT", ".${env.BUILD_NUMBER}")
        steps.sh "mvn -B versions:set -DgenerateBackupPoms=false -DnewVersion=${pom.version}"
        steps.echo "Changed version from ${oldVersion} to ${pom.version}"
        return pom.version
    }

    def execute(String goals, String profiles = null) {
        def commandLine = 'mvn -B' << ''

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

