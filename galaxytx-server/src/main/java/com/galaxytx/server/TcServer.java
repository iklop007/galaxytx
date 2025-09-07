package com.galaxytx.server;

import com.galaxytx.core.model.BranchTransaction;
import com.galaxytx.core.protocol.MessageType;
import com.galaxytx.core.protocol.ProtocolDecoder;
import com.galaxytx.core.protocol.ProtocolEncoder;
import com.galaxytx.core.protocol.RpcMessage;
import com.galaxytx.core.model.GlobalTransaction;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcServer {
    private static final Logger logger = LoggerFactory.getLogger(TcServer.class);

    private final int port;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public TcServer(int port) {
        this.port = port;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(
                                    new ProtocolDecoder(),
                                    new ProtocolEncoder(),
                                    new ServerHandler()
                            );
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = bootstrap.bind(port).sync();
            logger.info("TC Server started on port {}", port);
            future.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    @ChannelHandler.Sharable
    private class ServerHandler extends SimpleChannelInboundHandler<RpcMessage> {
        private final TransactionCoordinator coordinator = new TransactionCoordinator();

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
            Object response = processMessage(msg);
            RpcMessage responseMsg = new RpcMessage(MessageType.RESULT, response);
            responseMsg.setId(msg.getId());
            ctx.writeAndFlush(responseMsg);
        }

        private Object processMessage(RpcMessage msg) {
            switch (msg.getMessageType()) {
                case GLOBAL_BEGIN:
                    return coordinator.beginGlobalTransaction((GlobalTransaction) msg.getBody());
                case GLOBAL_COMMIT:
                    return coordinator.commitGlobalTransaction((String) msg.getBody());
                case GLOBAL_ROLLBACK:
                    return coordinator.rollbackGlobalTransaction((String) msg.getBody());
                case BRANCH_REGISTER:
                    return coordinator.registerBranch((BranchTransaction) msg.getBody());
                default:
                    return "Unsupported message type";
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        new TcServer(8091).start();
    }
}