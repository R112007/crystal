package crystal.entities.shentong;

import arc.Core;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.util.io.Reads;
import arc.util.io.Writes;
import crystal.entities.units.UnitEnum.JingJie;
import crystal.gen.*;
import crystal.type.MagicUnitType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.Locale;

public abstract class ShenTong implements Cloneable {
  public static ObjectMap<Integer, ShenTong> shengTongMap = new ObjectMap<>();
  public int id;
  public TextureRegionDrawable icon;

  public void update(Magicc magic) {
  }

  public void setStat(MagicUnitType magic, Table table) {
  }

  public void setBar(Magicc magic, Table table) {
  }

  public void death(Magicc magic) {
  }

  public void write(Writes writes) {
  }

  public void read(Reads reads) {
  }

  public void init(MagicUnitType type) {
  }

  public abstract String name();

  public abstract String description();

  public ShenTong create() {
    try {
      return (ShenTong) clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException("java sucks", e);
    }
  }

  /**
   * Returns the public numeric constructor with the largest parameter list.
   * Editor configuration follows this constructor so adding a new shen tong
   * only requires declaring its constructor and bundle labels.
   */
  public Constructor<?> editableConstructor() {
    Constructor<?> best = null;
    for (Constructor<?> constructor : getClass().getConstructors()) {
      boolean numeric = true;
      for (Class<?> parameter : constructor.getParameterTypes()) {
        numeric &= isNumeric(parameter);
      }
      if (numeric && (best == null || constructor.getParameterCount() > best.getParameterCount())) {
        best = constructor;
      }
    }
    return best;
  }

  public Parameter[] editableParameters() {
    Constructor<?> constructor = editableConstructor();
    return constructor == null ? new Parameter[0] : constructor.getParameters();
  }

  public float[] defaultEditableValues() {
    Parameter[] parameters = editableParameters();
    float[] values = new float[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      try {
        Field field = getClass().getField(parameters[i].getName());
        Object value = field.get(this);
        if (value instanceof Number number) {
          values[i] = number.floatValue();
        }
      } catch (ReflectiveOperationException ignored) {
      }
    }
    return values;
  }

  public String editableParameterLabel(Parameter parameter) {
    String prefix = getClass().getSimpleName().toLowerCase(Locale.ROOT);
    String name = parameter.getName();
    String lowerName = name.toLowerCase(Locale.ROOT);
    String[] keys = {
        prefix + "." + name,
        prefix + "." + lowerName,
        "shengtong." + prefix + "." + name,
        "shengtong." + prefix + "." + lowerName
    };

    if (Core.bundle != null) {
      for (String key : keys) {
        String value = Core.bundle.getOrNull(key);
        if (value != null) {
          return value;
        }
      }
    }
    return name;
  }

  /**
   * Creates a configured instance from the editable constructor. Returns null
   * when the values do not match, allowing callers to use their normal default.
   */
  public ShenTong createConfigured(float[] values) {
    Constructor<?> constructor = editableConstructor();
    if (constructor == null || values == null || values.length != constructor.getParameterCount()) {
      return null;
    }

    Class<?>[] types = constructor.getParameterTypes();
    Object[] arguments = new Object[types.length];
    for (int i = 0; i < types.length; i++) {
      arguments[i] = convert(values[i], types[i]);
    }

    try {
      return (ShenTong) constructor.newInstance(arguments);
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }

  private static boolean isNumeric(Class<?> type) {
    return type == byte.class || type == short.class || type == int.class || type == long.class
        || type == float.class || type == double.class
        || type == Byte.class || type == Short.class || type == Integer.class || type == Long.class
        || type == Float.class || type == Double.class;
  }

  private static Object convert(float value, Class<?> type) {
    if (type == float.class || type == Float.class) {
      return value;
    }
    if (type == double.class || type == Double.class) {
      return (double) value;
    }
    if (type == byte.class || type == Byte.class) {
      return (byte) value;
    }
    if (type == short.class || type == Short.class) {
      return (short) value;
    }
    if (type == int.class || type == Integer.class) {
      return (int) value;
    }
    if (type == long.class || type == Long.class) {
      return (long) value;
    }
    return value;
  }

  public abstract JingJie minimumJingJie();

  @Override
  public boolean equals(Object object) {
    if (object == this)
      return true;
    ShenTong shenTong = (ShenTong) object;
    if (this.name() == shenTong.name() && this.description() == shenTong.description())
      return true;
    else
      return false;
  }
}
