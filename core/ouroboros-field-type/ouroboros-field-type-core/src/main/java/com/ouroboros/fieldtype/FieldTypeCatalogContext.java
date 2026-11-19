package com.ouroboros.fieldtype;

public class FieldTypeCatalogContext {
  private FieldTypeCatalogScene scene;
  private String appName;

  public static FieldTypeCatalogContext runtime() {
    var context = new FieldTypeCatalogContext();
    context.setScene(FieldTypeCatalogScene.RUNTIME);
    return context;
  }

  public static FieldTypeCatalogContext devApp(String appName) {
    var context = new FieldTypeCatalogContext();
    context.setScene(FieldTypeCatalogScene.DEV_APP);
    context.setAppName(appName);
    return context;
  }

  public FieldTypeCatalogScene getScene() {
    return scene;
  }

  public void setScene(FieldTypeCatalogScene scene) {
    this.scene = scene;
  }

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }
}
