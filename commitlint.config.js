// Commit message rules for this repository.
// Keep in sync with docs/git-convention.md (team baseline v1.0).
const TYPES = ['feat', 'fix', 'docs', 'style', 'refactor', 'perf', 'test', 'chore', 'revert'];

// Layered, infrastructure and engineering scopes from the team baseline,
// plus this repository's business domains.
const SCOPES = [
  // layers / components
  'controller', 'service', 'repository', 'mapper', 'entity', 'dto',
  'es', 'springai', 'embedding', 'ollama', 'security', 'cache', 'mq', 'scheduler',
  // infrastructure
  'config', 'docker', 'k8s', 'db', 'migration', 'logging', 'monitor',
  // engineering
  'deps', 'build', 'github-actions', 'gitlab-ci', 'docs', 'test', 'release',
  // platform
  'api', 'auth', 'gateway', 'bom', 'parent',
  // this repository
  'device', 'telemetry', 'work-order', 'process', 'plan', 'web', 'mobile', 'ai', 'git'
];

const CODE_TYPES = ['feat', 'fix', 'refactor', 'perf', 'test'];

module.exports = {
  extends: ['@commitlint/config-conventional'],

  ignores: [
    (message) => /^chore\(deps\): (bump|update|pin)/i.test(message),
    (message) => /^Merge (branch|pull request|remote-tracking)/i.test(message),
    (message) => /^Revert ".+"$/.test(message)
  ],

  plugins: [
    {
      rules: {
        'subject-first-word-lower': ({ subject }) => {
          if (!subject) return [true];
          const firstWord = subject.trim().split(/\s+/)[0];
          return [
            firstWord === firstWord.toLowerCase(),
            'the first word of the subject must be lower case'
          ];
        },
        'scope-required-for-code-types': ({ type, scope }) => {
          if (!CODE_TYPES.includes(type)) return [true];
          return [
            Boolean(scope && scope.trim()),
            `${type} commits must declare a scope`
          ];
        }
      }
    }
  ],

  rules: {
    'type-empty': [2, 'never'],
    'type-case': [2, 'always', 'lower-case'],
    'type-enum': [2, 'always', TYPES],

    'scope-case': [2, 'always', 'lower-case'],
    'scope-enum': [2, 'always', SCOPES],
    'scope-empty': [0],

    'subject-empty': [2, 'never'],
    'subject-full-stop': [2, 'never', '.'],
    'subject-max-length': [2, 'always', 50],
    'subject-case': [0],
    'subject-first-word-lower': [2, 'always'],
    'scope-required-for-code-types': [2, 'always'],

    'header-max-length': [2, 'always', 100],
    'header-trim': [2, 'always'],
    'body-leading-blank': [2, 'always'],
    'body-max-line-length': [2, 'always', 72],
    'footer-leading-blank': [2, 'always'],
    'footer-max-line-length': [2, 'always', 72]
  }
};
