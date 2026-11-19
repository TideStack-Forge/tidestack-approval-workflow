# TideStack Approval Workflow

审批流核心 API/SPI、Flowable 实现与共享引擎测试契约。本仓由平台总仓按清单单向导出，默认分支为 `master`。

## 模块与依赖

审批模块位于 `core/ouroboros-approval-workflow`，包含 core、flowable 和 engine-testkit。为保持原始实现与编译依赖，本仓同时携带清单声明的 Data、字段类型、UI Schema、公共库支持模块及原有父 POM/BOM 层级，聚合模块限定为导出集合。

候选人解析、任务动作、事件与转译沿用原有 SPI。平台 manage、web、runtime、dev 和表单集成仍由总仓维护，未包含在本次引擎分发中。

## 构建

使用 Java 21 和 Maven 3.8.6+，先安装本仓 BOM，再运行引擎及依赖的构建：

```bash
mvn -B -f bom/pom.xml install
mvn -B -Ppublic -pl core/ouroboros-approval-workflow -am verify
```

engine-testkit 以附加 tests.jar 分发。消费方需按 API/SPI 提供宿主能力并装配 Flowable；本仓不提供完整平台运行镜像。公共 Maven 制品尚待总仓完成 Sonatype 发布，源码同步不表示已能从公共 Maven 仓解析这些制品。

## 贡献与授权

实现和测试统一在平台主仓维护，模块仓不独立维护业务分叉。同一 Maven 坐标由总仓统一发布。本分发采用 Apache License 2.0，见 `LICENSE`。
