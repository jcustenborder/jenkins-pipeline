package com.github.jcustenborder.jenkins.pipeline

class MavenUtilities implements Serializable {
    def steps
    def settings;

    MavenUtilities(steps, settings) { this.steps = steps; this.settings = settings }

    def shouldChangeVersion() {
        return env.BRANCH_NAME == 'master'
    }

    def changeVersion(String version) {
        if (!shouldChangeVersion()) {
            return
        }

        pom = steps.readMavenPom file: 'pom.xml'
        def oldVersion = pom.version
        def matcher = (pom.version =~ /-SNAPSHOT$/)

        if (!matcher.find()) {
            return
        }

        pom.version = matcher.replaceFirst(".${version}")

        steps.echo "Changed pom version from ${oldVersion} to ${pom.version}"

        writeMavenPom model: pom
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

