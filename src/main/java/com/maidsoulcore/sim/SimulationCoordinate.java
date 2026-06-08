package com.maidsoulcore.sim;

/**
 * 文字模拟环境中的三维坐标。
 * <p>
 * 这里不追求 Minecraft 里的完整方块逻辑，只保留：
 * 1. 位置展示；
 * 2. 女仆与主人/家的距离关系；
 * 3. 工具执行后的位移结果。
 */
public record SimulationCoordinate(int x, int y, int z) {
    /**
     * 返回便于日志展示的短文本。
     */
    public String shortText() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
