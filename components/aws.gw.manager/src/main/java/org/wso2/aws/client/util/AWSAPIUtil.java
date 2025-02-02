package org.wso2.aws.client.util;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.wso2.carbon.apimgt.api.model.API;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.*;

import java.util.List;
import java.util.Map;

public class AWSAPIUtil {
    private static final Log log = LogFactory.getLog(AWSAPIUtil.class);

    public static String importRestAPI (API api) {
        String openAPI = api.getSwaggerDefinition();
        String apiId = null;
        Region region = Region.EU_NORTH_1;
        SdkHttpClient httpClient = ApacheHttpClient.builder().build();

        try (ApiGatewayClient apiGatewayClient = ApiGatewayClient.builder().httpClient(httpClient).region(region).build()) {
            ImportRestApiRequest importApiRequest = ImportRestApiRequest.builder()
                    .body(SdkBytes.fromUtf8String(openAPI))
                    .failOnWarnings(false)
                    .build();

            //import rest API with the openapi definition
            ImportRestApiResponse importApiResponse = apiGatewayClient.importRestApi(importApiRequest);
            apiId = importApiResponse.id();

            //add integrations for each resource
            GetResourcesRequest getResourcesRequest = GetResourcesRequest.builder()
                    .restApiId(apiId)
                    .build();
            GetResourcesResponse getResourcesResponse = apiGatewayClient.getResources(getResourcesRequest);

            //todo: extract endpoint from the API's endpoint configuration
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
                    for (Map.Entry entry : resourceMethods.entrySet()) {
                        PutIntegrationRequest putIntegrationRequest = PutIntegrationRequest.builder()
                                .httpMethod(entry.getKey().toString())
                                .integrationHttpMethod(entry.getKey().toString())
                                .resourceId(resource.id())
                                .restApiId(apiId)
                                .type(IntegrationType.HTTP)
                                .uri(productionEndpoint + resource.path())
                                .build();
                        PutIntegrationResponse putIntegrationResponse = apiGatewayClient.putIntegration(putIntegrationRequest);
                        String integrationURI = putIntegrationResponse.uri();

                        //Configure default output mapping
                        PutIntegrationResponseRequest putIntegrationResponseRequest = PutIntegrationResponseRequest.builder()
                                .httpMethod(entry.getKey().toString())
                                .resourceId(resource.id())
                                .restApiId(apiId)
                                .statusCode("200")
                                .responseTemplates(Map.of("application/json", ""))
                                .build();
                        apiGatewayClient.putIntegrationResponse(putIntegrationResponseRequest);
                    }
                }
            }

            //create stage and deploy API
            CreateDeploymentRequest createDeploymentRequest = CreateDeploymentRequest.builder().restApiId(apiId)
                    .stageDescription("Dev Stage").build();
            CreateDeploymentResponse createDeploymentResponse = apiGatewayClient.createDeployment(createDeploymentRequest);
            String deploymentId = createDeploymentResponse.id();

            if (StringUtils.isNotEmpty(deploymentId)) {
                CreateStageRequest createStageRequest = CreateStageRequest.builder().restApiId(apiId).stageName("dev")
                        .deploymentId(deploymentId).build();
                apiGatewayClient.createStage(createStageRequest);
            }
        } catch (Exception e) {
            log.error("Error occurred while importing API: " + e.getMessage());
        }
        return apiId;
    }
}
