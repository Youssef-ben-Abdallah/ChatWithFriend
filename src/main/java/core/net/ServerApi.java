package core.net;

public interface ServerApi extends AutoCloseable {
    void start() throws Exception;
    boolean isRunning();
    @Override void close();
}
