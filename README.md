项目结构
galaxytx/
├── galaxytx-core/           # 核心模块
│   ├── src/main/java/
│   │   └── com/galaxytx/
│   │       ├── annotation/
│   │       ├── client/
│   │       ├── common/
│   │       ├── model/
│   │       ├── protocol/
│   │       └── store/
│   └── pom.xml
├── galaxytx-spring-boot-starter/ # Spring集成
│   └── src/main/java/
│       └── com/galaxytx/spring/
├── galaxytx-server/         # TC服务器
│   └── src/main/java/
│       └── com/galaxytx/server/
├── galaxytx-datasource/     # 数据源代理
│   └── src/main/java/
│       └── com/galaxytx/datasource/
└── pom.xml

目前包含了分布式事务框架的核心组件

网络通信：基于Netty的二进制协议

AT模式支持：SQL解析、前后镜像、undo log管理

Spring集成：注解驱动、自动代理、AOP拦截

TC服务器：事务协调器基础框架

实际生产环境还需要完善：

数据库存储实现

TCC模式完整实现

超时控制与重试机制

全局锁实现

监控和管理界面

集群和高可用配置


核心组件解释：

TC (Transaction Coordinator)： 事务协调器集群。大脑，负责维护全局事务状态，驱动提交或回滚。

TM (Transaction Manager)： 事务管理器。嵌入在客户端，负责开启、提交、回滚全局事务。

RM (Resource Manager)： 资源管理器。嵌入在客户端，负责管理分支事务、注册分支、上报状态、执行指令。代理了数据源。

Client SDK： 整合了TM和RM，提供注解、API、与Spring等框架集成的逻辑。

步骤1：定义通信协议
选择：自研基于TCP的二进制协议（类似Seata）。相比HTTP/1.1，性能更高，吞吐量更大。

消息结构： Header + Body

Header： Magic Code, Version, Msg Type, Msg ID, Body Length。

Body： 序列化后的Java对象（JSON或Hessian）。

序列化： 支持多种（JSON, Hessian, Protobuf），通过配置切换。Hessian在性能和兼容性上平衡较好。

连接管理： SDK与TC集群保持长连接，心跳保活。支持故障自动重连和负载均衡。

步骤2：设计元数据存储（核心中的核心）
选择：分库分表的MySQL集群或TiDB。表结构设计：

global_table (全局事务表)

xid (varchar(128)): 全局唯一事务ID，分片键。

status (tinyint): 状态（Begin, Committed, Rollbacked, ...）。

application_id (varchar(32)): 应用名。

transaction_name (varchar(128)): 事务方法名。

timeout (int): 超时时间。

begin_time (bigint): 开始时间。

application_data (varchar(2000)): 应用数据，用于上下文传递。

branch_table (分支事务表)

branch_id (bigint): 分支事务ID，全局唯一。

xid (varchar(128)): 关联的全局事务ID。

resource_group_id (varchar(32)): 资源组（如数据源Key）。

resource_id (varchar(256)): 资源（如AT模式的表名，TCC的bean名）。

lock_key (text): AT模式全局锁的关键。记录被修改行的主键（e.g., table_name:pk1,pk2）。

status (tinyint): 分支状态。

msg (varchar(1000)): 错误信息。

undo_log (回滚日志表，AT模式专用，在每个参与事务的业务库中)

id (bigint): 自增主键。

branch_id (bigint): 分支事务ID。

xid (varchar(128)): 全局事务ID。

context (varchar(128)): 上下文（序列化后的数据）。

rollback_info (longblob): 回滚日志，序列化后的SQL反向操作（前镜像、后镜像）。

log_status (tinyint): 状态。

log_created (datetime): 创建时间。

log_modified (datetime): 修改时间。

必须建立xid和branch_id的索引。

步骤3：实现AT模式
一阶段（业务执行）：

SDK通过数据源代理拦截所有DML（Update/Insert/Delete）SQL。

SQL解析： 使用Druid SQL Parser解析SQL，得到表名、操作类型、where条件等。

查询前镜像： 根据where条件，执行 SELECT * FROM table_name WHERE ...，得到修改前的数据（Before Image）。

执行业务SQL。

查询后镜像： 根据主键（通过前镜像或SQL解析获取），查询修改后的数据（After Image）。

生成回滚日志： 根据前后镜像，生成一条反向SQL（如：前镜像是name='old'，后镜像是name='new'，则回滚SQL是UPDATE table SET name='old' WHERE pk=?）。

写入undo_log： 将前后镜像和回滚SQL信息序列化后，与业务SQL在同一个本地事务中写入本地undo_log表。

获取全局锁： 向TC注册分支事务，并上报被修改行的主键信息（作为锁Key）。TC会在branch_table中记录这些锁Key，并尝试获取全局锁（检查这些Key是否已被其他全局事务锁定）。

本地事务提交： 提交本地业务事务（此时undo_log也已持久化）。

二阶段提交：

TC异步通知所有成功的分支事务提交。

RM收到指令后，直接删除对应的undo_log记录。

本地事务异步提交，释放资源。

二阶段回滚：

TC异步通知所有成功的分支事务回滚。

RM收到指令后：

根据xid和branch_id查询本地的undo_log记录。

执行回滚日志中的反向SQL，将数据还原。

验证后镜像： 执行回滚SQL前，会校验当前数据是否与后镜像一致。如果不一致，说明有脏写，需要人工介入或触发告警。

删除undo_log记录。

释放全局锁： 回滚完成后，向TC汇报，TC释放该分支持有的全局锁。

步骤4：实现TCC模式
一阶段 Try：

业务执行@TwoPhaseBusinessAction(name="...")注解的Try方法。

RM向TC注册TCC分支事务，并上报状态为Trying。

TC将状态持久化到branch_table。

二阶段 Confirm：

如果所有Try成功，TC异步调用所有分支的Confirm方法。

Confirm方法必须是幂等的。框架通过xid和branch_id进行幂等控制。

成功后，TC更新分支状态为Committed。

二阶段 Cancel：

如果任何Try失败，TC异步调用所有已成功Try分支的Cancel方法。

Cancel方法必须是幂等的。

成功后，TC更新分支状态为Rollbacked。

解决TCC三大难题：

空回滚： Try超时未执行，Cancel先执行。在Cancel方法中检查无对应的Try日志时，直接返回成功并记录空回滚日志。

幂等： 通过xid和branch_id在业务侧建表或使用Redis判断是否已执行过。

防悬挂： Try在Cancel之后执行。在Try方法中检查，如果已有该xid和branch_id的空回滚记录，则拒绝执行（记录日志并报警）。

步骤5：高可用与性能优化
TC集群化：

使用Raft协议实现TC节点间状态同步和Leader选举。TC节点无状态，所有状态在DB中。

客户端SDK通过注册中心（如Nacos）或配置的VIP发现所有TC节点，并基于负载均衡算法（RR）连接。

异步化与批量处理：

TC写日志异步化： 收到RM上报后，TC先将日志写入内存队列，然后由批量线程合并写入数据库，极大提升吞吐量。

网络通信异步化： SDK与TC的RPC调用全部采用NIO异步模型。

锁优化：

全局锁减少持有时间： 一阶段本地事务提交后立即释放本地锁，只持有全局锁直到二阶段结束。

锁查询优化： 检查锁冲突时，在数据库中使用SELECT ... FOR UPDATE语句，保证并发安全。

步骤6：可观测性实现
Metrics (Micrometer)：

暴露galaxytx.transaction.global.count (type=success/failure)、galaxytx.transaction.duration等指标，集成Prometheus。

Tracing：

将xid和branch_id注入到SLF4J MDC（Mapped Diagnostic Context）和OpenTracing Span中，实现全链路追踪。

Logging：

输出结构化JSON日志，包含xid, branch_id, action, status等关键字段，便于ELK聚合分析。

Admin Console：

提供一个独立的管理界面，直接查询global_table和branch_table，支持按xid、状态、时间范围搜索，并支持手动触发重试或回滚异常事务。


核心库 (galaxytx-core)： 协议、模型、客户端核心逻辑。

Spring Boot Starter (galaxytx-spring-boot-starter)： 自动配置、注解支持（@GlobalTransactional）、切面逻辑。

数据源代理插件 (galaxytx-datasource)： 用于AT模式。

TC服务器 (galaxytx-server)： 可独立部署的协调器集群。

管理控制台 (galaxytx-console)： 前后端分离的Web应用，用于监控和运维。

详尽文档与最佳实践： 包括快速开始、API详解、性能调优指南、常见问题排查。

运维工具： 数据库初始化脚本、集群部署脚本（Docker/K8s Helm Chart）、监控告警配置模板。
