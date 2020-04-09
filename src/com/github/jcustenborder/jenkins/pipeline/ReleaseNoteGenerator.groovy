package com.github.jcustenborder.jenkins.pipeline

class ReleaseNoteGenerator implements Serializable {
    def env
    def steps

    ReleaseNoteGenerator(env, steps) {
        this.env = env
        this.steps = steps
    }

    def generate() {
        def from = "${this.env.GIT_PREVIOUS_SUCCESSFUL_COMMIT}"
        def to = "${this.env.GIT_COMMIT}"
        if ("".equals(from)) {
            from = "0000000000000000000000000000000000000000"
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
