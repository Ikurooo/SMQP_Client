package dslab.connection;

import java.util.function.Consumer;

public class Subscription extends Thread {

    private IChannel channel;
    private Consumer<String> callback;

    public Subscription(IChannel channel, Consumer<String> callback) {
        this.channel = channel;
        this.callback = callback;
    }

    @Override
    public void run() {
        channel.subscribe(this.callback).run();
    }
}
