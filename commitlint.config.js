export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    // 允许的提交类型
    'type-enum': [
      2,
      'always',
      [
        'feat', // 新功能
        'fix', // 修复 bug
        'docs', // 文档更新
        'style', // 代码格式调整
        'refactor', // 重构
        'test', // 增加测试
        'build', // 构建相关
        'ci', // CI/CD 配置
        'chore', // 其他修改
        'revert', // 回滚
      ],
    ],
    // 类型必须小写
    'type-case': [2, 'always', 'lower-case'],
    // 类型不能为空
    'type-empty': [2, 'never'],
    // 范围必须小写
    'scope-case': [2, 'always', 'lower-case'],
    // 主题格式
    'subject-case': [2, 'never', ['sentence-case', 'start-case', 'pascal-case', 'upper-case']],
    'subject-empty': [2, 'never'],
    'subject-full-stop': [2, 'never', '.'],
    // 头部最大长度
    'header-max-length': [2, 'always', 100],
    // 正文格式
    'body-leading-blank': [1, 'always'],
    'body-max-line-length': [2, 'always', 100],
    'footer-leading-blank': [1, 'always'],
    'footer-max-line-length': [2, 'always', 100],
  },
}
