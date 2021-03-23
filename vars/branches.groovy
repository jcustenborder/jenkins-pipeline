def isMainBranch(branch) {
    Set mainBranches = ["main", "master"]
    return mainBranches.contains(branch)
}