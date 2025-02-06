package org.wso2.aws.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.annotations.Component;
import org.wso2.aws.client.util.AWSAPIUtil;
import org.wso2.aws.client.util.GatewatUtil;
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
                .add(new ConfigurationDto("service_name", "AWS Service Name", "input", "AWS Service Name", "", true, false, Collections.emptyList(), false));
        configurationDtoList
                .add(new ConfigurationDto("api_url", "Management API URL", "input", "Management API URL", "", true, false, Collections.emptyList(), false));
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
            errorList.add(GatewatUtil.validateAWSAPIEndpoint(GatewatUtil.getEndpointURL(api)));
            // Check for wildcard in the resources
            errorList.add(GatewatUtil.validateResourceContexts(api));

            return errorList.stream().filter(Objects::nonNull).collect(Collectors.toList());
        } catch (DeployerException e) {
            throw new DeployerException("Error while validating API with AWS Gateway", e);
        }
    }
}
