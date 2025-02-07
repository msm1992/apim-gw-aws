package org.wso2.aws.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.annotations.Component;
import org.wso2.aws.client.util.AWSAPIUtil;
import org.wso2.aws.client.util.GatewayUtil;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.ConfigurationDto;
import org.wso2.carbon.apimgt.api.model.Environment;
import org.wso2.carbon.apimgt.impl.deployer.ExternalGatewayDeployer;
import org.wso2.carbon.apimgt.impl.deployer.exceptions.DeployerException;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@Component(
        name = "aws.external.gateway.deployer.component",
        immediate = true,
        service = ExternalGatewayDeployer.class
)
public class AWSGatewayDeployer implements ExternalGatewayDeployer {
    private static final Log log = LogFactory.getLog(AWSAPIUtil.class);

    @Override
    public boolean deploy(API api, Environment environment) throws DeployerException {
        try {
            String awsApiId = APIUtil.getApiAWSApiMappingByApiId(api.getUuid(), environment.getUuid());
            if (awsApiId == null) {
                awsApiId = AWSAPIUtil.importRestAPI(api, environment);
                APIUtil.addApiAWSApiMapping(api.getUuid(), awsApiId, environment.getUuid());
            } else {
                AWSAPIUtil.reimportRestAPI(awsApiId, api, environment);
            }
            return true;
        } catch (APIManagementException e) {
            throw new DeployerException("Error while deploying API to AWS Gateway", e);
        }
    }

    @Override
    public boolean undeploy(String apiID, String apiName, String apiVersion, String apiContext,
                            Environment environment) throws DeployerException {

        return AWSAPIUtil.deleteDeployment(apiID, environment);
    }

    @Override
    public boolean undeployWhenRetire(API api, Environment environment) throws DeployerException {

        return AWSAPIUtil.deleteDeployment(api.getUuid(), environment);
    }

    @Override
    public List<ConfigurationDto> getConnectionConfigurations() {
        List<ConfigurationDto> configurationDtoList = new ArrayList<>();
        configurationDtoList
                .add(new ConfigurationDto("access_key", "Access Key", "input", "AWS Access Key for Signature Authentication", "", true,
                        true, Collections.emptyList(), false));
        configurationDtoList
                .add(new ConfigurationDto("secret_key", "Secret Key", "input", "AWS Secret Key for Signature Authentication", "",
                        true, true, Collections.emptyList(), false));
        configurationDtoList
                .add(new ConfigurationDto("region", "AWS Region", "input", "AWS Region", "", true, false, Collections.emptyList(), false));
        configurationDtoList
                .add(new ConfigurationDto("oauth2_lambda_arn", "OAuth2 Lambda ARN", "input", "Lambda function to " +
                        "support OAuth2", "", true, false, Collections.emptyList(), false));
        configurationDtoList.add(new ConfigurationDto("stage", "Stage Name", "input", "Default stage name", "", true,
                false,
                Collections.emptyList(), false));

        return configurationDtoList;
    }

    @Override
    public String getType() {
        return AWSConstants.AWS_TYPE;
    }

    @Override
    public String getGatewayFeatureCatalog() {
        return AWSConstants.AWS_GATEWAY_FEATURES;
    }

    @Override
    public List<String> validateApi(API api) throws DeployerException {
        List<String> errorList = new ArrayList<>();
        try {
            // Endpoint validation
            errorList.add(GatewayUtil.validateAWSAPIEndpoint(GatewayUtil.getEndpointURL(api)));
            // Check for wildcard in the resources
            errorList.add(GatewayUtil.validateResourceContexts(api));

            return errorList.stream().filter(Objects::nonNull).collect(Collectors.toList());
        } catch (DeployerException e) {
            throw new DeployerException("Error while validating API with AWS Gateway", e);
        }
    }

    @Override
    public String getAPIExecutionURL(String apiId, String url, Environment environment) throws DeployerException {
        StringBuilder resolvedUrl = new StringBuilder(url);
        try {
            String awsAPIId = APIUtil.getApiAWSApiMappingByApiId(apiId, environment.getUuid());

            //replace {apiId} placeHolder with actual API ID
            int start = resolvedUrl.indexOf("{apiId}");
            if (start != -1) {
                resolvedUrl.replace(start, start + "{apiId}".length(), awsAPIId);
            }

            //replace {region} placeHolder with actual region
            String region = environment.getAdditionalProperties().get("region");
            start = resolvedUrl.indexOf("{region}");
            if (start != -1) {
                resolvedUrl.replace(start, start + "{region}".length(), region);
            }
        } catch (APIManagementException e) {
            throw new DeployerException("Error while getting resolved API invocation URL", e);
        }
        return resolvedUrl.toString() + "/" + environment.getAdditionalProperties().get("stage");
    }
}
