# Ouroboros Exception Mapper

异常映射器模块，用于将数据模型层异常转换为 Ouroboros 通用异常体系。

## 功能说明

本模块提供 `DataModelExceptionMapper` 类，实现数据模型异常到 Ouroboros 异常的单向转换。

### 映射规则

| 数据模型异常类型 | 映射到 Ouroboros 异常 | 说明 |
|----------------|---------------------|------|
| StatementException | PlatformException | 语句异常（语法错误、格式错误等）属于开发者使用不当 |
| DataValidationException | ValidationException | 数据验证异常（字段验证失败等）属于数据验证错误 |
| DataAccessException | InternalException | 数据访问异常（数据库错误等）属于底层内部错误 |

### 异常层次

#### DataModelException 异常体系

```
DataModelException (数据模型异常基类)
├── StatementException (语句异常)
│   ├── NormalizeException (规范化异常)
│   ├── TranspileException (转译异常)
│   ├── InvalidStatementException (无效语句异常)
│   ├── InvalidEntityNameException (无效实体名异常)
│   └── StatementSyntaxException (语句语法异常)
├── DataValidationException (数据验证异常)
│   ├── RecordValidationException (记录验证异常)
│   └── PrimaryKeyValidationException (主键验证异常)
└── DataAccessException (数据访问异常)
    ├── DatabaseException (数据库异常)
    └── QueryExecutionException (查询执行异常)
```

## 使用方法

### 基本用法

```java
import com.ouroboros.data.exception.InvalidStatementException;
import com.ouroboros.data.exception.DataValidationException;
import com.ouroboros.data.exception.RecordValidationException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.exception.mapper.DataModelExceptionMapper;

// 语句异常 -> PlatformException
var statementException = new InvalidStatementException("无效的查询语句");
OuroborosException result1 = DataModelExceptionMapper.toOuroborosException(statementException);
// 结果: PlatformException("无效的查询语句", statementException)

// 数据验证异常 -> ValidationException
var validationException = new DataValidationException("数据验证失败");
OuroborosException result2 = DataModelExceptionMapper.toOuroborosException(validationException);
// 结果: ValidationException("数据验证失败")

// 记录验证异常（带字段错误）-> ValidationException（字段错误信息会自动传递）
Map<String, List<String>> fieldErrors = new HashMap<>();
fieldErrors.put("name", Arrays.asList("名称不能为空", "名称长度不能超过50个字符"));
fieldErrors.put("email", Arrays.asList("邮箱格式不正确"));

var recordValidationException = new RecordValidationException(fieldErrors);
ValidationException result3 = (ValidationException) DataModelExceptionMapper.toOuroborosException(recordValidationException);
// 结果: ValidationException，其 errors map 包含:
// {"name": "名称不能为空,名称长度不能超过50个字符", "email": "邮箱格式不正确"}
```

### 转换 Throwable

```java
try {
    // 某些可能抛出异常的操作
    someDataModelOperation();
} catch (Throwable e) {
    // 统一转换为 OuroborosException
    OuroborosException ouroborosException =
        DataModelExceptionMapper.toOuroborosException(e);
    throw ouroborosException;
}
```

### 转换为指定类型

```java
import com.ouroboros.exception.PlatformException;
import com.ouroboros.exception.ValidationException;

var statementException = new InvalidStatementException("测试异常");
var validationException = new DataValidationException("验证错误");

// 转换为指定的 OuroborosException 子类
PlatformException platformException =
    DataModelExceptionMapper.toOuroborosException(
        statementException,
        PlatformException.class
    );

ValidationException validation =
    DataModelExceptionMapper.toOuroborosException(
        validationException,
        ValidationException.class
    );
```

## Maven 依赖

```xml
<dependency>
    <groupId>com.ouroboros</groupId>
    <artifactId>ouroboros-exception-mapper</artifactId>
    <version>${ouroboros.version}</version>
</dependency>
```

## 设计目标

1. **解耦异常体系**: 数据模型层使用独立的异常体系，通过映射器转换为通用异常
2. **清晰的异常分层**: 区分语句错误、数据验证错误和数据访问错误
3. **异常链边界**: 语句、访问及默认包装分支保留原始 cause；数据校验分支只传递字段错误或消息，不保留 cause
4. **灵活的转换策略**: 支持自动映射和手动指定目标类型
5. **字段错误传递**: `RecordValidationException` 的字段级错误信息会自动传递到 `ValidationException.errors` map 中

## 特性说明

### 字段错误信息映射

当转换 `RecordValidationException` 时，映射器会：
1. 提取所有字段的错误列表（`Map<String, List<String>>`）
2. 将每个字段的多个错误消息用逗号合并为单个字符串
3. 填充到 `ValidationException` 的 `errors` map 中（`Map<String, String>`）

**示例：**
```java
// RecordValidationException 字段错误
Map<String, List<String>> fieldErrors = Map.of(
    "password", List.of("密码强度不够", "密码长度至少8位")
);

// 转换后的 ValidationException
ValidationException result = ...;
result.getErrors().get("password"); // "密码强度不够,密码长度至少8位"
```

## 注意事项

- 所有转换方法都是静态的，无需实例化
- 映射器不会修改原始异常，而是创建新的 OuroborosException 实例
- 如果输入为 null，转换结果也为 null
- 是否保留 cause 取决于映射分支；`DataValidationException` 转为 `ValidationException` 时未传入 cause，不能从目标异常还原其完整原始堆栈
- `RecordValidationException` 的字段错误会自动合并（用逗号分隔）后传递给 `ValidationException`
- 对于非 `RecordValidationException` 的 `DataValidationException`，只传递异常消息
