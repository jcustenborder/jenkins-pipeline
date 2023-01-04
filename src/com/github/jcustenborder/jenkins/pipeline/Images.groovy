package com.github.jcustenborder.jenkins.pipeline

class Images {
    static String getJdkImage(version) {
        String result

        switch (version) {
            case 8:
                result = 'docker.custenborder.com/jcustenborder/jenkins-maven-jdk8:latest'
                break;
            case 11:
                result = 'docker.custenborder.com/jcustenborder/jenkins-maven-jdk11:latest'
                break;
            default:
                result = 'docker.custenborder.com/jcustenborder/jenkins-maven-jdk8:latest'
                break;
        }

        return result
    }
}
