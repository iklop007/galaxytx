package com.galaxytx.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * GalaxyTX 分布式事务配置属性
 *
 * @author 刘志成
 * @date 2023/09/07
 */
@ConfigurationProperties(prefix = "galaxytx")
public class GalaxyTxProperties {

    /**
     * 是否启用GalaxyTX分布式事务
     */
    private boolean enabled = true;

    /**
     * 应用ID（用于标识服务）
     */
    private String applicationId;

    /**
     * 事务服务组（用于逻辑分组）
     */
    private String txServiceGroup = "default";

    /**
     * TC服务器配置
     */
    @NestedConfigurationProperty
    private ServerConfig server = new ServerConfig();

    /**
     * 客户端配置
     */
    @NestedConfigurationProperty
    private ClientConfig client = new ClientConfig();

    /**
     * 事务配置
     */
    @NestedConfigurationProperty
    private TransactionConfig transaction = new TransactionConfig();

    /**
     * 数据源代理配置
     */
    @NestedConfigurationProperty
    private DataSourceConfig dataSource = new DataSourceConfig();

    /**
     * 重试配置
     */
    @NestedConfigurationProperty
    private RetryConfig retry = new RetryConfig();

    /**
     * 监控配置
     */
    @NestedConfigurationProperty
    private MonitorConfig monitor = new MonitorConfig();

    // Getter和Setter方法
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }

    public String getTxServiceGroup() { return txServiceGroup; }
    public void setTxServiceGroup(String txServiceGroup) { this.txServiceGroup = txServiceGroup; }

    public ServerConfig getServer() { return server; }
    public void setServer(ServerConfig server) { this.server = server; }

    public ClientConfig getClient() { return client; }
    public void setClient(ClientConfig client) { this.client = client; }

    public TransactionConfig getTransaction() { return transaction; }
    public void setTransaction(TransactionConfig transaction) { this.transaction = transaction; }

    public DataSourceConfig getDataSource() { return dataSource; }
    public void setDataSource(DataSourceConfig dataSource) { this.dataSource = dataSource; }

    public RetryConfig getRetry() { return retry; }
    public void setRetry(RetryConfig retry) { this.retry = retry; }

    public MonitorConfig getMonitor() { return monitor; }
    public void setMonitor(MonitorConfig monitor) { this.monitor = monitor; }

    /**
     * TC服务器配置
     */
    public static class ServerConfig {
        /**
         * TC服务器地址
         */
        private String address = "127.0.0.1";

        /**
         * TC服务器端口
         */
        private int port = 8091;

        /**
         * TC服务器集群节点（格式：ip1:port1,ip2:port2）
         */
        private String clusterNodes;

        /**
         * 服务器工作线程数
         */
        private int workerThreads = Runtime.getRuntime().availableProcessors() * 2;

        /**
         * 服务器boss线程数
         */
        private int bossThreads = 1;

        /**
         * 服务器空闲连接超时时间（毫秒）
         */
        private int idleTimeout = 60000;

        /**
         * 服务器请求超时时间（毫秒）
         */
        private int requestTimeout = 3000;

        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public String getClusterNodes() { return clusterNodes; }
        public void setClusterNodes(String clusterNodes) { this.clusterNodes = clusterNodes; }

        public int getWorkerThreads() { return workerThreads; }
        public void setWorkerThreads(int workerThreads) { this.workerThreads = workerThreads; }

        public int getBossThreads() { return bossThreads; }
        public void setBossThreads(int bossThreads) { this.bossThreads = bossThreads; }

        public int getIdleTimeout() { return idleTimeout; }
        public void setIdleTimeout(int idleTimeout) { this.idleTimeout = idleTimeout; }

        public int getRequestTimeout() { return requestTimeout; }
        public void setRequestTimeout(int requestTimeout) { this.requestTimeout = requestTimeout; }
    }

    /**
     * 客户端配置
     */
    public static class ClientConfig {
        /**
         * 客户端重连间隔（毫秒）
         */
        private int reconnectInterval = 1000;

        /**
         * 客户端最大重连次数
         */
        private int maxReconnectAttempts = 10;

        /**
         * 客户端连接超时时间（毫秒）
         */
        private int connectTimeout = 3000;

        /**
         * 客户端请求超时时间（毫秒）
         */
        private int requestTimeout = 5000;

        /**
         * 客户端心跳间隔（毫秒）
         */
        private int heartbeatInterval = 30000;

        /**
         * 客户端负载均衡策略（random, round_robin）
         */
        private String loadBalanceType = "round_robin";

        /**
         * 客户端是否启用故障转移
         */
        private boolean failoverEnabled = true;

        /**
         * 客户端是否启用粘性连接
         */
        private boolean sticky = true;

        public int getReconnectInterval() { return reconnectInterval; }
        public void setReconnectInterval(int reconnectInterval) { this.reconnectInterval = reconnectInterval; }

        public int getMaxReconnectAttempts() { return maxReconnectAttempts; }
        public void setMaxReconnectAttempts(int maxReconnectAttempts) { this.maxReconnectAttempts = maxReconnectAttempts; }

        public int getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }

        public int getRequestTimeout() { return requestTimeout; }
        public void setRequestTimeout(int requestTimeout) { this.requestTimeout = requestTimeout; }

        public int getHeartbeatInterval() { return heartbeatInterval; }
        public void setHeartbeatInterval(int heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; }

        public String getLoadBalanceType() { return loadBalanceType; }
        public void setLoadBalanceType(String loadBalanceType) { this.loadBalanceType = loadBalanceType; }

        public boolean isFailoverEnabled() { return failoverEnabled; }
        public void setFailoverEnabled(boolean failoverEnabled) { this.failoverEnabled = failoverEnabled; }

        public boolean isSticky() { return sticky; }
        public void setSticky(boolean sticky) { this.sticky = sticky; }
    }

    /**
     * 事务配置
     */
    public static class TransactionConfig {
        /**
         * 全局事务默认超时时间（毫秒）
         */
        private int defaultTimeout = 60000;

        /**
         * 全局事务最大超时时间（毫秒）
         */
        private int maxTimeout = 300000;

        /**
         * 分支事务默认超时时间（毫秒）
         */
        private int branchTimeout = 30000;

        /**
         * 事务模式（AT, TCC, XA, AUTO）
         */
        private String mode = "AT";

        /**
         * 是否启用全局锁
         */
        private boolean globalLockEnabled = true;

        /**
         * 全局锁超时时间（毫秒）
         */
        private int lockTimeout = 10000;

        /**
         * 全局锁重试间隔（毫秒）
         */
        private int lockRetryInterval = 10;

        /**
         * 全局锁最大重试次数
         */
        private int lockMaxRetries = 30;

        /**
         * 是否启用快速失败
         */
        private boolean failFastEnabled = true;

        /**
         * 是否启用事务防悬挂
         */
        private boolean antiSuspendEnabled = true;

        public int getDefaultTimeout() { return defaultTimeout; }
        public void setDefaultTimeout(int defaultTimeout) { this.defaultTimeout = defaultTimeout; }

        public int getMaxTimeout() { return maxTimeout; }
        public void setMaxTimeout(int maxTimeout) { this.maxTimeout = maxTimeout; }

        public int getBranchTimeout() { return branchTimeout; }
        public void setBranchTimeout(int branchTimeout) { this.branchTimeout = branchTimeout; }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public boolean isGlobalLockEnabled() { return globalLockEnabled; }
        public void setGlobalLockEnabled(boolean globalLockEnabled) { this.globalLockEnabled = globalLockEnabled; }

        public int getLockTimeout() { return lockTimeout; }
        public void setLockTimeout(int lockTimeout) { this.lockTimeout = lockTimeout; }

        public int getLockRetryInterval() { return lockRetryInterval; }
        public void setLockRetryInterval(int lockRetryInterval) { this.lockRetryInterval = lockRetryInterval; }

        public int getLockMaxRetries() { return lockMaxRetries; }
        public void setLockMaxRetries(int lockMaxRetries) { this.lockMaxRetries = lockMaxRetries; }

        public boolean isFailFastEnabled() { return failFastEnabled; }
        public void setFailFastEnabled(boolean failFastEnabled) { this.failFastEnabled = failFastEnabled; }

        public boolean isAntiSuspendEnabled() { return antiSuspendEnabled; }
        public void setAntiSuspendEnabled(boolean antiSuspendEnabled) { this.antiSuspendEnabled = antiSuspendEnabled; }
    }

    /**
     * 数据源代理配置
     */
    public static class DataSourceConfig {
        /**
         * 是否启用数据源代理
         */
        private boolean enabled = true;

        /**
         * 数据源代理模式（ALL, XA, AT）
         */
        private String mode = "ALL";

        /**
         * 资源组ID
         */
        private String resourceGroupId = "default";

        /**
         * 是否自动代理数据源
         */
        private boolean autoProxy = true;

        /**
         * 代理的数据源bean名称模式
         */
        private String dataSourcePattern = ".*DataSource";

        /**
         * 排除的数据源bean名称模式
         */
        private String excludeDataSourcePattern;

        /**
         * 是否启用SQL解析缓存
         */
        private boolean sqlParseCacheEnabled = true;

        /**
         * SQL解析缓存大小
         */
        private int sqlParseCacheSize = 1024;

        /**
         * 是否启用连接池监控
         */
        private boolean connectionPoolMonitorEnabled = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public String getResourceGroupId() { return resourceGroupId; }
        public void setResourceGroupId(String resourceGroupId) { this.resourceGroupId = resourceGroupId; }

        public boolean isAutoProxy() { return autoProxy; }
        public void setAutoProxy(boolean autoProxy) { this.autoProxy = autoProxy; }

        public String getDataSourcePattern() { return dataSourcePattern; }
        public void setDataSourcePattern(String dataSourcePattern) { this.dataSourcePattern = dataSourcePattern; }

        public String getExcludeDataSourcePattern() { return excludeDataSourcePattern; }
        public void setExcludeDataSourcePattern(String excludeDataSourcePattern) { this.excludeDataSourcePattern = excludeDataSourcePattern; }

        public boolean isSqlParseCacheEnabled() { return sqlParseCacheEnabled; }
        public void setSqlParseCacheEnabled(boolean sqlParseCacheEnabled) { this.sqlParseCacheEnabled = sqlParseCacheEnabled; }

        public int getSqlParseCacheSize() { return sqlParseCacheSize; }
        public void setSqlParseCacheSize(int sqlParseCacheSize) { this.sqlParseCacheSize = sqlParseCacheSize; }

        public boolean isConnectionPoolMonitorEnabled() { return connectionPoolMonitorEnabled; }
        public void setConnectionPoolMonitorEnabled(boolean connectionPoolMonitorEnabled) { this.connectionPoolMonitorEnabled = connectionPoolMonitorEnabled; }
    }

    /**
     * 重试配置
     */
    public static class RetryConfig {
        /**
         * 是否启用重试机制
         */
        private boolean enabled = true;

        /**
         * 最大重试次数
         */
        private int maxAttempts = 3;

        /**
         * 重试初始间隔（毫秒）
         */
        private int initialInterval = 1000;

        /**
         * 重试间隔乘数
         */
        private double multiplier = 1.5;

        /**
         * 最大重试间隔（毫秒）
         */
        private int maxInterval = 10000;

        /**
         * 是否启用指数退避
         */
        private boolean exponentialBackoff = true;

        /**
         * 重试随机因子
         */
        private double randomFactor = 0.2;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

        public int getInitialInterval() { return initialInterval; }
        public void setInitialInterval(int initialInterval) { this.initialInterval = initialInterval; }

        public double getMultiplier() { return multiplier; }
        public void setMultiplier(double multiplier) { this.multiplier = multiplier; }

        public int getMaxInterval() { return maxInterval; }
        public void setMaxInterval(int maxInterval) { this.maxInterval = maxInterval; }

        public boolean isExponentialBackoff() { return exponentialBackoff; }
        public void setExponentialBackoff(boolean exponentialBackoff) { this.exponentialBackoff = exponentialBackoff; }

        public double getRandomFactor() { return randomFactor; }
        public void setRandomFactor(double randomFactor) { this.randomFactor = randomFactor; }
    }

    /**
     * 监控配置
     */
    public static class MonitorConfig {
        /**
         * 是否启用监控
         */
        private boolean enabled = true;

        /**
         * 监控数据上报间隔（毫秒）
         */
        private int reportInterval = 60000;

        /**
         * 是否启用事务指标收集
         */
        private boolean metricsEnabled = true;

        /**
         * 是否启用事务日志记录
         */
        private boolean loggingEnabled = true;

        /**
         * 是否启用分布式追踪
         */
        private boolean tracingEnabled = true;

        /**
         * 监控数据存储方式（MEMORY, DB, LOG）
         */
        private String storageType = "MEMORY";

        /**
         * 监控数据保留时间（小时）
         */
        private int retentionHours = 72;

        /**
         * 是否启用慢事务告警
         */
        private boolean slowTxAlarmEnabled = true;

        /**
         * 慢事务阈值（毫秒）
         */
        private int slowTxThreshold = 5000;

        /**
         * 是否启用异常事务告警
         */
        private boolean errorTxAlarmEnabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getReportInterval() { return reportInterval; }
        public void setReportInterval(int reportInterval) { this.reportInterval = reportInterval; }

        public boolean isMetricsEnabled() { return metricsEnabled; }
        public void setMetricsEnabled(boolean metricsEnabled) { this.metricsEnabled = metricsEnabled; }

        public boolean isLoggingEnabled() { return loggingEnabled; }
        public void setLoggingEnabled(boolean loggingEnabled) { this.loggingEnabled = loggingEnabled; }

        public boolean isTracingEnabled() { return tracingEnabled; }
        public void setTracingEnabled(boolean tracingEnabled) { this.tracingEnabled = tracingEnabled; }

        public String getStorageType() { return storageType; }
        public void setStorageType(String storageType) { this.storageType = storageType; }

        public int getRetentionHours() { return retentionHours; }
        public void setRetentionHours(int retentionHours) { this.retentionHours = retentionHours; }

        public boolean isSlowTxAlarmEnabled() { return slowTxAlarmEnabled; }
        public void setSlowTxAlarmEnabled(boolean slowTxAlarmEnabled) { this.slowTxAlarmEnabled = slowTxAlarmEnabled; }

        public int getSlowTxThreshold() { return slowTxThreshold; }
        public void setSlowTxThreshold(int slowTxThreshold) { this.slowTxThreshold = slowTxThreshold; }

        public boolean isErrorTxAlarmEnabled() { return errorTxAlarmEnabled; }
        public void setErrorTxAlarmEnabled(boolean errorTxAlarmEnabled) { this.errorTxAlarmEnabled = errorTxAlarmEnabled; }
    }
}