# AngelBlock 天使方块插件

<div align="center">

一个强大的 Minecraft 服务器插件，允许玩家在空中放置特殊方块，支持自定义距离调整、领地保护和粒子特效。

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.2-green.svg)](https://github.com/mc506lw/AngelBlock)
[![API](https://img.shields.io/badge/API-1.21-orange.svg)](https://www.spigotmc.org/)
[![Java](https://img.shields.io/badge/Java-17-red.svg)](https://openjdk.org/projects/jdk/17/)

</div>

## 功能特性

- 空中放置特殊方块，支持任意位置
- 通过 Shift + 滚轮调整放置距离
- 精美的粒子特效预览
- 完整的领地保护支持（HuskClaims、Dominion）
- 数据持久化存储（SQLite）
- 支持限制玩家最大放置数量
- 管理员命令支持
- 完全兼容 Folia
- 支持 HEX 颜色和渐变效果

## 前置要求

- Minecraft 1.21+
- Java 17+
- Spigot/Paper/Folia 服务器

## 安装方法

1. 从 [Releases](https://github.com/mc506lw/AngelBlock/releases) 下载最新版本的 `AngelBlock-x.x.jar`
2. 将 jar 文件放入服务器的 `plugins` 文件夹
3. 重启服务器或使用 `/reload` 重载插件
4. 根据需要修改 `plugins/AngelBlock/config.yml` 配置文件

## 使用说明

### 获取天使方块

```bash
/angelblock              # 给自己天使方块
/angelblock <玩家名>     # 给指定玩家天使方块
```

### 放置和拆除

- **放置**: 右键点击空中位置
- **拆除**: 左键点击已放置的天使方块
- **调整距离**: 按住 Shift + 滚轮调整放置距离（默认 2-6 格）

### 管理命令

```bash
/angelblock reload                      # 重载配置文件
/angelblock removeall                    # 移除自己的所有天使方块
/angelblock removeall <玩家名>           # 移除指定玩家的天使方块（需要权限）
/angelblock removeall @a                # 移除所有玩家的天使方块（需要权限）
/angelblock removeall @r                # 移除随机玩家的天使方块（需要权限）
```

### 命令别名

插件支持 `/ab` 作为 `/angelblock` 的简写

## 权限系统

| 权限节点 | 描述 | 默认值 |
|---------|------|--------|
| `angelblock.use` | 允许使用天使方块 | true |
| `angelblock.give` | 允许给自己或他人天使方块 | op |
| `angelblock.reload` | 允许重载插件配置 | op |
| `angelblock.admin` | 所有插件管理权限 | op |
| `angelblock.removeall` | 允许移除自己的天使方块 | true |
| `angelblock.removeall.others` | 允许移除其他玩家的天使方块 | op |
| `angelblock.max.unlimited` | 允许无限放置天使方块 | op |

## 配置文件

插件会在首次启动时生成 `config.yml` 配置文件，主要配置项包括：

### 物品设置
```yaml
item:
  material: OBSIDIAN                    # 物品材质
  name: "<gradient:#cba6f7-#89b4fa>天使方块</gradient>"  # 物品名称
  lore:                                # 物品描述
    - "&7可以放置在任何位置"
    - "&eShift + 滚轮调整距离"
    - "&e右击放置，左击拆除"
```

### 粒子效果
```yaml
particle:
  color: "#f5e0dc"                      # 粒子颜色（HEX）
  size: 0.5                             # 粒子大小
```

### 放置距离
```yaml
distance:
  min: 2                                # 最小放置距离
  max: 6                                # 最大放置距离
  default: 3                            # 默认放置距离
```

### 消息提示
所有提示信息都支持自定义，支持 HEX 颜色和渐变效果。

## 兼容性

插件与以下领地保护插件兼容：

- **HuskClaims** - 自动检测并集成
- **Dominion** - 自动检测并集成

在受保护的领地内，玩家无法放置或破坏天使方块。

## 技术架构

- **语言**: Java 17
- **构建工具**: Gradle
- **数据库**: SQLite
- **API 版本**: 1.21
- **Folia 支持**: 是

## 开源协议

本项目采用 [MIT License](LICENSE) 开源协议。

## 作者

**mc506lw**

## QQ群

810329264

## 贡献

欢迎提交 Issue 和 Pull Request！

## 更新日志

### v1.2
- 添加 Folia 支持
- 优化粒子效果性能
- 新增领地保护兼容（HuskClaims、Dominion）
- 改进数据存储机制

## 常见问题

**Q: 天使方块会掉落吗？**
A: 天使方块不会自然掉落，只能通过命令获取。

**Q: 服务器重启后天使方块会消失吗？**
A: 不会，所有天使方块数据都会持久化保存到数据库中。

**Q: 如何限制玩家放置的天使方块数量？**
A: 可以通过权限系统控制，或者等待后续版本更新配置项。

**Q: 支持其他领地保护插件吗？**
A: 目前支持 HuskClaims 和 Dominion，未来会添加更多兼容性。

---

<div align="center">

Made with ❤️ by mc506lw

</div>
