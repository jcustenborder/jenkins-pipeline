package com.github.jcustenborder.jenkins.pipeline

class ReleaseNoteGenerator implements Serializable {
    def scmResult
    def steps

    ReleaseNoteGenerator(scmResult, steps) {
        this.scmResult = scmResult
        this.steps = steps
    }

    def generate() {
        def changelog = this.steps.gitChangelog returnType: 'STRING',
                from: [type: 'COMMIT', value: "${this.scmResult.GIT_PREVIOUS_SUCCESSFUL_COMMIT}"],
                to: [type: 'COMMIT', value: "${this.scmResult.GIT_COMMIT}"],
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