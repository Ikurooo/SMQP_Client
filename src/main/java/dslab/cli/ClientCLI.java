package dslab.cli;

import dslab.client.Client;
import dslab.client.IClient;
import dslab.config.Config;
import dslab.connection.Channel;
import dslab.connection.Subscription;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.function.Consumer;

public class ClientCLI implements IClientCLI {

    private Config config;
    private Channel channel;
    private IClient client;
    private BufferedWriter out;
    private BufferedReader in;

    public ClientCLI(IClient client, Config config, InputStream in, OutputStream out) {
        // TODO: pass in real arguments
        this.config = config;
        this.client = client;
        this.out = new BufferedWriter(new OutputStreamWriter(out));
        this.in = new BufferedReader(new InputStreamReader(in));
    }

    @Override
    public void run() {
        while (true) {
            this.printPrompt();
            String line = in.readLine();
            if (line.equals("shutdown"))
                client.shutdown();

            List<String> arguments = List.of(line.split(" "));
            String command = arguments.getFirst();

            switch (command) {
                case "channel" -> validateAndStartChannel(arguments);
                case "subscribe" -> this.startSubscription(s -> out.write(s + "\n"));

            }
        }
    }

    @Override
    public void printPrompt() {
        try {
            out.write(client.getComponentId() + "> ");
        } catch (IOException e) {
            // TODO: add proper error handling!
            e.printStackTrace();
        }
    }

    // TODO: idk if this is the best place to shut down the input output!
    public void shutdown() {
        try {
            channel.disconnect();
            in.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Subscription subscription;

    private void startSubscription(Consumer<String> callback) {
        this.subscription = new Subscription(this.channel, callback);
        this.subscription.run();
        this.in.readLine();
    }

    /**
     * Validates the channel arguments and initialises channel if arguments were
     * valid
     *
     * @param args the arguments for creating a channel (this does not include the
     *             'channel' argument itself)
     */
    private void validateAndStartChannel(List<String> args) {
        if (args.size() != 1 || this.channel != null)
            return;
        String brokerName = args.get(1);
        this.channel = this.createChannel(brokerName);
    }

    /**
     * A channel is an instance which is used to multiplex connections on a single
     * TCP connection.
     * Please read the documentation of the {@link Channel} class for more
     * information.
     * Attention should be paid if the channel is already connected to a broker, it
     * should be disconnected first.
     *
     * @param broker the broker to which the channel should be created
     * @return the channel to which a connection is established with the specified
     *         broker
     */
    private Channel createChannel(String broker) {
        if (!this.config.containsKey(broker))
            return null;
        int brokerPort = this.config.getInt(broker);
        return new Channel(broker, brokerPort);
    }
}
