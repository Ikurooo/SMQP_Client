package dslab.client;

import dslab.ComponentFactory;
import dslab.cli.ClientCLI;
import dslab.config.Config;

import java.io.InputStream;
import java.io.OutputStream;

public class Client implements IClient {

    private String componentId;
    private ClientCLI cli;
    private InputStream in;
    private OutputStream out;

    public Client(String componentId, InputStream in, OutputStream out) {
        this.componentId = componentId;
        this.in = in;
        this.out = out;
    }

    @Override
    public void run() {
        this.cli = new ClientCLI(this, new Config(this.componentId + ".properties"), this.in, this.out);
        this.cli.run();
    }

    @Override
    public String getComponentId() {
        return this.componentId;
    }

    @Override
    public void shutdown() {
        
    }

    /**
     * Creates a new {@link Client} and runs it.
     * Standard input, output and error streams are passed to the client which are
     * then used by the {@link ClientCLI}.
     *
     * @param args the client config filename found in classpath resources without
     *             the file extension
     */
    public static void main(String[] args) {
        ComponentFactory.createClient(args[0], System.in, System.out).run();
    }
}
