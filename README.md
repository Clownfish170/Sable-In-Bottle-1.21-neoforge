# Sable in Bottle

把 Sable 的物理船只装进玻璃瓶——收纳、携带、随地释放。

Sable in Bottle 是一个小型功能模组，为 [Sable](https://github.com/ryanhcode/sable) 的物理船只提供"瓶子"玩法：用瓶子把一艘船收进来，带着它四处走，再在任何地方放出来。瓶内的结构会实时渲染在玻璃腔里，空瓶和满瓶一眼可辨。

- **Mod ID**：`sableinbottle`
- **版本**：1.2.0
- **Minecraft**：1.21.1 · **加载器**：NeoForge 21.1.228+ · **Java**：21
- **作者**：Clownfish170
- **许可证**：MIT

## 依赖

| 模组 | 版本 | 说明 |
| --- | --- | --- |
| [Sable](https://modrinth.com/mod/sable) | 2.0.0 – 2.x | 物理船只与子层级系统（必需） |
| Create | 6.0.10+ | 提供 Ponder 思索界面（必需） |
| Create: Aeronautics | 1.3.2+ | 船只内容（必需） |
| NeoForge | 21.1.228+ | 加载器（必需） |

## 功能说明

### 收进瓶子
手持**空瓶**右键一艘 Sable 船只，即可将它连同**完整数据**（方块、方块实体、Ticking 状态、结构信息）序列化进瓶中。瓶子会立刻变为"满瓶"状态。

### 携带与释放
满瓶是普通物品，可以放进箱子、背包堆叠。**手持满瓶右键地面**（不潜行），船只会**出现在你指向的位置**——保存的只是结构数据，与原始坐标无关，想带去哪里都行。目标位置缺少依赖模组的方块、或数据损坏时，会收到对应提示且不会释放。

### 放置瓶子
把瓶子作为方块摆出来展示：空瓶直接右键地面；满瓶则**潜行 + 右键地面**，内部结构会一并带入方块实体。

### 瓶内实时渲染
- 放置出来的瓶子方块会在玻璃腔内**实时渲染**所装的物理结构，空瓶则只有玻璃，两态一眼可辨。
- 结构悬浮在腔体中央，带缓慢的**上下浮动**动画，不会贴死在玻璃上。
- 玻璃与木塞使用 multipart 分层渲染，避免半透明面 z-fighting。

### 思索（Ponder）预览
手持瓶子悬停并按住思索键（默认 **W**），即可在思索界面里查看瓶内保存的结构：
- 结构按真实比例还原，并自动缩放、居中，高大结构也能完整入画；
- 结构朝向与释放后完全一致（方块的 facing/axis 等随姿态同步旋转）；
- 超出预览上限时会显示提示并以演示船代替。

### 配置
首次运行后生成 `config/sableinbottle.json`：

```json
{
  "max_structure_size": [48, 32, 48]
}
```

`max_structure_size` 控制思索预览允许的最大结构尺寸（X/Y/Z）。超过上限的瓶子在思索中会显示提示；调大它可以预览更大的船。

## 构建

需要 **JDK 21**，其余依赖由 Gradle 自动拉取。

```bash
# 构建 jar（输出到 neoforge/build/libs/）
./gradlew build

# 启动开发客户端进行测试
./gradlew :neoforge:runClient
```

产物路径：

```
neoforge/build/libs/sableinbottle-neoforge-1.21.1-1.2.0.jar
```

将该 jar 放入实例的 `mods/` 文件夹（同时确保已安装上表依赖）即可游玩。

Windows 下请用 `gradlew.bat build`。

## 项目结构

```
common/    共享源码与资源
neoforge/  NeoForge 平台实现（主交付模块）
buildSrc/  多加载器 Gradle 构建脚本
```
