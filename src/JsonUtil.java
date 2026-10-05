package src;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Utilitaire minimal de sérialisation JSON par réflexion.
 * Aucune dépendance externe (pas de Jackson/Gson) pour garder Framework.jar léger.
 *
 * Gère : null, String, Number, Boolean, Character, Enum, Date/Temporal,
 * Map, Collection/Iterable, tableaux (primitifs inclus), POJO (champs privés inclus).
 */
public class JsonUtil {

    public static String toJson(Object obj) {
        return toJson(obj, new IdentityHashMap<>());
    }

    private static String toJson(Object obj, IdentityHashMap<Object, Boolean> seen) {
        if (obj == null) {
            return "null";
        }
        // Types simples
        if (obj instanceof String s) {
            return quote(s);
        }
        if (obj instanceof Character c) {
            return quote(c.toString());
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Enum<?> e) {
            return quote(e.name());
        }
        // Dates : sérialisées en ISO via toString entre guillemets
        if (obj instanceof Date || obj instanceof Temporal
                || obj instanceof LocalDate || obj instanceof LocalDateTime || obj instanceof LocalTime) {
            return quote(obj.toString());
        }

        // Anti-référence circulaire
        if (seen.containsKey(obj)) {
            return "null";
        }
        seen.put(obj, Boolean.TRUE);

        try {
            // Map -> {"k":v}
            if (obj instanceof Map<?, ?> map) {
                StringBuilder sb = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (!first) sb.append(",");
                    sb.append(quote(String.valueOf(e.getKey())));
                    sb.append(":");
                    sb.append(toJson(e.getValue(), seen));
                    first = false;
                }
                sb.append("}");
                return sb.toString();
            }

            // Collection / Iterable -> [...]
            if (obj instanceof Collection<?> col) {
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (Object item : col) {
                    if (!first) sb.append(",");
                    sb.append(toJson(item, seen));
                    first = false;
                }
                sb.append("]");
                return sb.toString();
            }
            if (obj instanceof Iterable<?> it) {
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (Object item : it) {
                    if (!first) sb.append(",");
                    sb.append(toJson(item, seen));
                    first = false;
                }
                sb.append("]");
                return sb.toString();
            }

            // Tableau (primitif ou objet) -> [...]
            if (obj.getClass().isArray()) {
                int len = Array.getLength(obj);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < len; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(toJson(Array.get(obj, i), seen));
                }
                sb.append("]");
                return sb.toString();
            }

            // POJO : tous les champs (privés + hérités, non statiques)
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            Class<?> clazz = obj.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    if (f.isSynthetic()) continue;
                    f.setAccessible(true);
                    Object value;
                    try {
                        value = f.get(obj);
                    } catch (IllegalAccessException ex) {
                        continue;
                    }
                    if (!first) sb.append(",");
                    sb.append(quote(f.getName()));
                    sb.append(":");
                    sb.append(toJson(value, seen));
                    first = false;
                }
                clazz = clazz.getSuperclass();
            }
            sb.append("}");
            return sb.toString();
        } finally {
            seen.remove(obj);
        }
    }

    private static String quote(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
