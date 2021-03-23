package com.github.jcustenborder.jenkins.pipeline

class Branches {
    static def isMainBranch(branch) {
        def mainBranches = ["main", "master"].toSet()
        return mainBranches.contains(branch)
    }
}
