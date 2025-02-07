package org.wso2.aws.client.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.Environment;

import org.wso2.carbon.apimgt.impl.deployer.exceptions.DeployerException;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AWSAPIUtil {
    private static final Log log = LogFactory.getLog(AWSAPIUtil.class);

    public static String importRestAPI (API api, Environment environment) throws DeployerException {
        String openAPI = api.getSwaggerDefinition();
        ApiGatewayClient apiGatewayClient = null;
        String apiId = null;

        try {
            String region = environment.getAdditionalProperties().get("region");
            String accessKey = environment.getAdditionalProperties().get("access_key");
            String secretAccessKey = environment.getAdditionalProperties().get("secret_key");

            apiGatewayClient = ApiGatewayClientManager.getClient(region, accessKey, secretAccessKey);

            ImportRestApiRequest importApiRequest = ImportRestApiRequest.builder()
                    .body(SdkBytes.fromUtf8String(openAPI))
                    .failOnWarnings(false)
                    .build();

            //import rest API with the openapi definition
            ImportRestApiResponse importApiResponse = apiGatewayClient.importRestApi(importApiRequest);
            apiId = importApiResponse.id();

            //add integrations for each resource
            GetResourcesRequest getResourcesRequest = GetResourcesRequest.builder().restApiId(apiId).build();
            GetResourcesResponse getResourcesResponse = apiGatewayClient.getResources(getResourcesRequest);

            //configure authorizer
            String lambdaArn = environment.getAdditionalProperties().get("oauth2_lambda_arn");
            List<String> keyManagers = api.getKeyManagers();
            String authorizerId = "";
            if (!keyManagers.isEmpty()) {
                String keyManager = keyManagers.get(0);
                CreateAuthorizerRequest createAuthorizerRequest = CreateAuthorizerRequest.builder()
                        .restApiId(apiId)
                        .name(keyManager + "-authorizer")
                        .type(AuthorizerType.TOKEN)
                        .identitySource("method.request.header.Authorization")
                        .authorizerUri("arn:aws:apigateway:" + region + ":lambda:path/2015-03-31/functions/" + lambdaArn +
                                "/invocations")
                        .authorizerCredentials("arn:aws:iam::713881799780:role/LambdaAuthInvokeRole")
                        .build();
                CreateAuthorizerResponse createAuthorizerResponse = apiGatewayClient.createAuthorizer(createAuthorizerRequest);
                authorizerId = createAuthorizerResponse.id();
            }

            String endpointConfig = api.getEndpointConfig();
            JSONParser parser = new JSONParser();
            JSONObject endpointConfigJson = (JSONObject) parser.parse(endpointConfig);
            JSONObject prodEndpoints = (JSONObject)endpointConfigJson.get("production_endpoints");
            String productionEndpoint = (String) prodEndpoints.get("url");

            productionEndpoint = productionEndpoint.charAt(productionEndpoint.length() - 1) == '/' ?
                    productionEndpoint.substring(0, productionEndpoint.length() - 1) : productionEndpoint;

            List<Resource> resources = getResourcesResponse.items();
            for (Resource resource : resources) {
                Map<String, Method> resourceMethods = resource.resourceMethods();
                if (!resourceMethods.isEmpty()) {
                    //check and configure CORS
                    configureOptionsCallForCORS(apiId, resource, apiGatewayClient);

                    for (Map.Entry entry : resourceMethods.entrySet()) {
                        PutIntegrationRequest putIntegrationRequest = PutIntegrationRequest.builder()
                                .httpMethod(entry.getKey().toString())
                                .integrationHttpMethod(entry.getKey().toString())
                                .resourceId(resource.id())
                                .restApiId(apiId)
                                .type(IntegrationType.HTTP)
                                .uri(productionEndpoint + resource.path())
                                .build();
                        PutIntegrationResponse putIntegrationResponse =
                                apiGatewayClient.putIntegration(putIntegrationRequest);
                        String integrationURI = putIntegrationResponse.uri();

                        //Configure default output mapping
                        PutIntegrationResponseRequest putIntegrationResponseRequest =
                                PutIntegrationResponseRequest.builder()
                                .httpMethod(entry.getKey().toString())
                                .resourceId(resource.id())
                                .restApiId(apiId)
                                .statusCode("200")
                                .responseTemplates(Map.of("application/json", ""))
                                .build();
                        apiGatewayClient.putIntegrationResponse(putIntegrationResponseRequest);

                        //configure authorizer
                        UpdateMethodRequest updateMethodRequest = UpdateMethodRequest.builder().restApiId(apiId)
                                .resourceId(resource.id()).httpMethod(entry.getKey().toString())
                                .patchOperations(PatchOperation.builder().op(Op.REPLACE).path("/authorizationType")
                                        .value("CUSTOM").build(),
                                        PatchOperation.builder().op(Op.REPLACE).path("/authorizerId")
                                                .value(authorizerId).build()).build();
                        apiGatewayClient.updateMethod(updateMethodRequest);

                        //configure CORS Headers at request Method level
                        configureCORSHeadersAtMethodLevel(apiId, resource, apiGatewayClient);
                    }
                }
            }

            String stageName = environment.getAdditionalProperties().get("stage");
            CreateDeploymentRequest createDeploymentRequest = CreateDeploymentRequest.builder().restApiId(apiId)
                    .stageName(stageName).build();
            apiGatewayClient.createDeployment(createDeploymentRequest);
        } catch (Exception e) {
            try {
                GatewayUtil.rollbackDeployment(apiGatewayClient, apiId, api.getUuid(), environment.getUuid());
            } catch (APIManagementException ex) {
                throw new DeployerException("Error occurred while rolling back deployment: " + ex.getMessage());
            }
            throw new DeployerException("Error occurred while importing API: " + e.getMessage());
        }
        return apiId;
    }

    public static void reimportRestAPI(String awsApiId, API api, Environment environment) throws DeployerException {

        ApiGatewayClient apiGatewayClient = null;
        try {
            String openAPI = api.getSwaggerDefinition();

            String region = environment.getAdditionalProperties().get("region");
            String accessKey = environment.getAdditionalProperties().get("access_key");
            String secretAccessKey = environment.getAdditionalProperties().get("secret_key");
            apiGatewayClient = ApiGatewayClientManager.getClient(region, accessKey, secretAccessKey);
            PutRestApiRequest reimportApiRequest = PutRestApiRequest.builder()
                    .restApiId(awsApiId)
                    .body(SdkBytes.fromUtf8String(openAPI))
                    .failOnWarnings(false)
                    .mode(PutMode.OVERWRITE)
                    .build();
            PutRestApiResponse reimportApiResponse = apiGatewayClient.putRestApi(reimportApiRequest);

            awsApiId = reimportApiResponse.id();

            //configure authorizer
            String lambdaArn = environment.getAdditionalProperties().get("oauth2_lambda_arn");
            List<String> keyManagers = api.getKeyManagers();
            String authorizerId = "";
            if (!keyManagers.isEmpty()) {
                String keyManager = keyManagers.get(0);
                CreateAuthorizerRequest createAuthorizerRequest = CreateAuthorizerRequest.builder()
                        .restApiId(awsApiId)
                        .name(keyManager + "-authorizer")
                        .type(AuthorizerType.TOKEN)
                        .identitySource("method.request.header.Authorization")
                        .authorizerUri("arn:aws:apigateway:" + region + ":lambda:path/2015-03-31/functions/" + lambdaArn +
                                "/invocations")
                        .authorizerCredentials("arn:aws:iam::713881799780:role/LambdaAuthInvokeRole")
                        .build();
                CreateAuthorizerResponse createAuthorizerResponse = apiGatewayClient.createAuthorizer(createAuthorizerRequest);
                authorizerId = createAuthorizerResponse.id();
            }

            //add integrations for each resource
            GetResourcesRequest getResourcesRequest = GetResourcesRequest.builder()
                    .restApiId(awsApiId)
                    .build();
            GetResourcesResponse getResourcesResponse = apiGatewayClient.getResources(getResourcesRequest);

            String endpointConfig = api.getEndpointConfig();
            JSONParser parser = new JSONParser();
            JSONObject endpointConfigJson = (JSONObject) parser.parse(endpointConfig);
            JSONObject prodEndpoints = (JSONObject)endpointConfigJson.get("production_endpoints");
            String productionEndpoint = (String) prodEndpoints.get("url");

            productionEndpoint = productionEndpoint.charAt(productionEndpoint.length() - 1) == '/' ?
                    productionEndpoint.substring(0, productionEndpoint.length() - 1) : productionEndpoint;

            List<Resource> resources = getResourcesResponse.items();
            for (Resource resource : resources) {
                Map<String, Method> resourceMethods = resource.resourceMethods();
                if (!resourceMethods.isEmpty()) {
                    //check and configure CORS
                    configureOptionsCallForCORS(awsApiId, resource, apiGatewayClient);

                    for (Map.Entry entry : resourceMethods.entrySet()) {
                        PutIntegrationRequest putIntegrationRequest = PutIntegrationRequest.builder()
                                .httpMethod(entry.getKey().toString())
                                .integrationHttpMethod(entry.getKey().toString())
                                .resourceId(resource.id())
                                .restApiId(awsApiId)
                                .type(IntegrationType.HTTP)
                                .uri(productionEndpoint + resource.path())
                                .build();
                        apiGatewayClient.putIntegration(putIntegrationRequest);

                        //Configure default output mapping
                        PutIntegrationResponseRequest putIntegrationResponseRequest =
                                PutIntegrationResponseRequest.builder()
                                        .httpMethod(entry.getKey().toString())
                                        .resourceId(resource.id())
                                        .restApiId(awsApiId)
                                        .statusCode("200")
                                        .responseTemplates(Map.of("application/json", ""))
                                        .build();
                        apiGatewayClient.putIntegrationResponse(putIntegrationResponseRequest);

                        UpdateMethodRequest updateMethodRequest = UpdateMethodRequest.builder().restApiId(awsApiId)
                                .resourceId(resource.id()).httpMethod(entry.getKey().toString())
                                .patchOperations(PatchOperation.builder().op(Op.REPLACE).path("/authorizationType")
                                                .value("CUSTOM").build(),
                                        PatchOperation.builder().op(Op.REPLACE).path("/authorizerId")
                                                .value(authorizerId).build()).build();
                        apiGatewayClient.updateMethod(updateMethodRequest);

                        //configure CORS Headers at request Method level
                        configureCORSHeadersAtMethodLevel(awsApiId, resource, entry.getKey().toString(),
                                apiGatewayClient);
                    }
                }
            }

            // re-deploy API
            String stageName = environment.getAdditionalProperties().get("stage");
            CreateDeploymentRequest createDeploymentRequest = CreateDeploymentRequest.builder().restApiId(awsApiId)
                    .stageName(stageName).build();
            CreateDeploymentResponse createDeploymentResponse =
                    apiGatewayClient.createDeployment(createDeploymentRequest);
            String deploymentId = createDeploymentResponse.id();

            GetDeploymentsRequest getDeploymentsRequest = GetDeploymentsRequest.builder().restApiId(awsApiId).build();
            GetDeploymentsResponse getDeploymentsResponse = apiGatewayClient.getDeployments(getDeploymentsRequest);
            List<Deployment> deployments = getDeploymentsResponse.items();
            for (Deployment deployment : deployments) {
                if (!deployment.id().equals(deploymentId)) {
                    DeleteDeploymentRequest deleteDeploymentRequest = DeleteDeploymentRequest.builder()
                            .deploymentId(deployment.id())
                            .restApiId(awsApiId)
                            .build();
                    apiGatewayClient.deleteDeployment(deleteDeploymentRequest);
                }
            }
        } catch (Exception e) {
            throw new DeployerException("Error occurred while re-importing API: " + e.getMessage());
        }
    }

    public static boolean deleteDeployment(String apiId, Environment environment) throws DeployerException {
        try {
            String awsApiId = APIUtil.getApiAWSApiMappingByApiId(apiId, environment.getUuid());
            if (awsApiId == null) {
                throw new DeployerException("API ID is not mapped with AWS API ID");
            }

            String region = environment.getAdditionalProperties().get("region");
            String accessKey = environment.getAdditionalProperties().get("access_key");
            String secretAccessKey = environment.getAdditionalProperties().get("secret_key");
            ApiGatewayClient apiGatewayClient = ApiGatewayClientManager.getClient(region, accessKey, secretAccessKey);
            String stageName = GatewayUtil.getStageOfGatewayEnvironment(environment.getGatewayType());

            // Delete the stage before deleting the deployment
            DeleteStageRequest deleteStageRequest = DeleteStageRequest.builder()
                    .restApiId(awsApiId)
                    .stageName(stageName)
                    .build();
            apiGatewayClient.deleteStage(deleteStageRequest);

            GetDeploymentsRequest getDeploymentsRequest = GetDeploymentsRequest.builder().restApiId(awsApiId).build();
            GetDeploymentsResponse getDeploymentsResponse = apiGatewayClient.getDeployments(getDeploymentsRequest);
            List<Deployment> deployments = getDeploymentsResponse.items();
            for (Deployment deployment : deployments) {
                DeleteDeploymentRequest deleteDeploymentRequest = DeleteDeploymentRequest.builder()
                        .deploymentId(deployment.id())
                        .restApiId(awsApiId)
                        .build();
                apiGatewayClient.deleteDeployment(deleteDeploymentRequest);
            }
            return true;
        } catch (APIManagementException e) {
            throw new DeployerException("Error occurred while deleting deployment: " + e.getMessage());
        }
    }

    private static void configureOptionsCallForCORS(String apiId, Resource resource, ApiGatewayClient apiGatewayClient) {
        //configure CORS
        PutMethodRequest putMethodRequest = PutMethodRequest.builder().restApiId(apiId)
                .resourceId(resource.id()).httpMethod("OPTIONS").authorizationType("NONE")
                .apiKeyRequired(false).build();
        apiGatewayClient.putMethod(putMethodRequest);

        PutMethodResponseRequest putMethodResponseRequest = PutMethodResponseRequest.builder()
                .restApiId(apiId).resourceId(resource.id()).httpMethod("OPTIONS").statusCode("200")
                .responseModels(new HashMap<>())
                .build();
        apiGatewayClient.putMethodResponse(putMethodResponseRequest);

        PutIntegrationRequest putMethodIntegrationRequest = PutIntegrationRequest.builder()
                .restApiId(apiId).resourceId(resource.id()).httpMethod("OPTIONS")
                .integrationHttpMethod("OPTIONS").type(IntegrationType.MOCK)
                .requestTemplates(Map.of("application/json", "{\"statusCode\": 200}"))
                .build();
        apiGatewayClient.putIntegration(putMethodIntegrationRequest);

        PutIntegrationResponseRequest putIntegrationResponseRequest = PutIntegrationResponseRequest.builder()
                .restApiId(apiId).resourceId(resource.id()).httpMethod("OPTIONS").statusCode("200")
                .responseTemplates(Map.of("application/json", ""))
                .build();
        apiGatewayClient.putIntegrationResponse(putIntegrationResponseRequest);

        UpdateMethodResponseRequest updateMethodResponseRequest = UpdateMethodResponseRequest.builder()
                .restApiId(apiId).resourceId(resource.id()).httpMethod("OPTIONS").statusCode("200")
                .patchOperations(PatchOperation.builder().op(Op.ADD).path("/responseParameters/method" +
                                ".response.header.Access-Control-Allow-Origin").build(),
                        PatchOperation.builder().op(Op.ADD).path("/responseParameters/method.response" +
                                ".header.Access-Control-Allow-Methods").build(),
                        PatchOperation.builder().op(Op.ADD).path("/responseParameters/method.response" +
                                ".header.Access-Control-Allow-Headers").build()).build();
        apiGatewayClient.updateMethodResponse(updateMethodResponseRequest);

        UpdateIntegrationResponseRequest updateIntegrationResponseRequest = UpdateIntegrationResponseRequest.builder()
                .restApiId(apiId).resourceId(resource.id()).httpMethod("OPTIONS").statusCode("200")
                .patchOperations(PatchOperation.builder().op(Op.ADD).path("/responseParameters/method" +
                                ".response.header.Access-Control-Allow-Origin").value("'*'").build(),
                        PatchOperation.builder().op(Op.ADD).path("/responseParameters/method.response" +
                                ".header.Access-Control-Allow-Methods").value("'GET,OPTIONS'").build(),
                        PatchOperation.builder().op(Op.ADD).path("/responseParameters/method.response" +
                                        ".header.Access-Control-Allow-Headers")
                                .value("'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token'").build())
                .build();
        apiGatewayClient.updateIntegrationResponse(updateIntegrationResponseRequest);

        UpdateGatewayResponseRequest updateGatewayResponseRequest = UpdateGatewayResponseRequest.builder()
                .restApiId(apiId).responseType("DEFAULT_4XX")
                .patchOperations(PatchOperation.builder().op(Op.ADD).path("/responseParameters/" +
                                "gatewayresponse.header.Access-Control-Allow-Origin").value("'*'").build(),
                        PatchOperation.builder().op(Op.ADD).path("/responseParameters/gatewayresponse." +
                                "header.Access-Control-Allow-Methods").value("'GET,OPTIONS'").build(),
                        PatchOperation.builder().op(Op.ADD).path("/responseParameters/gatewayresponse." +
                                        "header.Access-Control-Allow-Headers")
                                .value("'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token'").build())
                .build();
        apiGatewayClient.updateGatewayResponse(updateGatewayResponseRequest);
    }

    private static void configureCORSHeadersAtMethodLevel(String apiId, Resource resource, String httpMethod,
                                                          ApiGatewayClient apiGatewayClient) {
        UpdateMethodResponseRequest updateMethodResponseRequest = UpdateMethodResponseRequest.builder()
                .restApiId(apiId).resourceId(resource.id()).httpMethod(httpMethod).statusCode("200")
                .patchOperations(PatchOperation.builder().op(Op.ADD).path("/responseParameters/method" +
                        ".response.header.Access-Control-Allow-Origin").build()).build();
        apiGatewayClient.updateMethodResponse(updateMethodResponseRequest);

        UpdateIntegrationResponseRequest updateIntegrationResponseRequest =
                UpdateIntegrationResponseRequest.builder()
                        .restApiId(apiId).resourceId(resource.id()).httpMethod(httpMethod).statusCode("200")
                        .patchOperations(PatchOperation.builder().op(Op.ADD).path("/responseParameters/method" +
                                ".response.header.Access-Control-Allow-Origin").value("'*'").build()).build();
        apiGatewayClient.updateIntegrationResponse(updateIntegrationResponseRequest);
    }
}
