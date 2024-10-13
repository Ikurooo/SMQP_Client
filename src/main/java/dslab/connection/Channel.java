package dslab.connection;

import dslab.connection.types.ExchangeType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.function.Consumer;

public class Channel implements IChannel {

    private int serverPort;
    private String serverHost;
    private Set<String> performedOperations;

    public Channel(String serverHost, int serverPort) {
        this.serverPort = serverPort;
        this.serverHost = serverHost;
        this.performedOperations = new HashSet<>();
    }

    private Socket clientSocket;
    private BufferedReader socketReader;
    private BufferedWriter socketWriter;

    @Override
    public synchronized boolean connect() throws IOException {
        if (this.isConnected())
            return false;
        this.clientSocket = new Socket(serverHost, serverPort);
        this.socketReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        this.socketWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        return true;
    }

    @Override
    public synchronized void disconnect() throws IOException {
        if (!this.isConnected())
            return;

        socketWriter.flush();

        socketWriter.close();
        socketReader.close();
        clientSocket.close();
    }

    private String declaredExchangeName;
    private ExchangeType declaredExchangeType;

    @Override
    public synchronized boolean exchangeDeclare(ExchangeType exchangeType, String exchangeName) {
        if (!this.operationsMatchExpected(this.performedOperations, 0, List.of()))
            return false;
        if (exchangeName == null || exchangeName.isBlank() || exchangeType == null)
            return false;
        this.declaredExchangeType = exchangeType;
        this.declaredExchangeName = exchangeName;
        this.performedOperations.add("exchange");
        try {
            socketWriter.write(this.buildExchangeRequest(exchangeName, exchangeType));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    private String declaredQueueName;

    public synchronized boolean declareQueue(String queueName) {
        if (!this.operationsMatchExpected(this.performedOperations, 1, List.of("exchange")))
            return false;
        if (!this.isValidString(queueName))
            return false;
        this.declaredQueueName = queueName;
        this.performedOperations.add("queue");
        return true;
    }

    private String queueBindingKey;

    @Override
    public synchronized boolean queueBind(String queueName, String bindingKey) {
        if (!this.operationsMatchExpected(this.performedOperations, 2, List.of("exchange", "queue")))
            return false;
        if (!this.isValidString(bindingKey) || !this.isValidString(queueName))
            return false;
        this.queueBindingKey = bindingKey;
        this.performedOperations.add("bind");

        return true;
    }

    @Override
    public Thread subscribe(Consumer<String> messageConsumer) {
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

    private String buildExchangeRequest(String exchangeName, ExchangeType exchangeType) {
        return new StringBuilder()
                .append("exchange ")
                .append(exchangeType.name().toLowerCase())
                .append(" ")
                .append(exchangeName)
                .toString();
    }

    /**
     * Checks if all operaations are present and that those are the only operations
     * performed so far.
     *
     * @param performedOperations a set of operations that have already been
     *                            performed.
     * @param numberOfOperations  the number of operations against which you wish to
     *                            check.
     * @param operations          the operations against which we wisht to check.
     */
    private boolean operationsMatchExpected(Set<String> performedOperations, int numberOfOperations,
            List<String> expectedOperations) {
        return expectedOperations.containsAll(performedOperations) &&
                expectedOperations.size() == numberOfOperations;
    }

    private boolean isValidString(String value) {
        return value != null && !value.isBlank();
    }
}
