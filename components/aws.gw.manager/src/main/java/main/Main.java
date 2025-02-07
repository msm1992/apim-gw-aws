package main;

import org.wso2.aws.client.AWSGatewayDeployer;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.impl.deployer.exceptions.DeployerException;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.PutMethodRequest;
import software.amazon.awssdk.services.apigateway.model.UpdateMethodRequest;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, World!");

//        try {
//
//            ApiGatewayClient client = ApiGatewayClientManager.getClient("us-east-1", "AKIA2MNVLZRSO7M7GI4Y", "macLRKB5vwQqWFK/yy2JzDAKy+udyilsINTRPd/C");
//
////            UpdateMethodRequest updateMethodRequest = UpdateMethodRequest.builder()
////                    .httpMethod("GET")
////                    .resourceId("abc123")
////                    .restApiId("def456")
////                    .patchOperations(null)
////                    .build();
////
////            PutMethodRequest putMethodRequest = PutMethodRequest.builder()
////                    .httpMethod("GET")
////                    .resourceId("abc123")
////                    .restApiId("def456")
////                    .authorizationType("
//        } catch (DeployerException e) {
//            e.printStackTrace();
//        }
    }
}
