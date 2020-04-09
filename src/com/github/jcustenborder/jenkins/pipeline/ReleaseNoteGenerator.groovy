package com.github.jcustenborder.jenkins.pipeline

class ReleaseNoteGenerator implements Serializable {
    def scmResult
    def steps

    ReleaseNoteGenerator(scmResult, steps) {
        this.scmResult = scmResult
        this.steps = steps
    }

    def generate() {
        def from = "0000000000000000000000000000000000000000"
        def to = "${this.scmResult.GIT_COMMIT}"

        if ("".equals("${this.scmResult.GIT_PREVIOUS_SUCCESSFUL_COMMIT}")) {
            from = "${this.scmResult.GIT_PREVIOUS_SUCCESSFUL_COMMIT}"
        }
        
        this.steps.echo "Generating Change Log from:${from} to:${to}"
        
        def changelog = this.steps.gitChangelog returnType: 'STRING',
                from: [type: 'COMMIT', value: "${from}"],
                to: [type: 'COMMIT', value: "${to}"],
                template: """
# Changelog

{{#tags}}
## {{name}}
 {{#issues}}
  {{#hasIssue}}
   {{#hasLink}}
### {{name}} [{{issue}}]({{link}}) {{title}} {{#hasIssueType}} *{{issueType}}* {{/hasIssueType}} {{#hasLabels}} {{#labels}} *{{.}}* {{/labels}} {{/hasLabels}}
   {{/hasLink}}
   {{^hasLink}}
### {{name}} {{issue}} {{title}} {{#hasIssueType}} *{{issueType}}* {{/hasIssueType}} {{#hasLabels}} {{#labels}} *{{.}}* {{/labels}} {{/hasLabels}}
   {{/hasLink}}
  {{/hasIssue}}
  {{^hasIssue}}
### {{name}}
  {{/hasIssue}}

  {{#commits}}
**{{{messageTitle}}}**

{{#messageBodyItems}}
 * {{.}} 
{{/messageBodyItems}}

[{{hash}}](https://github.com/{{ownerName}}/{{repoName}}/commit/{{hash}}) {{authorName}} *{{commitTime}}*

  {{/commits}}

 {{/issues}}
{{/tags}}
 """
        this.steps.echo "${changelog}"
        return changelog
    }
}
