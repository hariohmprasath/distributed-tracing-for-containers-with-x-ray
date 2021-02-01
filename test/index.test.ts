import * as cdk from '@aws-cdk/core';
import { XrayConstruct } from '../src';
import '@aws-cdk/assert/jest';

test('create app', () => {
  const app = new cdk.App();
  const stack = new cdk.Stack(app);
  new XrayConstruct(stack, 'XrayTracingCluster', {});
  expect(stack).toHaveResource('AWS::ECS::Cluster');
  expect(stack).toHaveResource('AWS::ECS::TaskDefinition');
  expect(stack).toHaveResource('AWS::IAM::Policy');
  expect(stack).toHaveResource('AWS::IAM::Role');
  expect(stack).toHaveResource('AWS::ECS::Service');
  expect(stack).toHaveResource('AWS::ApplicationAutoScaling::ScalingPolicy');
  expect(stack).toHaveResource('AWS::EC2::SecurityGroup');
  expect(stack).toHaveResource('AWS::SecretsManager::Secret');
  expect(stack).toHaveResource('AWS::Logs::LogGroup');
  expect(stack).toHaveResource('AWS::RDS::DBCluster');
});