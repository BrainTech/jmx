package multiplexer.jmx;

import java.net.SocketAddress;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import multiplexer.Multiplexer;
import multiplexer.Multiplexer.MultiplexerMessage;
import multiplexer.Multiplexer.WelcomeMessage;
import multiplexer.constants.Peers;
import multiplexer.constants.Types;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.protobuf.ProtobufDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufEncoder;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * 
 * @author Kasia Findeisen
 * @author Piotr Findeisen
 */
class ConnectionsManager {

	private final long instanceId = new Random().nextLong();
	private final int instanceType;
	private final ClientBootstrap bootstrap;
	private final ConnectionsMap connectionsMap = new ConnectionsMap();
	private MessageReceivedListener messageReceivedListener;
	private final ChannelFutureSet channelFutureSet = new ChannelFutureSet();

	/**
	 * Constructs new ConnectionsManager with given type.
	 * 
	 * @param instanceType
	 *            the type of the peer that this {@link ConnectionsManager} will
	 *            represent
	 */
	public ConnectionsManager(final int instanceType) {
		this(instanceType, Executors.newCachedThreadPool());
	}

	/**
	 * Constructs new ConnectionsManager with given type.
	 * 
	 * @param instanceType
	 *            the type of the peer that this {@link ConnectionsManager} will
	 *            represent
	 * @param threadPool
	 *            ExecutorService that will be used to create boss and worker
	 *            threads for handling {@code java.nio}
	 */
	public ConnectionsManager(final int instanceType, ExecutorService threadPool) {
		this(instanceType, threadPool, threadPool);
	}

	/**
	 * Constructs new ConnectionsManager with given type.
	 * 
	 * @param instanceType
	 *            the type of the peer that this {@link ConnectionsManager} will
	 *            represent
	 * @param bossExecutor
	 *            {@link ExecutorService} that will be used to create boss
	 *            thread
	 * @param workerExecutor
	 *            {@link ExecutorService} that will be used to create I/O worker
	 *            threads; it must be able to create at least a thread per CPU
	 *            core
	 */
	public ConnectionsManager(final int instanceType, Executor bossExecutor,
		Executor workerExecutor) {

		this.instanceType = instanceType;
		ChannelFactory channelFactory = new NioClientSocketChannelFactory(
			bossExecutor, workerExecutor);
		bootstrap = new ClientBootstrap(channelFactory);
		bootstrap.setOption("tcpNoDelay", true);
		bootstrap.setOption("keepAlive", true);

		ChannelPipelineFactory pipelineFactory = new ChannelPipelineFactory() {
			// Encoders
			private RawMessageCodecs.RawMessageEncoder rawMessageEncoder = new RawMessageCodecs.RawMessageEncoder();
			private ProtobufEncoder multiplexerMessageEncoder = new ProtobufEncoder();
			// Decoders
			private ProtobufDecoder multiplexerMessageDecoder = new ProtobufDecoder(
				Multiplexer.MultiplexerMessage.getDefaultInstance());
			// Protocol handler
			private MultiplexerProtocolHandler multiplexerProtocolHandler = new MultiplexerProtocolHandler(
				ConnectionsManager.this);

			@Override
			public ChannelPipeline getPipeline() throws Exception {
				ChannelPipeline pipeline = Channels.pipeline();
				// Encoders
				pipeline.addLast("rawMessageEncoder", rawMessageEncoder);
				pipeline.addLast("multiplexerMessageEncoder",
					multiplexerMessageEncoder);

				// Decoders
				pipeline.addLast("rawMessageDecoder",
					new RawMessageCodecs.RawMessageFrameDecoder());
				pipeline.addLast("multiplexerMessageDecoder",
					multiplexerMessageDecoder);

				// Protocol handler
				pipeline.addLast("multiplexerProtocolHandler",
					multiplexerProtocolHandler);
				return pipeline;
			}
		};

		bootstrap.setPipelineFactory(pipelineFactory);

	}

	public MultiplexerMessage.Builder createMessageBuilder() {
		return initializeMessageBuilder(MultiplexerMessage.newBuilder());
	}

	private MultiplexerMessage.Builder initializeMessageBuilder(
		MultiplexerMessage.Builder message) {
		return message.setId(new Random().nextLong()).setFrom(instanceId)
			.setTimestamp((int) (System.currentTimeMillis() / 1000));
	}

	public MultiplexerMessage createMessage(ByteString message, int type) {
		return createMessageBuilder().setMessage(message).setType(type).build(); 
	}

	public MultiplexerMessage createMessage(MultiplexerMessage.Builder message) {
		return initializeMessageBuilder(message).build();
	}

	public ChannelFuture asyncConnect(SocketAddress address) {
		ChannelFuture connectOperation = bootstrap.connect(address);
		connectOperation.addListener(new ChannelFutureListener() {

			@Override
			public void operationComplete(ChannelFuture future)
				throws Exception {
				assert future.isDone();
				// TODO zrobić, żeby user, gdy dostanie future, nie mógł zrobić
				// getChannel
				SocketChannel channel = (SocketChannel) future.getChannel();
				assert channel != null;
				connectionsMap.addNew(channel);

				// send out welcome message
				System.out.println("sending welcome message"); // TODO
				ByteString message = WelcomeMessage.newBuilder().setType(
					instanceType).setId(instanceId).build().toByteString();
				sendMessage(createMessage(message, Types.CONNECTION_WELCOME),
					channel);
			}
		});
		return connectOperation;
	}

	public void messageReceived(MultiplexerMessage message, Channel channel) {
		if (message.getType() == Types.CONNECTION_WELCOME) {
			ByteString msg = message.getMessage();
			WelcomeMessage welcome;
			try {
				welcome = WelcomeMessage.newBuilder().mergeFrom(msg).build();
				connectionsMap.add(channel, message.getFrom(), welcome
					.getType());
			} catch (InvalidProtocolBufferException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// TODO Nie akceptować drugiego WELCOME na tym channelu
		} else if (message.getType() == Types.HEARTBIT) {
		} else {
			if (messageReceivedListener != null) {
				messageReceivedListener.onMessageReceived(message,
					new Connection(channel));
			} else {
				System.err.println("Unhandled message\n" + message);
			}
		}
	}

	public MessageReceivedListener getMessageReceivedListener() {
		return messageReceivedListener;
	}

	public void setMessageReceivedListener(
		MessageReceivedListener messageReceivedListener) {
		if (messageReceivedListener == null) {
			throw new NullPointerException("messageReceivedListener");
		}
		this.messageReceivedListener = messageReceivedListener;
	}

	private ChannelFuture sendMessage(MultiplexerMessage message,
		Channel channel) {
		ChannelFuture cf = channel.write(message);
		channelFutureSet.add(cf);
		return cf;
	}

	public ChannelFutureGroup sendMessage(MultiplexerMessage message,
		SendingMethod method) {
		if (method == SendingMethod.THROUGH_ONE) {
			Channel channel = connectionsMap.getAny(Peers.MULTIPLEXER);
			return new ChannelFutureGroup(sendMessage(message, channel));
		} else if (method == SendingMethod.THROUGH_ALL) {
			Iterator<Channel> channels = connectionsMap
				.getAll(Peers.MULTIPLEXER);
			Channel channel;
			ChannelFutureGroup channelFutureGroup = new ChannelFutureGroup();
			while (channels.hasNext()) {
				channel = channels.next();
				channelFutureGroup.add(sendMessage(message, channel));
			}
			return channelFutureGroup;
		} else {
			return new ChannelFutureGroup(sendMessage(message, method
				.getConnection().getChannel()));
		}
	}

	public void flushAll() throws InterruptedException {
		copyActiveChannelFutures().await();
	}

	public boolean flushAll(long timeout, TimeUnit unit)
		throws InterruptedException {
		return copyActiveChannelFutures().await(timeout, unit);

	}

	public boolean flushAll(long timeoutMillis) throws InterruptedException {
		return copyActiveChannelFutures().await(timeoutMillis);

	}

	private ChannelFutureGroup copyActiveChannelFutures() {
		synchronized (channelFutureSet) {
			return new ChannelFutureGroup(channelFutureSet);
		}
	}

}