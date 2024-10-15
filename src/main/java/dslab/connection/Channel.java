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

    private int serverPort;
    private String serverHost;

    public Channel(String serverHost, int serverPort) {
        this.serverPort = serverPort;
        this.serverHost = serverHost;
    }

    private Socket clientSocket;
    private BufferedReader socketReader;
    private BufferedWriter socketWriter;

    // TODO
    @Override
    public synchronized boolean connect() throws IOException {
        this.clientSocket = new Socket(serverHost, serverPort);
        this.socketReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        this.socketWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        return true;
    }

    // TODO
    @Override
    public synchronized void disconnect() throws IOException {
        if (!this.isConnected())
            return;
        this.sendMessage("exit");
        socketWriter.flush();

        socketWriter.close();
        socketReader.close();
        clientSocket.close();
    }

    private String declaredExchangeName;
    private ExchangeType declaredExchangeType;

    // TODO
    @Override
    public synchronized boolean exchangeDeclare(ExchangeType exchangeType, String exchangeName) {
        this.declaredExchangeType = exchangeType;
        this.declaredExchangeName = exchangeName;
        sendMessage("exchange " + exchangeType.name().toLowerCase() + " " + exchangeName);
        return awaitResponse("ok");
    }

    String declaredQueueName;

    // TODO
    public synchronized boolean queueBind(String queueName, String bindingKey) {
        this.declaredQueueName = queueName;
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
                System.err.println("Error: broker side error");
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
            return null;
        }
    }

    @Override
    public boolean publish(String routingKey, String message) {
        return false;
    }

    /**
     * Checks if the socket is connected and not closed.
     *
     * @return true if connected, false otherwise.
     */
    private boolean isConnected() {
        return clientSocket != null && clientSocket.isConnected() && !clientSocket.isClosed();
    }

    private boolean isValidString(String value) {
        return value != null && !value.isBlank();
    }

    private void sendMessage(String message) {
        try {
            socketWriter.write(message + "\n");
            socketWriter.flush();
        } catch (IOException e) {
            System.out.println("error while writing to socket");
        }
    }

    private boolean awaitResponse(String expectedResponse) {
        try {
            String response = socketReader.readLine();
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
