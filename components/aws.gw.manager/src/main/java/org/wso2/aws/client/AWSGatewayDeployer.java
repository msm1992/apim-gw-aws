package org.wso2.aws.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.annotations.Component;
import org.wso2.aws.client.util.AWSAPIUtil;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.ConfigurationDto;
import org.wso2.carbon.apimgt.api.model.Environment;
import org.wso2.carbon.apimgt.impl.deployer.ExternalGatewayDeployer;
import org.wso2.carbon.apimgt.impl.deployer.exceptions.DeployerException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Component(
        name = "aws.external.gateway.deployer.component",
        immediate = true,
        service = ExternalGatewayDeployer.class
)
public class AWSGatewayDeployer implements ExternalGatewayDeployer {
    private static final Log log = LogFactory.getLog(AWSAPIUtil.class);

    @Override
    public boolean deploy(API api, Environment environment) throws DeployerException {
        // Create API in AWS'
        AWSAPIUtil.importRestAPI(api);
        log.info("API deployed in AWS....");
        return false;
    }

    @Override
    public boolean undeploy(String s, String s1, String s2, Environment environment) throws DeployerException {
        return false;
    }

    @Override
    public boolean undeployWhenRetire(API api, Environment environment) throws DeployerException {
        return false;
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
}
