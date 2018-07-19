/*
 *
 *   Copyright 2017 the original author or authors.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package org.springframework.data.mybatis.autoconfiguration;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.data.AbstractRepositoryConfigurationSourceSupport;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.data.mybatis.id.support.TableGeneratorConfig;
import org.springframework.data.mybatis.repository.config.EnableMybatisRepositories;
import org.springframework.data.mybatis.repository.config.MybatisAnnotationRepositoryConfigurationSource;
import org.springframework.data.mybatis.repository.config.MybatisRepositoryConfigExtension;
import org.springframework.data.repository.config.AnnotationRepositoryConfigurationSource;
import org.springframework.data.repository.config.RepositoryConfigurationDelegate;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;
import org.springframework.data.util.Streamable;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

/**
 * @author Jarvis Song
 */
@Configuration
class MybatisRepositoriesAutoConfigureRegistrar extends AbstractRepositoryConfigurationSourceSupport
		implements ResourceLoaderAware, EnvironmentAware {

	private transient static final Logger logger = LoggerFactory
			.getLogger(MybatisRepositoriesAutoConfigureRegistrar.class);

	private MybatisProperties properties;

	private ResourceLoader resourceLoader;
	private Environment environment;

	@Override
	protected Class<? extends Annotation> getAnnotation() {
		return EnableMybatisRepositories.class;
	}

	@Override
	protected Class<?> getConfiguration() {
		return EnableMybatisRepositoriesConfiguration.class;
	}

	@Override
	protected RepositoryConfigurationExtension getRepositoryConfigurationExtension() {
		return new MybatisRepositoryConfigExtension(resourceLoader);
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
		super.setResourceLoader(resourceLoader);
	}

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
		super.setEnvironment(environment);
		properties = new MybatisProperties();
		properties.setMapperLocations(
				environment.getProperty(MybatisProperties.PREFIX + ".mapper-locations", String[].class));
		properties.setRepositoriesBasePackages(
				environment.getProperty(MybatisProperties.PREFIX + ".repositories-base-packages", String[].class));
		properties.setRepositoriesBasePackagesFile(
				environment.getProperty(MybatisProperties.PREFIX + ".repositories-base-packages-file", String[].class));
	}

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		new RepositoryConfigurationDelegate(getConfigurationSource(registry), this.resourceLoader, this.environment)
				.registerRepositoriesIn(registry, getRepositoryConfigurationExtension());
	}

	private AnnotationRepositoryConfigurationSource getConfigurationSource(BeanDefinitionRegistry registry) {
		StandardAnnotationMetadata metadata = new StandardAnnotationMetadata(getConfiguration(), true);

		return new MybatisAnnotationRepositoryConfigurationSource(metadata, getAnnotation(), resourceLoader,
				this.environment, registry) {

			@Override
			public String[] getMapperLocations() {
				return MybatisRepositoriesAutoConfigureRegistrar.this.getMapperLocations();
			}

			@Override
			public Streamable<String> getBasePackages() {
				return MybatisRepositoriesAutoConfigureRegistrar.this.getBasePackages();
			}

			@Override
			public TableGeneratorConfig getTableGeneratorConfig() {
				TableGeneratorConfig defaultConfig = super.getTableGeneratorConfig();

				Integer allocationSize = environment
						.getProperty(MybatisProperties.PREFIX + ".table-generator.allocation-size", Integer.class);

				if (null != allocationSize) {
					defaultConfig.setAllocationSize(allocationSize);
				}
				String table = environment.getProperty(MybatisProperties.PREFIX + ".table-generator.table",
						String.class);

				if (null != table) {
					defaultConfig.setTable(table);
				}

				String schema = environment.getProperty(MybatisProperties.PREFIX + ".table-generator.schema",
						String.class);

				if (null != schema) {
					defaultConfig.setSchema(schema);
				}

				String catalog = environment.getProperty(MybatisProperties.PREFIX + ".table-generator.catalog",
						String.class);

				if (null != catalog) {
					defaultConfig.setCatalog(catalog);
				}

				String pkColumnName = environment
						.getProperty(MybatisProperties.PREFIX + ".table-generator.pk-column-name", String.class);

				if (null != pkColumnName) {
					defaultConfig.setPkColumnName(pkColumnName);
				}

				String valueColumnName = environment
						.getProperty(MybatisProperties.PREFIX + ".table-generator.value-column-name", String.class);

				if (null != valueColumnName) {
					defaultConfig.setValueColumnName(valueColumnName);
				}

				Integer initialValue = environment
						.getProperty(MybatisProperties.PREFIX + ".table-generator.initial-value", Integer.class);

				if (null != initialValue) {
					defaultConfig.setInitialValue(initialValue);
				}
				return defaultConfig;
			}

		};
	}

	protected String[] getMapperLocations() {
		return properties.getMapperLocations();
	}

	@Override
	protected Streamable<String> getBasePackages() {
		if ((null == properties.getRepositoriesBasePackages() || properties.getRepositoriesBasePackages().length == 0)
				&& (null == properties.getRepositoriesBasePackagesFile()
						|| properties.getRepositoriesBasePackagesFile().length == 0)) {
			return super.getBasePackages();
		}

		Set<String> basePackages = new HashSet<String>();
		if (null != properties.getRepositoriesBasePackages() && properties.getRepositoriesBasePackages().length > 0) {
			for (String s : properties.getRepositoriesBasePackages()) {
				if (StringUtils.isEmpty(s)) {
					continue;
				}
				basePackages.add(s);
			}
		}

		if (null != properties.getRepositoriesBasePackagesFile()
				&& properties.getRepositoriesBasePackagesFile().length > 0) {
			for (String s : properties.getRepositoriesBasePackagesFile()) {
				if (StringUtils.isEmpty(s)) {
					continue;
				}
				try {
					Resource[] resources = ResourcePatternUtils.getResourcePatternResolver(this.resourceLoader)
							.getResources(s);
					if (null == resources || resources.length == 0) {
						continue;
					}
					for (Resource resource : resources) {
						String ss = StreamUtils.copyToString(resource.getInputStream(), Charset.forName("UTF-8"));
						if (StringUtils.isEmpty(ss)) {
							continue;
						}
						String[] sss = ss.split("\n");
						if (null == sss || sss.length == 0) {
							continue;
						}
						for (String pack : sss) {
							if (StringUtils.isEmpty(pack)) {
								continue;
							}
							basePackages.add(StringUtils.trimAllWhitespace(pack));
						}
					}

				} catch (IOException e) {
					// ignore
					logger.warn(e.getMessage(), e);
				}

			}

		}

		return Streamable.of(basePackages);
	}

	@EnableMybatisRepositories
	private static class EnableMybatisRepositoriesConfiguration {

	}
}
