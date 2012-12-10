package org.jbpm.simulation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.drools.KnowledgeBase;
import org.drools.builder.KnowledgeBuilder;
import org.drools.builder.ResourceType;
import org.drools.command.World;
import org.drools.command.runtime.rule.InsertElementsCommand;
import org.drools.fluent.simulation.SimulationFluent;
import org.drools.fluent.simulation.impl.DefaultSimulationFluent;
import org.drools.io.ResourceFactory;
import org.drools.runtime.StatefulKnowledgeSession;
import org.jbpm.simulation.converter.SimulationFilterPathFormatConverter;
import org.jbpm.simulation.impl.BPMN2SimulationDataProvider;
import org.jbpm.simulation.impl.SimulateProcessPathCommand;
import org.jbpm.simulation.impl.SimulationPath;
import org.jbpm.simulation.impl.WorkingMemorySimulationRepository;
import org.jbpm.simulation.impl.events.ActivitySimulationEvent;
import org.jbpm.simulation.impl.events.AggregatedEndEventSimulationEvent;
import org.jbpm.simulation.impl.events.AggregatedProcessSimulationEvent;
import org.jbpm.simulation.impl.events.EndSimulationEvent;
import org.jbpm.simulation.impl.events.GenericSimulationEvent;
import org.jbpm.simulation.impl.events.HumanTaskActivitySimulationEvent;
import org.jbpm.simulation.impl.events.ProcessInstanceEndSimulationEvent;
import org.junit.Test;

public class SimulateProcessTest {

    @Test
    public void testSimpleExclusiveGatewayTest() {
        
        PathFinder finder = PathFinderFactory.getInstance(this.getClass().getResourceAsStream("/BPMN-SimpleExclusiveGatewayProcess.bpmn2"));
        
        List<SimulationPath> paths = finder.findPaths(new SimulationFilterPathFormatConverter());
        assertEquals(2, paths.size());
        
        SimulationContext context = SimulationContextFactory.newContext(new BPMN2SimulationDataProvider(this.getClass().getResourceAsStream("/BPMN-SimpleExclusiveGatewayProcess.bpmn2")));
        
        
        SimulationDataProvider provider = context.getDataProvider();
        
        SimulationFluent f = new DefaultSimulationFluent();
        // @formatter:off
        // FIXME why building knowledge base on this level does not work??
        int numberOfAllInstances = 10;
        int counter = 0;
        // default interval 2 seconds, meaning each step in a path will be started after 2 seconds
        long interval = 2*1000*60;
        for (SimulationPath path : paths) {
            
            double probability = provider.calculatePathProbability(path);
            f.newPath("path" + counter);

            f.newKnowledgeBuilder().add( ResourceFactory.newClassPathResource("BPMN-SimpleExclusiveGatewayProcess.bpmn2"),
                    ResourceType.BPMN2 )
              .end(World.ROOT, KnowledgeBuilder.class.getName() )
            .newKnowledgeBase()
              .addKnowledgePackages()
              .end(World.ROOT, KnowledgeBase.class.getName() );
            
            // count how many instances/steps should current path have
            int instancesOfPath = (int) (numberOfAllInstances * probability);
            
            for (int i = 0; i < instancesOfPath; i++) {
                f.newStep( interval * i )
                    .newStatefulKnowledgeSession()
                        .end(World.ROOT, StatefulKnowledgeSession.class.getName())
                    .addCommand(new SimulateProcessPathCommand("defaultPackage.test", context, path));
            }
            
            counter++;
        }
        f.runSimulation();
        // @formatter:on
        
    }
    
    @Test
    public void testSimulationRunner() throws IOException {
        
        InputStreamReader in = new InputStreamReader(this.getClass().getResourceAsStream("/BPMN2-TwoUserTasks.bpmn2"));
        
        String out = new String();
        BufferedReader br = new BufferedReader(in);
        for(String line = br.readLine(); line != null; line = br.readLine()) 
          out += line;


        
        SimulationRepository repo = SimulationRunner.runSimulation("BPMN2-TwoUserTasks", out, 10, 2000, "default.simulation.rules.drl");
        assertNotNull(repo);
        
        WorkingMemorySimulationRepository wmRepo = (WorkingMemorySimulationRepository) repo;
        wmRepo.fireAllRules();
        
        assertEquals(4, wmRepo.getAggregatedEvents().size());
        assertEquals(50, wmRepo.getEvents().size());
        
        AggregatedSimulationEvent event = wmRepo.getAggregatedEvents().get(0);
        if (event instanceof AggregatedEndEventSimulationEvent) {
            assertNotNull(event.getProperty("minProcessDuration"));
            assertFalse(event.getProperty("activityId").equals(""));
        } 
        
        event = wmRepo.getAggregatedEvents().get(1);
        assertFalse(event.getProperty("activityId").equals(""));
        assertNotNull(event.getProperty("minExecutionTime"));
        event = wmRepo.getAggregatedEvents().get(2);
        assertFalse(event.getProperty("activityId").equals(""));
        assertNotNull(event.getProperty("minExecutionTime"));
        
        event = wmRepo.getAggregatedEvents().get(3);
        assertNotNull(event.getProperty("minExecutionTime"));
        wmRepo.close();
        
    }
    
    @Test
    public void testSimulationRunnerWithGateway() throws IOException {
        
        InputStreamReader in = new InputStreamReader(this.getClass().getResourceAsStream("/BPMN-SimpleExclusiveGatewayProcess.bpmn2"));
        
        String out = new String();
        BufferedReader br = new BufferedReader(in);
        for(String line = br.readLine(); line != null; line = br.readLine()) 
          out += line;


        
        SimulationRepository repo = SimulationRunner.runSimulation("defaultPackage.test", out, 10, 2000, "default.simulation.rules.drl");
        assertNotNull(repo);
        
        WorkingMemorySimulationRepository wmRepo = (WorkingMemorySimulationRepository) repo;
        wmRepo.fireAllRules();
        assertEquals(5, wmRepo.getAggregatedEvents().size());
        assertEquals(70, wmRepo.getEvents().size());
        
        List<AggregatedSimulationEvent> aggEvents = wmRepo.getAggregatedEvents();
        for (AggregatedSimulationEvent event : aggEvents) {
            if (event instanceof AggregatedProcessSimulationEvent) {
                Map<String, Integer> numberOfInstancePerPath = ((AggregatedProcessSimulationEvent) event).getPathNumberOfInstances();
                assertNotNull(numberOfInstancePerPath);
                assertTrue(3 == numberOfInstancePerPath.get("Path800898475"));
                assertTrue(7 == numberOfInstancePerPath.get("Path-960633761"));
            }
        }
        wmRepo.close();
    }

    @Test
    public void testSimulationRunnerWithGatewaySingleInstance() throws IOException {
        
        InputStreamReader in = new InputStreamReader(this.getClass().getResourceAsStream("/BPMN-SimpleExclusiveGatewayProcess.bpmn2"));
        
        String out = new String();
        BufferedReader br = new BufferedReader(in);
        for(String line = br.readLine(); line != null; line = br.readLine()) 
          out += line;


        
        SimulationRepository repo = SimulationRunner.runSimulation("defaultPackage.test", out, 1, 2000, "default.simulation.rules.drl");
        assertNotNull(repo);
        
        WorkingMemorySimulationRepository wmRepo = (WorkingMemorySimulationRepository) repo;
        wmRepo.fireAllRules();
        assertEquals(4, wmRepo.getAggregatedEvents().size());
        assertEquals(7, wmRepo.getEvents().size());
        wmRepo.close();
    }
    
    @Test
    public void testSimulationRunnerWithGatewayTwoInstances() throws IOException {
        
        InputStreamReader in = new InputStreamReader(this.getClass().getResourceAsStream("/BPMN-SimpleExclusiveGatewayProcess.bpmn2"));
        
        String out = new String();
        BufferedReader br = new BufferedReader(in);
        for(String line = br.readLine(); line != null; line = br.readLine()) 
          out += line;


        
        SimulationRepository repo = SimulationRunner.runSimulation("defaultPackage.test", out, 2, 2000, "default.simulation.rules.drl");
        assertNotNull(repo);
        
        WorkingMemorySimulationRepository wmRepo = (WorkingMemorySimulationRepository) repo;
        wmRepo.fireAllRules();
        assertEquals(5, wmRepo.getAggregatedEvents().size());
        assertEquals(14, wmRepo.getEvents().size());
        wmRepo.close();
    }
    
    @Test
    public void testSimulationRunnerWithGatewaySingleInstanceWithRunRulesOnEveryEvent() throws IOException {
        
        InputStreamReader in = new InputStreamReader(this.getClass().getResourceAsStream("/BPMN-SimpleExclusiveGatewayProcess.bpmn2"));
        
        String out = new String();
        BufferedReader br = new BufferedReader(in);
        for(String line = br.readLine(); line != null; line = br.readLine()) 
          out += line;


        
        SimulationRepository repo = SimulationRunner.runSimulation("defaultPackage.test", out, 5, 2000, true, "default.simulation.rules.drl");
        assertNotNull(repo);
        
        WorkingMemorySimulationRepository wmRepo = (WorkingMemorySimulationRepository) repo;

        assertEquals(20, wmRepo.getAggregatedEvents().size());
        assertEquals(35, wmRepo.getEvents().size());
        wmRepo.close();
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testSimulationRunnerWithRunRulesOnEveryEvent() throws IOException {
        
        InputStreamReader in = new InputStreamReader(this.getClass().getResourceAsStream("/BPMN-SimpleExclusiveGatewayProcess.bpmn2"));
        
        String out = new String();
        BufferedReader br = new BufferedReader(in);
        for(String line = br.readLine(); line != null; line = br.readLine()) 
          out += line;


        
        SimulationRepository repo = SimulationRunner.runSimulation("defaultPackage.test", out, 5, 2000, true, "onevent.simulation.rules.drl");
        assertNotNull(repo);
        
        WorkingMemorySimulationRepository wmRepo = (WorkingMemorySimulationRepository) repo;

        assertEquals(20, wmRepo.getAggregatedEvents().size());
        assertEquals(35, wmRepo.getEvents().size());

        for (SimulationEvent event : wmRepo.getEvents()) {
            if ((event instanceof EndSimulationEvent) || (event instanceof ActivitySimulationEvent)|| (event instanceof HumanTaskActivitySimulationEvent)) {
                assertNotNull(((GenericSimulationEvent) event).getAggregatedEvent());
                assertTrue(((GenericSimulationEvent) event).getAggregatedEvent() instanceof AggregatedProcessSimulationEvent);
            } else if (event instanceof ProcessInstanceEndSimulationEvent) {
                assertNull(((GenericSimulationEvent) event).getAggregatedEvent());
            }
        }
        wmRepo.getSession().execute(new InsertElementsCommand((Collection)wmRepo.getAggregatedEvents()));
        wmRepo.fireAllRules();
        List<AggregatedSimulationEvent> summary = (List<AggregatedSimulationEvent>) wmRepo.getGlobal("summary");
        assertNotNull(summary);
        assertEquals(5, summary.size());
        for (AggregatedSimulationEvent event : summary) {
            if (event instanceof AggregatedProcessSimulationEvent) {
                Map<String, Integer> numberOfInstancePerPath = ((AggregatedProcessSimulationEvent) event).getPathNumberOfInstances();
                assertNotNull(numberOfInstancePerPath);
                assertTrue(1 == numberOfInstancePerPath.get("Path800898475"));
                assertTrue(4 == numberOfInstancePerPath.get("Path-960633761"));
            }
        }
        
        SimulationInfo info = wmRepo.getSimulationInfo();
        
        assertNotNull(info);
        assertEquals("defaultPackage.test", info.getProcessId());
        assertEquals("test", info.getProcessName());
        assertEquals(5, info.getNumberOfExecutions());
        assertEquals(2000, info.getInterval());
        
        System.out.println("Start date is " + new Date(info.getStartTime()) + " end date is " + new Date(info.getEndTime()));
        wmRepo.close();
    }
    
    @Test
    public void testSimulationRunnerWithSinglePath() throws IOException {
        
        InputStreamReader in = new InputStreamReader(this.getClass().getResourceAsStream("/BPMN2-UserTaskWithSimulationMetaData.bpmn2"));
        
        String out = new String();
        BufferedReader br = new BufferedReader(in);
        for(String line = br.readLine(); line != null; line = br.readLine()) 
          out += line;


        
        SimulationRepository repo = SimulationRunner.runSimulation("UserTask", out, 5, 2000, true, "onevent.simulation.rules.drl");
        assertNotNull(repo);
        
        WorkingMemorySimulationRepository wmRepo = (WorkingMemorySimulationRepository) repo;

        assertEquals(15, wmRepo.getAggregatedEvents().size());
        assertEquals(20, wmRepo.getEvents().size());
        wmRepo.close();
    }
    
    @Test
    public void testSimulationRunnerWithSinglePathAndCatchingEvent() throws IOException {
        
        InputStreamReader in = new InputStreamReader(this.getClass().getResourceAsStream("/BPMN2-SinglePathWithCatchingEvent.bpmn2"));
        
        String out = new String();
        BufferedReader br = new BufferedReader(in);
        for(String line = br.readLine(); line != null; line = br.readLine()) 
          out += line;


        
        SimulationRepository repo = SimulationRunner.runSimulation("defaultPackage.test", out, 5, 2000, true, "onevent.simulation.rules.drl");
        assertNotNull(repo);
        
        WorkingMemorySimulationRepository wmRepo = (WorkingMemorySimulationRepository) repo;

        assertEquals(25, wmRepo.getAggregatedEvents().size());
        assertEquals(30, wmRepo.getEvents().size());
        wmRepo.close();
    }
    
    @Test
    public void testSimulationRunnerWithSinglePathAndThrowingEvent() throws IOException {
        
        InputStreamReader in = new InputStreamReader(this.getClass().getResourceAsStream("/BPMN2-SinglePathWithThrowingEvent.bpmn2"));
        
        String out = new String();
        BufferedReader br = new BufferedReader(in);
        for(String line = br.readLine(); line != null; line = br.readLine()) 
          out += line;


        
        SimulationRepository repo = SimulationRunner.runSimulation("defaultPackage.test", out, 5, 2000, true, "onevent.simulation.rules.drl");
        assertNotNull(repo);
        
        WorkingMemorySimulationRepository wmRepo = (WorkingMemorySimulationRepository) repo;

        assertEquals(25, wmRepo.getAggregatedEvents().size());
        assertEquals(30, wmRepo.getEvents().size());
        wmRepo.close();
    }
    
    @Test
    public void testSimulationRunnerWithBoundaryEvent() throws IOException {
        
        InputStreamReader in = new InputStreamReader(this.getClass().getResourceAsStream("/BPMN2-SimpleWithBoundaryEvent.bpmn2"));
        
        String out = new String();
        BufferedReader br = new BufferedReader(in);
        for(String line = br.readLine(); line != null; line = br.readLine()) 
          out += line;


        
        SimulationRepository repo = SimulationRunner.runSimulation("defaultPackage.test", out, 5, 2000, true, "onevent.simulation.rules.drl");
        assertNotNull(repo);
        
        WorkingMemorySimulationRepository wmRepo = (WorkingMemorySimulationRepository) repo;

        assertEquals(25, wmRepo.getAggregatedEvents().size());
        assertEquals(30, wmRepo.getEvents().size());
        wmRepo.close();
    }
    
    @Test
    public void testSimulationRunnerWithScriptRuleXor() throws IOException {
        
        InputStreamReader in = new InputStreamReader(this.getClass().getResourceAsStream("/BPMN2-ScriptRuleXor.bpmn2"));
        
        String out = new String();
        BufferedReader br = new BufferedReader(in);
        for(String line = br.readLine(); line != null; line = br.readLine()) 
          out += line;


        
        SimulationRepository repo = SimulationRunner.runSimulation("defaultPackage.demo", out, 5, 2000, true, "onevent.simulation.rules.drl");
        assertNotNull(repo);
        
        WorkingMemorySimulationRepository wmRepo = (WorkingMemorySimulationRepository) repo;

        assertEquals(30, wmRepo.getAggregatedEvents().size());
        assertEquals(45, wmRepo.getEvents().size());
        
        wmRepo.getSession().execute(new InsertElementsCommand((Collection)wmRepo.getAggregatedEvents()));
        wmRepo.fireAllRules();
        
        List<AggregatedSimulationEvent> summary = (List<AggregatedSimulationEvent>) wmRepo.getGlobal("summary");
        assertNotNull(summary);
        assertEquals(7, summary.size());
        wmRepo.close();
    }
    
    @Test
    public void testSimulationRunnerWithLoop() throws IOException {
        
        InputStreamReader in = new InputStreamReader(this.getClass().getResourceAsStream("/BPMN2-loop-sim.bpmn2"));
        
        String out = new String();
        BufferedReader br = new BufferedReader(in);
        for(String line = br.readLine(); line != null; line = br.readLine()) 
          out += line;


        
        SimulationRepository repo = SimulationRunner.runSimulation("defaultPackage.loop-sim", out, 5, 2000, true, "onevent.simulation.rules.drl");
        assertNotNull(repo);
        
        WorkingMemorySimulationRepository wmRepo = (WorkingMemorySimulationRepository) repo;

        assertEquals(19, wmRepo.getAggregatedEvents().size());
        assertEquals(37, wmRepo.getEvents().size());
        wmRepo.close();
    }
}
