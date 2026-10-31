## EB-ARCH-001 通用调度与格式适配保持职责边界

**具体规则**：对于只增加输入输出格式、不改变通用执行语义的需求，格式专用解析、序列化及驱动调用应封装于适配组件；不得仅为该格式让 BatchJob 或 JobBuilder 增加专用类型判断及解析分支。通用语义确需变化时可以修改 core；不要求每项功能新建模块。

**类别**：结构依赖／职责与扩展

**规则依据**：

- [BatchJob.java:61](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/BatchJob.java:61)：实现：通过 RecordReader、RecordWriter、RecordProcessor 抽象协作。
- [JobBuilder.java:79](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/JobBuilder.java:79)：实现：读取组件通过接口注册。
- [pom.xml:62](D:/four/graduation/project/easy-batch-master/easy-batch-json/pom.xml:62)：依赖：JSON 适配模块依赖 core。此边界约束由实现推导，并非维护者明文禁止。

**符合示例**：JSON 解析留在 JSON Reader 内，作业通过 reader(...) 使用。

**违规示例**：在 BatchJob 内检测 NDJSON 类型并手写 JSON 解析。

## EB-ARCH-002 读取结束信号保持唯一含义

**具体规则**：适用于标准作业使用的 RecordReader：每次返回下一条 Record；只有逻辑数据源结束才返回 null。若需求允许跳过空行或注释，Reader 应继续寻找下一条记录，不得将跳过单条输入表达为返回 null。

**类别**：接口与生命周期契约

**规则依据**：

- [RecordReader.java:48](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/reader/RecordReader.java:48)：接口注释明确规定下一条记录或到达末尾时返回 null。
- [BatchJob.java:183](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/BatchJob.java:183)：实现：收到 null 后停止读取。
- [FlatFileRecordReaderTest.java:53](D:/four/graduation/project/easy-batch-master/easy-batch-flatfile/src/test/java/org/jeasy/batch/flatfile/FlatFileRecordReaderTest.java:53)：测试：顺序读取后返回 null，另有空文件测试。

**符合示例**：跳过获准忽略的空行后继续读取下一行，EOF 返回 null。

**违规示例**：还有有效数据时，遇到空行就返回 null，截断整个作业。

## EB-ARCH-003 组件生命周期由标准作业入口统一驱动

**具体规则**：接入标准作业的 Reader、Writer 应支持由作业统一调用 open→读取或写入→close 的生命周期。组件不得要求调用方额外执行格式专用启动或结束步骤，才能完成标准作业流程。无资源组件可以使用接口默认的空 open、close 实现；直接使用组件而不经过 Job 的场景不属于本条范围。

**类别**：接口与生命周期契约

**规则依据**：

- [RecordReader.java:38](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/reader/RecordReader.java:38)：接口提供 open、readRecord、close，open 与 close 有默认空实现。
- [RecordWriter.java:36](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/writer/RecordWriter.java:36)：接口提供 Writer 的 open、writeRecords、close。
- [BatchJob.java:104](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/BatchJob.java:104)：实现：标准作业统一调用组件打开、读写与关闭。“无需格式专用启动步骤”是根据该协作方式提出的约束。

**符合示例**：文件 Reader 通过标准 open 准备输入，作业随后调用 readRecord 和 close。

**违规示例**：新 Reader 虽然配置给 JobBuilder，却必须由业务调用方额外调用 beginNdjsonSession 才能读取。

## EB-ARCH-004 读取失败进入作业错误通道

**具体规则**：Reader 遇到未被需求明确允许恢复的读取错误时，必须向调用方抛出异常，交由读取异常通知和作业报告处理；不得以 null 或伪造记录将失败包装为成功。允许明确的重试或恢复策略，恢复失败仍须保留失败语义。

**类别**：接口与生命周期契约

**规则依据**：

- [RecordReader.java:50](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/reader/RecordReader.java:50)：接口明确 EOF 与读取异常两种情况。
- [BatchJob.java:198](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/BatchJob.java:198)：实现：通知 onRecordReadingException 后重抛。
- [BatchJobTest.java:289](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/test/java/org/jeasy/batch/core/job/BatchJobTest.java:289)：测试：无法读取下一条时作业失败。

**符合示例**：I/O 异常直接抛出，作业报告 FAILED。

**违规示例**：catch IOException 后返回 null，导致作业误报正常读完。

## EB-ARCH-005 组件协作沿用 Record 与 Batch 数据模型

**具体规则**：接入现有作业流水线的输入和处理组件通过 Record<P> 交换单条数据，Writer 通过 Batch<P> 接收输出；新增格式不得仅为自身绕开公共模型建立并行调度数据通道。允许新增 Record 实现和 payload 类型，不要求统一使用字符串。

**类别**：结构依赖／接口与生命周期契约

**规则依据**：

- [Record.java:42](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/record/Record.java:42)：接口规定 Header 与 payload 的访问入口。
- [RecordProcessor.java:47](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/processor/RecordProcessor.java:47)：接口规定 Record 输入输出。
- [RecordWriter.java:51](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/writer/RecordWriter.java:51)：接口规定 Batch 输入。

**符合示例**：新增记录类型实现 Record，业务对象放在 payload。

**违规示例**：新格式另建 List<Map> 通道直接从 Reader 传给专用调度器，绕开标准流水线。

## EB-ARCH-006 过滤结果只终止当前记录的后续处理

**具体规则**：Processor 返回 null 表示当前记录被过滤。CompositeRecordProcessor 及调度器应跳过该记录后续处理和写出，并继续读取后续记录；不得把处理阶段的 null 当成整个数据源结束。

**类别**：接口与生命周期契约

**规则依据**：

- [RecordProcessor.java:29](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/processor/RecordProcessor.java:29)：接口注释明确 null 的过滤及跳过后续处理语义。
- [CompositeRecordProcessor.java:58](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/processor/CompositeRecordProcessor.java:58)：实现：遇到 null 停止当前处理链。
- [CompositeRecordProcessorTest.java:67](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/test/java/org/jeasy/batch/core/processor/CompositeRecordProcessorTest.java:67)：测试：过滤后不调用后续 Processor。

**符合示例**：第二个 Processor 过滤一条记录，第三个不处理它，下一条仍正常处理。

**违规示例**：过滤后仍把 null 交给后续 Mapper，或者停止整个 Job。

## EB-ARCH-007 可配置扩展组件满足对应接口类型

**具体规则**：承诺通过现有 JobBuilder 入口接入的 Reader、Processor、Filter、Mapper、Validator、Marshaller 和 Writer，必须是各入口形参要求接口的可赋值子类型。允许间接继承实现；纯辅助类无需实现这些接口，不按类名后缀判断角色。

**类别**：结构依赖

**规则依据**：

- [JobBuilder.java:79](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/JobBuilder.java:79)：API：reader 接受 RecordReader；91、103、115、127、139、151 行分别声明其他角色接口。
- [AbstractFileRecordReader.java:35](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/reader/AbstractFileRecordReader.java:35)：实现：文件 Reader 可经父类间接实现接口。

**符合示例**：FlatFileRecordReader 继承 AbstractFileRecordReader，能够赋给 RecordReader。

**违规示例**：声称可配置的 Reader 只定义同名方法，既未直接也未间接实现接口，另写入口才能调用。

## EB-ARCH-008 处理流水线保持注册顺序与逐级传递

**具体规则**：使用 JobBuilder 注册处理组件时，Filter、Mapper、Validator、Marshaller、普通 Processor 应按注册顺序进入同一处理链；每个组件接收前一个组件的输出。新增处理功能不得在未改变通用 API 语义的情况下私自重排或改为全部接收原始输入。

**类别**：职责与扩展／接口与生命周期契约

**规则依据**：

- [JobBuilder.java:91](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/JobBuilder.java:91)：实现：各角色都调用 addRecordProcessor；后续 mapper、validator 等采用一致方式。
- [CompositeRecordProcessor.java:58](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/processor/CompositeRecordProcessor.java:58)：实现：按列表顺序将 processedRecord 传给下一个组件。
- [BatchJobTest.java:139](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/test/java/org/jeasy/batch/core/job/BatchJobTest.java:139)：测试：每条记录按顺序调用处理组件，随后写出。

**符合示例**：先将文本映射为对象，再由 Validator 检查对象。

**违规示例**：为了优化而将 Validator 移到 Mapper 前，或让下游始终收到原始字符串。

## EB-ARCH-009 Filter 保留通过记录的原始身份

**具体规则**：实现 RecordFilter 的组件应在通过时返回输入 Record，在拒绝时返回 null；不应借过滤角色替换记录或完成 payload 转换。需要转换时使用 Mapper 或一般 Processor。

**类别**：职责与扩展／接口与生命周期契约

**规则依据**：

- [RecordFilter.java:35](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/filter/RecordFilter.java:35)：接口继承 RecordProcessor<R,R>；41 行注释要求通过时返回 input record。
- [EmptyStringRecordFilter.java:37](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/filter/EmptyStringRecordFilter.java:37)：实现：不符合过滤条件时返回输入 record。

**符合示例**：非空记录原样返回，空记录返回 null。

**违规示例**：Filter 检查通过后返回另一个 GenericRecord 并顺便转换 payload。

## EB-ARCH-010 一对一表示转换保留记录来源信息

**具体规则**：仅改变同一逻辑记录表示形式的 Mapper、Marshaller，应保留输入 Header 所承载的来源与编号等身份信息，避免无故重建来源。拆分、聚合或生成新逻辑记录时可定义新的 Header，但须说明对应关系。

**类别**：职责与扩展

**规则依据**：

- [JsonRecordMapper.java:70](D:/four/graduation/project/easy-batch-master/easy-batch-json/src/main/java/org/jeasy/batch/json/JsonRecordMapper.java:70)：实现：新 GenericRecord 沿用 record.getHeader()。
- [JsonRecordMarshaller.java:62](D:/four/graduation/project/easy-batch-master/easy-batch-json/src/main/java/org/jeasy/batch/json/JsonRecordMarshaller.java:62)：实现：序列化后的 JsonRecord 沿用输入 Header。
- [JsonRecordMapperTest.java:59](D:/four/graduation/project/easy-batch-master/easy-batch-json/src/test/java/org/jeasy/batch/json/JsonRecordMapperTest.java:59)：testMapRecord 断言输出 Header 等于输入 Header。由实现及测试推导，不是所有 RecordProcessor 的统一契约。

**符合示例**：JSON 字符串映射为对象时沿用原 Header。

**违规示例**：单纯序列化对象时把编号重设为 1、来源改成未知。

## EB-ARCH-011 校验与过滤保持错误语义区分

**具体规则**：对于要求将校验不通过记为处理错误的 Validator，应抛出异常进入处理错误通道；对沿用 BeanValidationRecordValidator 现有行为的校验组件，校验通过返回输入记录；对于其他校验组件，应依据其公开契约确认是否允许替换或规范化记录。不得未经功能规格允许就返回 null，将错误改报为过滤。若明确采用无效记录过滤策略，应使用或说明过滤语义。

**类别**：职责与扩展／接口与生命周期契约

**规则依据**：

- [RecordValidator.java:29](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/validator/RecordValidator.java:29)：接口：校验角色，类型保持 R→R；接口本身没有明文规定全部校验失败必须抛异常。
- [BeanValidationRecordValidator.java:56](D:/four/graduation/project/easy-batch-master/easy-batch-validation/src/main/java/org/jeasy/batch/validation/BeanValidationRecordValidator.java:56)：实现：有约束违反时抛异常，通过时返回原记录。
- [RecordProcessor.java:31](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/processor/RecordProcessor.java:31)：接口明确异常记错误、null 记过滤。该规则的适用条件据此限定。

**符合示例**：Bean 校验失败抛异常，作业统计为处理错误。

**违规示例**：需求要求记录校验错误，但 Validator 返回 null，错误数始终为零。

## EB-ARCH-012 记录处理错误交由作业阈值统一决策

**具体规则**：对进入标准流水线、未被组件恢复的处理异常，调度器应沿用记录错误通知、错误计数、lastError 和 errorThreshold 的决策机制；组件不得自行结束整个进程或私改作业状态。保留当前超过阈值才终止的边界，除非需求明确修改通用错误策略。

**类别**：职责与扩展／接口与生命周期契约

**规则依据**：

- [JobBuilder.java:157](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/JobBuilder.java:157)：API 文档说明阈值被超过后终止。
- [BatchJob.java:233](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/BatchJob.java:233)：实现：记录异常、增加计数、超过阈值时抛 ErrorThresholdExceededException。
- [BatchJobTest.java:337](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/test/java/org/jeasy/batch/core/job/BatchJobTest.java:337)：测试覆盖错误阈值。

**符合示例**：Processor 抛异常；累计未超过阈值时作业按现有机制继续。

**违规示例**：Processor 捕获业务异常后调用 System.exit，绕过作业报告和资源清理。

## EB-ARCH-013 Writer 复用作业的批量写出边界

**具体规则**：标准 Writer 接收作业传入的 Batch 并完成该批输出；批次积累及 batchSize 的通用调度留在作业层。Writer 可以内部逐条写或使用数据库批处理，但不得仅为适配一种输出源另行驱动作业读取或业务处理。

**类别**：职责与扩展／接口与生命周期契约

**规则依据**：

- [RecordWriter.java:45](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/writer/RecordWriter.java:45)：接口规定写出一批记录。
- [BatchJob.java:180](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/BatchJob.java:180)：实现：作业组装处理后的 Batch。
- [BatchJob.java:246](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/BatchJob.java:246)：实现：非空批次交给 Writer。职责边界部分由调用关系推导。

**符合示例**：Jdbc Writer 将接收到的一批记录绑定语句并批量提交。

**违规示例**：Writer 自己打开输入文件、读取下一批并重新运行 Mapper，形成第二套流水线。

## EB-ARCH-014 Writer 将未恢复的写出失败向上报告

**具体规则**：标准 Writer 在未成功完成约定写入且无法恢复时应抛出异常，不得只记录日志后正常返回，使失败批次被作业计为成功。允许组件按照明确约定先自行恢复；部分写入成功时仍须如实报告未完成的约定写入，具体事务与幂等策略由组件契约说明。

**类别**：接口与生命周期契约

**规则依据**：

- [RecordWriter.java:45](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/writer/RecordWriter.java:45)：接口明确 writeRecords 写入一批记录，并在写入错误时抛异常。
- [BatchJob.java:246](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/BatchJob.java:246)：实现：writeRecords 正常返回后增加写出计数；抛异常后进入异常处理路径。
- [BatchJobTest.java:305](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/test/java/org/jeasy/batch/core/job/BatchJobTest.java:305)：测试：未启用恢复场景下写出失败使作业失败。

**符合示例**：数据库 Writer 无法恢复批写失败时抛异常，由作业接收失败信息。

**违规示例**：Writer 捕获数据库异常后只记日志便返回，作业把整个失败批次计入 writeCount。

## EB-ARCH-015 扩展执行路径保持监听器通知协议

**具体规则**：修改标准作业的读取、处理、写出路径时，应保留相应阶段现有的执行前、正常完成及异常通知，并传递对应记录、批次或异常；不得将失败伪装成正常完成。各阶段按自身既有回调语义执行，不要求失败后仍补发正常完成通知。

**类别**：接口与生命周期契约

**规则依据**：

- [BatchJob.java:198](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/BatchJob.java:198)：实现：读取前后及读取异常分别通知。
- [BatchJob.java:219](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/BatchJob.java:219)：实现：处理前回调可以替换或过滤待处理记录；不能把所有监听器理解为纯观察者。
- [BatchJobTest.java:500](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/test/java/org/jeasy/batch/core/job/BatchJobTest.java:500)：测试覆盖读取监听器；515 行覆盖读取异常通知。
- [BatchJobTest.java:566](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/test/java/org/jeasy/batch/core/job/BatchJobTest.java:566)：测试覆盖处理前后通知；548 行覆盖写出异常通知。

**符合示例**：新增读取路径仍先通知 beforeRecordReading，发生读取异常时通知 onRecordReadingException。

**违规示例**：新格式的快速读取分支绕过监听器，或者写入失败却仍发出正常写入完成通知。

## EB-ARCH-016 局部重试包装保持组件原协作接口

**具体规则**：对现有 Reader、Processor 或 Writer 增加局部重试、且不改变通用调度语义时，优先使用实现原接口的包装器接入。Reader、Writer 包装器应按被包装组件的生命周期契约转发 open、close，使作业仍能通过原接口完整使用组件；不得仅转发读写而遗漏生命周期。通用调度语义确需变化时可另行论证，不要求所有操作都加重试。

**类别**：职责与扩展／接口与生命周期契约

**规则依据**：

- [RetryableRecordReader.java:40](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/reader/RetryableRecordReader.java:40)：实现：包装器实现 RecordReader；59、69 行分别委托 open、close。
- [RetryableRecordProcessor.java:40](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/processor/RetryableRecordProcessor.java:40)：实现：重试包装器保持 RecordProcessor 接口，不要求调度器识别专用组件类型。优先采用该包装方式由一致扩展实现推导。

**符合示例**：已有文件 Reader 套上重试包装器后，Job 仍通过同一接口打开、读取、关闭它。

**违规示例**：为一个网络 Reader 在 BatchJob 增加重试专用类型分支；或包装器只委托 readRecord，导致原 Reader 无法正常打开、关闭。

## EB-ARCH-017 对象字段类型转换通过转换器扩展点接入

**具体规则**：新增 ObjectMapper 支持的字段类型、且不改变通用映射语义时，应实现或复用 TypeConverter<String,T> 并通过已有注册机制接入；不得把字段类型转换特判放进 BatchJob，也不应仅为单个业务字段改写通用映射循环。通用转换算法确需修订时允许修改映射层。

**类别**：职责与扩展／结构依赖

**规则依据**：

- [TypeConverter.java:33](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/converter/TypeConverter.java:33)：接口定义源类型到目标类型的 convert。
- [ObjectMapper.java:115](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/mapper/ObjectMapper.java:115)：实现：从目标类型查找转换器。
- [ObjectMapper.java:167](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/mapper/ObjectMapper.java:167)：实现：调用转换器转换字段值。
- [ObjectMapper.java:211](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/mapper/ObjectMapper.java:211)：API 明确提供自定义字段转换器注册入口。扩展优先策略由该 API 和调用路径推导。

**符合示例**：为新的值对象实现字符串转换器，注册给 ObjectMapper。

**违规示例**：在 BatchJob 中判断某个业务字段是否为金额，再执行其类型转换。

## EB-ARCH-018 作业执行与作业实现通过 Job 契约解耦

**具体规则**：接入标准 JobExecutor 的作业必须满足 Job 接口，并通过 call 返回 JobReport；执行器通过 Job 抽象提交作业。不得仅为新增格式或业务任务，要求执行器识别具体 BatchJob 实现或调用其专用执行入口。允许应用实现新的 Job，不要求所有作业都使用 BatchJob。

**类别**：结构依赖／接口与生命周期契约

**规则依据**：

- [Job.java:33](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/Job.java:33)：接口继承 Callable<JobReport>，48 行声明 call。
- [JobExecutor.java:83](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/JobExecutor.java:83)：实现：execute 接受 Job，提交执行并等待报告。
- [BatchJob.java:54](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/BatchJob.java:54)：实现：包内可见的 BatchJob 实现 Job。保持执行器对具体实现的解耦是根据公开接口和可见性提出的约束。

**符合示例**：自定义 Job 实现 call 并返回报告，用 JobExecutor.execute(job) 启动。

**违规示例**：执行器为一种新业务识别某个具体 Job 类型，再绕过 call 调用该业务的专用执行方法。

## EB-ARCH-019 调度器保留正常与异常路径的关闭尝试

**具体规则**：修改标准 BatchJob 执行流程时，应保留进入受保护执行区间后，在正常结束和异常退出时对 Reader、Writer 的关闭尝试，包括组件打开失败后的退出路径；不得只在正常 EOF 分支清理，也不得因 Reader 的关闭异常而取消对 Writer 的关闭尝试。这里约束调度器发起关闭，不等同于证明组件资源已全部释放，也不扩大为进入该区间之前的任何异常均已受保护。

**类别**：接口与生命周期契约

**规则依据**：

- [BatchJob.java:104](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/BatchJob.java:104)：实现：open 与循环位于 try 内，finally 中调用 closeReader、closeWriter；start 位于 try 外。
- [BatchJob.java:311](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/BatchJob.java:311)：实现：closeReader 捕获关闭异常；随后 closeWriter 仍有机会执行。
- [BatchJobTest.java:157](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/test/java/org/jeasy/batch/core/job/BatchJobTest.java:157)：测试检查执行结束后的 Reader 关闭；164 行检查 Writer 关闭。
- [BatchJobTest.java:171](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/test/java/org/jeasy/batch/core/job/BatchJobTest.java:171)：测试：打开 Reader 失败后仍调用 Reader、Writer 的 close。

**符合示例**：读取抛异常后仍进入 finally，分别尝试关闭 Reader 和 Writer。

**违规示例**：只在 moreRecords 返回 false 后关闭组件，读取异常时跳过清理；或 Reader 关闭异常直接阻止 Writer 关闭。

## EB-ARCH-020 组件关闭操作释放其拥有的资源

**具体规则**：有资源的 Reader、Writer 应在 close 中释放由自身负责管理的资源，并考虑 open 未成功完成或仅部分完成后仍收到 close 的情况，避免未初始化资源的访问阻断已获得资源的清理。外部传入资源是否关闭以明确的所有权约定为准；无资源组件可保留默认空实现。不据此额外要求所有组件可重复打开或 close 必须可任意重复调用。

**类别**：接口与生命周期契约

**规则依据**：

- [FlatFileRecordReader.java:82](D:/four/graduation/project/easy-batch-master/easy-batch-flatfile/src/main/java/org/jeasy/batch/flatfile/FlatFileRecordReader.java:82)：实现：open 创建 BufferedReader，88 行的 close 先判空再关闭。
- [BatchJobTest.java:171](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/test/java/org/jeasy/batch/core/job/BatchJobTest.java:171)：测试表明 open 失败后也会尝试 close，因此组件清理应考虑未完全初始化。“部分初始化清理”和所有权边界属于据此提出的约束。
- [JsonFileRecordReader.java:74](D:/four/graduation/project/easy-batch-master/easy-batch-json/src/main/java/org/jeasy/batch/json/JsonFileRecordReader.java:74)：基线边界：创建内部 Reader 可能在赋值前失败，85 行直接调用其 close，存在待运行核实的未初始化清理风险；不能把本候选要求说成全库既已满足。

**符合示例**：组件打开第二个资源失败后，close 仍能关闭先前取得的第一个资源；尚未创建的资源先判空。

**违规示例**：close 首先访问尚未初始化的字段并抛空指针异常，导致此前取得的文件流没有机会关闭。

## EB-ARCH-021 批次写出失败按作业扫描配置恢复

**具体规则**：修改标准作业的批写异常处理时，应保留写入异常通知及 lastError 记录，并按 batchScanning 配置选择恢复路径：未开启扫描时传播批写异常；开启时逐条扫描失败批次，沿用扫描标记、成功写出计数和单条异常记录机制。只有明确修改通用恢复策略的需求才可改变该语义。扫描可能重复写入，不据此承诺恰好一次；具体 Writer 的事务与幂等能力需另行确认。

**类别**：接口与生命周期契约

**规则依据**：

- [BatchJob.java:256](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/BatchJob.java:256)：实现：批写异常时通知监听器、保存 lastError，并根据扫描开关分支。
- [BatchJob.java:269](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/BatchJob.java:269)：实现：扫描时逐条标记 scanned 并重写，记录成功计数或单条错误。
- [BatchJobTest.java:760](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/test/java/org/jeasy/batch/core/job/BatchJobTest.java:760)：测试：开启扫描后逐条重写；305 行测试未启用恢复时的写出失败。

**符合示例**：相同批写异常在未开启扫描时使作业失败，开启后进入既有逐条扫描路径。

**违规示例**：调度器忽略 batchScanning 开关，批写失败后始终丢弃批次继续运行，或无条件重复写整个批次。

## EB-ARCH-022 监听扩展通过既有注册入口接入

**具体规则**：对可由现有监听器契约表达的观察或处理前干预需求，应通过 JobBuilder 的相应监听器注册入口接入，不得仅为单个业务在 BatchJob 中硬编码其监听实现。允许 PipelineListener 按其契约替换或过滤待处理记录；需要新的通用事件时可以扩展监听机制，不强制所有需求使用现有回调。

**类别**：职责与扩展／结构依赖

**规则依据**：

- [JobBuilder.java:247](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/JobBuilder.java:247)：API：readerListener 注册读取监听器。
- [JobBuilder.java:260](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/JobBuilder.java:260)：API：pipelineListener 注册流水线监听器。
- [BatchJob.java:219](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/BatchJob.java:219)：实现：beforeRecordProcessing 的结果参与后续处理。复用监听入口、避免业务实现侵入调度器是根据该扩展机制推导的约束。

**符合示例**：业务统计通过 readerListener 注册，在读取事件回调中收集数据。

**违规示例**：为某个业务统计需求，在 BatchJob 中直接创建并调用该业务专用审计监听类。

## EB-ARCH-023 重试执行遵循策略并传播最终失败

**具体规则**：使用 RetryTemplate 或兼容其现有契约的重试执行逻辑时，应遵循 RetryPolicy 指定的尝试上限、重试间隔和时间单位；被重试操作持续失败、达到尝试上限时应向调用方抛出异常，不得转成 null、伪造成功或改为无限重试。此约束不要求所有异常都应重试；有副作用的操作需先明确重试适用性，通用重试策略确需变化时应同步修订其契约。

**类别**：接口与生命周期契约

**规则依据**：

- [RetryTemplate.java:49](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/retry/RetryTemplate.java:49)：接口注释：仍失败时抛异常。
- [RetryTemplate.java:57](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/retry/RetryTemplate.java:57)：实现：读取 maxAttempts、delay、timeUnit；失败到达上限后重抛异常，未到上限时等待再尝试。
- [RetryableRecordReader.java:64](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/reader/RetryableRecordReader.java:64)：实现：读取包装器把读操作交给重试模板执行。

**符合示例**：按配置尝试三次，前两次失败后等待，第三次仍失败则将异常交给作业。

**违规示例**：捕获最后一次失败后返回 null，让作业误以为输入结束；或忽略最大尝试次数永远重试。

## EB-ARCH-024 独占执行器由其生命周期负责人关闭

**具体规则**：创建并独占使用 JobExecutor 的调用方，应在不再提交作业时通过 shutdown 或 close 发起关闭，异常退出路径也应安排关闭。需要等待已提交作业终止时，按调用约定另行等待；shutdown 或 close 本身不等于已经等待全部作业完成。对于外部管理或共享的执行器、线程池，由约定的生命周期负责人关闭，不应由单个使用方擅自终止。

**类别**：接口与生命周期契约

**规则依据**：

- [JobExecutor.java:47](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/JobExecutor.java:47)：实现：JobExecutor 实现 AutoCloseable。
- [JobExecutor.java:128](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/JobExecutor.java:128)：实现：shutdown 调用底层 executorService.shutdown。
- [JobExecutor.java:137](D:/four/graduation/project/easy-batch-master/easy-batch-core/src/main/java/org/jeasy/batch/core/job/JobExecutor.java:137)：实现：awaitTermination 是独立等待操作；146 行 close 委托 shutdown。调用方的所有权责任由该 API 及资源管理方式推导。

**符合示例**：独占执行器通过 try-with-resources 使用，作用域退出时关闭；需要等待终止则另行调用等待操作。

**违规示例**：调用方反复创建独占执行器，用完后在正常或异常路径均不关闭；或单个任务擅自关闭共享执行器。
