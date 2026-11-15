package com.ouroboros.exception.mapper;

import java.util.Map;

import com.ouroboros.data.exception.*;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.exception.ValidationException;

/**
 * 数据模型异常映射器
 * <p>
 * 将数据模型层的异常转换为 Ouroboros 通用异常体系。
 * 映射规则：
 * <ul>
 *   <li>StatementException（语句异常）→ PlatformException（平台异常）</li>
 *   <li>DataValidationException（数据验证异常）→ ValidationException（验证异常）</li>
 *   <li>DataAccessException（数据访问异常）→ InternalException（内部异常）</li>
 * </ul>
 *
 * @author Song Mingxu
 */
public class DataModelExceptionMapper implements OuroborosExceptionMapper {

  /**
   * 将 DataModelException 转换为 OuroborosException
   *
   * @param exception 数据模型异常
   * @return Ouroboros 异常
   */
  public static OuroborosException toOuroborosException(DataModelException exception) {
    if (exception == null) {
      return null;
    }

    // 数据验证异常 → 验证异常
    if (exception instanceof DataValidationException) {
      return convertToValidationException((DataValidationException) exception);
    }

    // 语句异常 → 平台异常（开发者使用不当）
    if (exception instanceof StatementException) {
      return new PlatformException(exception.getMessage(), exception);
    }

    // 数据访问异常 → 内部异常（底层数据库错误）
    if (exception instanceof DataAccessException) {
      return new InternalException(exception.getMessage(), exception);
    }

    // 其他未知的数据模型异常，默认映射为内部异常
    return new InternalException("数据模型操作失败: " + exception.getMessage(), exception);
  }

  /**
   * 将 DataValidationException 转换为 ValidationException
   * <p>
   * 如果是 RecordValidationException，会提取字段错误信息并填充到 ValidationException 的 errors map 中。
   *
   * @param exception 数据验证异常
   * @return 验证异常
   */
  private static ValidationException convertToValidationException(DataValidationException exception) {
    // 如果是 RecordValidationException，提取字段错误信息
    if (exception instanceof RecordValidationException recordValidationException) {
      Map<String, String> errors = recordValidationException.getJoinedErrors(",");
      if (errors != null && !errors.isEmpty()) {
        return new ValidationException(errors);
      }
    }

    // 其他情况，只传递消息
    return new ValidationException(exception.getMessage());
  }

  @Override
  public OuroborosException map(Throwable throwable) {
    return toOuroborosException((DataModelException) throwable);
  }

  @Override
  public boolean supports(Throwable throwable) {
    return throwable instanceof DataModelException;
  }
}
