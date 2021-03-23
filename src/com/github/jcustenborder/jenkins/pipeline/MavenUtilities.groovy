package com.github.jcustenborder.jenkins.pipeline

class MavenUtilities implements Serializable {
    def env
    def steps
    def settingsPath
    String pubringPath
    String secringPath

    MavenUtilities(env, steps, String settingsPath, String pubringPath = null, String secringPath = null) {
        this.env = env
        this.steps = steps
        this.settingsPath = settingsPath
        this.pubringPath = pubringPath;
        this.secringPath = secringPath
    }

    def artifactId() {
        return steps.readMavenPom().artifactId
    }

    def description() {
        return steps.readMavenPom().description
    }

    def url() {
        return steps.readMavenPom().url
    }

    def changeVersion() {
        def pom = steps.readMavenPom()

        if (!Branches.isMainBranch(env.BRANCH_NAME)) {
            steps.echo "version changes only on master. Current branch is ${env.BRANCH_NAME}"
            return pom.version
        }

        if (!pom.version.endsWith("-SNAPSHOT")) {
            steps.echo "Version '${pom.version}' does not end with -SNAPSHOT."
            return pom.version
        }

        def oldVersion = pom.version
        pom.version = pom.version.replace("-SNAPSHOT", ".${env.BUILD_NUMBER}")
        steps.sh "mvn -B --settings ${this.settingsPath} versions:set -DgenerateBackupPoms=false -DnewVersion=${pom.version}"
        steps.echo "Changed version from ${oldVersion} to ${pom.version}"
        return pom.version
    }

    def execute(String goals, String profiles = null) {
        def commandLine = 'mvn -B' << ''

        if (null != this.pubringPath) {
            commandLine << " -Dgpg.publicKeyring=${this.pubringPath}"
        }
        if (null != this.secringPath) {
            commandLine << " -Dgpg.secretKeyring=${this.secringPath}"
            steps.sh "gpg --version"
            steps.sh "gpg --batch --import '${this.secringPath}'"
        }

        if (null != this.settingsPath) {
            commandLine << " --settings ${this.settingsPath}"
        }

        if (null != profiles) {
            commandLine << " -P ${profiles}"
        }

        commandLine << " ${goals}"

        steps.sh commandLine.toString()
    }
}

