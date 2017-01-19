package com.github.jcustenborder.jenkins.pipeline

class MavenUtilities implements Serializable {
    def steps
    def settings;

    MavenUtilities(steps, settings) { this.steps = steps; this.settings = settings }

    def shouldChangeVersion() {
        return env.BRANCH_NAME == 'master'
    }

    def changeVersion(String version) {
        steps.sh "mvn --batch-mode versions:set -DgenerateBackupPoms=false -DnewVersion=${version}"
    }

    def execute(String goals, String profiles = null) {
        def commandLine = 'mvn -U -B' << ''

        if (null != this.settings) {
            commandLine << " --settings ${this.settings}"
        }

        if (null != this.profiles) {
            commandLine << " -P ${profiles}"
        }

        commandLine << " ${goals}"

        steps.sh commandLine.toString()
    }
}

