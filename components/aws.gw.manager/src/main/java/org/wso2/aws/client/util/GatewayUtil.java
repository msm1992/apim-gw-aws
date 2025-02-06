package org.wso2.aws.client.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.impl.deployer.exceptions.DeployerException;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.DeleteRestApiRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class GatewayUtil {

    private static final Pattern VALID_PATH_PATTERN = Pattern.compile("^[a-zA-Z0-9-._~%!$&'()*+,;=:@/]*$");

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

    public static String getEndpointURL(API api) throws DeployerException {

        try {
            String endpointConfig = api.getEndpointConfig();
            if (StringUtils.isEmpty(endpointConfig)) {
                return endpointConfig;
            }
            JSONParser parser = new JSONParser();
            JSONObject endpointConfigJson = null;

            endpointConfigJson = (JSONObject) parser.parse(endpointConfig);

            JSONObject prodEndpoints = (JSONObject)endpointConfigJson.get("production_endpoints");
            String productionEndpoint = (String) prodEndpoints.get("url");

            return productionEndpoint.charAt(productionEndpoint.length() - 1) == '/' ?
                    productionEndpoint.substring(0, productionEndpoint.length() - 1) : productionEndpoint;
        } catch (ParseException e) {
            throw new DeployerException("Error while parsing endpoint configuration", e);
        }
    }

    public static String validateAWSAPIEndpoint(String urlString) {
        try {
            if (StringUtils.isEmpty(urlString)) {
                return null;
            }
            URL url = new URL(urlString);

            // Validate scheme (only http and https are allowed)
            String protocol = url.getProtocol();
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                return "Invalid Endpoint URL";
            }

            // Validate host
            if (url.getHost() == null || url.getHost().isEmpty()
                    || url.getHost().equalsIgnoreCase("localhost")) {
                return "Invalid Endpoint URL";
            }

            // Validate path (no illegal characters)
            if (!VALID_PATH_PATTERN.matcher(url.getPath()).matches()) {
                return "Invalid Endpoint URL";
            }
            return null;
        } catch (MalformedURLException e) {
            return "Invalid Endpoint URL";
        }
    }

    public static String validateResourceContexts(API api) {
        String openAPI = api.getSwaggerDefinition();

        JsonObject swaggerJson = new Gson().fromJson(openAPI, JsonObject.class);
        Set<String> contexts = new HashSet<>();

        if (swaggerJson.has("paths")) {
            JsonObject paths = swaggerJson.getAsJsonObject("paths");
            for (Map.Entry<String, JsonElement> entry : paths.entrySet()) {
                contexts.add(entry.getKey());
            }
        }

        if (contexts.stream().anyMatch(context -> context.contains("*"))) {
            return "Some resource contexts contain '*' wildcard";
        }
        return null;
    }
}
