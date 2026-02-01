package core.net;

public interface LogSink {
    void log(String line);

    static LogSink stdout() {
        return System.out::println;
    }
}
