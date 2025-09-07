package com.galaxytx.spring;

import com.galaxytx.core.client.TcClient;
import com.galaxytx.core.common.TransactionContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlobalTransactionalInterceptor implements MethodInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(GlobalTransactionalInterceptor.class);

    private final TcClient tcClient;

    /**
     * 全局事务默认超时时间（毫秒）
     */
    private int defaultTimeout;

    public GlobalTransactionalInterceptor(TcClient tcClient,int defaultTimeout) {
        this.tcClient = tcClient;
        this.defaultTimeout = defaultTimeout;
    }

    public GlobalTransactionalInterceptor(TcClient tcClient) {
        this.tcClient = tcClient;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String xid = tcClient.beginGlobalTransaction(
                "my-app",
                invocation.getMethod().getName(),
                60000
        );

        TransactionContext.bind(xid);

        try {
            Object result = invocation.proceed();
            tcClient.commitGlobalTransaction(xid);
            return result;
        } catch (Exception e) {
            tcClient.rollbackGlobalTransaction(xid);
            throw e;
        } finally {
            TransactionContext.unbind();
        }
    }
}