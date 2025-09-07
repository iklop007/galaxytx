package com.galaxytx.spring;

import com.galaxytx.core.client.TcClient;
import com.galaxytx.datasource.DataSourceProxy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(GalaxyTxProperties.class)
@ConditionalOnClass(TcClient.class)
@ConditionalOnProperty(prefix = "galaxytx", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GalaxyTxAutoConfiguration {
    private final GalaxyTxProperties properties;

    public GalaxyTxAutoConfiguration(GalaxyTxProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public TcClient tcClient() throws InterruptedException {
        TcClient client = new TcClient(properties.getServer().getAddress(), properties.getServer().getPort());
        client.init();
        return client;
    }

    @Bean
    @ConditionalOnProperty(prefix = "galaxytx.datasource", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DataSource dataSource(DataSource originalDataSource) {
        return new DataSourceProxy(
                originalDataSource,
                properties.getDataSource().getResourceGroupId()
        );
    }

    @Bean
    public GlobalTransactionalInterceptor globalTransactionalInterceptor(TcClient tcClient) {
        return new GlobalTransactionalInterceptor(
                tcClient,
                properties.getTransaction().getDefaultTimeout()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public GalaxyTxConfigManager galaxyTxConfigManager() {
        return new GalaxyTxConfigManager(properties);
    }
}
