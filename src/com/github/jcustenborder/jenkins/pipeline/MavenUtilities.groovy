package com.github.jcustenborder.jenkins.pipeline;

class MavenUtilities {
    def steps

    MavenUtilities(steps) { this.steps = steps; }

    def changeVersion(String mvnHome, String version) {
        if (env.BRANCH_NAME == 'master') {
            sh "${mvnHome}/bin/mvn -B versions:set -DgenerateBackupPoms=false -DnewVersion=${version}"
        }
    }
}

