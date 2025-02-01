package org.wso2.aws.client.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.ImportRestApiRequest;
import software.amazon.awssdk.services.apigateway.model.ImportRestApiResponse;
import software.amazon.awssdk.services.apigatewayv2.ApiGatewayV2Client;
import software.amazon.awssdk.services.apigatewayv2.model.*;

public class AWSAPIUtil {
    private static final Log log = LogFactory.getLog(AWSAPIUtil.class);

    public static String importRestAPI (String openAPI) {
        String apiId = null;
        Region region = Region.EU_NORTH_1;
        SdkHttpClient httpClient = ApacheHttpClient.builder().build();

        try (ApiGatewayClient apiGatewayClient = ApiGatewayClient.builder().httpClient(httpClient).region(region).build()) {
            ImportRestApiRequest importApiRequest = ImportRestApiRequest.builder()
                    .body(SdkBytes.fromUtf8String(openAPI))
                    .failOnWarnings(false)
                    .build();
            ImportRestApiResponse importApiResponse = apiGatewayClient.importRestApi(importApiRequest);

            // Output the API ID and API URL
            apiId = importApiResponse.id();
            log.info("[CUSTOM WORKFLOW EXECUTOR] API Imported Successfully!");
            log.info("[CUSTOM WORKFLOW EXECUTOR] API ID: " + apiId);
            log.info("[CUSTOM WORKFLOW EXECUTOR] API Endpoint configuration: " +
                    importApiResponse.endpointConfiguration());
        } catch (Exception e) {
            log.error("Error occurred while importing API: " + e.getMessage());
        }
        return apiId;
    }
}
