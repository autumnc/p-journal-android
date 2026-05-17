#!/usr/bin/env python3
"""
个人日记 — 每日写作练习工具。
条目一经写入即保存为纯文本。

用法:
    python3 journal.py
"""

import base64
import curses
import hashlib
import json
import locale
import os
import random
import re
import sys
import textwrap
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone

# ── 配置 ──────────────────────────────────────────────────────────────────

JOURNAL_DIR = os.path.expanduser("~/journal")
FILE_EXT = ".txt"
TAB_WIDTH = 4
CONFIG_FILE = os.path.expanduser("~/.pjournal")
SYNC_STATE_FILE = os.path.expanduser("~/.pjournal_sync_state")

# Flomo API 常量
FLOMO_API_BASE = "https://flomoapp.com/api/v1"
FLOMO_API_KEY = "flomo_web"
FLOMO_APP_VERSION = "4.0"
FLOMO_PLATFORM = "web"
FLOMO_SIGN_SECRET = "dbbc3dd73364b4084c3a69346e0ce2b2"
FLOMO_TIMEZONE = "8:0"

# Deepseek API 常量
DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions"

# ── CJK 字符宽度支持 ─────────────────────────────────────────────────────

def char_width(ch):
    """获取字符的显示宽度（CJK 字符为2，其他为1）。"""
    cp = ord(ch)
    if (0x1100 <= cp <= 0x115F or
        0x2E80 <= cp <= 0x303E or
        0x3040 <= cp <= 0x9FFF or
        0xAC00 <= cp <= 0xD7A3 or
        0xF900 <= cp <= 0xFAFF or
        0xFE30 <= cp <= 0xFE6F or
        0xFF01 <= cp <= 0xFF60 or
        0xFFE0 <= cp <= 0xFFE6 or
        0x20000 <= cp <= 0x2FFFD or
        0x30000 <= cp <= 0x3FFFD):
        return 2
    return 1

def string_width(s):
    """获取字符串的显示宽度。"""
    return sum(char_width(ch) for ch in s)

# ── 写作提示词 ─────────────────────────────────────────────────────────────

PROMPTS = [
    "今天发生了什么？",
    "今天发生的最好的事情是什么？",
    "今天发生的最糟糕的事情是什么？",
    "今天我看到或听到的最有趣的事情是什么？",
    "今天我面临的最大挑战是什么？",
    "今天我感恩什么？",
    "今天我学到了什么？",
    "今天我做的最有趣的事情是什么？",
    "今天发生的最令人惊讶的事情是什么？",
    "今天我做了什么让自己感到自豪的事？",
    "关于这个问题或挑战，我的目标和目的是什么？",
    "针对这个问题或挑战，有哪些潜在的解决方案？",
    "有哪些创造性和非传统的解决方案可以考虑？",
    "每种潜在解决方案的优缺点是什么？",
    "我如何与他人合作找到解决方案？",
    "我可以利用哪些资源来帮助解决这个挑战？",
    "我如何将我的技能、知识和经验运用到这个挑战中？",
    "实施解决方案可能遇到哪些障碍，我如何克服它们？",
    "我如何优先排序和整理我的想法，以有效地解决这个问题？",
    "关于我的身体，我需要放下哪些信念或观念，才能培养更多的自爱和接纳？",
    "哪些活动或练习帮助我与身体保持连结和协调？",
    "我如何对自己的身体更加慈悲，尤其是在自我批评或消极的时候？",
    "社交媒体或媒体在塑造我的身体形象方面扮演了什么角色，我如何与这些影响来源建立更积极的关系？",
    "如果我放下与他人比较身体的需要，转而关注自己独特的优势和美丽，会是什么感觉？",
    "我如何优先关注身体健康和幸福，而不陷入节食文化或身材羞辱的陷阱？",
    "我如何将注意力从基于外貌的目标转向更全面的健康衡量标准（如精力水平、情绪、力量等）？",
    "真正体现自爱和身体积极意味着什么，我如何每天采取小步骤朝这个方向前进？",
    "我如何培养对身体的欣赏和热爱，即使它不符合社会理想？",
    "我可以用哪些方式庆祝和照顾我的身体，不论它的形状和大小？",
    "我如何在日常生活中运用创造力？",
    "有什么东西是我一直想创造的，我可以采取哪些步骤使之成为现实？",
    "有什么地方或环境能激发我的创造力，我如何创造更多机会待在那个空间？",
    "我的热情和兴趣是什么，我如何将它们融入我的工作或个人生活？",
    "今天我可以做的一个小型创意项目是什么，我如何使之具有我的个人风格？",
    "有什么恐惧或障碍在创意方面阻碍着我，我能做些什么来克服它？",
    "有什么东西我可以学习或尝试，以拓展我的创意技能和知识？",
    "我可以给自己什么挑战或提示来推动自己创意地前进？",
    "我可以用什么方式创意地表达对生活中某人的感恩、爱或欣赏？",
    "我如何挑战自己跳出框架思考，拥抱新的创意想法？",
    "我如何让自己置身于促进创意和灵感的人群和环境中？",
    "我可以通过哪些方式给自己时间充电，以培养创造力和灵感？",
    "我可以追求哪些爱好或活动来开发我的创造力和想象力？",
    "我如何在生活中融入更多的玩乐和乐趣来培养创造力和灵感？",
    "我可以用哪些方式走出舒适区、尝试新事物来激发创造力和灵感？",
    "我如何变得更加开放，接受新的想法和观点？",
    "我可以用哪些方式利用技术和创新来增强我的创造力和灵感？",
    "我如何寻找新体验和冒险来拓展视野、激发创造力？",
    "我如何为我的心灵、身体和灵魂创造一个支持和滋养的环境来鼓励创造力和灵感？",
    "从一个无生命物体复活的角度写一个故事。",
    "写一首关于一直伴随你的童年记忆的诗。",
    "写一个角色某天醒来获得了超能力的故事。",
    "写一首关于季节更替和自然之美的诗。",
    "写一个以「门吱呀一声打开了，露出一个被遗忘已久的房间」开头的故事。",
    "写一个关于一群人被困在荒岛上的故事。",
    "写一首探索时间概念及其如何塑造我们生活的诗。",
    "写一个从失去记忆、试图拼凑过去的角色视角出发的故事。",
    "写一首反思日常时刻之美的诗。",
    "写一个时间旅行者意外困在错误时代的故事。",
    "写一段教会你关于自己或周围世界重要一课的关系。",
    "写一个角色发现一本藏有隐秘信息的神秘之书的故事。",
    "写一首以水为主题传达更深层意义或情感的诗。",
    "写一个对你生活有重大影响的地方，以及它带给你的回忆或情感。",
    "写一个角色被迫面对最深层恐惧的故事。",
    "写一首探索家的概念及其对你意义的诗。",
    "写一个从试图在荒野中生存的动物视角出发的故事。",
    "写一段教会你关于宽恕或接纳的宝贵一课的经历。",
    "写一个角色收到一封来自失散多年的亲戚的信，信中有一个惊人真相的故事。",
    "今天我的身体感觉如何？",
    "今天我紧张或焦虑的是什么？",
    "针对每件让我紧张或焦虑的事情，我可以采取什么行动？",
    "今天的首要优先事项是什么？",
    "我可以做什么让今天变得精彩？",
    "今天我学到了什么？我如何在未来运用这些知识？",
    "今天我面临了什么挑战？我是如何克服的？我可以从这些经历中学到什么？",
    "今天我做了什么让我感到快乐或满足的事？我如何将更多这样的活动融入日常？",
    "今天哪个时刻让我感到快乐、愉悦或满足？",
    "今天我注意到了什么小细节？",
    "今天的天气怎么样？",
    "今天我感谢什么？",
    "今天我本可以怎么做会不一样？",
    "我如何让明天变得更好？",
    "我需要做什么决定？",
    "我需要在什么时候做出这个决定？",
    "我希望达到的理想结果是什么？",
    "每个选项的优缺点是什么？",
    "我对做这个决定有什么恐惧或担忧？",
    "我从过去类似的决定中获得了什么见解或教训？",
    "这些教训或见解如何适用于当前情况？",
    "如果朋友处于同样的情况，我会给什么建议？",
    "我的直觉对这个决定告诉我什么？",
    "这个决定对我自己和他人会有什么影响？",
    "这个决定如何与我的价值观一致？",
    "我需要什么资源或支持才能自信和清晰地做出这个决定？",
    "如果我做出这个决定，最坏的情况是什么？",
    "我有什么事实来支持我的决定？",
    "我对自己的决定感觉如何？",
    "我对这个决定有多大信心？",
    "关于这个决定，我的下一步是什么？",
    "昨晚我做过的最难忘的梦是什么？写下你能记住的所有细节。",
    "我的梦中出现了哪些反复出现的主题或符号？我能识别出什么模式吗？",
    "我在梦中感受到了什么情绪，它们是否与我现实生活中的某些问题有关？",
    "我认为我的梦在试图告诉我什么？我如何将它的信息运用到生活中？",
    "如果我今晚可以做任何梦，它会是什么？",
    "如果我可以问梦中的角色一个问题，我会选择谁，问什么？",
    "我做过的最离奇或最超现实的梦是什么？我认为它们意味着什么？",
    "我最常做的梦是什么类型（如噩梦、飞行梦等）？我认为这反映了我的心理什么？",
    "今天哪三件事进行得很顺利，为什么？",
    "今天的高光时刻是什么？",
    "今天哪三件事我本可以做得不同，我如何从这些经历中学习？",
    "今天我学到了什么？",
    "今天我如何表达了感恩？",
    "今天我面临了什么挑战，我是如何克服的？",
    "今天我做了什么来照顾自己？",
    "今天我做了什么来帮助他人？",
    "今天我如何安排了我的时间优先级？",
    "今天我做了什么为一天带来积极性？",
    "今天我做了什么让自己感到自豪？",
    "今天最重要的事件是什么？",
    "在一天的不同时刻我感觉如何？",
    "今天发生了什么意外事件？",
    "今天我和谁互动了，那些互动是怎样的？",
    "今天我完成了什么？",
    "明天我想做些什么不同的事？",
    "今天我做了什么来放松和充电？",
    "今天我体验了哪些景象、声音和气味？",
    "我今天如何处理了出现的困难情况？",
    "我期待明天的什么？",
    "今天我体验了什么情绪？",
    "我对每种情绪如何回应？什么触发了每种情绪？",
    "今天我做了什么对别人的日子产生了积极影响？",
    "我期待明天的什么？",
    "我可以做什么来准备一个安宁的睡眠？",
    "今天最重要的事件是什么，为什么重要？",
    "今天我如何处理了冲突或困难情况？",
    "今天我对自己了解了什么？",
    "明天我可以做些什么不同的事来度过更好的一天？",
    "谁对我的今天产生了积极影响，如何影响的？",
    "我做了什么让别人的一天变得更好？",
    "关于今天我想记住什么？",
    "什么傻事总能让我笑？",
    "什么童年记忆仍然给我带来快乐？",
    "如果我可以生活在任何时代或地方，我会选择哪里，为什么？",
    "我最喜欢的餐食或食物类型是什么，为什么我这么喜欢它？",
    "如果我可以拥有任何超能力，会是什么，为什么？",
    "什么书或电影总能让我心情变好，为什么？",
    "有什么东西我一直想尝试但还没试过？我如何使之实现？",
    "有哪一样东西我无法没有？",
    "关于我的生活有什么有趣的故事，我不介意和别人分享的？",
    "关于我自己有什么我知道很古怪的地方？",
    "如果我可以成为任何虚构角色，我会选择谁，为什么？",
    "我穿过的最夸张的服装或装扮是什么？我在哪里穿的，感觉如何？",
    "我最喜欢的傻笑话或双关语是什么，为什么让我笑？",
    "我送出的最好的礼物是什么，为什么那么特别？",
    "如果我是超级英雄，我的名字、能力和服装会是什么？",
    "我对别人玩过的最有趣的恶作剧，或别人对我玩过的最有趣的恶作剧是什么？",
    "如果我可以神奇地与任何人互换生活一天，我会选择谁，为什么？",
    "我最喜欢的童年玩具或游戏是什么，为什么我那么喜欢它？",
    "我最喜欢的舞蹈动作是什么，我能教给别人吗（或用文字描述）？",
    "如果我可以去世界上（或更远）的任何地方旅行，我会去哪里，做什么？",
    "明年我的三大目标是什么？",
    "我可以采取哪些可行的步骤来实现我的目标？",
    "下个月我想养成的一个新习惯是什么？",
    "我如何制定一个计划使这个新习惯成为日常的一部分？",
    "明年我想发展的三个技能或知识领域是什么？",
    "我可以寻求什么资源或支持来帮助实现目标？",
    "阻碍我实现目标的三个因素是什么？",
    "我如何努力克服这些障碍？",
    "本周我可以为自己设定的三个小的、可衡量的目标是什么？",
    "我将如何让自己对完成目标负责？",
    "我的长期职业目标是什么？我可以采取哪些具体步骤来接近实现它们？",
    "我的个人价值观是什么，它们与我的目标有什么关系？",
    "我如何确保我的目标与我的价值观一致？",
    "在朝目标努力的过程中，我可能遇到哪些潜在的障碍或挑战？",
    "我如何制定一个计划来克服这些障碍或挑战？",
    "我如何追踪我朝目标的进展？",
    "我可以使用什么工具或系统来保持动力和正轨？",
    "每天我可以为自己设定的三个小的、具体的目标是什么？",
    "我如何确保我的日常行动与更大的目标和优先级一致？",
    "为了实现目标我需要什么习惯？",
    "我生活中感恩的三个人是谁，为什么？",
    "今天发生的三件小事让我感恩的是什么？",
    "生活中有什么东西我常常视为理所当然，我如何培养对它更多的欣赏和感恩？",
    "我拥有哪些积极的品质或优势，我如何为此感恩？",
    "生活中有什么让我觉得「幸运」拥有的东西？",
    "最近我一直享受的小确幸是什么？",
    "最近我感恩学到了什么？",
    "过去一年我在哪些方面成长了？",
    "我喜欢我现在住的地方的什么？",
    "今天有哪些快乐时刻？",
    "表达感恩让我现在感觉如何？",
    "今天我如何表达我的感恩？",
    "我可以用哪些方式表达感恩，欣赏周围世界的美和奇妙？",
    "我生活中哪些方面倾向于固定思维？",
    "我如何转变思维采用成长型思维？",
    "有哪些目标我因为害怕失败或被拒绝而不敢追求？",
    "我如何重新构建思维，将失败视为学习过程的自然部分，并将其作为成长的机会？",
    "我的一些限制性信念和自我对话可能正在阻碍我？",
    "我如何挑战并克服它们？",
    "我如何将挑战和失败视为成长和发展的机会，而不是挫折？",
    "即使在逆境和困难面前，我如何培养积极和乐观的态度？",
    "我可以用哪些方式寻求反馈和建设性批评来持续成长和改进？",
    "我如何在个人和职业生活中追求进步而非完美？",
    "我的一些优势和成长领域是什么，我如何利用这些知识推动个人发展？",
    "我如何寻找新体验、机会和关系来拓展视野并支持个人成长？",
    "我如何在面对障碍和挑战时培养韧性和毅力？",
    "我如何对自己的思想、感受和行动负责，并将其作为成长和发展的机会？",
    "我如何将错误和失败视为学习机会而非挫折或障碍？",
    "我想发展哪些新技能或知识领域？",
    "我如何培养好奇和开放的心态，寻求新的信息和知识来支持成长和发展？",
    "我可以用哪些方式采取主动而非被动的方式来应对挑战和困难？",
    "我对童年有什么记忆？有哪些快乐的记忆特别突出？",
    "我小时候最喜欢的活动是什么？我有什么爱好或兴趣特别喜欢？",
    "我小时候的空闲时间是怎么度过的？我玩什么游戏？读什么书？",
    "我最喜欢学校的什么？我有最喜欢的科目或老师吗？",
    "我小时候有什么梦想或抱负？我长大后想成为什么？",
    "我童年时面临的一些挑战或困难是什么？那些经历如何塑造了我？",
    "我的家庭和成长环境如何影响了我童年的经历？我受到了什么积极或消极的影响？",
    "我在童年形成了哪些可能至今仍在影响我的信念或态度？",
    "我现在如何滋养和照顾内心的孩子？什么活动或经历带给我喜悦和玩乐？",
    "我可以从内心的孩子那里学到什么？我如何挖掘童年时的好奇心、创造力和韧性？",
    "童年时哪些活动或经历带给我快乐？",
    "我现在如何将这些活动融入生活？",
    "我如何滋养内心的孩子，培养一种玩乐和惊奇的感觉？",
    "我上一次感到受启发是什么时候？",
    "我通常在哪里找到灵感？",
    "什么东西启发我？",
    "谁是启发我的人，他们拥有什么我欣赏的品质？",
    "有一本书或电影启发了我，是什么，为什么？",
    "我最喜欢的一些艺术、文学或媒体形式是什么，它们如何启发我？",
    "有一句名言或格言启发了我，我如何将它的智慧运用到生活中？",
    "有一个创意项目我一直想做的，我可以采取什么步骤开始？",
    "我上一次对某事感到完全敬畏是什么时候，是什么激发了那种感觉？",
    "有什么东西我一直想学，我如何腾出时间来追求这个兴趣？",
    "我每天可以做的一件小事来培养更大的灵感和创造力是什么？",
    "这个月/这周/今天我想关注什么？",
    "我今天的目标是什么？",
    "我最大的「为什么」（我目标背后的深层目的或动力）是什么？",
    "我如何用我的「为什么」来保持专注和投入？",
    "我如何相应地优先安排我的时间和精力？",
    "有哪些外部因素可能影响我专注于目标，我如何提前规划来应对？",
    "有哪些干扰或浪费时间的事情我需要消除，以专注于真正重要的事？",
    "什么带给我最大的快乐和满足，我如何为这些事腾出时间？",
    "幸福对我意味着什么？我可以做什么来培养更多的幸福和满足？",
    "我现在面临什么决定？",
    "我如何定义成功？我可以采取什么步骤来实现它？",
    "我的恐惧和不安全感是什么？我如何努力克服它们变得更加自信？",
    "我生活中最重要的关系是什么？我如何加强它们？",
    "总体来说，我对生活目前的状况感觉如何？",
    "我生活中哪些方面目前感觉停滞不前？我可以采取什么步骤来推进？",
    "我最近在生活中注意到了什么主题、模式或符号？",
    "我对自己或周围世界持有一些什么信念或假设？",
    "当我面临挑战或障碍时，我通常的反应是什么？",
    "有哪些活动或习惯消耗我的精力或动力？",
    "我通常如何处理我的情绪和感受？有什么情绪我倾向于回避或压抑吗？",
    "我生活中最感恩的事情是什么？我如何培养更多的感恩和欣赏？",
    "我对失去的那个人最珍贵的记忆是什么？",
    "我有什么话或什么事希望在那个人离开之前说或做的？",
    "面对失去最困难的是什么？",
    "我如何找到应对悲伤的方法？",
    "这次失去如何影响了我的日常生活？",
    "因为这次失去，我对自己或对生活学到了什么？",
    "我可以采取哪些积极的步骤来纪念我失去的那个人？",
    "我如何在这段困难时期找到支持和安慰？",
    "在我走过悲伤的过程中，我可以向谁寻求关怀和支持？",
    "有哪些健康的方式可以处理我的悲伤，比如通过运动、冥想或艺术、音乐等创意途径？",
    "发生了什么让这段时间如此困难？",
    "是什么引起了我的痛苦？",
    "我可以向谁寻求支持？",
    "过去我是如何度过困难时期的？",
    "即使在艰难的环境中，我感谢什么？",
    "我如何在逆境中培养欣赏和乐观？",
    "过去什么自我关怀做法帮助过我？",
    "我可以从这段经历中学到什么？我可能学到什么教训？",
    "我如何重新审视这个情况？",
    "我可以采取什么行动来改善这个情况？",
    "我现在生活中有什么积极的东西？",
    "我现在可以做什么来照顾自己？",
    "我的个人价值观和信念是什么？它们如何塑造我的身份？",
    "我在生活中承担了哪些角色？这些角色如何贡献于我的身份感？",
    "我如何通过与他人的关系来定义自己？这些关系如何塑造我的自我认知？",
    "我对我的文化或种族背景了解什么？它如何塑造我的身份？",
    "我拥有哪些优势、才能或独特品质？它们如何贡献于我的自我认知？",
    "我的外貌如何塑造我的身份感？",
    "哪些生活经历塑造了今天的我？",
    "我对自己的身份有什么恐惧或怀疑？我如何以健康的方式应对？",
    "我如何平衡对个性的需求与对归属感的需求？",
    "我想要在生活中实现或完成什么？这些目标如何贡献于我的身份感？",
    "我最早的童年记忆之一是什么？",
    "这段记忆唤起了什么情绪？",
    "童年的一个快乐记忆是什么？为什么那么特别？",
    "过去一段困难的记忆是什么？这段记忆如何塑造了我？",
    "我成长过程中最亲密的朋友是谁？他们对我的人生有什么影响？",
    "我成长过程中的榜样或导师是谁？他们对我的人生有什么影响？",
    "我成长过程中最喜欢的爱好或活动是什么？我现在还喜欢吗？",
    "我人生中达成的一些重要里程碑或成就是什么？它们让我感觉如何？",
    "我经历过的最具挑战性或最变革性的经历是什么？它们如何塑造了我的观念或价值观？",
    "我人生中最大的惊喜或意外转折是什么？我是如何应对这些变化的？",
    "哪些人或经历给我带来了最多的快乐或意义？我如何在当下培养更多这些积极影响？",
    "我最喜欢的爱好或活动是什么？",
    "我最喜欢的爱好或活动让我感觉如何？",
    "如果我拥有所需的所有时间和资源，我会追求什么活动或爱好？",
    "我最喜欢的爱好中我最享受的是什么？我如何将更多这样的东西融入生活？",
    "我认识谁与我分享同样的热情或爱好，我们如何合作或互相支持？",
    "我拥有什么技能可以应用于新的爱好或活动？",
    "有什么东西我一直想尝试但还没试过，是什么在阻碍我？",
    "如果我能把我的热情或爱好变成职业或副业，我可以采取什么步骤使之实现？",
    "我害怕什么？",
    "我的恐惧来自哪里？它的根源是什么？",
    "我的恐惧如何影响我的生活？它在哪些方面阻碍了我？",
    "如果没有这种恐惧，我的生活会是什么样？我能实现或体验什么？",
    "我如何重新审视我的恐惧？有没有不同的方式看待这个问题？",
    "我可以采取什么步骤来面对恐惧？我可以采取什么行动来穿越它？",
    "我可以向谁寻求支持？谁可以帮助我面对恐惧？",
    "从过去面对恐惧的经历中我学到了什么？什么有效，什么无效？",
    "我如何将恐惧转化为动力？我能把恐惧变成推动我前进的积极力量吗？",
    "如果我面对恐惧，最坏的情况是什么？最好的情况是什么？",
    "有哪些恐惧或限制性信念在阻碍我？",
    "我如何努力克服它们？",
    "我可以寻求什么资源或支持来帮助克服恐惧？",
    "我现在感受到什么情绪？写下任何浮现在脑海中的情绪，无论大小。",
    "我在身体的哪个部位感受到这种情绪？当我感受这种情绪时体验到什么身体感觉？它是否在身体的某个部位或以特定方式表现出来？",
    "什么触发了这种情绪？是一个想法、一段记忆，还是某人说了或做了什么？",
    "我如何回应这种情绪？",
    "我上一次有这种感觉是什么时候？",
    "我最常感受到什么情绪？",
    "我回避感受什么情绪？",
    "今天我的情绪如何影响了我的想法和行为？",
    "我如何以健康的方式表达这种情绪？",
    "我可以从这种情绪中学到什么？思考这种情绪如何教会你关于自己、价值观或需求的东西。",
    "今天有哪些压力或沮丧的时刻？",
    "今天有哪些平静或安宁的时刻？",
    "今天我如何处理了消极情绪？",
    "我将来如何更好地应对困难情绪？",
    "我可以用哪些方式在生活中促进积极性和幸福感？",
    "我如何通过这种情绪支持自己？写下自我关怀策略，帮助你在体验这种情绪时感觉更加踏实和居中。",
    "此时此刻正在发生什么？",
    "我现在能看到五样东西是什么，它们有什么颜色、形状和质地？",
    "如果我的心灵现在像海洋，水面是什么样的？",
    "我现在在观察什么想法？",
    "在此时此刻我得到了什么感官信息？",
    "我现在能听到的三样东西是什么，它们听起来如何？",
    "我现在能身体上感受到的三样东西是什么，比如身体在椅子上的重量或衣服的质地？",
    "我现在能闻到的三样东西是什么，它们的气味如何？",
    "我现在能尝到的三样东西是什么，它们的味道如何？",
    "我现在感受到什么情绪，我如何对它们练习接纳和自我慈悲？",
    "现在我的脑海中掠过什么想法，我如何承认它们而不被它们困住？",
    "在接下来的一小时里我期待的三件事是什么，我如何保持临在并充分体验它们？",
    "现在让我担心的三件事是什么，我如何通过正念练习来减少压力和焦虑？",
    "我现在可以采取的三个小行动来将自己带回当下是什么，比如深呼吸、伸展或品味一口茶或咖啡？",
    "今天早上我心中想的是什么？",
    "今天我期待什么？",
    "今天我需要做什么？",
    "今天我的目标是什么？",
    "今天我可以用哪些方式提高效率？",
    "今天我可以做什么来照顾我的身心健康？",
    "今天我可能面临什么挑战，我如何为它们做准备？",
    "今天我如何优先关注自我关怀？",
    "今天我可以向谁寻求支持？",
    "今天我可以做一件什么来帮助别人？",
    "今天我如何为一天带来积极性？",
    "我可以告诉自己什么积极的自我肯定来以积极的方式开始这一天？",
    "我今天想培养什么心态或态度？我如何在一整天中提醒自己？",
    "今天什么让我庆幸活着？",
    "在新的一年开始之际，我最感恩什么？",
    "过去一年教会了我什么教训？",
    "去年我完成了哪三件事？",
    "今年什么价值观将指引我的选择？",
    "今年我想更经常地品味或享受什么？",
    "今年我希望完成的三个目标是什么？",
    "今年我想学习或提升什么新技能？",
    "哪些关系对我是最重要的？今年我如何继续投入这些关系？",
    "今年我想解决什么问题？",
    "今年我想如何成长或发展？",
    "今年我想养成的一个习惯是什么？",
    "在接下来的一年里我想为别人做什么？",
    "在接下来的一年里我想为自己做什么？",
    "今年我如何优先关注我的健康和/或健身？",
    "今年我想尝试什么新体验？",
    "今年我想去什么新地方？",
    "今年我想开始什么新的创意项目或爱好？",
    "今年我想克服什么恐惧？",
    "今年我如何表达更多的感恩？",
    "今年我如何更经常地休息或放松？",
    "在接下来的一年里我期待什么？",
    "我想给今年什么词或短语？",
    "我对来年最大的梦想是什么？",
    "我想在日常生活中体现的三个品质是什么？",
    "我最大的恐惧是什么，我如何克服它们？",
    "有哪些限制性信念在阻碍我，我如何挑战它们？",
    "有哪些习惯我想养成或戒掉，我如何朝着这些目标取得进展？",
    "有哪些过去的错误或失败教会了我宝贵的教训，我如何将这些教训运用到当前的生活中？",
    "我如何设定并朝着可达到但又有挑战性的目标努力来推动个人成长？",
    "我如何更主动和有意识地寻找成长机会，而不是等待机会来到我面前？",
    "我如何平衡冒险和走出舒适区与照顾自己和身心健康？",
    "我如何在我困难的领域发展成长型思维，比如公开演讲或自我推广？",
    "我如何寻求和拥抱建设性批评和反馈，并将其作为成长和发展的机会？",
    "我如何为自己和他人的关系培养一个支持和鼓励的成长环境？",
    "我可以用哪些方式为我的社区或周围的世界做贡献？",
    "我如何在关系中沟通我的需求和界限？",
    "我可以用哪些方式加深与亲人的连结？",
    "在关系方面我的价值观和优先事项是什么？",
    "这些价值观如何影响我的行动和选择？",
    "我如何回应关系中的冲突？",
    "当事情变得困难时，我注意到什么沟通模式？",
    "我可以用哪些方式对生活中的人表达欣赏和感恩？",
    "我如何表达爱和关怀？",
    "我在关系中面临什么挑战？我如何努力改善这些挑战？",
    "我如何处理与亲人的分歧或意见不合？有哪些健康的方式来应对这些情况？",
    "我的关系目标是什么？",
    "我在当前关系中或未来关系中想实现什么？",
    "在关系中我需要在哪些方面努力设定界限？我如何创建更健康的界限？",
    "我如何平衡自己的需求与伴侣或亲人的需求？有什么方式确保双方都感到被倾听和尊重？",
    "我如何在关系中管理压力和情绪？",
    "有哪些技巧可以管理关系中的焦虑或其他困难情绪？",
    "我的爱的语言是什么？我如何向伴侣或亲人传达爱和关怀？",
    "我如何定义自我关怀？",
    "自我关怀在我的心理、身体和情感健康中扮演什么角色？",
    "我最喜欢的自我关怀形式是什么？",
    "我可以用哪些方式优先关注身体健康和幸福来照顾自己？",
    "我喜欢什么形式的运动？",
    "什么活动帮助我感到平静和居中？",
    "我如何将健康饮食融入日常生活？",
    "我如何帮助自己获得充足的睡眠？",
    "有哪些活动或爱好带给我快乐和放松？我如何为它们腾出时间？",
    "我如何更好地管理和减少压力和焦虑？",
    "我如何探索正念练习或冥想？",
    "我如何向他人寻求支持？",
    "在困难或充满挑战的时期，我如何优先关注自我关怀，避免忽视自己的需求？",
    "我如何与他人设定界限，确保我有时间和精力进行自我关怀？",
    "我如何寻找并与支持和积极的关系建立连结，这些关系能够提升和赋予我力量？",
    "我如何识别和应对有毒或不健康的模式或行为，并朝着为我的幸福做出积极改变而努力？",
    "我如何培养自我慈悲和自我宽恕，避免自我批评和消极的自我对话？",
    "当我感到不堪重负或精疲力竭时，我如何优先关注自我关怀，并采取措施防止未来的倦怠？",
    "我上一次休息或给自己放假是什么时候？感觉如何？",
    "我的核心价值观是什么？花些时间反思生活中最重要的价值观，以及它们为什么对你重要。",
    "我什么时候感觉最有活力？反思那些让你感觉完全临在、投入和充满能量的时刻、经历和活动。",
    "什么给了我生命的意义或目的？考虑对你最重要的活动、关系、事业和价值观。",
    "我的优势和弱点是什么？考虑你擅长的和你挣扎的方面。",
    "我如何利用我的优势并改善我的弱点？考虑以新方式运用你的技能、知识或才能的方法。",
    "我的目标和抱负是什么？写下你的短期和长期目标，以及你需要采取的步骤来实现它们。",
    "我的热情和兴趣是什么？想想那些启发和激励你的活动、话题和事业。你如何将更多这些东西融入生活？",
    "我的恐惧和限制性信念是什么？探索那些可能阻碍你发挥全部潜力的恐惧和信念。你如何挑战和克服它们？",
    "我理想的生活是什么样的？设想你想为自己创造的生活，以及你需要采取什么步骤使之成为现实。",
    "我人生中最具决定性的时刻是什么？反思那些塑造了今天的你的经历，以及你从中学到了什么。",
    "哪些活动带给我最大的快乐和满足？",
    "我希望在世界上产生什么影响？反思你如何将日常行动与更深层的使命感对齐。",
    "我的兴趣如何随时间变化？回顾过去，反思你曾经喜欢的活动以及你现在喜欢的活动。",
    "我的一些最难忘和最有意义的经历是什么？它们如何激励我前进？",
    "我如何拥抱生活中的变化和新机会？",
    "有什么事情让我感到自信？",
    "我过去如何克服挑战，我从那些经历中学到了什么？",
    "今天我可以做一件什么事来走出舒适区并建立自信？",
    "我有哪些消极的自我对话模式，我如何以更积极的方式重新构建这些想法？",
    "我的优势是什么，我如何利用它们来实现我的目标？",
    "别人过去给过我哪些赞美，我如何内化这些积极的信息？",
    "在感到不确定或怀疑的时刻，我如何照顾自己并练习自我慈悲？",
    "我会对一个在自信方面挣扎的朋友说什么，我如何将这个建议运用到自己的生活中？",
    "我如何拥抱我的独特品质并善加利用？",
    "今天我可以采取什么步骤来朝一个能建立自信的目标前进？",
    "我有哪些独特的品质和优势，我如何更充分地拥抱和庆祝它们？",
    "这周我完成了哪三件让我自豪的事？",
    "今天我如何对自己更友善？",
    "我有哪些独特的优势，它们过去如何帮助了我？",
    "我对自己有什么消极的想法，我可以用什么积极的想法来挑战它？",
    "今天我可以做什么来在身体和情感上照顾自己？",
    "我爱自己的三件事是什么？",
    "过去一年我如何成长和变化？",
    "有什么积极的自我肯定我可以一整天对自己重复？",
    "今天我可以采取什么小步骤来朝个人目标或梦想前进？",
    "哪些价值观对我是重要的，它们如何指引我的决定和行动？",
    "我的哪些过去经历塑造了今天的我，它们如何影响了我的信念和态度？",
    "哪些事情带给我快乐和满足，我如何将更多这样的事情融入生活？",
    "我有哪些行为或思维模式在阻碍我，我如何努力打破这些模式？",
    "我有哪些人生目标或抱负，我可以采取什么步骤来朝它们努力？",
    "有哪些恐惧或不安全感在阻碍我，我如何努力克服它们？",
    "哪些关系对我是重要的，我如何滋养和加强这些关系？",
    "我的哪些过去的错误或失败教会了我宝贵的教训，我如何将这些教训运用到当前的生活中？",
    "哪些自我关怀做法对我是重要的，我如何将它们变成日常的一部分？",
    "生活中我感恩什么，我如何每天培养更多的感恩？",
    "今天什么触发了我的消极感受？",
    "我认为别人如何看待我？",
    "别人对我传达过什么关于我自己的信息？",
    "我如何回应赞美？",
    "我什么时候感到被重视和被爱？",
    "童年时我面临了什么挑战？",
    "我最好和最差的特质是什么？",
    "我需要原谅自己什么？",
    "我评判别人什么，为什么？",
    "我是否对什么感到内疚或羞耻？",
    "我如何支持别人，我是否也对自己展现同样的爱？",
    "我认为什么是健康的界限？",
    "我什么时候感到需要说谎，我说过的最严重的谎言是什么？",
    "我隐藏了自己的哪些部分？",
    "灵性对我意味着什么？",
    "灵性在我的日常生活中扮演什么角色？",
    "哪些灵性书籍、教诲或导师影响了我？我从这些来源学到了什么？",
    "我如何将灵性信念和实践融入我的日常？",
    "我如何定义我的信念和价值观？",
    "我的信念和价值观如何随时间演变？",
    "我如何与更高的力量或神圣连结？",
    "我发现哪些实践或仪式有助于滋养我的灵性？",
    "我如何将更多灵性融入日常生活？",
    "我如何探索我与神圣或更高力量的关系？",
    "关于灵性我有什么问题或不确定性？我如何探索这些问题并寻求答案？",
    "我如何运用灵性培养对他人的慈悲和共情，并为人类的更大福祉做贡献？",
    "我现在生活中压力的来源是什么？",
    "过去我是如何应对压力的？",
    "有哪些健康的应对机制我可以用来管理压力？",
    "我如何优先关注自我关怀来减少压力？",
    "我可以告诉自己什么积极自我肯定来对抗压力？",
    "当我感到有压力时，我可以向谁寻求支持和鼓励？",
    "我如何重新构建消极想法并保持积极的心态？",
    "有哪些活动或爱好帮助我放松和减压？",
    "我如何在家里或工作中创造一个无压环境？",
    "我可以采取什么步骤来防止压力在未来压倒我？",
    "生活中压力来源的一些实际解决方案是什么？",
    "我如何优先安排时间和责任来减少压力？",
    "我可以做哪些体育活动来缓解压力？",
    "我如何保持健康的工作与生活平衡来减少压力？",
    "我如何保持条理和正轨来减少压力？",
    "我如何在生活中找到幽默和快乐来对抗压力？",
    "我可以做哪些自我反思练习来减少压力？",
    "我如何保持健康的生活方式来减少压力，比如良好的饮食、充足的睡眠和规律的锻炼？",
    "我如何设定现实的期望和界限来减少压力？",
    "我可以做些什么来保持积极和放松的心态，比如冥想、练习正念或在大自然中度过时间？",
    "我目前正在前往哪里旅行，对这次旅行有什么期望？",
    "在这次旅行中我想体验和尝试什么新事物？",
    "关于我访问的地方的文化和人民，我想了解或理解什么？",
    "到达目的地时我感觉如何？第一印象是什么？",
    "旅行的第一天我做了什么？有什么亮点？",
    "我在这里期间想做什么或看什么？",
    "今天我做了什么？有什么亮点？",
    "今天我对所访问的地方了解了什么？",
    "我遇到了什么有趣的人？从他们身上学到了什么？",
    "我对所访问的地方有什么印象？",
    "我访问的地方有什么美丽或独特之处？",
    "到目前为止旅途中最难忘的时刻是什么，为什么？",
    "今天我看到了什么自然奇观？它们让我感觉如何？",
    "今天我参与了什么户外活动？是什么，它们如何挑战或启发了我？",
    "今天我遇到了什么当地的动植物？我了解到了什么？",
    "今天我有没有放松一下？我是怎么度过那段时间的？",
    "我对目前的旅途感觉如何？有什么让我惊喜的？",
    "在旅途结束之前我想尝试什么新事物？",
    "在这次旅途中我对自己了解了什么？",
    "这次旅行中我最感恩什么？",
    "旅行中我遇到了什么挑战，我是如何克服的？",
    "如果可以重新来一次这次旅行，我会做些什么不同的事？",
    "关于这个地方我最想念什么？",
    "在这次旅途中我遇到了谁对我有影响，我从他们身上学到了什么？",
    "关于我访问的地方有什么有趣的观察或见解？",
    "旅行中我对自己了解了什么，这次经历如何改变了我？",
    "我如何将旅行中的教训和经历运用到我的家庭生活中？",
    "如果我有飞行的能力会怎样？我会如何使用这种能力，去哪里？",
    "如果我可以住在世界上任何地方会怎样？我会选择哪里，为什么？",
    "如果我中了彩票会怎样？我的生活会怎样改变，我会用那些钱做什么？",
    "如果我可以和某人互换一天会怎样？我会选择谁，穿着他/她的鞋子我会做什么？",
    "如果我可以见到任何名人，无论在世还是已故会怎样？我会选择谁，问他们什么？",
    "如果我可以流利地说任何语言会怎样？我会选择哪种语言，用这种技能做什么？",
    "如果我可以重过我过去的任何一天会怎样？我会选择哪一天，会做些什么不同的事？",
    "如果我可以和任何动物说话会怎样？我会选择哪种动物，问它们什么？",
    "如果我在过去做了一个不同的关键决定会怎样？哪个决定会改变我人生的轨迹？",
]

# ── 日记状态 ───────────────────────────────────────────────────────────

def ensure_journal_dir():
    os.makedirs(JOURNAL_DIR, exist_ok=True)

def today_str():
    return datetime.now().strftime("%Y-%m-%d")

def entry_exists(date_str):
    """检查某日期是否存在日记条目。"""
    ensure_journal_dir()
    return any(f.startswith(date_str) and f.endswith(FILE_EXT)
               for f in os.listdir(JOURNAL_DIR))

def entry_count_today():
    """计算今天的条目数量。"""
    ensure_journal_dir()
    ds = today_str()
    return sum(1 for f in os.listdir(JOURNAL_DIR)
               if f.startswith(ds) and f.endswith(FILE_EXT))

def list_entries():
    """返回条目文件名列表，最新的在前。"""
    ensure_journal_dir()
    files = [f for f in os.listdir(JOURNAL_DIR)
             if f.endswith(FILE_EXT) and not f.startswith(".")]
    files.sort(reverse=True)
    return files

def read_entry(filename):
    """读取条目内容。返回文本或 None。"""
    try:
        with open(os.path.join(JOURNAL_DIR, filename), 'r', encoding='utf-8', errors='replace') as f:
            return f.read()
    except Exception:
        return None

def get_week_dates():
    """获取本周周一到周日的日期。"""
    today = datetime.now().date()
    monday = today - timedelta(days=today.weekday())
    return [monday + timedelta(days=i) for i in range(7)]

def get_streak():
    """计算以昨天或今天结尾的连续写日记天数。"""
    today = datetime.now().date()
    streak = 0
    day = today
    while True:
        if entry_exists(day.strftime("%Y-%m-%d")):
            streak += 1
            day -= timedelta(days=1)
        else:
            break
    return streak

def get_total_entries():
    """计算总日记条目数。"""
    ensure_journal_dir()
    return len([f for f in os.listdir(JOURNAL_DIR)
                if f.endswith(FILE_EXT) and not f.startswith(".")])

def save_entry(text):
    """将日记条目保存为纯文本。"""
    ensure_journal_dir()
    timestamp = datetime.now().strftime("%Y-%m-%d_%H%M%S")
    filepath = os.path.join(JOURNAL_DIR, f"{timestamp}{FILE_EXT}")
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(text)
    return filepath

def extract_body_from_entry(content):
    """从保存的日记内容中提取正文（去除元数据头）。"""
    if not content:
        return ""
    lines = content.split('\n')
    in_metadata = True
    body_lines = []
    for line in lines:
        stripped = line.strip()
        if in_metadata:
            if stripped.startswith(('日期:', '字数:', '提示词:')) or stripped == '自由写作' or stripped == '':
                continue
            else:
                in_metadata = False
                body_lines.append(line)
        else:
            body_lines.append(line)
    return '\n'.join(body_lines).strip()

# ── 配置管理 ──────────────────────────────────────────────────────────────

def load_config():
    """加载配置文件 ~/.pjournal。"""
    try:
        with open(CONFIG_FILE, 'r', encoding='utf-8') as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return {}

def save_config(config):
    """保存配置到 ~/.pjournal。"""
    with open(CONFIG_FILE, 'w', encoding='utf-8') as f:
        json.dump(config, f, ensure_ascii=False, indent=2)
    os.chmod(CONFIG_FILE, 0o600)

# ── Flomo API ──────────────────────────────────────────────────────────────

def generate_flomo_sign(params):
    """计算 Flomo 请求签名。"""
    parts = []
    for key in sorted(params.keys()):
        value = params[key]
        if value is None or value == "":
            continue
        if isinstance(value, list):
            for item in sorted(str(v) for v in value if v is not None):
                parts.append(f"{key}[]={item}")
        else:
            parts.append(f"{key}={value}")
    raw = "&".join(parts) + FLOMO_SIGN_SECRET
    return hashlib.md5(raw.encode()).hexdigest()

def flomo_login(email, password):
    """登录 Flomo，返回包含 access_token 的字典或 None。"""
    params = {
        "email": email,
        "password": password,
        "wechat_union_id": "",
        "wechat_oa_open_id": "",
        "timestamp": str(int(time.time())),
        "api_key": FLOMO_API_KEY,
        "app_version": FLOMO_APP_VERSION,
        "platform": FLOMO_PLATFORM,
        "webp": "1",
    }
    params["sign"] = generate_flomo_sign(params)

    url = f"{FLOMO_API_BASE}/user/login_by_email"
    data = json.dumps(params).encode('utf-8')
    req = urllib.request.Request(url, data=data, method='POST')
    req.add_header('Content-Type', 'application/json')
    req.add_header('User-Agent', 'pjournal/1.0')

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            result = json.loads(resp.read().decode('utf-8'))
            if result.get('code') == 0:
                return result.get('data', result)
            return None
    except Exception:
        return None

def flomo_create_memo(token, content):
    """创建 Flomo 笔记，返回是否成功。"""
    params = {
        "timestamp": str(int(time.time())),
        "api_key": FLOMO_API_KEY,
        "app_version": FLOMO_APP_VERSION,
        "platform": FLOMO_PLATFORM,
        "webp": "1",
        "content": content,
        "source": "web",
        "tz": FLOMO_TIMEZONE,
    }
    params["sign"] = generate_flomo_sign(params)

    url = f"{FLOMO_API_BASE}/memo"
    data = json.dumps(params).encode('utf-8')
    req = urllib.request.Request(url, data=data, method='PUT')
    req.add_header('Content-Type', 'application/json')
    req.add_header('Authorization', f'Bearer {token}')
    req.add_header('User-Agent', 'pjournal/1.0')

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            result = json.loads(resp.read().decode('utf-8'))
            return result.get('code') == 0
    except urllib.error.HTTPError as e:
        # Token 过期（code -10）时返回 False
        try:
            body = json.loads(e.read().decode('utf-8'))
            if body.get('code') in (-10, -20):
                return False
        except Exception:
            pass
        return False
    except Exception:
        return False

def send_to_flomo(text, config):
    """发送日记到 Flomo，返回 (成功与否, 消息)。"""
    email = config.get('flomo_email', '')
    password = config.get('flomo_password', '')

    if not email or not password:
        return False, "请先在设置中配置Flomo账号"

    # 格式化内容：添加 #日记 标签
    content = f"<p>{text}\n\n#日记</p>"

    # 尝试使用缓存的 token
    token = config.get('flomo_token', '')
    if token:
        if flomo_create_memo(token, content):
            return True, "已发送到Flomo ✓"

    # 重新登录获取 token
    result = flomo_login(email, password)
    if result and result.get('access_token'):
        token = result['access_token']
        config['flomo_token'] = token
        save_config(config)

        if flomo_create_memo(token, content):
            return True, "已发送到Flomo ✓"
        else:
            return False, "发送到Flomo失败"
    else:
        # 清除无效 token
        config.pop('flomo_token', None)
        save_config(config)
        return False, "Flomo登录失败，请检查账号密码"

# ── WebDAV 同步 ────────────────────────────────────────────────────────────

# WebDAV XML 命名空间
WEBDAV_NS = {'d': 'DAV:'}

def webdav_request(url, method, username, password, data=None, headers=None):
    """发送 WebDAV HTTP 请求。
    返回 (响应体bytes, HTTP状态码, 响应头dict) 或 (None, 0, {})。
    注意：响应体在 with 块内读取，避免响应关闭后无法读取的问题。
    """
    if headers is None:
        headers = {}
    # HTTP Basic Auth
    credentials = f"{username}:{password}"
    b64 = base64.b64encode(credentials.encode('utf-8')).decode('ascii')
    headers['Authorization'] = f'Basic {b64}'
    headers['User-Agent'] = 'pjournal/1.0'

    if data is not None:
        if isinstance(data, str):
            data = data.encode('utf-8')
        req = urllib.request.Request(url, data=data, method=method)
    else:
        req = urllib.request.Request(url, method=method)

    for k, v in headers.items():
        req.add_header(k, v)

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = resp.read()
            code = resp.getcode()
            resp_headers = dict(resp.headers)
            return body, code, resp_headers
    except urllib.error.HTTPError as e:
        # 某些错误响应也有 body（如 207 Multi-Status 可能通过错误返回）
        try:
            body = e.read()
        except Exception:
            body = None
        return body, e.code, dict(e.headers) if hasattr(e, 'headers') else {}
    except Exception:
        return None, 0, {}

def webdav_mkdir(url, username, password):
    """在 WebDAV 服务器上创建目录（MKCOL）。返回是否成功。"""
    if not url.endswith('/'):
        url += '/'

    _, code, _ = webdav_request(url, 'MKCOL', username, password)
    # 201 = Created, 405 = Already exists, 301/302 = redirect (exists)
    return code in (200, 201, 405, 301, 302)

def webdav_upload(url, username, password, content, filename):
    """上传文件到 WebDAV 服务器（PUT）。返回是否成功。"""
    if not url.endswith('/'):
        url += '/'
    full_url = url + urllib.parse.quote(filename)

    _, code, _ = webdav_request(full_url, 'PUT', username, password,
                             data=content,
                             headers={'Content-Type': 'text/plain; charset=utf-8'})
    return code in (200, 201, 204)

def webdav_download(url, username, password, filename):
    """从 WebDAV 服务器下载文件（GET）。返回内容文本或 None。"""
    if not url.endswith('/'):
        url += '/'
    full_url = url + urllib.parse.quote(filename)

    body, code, _ = webdav_request(full_url, 'GET', username, password)
    if code in (200, 203) and body is not None:
        try:
            return body.decode('utf-8', errors='replace')
        except Exception:
            return None
    return None

def webdav_propfind(url, username, password, depth=1):
    """列出 WebDAV 目录中的文件及其修改时间（PROPFIND）。
    返回字典 {filename: mtime_datetime}，失败返回 None。
    """
    if not url.endswith('/'):
        url += '/'

    # PROPFIND 请求体：请求 getlastmodified 属性
    propfind_body = '''<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:getlastmodified/>
    <d:resourcetype/>
  </d:prop>
</d:propfind>'''

    body, code, _ = webdav_request(url, 'PROPFIND', username, password,
                                data=propfind_body,
                                headers={
                                    'Content-Type': 'application/xml; charset=utf-8',
                                    'Depth': str(depth),
                                })
    if code not in (207, 200) or body is None:
        return None

    try:
        body_str = body.decode('utf-8', errors='replace')
    except Exception:
        return None

    # 解析多状态 XML 响应
    result = {}
    try:
        root = ET.fromstring(body_str)
        for resp_elem in root.findall('d:response', WEBDAV_NS):
            href_elem = resp_elem.find('d:href', WEBDAV_NS)
            if href_elem is None:
                continue
            href = href_elem.text or ''

            # 跳过目录本身
            propstat = resp_elem.find('d:propstat', WEBDAV_NS)
            if propstat is None:
                continue
            prop = propstat.find('d:prop', WEBDAV_NS)
            if prop is None:
                continue

            # 检查是否是集合（目录）
            resourcetype = prop.find('d:resourcetype', WEBDAV_NS)
            if resourcetype is not None and resourcetype.find('d:collection', WEBDAV_NS) is not None:
                continue

            # 获取文件名
            filename = urllib.parse.unquote(href.rstrip('/').split('/')[-1])
            if not filename or not filename.endswith(FILE_EXT):
                continue

            # 获取修改时间
            mtime_elem = prop.find('d:getlastmodified', WEBDAV_NS)
            if mtime_elem is None or mtime_elem.text is None:
                result[filename] = None
                continue

            mtime_str = mtime_elem.text.strip()
            # 尝试解析 RFC 2822 / ISO 8601 格式的时间
            mtime = None
            for fmt in (
                '%a, %d %b %Y %H:%M:%S GMT',     # RFC 2822
                '%a, %d %b %Y %H:%M:%S %Z',      # RFC 2822 with timezone
                '%Y-%m-%dT%H:%M:%SZ',              # ISO 8601 UTC
                '%Y-%m-%dT%H:%M:%S%z',             # ISO 8601 with tz
                '%Y-%m-%dT%H:%M:%S',               # ISO 8601 without tz
            ):
                try:
                    mtime = datetime.strptime(mtime_str, fmt)
                    # 如果没有时区信息，假设为 UTC
                    if mtime.tzinfo is None:
                        mtime = mtime.replace(tzinfo=timezone.utc)
                    break
                except ValueError:
                    continue

            result[filename] = mtime
    except ET.ParseError:
        return None

    return result

def parse_http_date(date_str):
    """解析 HTTP 日期头（Last-Modified 等）为 datetime。"""
    if not date_str:
        return None
    date_str = date_str.strip()
    for fmt in (
        '%a, %d %b %Y %H:%M:%S GMT',
        '%a, %d %b %Y %H:%M:%S %Z',
        '%Y-%m-%dT%H:%M:%SZ',
        '%Y-%m-%dT%H:%M:%S%z',
    ):
        try:
            dt = datetime.strptime(date_str, fmt)
            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=timezone.utc)
            return dt
        except ValueError:
            continue
    return None

def webdav_head(url, username, password, filename):
    """获取远程文件的修改时间（HEAD）。返回 datetime 或 None。"""
    if not url.endswith('/'):
        url += '/'
    full_url = url + urllib.parse.quote(filename)

    _, code, resp_headers = webdav_request(full_url, 'HEAD', username, password)
    if code in (200, 203) and resp_headers:
        last_modified = resp_headers.get('Last-Modified')
        return parse_http_date(last_modified)
    return None

def get_local_mtime(filename):
    """获取本地文件的修改时间（UTC datetime）。"""
    filepath = os.path.join(JOURNAL_DIR, filename)
    try:
        mtime = os.path.getmtime(filepath)
        return datetime.fromtimestamp(mtime, tz=timezone.utc)
    except OSError:
        return None

def webdav_delete(url, username, password, filename):
    """删除 WebDAV 服务器上的文件（DELETE）。返回是否成功。"""
    if not url.endswith('/'):
        url += '/'
    full_url = url + urllib.parse.quote(filename)
    _, code, _ = webdav_request(full_url, 'DELETE', username, password)
    return code in (200, 204, 404)  # 404 表示已不存在，视为成功

def load_sync_state():
    """加载上次同步状态。返回 {filename: mtime_iso_str} 字典。"""
    try:
        with open(SYNC_STATE_FILE, 'r', encoding='utf-8') as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return {}

def save_sync_state(state):
    """保存同步状态。state 为 {filename: mtime_iso_str} 字典。"""
    with open(SYNC_STATE_FILE, 'w', encoding='utf-8') as f:
        json.dump(state, f, ensure_ascii=False, indent=2)
    os.chmod(SYNC_STATE_FILE, 0o600)

def sync_to_webdav(config):
    """双向同步日记到 WebDAV 服务器，保持本地和远程一致性。
    同步规则：
    - 新增文件：仅本地存在 → 上传；仅远程存在 → 下载
    - 修改文件：本地较新 → 上传；远程较新 → 下载；相同 → 跳过
    - 删除文件：参照上次同步状态，判定删除方向并传播
      - 上次同步存在、现在本地不存在 → 本地删除 → 删除远程
      - 上次同步存在、现在远程不存在 → 远程删除 → 删除本地
    - 无法判断（首次同步且仅一方存在）→ 保守策略：上传+下载，不删除
    返回 (成功与否, 消息)。
    """
    url = config.get('webdav_url', '').rstrip()
    username = config.get('webdav_username', '').rstrip()
    password = config.get('webdav_password', '').rstrip()

    if not url or not username or not password:
        return False, "请先在设置中配置 WebDAV"

    ensure_journal_dir()

    # 确保远程目录存在
    remote_dir = url.rstrip('/') + '/journal/'
    if not webdav_mkdir(remote_dir, username, password):
        remote_dir = url.rstrip('/') + '/'
        if not webdav_mkdir(remote_dir, username, password):
            return False, "无法创建 WebDAV 远程目录"

    # 获取本地文件列表及修改时间
    local_files = {}
    for fname in list_entries():
        local_files[fname] = get_local_mtime(fname)

    # 获取远程文件列表及修改时间
    remote_files = webdav_propfind(remote_dir, username, password)
    if remote_files is None:
        remote_files = {}

    # 加载上次同步状态
    prev_state = load_sync_state()

    uploaded = 0
    downloaded = 0
    skipped = 0
    deleted_local = 0   # 从本地删除（远程已删）
    deleted_remote = 0  # 从远程删除（本地已删）
    failed = 0

    # 新同步状态
    new_state = {}

    # 所有文件名的并集
    all_filenames = set(local_files.keys()) | set(remote_files.keys())

    for fname in sorted(all_filenames):
        local_mtime = local_files.get(fname)
        remote_mtime = remote_files.get(fname)
        in_prev = fname in prev_state

        # 如果 PROPFIND 未返回该文件的修改时间，尝试 HEAD 请求
        if fname in remote_files and remote_mtime is None:
            remote_mtime = webdav_head(remote_dir, username, password, fname)

        local_exists = fname in local_files
        remote_exists = fname in remote_files

        if not local_exists and not remote_exists:
            # 两端都不存在（不应该出现），跳过
            continue

        elif not local_exists and remote_exists:
            # 本地不存在，远程存在
            if in_prev:
                # 上次同步时存在 → 本地删除了 → 从远程删除
                if webdav_delete(remote_dir, username, password, fname):
                    deleted_remote += 1
                else:
                    failed += 1
                # 不记录到 new_state（已删除）
            else:
                # 首次同步且仅远程存在 → 远程新增 → 下载
                content = webdav_download(remote_dir, username, password, fname)
                if content is not None:
                    try:
                        filepath = os.path.join(JOURNAL_DIR, fname)
                        with open(filepath, 'w', encoding='utf-8') as f:
                            f.write(content)
                        downloaded += 1
                        # 记录到新状态
                        if remote_mtime:
                            new_state[fname] = remote_mtime.isoformat()
                        else:
                            new_state[fname] = datetime.now(timezone.utc).isoformat()
                    except Exception:
                        failed += 1
                else:
                    failed += 1

        elif local_exists and not remote_exists:
            # 本地存在，远程不存在
            if in_prev:
                # 上次同步时存在 → 远程删除了 → 从本地删除
                try:
                    os.remove(os.path.join(JOURNAL_DIR, fname))
                    deleted_local += 1
                except Exception:
                    failed += 1
                # 不记录到 new_state（已删除）
            else:
                # 首次同步且仅本地存在 → 本地新增 → 上传
                filepath = os.path.join(JOURNAL_DIR, fname)
                try:
                    with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
                        content = f.read()
                    if webdav_upload(remote_dir, username, password, content, fname):
                        uploaded += 1
                        # 记录到新状态
                        if local_mtime:
                            new_state[fname] = local_mtime.isoformat()
                        else:
                            new_state[fname] = datetime.now(timezone.utc).isoformat()
                    else:
                        failed += 1
                except Exception:
                    failed += 1

        else:
            # 两端都存在，比较修改时间
            if local_mtime is None or remote_mtime is None:
                # 无法获取时间，记录当前状态，跳过
                skipped += 1
                new_state[fname] = prev_state.get(fname, datetime.now(timezone.utc).isoformat())
                continue

            # 统一为 UTC 比较，允许 1 秒误差
            local_utc = local_mtime.astimezone(timezone.utc) if local_mtime.tzinfo else local_mtime.replace(tzinfo=timezone.utc)
            remote_utc = remote_mtime.astimezone(timezone.utc) if remote_mtime.tzinfo else remote_mtime.replace(tzinfo=timezone.utc)

            diff = (local_utc - remote_utc).total_seconds()

            if abs(diff) <= 1:
                # 时间基本相同 → 跳过
                skipped += 1
                new_state[fname] = local_utc.isoformat()
            elif diff > 1:
                # 本地较新 → 上传覆盖远程
                filepath = os.path.join(JOURNAL_DIR, fname)
                try:
                    with open(filepath, 'r', encoding='utf-8', errors='replace') as f:
                        content = f.read()
                    if webdav_upload(remote_dir, username, password, content, fname):
                        uploaded += 1
                        new_state[fname] = local_utc.isoformat()
                    else:
                        failed += 1
                        new_state[fname] = prev_state.get(fname, local_utc.isoformat())
                except Exception:
                    failed += 1
                    new_state[fname] = prev_state.get(fname, local_utc.isoformat())
            else:
                # 远程较新 → 下载覆盖本地
                content = webdav_download(remote_dir, username, password, fname)
                if content is not None:
                    try:
                        filepath = os.path.join(JOURNAL_DIR, fname)
                        with open(filepath, 'w', encoding='utf-8') as f:
                            f.write(content)
                        downloaded += 1
                        new_state[fname] = remote_utc.isoformat()
                    except Exception:
                        failed += 1
                        new_state[fname] = prev_state.get(fname, remote_utc.isoformat())
                else:
                    failed += 1
                    new_state[fname] = prev_state.get(fname, remote_utc.isoformat())

    # 保存同步状态
    save_sync_state(new_state)

    # 构建结果消息
    parts = []
    if uploaded > 0:
        parts.append(f"上传 {uploaded} 篇")
    if downloaded > 0:
        parts.append(f"下载 {downloaded} 篇")
    if deleted_local > 0:
        parts.append(f"本地删除 {deleted_local} 篇")
    if deleted_remote > 0:
        parts.append(f"远程删除 {deleted_remote} 篇")
    if skipped > 0:
        parts.append(f"跳过 {skipped} 篇")
    if failed > 0:
        parts.append(f"失败 {failed} 篇")

    if not parts:
        return True, "无需同步，本地和远程一致 ✓"

    if failed == 0:
        return True, "同步完成: " + " · ".join(parts) + " ✓"
    elif uploaded + downloaded == 0 and deleted_local + deleted_remote == 0:
        return False, "同步失败: " + " · ".join(parts)
    else:
        return True, "部分同步: " + " · ".join(parts)

# ── Deepseek API ───────────────────────────────────────────────────────────

def generate_ai_prompt(api_key, experience, hobbies):
    """调用 Deepseek API 生成日记提示词。返回提示词字符串或 None。"""
    system_prompt = (
        "你是一个日记写作助手。根据用户的个人信息，随机生成一个日记提示词，"
        "以问题的形式呈现。提示词应该与个人的经历和爱好相关，帮助用户深入思考。"
        "只生成一个问题，不要其他内容，不要加引号。"
    )

    user_parts = []
    if experience:
        user_parts.append(f"个人经历：{experience}")
    if hobbies:
        user_parts.append(f"个人爱好：{hobbies}")
    user_parts.append("请生成一个日记提示词：")
    user_message = "\n".join(user_parts)

    payload = {
        "model": "deepseek-chat",
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_message}
        ],
        "max_tokens": 100,
        "temperature": 0.9
    }

    data = json.dumps(payload).encode('utf-8')
    req = urllib.request.Request(DEEPSEEK_API_URL, data=data, method='POST')
    req.add_header('Content-Type', 'application/json')
    req.add_header('Authorization', f'Bearer {api_key}')

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            result = json.loads(resp.read().decode('utf-8'))
            content = result['choices'][0]['message']['content'].strip()
            # 去除可能的引号包裹
            content = content.strip('"\'""''')
            return content
    except Exception:
        return None

# ── UI 组件 ───────────────────────────────────────────────────────────

def draw_status(stdscr, left="", right="", style=None):
    h, w = stdscr.getmaxyx()
    if style is None:
        style = curses.A_REVERSE
    bar = left + " " * max(0, w - string_width(left) - string_width(right)) + right
    bar = bar[:w]
    try:
        stdscr.addstr(h - 1, 0, bar, style)
    except curses.error:
        pass

def draw_help_bar(stdscr, text):
    h, w = stdscr.getmaxyx()
    text = text[:w]
    try:
        stdscr.addstr(h - 2, 0, text + " " * max(0, w - string_width(text)), curses.A_DIM)
    except curses.error:
        pass

def read_utf8_char(stdscr):
    """从终端读取一个 UTF-8 字符（支持中文等多字节字符）。"""
    ch = stdscr.getch()
    if ch < 0:
        return ch, None  # 无输入或错误
    if ch < 128:
        return ch, chr(ch) if 32 <= ch < 127 else None
    if ch > 255:
        # 特殊键码（方向键、功能键等），直接返回键码
        return ch, None
    # UTF-8 多字节序列 (128-255)
    utf8_bytes = bytearray([ch])
    if ch & 0xE0 == 0xC0:  # 2 字节序列
        utf8_bytes.append(stdscr.getch())
    elif ch & 0xF0 == 0xE0:  # 3 字节序列（大多数 CJK）
        utf8_bytes.append(stdscr.getch())
        utf8_bytes.append(stdscr.getch())
    elif ch & 0xF8 == 0xF0:  # 4 字节序列
        utf8_bytes.append(stdscr.getch())
        utf8_bytes.append(stdscr.getch())
        utf8_bytes.append(stdscr.getch())
    try:
        char = utf8_bytes.decode('utf-8')
        return ch, char
    except UnicodeDecodeError:
        return ch, None

def prompt_input(stdscr, label, default="", masked=False):
    """提示文本输入。返回字符串，按 Esc 返回 None。"""
    curses.curs_set(1)
    h, w = stdscr.getmaxyx()
    buf = default
    while True:
        if masked and buf:
            display_buf = "*" * len(buf)
        else:
            display_buf = buf
        display = f" {label}{display_buf}"
        try:
            stdscr.addstr(h - 1, 0, display + " " * max(0, w - string_width(display)),
                          curses.A_REVERSE)
            stdscr.move(h - 1, min(string_width(display), w - 1))
        except curses.error:
            pass
        stdscr.refresh()

        ch, char = read_utf8_char(stdscr)
        if ch == 27:
            curses.curs_set(0)
            return None
        elif ch in (curses.KEY_ENTER, 10, 13):
            curses.curs_set(0)
            return buf.strip()
        elif ch in (curses.KEY_BACKSPACE, 127, 8):
            buf = buf[:-1]
        elif ch == 21:  # Ctrl+U — 清空输入
            buf = ""
        elif char is not None:
            buf += char

def confirm(stdscr, message):
    """是/否确认。"""
    h, w = stdscr.getmaxyx()
    draw_status(stdscr, left=f" {message} (y/n)")
    stdscr.refresh()
    while True:
        ch = stdscr.getch()
        if ch in (ord('y'), ord('Y')):
            return True
        if ch in (ord('n'), ord('N'), 27):
            return False

def show_message(stdscr, message, duration=2):
    """在屏幕中央显示临时消息。"""
    h, w = stdscr.getmaxyx()
    msg_w = string_width(message) + 6
    msg_h = 3
    y = max(0, (h - msg_h) // 2)
    x = max(0, (w - msg_w) // 2)

    # 绘制背景
    for i in range(msg_h):
        line = " " * min(msg_w, w - max(0, x))
        try:
            stdscr.addstr(y + i, max(0, x), line, curses.A_REVERSE)
        except curses.error:
            pass

    # 绘制消息
    try:
        stdscr.addstr(y + 1, max(0, x + 3), f" {message} "[:w - max(0, x) - 4],
                      curses.A_REVERSE | curses.A_BOLD)
    except curses.error:
        pass

    stdscr.refresh()
    time.sleep(duration)

# ── 自动换行 ───────────────────────────────────────────────────────────────

def wrap_line(line, width):
    """支持 CJK 字符宽度的自动换行。"""
    if width <= 0:
        return [(0, len(line))]
    if len(line) == 0:
        return [(0, 0)]
    segments = []
    pos = 0
    length = len(line)
    current_width = 0
    seg_start = 0

    while pos < length:
        ch = line[pos]
        cw = char_width(ch)

        if current_width + cw > width:
            if ch == ' ':
                segments.append((seg_start, pos))
                pos += 1
                seg_start = pos
                current_width = 0
            else:
                has_space = False
                break_pos = pos
                for i in range(seg_start, pos):
                    if line[i] == ' ':
                        has_space = True
                        break_pos = i

                if has_space and break_pos > seg_start:
                    segments.append((seg_start, break_pos))
                    pos = break_pos + 1
                    seg_start = pos
                    current_width = string_width(line[seg_start:pos + 1]) if pos < length else 0
                else:
                    segments.append((seg_start, pos))
                    seg_start = pos
                    current_width = cw
            pos += 1 if seg_start == pos else 0
            if seg_start < pos:
                continue
        else:
            current_width += cw
            pos += 1

    if seg_start < length:
        segments.append((seg_start, length))
    elif seg_start == length and not segments:
        segments.append((0, 0))

    return segments if segments else [(0, 0)]

def build_wrap_map(lines, width):
    vrows = []
    for li, line in enumerate(lines):
        segs = wrap_line(line, width)
        for start, end in segs:
            vrows.append((li, start, end))
    return vrows

def logical_to_visual(vrows, lines, cy, cx):
    """将逻辑坐标转换为视觉坐标（支持 CJK 宽度）。"""
    for vi, (li, scol, ecol) in enumerate(vrows):
        if li == cy and scol <= cx <= ecol:
            if cx == ecol and ecol > scol:
                if vi + 1 < len(vrows) and vrows[vi + 1][0] == li:
                    continue
            return vi, string_width(lines[li][scol:cx]) if cx > scol else 0
    if vrows:
        vi = len(vrows) - 1
        li, scol, ecol = vrows[vi]
        return vi, min(string_width(lines[li][scol:cx]) if li < len(lines) else 0, ecol - scol)
    return 0, 0

def visual_to_logical(vrows, lines, vi, screen_cx):
    """将视觉坐标转换为逻辑坐标（支持 CJK 宽度）。"""
    if not vrows or not lines:
        return 0, 0
    vi = max(0, min(vi, len(vrows) - 1))
    li, scol, ecol = vrows[vi]
    if li >= len(lines):
        return 0, 0
    segment = lines[li][scol:ecol] if ecol > scol else ""
    visual_pos = 0
    logical_pos = scol
    for i in range(len(segment)):
        if visual_pos >= screen_cx:
            break
        visual_pos += char_width(segment[i])
        logical_pos = scol + i + 1
    return li, min(logical_pos, ecol)

def char_count(lines):
    """统计字符数（中文按字符计）。"""
    return sum(len(line) for line in lines if line.strip())

def word_count(lines):
    """统计字数（中文按字符计，英文按单词计）。"""
    total = 0
    for line in lines:
        if not line.strip():
            continue
        chinese_chars = len(re.findall(r'[\u4e00-\u9fff\u3000-\u303f\uff00-\uffef]', line))
        english_words = len(re.findall(r'[a-zA-Z]+', line))
        total += chinese_chars + english_words
    return total

# ── 设置界面 ──────────────────────────────────────────────────────────

def settings_screen(stdscr, accent):
    """设置界面。"""
    config = load_config()

    # 分组设置项
    groups = [
        ("── AI ──", [
            ("deepseek_api_key",    "Deepseek API Key", False),
        ]),
        ("── Flomo ──", [
            ("flomo_email",         "邮箱",       False),
            ("flomo_password",      "密码",       True),
        ]),
        ("── WebDAV ──", [
            ("webdav_url",          "服务器地址", False),
            ("webdav_username",     "用户名",    False),
            ("webdav_password",     "密码",      True),
        ]),
        ("── 个人 ──", [
            ("personal_experience", "经历",      False),
            ("personal_hobbies",    "爱好",      False),
        ]),
    ]

    # 构建扁平的显示行列表
    display_rows = []  # (type, data)  type: 'group'|'field'
    for group_title, group_fields in groups:
        display_rows.append(('group', group_title))
        for field in group_fields:
            display_rows.append(('field', field))

    # 可选中的行索引（只有 field 行可选中）
    selectable = [i for i, (t, _) in enumerate(display_rows) if t == 'field']
    sel_idx = 0  # selectable 列表中的索引
    scroll_off = 0

    while True:
        stdscr.erase()
        h, w = stdscr.getmaxyx()
        usable = h - 3  # 标题行 + 帮助栏 + 状态栏

        # 标题
        row = 0
        title = "── 设置 ──"
        tx = max(0, (w - string_width(title)) // 2)
        try:
            stdscr.addstr(row, tx, title, accent | curses.A_BOLD)
        except curses.error:
            pass

        # 计算选中行的显示行号
        if selectable:
            sel_idx = max(0, min(sel_idx, len(selectable) - 1))
            sel_display_row = selectable[sel_idx]
        else:
            sel_display_row = 0

        # 滚动
        if sel_display_row < scroll_off:
            scroll_off = sel_display_row
        if sel_display_row >= scroll_off + usable:
            scroll_off = sel_display_row - usable + 1
        scroll_off = max(0, scroll_off)

        # 绘制设置项
        draw_row = 1
        for vi in range(scroll_off, min(len(display_rows), scroll_off + usable)):
            dtype, data = display_rows[vi]
            if dtype == 'group':
                # 分组标题
                gt = f" {data}"
                try:
                    stdscr.addstr(draw_row, 0, gt, accent | curses.A_BOLD)
                except curses.error:
                    pass
            else:
                # 设置字段：标签和值在同一行
                key, label, masked = data
                value = config.get(key, '')
                if masked and value:
                    display_value = "*" * min(len(value), 20)
                elif value:
                    display_value = value[:40] + ("..." if len(value) > 40 else "")
                else:
                    display_value = "(未设置)"

                is_sel = (vi == sel_display_row)
                style = curses.A_REVERSE if is_sel else curses.A_NORMAL
                val_style = style if is_sel else (curses.A_DIM if not value else curses.A_NORMAL)

                line = f"  {label}: "
                lw = string_width(line)
                remaining = w - lw - 2
                if remaining < 10:
                    remaining = w - lw
                try:
                    stdscr.addstr(draw_row, 0, line, style | curses.A_BOLD)
                    stdscr.addstr(draw_row, lw, display_value[:remaining], val_style)
                    # 填充行背景
                    total_w = lw + string_width(display_value[:remaining])
                    if is_sel and total_w < w:
                        stdscr.addstr(draw_row, total_w, " " * (w - total_w), style)
                except curses.error:
                    pass
            draw_row += 1

        # 滚动条
        if len(display_rows) > usable:
            bar_h = max(1, usable * usable // len(display_rows))
            bar_top = scroll_off * usable // len(display_rows)
            for bi in range(bar_h):
                by = 1 + bar_top + bi
                if by < h - 2:
                    try:
                        stdscr.addch(by, w - 1, '│', curses.A_DIM)
                    except curses.error:
                        pass

        # 帮助栏
        help_text = " [回车] 编辑  [d] 清空  [q] 返回"
        try:
            stdscr.addstr(h - 2, 0, (help_text + " " * w)[:w], curses.A_DIM)
        except curses.error:
            pass
        draw_status(stdscr, left=" ~/设置")

        stdscr.refresh()
        ch = stdscr.getch()

        if ch == ord('q') or ch == 27:
            return
        elif ch == curses.KEY_UP or ch == ord('k'):
            sel_idx = max(0, sel_idx - 1)
        elif ch == curses.KEY_DOWN or ch == ord('j'):
            sel_idx = min(len(selectable) - 1, sel_idx + 1)
        elif ch == curses.KEY_HOME:
            sel_idx = 0
        elif ch == curses.KEY_END:
            sel_idx = len(selectable) - 1 if selectable else 0
        elif ch == ord('d') or ch == ord('D'):
            if selectable:
                key, label, masked = display_rows[selectable[sel_idx]][1]
                config[key] = ''
                save_config(config)
        elif ch in (curses.KEY_ENTER, 10, 13):
            if selectable:
                key, label, masked = display_rows[selectable[sel_idx]][1]
                current = config.get(key, '')
                new_value = prompt_input(stdscr, f"{label}: ", default=current, masked=masked)
                if new_value is not None:
                    config[key] = new_value
                    save_config(config)
                    # 如果修改了 Flomo 账号，清除缓存的 token
                    if key in ('flomo_email', 'flomo_password'):
                        config.pop('flomo_token', None)
                        save_config(config)

# ── 主屏幕 ─────────────────────────────────────────────────────────────

def draw_main_screen(stdscr, accent):
    """绘制日记主屏幕，含周追踪器。"""
    curses.curs_set(0)

    while True:
        stdscr.erase()
        h, w = stdscr.getmaxyx()

        week_dates = get_week_dates()
        today = datetime.now().date()
        today_count = entry_count_today()
        streak = get_streak()
        total = get_total_entries()

        # 标题
        row = 1
        title = "个人日记"
        tx = max(0, (w - string_width(title)) // 2)
        try:
            stdscr.addstr(row, tx, title, curses.A_BOLD)
        except curses.error:
            pass

        # 周追踪器
        row = 4
        day_names = ["一", "二", "三", "四", "五", "六", "日"]

        header_parts = []
        mark_parts = []
        for i, d in enumerate(week_dates):
            dstr = d.strftime("%Y-%m-%d")
            is_today = (d == today)
            has_entry = entry_exists(dstr)

            name = day_names[i]
            if is_today:
                day_cell = f"[{name}]"
            else:
                day_cell = f" {name} "
            header_parts.append(day_cell)

            if has_entry:
                mark_parts.append(" ✓  ")
            elif d <= today:
                mark_parts.append(" ·  ")
            else:
                mark_parts.append("    ")

        header = "  " + "  ".join(header_parts)
        marks = "  " + "  ".join(mark_parts)

        hx = max(0, (w - string_width(header)) // 2)
        mx = max(0, (w - string_width(marks)) // 2)
        try:
            stdscr.addstr(row, hx, header, accent)
            stdscr.addstr(row + 1, mx, marks, curses.A_BOLD)
        except curses.error:
            pass

        # 统计
        row = 8
        stats = f"连续: {streak} 天  ·  总计: {total} 篇"
        sx = max(0, (w - string_width(stats)) // 2)
        try:
            stdscr.addstr(row, sx, stats, curses.A_DIM)
        except curses.error:
            pass

        # 今日状态
        row = 10
        if today_count > 0:
            if today_count == 1:
                status = "✓ 今日已写 1 篇"
            else:
                status = f"✓ 今日已写 {today_count} 篇"
            style = accent | curses.A_BOLD
        else:
            status = "今日尚未写日记"
            style = curses.A_DIM
        stx = max(0, (w - string_width(status)) // 2)
        try:
            stdscr.addstr(row, stx, status, style)
        except curses.error:
            pass

        # 菜单
        row = 13
        opt1 = "[p] 提示写作"
        opt2 = "[f] 自由写作"
        opt3 = "[v] 查看过往日记"
        opt4 = "[w] 同步到WebDAV"
        opt5 = "[s] 设置"
        o1x = max(0, (w - string_width(opt1)) // 2)
        o2x = max(0, (w - string_width(opt2)) // 2)
        o3x = max(0, (w - string_width(opt3)) // 2)
        o4x = max(0, (w - string_width(opt4)) // 2)
        o5x = max(0, (w - string_width(opt5)) // 2)
        try:
            stdscr.addstr(row, o1x, opt1)
            stdscr.addstr(row + 1, o2x, opt2)
            if total > 0:
                stdscr.addstr(row + 2, o3x, opt3)
            stdscr.addstr(row + 3, o4x, opt4)
            stdscr.addstr(row + 4, o5x, opt5)
        except curses.error:
            pass

        quit_opt = "[q] 退出"
        qx = max(0, (w - string_width(quit_opt)) // 2)
        try:
            stdscr.addstr(row + 6, qx, quit_opt, curses.A_DIM)
        except curses.error:
            pass

        stdscr.refresh()
        ch = stdscr.getch()

        if ch == ord('q'):
            return None
        elif ch == ord('p'):
            return "prompt"
        elif ch == ord('f'):
            return "freewrite"
        elif ch == ord('v') and total > 0:
            return "view"
        elif ch == ord('w'):
            return "webdav"
        elif ch == ord('s'):
            return "settings"

# ── 条目浏览与查看 ──────────────────────────────────────────────────────────

def entry_browser(stdscr, accent):
    """浏览过往条目。返回要查看的文件名，或 None。"""
    curses.curs_set(0)
    sel = 0
    scroll_off = 0

    while True:
        stdscr.erase()
        h, w = stdscr.getmaxyx()
        usable = h - 3

        entries = list_entries()

        header = " 过往日记"
        try:
            stdscr.addstr(0, 0, (header + " " * w)[:w], curses.A_BOLD)
        except curses.error:
            pass

        if not entries:
            msg = "暂无日记。"
            try:
                stdscr.addstr(h // 2, max(0, (w - string_width(msg)) // 2), msg, curses.A_DIM)
            except curses.error:
                pass
        else:
            sel = max(0, min(sel, len(entries) - 1))
            if sel < scroll_off:
                scroll_off = sel
            if sel >= scroll_off + usable:
                scroll_off = sel - usable + 1

            for i in range(usable):
                idx = scroll_off + i
                if idx >= len(entries):
                    break
                fname = entries[idx]
                date_part = fname.replace(FILE_EXT, '')
                try:
                    dt = datetime.strptime(date_part, "%Y-%m-%d_%H%M%S")
                    display_date = dt.strftime("%Y年%m月%d日 %H:%M")
                except ValueError:
                    display_date = date_part

                content = read_entry(fname)
                preview = ""
                if content:
                    for line in content.split('\n'):
                        stripped = line.strip()
                        if stripped and not stripped.startswith(('日期:', '字数:', '提示词:', '自由写作')):
                            preview = stripped[:w - string_width(display_date) - 10]
                            break
                    if not preview:
                        lines = [l.strip() for l in content.split('\n') if l.strip()]
                        if lines:
                            preview = lines[0][:w - string_width(display_date) - 10]

                row = i + 1
                if idx == sel:
                    style = curses.A_REVERSE
                    prefix = " › "
                else:
                    style = curses.A_NORMAL
                    prefix = "   "

                line = prefix + display_date
                if preview:
                    remaining = w - string_width(line) - 3
                    if remaining > 10:
                        line += "  " + preview[:remaining]
                line = line[:w]
                try:
                    stdscr.addstr(row, 0, line + " " * max(0, w - string_width(line)), style)
                except curses.error:
                    pass

        help_text = " [回车] 阅读  [d] 删除  [^S] 发送Flomo  [q] 返回"
        try:
            stdscr.addstr(h - 2, 0, (help_text + " " * w)[:w], curses.A_DIM)
        except curses.error:
            pass

        count = f"{len(entries)} 篇"
        draw_status(stdscr, left=" ~/日记", right=f"{count} ")

        stdscr.refresh()
        ch = stdscr.getch()

        if ch == ord('q') or ch == 27:
            return None
        elif ch == curses.KEY_UP or ch == ord('k'):
            sel = max(0, sel - 1)
        elif ch == curses.KEY_DOWN or ch == ord('j'):
            sel = min(max(0, len(entries) - 1), sel + 1)
        elif ch == curses.KEY_HOME:
            sel = 0
        elif ch == curses.KEY_END:
            sel = max(0, len(entries) - 1)
        elif ch in (curses.KEY_ENTER, 10, 13):
            if entries:
                return entries[sel]
        elif ch == ord('d') or ch == ord('D'):  # 删除选中日记
            if entries:
                fname = entries[sel]
                date_part = fname.replace(FILE_EXT, '')
                try:
                    dt = datetime.strptime(date_part, "%Y-%m-%d_%H%M%S")
                    display_date = dt.strftime("%Y年%m月%d日 %H:%M")
                except ValueError:
                    display_date = date_part
                if confirm(stdscr, f"删除 {display_date} 的日记？"):
                    try:
                        os.remove(os.path.join(JOURNAL_DIR, fname))
                        show_message(stdscr, "已删除", duration=1)
                        # 重新加载条目列表并调整选中项
                        entries = list_entries()
                        if not entries:
                            return None
                        sel = min(sel, len(entries) - 1)
                    except Exception:
                        show_message(stdscr, "删除失败", duration=1.5)
        elif ch == 19:  # Ctrl+S — 发送选中日记到 Flomo
            if entries:
                fname = entries[sel]
                content = read_entry(fname)
                if content:
                    body = extract_body_from_entry(content)
                    if body:
                        config = load_config()
                        success, msg = send_to_flomo(body, config)
                        show_message(stdscr, msg, duration=2)
                    else:
                        show_message(stdscr, "日记内容为空", duration=1.5)
                else:
                    show_message(stdscr, "无法读取日记", duration=1.5)


def entry_viewer(stdscr, accent, filename):
    """只读分页器，用于查看日记条目。"""
    curses.curs_set(0)

    content = read_entry(filename)
    if not content:
        return

    h, w = stdscr.getmaxyx()

    # 解析日期用于标题
    date_part = filename.replace(FILE_EXT, '')
    try:
        dt = datetime.strptime(date_part, "%Y-%m-%d_%H%M%S")
        display_date = dt.strftime("%Y年%m月%d日 %H:%M")
    except ValueError:
        display_date = date_part

    # 构建显示行
    lines = []
    lines.append({"text": "", "style": curses.A_NORMAL})
    lines.append({"text": f"  {display_date}", "style": accent | curses.A_BOLD})
    lines.append({"text": "", "style": curses.A_NORMAL})

    for raw_line in content.split('\n'):
        stripped = raw_line.strip()
        # 对元数据使用不同样式
        if stripped.startswith(('日期:', '字数:')):
            continue  # 跳过元数据，在标题中显示日期
        elif stripped.startswith('提示词:'):
            prompt_text = stripped[4:].strip()
            wrapped = textwrap.fill(prompt_text, width=w - 6)
            for wl in wrapped.split('\n'):
                lines.append({"text": f"  {wl}", "style": accent | curses.A_DIM})
            lines.append({"text": "", "style": curses.A_NORMAL})
        elif stripped == '自由写作':
            lines.append({"text": "  自由写作", "style": curses.A_DIM})
            lines.append({"text": "", "style": curses.A_NORMAL})
        elif stripped == '':
            lines.append({"text": "", "style": curses.A_NORMAL})
        else:
            wrapped = textwrap.fill(raw_line, width=w - 4)
            for wl in wrapped.split('\n'):
                lines.append({"text": f"  {wl}", "style": curses.A_NORMAL})

    lines.append({"text": "", "style": curses.A_NORMAL})

    scroll = 0

    while True:
        stdscr.erase()
        h, w = stdscr.getmaxyx()
        text_h = h - 2
        max_scroll = max(0, len(lines) - text_h)
        scroll = max(0, min(scroll, max_scroll))

        for i in range(text_h):
            line_idx = scroll + i
            if line_idx >= len(lines):
                break
            ld = lines[line_idx]
            try:
                stdscr.addstr(i, 0, ld["text"][:w - 1], ld.get("style", curses.A_NORMAL))
            except curses.error:
                pass

        # 状态栏
        if len(lines) > text_h:
            pct = int((scroll / max(1, max_scroll)) * 100)
            pos = f" {pct}%"
        else:
            pos = " 100%"
        draw_status(stdscr, left=f" {display_date}  (只读)", right=f"{pos} ")

        help_text = " ↑↓ 滚动  g/G 顶/底  ^S 发送Flomo  q:返回"
        try:
            stdscr.addstr(h - 2, 0, (help_text + " " * w)[:w], curses.A_DIM)
        except curses.error:
            pass

        stdscr.refresh()
        ch = stdscr.getch()

        if ch == ord('q') or ch == 27:
            return
        elif ch == curses.KEY_UP or ch == ord('k'):
            scroll = max(0, scroll - 1)
        elif ch == curses.KEY_DOWN or ch == ord('j'):
            scroll = min(max_scroll, scroll + 1)
        elif ch == curses.KEY_PPAGE or ch == ord(' '):
            scroll = max(0, scroll - text_h)
        elif ch == curses.KEY_NPAGE:
            scroll = min(max_scroll, scroll + text_h)
        elif ch == ord('g'):
            scroll = 0
        elif ch == ord('G'):
            scroll = max_scroll
        elif ch == 19:  # Ctrl+S — 发送到 Flomo
            body = extract_body_from_entry(content)
            if body:
                config = load_config()
                success, msg = send_to_flomo(body, config)
                show_message(stdscr, msg, duration=2)
            else:
                show_message(stdscr, "日记内容为空", duration=1.5)


# ── 编辑器 ──────────────────────────────────────────────────────────────────

def journal_editor(stdscr, accent, prompt_text=None):
    """
    一次性日记编辑器。
    返回条目文本，如果取消且未写内容则返回 None。
    按 Ctrl+S 保存并发送到 Flomo 后返回文本。
    """
    lines = ['']
    cx, cy = 0, 0
    scroll_y = 0
    target_screen_cx = None

    # 计算提示词显示高度用于偏移
    prompt_lines = []
    prompt_h = 0
    if prompt_text:
        h, w = stdscr.getmaxyx()
        wrapped = textwrap.fill(prompt_text, width=w - 6)
        prompt_lines = wrapped.split('\n')
        prompt_h = len(prompt_lines) + 3  # 空行 + 提示词行 + 空行 + 分隔线

    curses.curs_set(1)

    while True:
        stdscr.erase()
        h, w = stdscr.getmaxyx()
        text_h = h - 2 - prompt_h  # 状态栏 + 帮助栏 + 提示词区域

        # 限制光标
        cy = max(0, min(cy, len(lines) - 1))
        cx = max(0, min(cx, len(lines[cy])))

        # 构建换行映射
        vrows = build_wrap_map(lines, w)

        # 计算光标的视觉位置
        vi_cursor = 0
        scx_cursor = 0
        for vi, (li, scol, ecol) in enumerate(vrows):
            if li == cy and scol <= cx <= ecol:
                if cx == ecol and ecol > scol and vi + 1 < len(vrows) and vrows[vi + 1][0] == li:
                    continue
                vi_cursor = vi
                scx_cursor = string_width(lines[li][scol:cx])
                break

        # 滚动
        if vi_cursor < scroll_y:
            scroll_y = vi_cursor
        if vi_cursor >= scroll_y + text_h:
            scroll_y = vi_cursor - text_h + 1
        scroll_y = max(0, min(scroll_y, max(0, len(vrows) - text_h)))

        # 在顶部绘制提示词区域
        draw_row = 0
        if prompt_text:
            draw_row += 1  # 空行
            for pl in prompt_lines:
                try:
                    stdscr.addstr(draw_row, 3, pl, accent | curses.A_DIM)
                except curses.error:
                    pass
                draw_row += 1
            draw_row += 1  # 空行
            # 细分隔线
            sep = "─" * (w - 2)
            try:
                stdscr.addstr(draw_row, 1, sep, curses.A_DIM)
            except curses.error:
                pass
            draw_row += 1

        # 绘制文本
        for i in range(text_h):
            vi = scroll_y + i
            if vi >= len(vrows):
                break
            li, scol, ecol = vrows[vi]
            segment = lines[li][scol:ecol]
            try:
                stdscr.addstr(draw_row + i, 0, segment)
            except curses.error:
                pass

        # 帮助栏
        help_parts = [" ^W 完成", "^Q 放弃"]
        config = load_config()
        if config.get('deepseek_api_key'):
            help_parts.append("^P AI提示")
        if config.get('flomo_email') and config.get('flomo_password'):
            help_parts.append("^S 发送Flomo")
        draw_help_bar(stdscr, " " + "  ".join(help_parts))

        # 状态栏
        wc = word_count(lines)
        date_display = datetime.now().strftime("%Y年%m月%d日")
        mode = "提示写作" if prompt_text else "自由写作"
        left = f" {date_display}  ·  {mode}"
        right = f"第{cy + 1}行/共{len(lines)}行  {wc}字 "
        draw_status(stdscr, left=left, right=right)

        # 光标
        screen_row = draw_row + (vi_cursor - scroll_y)
        try:
            stdscr.move(screen_row, scx_cursor)
        except curses.error:
            pass

        stdscr.refresh()
        ch_data = read_utf8_char(stdscr)
        ch = ch_data[0]
        char = ch_data[1]
        continue_sticky = False

        # ── 导航 ──

        if ch == curses.KEY_UP:
            if vi_cursor > 0:
                if target_screen_cx is None:
                    target_screen_cx = scx_cursor
                for vi2 in range(vi_cursor - 1, -1, -1):
                    if vrows[vi2][0] != vrows[vi_cursor][0] or vi2 == 0:
                        li2, scol2, ecol2 = vrows[vi2]
                        cy = li2
                        vis_pos = 0
                        cx = scol2
                        for k in range(scol2, ecol2):
                            if vis_pos >= target_screen_cx:
                                break
                            vis_pos += char_width(lines[li2][k])
                            cx = k + 1
                        cx = min(cx, len(lines[cy]))
                        break
            continue_sticky = True

        elif ch == curses.KEY_DOWN:
            if vi_cursor < len(vrows) - 1:
                if target_screen_cx is None:
                    target_screen_cx = scx_cursor
                current_li = vrows[vi_cursor][0]
                for vi2 in range(vi_cursor + 1, len(vrows)):
                    if vrows[vi2][0] != current_li:
                        li2, scol2, ecol2 = vrows[vi2]
                        cy = li2
                        vis_pos = 0
                        cx = scol2
                        for k in range(scol2, ecol2):
                            if vis_pos >= target_screen_cx:
                                break
                            vis_pos += char_width(lines[li2][k])
                            cx = k + 1
                        cx = min(cx, len(lines[cy]))
                        break
            continue_sticky = True

        elif ch == curses.KEY_LEFT:
            if cx > 0:
                cx -= 1
            elif cy > 0:
                cy -= 1
                cx = len(lines[cy])

        elif ch == curses.KEY_RIGHT:
            if cx < len(lines[cy]):
                cx += 1
            elif cy < len(lines) - 1:
                cy += 1
                cx = 0

        elif ch == curses.KEY_HOME:
            li, scol, ecol = vrows[vi_cursor]
            cx = scol

        elif ch == curses.KEY_END:
            li, scol, ecol = vrows[vi_cursor]
            cx = ecol

        elif ch == curses.KEY_PPAGE:
            target_vi = max(0, vi_cursor - text_h)
            if target_screen_cx is None:
                target_screen_cx = scx_cursor
            li2, scol2, ecol2 = vrows[target_vi]
            cy = li2
            cx = scol2

        elif ch == curses.KEY_NPAGE:
            target_vi = min(len(vrows) - 1, vi_cursor + text_h)
            if target_screen_cx is None:
                target_screen_cx = scx_cursor
            li2, scol2, ecol2 = vrows[target_vi]
            cy = li2
            cx = scol2

        # ── 编辑 ──

        elif ch in (curses.KEY_BACKSPACE, 127, 8):
            if cx > 0:
                lines[cy] = lines[cy][:cx - 1] + lines[cy][cx:]
                cx -= 1
            elif cy > 0:
                cx = len(lines[cy - 1])
                lines[cy - 1] += lines[cy]
                lines.pop(cy)
                cy -= 1

        elif ch == curses.KEY_DC:
            if cx < len(lines[cy]):
                lines[cy] = lines[cy][:cx] + lines[cy][cx + 1:]
            elif cy < len(lines) - 1:
                lines[cy] += lines[cy + 1]
                lines.pop(cy + 1)

        elif ch in (curses.KEY_ENTER, 10, 13):
            rest = lines[cy][cx:]
            lines[cy] = lines[cy][:cx]
            cy += 1
            lines.insert(cy, rest)
            cx = 0

        elif ch == 9:  # Tab
            spaces = " " * TAB_WIDTH
            lines[cy] = lines[cy][:cx] + spaces + lines[cy][cx:]
            cx += TAB_WIDTH

        # ── 命令 ──

        elif ch == 16:  # Ctrl+P — AI 生成提示词
            cfg = load_config()
            api_key = cfg.get('deepseek_api_key', '')
            if not api_key:
                draw_help_bar(stdscr, " 请先在设置中配置 Deepseek API Key (按[s]进入设置)")
                draw_status(stdscr, left=" 错误: 未配置 Deepseek API Key")
                stdscr.refresh()
                time.sleep(2)
                continue

            # 显示生成中消息
            draw_help_bar(stdscr, " AI 生成提示词中，请稍候...")
            draw_status(stdscr, left=" 生成中...", right="")
            stdscr.refresh()

            experience = cfg.get('personal_experience', '')
            hobbies = cfg.get('personal_hobbies', '')

            result = generate_ai_prompt(api_key, experience, hobbies)
            if result:
                prompt_text = result
                # 重新计算提示词显示
                wrapped = textwrap.fill(prompt_text, width=w - 6)
                prompt_lines_new = wrapped.split('\n')
                prompt_h = len(prompt_lines_new) + 3
                prompt_lines = prompt_lines_new
                draw_help_bar(stdscr, " ✓ AI 提示词已生成")
                draw_status(stdscr, left=" 提示词已生成")
            else:
                draw_help_bar(stdscr, " ✗ 生成失败，请检查 API Key 和网络连接")
                draw_status(stdscr, left=" 生成失败")
            stdscr.refresh()
            time.sleep(1.5)
            continue

        elif ch == 19:  # Ctrl+S — 保存并发送到 Flomo
            text = '\n'.join(lines).strip()
            if not text:
                draw_help_bar(stdscr, " 日记内容为空，无法发送")
                stdscr.refresh()
                time.sleep(1.5)
                continue

            # 显示发送中消息
            draw_help_bar(stdscr, " 正在发送到 Flomo...")
            draw_status(stdscr, left=" 发送中...", right="")
            stdscr.refresh()

            cfg = load_config()
            success, msg = send_to_flomo(text, cfg)

            # 显示结果
            stdscr.erase()
            curses.curs_set(0)
            show_message(stdscr, msg, duration=2)
            return text

        elif ch == 23:  # Ctrl+W — 完成并保存
            text = '\n'.join(lines).strip()
            if not text:
                curses.curs_set(0)
                return None
            curses.curs_set(0)
            return text

        elif ch == 17:  # Ctrl+Q — 放弃
            text = '\n'.join(lines).strip()
            if text:
                if confirm(stdscr, "放弃这篇日记？"):
                    curses.curs_set(0)
                    return None
                curses.curs_set(1)
            else:
                curses.curs_set(0)
                return None

        elif ch == 27:  # Esc — 同 Ctrl+Q
            text = '\n'.join(lines).strip()
            if text:
                if confirm(stdscr, "放弃这篇日记？"):
                    curses.curs_set(0)
                    return None
                curses.curs_set(1)
            else:
                curses.curs_set(0)
                return None

        # ── 可打印字符（包括中文） ──

        elif char is not None and len(char) == 1 and 32 <= ord(char) <= 126:
            lines[cy] = lines[cy][:cx] + char + lines[cy][cx:]
            cx += 1

        elif char is not None and ord(char) > 127:
            # 中文字符或多字节字符
            lines[cy] = lines[cy][:cx] + char + lines[cy][cx:]
            cx += 1

        if not continue_sticky:
            target_screen_cx = None

# ── 主函数 ──────────────────────────────────────────────────────────────────

def main(stdscr):
    # 设置 locale 以支持中文
    try:
        locale.setlocale(locale.LC_ALL, '')
    except locale.Error:
        pass

    curses.raw()
    stdscr.keypad(True)
    curses.use_default_colors()
    curses.set_escdelay(25)
    curses.curs_set(0)

    # 颜色
    curses.init_pair(1, curses.COLOR_YELLOW, -1)
    accent = curses.color_pair(1)

    ensure_journal_dir()

    while True:
        action = draw_main_screen(stdscr, accent)

        if action is None:
            break

        if action == "settings":
            settings_screen(stdscr, accent)
            continue

        if action == "webdav":
            config = load_config()
            show_message(stdscr, "正在双向同步 WebDAV...", duration=0.5)
            success, msg = sync_to_webdav(config)
            show_message(stdscr, msg, duration=3)
            continue

        if action == "view":
            # 浏览和查看过往条目
            while True:
                filename = entry_browser(stdscr, accent)
                if filename is None:
                    break
                entry_viewer(stdscr, accent, filename)
            continue

        # 选择提示词
        if action == "prompt":
            prompt_text = random.choice(PROMPTS)
        else:
            prompt_text = None

        # 写条目
        text = journal_editor(stdscr, accent, prompt_text=prompt_text)

        if text is None:
            continue

        # 如果有提示词，将提示词附加到保存的文本中
        if prompt_text:
            full_text = f"提示词: {prompt_text}\n\n{text}"
        else:
            full_text = f"自由写作\n\n{text}"

        # 添加元数据
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        wc = word_count(text.split('\n'))
        header = f"日期: {timestamp}\n字数: {wc}\n\n"
        full_text = header + full_text

        save_entry(full_text)


if __name__ == "__main__":
    try:
        curses.wrapper(main)
    except KeyboardInterrupt:
        pass
    print("再见。")
