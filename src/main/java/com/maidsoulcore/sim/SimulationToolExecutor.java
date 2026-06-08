package com.maidsoulcore.sim;

import com.maidsoulcore.tool.ToolCall;
import com.maidsoulcore.tool.ToolDefinition;
import com.maidsoulcore.tool.ToolExecutor;
import com.maidsoulcore.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SimulationToolExecutor implements ToolExecutor {
    private final SimulationEnvironment environment;
    private final ToolRegistry toolRegistry;

    public SimulationToolExecutor(SimulationEnvironment environment) {
        this.environment = environment;
        this.toolRegistry = new ToolRegistry();
        registerDefaults();
    }

    private void registerDefaults() {
        toolRegistry.register(new ToolDefinition("scan_world_state", "Read world time, schedule, threat and recent scene summary", false));
        toolRegistry.register(new ToolDefinition("scan_self_state", "Read maid health, energy, hunger, follow state and current work task", false));
        toolRegistry.register(new ToolDefinition("scan_owner_state", "Read owner health, held item and current distance", false));
        toolRegistry.register(new ToolDefinition("scan_position_state", "Read maid, owner and home positions plus distance and light", false));
        toolRegistry.register(new ToolDefinition("scan_inventory_state", "Read maid equipment and bag summary", false));
        toolRegistry.register(new ToolDefinition("scan_nearby_entities", "Read nearby hostile and passive entities", false));
        toolRegistry.register(new ToolDefinition("focus_owner", "Lock on the owner as the focus target", false));
        toolRegistry.register(new ToolDefinition("capture_view", "Simulate a screenshot and produce a short view summary", false));
        toolRegistry.register(new ToolDefinition("follow_owner", "Enable default follow mode and move close to the owner", true));
        toolRegistry.register(new ToolDefinition("stay_here", "Stop following and stay at the current place as standby home", true));
        toolRegistry.register(new ToolDefinition("move_to_owner", "Move close to the owner", true));
        toolRegistry.register(new ToolDefinition("return_home", "Move back to the home position", true));
        toolRegistry.register(new ToolDefinition("return_home_and_standby", "Go back home and remain in standby mode", true));
        toolRegistry.register(new ToolDefinition("sit_down", "Sit down and stay calm", true));
        toolRegistry.register(new ToolDefinition("stand_up", "Stand up and prepare to move", true));
        toolRegistry.register(new ToolDefinition("set_schedule_day", "Set work schedule to daytime", true));
        toolRegistry.register(new ToolDefinition("set_schedule_night", "Set work schedule to nighttime", true));
        toolRegistry.register(new ToolDefinition("set_schedule_all", "Set work schedule to all-day", true));
        toolRegistry.register(new ToolDefinition("enter_idle_mode", "Stop work and enter idle companion mode", true));
        toolRegistry.register(new ToolDefinition("start_melee_guard", "Switch to TLM melee guard task", true));
        toolRegistry.register(new ToolDefinition("start_bow_guard", "Switch to TLM bow guard task", true));
        toolRegistry.register(new ToolDefinition("start_crossbow_guard", "Switch to TLM crossbow guard task", true));
        toolRegistry.register(new ToolDefinition("start_danmaku_guard", "Switch to TLM danmaku guard task", true));
        toolRegistry.register(new ToolDefinition("start_trident_guard", "Switch to TLM trident guard task", true));
        toolRegistry.register(new ToolDefinition("start_farming", "Switch to TLM farm task", true));
        toolRegistry.register(new ToolDefinition("start_sugar_cane_work", "Switch to TLM sugar cane task", true));
        toolRegistry.register(new ToolDefinition("start_melon_work", "Switch to TLM melon task", true));
        toolRegistry.register(new ToolDefinition("start_cocoa_work", "Switch to TLM cocoa task", true));
        toolRegistry.register(new ToolDefinition("start_honey_work", "Switch to TLM honey task", true));
        toolRegistry.register(new ToolDefinition("start_cleanup_grass", "Switch to TLM grass cleanup task", true));
        toolRegistry.register(new ToolDefinition("start_cleanup_snow", "Switch to TLM snow cleanup task", true));
        toolRegistry.register(new ToolDefinition("start_shearing", "Switch to TLM shearing task", true));
        toolRegistry.register(new ToolDefinition("start_milking", "Switch to TLM milking task", true));
        toolRegistry.register(new ToolDefinition("start_torch_placing", "Switch to TLM torch placement task", true));
        toolRegistry.register(new ToolDefinition("start_feeding_owner", "Switch to TLM owner feeding task", true));
        toolRegistry.register(new ToolDefinition("start_feeding_animals", "Switch to TLM animal feeding task", true));
        toolRegistry.register(new ToolDefinition("start_fishing", "Switch to TLM fishing task", true));
        toolRegistry.register(new ToolDefinition("start_extinguishing", "Switch to TLM extinguishing task", true));
        toolRegistry.register(new ToolDefinition("start_board_game", "Switch to TLM board game task", true));
        toolRegistry.register(new ToolDefinition("guard_owner", "High-level guard behavior that scans threat and selects a combat task", true));
        toolRegistry.register(new ToolDefinition("care_owner", "High-level care behavior that checks owner state and switches to care task if needed", true));
        toolRegistry.register(new ToolDefinition("keep_company", "High-level companion behavior: follow, sit, idle and chat nearby", true));
        toolRegistry.register(new ToolDefinition("sleep", "Enter sleeping state", true));
        toolRegistry.register(new ToolDefinition("wake", "Exit sleeping state", true));
        toolRegistry.register(new ToolDefinition("accept_item", "Accept food from the owner", true));
        toolRegistry.register(new ToolDefinition("defend_self", "Enter a self-defense state", true));
        toolRegistry.register(new ToolDefinition("emote", "Update the current action or emotion summary", true));
    }

    public ToolRegistry toolRegistry() {
        return toolRegistry;
    }

    @Override
    public Object execute(ToolCall call) {
        String toolName = call.toolName();
        Map<String, Object> arguments = call.arguments();
        return switch (toolName) {
            case "scan_world_state" -> environment.worldStateSummary();
            case "scan_self_state" -> environment.selfStateSummary();
            case "scan_owner_state" -> environment.ownerStateSummary();
            case "scan_position_state" -> environment.positionStateSummary();
            case "scan_inventory_state" -> environment.inventoryStateSummary();
            case "scan_nearby_entities" -> environment.nearbyEntitiesSummary();
            case "focus_owner" -> {
                environment.setLastActionSummary("focus_owner");
                yield "focus on owner";
            }
            case "capture_view" -> {
                String summary = "view: owner=" + environment.ownerPosition().shortText()
                        + ", maid=" + environment.maidPosition().shortText()
                        + ", threat=" + environment.nearbyThreat();
                environment.setLastVisionSummary(summary);
                environment.markCapture();
                yield summary;
            }
            case "follow_owner" -> {
                environment.setDefaultFollowPolicy("tool: follow_owner");
                environment.setMaidSitting(false);
                environment.setMaidPosition(environment.ownerPosition());
                environment.setLastActionSummary("follow_owner");
                yield "follow mode enabled";
            }
            case "stay_here" -> {
                environment.setExplicitStayPolicy("tool: stay_here");
                environment.setHomePosition(environment.maidPosition());
                environment.setCurrentWorkTask("idle");
                environment.setLastActionSummary("stay_here");
                yield "stay mode enabled at current position";
            }
            case "move_to_owner" -> {
                environment.setMaidPosition(environment.ownerPosition());
                environment.spendEnergy(0.01D);
                environment.setLastActionSummary("move_to_owner");
                yield "moved near owner";
            }
            case "return_home" -> {
                if (environment.shouldDefaultFollow()) {
                    yield "blocked: default follow policy is active";
                }
                if (!environment.canStartWorkTask()) {
                    yield "blocked: energy too low for return_home";
                }
                environment.setMaidPosition(environment.homePosition());
                environment.spendEnergy(0.02D);
                environment.setLastActionSummary("return_home");
                yield "returned home";
            }
            case "return_home_and_standby" -> {
                if (!environment.canStartWorkTask()) {
                    yield "blocked: energy too low for return_home_and_standby";
                }
                environment.setExplicitStayPolicy("tool: return_home_and_standby");
                environment.setMaidPosition(environment.homePosition());
                environment.setCurrentWorkTask("idle");
                environment.setMaidSitting(true);
                environment.spendEnergy(0.03D);
                environment.setLastActionSummary("return_home_and_standby");
                yield "returned home and entered standby";
            }
            case "sit_down" -> {
                environment.setMaidSitting(true);
                environment.setLastActionSummary("sit_down");
                yield "maid is now sitting";
            }
            case "stand_up" -> {
                environment.setMaidSitting(false);
                environment.setLastActionSummary("stand_up");
                yield "maid is now standing";
            }
            case "set_schedule_day" -> switchSchedule("DAY");
            case "set_schedule_night" -> switchSchedule("NIGHT");
            case "set_schedule_all" -> switchSchedule("ALL");
            case "enter_idle_mode" -> switchTask("idle", 0.0D, "entered idle companion mode");
            case "start_melee_guard" -> switchTask("attack", 0.05D, "switched to melee guard");
            case "start_bow_guard" -> switchTask("ranged_attack", 0.05D, "switched to bow guard");
            case "start_crossbow_guard" -> switchTask("crossbow_attack", 0.05D, "switched to crossbow guard");
            case "start_danmaku_guard" -> switchTask("danmaku_attack", 0.05D, "switched to danmaku guard");
            case "start_trident_guard" -> switchTask("trident_attack", 0.05D, "switched to trident guard");
            case "start_farming" -> switchTask("farm", 0.04D, "switched to farm task");
            case "start_sugar_cane_work" -> switchTask("sugar_cane", 0.04D, "switched to sugar cane task");
            case "start_melon_work" -> switchTask("melon", 0.04D, "switched to melon task");
            case "start_cocoa_work" -> switchTask("cocoa", 0.04D, "switched to cocoa task");
            case "start_honey_work" -> switchTask("honey", 0.05D, "switched to honey task");
            case "start_cleanup_grass" -> switchTask("grass", 0.03D, "switched to grass cleanup task");
            case "start_cleanup_snow" -> switchTask("snow", 0.03D, "switched to snow cleanup task");
            case "start_shearing" -> switchTask("shears", 0.03D, "switched to shearing task");
            case "start_milking" -> switchTask("milk", 0.03D, "switched to milking task");
            case "start_torch_placing" -> switchTask("torch", 0.03D, "switched to torch placing task");
            case "start_feeding_owner" -> switchTask("feed", 0.03D, "switched to owner feeding task");
            case "start_feeding_animals" -> switchTask("feed_animal", 0.03D, "switched to animal feeding task");
            case "start_fishing" -> switchTask("fishing", 0.03D, "switched to fishing task");
            case "start_extinguishing" -> switchTask("extinguishing", 0.04D, "switched to extinguishing task");
            case "start_board_game" -> switchTask("board_games", 0.01D, "switched to board game task");
            case "guard_owner" -> {
                String threat = environment.nearbyThreat();
                if ("none".equals(threat)) {
                    environment.setMaidPosition(environment.ownerPosition());
                    environment.setCurrentWorkTask("idle");
                    environment.setLastActionSummary("guard_owner:follow");
                    yield "no threat found, keep close to owner";
                }
                yield switchTask("attack", 0.05D, "guarding owner against " + threat);
            }
            case "care_owner" -> {
                boolean hungry = environment.maidHunger() > 0.55D;
                if (hungry) {
                    environment.tryGainFavorability("care", 1, "care_owner");
                    yield switchTask("feed", 0.03D, "owner care mode enabled");
                }
                environment.setCurrentWorkTask("idle");
                environment.setLastActionSummary("care_owner:observe");
                yield "owner checked, no urgent care task needed";
            }
            case "keep_company" -> {
                if (!environment.shouldDefaultFollow()) {
                    environment.setMaidSitting(true);
                    environment.setCurrentWorkTask("idle");
                    environment.setLastActionSummary("keep_company:stay_near_home");
                    yield "keeping company at standby place";
                }
                environment.setMaidSitting(false);
                environment.setMaidPosition(environment.ownerPosition());
                environment.setCurrentWorkTask("idle");
                environment.setLastActionSummary("keep_company:follow_owner");
                yield "keeping company near owner";
            }
            case "sleep" -> {
                environment.setMaidSleeping(true);
                environment.setCurrentWorkTask("idle");
                environment.recoverEnergy(0.25D);
                environment.setLastActionSummary("sleep");
                yield "entered sleeping state";
            }
            case "wake" -> {
                environment.setMaidSleeping(false);
                environment.setLastActionSummary("wake");
                yield "woke up";
            }
            case "accept_item" -> {
                String item = String.valueOf(arguments.getOrDefault("item", "unknown_food"));
                environment.setMaidHunger(environment.maidHunger() - 0.35D);
                environment.recoverEnergy(0.10D);
                environment.tryGainFavorability("care", 1, "accept_item");
                environment.setLastActionSummary("accept_item:" + item);
                yield "accepted item=" + item;
            }
            case "defend_self" -> {
                String source = String.valueOf(arguments.getOrDefault("source", "unknown_target"));
                environment.setNearbyThreat(source);
                environment.setCurrentWorkTask("attack");
                environment.spendEnergy(0.06D);
                environment.setLastActionSummary("defend_self:" + source);
                yield "defending against " + source;
            }
            case "emote" -> {
                String mood = String.valueOf(arguments.getOrDefault("mood", "calm"));
                environment.setLastActionSummary("emote:" + mood);
                yield "emotion=" + mood;
            }
            case "reply" -> "reply stage";
            default -> "unknown tool: " + toolName;
        };
    }

    /**
     * 统一模拟 TLM schedule 切换。
     */
    private String switchSchedule(String schedule) {
        environment.setCurrentSchedule(schedule);
        environment.setLastActionSummary("switch_schedule:" + schedule);
        return "schedule=" + schedule;
    }

    /**
     * 统一模拟 TLM switch_work_task。
     */
    private String switchTask(String taskId, double energyCost, String successText) {
        if (!environment.canStartWorkTask() && !"idle".equals(taskId)) {
            return "blocked: energy too low for task=" + taskId;
        }
        environment.setCurrentWorkTask(taskId);
        environment.setMaidSleeping(false);
        environment.setMaidSitting(false);
        if (energyCost > 0.0D) {
            environment.spendEnergy(energyCost);
        }
        environment.tryGainFavorability("action", 1, "task:" + taskId);
        environment.setLastActionSummary("switch_task:" + taskId);
        return successText;
    }

    public List<String> executePlan(List<ToolCall> toolCalls) {
        List<String> outputs = new ArrayList<>();
        for (ToolCall toolCall : toolCalls) {
            outputs.add(toolCall.toolName() + " => " + execute(toolCall));
        }
        return outputs;
    }
}
