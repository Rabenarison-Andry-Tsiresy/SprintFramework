package src;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import src.annotation.Param;

// Remplit un POJO depuis la requête : new Employe() + set de chaque champ @Param.
public class ParamBinder {

    // Ex : save(Employe emp) -> [emp rempli], save(@Param("nom") String n) -> ["Rakoto"]
    public static Object[] resolveMethodArgs(java.lang.reflect.Method m, HttpServletRequest req,
                                             HttpServletResponse res) throws Exception {
        Parameter[] ps = m.getParameters();
        Object[] args = new Object[ps.length];
        for (int i = 0; i < ps.length; i++) {
            Class<?> t = ps[i].getType();
            if (t == HttpServletRequest.class) { args[i] = req; continue; }
            if (t == HttpServletResponse.class) { args[i] = res; continue; }
            if (t == HttpSession.class) { args[i] = req.getSession(); continue; }

            Param a = ps[i].getAnnotation(Param.class);
            if (isSimple(t)) {
                String name = (a != null && !a.value().isBlank()) ? a.value() : ps[i].getName();
                args[i] = toValue(req.getParameter(name), t);
            } else {
                String prefix = (a != null && !a.value().isBlank()) ? a.value() : null;
                args[i] = bind(t, req, prefix);
            }
        }
        return args;
    }

    // Ex : bind(Employe.class, req) -> Employe[nom=Rakoto, age=30]
    public static <T> T bind(Class<T> clazz, HttpServletRequest req, String prefix) throws Exception {
        T obj = clazz.getDeclaredConstructor().newInstance();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                f.setAccessible(true);
                Param a = f.getAnnotation(Param.class);
                String name = (a != null && !a.value().isBlank()) ? a.value() : f.getName();

                String raw = req.getParameter(name);
                if (raw == null && prefix != null) raw = req.getParameter(prefix + "." + name);

                if (raw != null) {
                    if (!raw.isEmpty()) f.set(obj, toValue(raw, f.getType()));
                    continue;
                }
                // Objet imbriqué : adresse.rue -> bind(Adresse.class, req, "adresse")
                if (!isSimple(f.getType()) && hasPrefix(req, name)) {
                    f.set(obj, bind(f.getType(), req, name));
                }
            }
        }
        return obj;
    }

    public static <T> T bind(Class<T> clazz, HttpServletRequest req) throws Exception {
        return bind(clazz, req, null);
    }

    // Garde compat avec l'ancien nom utilisé dans le servlet
    public static <T> T bindObject(Class<T> clazz, HttpServletRequest req, String prefix) throws Exception {
        return bind(clazz, req, prefix);
    }

    public static <T> T bindObject(Class<T> clazz, HttpServletRequest req) throws Exception {
        return bind(clazz, req, null);
    }

    private static boolean hasPrefix(HttpServletRequest req, String prefix) {
        for (String k : req.getParameterMap().keySet())
            if (k.startsWith(prefix + ".")) return true;
        return false;
    }

    static boolean isSimple(Class<?> t) {
        return t == String.class || t == int.class || t == Integer.class
                || t == long.class || t == Long.class || t == double.class || t == Double.class
                || t == float.class || t == Float.class || t == boolean.class || t == Boolean.class
                || t == short.class || t == Short.class || t == byte.class || t == Byte.class
                || t == char.class || t == Character.class || t.isEnum()
                || t == LocalDate.class || t == LocalDateTime.class || t == LocalTime.class
                || t == java.util.Date.class || t == java.sql.Date.class;
    }

    private static Object defaultVal(Class<?> t) {
        if (!t.isPrimitive()) return null;
        if (t == boolean.class) return false;
        if (t == char.class) return '\0';
        if (t == long.class) return 0L;
        if (t == double.class) return 0d;
        if (t == float.class) return 0f;
        return 0;
    }

    // "30" -> 30, "on" -> true, "2026-10-02" -> LocalDate
    @SuppressWarnings({ "unchecked", "rawtypes" })
    static Object toValue(String raw, Class<?> t) throws Exception {
        if (t == String.class) return raw;
        if (raw == null || raw.trim().isEmpty()) return defaultVal(t);
        String v = raw.trim();
        if (t == int.class || t == Integer.class) return Integer.parseInt(v);
        if (t == long.class || t == Long.class) return Long.parseLong(v);
        if (t == double.class || t == Double.class) return Double.parseDouble(v.replace(',', '.'));
        if (t == float.class || t == Float.class) return Float.parseFloat(v.replace(',', '.'));
        if (t == short.class || t == Short.class) return Short.parseShort(v);
        if (t == byte.class || t == Byte.class) return Byte.parseByte(v);
        if (t == boolean.class || t == Boolean.class)
            return v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("on") || v.equalsIgnoreCase("yes");
        if (t == char.class || t == Character.class) return v.charAt(0);
        if (t.isEnum()) return Enum.valueOf((Class<Enum>) t, v);
        if (t == LocalDate.class) return LocalDate.parse(v);
        if (t == LocalTime.class) return LocalTime.parse(v);
        if (t == LocalDateTime.class) return LocalDateTime.parse(v.replace(" ", "T"));
        if (t == java.sql.Date.class) return java.sql.Date.valueOf(v);
        if (t == java.util.Date.class) return java.sql.Date.valueOf(v);
        return t.getConstructor(String.class).newInstance(raw);
    }

    // Garde compat avec l'ancien nom
    static Object convert(String raw, Class<?> t) throws Exception {
        return toValue(raw, t);
    }
}
