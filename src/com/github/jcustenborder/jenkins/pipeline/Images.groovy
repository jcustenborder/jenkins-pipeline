package com.github.jcustenborder.jenkins.pipeline

class Images {
    static String getJdkImage(int image) {
        String result

        switch (image) {
            case 8:
                result = 'docker.custenborder.com/jcustenborder/jenkins-maven-jdk8:0.0.5'
                break;
            case 11:
                result = 'docker.custenborder.com/jcustenborder/jenkins-maven-jdk11:0.0.2'
                break;
            default:
                throw new Exception("${image} is not a supported Java Version")
        }

        return result
    }
}
