package com.ouroboros.data;

import java.util.HashMap;
import java.util.List;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.model.TypedDataModelCenter;
import com.ouroboros.data.util.DataJson;

/**
 * @author liansz
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class)
public class TestModelOperate {

  @Test
  public void add() {
    TestModel testModel = new TestModel();
    testModel.setId("1");
    testModel.setName("张三");
    testModel.setAge(18);

    TypedDataModel<String, TestModel> dataMode = getDataModel();
    TestModel result = dataMode.insert(testModel)
        .getOrElseThrow(TestModelOperate::toRuntimeException);

    System.out.println(DataJson.toJsonString(result));
  }

  @Test
  public void delete() {
    TypedDataModel<String, TestModel> dataMode = getDataModel();
    Long result = dataMode.delete("1")
        .getOrElseThrow(TestModelOperate::toRuntimeException);

    System.out.println(result);
  }

  @Test
  public void update() {
    TestModel testModel = new TestModel();
    testModel.setId("1");
    testModel.setName("张三-update");
    testModel.setAge(18);

    TypedDataModel<String, TestModel> dataMode = getDataModel();
    Long result = dataMode.update("1", testModel)
        .getOrElseThrow(TestModelOperate::toRuntimeException);

    System.out.println(result);
  }

  @Test
  public void getById() {
    TypedDataModel<String, TestModel> dataMode = getDataModel();
    TestModel result = dataMode.get("1")
        .getOrElseThrow(TestModelOperate::toRuntimeException);

    System.out.println(DataJson.toJsonString(result));
  }

  @Test
  public void getAll() {
    TypedDataModel<String, TestModel> dataMode = getDataModel();
    List<TestModel> result = dataMode.query(new HashMap<>())
        .getOrElseThrow(TestModelOperate::toRuntimeException);

    System.out.println(DataJson.toJsonString(result));
  }

  private TypedDataModel<String, TestModel> getDataModel() {
    var dataModel = TypedDataModelCenter.getDataModel(String.class, TestModel.class);
    Assume.assumeTrue("数据模型不存在，跳过集成测试", dataModel.isPresent());
    return dataModel.get();
  }

  private static RuntimeException toRuntimeException(Throwable cause) {
    if (cause instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    return new RuntimeException(cause);
  }
}
