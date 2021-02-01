import * as cdk from '@aws-cdk/core';
import { XrayConstruct } from './index';

const app = new cdk.App();
const env = {
  region: process.env.CDK_DEFAULT_REGION,
  account: process.env.CDK_DEFAULT_ACCOUNT,
};

const stack = new cdk.Stack(app, 'testing-stack', { env });

new XrayConstruct(stack, 'XrayConstructCluster', {

});
