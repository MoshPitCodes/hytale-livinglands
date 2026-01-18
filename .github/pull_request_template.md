## Description

<!-- Provide a clear and concise description of your changes -->

## Type of Change

<!-- Mark the relevant option with an 'x' -->

- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update
- [ ] Configuration change
- [ ] Refactoring (no functional changes)
- [ ] Performance improvement
- [ ] CI/CD workflow update

## Changes Made

<!-- List the specific changes in this PR -->

-
-
-

## Related Issues

<!-- Link to related issues using keywords like "Fixes #123" or "Relates to #456" -->

Fixes #
Relates to #

## Testing

<!-- Describe the testing you've performed -->

### Test Environment

- [ ] Tested on Windows 11
- [ ] Tested on WSL2 Ubuntu
- [ ] PowerShell version:
- [ ] Ansible version:

### Test Steps

1.
2.
3.

### Test Results

<!-- Describe the results of your testing -->

## Checklist

<!-- Mark completed items with an 'x' -->

### General

- [ ] My code follows the style guidelines of this project
- [ ] I have performed a self-review of my own code
- [ ] I have commented my code, particularly in hard-to-understand areas
- [ ] My changes generate no new warnings
- [ ] I have updated the documentation accordingly

### PowerShell Scripts

- [ ] Scripts pass PSScriptAnalyzer checks
- [ ] Scripts have proper error handling
- [ ] Scripts include help comments and examples
- [ ] Scripts tested on Windows 11 with PowerShell 7.5+

### Ansible Configuration

- [ ] Playbooks pass `ansible-lint` checks
- [ ] Playbooks pass syntax check (`ansible-playbook --syntax-check`)
- [ ] YAML files pass `yamllint` validation
- [ ] Roles have proper task organization and tags
- [ ] Variables are properly documented in `vars/user_environment.yml.template`
- [ ] Tested with `ansible-playbook --check` (dry run)
- [ ] Tested with full playbook execution

### Documentation

- [ ] README.md updated if needed
- [ ] QUICK_START.md updated if needed
- [ ] Inline documentation added for new features
- [ ] Markdown files pass markdownlint validation
- [ ] All links in documentation are valid
- [ ] Version numbers match `ansible/vars/user_environment.yml.template`

### GitHub Workflows

- [ ] Workflow syntax is valid
- [ ] Workflow tested (if applicable)
- [ ] Workflow permissions are minimal and appropriate

## Breaking Changes

<!-- If this PR includes breaking changes, describe them here -->

- [ ] No breaking changes

<!-- If there are breaking changes, list them: -->

## Additional Notes

<!-- Add any additional notes, concerns, or context about the PR -->

## Screenshots (if applicable)

<!-- Add screenshots to help explain your changes -->

## Reviewer Notes

<!-- Any specific areas you'd like reviewers to focus on? -->
