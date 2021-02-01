const { AwsCdkConstructLibrary } = require('projen');

const project = new AwsCdkConstructLibrary({
  author: 'Hari Ohm Prasath',
  authorAddress: 'harrajag@amazon.com',
  cdkVersion: '1.73.0',
  jsiiFqn: 'projen.AwsCdkConstructLibrary',
  name: 'x-ray-distributed-spring-boot',
  repositoryUrl: 'git@ssh.gitlab.aws.dev:am3-app-modernization-gsp/distributed-tracing-for-containers-using-x-ray.git',
  cdkDependencies: [
    '@aws-cdk/core',
    '@aws-cdk/aws-ec2',
    '@aws-cdk/aws-ecs',
    '@aws-cdk/aws-eks',
    '@aws-cdk/aws-iam',
    '@aws-cdk/aws-applicationautoscaling',
    '@aws-cdk/aws-cloudwatch',
    '@aws-cdk/aws-elasticloadbalancingv2',
    '@aws-cdk/aws-rds',
    '@aws-cdk/aws-secretsmanager',
    '@aws-cdk/aws-ecs-patterns',
  ],
  gitignore: [
    'cdk.out',
  ],
});

project.synth();
