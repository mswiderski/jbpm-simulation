package org.jbpm.simulation.impl.simulators;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.drools.definition.process.Node;
import org.drools.runtime.process.NodeInstance;
import org.drools.runtime.process.ProcessInstance;
import org.jbpm.simulation.ActivitySimulator;
import org.jbpm.simulation.SimulationContext;
import org.jbpm.simulation.SimulationDataProvider;
import org.jbpm.simulation.SimulationEvent;
import org.jbpm.simulation.TimeGenerator;
import org.jbpm.simulation.TimeGeneratorFactory;
import org.jbpm.simulation.impl.events.ActivitySimulationEvent;

public class EventSimulator implements ActivitySimulator  {

    public SimulationEvent simulate(Object activity, SimulationContext context) {
        NodeInstance eventNodeInstance = (NodeInstance) activity;
        long startTime = context.getClock().getCurrentTime();
        Map<String, Object> metadata = eventNodeInstance.getNode().getMetaData();
        
        ProcessInstance pi = eventNodeInstance.getProcessInstance();
        Node node = eventNodeInstance.getNode();
        String bpmn2NodeId = (String) metadata.get("UniqueId");
        SimulationDataProvider provider = context.getDataProvider();
        
        TimeGenerator timeGenerator=TimeGeneratorFactory.newTimeGenerator(provider.getSimulationDataForNode(node));
        long duration = timeGenerator.generateTime();
        
        context.getClock().advanceTime(duration, TimeUnit.MILLISECONDS);
        // set end time for processinstance end time
        context.setMaxEndTime(context.getClock().getCurrentTime());
        return new ActivitySimulationEvent(pi.getProcessId(), pi.getId(), node.getName(), bpmn2NodeId, duration, startTime, context.getClock().getCurrentTime());
    }

}