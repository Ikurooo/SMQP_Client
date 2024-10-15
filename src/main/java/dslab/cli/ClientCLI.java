package dslab.cli;

import dslab.client.IClient;
import dslab.config.Config;
import dslab.connection.Channel;
import dslab.connection.Subscription;
import dslab.connection.types.ExchangeType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOError;
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
        try {
            while (true) {
                this.printPrompt();
                String line = in.readLine();
                if (line == null)
                    continue;
                if (line.equals("shutdown")) {
                    this.shutdown();
                    break;
                }
                List<String> arguments = List.of(line.split(" "));
                String command = arguments.getFirst();

                switch (command) {
                    case "channel" -> this.validateAndStartChannel(arguments);
                    case "subscribe" -> this.startSubscription(s -> writeToOut(s), arguments);
                    default -> this.writeToOut("error: unknown command");

                }
            }
        } catch (IOException e) {
        }
    }

    @Override
    public void printPrompt() {
        try {
            this.out.write("%s> ".formatted(client.getComponentId()));
            this.out.flush();
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
            client.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    Subscription subscription;

    private void startSubscription(Consumer<String> callback, List<String> args) {
        if (args.size() != 5 || this.channel == null) {
            this.writeToOut("error");
            return;
        }
        try {
            String exchangeName = args.get(1);
            ExchangeType exchangeType = ExchangeType.valueOf(args.get(2).toUpperCase());
            String queueName = args.get(3);
            String bindingKey = args.get(4);

            this.channel.exchangeDeclare(exchangeType, exchangeName);
            this.channel.queueBind(queueName, bindingKey);
            this.subscription = new Subscription(this.channel, callback);
            this.subscription.start();

            this.in.readLine();
            // TODO: send exit message to broker
            this.subscription.interrupt();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            this.writeToOut("error: unknown exchange type");
        }
    }

    private void writeToOut(String message) {
        try {
            this.out.write(message + "\n");
        } catch (IOException e) {
            System.out.println("error");
        }
    }

    /**
     * Validates the channel arguments and initialises channel if arguments were
     * valid
     *
     * @param args the arguments for creating a channel
     *
     */
    private void validateAndStartChannel(List<String> args) {
        if (args.size() != 2 || this.channel != null) {
            writeToOut("error: invalid channel arguments");
            return;
        }
        String brokerName = args.getLast();
        this.channel = this.createChannel(brokerName);
        try {
            this.channel.connect();
        } catch (IOException e) {
            this.writeToOut("error: failed to connect to broker");
        }
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
        if (!this.config.containsKey(broker + ".host")) {
            this.writeToOut("error: broker does not exist.");
            return null;
        }
        String brokerHost = this.config.getString(broker + ".host");
        int brokerPort = this.config.getInt(broker + ".port");
        return new Channel(brokerHost, brokerPort);
    }
}
