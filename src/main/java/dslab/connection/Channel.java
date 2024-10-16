package dslab.connection;

import dslab.connection.types.ExchangeType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.function.Consumer;

public class Channel implements IChannel {

    private final int serverPort;
    private final String serverHost;

    public Channel(String serverHost, int serverPort) {
        this.serverPort = serverPort;
        this.serverHost = serverHost;
    }

    private Socket clientSocket;
    private BufferedReader socketReader;
    private BufferedWriter socketWriter;

    @Override
    public synchronized boolean connect() throws IOException {
        this.clientSocket = new Socket(serverHost, serverPort);
        this.socketReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        this.socketWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        return this.awaitResponse("ok SMQP");
    }

    @Override
    public synchronized void disconnect() throws IOException {
        if (!this.isConnected())
            return;
        this.sendMessage("exit");
        this.socketWriter.close();
        this.socketReader.close();
        this.clientSocket.close();
    }

    @Override
    public synchronized boolean exchangeDeclare(ExchangeType exchangeType, String exchangeName) {
        if (exchangeType == null || !this.isValidString(exchangeName))
            return false;

        sendMessage("exchange " + exchangeType.name().toLowerCase() + " " + exchangeName);
        return awaitResponse("ok");
    }

    public synchronized boolean queueBind(String queueName, String bindingKey) {
        if (!this.isValidString(queueName) || !this.isValidString(bindingKey))
            return false;

        this.sendMessage("queue " + queueName);

        if (!awaitResponse("ok"))
            return false;

        this.sendMessage("bind " + bindingKey);
        return awaitResponse("ok");
    }

    @Override
    public synchronized Thread subscribe(Consumer<String> messageConsumer) {
        this.sendMessage("subscribe");
        if (!this.awaitResponse("ok")) {
            return new Thread(() -> {
                System.err.println("broker side error, press ENTER to continue.");
            });
        }
        return new Thread(() -> Stream.generate(this::getFromSubscription)
                .takeWhile(line -> !Thread.currentThread().isInterrupted())
                .filter(Objects::nonNull)
                .forEach(messageConsumer));
    }

    @Override
    public String getFromSubscription() {
        try {
            return socketReader.readLine();
        } catch (IOException e) {
            System.err.println("error while reading from socket.");
            return null;
        }
    }

    @Override
    public boolean publish(String routingKey, String message) {
        this.sendMessage("publish " + routingKey + " " + message);
        return this.awaitResponse("ok");
    }

    private boolean isConnected() {
        return this.clientSocket != null &&
                this.clientSocket.isConnected() &&
                !this.clientSocket.isClosed();
    }

    private boolean isValidString(String value) {
        return value != null && !value.isBlank();
    }

    private void sendMessage(String message) {
        try {
            this.socketWriter.write(message + "\n");
            this.socketWriter.flush();
        } catch (IOException e) {
            System.err.println("error while writing to socket.");
        }
    }

    private boolean awaitResponse(String expectedResponse) {
        try {
            String response = this.socketReader.readLine();
            if (response == null)
                return false;
            if (!response.contains(expectedResponse))
                return false;
        } catch (IOException e) {
            return false;
        }
        return true;
    }
}
