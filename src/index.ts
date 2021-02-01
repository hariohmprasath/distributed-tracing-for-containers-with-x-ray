import * as path from 'path';
import * as ec2 from '@aws-cdk/aws-ec2';
import * as ecs from '@aws-cdk/aws-ecs';
import * as ecsPatterns from '@aws-cdk/aws-ecs-patterns';
import * as iam from '@aws-cdk/aws-iam';
import * as rds from '@aws-cdk/aws-rds';
import * as cdk from '@aws-cdk/core';


// Customizable construct inputs
export interface XrayConstructProps {
  // VPC
  readonly vpc?: ec2.IVpc;
}

export class XrayConstruct extends cdk.Construct {
  readonly vpc: ec2.IVpc;

  constructor(scope: cdk.Construct, id: string, props: XrayConstructProps = {}) {
    super(scope, id);

    this.vpc = props.vpc ?? new ec2.Vpc(this, 'xray-scaling-Vpc', { natGateways: 1 });

    // Custom security group
    var securityGroup = new ec2.SecurityGroup(this, 'xray-security-group', {
      vpc: this.vpc,
      allowAllOutbound: true,
    });

    // Allow inbound port 3306 (Mysql), 80 (Load balancer), 2000 (X-ray UDP)
    securityGroup.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(3306), 'Port 3306 for inbound traffic from IPv4');
    securityGroup.addIngressRule(ec2.Peer.anyIpv6(), ec2.Port.tcp(3306), 'Port 3306 for inbound traffic from IPv6');
    securityGroup.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(80), 'Port 80 for inbound traffic from IPv4');
    securityGroup.addIngressRule(ec2.Peer.anyIpv6(), ec2.Port.tcp(80), 'Port 80 for inbound traffic from IPv6');
    securityGroup.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(2000), 'Port 2000 for inbound traffic from IPv4');
    securityGroup.addIngressRule(ec2.Peer.anyIpv6(), ec2.Port.tcp(2000), 'Port 2000 for inbound traffic from IPv6');

    // Cluster
    const cluster = new ecs.Cluster(this, 'cluster', {
      vpc: this.vpc,
      containerInsights: true,
    });

    // RDS Aurora MySQL (with data API enabled)
    const db = new rds.ServerlessCluster(this, 'Db', {
      engine: rds.DatabaseClusterEngine.AURORA_MYSQL,
      vpc: this.vpc,
      enableDataApi: true,
      securityGroups: [securityGroup],
      scaling: {
        minCapacity: rds.AuroraCapacityUnit.ACU_8,
        maxCapacity: rds.AuroraCapacityUnit.ACU_32,
      },
      credentials: rds.Credentials.fromGeneratedSecret('syscdk'),
    });

    // Setup capacity providers
    const cfnEcsCluster = cluster.node.defaultChild as ecs.CfnCluster;
    cfnEcsCluster.capacityProviders = ['FARGATE', 'FARGATE_SPOT'];

    // IAM role for ECS tasks
    const ecsFargateTaskRole = new iam.Role(this, 'x-ray-task-execution-role', {
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonRDSFullAccess'),
        iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonEC2ContainerRegistryFullAccess'),
        iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonDynamoDBFullAccess'),
        iam.ManagedPolicy.fromAwsManagedPolicyName('CloudWatchLogsFullAccess'),
        iam.ManagedPolicy.fromAwsManagedPolicyName('AWSXrayFullAccess'),
      ],
    });

    // Task definition
    const ecsTaskDefinition = new ecs.FargateTaskDefinition(this, 'x-ray-task', {
      taskRole: ecsFargateTaskRole,
      executionRole: ecsFargateTaskRole,
      memoryLimitMiB: 4096,
      cpu: 2048,
    });

    // Default container (Main application)
    const containerDefinition = ecsTaskDefinition.addContainer('x-ray-main-container', {
      image: ecs.ContainerImage.fromAsset(path.resolve(
        __dirname,
        'xray-hit-counter',
      )),
      environment: {
        RDS_HOSTNAME: db.clusterEndpoint.hostname,
        RDS_PASSWORD: db.secret?.secretValueFromJson('password')?.toString()!,
        RDS_USERNAME: db.secret?.secretValueFromJson('username')?.toString()!,
      },
      essential: true,
      logging: new ecs.AwsLogDriver({
        streamPrefix: 'x-ray-task-logs',
      }),
    });

    // Port mapping
    containerDefinition.addPortMappings({
      containerPort: 80,
      hostPort: 80,
      protocol: ecs.Protocol.TCP,
    });

    // Xray Demon container
    const xrayDefinition = ecsTaskDefinition.addContainer('x-ray-demon-container', {
      image: ecs.ContainerImage.fromAsset(path.resolve(
        __dirname,
        'xray-demon',
      )),
      essential: true,
      logging: new ecs.AwsLogDriver({
        streamPrefix: 'x-ray-demon-logs',
      }),
      cpu: 32,
    });

    // Xray Port mapping
    xrayDefinition.addPortMappings({
      containerPort: 2000,
      hostPort: 2000,
      protocol: ecs.Protocol.UDP,
    });

    // ALB for Fargate service
    const loadBalancedFargateService = new ecsPatterns.ApplicationLoadBalancedFargateService(this, 'x-ray-service', {
      cluster,
      taskDefinition: ecsTaskDefinition,
      securityGroups: [securityGroup],
      publicLoadBalancer: true,
    });

    // Health check pointing to spring actuator
    loadBalancedFargateService.targetGroup.configureHealthCheck({
      path: '/health',
    });

    // Autoscaling with min=2, max=5 instance scaled based on CPU Utilization
    const scalableTarget = loadBalancedFargateService.service.autoScaleTaskCount({
      minCapacity: 2,
      maxCapacity: 5,
    });

    scalableTarget.scaleOnCpuUtilization('x-ray-scale-on-cpu', {
      targetUtilizationPercent: 80,
    });

    // Output -> Load balancer DNS
    new cdk.CfnOutput(this, 'x-ray-loadbalancer', {
      exportName: 'Load-balancer',
      value: loadBalancedFargateService.loadBalancer.loadBalancerDnsName,
    });

    // RDS endpoint
    new cdk.CfnOutput(this, 'x-ray-rds', {
      exportName: 'RDS-balancer',
      value: db.clusterEndpoint.hostname,
    });
  }
}