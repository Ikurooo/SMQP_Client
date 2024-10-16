package dslab.cli;

import dslab.client.IClient;
import dslab.config.Config;
import dslab.connection.Channel;
import dslab.connection.Subscription;
import dslab.connection.types.ExchangeType;

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

    private Channel channel;
    private final Config config;
    private final IClient client;
    private final BufferedWriter out;
    private final BufferedReader in;

    public ClientCLI(IClient client, Config config, InputStream in, OutputStream out) {
        if (in == null)
            throw new IllegalArgumentException("InputStream cannot be null");
        if (out == null)
            throw new IllegalArgumentException("OutputStream cannot be null");

        this.config = config;
        this.client = client;
        this.out = new BufferedWriter(new OutputStreamWriter(out));
        this.in = new BufferedReader(new InputStreamReader(in));
    }

    @Override
    public void run() {
        while (true) {
            printPrompt();
            String line = readFromIn();

            if (line == null)
                continue;

            if ("shutdown".equals(line)) {
                shutdown();
                break;
            }

            List<String> arguments = List.of(line.split(" "));
            String command = arguments.get(0);

            switch (command) {
                case "channel" -> startChannel(arguments);
                case "subscribe" -> startSubscription(this::writeToOut, arguments);
                case "publish" -> publishMessage(arguments);
                default -> writeToOut("error: unknown command");
            }
        }
    }

    @Override
    public void printPrompt() {
        try {
            out.write(client.getComponentId() + "> ");
            out.flush();
        } catch (IOException e) {
            System.err.println("error: unable to write prompt.");
        }
    }

    public void shutdown() {
        try {
            if (this.channel != null)
                this.channel.disconnect();
            this.in.close();
            this.out.flush();
            this.out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startChannel(List<String> args) {
        if (args.size() != 2 || channel != null) {
            writeToOut("error: invalid channel arguments");
            return;
        }

        String brokerName = args.get(1);
        if (!config.containsKey(brokerName + ".host")) {
            writeToOut("error: broker does not exist.");
            return;
        }

        String brokerHost = config.getString(brokerName + ".host");
        int brokerPort = config.getInt(brokerName + ".port");

        this.channel = new Channel(brokerHost, brokerPort);

        try {
            this.channel.connect();
        } catch (IOException e) {
            writeToOut("error: failed to connect to broker");
        }
    }

    private void publishMessage(List<String> args) {
        if (args.size() != 5 || channel == null) {
            writeToOut("error: incorrect arguments or channel does not exist");
            return;
        }
        try {
            String exchangeName = args.get(1);
            String exchangeType = args.get(2).toUpperCase();
            String routingKey = args.get(3);
            String message = args.get(4);

            channel.exchangeDeclare(ExchangeType.valueOf(exchangeType), exchangeName);
            channel.publish(routingKey, message);

        } catch (IllegalArgumentException e) {
            writeToOut("error: unknown exchange type");
        }
    }

    private void startSubscription(Consumer<String> callback, List<String> args) {
        if (args.size() != 5 || channel == null) {
            writeToOut("error: incorrect arguments or channel does not exist");
            return;
        }

        try {
            String exchangeName = args.get(1);
            String exchangeType = args.get(2).toUpperCase();
            String queueName = args.get(3);
            String bindingKey = args.get(4);

            channel.exchangeDeclare(ExchangeType.valueOf(exchangeType), exchangeName);
            channel.queueBind(queueName, bindingKey);

            Subscription subscription = new Subscription(channel, callback);
            subscription.start();

            readFromIn(); // Wait for user input to interrupt
            subscription.interrupt();

        } catch (IllegalArgumentException e) {
            writeToOut("error: unknown exchange type");
        }
    }

    private void writeToOut(String message) {
        try {
            out.write(message + "\n");
            out.flush();
        } catch (IOException e) {
            System.err.println("error: failed to write output.");
        }
    }

    private String readFromIn() {
        try {
            return in.readLine();
        } catch (IOException e) {
            System.err.println("error: failed reading from cli.");
            return null;
        }
    }
}
