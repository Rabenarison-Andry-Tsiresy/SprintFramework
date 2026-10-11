package src;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import src.annotation.DateFormat;
import src.annotation.Numeric;
import src.annotation.Param;
import src.annotation.ParamObject;
import src.annotation.Range;
import src.annotation.Required;

// Sprint 7 : @Param simple + conversion.
// Sprint 7b : @ParamObject(prefix) + validation (@Required, @Numeric, @Range, @DateFormat).
public class ParamBinder {

    public static Object[] resolveMethodArgs(Method method, HttpServletRequest req, HttpServletResponse res)
            throws Exception {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            Class<?> type = p.getType();

            // Objets servlet injectés directement
            if (type.isAssignableFrom(HttpServletRequest.class)) { args[i] = req; continue; }
            if (type.isAssignableFrom(HttpServletResponse.class)) { args[i] = res; continue; }
            if (type.isAssignableFrom(HttpSession.class)) { args[i] = req.getSession(); continue; }

            rejectRequestParam(method, p);

            Param paramAnn = p.getAnnotation(Param.class);
            if (paramAnn != null) {
                args[i] = resolveSimpleParam(p, type, paramAnn, req);
                continue;
            }

            ParamObject poAnn = p.getAnnotation(ParamObject.class);
            if (poAnn != null) {
                String prefix = prefixOf(poAnn, type);
                Object obj = bindObject(type, prefix, req);
                validateObject(obj);
                args[i] = obj;
                continue;
            }

            throw new BindingException(
                "Parametre non annote sur " + method.getDeclaringClass().getSimpleName()
                + "." + method.getName() + "() param[" + i + " " + type.getSimpleName() + " " + p.getName() + "] : "
                + "ajoutez @Param(name=\"...\") (Sprint 7), @ParamObject(name=\"...\") (Sprint 7b), "
                + "ou HttpServletRequest/Response/Session.");
        }
        return args;
    }

    private static void rejectRequestParam(Method method, Parameter p) {
        for (Annotation a : p.getAnnotations()) {
            if (a.annotationType().getSimpleName().equals("RequestParam")) {
                throw new BindingException(
                    "ETU004384 : @RequestParam non supporte par le framework. "
                    + "Utilisez @Param(name=\"...\") (Sprint 7) ou @ParamObject(name=\"...\") (Sprint 7b) "
                    + "sur " + method.getDeclaringClass().getSimpleName() + "." + method.getName()
                    + " param[" + p.getName() + "].");
            }
        }
    }

    private static Object resolveSimpleParam(Parameter p, Class<?> type, Param ann, HttpServletRequest req)
            throws Exception {
        String name = !ann.name().isBlank() ? ann.name() : ann.value();
        if (name.isBlank()) {
            name = p.getName(); // nécessite -parameters à la compilation
        }
        String raw = req.getParameter(name);
        try {
            return convert(raw, type, name, null);
        } catch (BindingException be) {
            throw be;
        } catch (Exception e) {
            throw new BindingException(
                "Echec de conversion @Param(name=\"" + name + "\")=\"" + raw
                + "\" vers " + type.getSimpleName() + " : " + e.getMessage(), e);
        }
    }

    private static String prefixOf(ParamObject ann, Class<?> type) {
        String prefix = !ann.name().isBlank() ? ann.name() : ann.value();
        return prefix.isBlank() ? decapitalize(type.getSimpleName()) : prefix;
    }

    // ---------- Binding objet ----------

    private static Object bindObject(Class<?> clazz, String prefix, HttpServletRequest req) throws Exception {
        Object instance;
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new BindingException(
                "Impossible d'instancier " + clazz.getSimpleName()
                + " (constructeur sans argument requis) : " + e.getMessage(), e);
        }

        for (Field f : getAllFields(clazz)) {
            String raw = readParam(req, prefix, f);
            if (raw == null) {
                continue; // champ absent -> @Required s'en chargera
            }
            DateFormat df = f.getAnnotation(DateFormat.class);
            String key = prefix + "." + customName(f);
            try {
                setFieldValue(instance, f, convert(raw, f.getType(), key, df != null ? df.pattern() : null));
            } catch (Exception e) {
                throw new BindingException(
                    "Echec de conversion " + key + "=\"" + raw + "\" vers "
                    + f.getType().getSimpleName() + " : " + e.getMessage(), e);
            }
        }
        return instance;
    }

    // Cherche la valeur HTTP : prefix.champ, champ seul, puis nom Java si @Param renomme.
    private static String readParam(HttpServletRequest req, String prefix, Field f) {
        String custom = customName(f);
        String raw = req.getParameter(prefix + "." + custom);
        if (raw == null) {
            raw = req.getParameter(custom);
        }
        if (raw == null && !custom.equals(f.getName())) {
            raw = req.getParameter(prefix + "." + f.getName());
            if (raw == null) {
                raw = req.getParameter(f.getName());
            }
        }
        return raw;
    }

    private static String customName(Field f) {
        Param fp = f.getAnnotation(Param.class);
        if (fp != null) {
            String cn = !fp.name().isBlank() ? fp.name() : fp.value();
            if (!cn.isBlank()) {
                return cn;
            }
        }
        return f.getName();
    }

    private static void setFieldValue(Object instance, Field field, Object value) throws Exception {
        Method setter = findSetter(instance.getClass(), field, value);
        if (setter != null) {
            try {
                setter.invoke(instance, value);
                return;
            } catch (BindingException be) {
                throw be;
            } catch (Exception ignored) {
                // fallback : accès direct
            }
        }
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static Method findSetter(Class<?> clazz, Field field, Object value) {
        String name = "set" + Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
        for (Method m : clazz.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != 1) {
                continue;
            }
            Class<?> arg = m.getParameterTypes()[0];
            if (arg.isAssignableFrom(field.getType())
                    || isPrimitiveCompatible(arg, field.getType())
                    || (value != null && arg.isAssignableFrom(value.getClass()))) {
                return m;
            }
        }
        return null;
    }

    private static boolean isPrimitiveCompatible(Class<?> a, Class<?> b) {
        return box(a).isAssignableFrom(box(b)) || box(b).isAssignableFrom(box(a));
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        if (c == boolean.class) return Boolean.class;
        if (c == short.class) return Short.class;
        if (c == byte.class) return Byte.class;
        if (c == char.class) return Character.class;
        return c;
    }

    // ---------- Validation Sprint 7b ----------

    private static void validateObject(Object obj) {
        List<String> errors = new ArrayList<>();
        for (Field f : getAllFields(obj.getClass())) {
            f.setAccessible(true);
            Object value;
            try {
                value = f.get(obj);
            } catch (IllegalAccessException e) {
                continue;
            }
            checkRequired(f, value, errors);
            checkNumeric(f, value, errors);
            checkRange(f, value, errors);
            checkDateFormat(f, value, errors);
        }
        if (!errors.isEmpty()) {
            throw new BindingException("Echec validation (" + obj.getClass().getSimpleName() + ") : "
                + String.join(" | ", errors));
        }
    }

    private static void checkRequired(Field f, Object value, List<String> errors) {
        Required req = f.getAnnotation(Required.class);
        if (req != null && (value == null || (value instanceof String s && s.isBlank()))) {
            errors.add(f.getName() + " : " + req.message() + " (@Required)");
        }
    }

    private static void checkNumeric(Field f, Object value, List<String> errors) {
        Numeric num = f.getAnnotation(Numeric.class);
        if (num == null || value == null) {
            return;
        }
        if (value instanceof Number) {
            return;
        }
        if (value instanceof String s) {
            if (!s.isBlank() && !s.trim().matches("[+-]?\\d+(\\.\\d+)?")) {
                errors.add(f.getName() + " : " + num.message() + " (@Numeric, valeur=\"" + value + "\")");
            }
            return;
        }
        errors.add(f.getName() + " : " + num.message() + " (@Numeric)");
    }

    private static void checkRange(Field f, Object value, List<String> errors) {
        Range range = f.getAnnotation(Range.class);
        if (range == null || value == null) {
            return;
        }
        Double d = toDouble(value);
        if (d == null && value instanceof String s && !s.isBlank()) {
            errors.add(f.getName() + " : non numerique pour @Range (valeur=\"" + value + "\")");
            return;
        }
        if (d != null && (d < range.min() || d > range.max())) {
            errors.add(f.getName() + " : " + range.message()
                + " (@Range min=" + range.min() + " max=" + range.max() + ", valeur=" + value + ")");
        }
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static void checkDateFormat(Field f, Object value, List<String> errors) {
        DateFormat df = f.getAnnotation(DateFormat.class);
        if (df == null || !(value instanceof String s) || s.isBlank()) {
            return;
        }
        if (!tryParseDate(s.trim(), df.pattern())) {
            errors.add(f.getName() + " : " + df.message()
                + " (@DateFormat pattern=\"" + df.pattern() + "\", valeur=\"" + value + "\")");
        }
    }

    private static boolean tryParseDate(String value, String pattern) {
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern);
            try { LocalDate.parse(value, fmt); return true; } catch (Exception ignored) {}
            try { LocalDateTime.parse(value, fmt); return true; } catch (Exception ignored) {}
            SimpleDateFormat sdf = new SimpleDateFormat(pattern);
            sdf.setLenient(false);
            sdf.parse(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- Conversion String -> type ----------

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Object convert(String raw, Class<?> target, String paramName, String datePattern) throws Exception {
        String v = raw == null ? null : raw.trim();

        if (v == null || v.isEmpty()) {
            return defaultForEmpty(target, paramName);
        }
        if (target == String.class) {
            return raw;
        }
        if (target == int.class || target == Integer.class) return Integer.parseInt(v);
        if (target == long.class || target == Long.class) return Long.parseLong(v);
        if (target == double.class || target == Double.class) return Double.parseDouble(v);
        if (target == float.class || target == Float.class) return Float.parseFloat(v);
        if (target == short.class || target == Short.class) return Short.parseShort(v);
        if (target == byte.class || target == Byte.class) return Byte.parseByte(v);
        if (target == boolean.class || target == Boolean.class) return parseBoolean(v);
        if (target == char.class || target == Character.class) return v.charAt(0);
        if (target.isEnum()) {
            return Enum.valueOf((Class<Enum>) target, v);
        }
        return convertDate(v, target, paramName, datePattern);
    }

    private static Object defaultForEmpty(Class<?> target, String paramName) {
        if (target == String.class) {
            return null;
        }
        if (target.isPrimitive()) {
            if (target == boolean.class) return false;
            if (target == char.class) return '\0';
            if (target == int.class) throw missing(paramName, "int");
            if (target == long.class) throw missing(paramName, "long");
            if (target == double.class) throw missing(paramName, "double");
            if (target == float.class) throw missing(paramName, "float");
            if (target == short.class) throw missing(paramName, "short");
            if (target == byte.class) throw missing(paramName, "byte");
        }
        return null;
    }

    private static BindingException missing(String paramName, String type) {
        return new BindingException("Parametre manquant : \"" + paramName + "\" (" + type + " requis)");
    }

    private static Object convertDate(String v, Class<?> target, String paramName, String datePattern)
            throws Exception {
        if (target == java.sql.Date.class) {
            if (datePattern != null && !datePattern.equals("yyyy-MM-dd")) {
                SimpleDateFormat sdf = new SimpleDateFormat(datePattern);
                sdf.setLenient(false);
                return new java.sql.Date(sdf.parse(v).getTime());
            }
            return java.sql.Date.valueOf(v);
        }
        if (target == java.util.Date.class) {
            return parseUtilDate(v, datePattern);
        }
        if (target == java.sql.Timestamp.class) {
            try {
                return java.sql.Timestamp.valueOf(v);
            } catch (Exception e) {
                return new java.sql.Timestamp(parseUtilDate(v, datePattern).getTime());
            }
        }
        if (target == LocalDate.class) {
            return LocalDate.parse(v, DateTimeFormatter.ofPattern(datePattern != null ? datePattern : "yyyy-MM-dd"));
        }
        if (target == LocalDateTime.class) {
            try {
                return LocalDateTime.parse(v,
                    DateTimeFormatter.ofPattern(datePattern != null ? datePattern : "yyyy-MM-dd'T'HH:mm"));
            } catch (Exception e) {
                return LocalDateTime.parse(v); // ISO
            }
        }
        if (target == LocalTime.class) {
            return LocalTime.parse(v);
        }
        throw new BindingException("Type non supporte pour le binding : " + target.getName()
            + " (param=\"" + paramName + "\"). Types supportes : String, int/Integer, long, double, float, short, byte, boolean, char, Enum, java.sql.Date, java.util.Date, LocalDate.");
    }

    private static java.util.Date parseUtilDate(String v, String datePattern) throws Exception {
        // Pattern @DateFormat en premier, puis fallbacks courants.
        String first = datePattern != null ? datePattern : "yyyy-MM-dd";
        String[] patterns = { first, "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd HH:mm:ss", "dd/MM/yyyy", "yyyy-MM-dd" };
        Exception last = null;
        for (String p : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(p);
                sdf.setLenient(false);
                return sdf.parse(v);
            } catch (Exception e) {
                last = e;
            }
        }
        throw last;
    }

    private static boolean parseBoolean(String v) {
        String s = v.trim().toLowerCase();
        if (s.equals("true") || s.equals("1") || s.equals("on") || s.equals("oui") || s.equals("yes")) return true;
        if (s.equals("false") || s.equals("0") || s.equals("off") || s.equals("non") || s.equals("no") || s.equals("")) return false;
        return Boolean.parseBoolean(s);
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> list = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isSynthetic() || java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                list.add(f);
            }
        }
        return list;
    }

    private static String decapitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
