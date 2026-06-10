package crystal.util;

import java.lang.reflect.Method;

import arc.util.Log;

public class ReflectUtil {

  // ========== 之前的字段相关方法 ==========
  public static boolean setField(Object target, String fieldName, Object newValue) {
    try {
      java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, newValue);
      return true;
    } catch (Exception e) {
      Log.err("反射修改字段失败: " + fieldName, e);
      return false;
    }
  }

  public static <T> T getField(Object target, String fieldName) {
    try {
      java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      return (T) field.get(target);
    } catch (Exception e) {
      Log.err("反射读取字段失败: " + fieldName, e);
      return null;
    }
  }

  public static boolean setStaticField(Class<?> clazz, String fieldName, Object newValue) {
    try {
      java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(null, newValue);
      return true;
    } catch (Exception e) {
      Log.err("反射修改静态字段失败: " + fieldName, e);
      return false;
    }
  }

  public static <T> T getStaticField(Class<?> clazz, String fieldName) {
    try {
      java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
      field.setAccessible(true);
      return (T) field.get(null);
    } catch (Exception e) {
      Log.err("反射读取静态字段失败: " + fieldName, e);
      return null;
    }
  }

  // ========== 新增：方法调用相关方法 ==========
  /**
   * 调用实例对象的方法（无参/有参通用）
   * 
   * @param target     要调用方法的目标对象
   * @param methodName 方法名
   * @param args       方法的实参（按顺序传入）
   * @return 方法返回值，无返回值返回null
   */
  public static <T> T invokeMethod(Object target, String methodName, Object... args) {
    try {
      // 自动提取参数的类型
      Class<?>[] paramTypes = new Class[args.length];
      for (int i = 0; i < args.length; i++) {
        paramTypes[i] = args[i].getClass();
      }
      Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
      method.setAccessible(true);
      return (T) method.invoke(target, args);
    } catch (Exception e) {
      Log.err("反射调用方法失败: " + methodName, e);
      return null;
    }
  }

  /**
   * 调用类的静态方法（无参/有参通用）
   * 
   * @param clazz      目标类
   * @param methodName 方法名
   * @param args       方法的实参（按顺序传入）
   * @return 方法返回值，无返回值返回null
   */
  public static <T> T invokeStaticMethod(Class<?> clazz, String methodName, Object... args) {
    try {
      Class<?>[] paramTypes = new Class[args.length];
      for (int i = 0; i < args.length; i++) {
        paramTypes[i] = args[i].getClass();
      }
      Method method = clazz.getDeclaredMethod(methodName, paramTypes);
      method.setAccessible(true);
      return (T) method.invoke(null, args);
    } catch (Exception e) {
      Log.err("反射调用静态方法失败: " + methodName, e);
      return null;
    }
  }

  /**
   * 精准调用重载方法（手动指定参数类型，解决重载匹配问题）
   * 
   * @param target     要调用方法的目标对象
   * @param methodName 方法名
   * @param paramTypes 方法的参数类型数组，和方法定义完全匹配
   * @param args       方法的实参（按顺序传入）
   * @return 方法返回值，无返回值返回null
   */
  public static <T> T invokeMethodExact(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
    try {
      Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
      method.setAccessible(true);
      return (T) method.invoke(target, args);
    } catch (Exception e) {
      Log.err("反射精准调用方法失败: " + methodName, e);
      return null;
    }
  }
}
