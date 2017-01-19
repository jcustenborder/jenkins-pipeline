package com.github.jcustenborder.jenkins.pipeline

class MavenUtilities implements Serializable {
    def steps
    def settings;

    MavenUtilities(steps, settings) { this.steps = steps; this.settings = settings }

    def changeVersion(String version) {
        if (env.BRANCH_NAME == 'master') {
            steps.sh "mvn --batch-mode versions:set -DgenerateBackupPoms=false -DnewVersion=${version}"
        }
    }

    def execute(String goals, String stage = 'build') {
        steps.sh "mvn --settings ${this.settings} --batch-mode ${goals}"
    }
}

