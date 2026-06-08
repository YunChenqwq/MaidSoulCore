#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
affective-longing 本地聊天体验台。

这个脚本的目标不是把外部 Python 项目塞进 Forge，而是让你能直观看到：
1. 玩家每句话如何被粗略映射成关系事件；
2. 事件如何推动亲密度、冲突度、关系阶段；
3. VAD 情绪向量如何变化；
4. 当前上下文如何触发记忆，并影响“想念/主动”分数；
5. LLM 在看到这些状态后，会如何生成更像角色的回复。

密钥读取顺序：
1. 环境变量 MAIDSOUL_AFFECTIVE_API_KEY；
2. 本地文件 .maidsoul_local/affective_gui_config.json。

注意：.maidsoul_local 已加入 .gitignore，不会被提交。
"""

from __future__ import annotations

import json
import math
import os
import queue
import random
import threading
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path
from tkinter import BOTH, END, LEFT, RIGHT, TOP, Button, Entry, Frame, Label, Listbox, StringVar, Text, Tk, ttk
from tkinter.scrolledtext import ScrolledText


ROOT = Path(__file__).resolve().parents[1]
LOCAL_CONFIG = ROOT / ".maidsoul_local" / "affective_gui_config.json"
MEMORY_FILE = ROOT / ".maidsoul_local" / "affective_gui_memories.json"


STAGE_LABELS = {
    "courting": "初识试探",
    "sweet": "甜蜜亲近",
    "passionate": "热烈依恋",
    "stable": "稳定陪伴",
    "cold": "冷淡受伤",
    "repairing": "修复关系",
}

EMOTION_LABELS = {
    "joy": "开心",
    "trust": "信任",
    "contentment": "安心",
    "excitement": "期待",
    "love": "喜欢",
    "anticipation": "预感",
    "anxiety": "不安",
    "sadness": "难过",
    "anger": "生气",
    "fear": "害怕",
    "neutral": "平静",
}

EVENT_LABELS = {
    "reply_fast": "快速回应：主人很快回应了她",
    "reply_slow": "慢速回应：主人隔了比较久才回应",
    "no_reply": "没有回应：她发出后没有得到回复",
    "long_silence": "长时间沉默：很久没有互动",
    "affection": "亲昵表达：主人表达喜欢、夸奖、靠近",
    "fight": "冲突争吵：主人说了伤人的话或发生冲突",
    "apology": "道歉修复：有人道歉，关系开始缓和",
    "initiate": "主动联系：主人主动开启话题",
    "reject": "拒绝疏远：主人拒绝她或要求远离",
}


@dataclass
class GuiConfig:
    """GUI 和 LLM 的本地配置。"""

    base_url: str = "https://api.siliconflow.cn/v1"
    model: str = "deepseek-ai/DeepSeek-V3"
    api_key: str = ""
    temperature: float = 0.8
    max_tokens: int = 260


def load_config() -> GuiConfig:
    """读取本地配置；没有配置时使用默认值。"""

    data = {}
    if LOCAL_CONFIG.exists():
        try:
            # PowerShell 5.x 的 Set-Content -Encoding UTF8 会写入 BOM。
            # 用 utf-8-sig 读取可以同时兼容“带 BOM”和“不带 BOM”的配置文件。
            data = json.loads(LOCAL_CONFIG.read_text(encoding="utf-8-sig"))
        except json.JSONDecodeError:
            data = {}

    config = GuiConfig(**{k: v for k, v in data.items() if k in GuiConfig.__annotations__})
    env_key = os.environ.get("MAIDSOUL_AFFECTIVE_API_KEY", "").strip()
    if env_key:
        config.api_key = env_key
    return config


def save_config(config: GuiConfig) -> None:
    """保存配置到本地忽略目录。"""

    LOCAL_CONFIG.parent.mkdir(parents=True, exist_ok=True)
    LOCAL_CONFIG.write_text(
        json.dumps(config.__dict__, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


class OuValue:
    """简化 OU 均值回归变量，用来模拟情绪/关系自然回落。"""

    def __init__(self, value: float, baseline: float, theta: float, sigma: float, low: float, high: float):
        self.value = value
        self.baseline = baseline
        self.theta = theta
        self.sigma = sigma
        self.low = low
        self.high = high
        self.rng = random.Random(42)

    def bump(self, delta: float) -> None:
        self.value = max(self.low, min(self.high, self.value + delta))

    def step(self, hours: float) -> None:
        noise = self.sigma * math.sqrt(max(0.0, hours)) * self.rng.gauss(0, 1)
        drift = self.theta * (self.baseline - self.value) * hours
        self.value = max(self.low, min(self.high, self.value + drift + noise))


@dataclass
class AffectiveState:
    """体验台里的角色状态。"""

    stage: str = "courting"
    intimacy: OuValue = field(default_factory=lambda: OuValue(0.50, 0.50, 0.05, 0.015, 0.0, 1.0))
    conflict: OuValue = field(default_factory=lambda: OuValue(0.10, 0.10, 0.08, 0.02, 0.0, 1.0))
    valence: OuValue = field(default_factory=lambda: OuValue(0.35, 0.35, 0.06, 0.015, -1.0, 1.0))
    arousal: OuValue = field(default_factory=lambda: OuValue(0.45, 0.45, 0.10, 0.02, 0.0, 1.0))
    dominance: OuValue = field(default_factory=lambda: OuValue(0.42, 0.42, 0.08, 0.015, 0.0, 1.0))
    active_curiosity: float = 65.0
    last_event: str = "initiate"
    last_trigger: str = ""
    trigger_score: float = 0.0

    def emotion_name(self) -> str:
        """把 VAD 粗略折算成中文情绪标签。"""

        v, a, d = self.valence.value, self.arousal.value, self.dominance.value
        if v > 0.65 and a > 0.55:
            return "love"
        if v > 0.50 and a <= 0.45:
            return "contentment"
        if v > 0.45:
            return "trust"
        if v < -0.45 and a > 0.55 and d > 0.55:
            return "anger"
        if v < -0.45 and a > 0.55:
            return "anxiety"
        if v < -0.35:
            return "sadness"
        if a > 0.65:
            return "anticipation"
        return "neutral"

    def apply_event(self, event: str) -> None:
        """根据事件推动关系与情绪。这里是显式表，不靠神秘 prompt。"""

        self.last_event = event
        relationship_bumps = {
            "reply_fast": (0.05, -0.02),
            "reply_slow": (-0.02, 0.01),
            "no_reply": (-0.05, 0.03),
            "long_silence": (-0.10, 0.05),
            "affection": (0.10, -0.05),
            "fight": (-0.15, 0.20),
            "apology": (0.05, -0.15),
            "initiate": (0.08, -0.02),
            "reject": (-0.12, 0.10),
        }
        emotion_bumps = {
            "reply_fast": (0.08, 0.05, 0.03),
            "reply_slow": (-0.03, 0.02, -0.02),
            "no_reply": (-0.06, 0.08, -0.05),
            "long_silence": (-0.10, 0.10, -0.08),
            "affection": (0.12, 0.08, 0.02),
            "fight": (-0.15, 0.15, -0.05),
            "apology": (0.05, -0.05, 0.03),
            "initiate": (0.10, 0.06, 0.05),
            "reject": (-0.12, 0.10, -0.10),
        }

        di, dc = relationship_bumps.get(event, (0.0, 0.0))
        dv, da, dd = emotion_bumps.get(event, (0.0, 0.0, 0.0))
        self.intimacy.bump(di)
        self.conflict.bump(dc)
        self.valence.bump(dv)
        self.arousal.bump(da)
        self.dominance.bump(dd)
        self._update_stage()
        self._update_active_curiosity()

    def step_time(self, hours: float) -> None:
        """模拟时间流逝：关系和情绪会往基线回落。"""

        for item in (self.intimacy, self.conflict, self.valence, self.arousal, self.dominance):
            item.step(hours)
        self._update_stage()
        self._update_active_curiosity()

    def _update_stage(self) -> None:
        """根据亲密/冲突推断当前阶段，方便 GUI 展示。"""

        i, c = self.intimacy.value, self.conflict.value
        if c > 0.58:
            self.stage = "cold"
        elif c > 0.35:
            self.stage = "repairing"
        elif i > 0.82:
            self.stage = "passionate"
        elif i > 0.64:
            self.stage = "sweet"
        elif i > 0.52 and c < 0.22:
            self.stage = "stable"
        else:
            self.stage = "courting"

        baselines = {
            "courting": (0.20, 0.60, 0.35),
            "sweet": (0.70, 0.50, 0.45),
            "passionate": (0.82, 0.68, 0.50),
            "stable": (0.55, 0.32, 0.50),
            "cold": (-0.45, 0.55, 0.30),
            "repairing": (0.05, 0.50, 0.38),
        }
        self.valence.baseline, self.arousal.baseline, self.dominance.baseline = baselines[self.stage]

    def _update_active_curiosity(self) -> None:
        """主动好奇分数：默认 65，再叠加亲密、记忆触发和风险/冲突。"""

        score = 65.0
        score += max(0.0, self.intimacy.value - 0.5) * 35
        score += self.trigger_score * 20
        score += max(0.0, self.conflict.value - 0.25) * 10
        if self.stage in ("sweet", "passionate"):
            score += 8
        if self.last_event in ("long_silence", "no_reply"):
            score += 10
        self.active_curiosity = max(0.0, min(100.0, score))


class MemoryBank:
    """轻量记忆库：不用 Chroma，先用词重叠做体验版触发。"""

    def __init__(self) -> None:
        self.memories: list[dict] = []
        self.load()

    def load(self) -> None:
        if MEMORY_FILE.exists():
            try:
                self.memories = json.loads(MEMORY_FILE.read_text(encoding="utf-8"))
            except json.JSONDecodeError:
                self.memories = []
        if not self.memories:
            self.memories = [
                {"text": "主人第一次把灵魂核心交给我，我觉得自己终于被认真选择了。", "tags": ["first_meeting"]},
                {"text": "主人说过喜欢温柔粘人的女仆，所以我会更主动地陪在他身边。", "tags": ["persona"]},
                {"text": "下雨和夜晚会让我更想靠近主人，因为那种时候很适合轻声聊天。", "tags": ["weather", "night"]},
            ]
            self.save()

    def save(self) -> None:
        MEMORY_FILE.parent.mkdir(parents=True, exist_ok=True)
        MEMORY_FILE.write_text(json.dumps(self.memories, ensure_ascii=False, indent=2), encoding="utf-8")

    def add(self, text: str, tags: list[str] | None = None) -> None:
        self.memories.append({"text": text, "tags": tags or [], "time": time.time()})
        self.save()

    def trigger(self, context: str) -> tuple[str, float]:
        """返回最相似记忆和触发分。这个只是体验版，正式版应换成我们的向量/图谱检索。"""

        context_tokens = tokenize_zh(context)
        if not context_tokens:
            return "", 0.0
        best_text, best_score = "", 0.0
        for item in self.memories:
            memory_tokens = tokenize_zh(item["text"])
            union = context_tokens | memory_tokens
            if not union:
                continue
            score = len(context_tokens & memory_tokens) / len(union)
            tag_bonus = 0.08 if any(tag in context for tag in item.get("tags", [])) else 0.0
            score = min(1.0, score + tag_bonus)
            if score > best_score:
                best_text, best_score = item["text"], score
        if best_score < 0.10:
            return "", 0.0
        return best_text, best_score


def tokenize_zh(text: str) -> set[str]:
    """非常轻量的中文切片，避免为了体验台安装大模型依赖。"""

    cleaned = "".join(ch for ch in text.lower() if not ch.isspace())
    tokens = {cleaned[i : i + 2] for i in range(max(0, len(cleaned) - 1))}
    tokens |= {cleaned[i : i + 3] for i in range(max(0, len(cleaned) - 2))}
    return tokens


def classify_event(text: str) -> str:
    """把玩家输入映射成关系事件；体验台用规则，正式链路应由 classifier/tool loop 产出结构事件。"""

    t = text.strip().lower()
    affection = ["喜欢", "爱你", "想你", "抱", "亲", "可爱", "陪我", "最喜欢", "贴贴", "温柔"]
    apology = ["对不起", "抱歉", "我错", "原谅", "不好意思"]
    fight = ["傻逼", "滚", "烦", "讨厌", "闭嘴", "废物", "生气", "不想理"]
    reject = ["不要", "拒绝", "离我远", "别跟", "不需要"]
    if any(x in t for x in apology):
        return "apology"
    if any(x in t for x in fight):
        return "fight"
    if any(x in t for x in reject):
        return "reject"
    if any(x in t for x in affection):
        return "affection"
    if len(t) > 35:
        return "reply_fast"
    return "initiate"


def build_messages(history: list[tuple[str, str]], state: AffectiveState, trigger: str) -> list[dict]:
    """组装给 LLM 的消息。状态用结构化中文喂进去，让回复能体现机制变化。"""

    system = f"""
你是一个温柔、粘人、很喜欢主人的女仆型 AI 角色。你会自然地回应主人，不要解释系统规则。

当前底层状态：
- 关系阶段：{STAGE_LABELS[state.stage]}
- 亲密度：{state.intimacy.value:.2f}
- 冲突度：{state.conflict.value:.2f}
- 情绪：{EMOTION_LABELS[state.emotion_name()]}
- VAD：valence={state.valence.value:.2f}, arousal={state.arousal.value:.2f}, dominance={state.dominance.value:.2f}
- 主动好奇：{state.active_curiosity:.1f}
- 最近事件：{state.last_event}
- 被触发的记忆：{trigger or "无"}

回复要求：
- 用中文，2 到 4 句。
- 更像在聊天，不要写报告。
- 如果触发了记忆，可以轻轻带到话里，但不要说“系统检测到记忆”。
- 语气温柔、亲近、会主动在意主人。
""".strip()

    messages = [{"role": "system", "content": system}]
    for role, content in history[-10:]:
        messages.append({"role": role, "content": content})
    return messages


def call_llm(config: GuiConfig, messages: list[dict]) -> str:
    """调用 OpenAI-compatible Chat Completions。"""

    if not config.api_key:
        return "我还没有拿到 API key。你可以在右上角填入并保存，或者设置 MAIDSOUL_AFFECTIVE_API_KEY。"

    url = config.base_url.rstrip("/") + "/chat/completions"
    payload = {
        "model": config.model,
        "messages": messages,
        "temperature": config.temperature,
        "max_tokens": config.max_tokens,
    }
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {config.api_key}",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        return data["choices"][0]["message"]["content"].strip()
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        return f"LLM 调用失败：HTTP {exc.code}\n{detail[:800]}"
    except Exception as exc:
        return f"LLM 调用失败：{exc}"


class ChatGui:
    """Tkinter 聊天窗口。"""

    def __init__(self) -> None:
        self.config = load_config()
        self.state = AffectiveState()
        self.memory = MemoryBank()
        self.history: list[tuple[str, str]] = []
        self.jobs: queue.Queue[tuple[str, str]] = queue.Queue()

        self.root = Tk()
        self.root.title("MaidSoulCore affective-longing 体验台")
        self.root.geometry("1180x760")

        self.base_url_var = StringVar(value=self.config.base_url)
        self.model_var = StringVar(value=self.config.model)
        self.key_var = StringVar(value=self.config.api_key)
        self.event_var = StringVar(value="事件：initiate")
        self.stage_var = StringVar()
        self.emotion_var = StringVar()
        self.trigger_var = StringVar()

        self._build_layout()
        self._refresh_state()
        self.root.after(100, self._poll_jobs)

    def _build_layout(self) -> None:
        top = Frame(self.root)
        top.pack(side=TOP, fill="x", padx=10, pady=8)

        Label(top, text="Base URL").pack(side=LEFT)
        Entry(top, textvariable=self.base_url_var, width=34).pack(side=LEFT, padx=4)
        Label(top, text="Model").pack(side=LEFT)
        Entry(top, textvariable=self.model_var, width=30).pack(side=LEFT, padx=4)
        Label(top, text="API Key").pack(side=LEFT)
        Entry(top, textvariable=self.key_var, width=34, show="*").pack(side=LEFT, padx=4)
        Button(top, text="保存配置", command=self._save_config).pack(side=LEFT, padx=4)

        body = Frame(self.root)
        body.pack(fill=BOTH, expand=True, padx=10, pady=4)

        left = Frame(body)
        left.pack(side=LEFT, fill=BOTH, expand=True)
        right = Frame(body, width=360)
        right.pack(side=RIGHT, fill="y", padx=(10, 0))

        self.chat = ScrolledText(left, wrap="word", font=("Microsoft YaHei UI", 11))
        self.chat.pack(fill=BOTH, expand=True)
        self.chat.configure(state="disabled")

        input_bar = Frame(left)
        input_bar.pack(fill="x", pady=(8, 0))
        self.input = Entry(input_bar, font=("Microsoft YaHei UI", 11))
        self.input.pack(side=LEFT, fill="x", expand=True)
        self.input.bind("<Return>", lambda _e: self._send())
        Button(input_bar, text="发送", command=self._send).pack(side=LEFT, padx=(6, 0))
        Button(input_bar, text="+1小时", command=lambda: self._step_time(1)).pack(side=LEFT, padx=(6, 0))
        Button(input_bar, text="+24小时沉默", command=lambda: self._silence()).pack(side=LEFT, padx=(6, 0))

        Label(right, text="状态面板", font=("Microsoft YaHei UI", 13, "bold")).pack(anchor="w")
        Label(right, textvariable=self.stage_var, justify=LEFT).pack(anchor="w", pady=(8, 0))
        Label(right, textvariable=self.emotion_var, justify=LEFT).pack(anchor="w", pady=(8, 0))
        Label(right, textvariable=self.event_var, justify=LEFT).pack(anchor="w", pady=(8, 0))
        Label(right, textvariable=self.trigger_var, justify=LEFT, wraplength=340).pack(anchor="w", pady=(8, 12))

        Label(right, text="记忆库", font=("Microsoft YaHei UI", 11, "bold")).pack(anchor="w")
        self.memory_list = Listbox(right, height=8)
        self.memory_list.pack(fill="x", pady=(4, 6))
        self._refresh_memories()

        self.memory_entry = Entry(right)
        self.memory_entry.pack(fill="x")
        Button(right, text="把这句话加入记忆", command=self._add_memory).pack(fill="x", pady=(4, 12))

        Label(right, text="事件手动注入", font=("Microsoft YaHei UI", 11, "bold")).pack(anchor="w")
        for name, label in [
            ("affection", "亲昵/夸奖"),
            ("fight", "争吵/受伤"),
            ("apology", "道歉/修复"),
            ("reject", "拒绝/疏远"),
            ("long_silence", "长沉默"),
        ]:
            Button(right, text=label, command=lambda n=name: self._manual_event(n)).pack(fill="x", pady=2)

    def _save_config(self) -> None:
        self.config = GuiConfig(
            base_url=self.base_url_var.get().strip(),
            model=self.model_var.get().strip(),
            api_key=self.key_var.get().strip(),
        )
        save_config(self.config)
        self._append("系统", "配置已保存到本地 .maidsoul_local。")

    def _send(self) -> None:
        text = self.input.get().strip()
        if not text:
            return
        self.input.delete(0, END)
        self._append("主人", text)

        event = classify_event(text)
        self.state.apply_event(event)
        trigger, score = self.memory.trigger(text)
        self.state.last_trigger = trigger
        self.state.trigger_score = score
        self.state._update_active_curiosity()
        self._refresh_state()

        self.history.append(("user", text))
        messages = build_messages(self.history, self.state, trigger)
        self.input.configure(state="disabled")
        threading.Thread(target=self._worker_call, args=(messages,), daemon=True).start()

    def _worker_call(self, messages: list[dict]) -> None:
        reply = call_llm(load_config(), messages)
        self.jobs.put(("reply", reply))

    def _poll_jobs(self) -> None:
        try:
            while True:
                kind, payload = self.jobs.get_nowait()
                if kind == "reply":
                    self.history.append(("assistant", payload))
                    self._append("女仆", payload)
                    self.state.apply_event("reply_fast")
                    self._refresh_state()
                    self.input.configure(state="normal")
                    self.input.focus_set()
        except queue.Empty:
            pass
        self.root.after(100, self._poll_jobs)

    def _append(self, speaker: str, text: str) -> None:
        self.chat.configure(state="normal")
        self.chat.insert(END, f"{speaker}：{text}\n\n")
        self.chat.see(END)
        self.chat.configure(state="disabled")

    def _refresh_state(self) -> None:
        self.stage_var.set(
            f"stage 关系阶段：{STAGE_LABELS[self.state.stage]}\n"
            f"intimacy 亲密/靠近程度：{self.state.intimacy.value:.2f}\n"
            f"conflict 冲突/受伤程度：{self.state.conflict.value:.2f}\n"
            f"boosted_probability 主动概率参考：{self.state.active_curiosity / 100:.2f}"
        )
        emotion = self.state.emotion_name()
        self.emotion_var.set(
            f"emotion 当前情绪：{EMOTION_LABELS[emotion]}\n"
            f"valence 愉快/难过：{self.state.valence.value:.2f}\n"
            f"arousal 平静/激动：{self.state.arousal.value:.2f}\n"
            f"dominance 弱势/掌控感：{self.state.dominance.value:.2f}"
        )
        self.event_var.set(f"event 最近事件：{EVENT_LABELS.get(self.state.last_event, self.state.last_event)}")
        self.trigger_var.set(
            f"memory_trigger 触发记忆：{self.state.last_trigger or '无'}\n"
            f"trigger_score 记忆触发强度：{self.state.trigger_score:.2f}"
        )

    def _refresh_memories(self) -> None:
        self.memory_list.delete(0, END)
        for item in self.memory.memories:
            self.memory_list.insert(END, item["text"])

    def _add_memory(self) -> None:
        text = self.memory_entry.get().strip()
        if not text:
            return
        self.memory.add(text)
        self.memory_entry.delete(0, END)
        self._refresh_memories()
        self._append("系统", f"已加入记忆：{text}")

    def _manual_event(self, event: str) -> None:
        self.state.apply_event(event)
        if event == "long_silence":
            self.state.step_time(24)
        self._refresh_state()
        self._append("系统", f"已注入事件：{event}")

    def _step_time(self, hours: float) -> None:
        self.state.step_time(hours)
        self._refresh_state()
        self._append("系统", f"时间流逝：{hours:g} 小时")

    def _silence(self) -> None:
        self.state.apply_event("long_silence")
        self.state.step_time(24)
        self._refresh_state()
        self._append("系统", "模拟 24 小时沉默。")

    def run(self) -> None:
        self.input.focus_set()
        self.root.mainloop()


def main() -> None:
    LOCAL_CONFIG.parent.mkdir(parents=True, exist_ok=True)
    if not LOCAL_CONFIG.exists():
        save_config(load_config())
    ChatGui().run()


if __name__ == "__main__":
    main()
