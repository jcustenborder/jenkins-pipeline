#!/usr/bin/env groovy

def changeVersion (String mvnHome, String version) {
    if (env.BRANCH_NAME == 'master') {
        sh "${mvnHome}/bin/mvn -B versions:set -DgenerateBackupPoms=false -DnewVersion=${version}"
    }
}