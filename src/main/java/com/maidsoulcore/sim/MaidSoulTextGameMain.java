package com.maidsoulcore.sim;

import com.maidsoulcore.decision.DecisionRoute;
import com.maidsoulcore.event.EventPriority;
import com.maidsoulcore.event.MaidEvent;
import com.maidsoulcore.runtime.RuntimeConfig;
import com.maidsoulcore.tool.ToolDefinition;
import com.maidsoulcore.trace.TraceEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

public final class MaidSoulTextGameMain {
    private MaidSoulTextGameMain() {
    }

    public static void main(String[] args) {
        SimulationEnvironment environment = new SimulationEnvironment("maid-demo-01", "owner-demo-01", "Maid", "Owner");
        SimulationEngine engine = new SimulationEngine(RuntimeConfig.defaults(), environment);
        SimulationAutoEventDirector director = new SimulationAutoEventDirector();
        Scanner scanner = new Scanner(System.in);

        System.out.println("MaidSoulCore text simulation");
        System.out.println("The world will produce random events. You only need to chat. Type /help for commands.\n");

        while (true) {
            playAutoEvents(engine, director.beforePlayerTurn(engine, environment), "world");

            System.out.print("you> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String line = scanner.nextLine().trim();
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("/")) {
                if (handleSlashCommand(engine, environment, line)) {
                    break;
                }
                continue;
            }

            environment.setLastPlayerUtterance(line);
            SimulationTurnResult userTurn = engine.handle(engine.newEvent("owner.talk", EventPriority.P1, Map.of("text", line)));
            printCompactTurnResult(userTurn, true);
            playAutoEvents(engine, director.afterPlayerTurn(engine, environment), "random");
        }
    }

    private static boolean handleSlashCommand(SimulationEngine engine, SimulationEnvironment environment, String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        if ("/exit".equals(lower) || "/quit".equals(lower)) {
            System.out.println("Simulation closed.");
            return true;
        }
        if ("/help".equals(lower)) {
            printHelp();
            return false;
        }
        if ("/status".equals(lower)) {
            System.out.println(environment.describe());
            return false;
        }
        if ("/trace".equals(lower)) {
            printTrace(engine.traceSnapshot());
            return false;
        }
        if ("/tools".equals(lower)) {
            printTools(engine);
            return false;
        }
        if ("/home".equals(lower)) {
            printCompactTurnResult(engine.handle(engine.newEvent("owner.command.return_home", EventPriority.P1, Map.of())), false);
            return false;
        }
        if ("/follow".equals(lower)) {
            printCompactTurnResult(engine.handle(engine.newEvent("owner.command.follow", EventPriority.P1, Map.of())), false);
            return false;
        }
        System.out.println("Unknown command. Type /help.");
        return false;
    }

    private static void playAutoEvents(SimulationEngine engine, List<MaidEvent> events, String title) {
        for (MaidEvent event : events) {
            SimulationTurnResult result = engine.handle(event);
            printCompactTurnResult(result, false, title);
        }
    }

    private static void printHelp() {
        System.out.println("""
                Type normal text to chat with the maid.
                Commands:
                  /help     show help
                  /status   show current environment state
                  /trace    show recent trace
                  /tools    list tools
                  /home     explicit command: return home and stay there
                  /follow   explicit command: resume follow
                  /exit     quit
                """);
    }

    private static void printCompactTurnResult(SimulationTurnResult result, boolean fromPlayer) {
        printCompactTurnResult(result, fromPlayer, fromPlayer ? "player" : "event");
    }

    private static void printCompactTurnResult(SimulationTurnResult result, boolean fromPlayer, String title) {
        MaidEvent event = result.event();
        System.out.println("\n--- " + title + " ---");
        if (fromPlayer) {
            System.out.println("you said: " + event.payload().getOrDefault("text", ""));
        } else {
            System.out.println("happened: " + event.type() + " " + event.payload());
        }
        System.out.println("maid: " + (result.reply().isBlank() ? "(no outward reply this turn)" : result.reply()));
        System.out.println("route=" + result.decision().route()
                + " mood=" + result.blackboard().mood()
                + " action=" + result.blackboard().state().getOrDefault("memory.last_action", "none"));
        System.out.println("follow=" + result.blackboard().state().getOrDefault("policy.follow_policy", "DEFAULT_FOLLOW")
                + " energy=" + result.blackboard().state().getOrDefault("maid.energy_state", "NORMAL")
                + " favor=" + result.blackboard().state().getOrDefault("relation.favorability_total", 0));
        if (result.plan() != null) {
            System.out.println("plan=" + result.plan().actions().stream().map(action -> action.actionType()).toList());
        }
        if (!result.executionLogs().isEmpty()) {
            System.out.println("tools=" + result.executionLogs());
        } else if (result.decision().route() == DecisionRoute.REPLY_ONLY || result.decision().route() == DecisionRoute.DROP) {
            System.out.println("tools=[]");
        }
    }

    private static void printTrace(List<TraceEvent> traceEvents) {
        System.out.println("\n=== Trace Tail ===");
        if (traceEvents.isEmpty()) {
            System.out.println("no trace");
            return;
        }
        traceEvents.stream()
                .sorted(Comparator.comparingLong(TraceEvent::sequence))
                .forEach(event -> System.out.println(
                        "#" + event.sequence()
                                + " stage=" + event.stage()
                                + " type=" + event.type()
                                + " priority=" + event.priority()
                                + " reason=" + event.reason()
                ));
    }

    private static void printTools(SimulationEngine engine) {
        System.out.println("\n=== Tools ===");
        engine.toolExecutor().toolRegistry().all().stream()
                .sorted(Comparator.comparing(ToolDefinition::name))
                .forEach(tool -> System.out.println(
                        "- " + tool.name()
                                + " | writeAction=" + tool.writeAction()
                                + " | " + tool.description()
                ));
    }
}
