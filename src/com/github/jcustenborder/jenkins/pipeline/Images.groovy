package com.github.jcustenborder.jenkins.pipeline

class Images {
    static String getJdkImage(version) {
        String result

        switch (version) {
            case 8:
                result = 'docker.custenborder.com/jcustenborder/jenkins-maven-jdk8:0.0.5'
                break;
            case 11:
                result = 'docker.custenborder.com/jcustenborder/jenkins-maven-jdk11:0.0.2'
                break;
            default:
                result = 'docker.custenborder.com/jcustenborder/jenkins-maven-jdk8:0.0.5'
                break;
        }

        return result
    }
}
