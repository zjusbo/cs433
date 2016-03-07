package asyncServer;
public interface ISocketReadWriteHandlerFactory {
    public IReadWriteHandler createHandler();
}