package asyncServer;
public class EchoLineReadWriteHandlerFactory implements
        ISocketReadWriteHandlerFactory {
    public IReadWriteHandler createHandler() {
        return new EchoLineReadWriteHandler();
    }
}
