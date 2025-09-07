package com.galaxytx.core.client;

import com.galaxytx.core.model.BranchStatus;
import com.galaxytx.core.protocol.MessageType;
import com.galaxytx.core.protocol.ProtocolDecoder;
import com.galaxytx.core.protocol.ProtocolEncoder;
import com.galaxytx.core.protocol.RpcMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * TC客户端
 * 负责与事务协调器（TC）进行通信，管理全局事务的生命周期
 * 支持同步和异步的事务操作
 *
 * @author 刘志成
 * @date 2023/07/05
 */
public class TcClient {
    private static final Logger logger = LoggerFactory.getLogger(TcClient.class);

    private final String serverAddress;
    private final int serverPort;
    private Channel channel;
    private EventLoopGroup workerGroup;
    private final ConcurrentHashMap<Integer, CompletableFuture<Object>> pendingRequests = new ConcurrentHashMap<>();

    public TcClient(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    public void init() throws InterruptedException {
        workerGroup = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                new ProtocolDecoder(),
                                new ProtocolEncoder(),
                                new ClientHandler()
                        );
                    }
                });

        ChannelFuture future = bootstrap.connect(serverAddress, serverPort).sync();
        this.channel = future.channel();
    }

    public String beginGlobalTransaction(String applicationId, String name, long timeout) {
        Map<String, Object> params = new HashMap<>();
        params.put("applicationId", applicationId);
        params.put("transactionName", name);
        params.put("timeout", timeout);

        RpcMessage message = new RpcMessage(MessageType.GLOBAL_BEGIN, params);

        try {
            Object response = sendSyncRequest(message);
            if (response instanceof String) {
                return (String) response;
            }
        } catch (Exception e) {
            logger.error("Begin global transaction failed", e);
        }
        return null;
    }

    /**
     * 提交全局事务
     */
    public boolean commitGlobalTransaction(String xid) {
        if (xid == null || xid.trim().isEmpty()) {
            logger.warn("Cannot commit global transaction with null xid");
            return false;
        }

        RpcMessage message = new RpcMessage(MessageType.GLOBAL_COMMIT, xid);

        try {
            Object response = sendSyncRequest(message);
            if (response instanceof Boolean) {
                boolean result = (Boolean) response;
                if (result) {
                    logger.info("Global transaction committed successfully: xid={}", xid);
                } else {
                    logger.warn("Global transaction commit failed: xid={}", xid);
                }
                return result;
            } else if (response instanceof String) {
                logger.info("Global transaction commit response: {}", response);
                return "SUCCESS".equals(response);
            }
        } catch (Exception e) {
            logger.error("Commit global transaction failed: xid={}", xid, e);
        }
        return false;
    }

    /**
     * 回滚全局事务
     */
    public boolean rollbackGlobalTransaction(String xid) {
        if (xid == null || xid.trim().isEmpty()) {
            logger.warn("Cannot rollback global transaction with null xid");
            return false;
        }

        RpcMessage message = new RpcMessage(MessageType.GLOBAL_ROLLBACK, xid);

        try {
            Object response = sendSyncRequest(message);
            if (response instanceof Boolean) {
                boolean result = (Boolean) response;
                if (result) {
                    logger.info("Global transaction rolled back successfully: xid={}", xid);
                } else {
                    logger.warn("Global transaction rollback failed: xid={}", xid);
                }
                return result;
            } else if (response instanceof String) {
                logger.info("Global transaction rollback response: {}", response);
                return "SUCCESS".equals(response);
            }
        } catch (Exception e) {
            logger.error("Rollback global transaction failed: xid={}", xid, e);
        }
        return false;
    }

    /**
     * 查询全局事务状态
     */
    public String getGlobalTransactionStatus(String xid) {
        if (xid == null || xid.trim().isEmpty()) {
            return "INVALID";
        }

        RpcMessage message = new RpcMessage(MessageType.GLOBAL_STATUS, xid);

        try {
            Object response = sendSyncRequest(message);
            if (response instanceof String) {
                return (String) response;
            }
        } catch (Exception e) {
            logger.error("Get global transaction status failed: xid={}", xid, e);
        }
        return "UNKNOWN";
    }

    public boolean reportBranchStatus(long branchId, BranchStatus status) {
        Map<String, Object> params = new HashMap<>();
        params.put("branchId", branchId);
        params.put("status", status.getCode());

        RpcMessage message = new RpcMessage(MessageType.BRANCH_STATUS_REPORT, params);

        try {
            Object response = sendSyncRequest(message);
            return response instanceof Boolean && (Boolean) response;
        } catch (Exception e) {
            logger.error("Report branch status failed for branch: {}", branchId, e);
            return false;
        }
    }

    /**
     * 异步提交全局事务
     */
    public CompletableFuture<Boolean> commitGlobalTransactionAsync(String xid) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (xid == null || xid.trim().isEmpty()) {
            future.completeExceptionally(new IllegalArgumentException("XID cannot be null"));
            return future;
        }

        RpcMessage message = new RpcMessage(MessageType.GLOBAL_COMMIT, xid);

        try {
            sendAsyncRequest(message).thenAccept(response -> {
                if (response instanceof Boolean) {
                    future.complete((Boolean) response);
                } else if (response instanceof String) {
                    future.complete("SUCCESS".equals(response));
                } else {
                    future.complete(false);
                }
            }).exceptionally(ex -> {
                future.completeExceptionally(ex);
                return null;
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * 异步回滚全局事务
     */
    public CompletableFuture<Boolean> rollbackGlobalTransactionAsync(String xid) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (xid == null || xid.trim().isEmpty()) {
            future.completeExceptionally(new IllegalArgumentException("XID cannot be null"));
            return future;
        }

        RpcMessage message = new RpcMessage(MessageType.GLOBAL_ROLLBACK, xid);

        try {
            sendAsyncRequest(message).thenAccept(response -> {
                if (response instanceof Boolean) {
                    future.complete((Boolean) response);
                } else if (response instanceof String) {
                    future.complete("SUCCESS".equals(response));
                } else {
                    future.complete(false);
                }
            }).exceptionally(ex -> {
                future.completeExceptionally(ex);
                return null;
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    private Object sendSyncRequest(RpcMessage message) throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Object> future = new CompletableFuture<>();
        pendingRequests.put(message.getId(), future);

        channel.writeAndFlush(message).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                future.completeExceptionally(f.cause());
                pendingRequests.remove(message.getId());
            }
        });

        return future.get(5, TimeUnit.SECONDS);
    }

    /**
     * 发送异步请求
     */
    private CompletableFuture<Object> sendAsyncRequest(RpcMessage message) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        pendingRequests.put(message.getId(), future);

        channel.writeAndFlush(message).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                future.completeExceptionally(f.cause());
                pendingRequests.remove(message.getId());
            }
        });

        // 设置超时处理
        scheduler.schedule(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("Request timeout after 5 seconds"));
                pendingRequests.remove(message.getId());
            }
        }, 5, TimeUnit.SECONDS);

        return future;
    }

    /**
     * 关闭客户端
     */
    public void shutdown() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
        pendingRequests.clear();
        logger.info("TC Client shutdown completed");
    }

    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    /**
     * 重新连接
     */
    public void reconnect() throws InterruptedException {
        if (isConnected()) {
            return;
        }

        logger.info("Attempting to reconnect to TC server...");
        shutdown();
        init();
    }

    // 定时任务调度器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @ChannelHandler.Sharable
    private class ClientHandler extends SimpleChannelInboundHandler<RpcMessage> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
            CompletableFuture<Object> future = pendingRequests.remove(msg.getId());
            if (future != null) {
                future.complete(msg.getBody());
            } else {
                logger.warn("Received response for unknown message id: {}", msg.getId());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("TC Client handler exception", cause);
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            logger.warn("Connection to TC server lost");
            // 可以在这里触发重连逻辑
            super.channelInactive(ctx);
        }
    }
}