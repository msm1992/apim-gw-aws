package org.wso2.aws.client;

public class AWSConstants {
    public static final String AWS_TYPE = "AWS";
    public static final String AWS_GATEWAY_FEATURES =
            "{\n  \"AWS\": {\n    \"apiTypes\": [\n      \"rest\"\n    ],\n    \"gatewayFeatures\": " +
            "{\n      \"basic\": [],\n      \"runtime\": [\n        \"cors\",\n        \"schemaValidation\",\n   " +
            "     \"transportsHTTP\",\n        \"transportsHTTPS\",\n        \"oauth2\",\n        " +
            "\"keyManagerConfig\"\n      ],\n      \"resources\": [\n        \"apiLevelRateLimiting\",\n        " +
            "\"operationLevelRateLimiting\"\n      ],\n      \"localScopes\": [],\n      \"monetization\": [\n   " +
            "     \"monetization\"\n      ],\n      \"subscriptions\": [],\n      \"endpoints\": [\n        " +
            "\"http\",\n        \"typePRODUCTION\",\n        \"typeSANDBOX\"\n      ]\n    }\n  }\n}";
}
