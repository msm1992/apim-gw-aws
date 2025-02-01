package main;

import org.wso2.aws.client.AWSGatewayDeployer;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.impl.deployer.exceptions.DeployerException;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, World!");

        try {
            AWSGatewayDeployer awsGatewayDeployer = new AWSGatewayDeployer();
            awsGatewayDeployer.deploy(new API(new APIIdentifier("admin", "API", "v1")), null);
        } catch (DeployerException e) {
            e.printStackTrace();
        }
    }
}
