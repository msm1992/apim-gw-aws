package org.wso2.aws.client.util;

import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.DeleteRestApiRequest;

public class GatewatUtil {

    public static String getStageOfGatewayEnvironment(String gatewayType) {
        // Get the current stage of the API Gateway from the configs
        return "dev";
    }

    public static void rollbackDeployment(ApiGatewayClient apiGatewayClient, String awsApiId, String apiId,
                                          String environmentId) throws APIManagementException {
        if (apiGatewayClient != null && awsApiId != null) {
            //delete the API if an error occurred
            DeleteRestApiRequest deleteRestApiRequest = DeleteRestApiRequest.builder().restApiId(awsApiId).build();
            apiGatewayClient.deleteRestApi(deleteRestApiRequest);

            APIUtil.deleteApiAWSApiMapping(apiId, environmentId);
        }
    }
}
