package org.activiti.cloud.examples.connectors;

import java.util.HashMap;
import java.util.Map;

import org.activiti.cloud.connectors.starter.channels.IntegrationResultSender;
import org.activiti.cloud.connectors.starter.configuration.ConnectorProperties;
import org.activiti.cloud.connectors.starter.model.IntegrationResultBuilder;
import org.activiti.cloud.api.process.model.IntegrationRequest;
import org.activiti.cloud.api.process.model.IntegrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import static net.logstash.logback.marker.Markers.append;

@Component
@EnableBinding(ExampleConnectorChannels.class)
public class ExampleConnector {

    private final Logger logger = LoggerFactory.getLogger(ExampleConnector.class);

    @Value("${spring.application.name}")
    private String appName;

    //just a convenience - not recommended in real implementations
    private String var1Copy = "";

    //just a convenience - not recommended in real implementations
    private Map<String, Object> inBoundVariables = new HashMap<>();

    @Autowired
    private ConnectorProperties connectorProperties;

    private final IntegrationResultSender integrationResultSender;

    public ExampleConnector(IntegrationResultSender integrationResultSender) {

        this.integrationResultSender = integrationResultSender;
    }

    /**
     * For acceptance test purpose, it expects only one variable in the message
     *
     **/
    @StreamListener(value = ExampleConnectorChannels.EXAMPLE_CONNECTOR_ACTION_CONSUMER)
    public void performTaskFromConnectorAction(IntegrationRequest event) throws InterruptedException {
        logger.info(append("service-name",
                appName),
                ">>> " + ExampleConnector.class.getSimpleName()+" was called for instance " + event.getIntegrationContext().getProcessInstanceId());

        Map<String,Object> outBoundVariables = new HashMap<>();

        for (Map.Entry<String, Object> entry : event.getIntegrationContext().getInBoundVariables().entrySet()) {
            logger.info("Item : " + entry.getKey() + " Object : " + entry.getValue());
            inBoundVariables.put(entry.getKey(), entry.getValue());
        }

        //matching outbound variable
        outBoundVariables.put("output-variable-name-1","output-variable-name-1");
        //not matching outbound variable
        outBoundVariables.put("output-no-match","output-no-match");

        sendOutBoundMessage(event, outBoundVariables);
    }

    @StreamListener(value = ExampleConnectorChannels.EXAMPLE_CONNECTOR_CONSUMER)
    public void performTaskFromConnector(IntegrationRequest event) throws InterruptedException {

        logger.info(append("service-name",
                           appName),
                    ">>> In example-cloud-connector");

        String var1 = ExampleConnector.class.getSimpleName()+" was called for instance " + event.getIntegrationContext().getProcessInstanceId();

        var1Copy = String.valueOf(var1);

        Map<String, Object> results = new HashMap<>();
        results.put("var1",
                    var1);
        sendOutBoundMessage(event, results);
    }

    private void sendOutBoundMessage(IntegrationRequest event, Map<String, Object> outBoundVariables){
        Message<IntegrationResult> message = IntegrationResultBuilder.resultFor(event, connectorProperties)
                .withOutboundVariables(outBoundVariables)
                .buildMessage();
        integrationResultSender.send(message);
    }

    public String getVar1Copy() {
        return var1Copy;
    }

    public Map<String, Object> getInBoundVariables() {
        return inBoundVariables;
    }
}
