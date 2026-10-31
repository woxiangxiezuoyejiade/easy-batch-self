# 从 DCL 摘录到 Easy Batch 候选规则 v0.2

日期：2026-10-29。本文基于用户提供的五张论文摘录，不代表已经核对论文全文或运行原论文工具。保留 v0.1 作为历史草稿；本版先具体修订两条结构相关规则，并示范如何记录核实结果，不宣称完成全项目检查。

## 可借鉴的方法

1. 区分多出来的不允许依赖（divergence）与缺少应有依赖（absence）。不要只检查违规 import，也要检查扩展组件是否遵循框架的类型约定。
2. 依赖需分清声明、创建、调用、继承、实现、异常声明、注解。只匹配 import 会漏掉全限定类型名，也可能把未使用 import 当真实依赖。
3. DCL 的 module 是规则中定义的类型集合，不必等于 Maven 模块。可定义为某模块生产类、某包类或明确列出的角色集合。必须把集合成员定义清楚，并排除测试、教程或内部类的误归类。
4. only A can-X B 限制的是谁可以对 B 建立 X 依赖，不表示 A 必须这样做，也不表示 A 只能依赖 B。A 的允许范围遗漏应用扩展类时会误报。仅凭目前有哪些实现，不能推导只有这些实现才被允许。
5. 用户摘录中的 must 约束支持继承、实现等指定关系，并不提供 must-access、must-create 等内部行为规定。不能用 must-implement 来证明 open 在 readRecord 前执行或 close 必然被调用。
6. 摘录报告将检测结果交由项目架构师确认。这支持“自动发现、人工核实”的工作分工，不支持 Easy Batch 当前候选已是 Ground Truth，也不证明给 Agent 规则一定改善开发效果。

下文的 DCL 风格表达都是概念映射，未检查工具语法或直接/间接继承语义，不是可直接运行的脚本。

## 共同适用版本

本地 pom 声明 7.0.3-SNAPSHOT；没有可识别 Git 提交。对应 baseline-sha256.csv 的 SHA-256：

`8DC47B79717A3E4D10684397C5DA027AD58626386A1285F22BE1A3EC071BB56C`

该文件记录源码等 522 个文件的指纹，不等于已经保存源码快照。正式实验需保存完整副本并逐文件核对。以下源码路径相对项目根目录；行号指此版本。

## EB-ARCH-001：输入格式扩展保持通用调度边界（修订）

### 第一步：证据

- 原文规定：core/.../reader/RecordReader.java:29–34 描述顺序读取角色；没有明文禁止调度器依赖格式实现，不能把后者说成接口规定。
- 实际调用：easy-batch-core/src/main/java/org/jeasy/batch/core/job/JobBuilder.java:79–82 接收 RecordReader 并保存；同目录 BatchJob.java:61、151、203、314 经由接口声明及调用 Reader。
- 一致方式：easy-batch-flatfile/src/main/java/org/jeasy/batch/flatfile/FlatFileRecordReader.java:46 通过父类接入；easy-batch-json/src/main/java/org/jeasy/batch/json/JsonRecordReader.java:60 直接实现接口。
- 模块证据：easy-batch-flatfile/pom.xml:56 和 easy-batch-json/pom.xml:62 依赖 core。core 的生产依赖未声明这些格式适配模块。
- 推断：格式适配与通用作业调度存在职责边界；维护者明确禁止反向依赖的设计声明尚未找到。

### 第二步：规则草稿

- 规则编号：EB-ARCH-001。
- 规则名称：输入格式扩展保持通用调度边界。
- 类别：结构依赖／职责与扩展；两个检查项分开判断。
- 规则内容：当需求只新增输入格式且不改变通用执行语义时，通过既有 Reader 协作机制接入；core 的通用作业调度代码不得为此直接依赖格式专用实现或解析库，也不得加入该格式的解析分支。
- 适用范围：core 的生产调度代码，重点 JobBuilder、BatchJob；格式实现集合以本次任务和所用库明确列出，不泛化为所有非 core 类。测试和教程不属于本条禁止来源。
- 触发条件：增加格式 Reader、接入新的输入解析库、修改读取配置或调度分派。
- 制定理由：避免每加一种格式都修改通用调度器，让输入扩展继续复用生命周期、处理和写出机制。
- 符合示例：在 JSON 适配侧实现 Reader，以 JobBuilder.reader(...) 配置。
- 违反示例：BatchJob 直接 new 某格式 Reader 并加入该格式的解析分支，仅为新格式增加专用执行路径。
- DCL 概念映射：Scheduler cannot-create FormatReaders；并分别考虑 cannot-declare、cannot-access 等关系。单个 cannot-create 不能代表禁止所有依赖。职责归属部分不能完全化为这种关系。

### 第三步：主动找反例

- BatchJob.java:87、381 自己创建并定义 NoOpRecordReader。这是合理默认组件，不能把规则写成“调度器不得创建任何 Reader”。
- core 自带 StringRecordReader、IterableRecordReader、RetryableRecordReader 等。不能写成“所有 Reader 均必须放在格式模块”。
- tutorials/.../advanced/recipes/RecipeRecordReader.java:37 是应用侧自定义实现。不能用 only 将允许实现者封闭为仓库现有格式模块。
- 例外与不适用：通用调度语义或 Reader 契约确需改变时可修改 core，但需说明变更为何通用、如何保持兼容及测试覆盖。通用包装器与默认 Reader 不属于格式专用实现集合。不要求新增模块。

### 第四步：检查办法、局限与基线

- 静态分析：检查 Maven 实际生产依赖和调度类的类型声明、构造调用、方法调用等关系。依赖集合必须记录为规则配置；不能凭类名包含 Json 就认定职责。
- 人工复核：检查无外部类型依赖但在 core 内手写格式解析的情况；判断某次 core 变更是否确属通用语义。
- 运行测试：新 Reader 经标准 JobBuilder 接入的集成测试及原有回归测试。
- 检查局限：import 搜索不覆盖反射、全限定名和手写解析逻辑；静态边界无违规也不能证明职责划分合理。
- 基线情况：本轮在 core/src/main/java 对已知适配包及 javax.json、Jackson、Gson 的 import 搜索没有命中；已核对 JobBuilder、BatchJob 的 Reader 调用为接口调用。此为有限静态检查，不能宣称全基线零违规。内部 NoOpReader 是缩小规则范围的证据，不记为违规。

### 第五步：状态

- 状态：根据实现推断，待确认。
- 适用版本：上述共同适用版本及文件指纹清单。
- 人工审定：尚无；需确认“通用调度代码”“格式专用类型”的集合与例外。检查器命中只称“疑似违反”，复核后再定性。

## EB-STRUCT-007：以 RecordReader 类型接入标准读取入口（新增）

### 第一步：证据

- 原文规定：easy-batch-core/src/main/java/org/jeasy/batch/core/job/JobBuilder.java:74–79 是注册 Reader 的入口，其形参明确为 RecordReader<I>。这里是公开 API 的类型约定。
- 一致方式：JsonRecordReader.java:60 直接实现 RecordReader；FlatFileRecordReader.java:46、JsonFileRecordReader.java:49 继承 AbstractFileRecordReader，后者在 core/.../reader/AbstractFileRecordReader.java:35 实现接口。
- 设计推断：将所有“面向标准作业入口的输入组件”要求为该接口的子类型，是由 API 类型要求推广到扩展角色的候选约束，尚无覆盖所有未来组件的维护者声明。

### 第二步：规则草稿

- 规则编号：EB-STRUCT-007。
- 规则名称：以 RecordReader 类型接入标准读取入口。
- 类别：结构依赖／接口契约。
- 规则内容：承诺直接接入现有 JobBuilder.reader 的具体输入组件，必须是 RecordReader 的可赋值子类型；允许直接实现或经父类间接实现。纯解析辅助类不必实现此接口。
- 适用范围：生产代码中面向标准作业读取入口的具体组件，包括内部类和应用扩展类；不按类名后缀推断角色。
- 触发条件：新增或重构可配置 Reader、调整继承关系、增加包装器。
- 制定理由：确保输入组件可复用现有配置与作业调度，不需要另造并行读取入口。
- 符合示例：JsonRecordReader 直接实现；FlatFileRecordReader 通过父类实现；包装器实现接口并委托。
- 违反示例：声称能直接接入 reader(...) 的输入类仅定义同名 readRecord 方法，却没有实现或继承该接口，最终只能另写调度调用它。
- DCL 概念映射：InputComponents must-implement RecordReader。应用前必须验证检测工具是否承认传递实现关系；否则使用 Java 的子类型可赋值检查表达本条。

### 第三步：主动找反例

- FlatFileRecordReader 是对“必须直接写 implements”的反例，但符合子类型约束。
- JsonFileRecordReader.java:89 的内部 Reader 继承 JsonRecordReader，表明检查器不能忽略内部类或只匹配公开类声明。
- RecipeRecordReader 表明允许集合不能限于框架格式模块。
- 例外与不适用：底层 JSON 解析器、文件扫描辅助类、测试替身以其自身职责确定，不因参与读取就全部纳入。若需求新增一种独立于现有 JobBuilder 的执行模型，应重新论证其架构，不用本规则强行裁剪。

### 第四步：检查办法、局限与基线

- 静态分析：在预先登记的组件集合中，检查能否赋值为 RecordReader；包括父类继承和接口继承。Java 编译能检查真实 reader(...) 调用的参数类型。
- 运行测试：至少一个标准 JobBuilder 接入用例；类型兼容不代表读取行为正确，仍需 EOF、异常、生命周期测试。
- 人工复核：判断组件集合是否遗漏了“另造入口绕过框架”的实现；不能通过把违规类排除出集合消除违规。
- 检查局限：仅按 *RecordReader 类名匹配会误包含辅助类、漏掉不同命名的输入组件；编译只会发现实际调用处的不兼容。
- 基线情况：本轮核对的 JsonRecordReader、FlatFileRecordReader、JsonFileRecordReader、RecipeRecordReader、NoOpRecordReader、RetryableRecordReader 均满足子类型约束。尚未进行全项目类型分析，也未运行编译，不报告全项目通过。

### 第五步：状态

- 状态：有明确文档依据（限于 JobBuilder 注册 Reader 的接口约定）。
- 适用版本：上述共同适用版本及文件指纹清单。
- 人工审定：尚无。“应纳入此规则的所有组件”仍需人工确定；不能把文档有依据等同于整条规则已人工审定。

## 下一步如何使用旧规则

v0.1 的 002、004、006 保留为行为契约候选，以接口注释为证据，行为用例验证；不要为了统一语法而硬翻译成 DCL。003 中“部分打开失败仍可清理”需核对现有实现：JsonFileRecordReader.java:74 的创建可能先抛异常，此时 85 行对尚未赋值字段的 close 调用存在空指针风险。这是待复现的基线问题；没有运行测试不能将其写成已证实泄漏。若未来正式执行新规则，不应把原有问题算作 Agent 引入的新违反。

状态统一使用：有明确文档依据／根据实现推断，待确认／已人工审定。前三者是用户要求的状态标签；同时用证据说明、审定人和审定记录保留依据与审批信息，因为文档依据与人工审批并非同一维度。当前没有任何条目标为已人工审定。

评测分别记录：基线违反、增量新违反、修复的原有违反；自动命中数与人工确认数分列。规则内部还应检查集合重叠和约束冲突：例如两个不相交集合分别用 only 限制同一创建关系，会使实际创建无从满足。用户摘录中工具不自动消除这种不一致，本项目也不能默认规则库天然一致。
