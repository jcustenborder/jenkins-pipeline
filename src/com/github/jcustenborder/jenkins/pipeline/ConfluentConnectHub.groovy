package com.github.jcustenborder.jenkins.pipeline

class ConfluentConnectHub implements Serializable {
    def production
    def steps

    ConfluentConnectHub(steps, Boolean production) {
        this.steps = steps
        this.production = production
    }

    def upload(String owner, String artifactId, String version) {

    }


}