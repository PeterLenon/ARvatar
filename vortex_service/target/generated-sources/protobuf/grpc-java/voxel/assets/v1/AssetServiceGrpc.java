package voxel.assets.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.58.0)",
    comments = "Source: voxel/asset/v1/asset_service.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class AssetServiceGrpc {

  private AssetServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "voxel.assets.v1.AssetService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsRequest,
      voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsResponse> getListPointCloudsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListPointClouds",
      requestType = voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsRequest.class,
      responseType = voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsRequest,
      voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsResponse> getListPointCloudsMethod() {
    io.grpc.MethodDescriptor<voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsRequest, voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsResponse> getListPointCloudsMethod;
    if ((getListPointCloudsMethod = AssetServiceGrpc.getListPointCloudsMethod) == null) {
      synchronized (AssetServiceGrpc.class) {
        if ((getListPointCloudsMethod = AssetServiceGrpc.getListPointCloudsMethod) == null) {
          AssetServiceGrpc.getListPointCloudsMethod = getListPointCloudsMethod =
              io.grpc.MethodDescriptor.<voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsRequest, voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListPointClouds"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AssetServiceMethodDescriptorSupplier("ListPointClouds"))
              .build();
        }
      }
    }
    return getListPointCloudsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<voxel.assets.v1.AssetServiceOuterClass.GetPointCloudRequest,
      voxel.assets.v1.AssetServiceOuterClass.GetPointCloudResponse> getGetPointCloudMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetPointCloud",
      requestType = voxel.assets.v1.AssetServiceOuterClass.GetPointCloudRequest.class,
      responseType = voxel.assets.v1.AssetServiceOuterClass.GetPointCloudResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<voxel.assets.v1.AssetServiceOuterClass.GetPointCloudRequest,
      voxel.assets.v1.AssetServiceOuterClass.GetPointCloudResponse> getGetPointCloudMethod() {
    io.grpc.MethodDescriptor<voxel.assets.v1.AssetServiceOuterClass.GetPointCloudRequest, voxel.assets.v1.AssetServiceOuterClass.GetPointCloudResponse> getGetPointCloudMethod;
    if ((getGetPointCloudMethod = AssetServiceGrpc.getGetPointCloudMethod) == null) {
      synchronized (AssetServiceGrpc.class) {
        if ((getGetPointCloudMethod = AssetServiceGrpc.getGetPointCloudMethod) == null) {
          AssetServiceGrpc.getGetPointCloudMethod = getGetPointCloudMethod =
              io.grpc.MethodDescriptor.<voxel.assets.v1.AssetServiceOuterClass.GetPointCloudRequest, voxel.assets.v1.AssetServiceOuterClass.GetPointCloudResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetPointCloud"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  voxel.assets.v1.AssetServiceOuterClass.GetPointCloudRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  voxel.assets.v1.AssetServiceOuterClass.GetPointCloudResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AssetServiceMethodDescriptorSupplier("GetPointCloud"))
              .build();
        }
      }
    }
    return getGetPointCloudMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static AssetServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AssetServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AssetServiceStub>() {
        @java.lang.Override
        public AssetServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AssetServiceStub(channel, callOptions);
        }
      };
    return AssetServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static AssetServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AssetServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AssetServiceBlockingStub>() {
        @java.lang.Override
        public AssetServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AssetServiceBlockingStub(channel, callOptions);
        }
      };
    return AssetServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static AssetServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AssetServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AssetServiceFutureStub>() {
        @java.lang.Override
        public AssetServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AssetServiceFutureStub(channel, callOptions);
        }
      };
    return AssetServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     * <pre>
     * Enumerate available point clouds (neutral, expressions, lighting variants, etc.)
     * </pre>
     */
    default void listPointClouds(voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsRequest request,
        io.grpc.stub.StreamObserver<voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListPointCloudsMethod(), responseObserver);
    }

    /**
     * <pre>
     * Fetch the binary point cloud (and optional mesh) for a guru/variant combination
     * </pre>
     */
    default void getPointCloud(voxel.assets.v1.AssetServiceOuterClass.GetPointCloudRequest request,
        io.grpc.stub.StreamObserver<voxel.assets.v1.AssetServiceOuterClass.GetPointCloudResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetPointCloudMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service AssetService.
   */
  public static abstract class AssetServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return AssetServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service AssetService.
   */
  public static final class AssetServiceStub
      extends io.grpc.stub.AbstractAsyncStub<AssetServiceStub> {
    private AssetServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AssetServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AssetServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Enumerate available point clouds (neutral, expressions, lighting variants, etc.)
     * </pre>
     */
    public void listPointClouds(voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsRequest request,
        io.grpc.stub.StreamObserver<voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListPointCloudsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Fetch the binary point cloud (and optional mesh) for a guru/variant combination
     * </pre>
     */
    public void getPointCloud(voxel.assets.v1.AssetServiceOuterClass.GetPointCloudRequest request,
        io.grpc.stub.StreamObserver<voxel.assets.v1.AssetServiceOuterClass.GetPointCloudResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetPointCloudMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service AssetService.
   */
  public static final class AssetServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<AssetServiceBlockingStub> {
    private AssetServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AssetServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AssetServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Enumerate available point clouds (neutral, expressions, lighting variants, etc.)
     * </pre>
     */
    public voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsResponse listPointClouds(voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListPointCloudsMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Fetch the binary point cloud (and optional mesh) for a guru/variant combination
     * </pre>
     */
    public voxel.assets.v1.AssetServiceOuterClass.GetPointCloudResponse getPointCloud(voxel.assets.v1.AssetServiceOuterClass.GetPointCloudRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetPointCloudMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service AssetService.
   */
  public static final class AssetServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<AssetServiceFutureStub> {
    private AssetServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AssetServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AssetServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Enumerate available point clouds (neutral, expressions, lighting variants, etc.)
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsResponse> listPointClouds(
        voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListPointCloudsMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Fetch the binary point cloud (and optional mesh) for a guru/variant combination
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<voxel.assets.v1.AssetServiceOuterClass.GetPointCloudResponse> getPointCloud(
        voxel.assets.v1.AssetServiceOuterClass.GetPointCloudRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetPointCloudMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_LIST_POINT_CLOUDS = 0;
  private static final int METHODID_GET_POINT_CLOUD = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_LIST_POINT_CLOUDS:
          serviceImpl.listPointClouds((voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsRequest) request,
              (io.grpc.stub.StreamObserver<voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsResponse>) responseObserver);
          break;
        case METHODID_GET_POINT_CLOUD:
          serviceImpl.getPointCloud((voxel.assets.v1.AssetServiceOuterClass.GetPointCloudRequest) request,
              (io.grpc.stub.StreamObserver<voxel.assets.v1.AssetServiceOuterClass.GetPointCloudResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getListPointCloudsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsRequest,
              voxel.assets.v1.AssetServiceOuterClass.ListPointCloudsResponse>(
                service, METHODID_LIST_POINT_CLOUDS)))
        .addMethod(
          getGetPointCloudMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              voxel.assets.v1.AssetServiceOuterClass.GetPointCloudRequest,
              voxel.assets.v1.AssetServiceOuterClass.GetPointCloudResponse>(
                service, METHODID_GET_POINT_CLOUD)))
        .build();
  }

  private static abstract class AssetServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    AssetServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return voxel.assets.v1.AssetServiceOuterClass.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("AssetService");
    }
  }

  private static final class AssetServiceFileDescriptorSupplier
      extends AssetServiceBaseDescriptorSupplier {
    AssetServiceFileDescriptorSupplier() {}
  }

  private static final class AssetServiceMethodDescriptorSupplier
      extends AssetServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    AssetServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (AssetServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new AssetServiceFileDescriptorSupplier())
              .addMethod(getListPointCloudsMethod())
              .addMethod(getGetPointCloudMethod())
              .build();
        }
      }
    }
    return result;
  }
}
