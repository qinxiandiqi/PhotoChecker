import wrap from 'word-wrap'

export default {
  prompter(cz, commit) {
    console.log('\n第一行将被裁剪为100个字符。其他行将在100个字符后自动换行。\n')

    const types = [
      { name: 'feat:     新功能', value: 'feat' },
      { name: 'fix:      修复 bug', value: 'fix' },
      { name: 'docs:     文档更新', value: 'docs' },
      { name: 'style:    代码格式调整', value: 'style' },
      { name: 'refactor: 重构', value: 'refactor' },
      { name: 'test:     增加测试', value: 'test' },
      { name: 'build:    构建相关变动', value: 'build' },
      { name: 'ci:       CI/CD 配置变动', value: 'ci' },
      { name: 'chore:    其他修改', value: 'chore' },
      { name: 'revert:   回滚', value: 'revert' },
    ]

    cz.prompt([
      {
        type: 'list',
        name: 'type',
        message: '选择你要提交的更改类型:',
        choices: types,
      },
      {
        type: 'input',
        name: 'scope',
        message: '此更改的范围是什么（例如组件或文件名，可选）:\n',
      },
      {
        type: 'input',
        name: 'subject',
        message: '写一个简短的命令式描述的更改:\n',
      },
      {
        type: 'input',
        name: 'body',
        message: '提供更详细的更改描述:\n',
      },
    ]).then(answers => {
      const maxLineWidth = 100

      const scope = answers.scope.trim()
      const scopeWithParentheses = scope ? `(${scope})` : ''

      // Format: feat: subject 或 feat(scope): subject
      const head = `${answers.type}${scopeWithParentheses}: ${answers.subject.trim()}`.slice(
        0,
        maxLineWidth
      )

      const wrapOptions = {
        trim: true,
        newline: '\n',
        indent: '',
        width: maxLineWidth,
      }

      const body = wrap(answers.body, wrapOptions)
      const footer = wrap(answers.footer, wrapOptions)

      commit(head + '\n\n' + body + '\n\n' + footer)
    })
  },
}
