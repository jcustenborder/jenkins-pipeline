package com.github.jcustenborder.jenkins.pipeline

class ConfluentConnectHub implements Serializable {
    def production
    def steps
    def env

    ConfluentConnectHub(env, steps, Boolean production) {
        this.steps = steps
        this.production = production
        this.env = env
    }

    def uploadPlugin(owner, artifactId, version) {
        this.steps.stage('publish') {
            def zipFiles = this.steps.findFiles(glob: "target/**/packages/*${artifactId}-${version}*.zip")
            def zipFile

            if (zipFiles) {
                zipFile = zipFiles[0]
            } else {
                this.steps.error("Could not find artifact that matched 'target/**/packages/*${artifactId}-${version}*.zip'")
            }

            this.steps.unzip(
                    glob: '**/manifest.json',
                    zipFile: "${zipFile.path}"
            )
            def manifests = this.steps.findFiles(glob: '**/manifest.json')

            if (manifests) {
                def manifestPath = manifests[0]

                this.steps.withAWS(credentials: 'confluent_aws', region: 'us-west-1') {
                    this.steps.withCredentials([this.steps.string(credentialsId: 'plugin_staging', variable: 'BUCKET')]) {
                        this.steps.s3Upload(
                                acl: 'Private',
                                file: "${zipFile.path}",
                                bucket: "${env.BUCKET}",
                                path: "${owner}/${artifactId}/${version}/${zipFile.name}"
                        )
                        this.steps.s3Upload(
                                acl: 'Private',
                                file: "${manifestPath.path}",
                                bucket: "${env.BUCKET}",
                                path: "${owner}/${artifactId}/${version}/manifest.json"
                        )
                    }
                }
            } else {
                this.steps.error("Could not find manifest in zip that matched '**/manifest.json'")
            }
        }
    }

}