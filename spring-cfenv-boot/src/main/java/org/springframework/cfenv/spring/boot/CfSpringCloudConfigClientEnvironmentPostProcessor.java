/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cfenv.spring.boot;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.config.ConfigFileApplicationListener;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.cfenv.core.CfCredentials;
import org.springframework.cfenv.core.CfEnv;
import org.springframework.cfenv.core.CfService;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

/**
 * @author Mark Pollack
 */
public class CfSpringCloudConfigClientEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered,
		ApplicationListener<ApplicationEvent> {

	public static final String SPRING_CLOUD_CONFIG_URI = "spring.cloud.config.uri";
	public static final String SPRING_CLOUD_CONFIG_OAUTH2_CLIENT_CLIENT_ID = "spring.cloud.config.client.oauth2.clientId";
	public static final String SPRING_CLOUD_CONFIG_OAUTH2_CLIENT_CLIENT_SECRET = "spring.cloud.config.client.oauth2.clientSecret";
	public static final String SPRING_CLOUD_CONFIG_OAUTH2_CLIENT_ACCESS_TOKEN_URI = "spring.cloud.config.client.oauth2.accessTokenUri";
	private static final String CONFIG_SERVER_SERVICE_TAG_NAME = "configuration";
	private static final String PROPERTY_SOURCE_NAME = "cfSpringCloudConfigClientEnvironmentPostProcessor";

	private Log logger = new DeferredLog();

	// Before ConfigFileApplicationListener so values there can use these ones
	private int order = ConfigFileApplicationListener.DEFAULT_ORDER - 1;

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {

		if (CloudPlatform.CLOUD_FOUNDRY.isActive(environment)) {

			CfEnv cfEnv = new CfEnv();
			CfService cfService;
			Map<String, Object> properties = new LinkedHashMap<>();
			try {
				cfService = cfEnv.findServiceByTag(CONFIG_SERVER_SERVICE_TAG_NAME);
			}
			catch (Exception e) {
				System.out.println("Skipping execution of CfDataSourceEnvironmentPostProcessor.");
				logger.info("Skipping execution of CfDataSourceEnvironmentPostProcessor.");
				return;
			}

			CfCredentials cfCredentials = cfService.getCredentials();
			String uri = cfCredentials.getUri();
			String clientId = cfCredentials.getString("client_id");
			String clientSecret = cfCredentials.getString("client_secret");
			String accessTokenUri = cfCredentials.getString("access_token_uri");

			properties.put(SPRING_CLOUD_CONFIG_URI, uri);
			properties.put(SPRING_CLOUD_CONFIG_OAUTH2_CLIENT_CLIENT_ID, clientId);
			properties.put(SPRING_CLOUD_CONFIG_OAUTH2_CLIENT_CLIENT_SECRET, clientSecret);
			properties.put(SPRING_CLOUD_CONFIG_OAUTH2_CLIENT_ACCESS_TOKEN_URI, accessTokenUri);
			System.out.println("Setting spring.cloud.config.client properties from bound service.");
			logger.info("Setting spring.cloud.config.client properties from bound service.");

			MutablePropertySources propertySources = environment.getPropertySources();
			if (propertySources.contains(
					CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME)) {
				propertySources.addAfter(
						CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME,
						new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
			}
			else {
				propertySources
						.addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
			}
		}
		else {
			System.out.println("Not setting spring.cloud.config.client properties, not in Cloud Foundry Environment");
			logger.debug("Not setting spring.cloud.config.client properties, not in Cloud Foundry Environment");
		}
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		this.logger = DeferredLog.replay(this.logger, LogFactory.getLog(getClass()));
	}

}
